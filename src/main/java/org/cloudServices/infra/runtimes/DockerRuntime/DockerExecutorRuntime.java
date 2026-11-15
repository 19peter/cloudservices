package org.cloudServices.infra.runtimes.DockerRuntime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cloudServices.global.port.PortAllocator;
import org.cloudServices.global.port.PortAllocator.PortLease;
import org.cloudServices.infra.runtimes.RuntimeInterface;
import org.cloudServices.shared.runtime.RuntimeSubmission;
import org.cloudServices.shared.runtime.ServiceEndpoint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DockerExecutorRuntime implements RuntimeInterface {
    private static final Logger logger = LogManager.getLogger(DockerExecutorRuntime.class);
    private static final String CONTAINER_NAME_PATTERN = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}";
    private static final String IMAGE_TAG_PATTERN = "[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}";
    private static final String ENVIRONMENT_KEY_PATTERN = "[A-Za-z_][A-Za-z0-9_]*";
    private static final String RUNTIME_LABEL = "com.cloudservices.runtime-id";
    private static final Duration START_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_COMMAND_OUTPUT_BYTES = 65536;

    private final DockerWorkload workload;
    private final DockerCommandExecutor commandExecutor;
    private PortLease portLease;
    private boolean booted;
    private String containerId;
    private String runtimeLabel;
    private ProcessBuilder processBuilder;
    private RuntimeConfiguration runtimeConfiguration;

    public DockerExecutorRuntime(DockerWorkload workload) {
        this(workload, DockerExecutorRuntime::executeDockerCommand);
    }

    DockerExecutorRuntime(DockerWorkload workload, DockerCommandExecutor commandExecutor) {
        this.workload = workload;
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor cannot be null");
    }

    @Override
    public synchronized void boot() {
        if (booted) {
            logger.info("DockerExecutorRuntime is already booted.");
            return;
        }

        if (workload == null) {
            throw new IllegalArgumentException("Workload cannot be null");
        }

        if (portLease != null || containerId != null || runtimeLabel != null) {
            throw new IllegalStateException("Cannot boot while a container or port lease is active");
        }

        this.runtimeConfiguration = validateAndPrepareConfiguration();
        this.processBuilder = new ProcessBuilder(runtimeConfiguration.command());
        this.booted = true;
        logger.info("DockerExecutorRuntime prepared successfully for image: {}.",
                runtimeConfiguration.imageReference());
    }

    @Override
    public synchronized RuntimeSubmission submit() {
        if (workload == null) {
            throw new IllegalArgumentException("Workload cannot be null");
        }

        if (!booted) {
            boot();
        }

        if (containerId != null) {
            throw new IllegalStateException("A container is already running (ID: " + containerId + ")");
        }

        if (portLease != null || runtimeLabel != null) {
            throw new IllegalStateException("A previous Docker operation has unresolved resources; stop it before retrying");
        }

        if (runtimeConfiguration == null || processBuilder == null) {
            throw new IllegalStateException("Docker runtime configuration has not been prepared");
        }

        this.portLease = PortAllocator.allocate();
        this.runtimeLabel = UUID.randomUUID().toString();

        try {
            List<String> command = createRunCommand(portLease.port());
            DockerCommandResult result = commandExecutor.execute(command, START_TIMEOUT);
            if (!result.succeeded()) {
                throw new IllegalStateException(commandFailureMessage("docker run", result));
            }

            this.containerId = findContainerId(result.output());
            if (containerId == null) {
                throw new IllegalStateException("Docker run succeeded but did not return a valid container ID");
            }

            RuntimeSubmission submission = new RuntimeSubmission(
                    containerId,
                    ServiceEndpoint.http("127.0.0.1", portLease.port()));
            logger.info("Started Docker container {} for image {} on host port {}.",
                    containerId, runtimeConfiguration.imageReference(), portLease.port());
            return submission;
        } catch (DockerCommandException e) {
            RuntimeException failure = new IllegalStateException(e.getMessage(), e);
            if (e.commandStarted()) {
                cleanupFailedStart(failure);
            } else {
                clearRunState();
            }
            throw failure;
        } catch (RuntimeException e) {
            cleanupFailedStart(e);
            throw e;
        }
    }

    @Override
    public synchronized void stop() {
        if (portLease == null && runtimeLabel == null && containerId == null) {
            logger.info("No active Docker runtime resources to stop.");
            return;
        }

        if (portLease == null || runtimeLabel == null) {
            throw new IllegalStateException("Docker runtime has inconsistent container or port-lease state");
        }

        RuntimeException stopFailure = null;
        if (containerId != null) {
            try {
                DockerCommandResult result = commandExecutor.execute(
                        List.of("docker", "stop", "--time", "10", containerId),
                        STOP_TIMEOUT);
                if (!result.succeeded()) {
                    stopFailure = new IllegalStateException(commandFailureMessage("docker stop", result));
                }
            } catch (RuntimeException e) {
                stopFailure = e;
            }
        }

        try {
            removeContainersWithRuntimeLabel();
            clearRunState();
            if (stopFailure != null) {
                logger.warn("docker stop did not complete cleanly, but the container was confirmed removed.",
                        stopFailure);
            }
            logger.info("Docker runtime stopped.");
        } catch (RuntimeException cleanupFailure) {
            if (stopFailure != null) {
                cleanupFailure.addSuppressed(stopFailure);
            }
            throw new IllegalStateException(
                    "Failed to confirm that the Docker container stopped; its port lease is retained",
                    cleanupFailure);
        }
    }

    public synchronized boolean isBooted() {
        return booted;
    }

    public synchronized boolean isRunning() {
        return containerId != null || (runtimeLabel != null && portLease != null);
    }

    private List<String> createRunCommand(int hostPort) {
        List<String> command = new ArrayList<>(processBuilder.command());
        int imageIndex = command.size() - 1;
        command.add(imageIndex++, "--publish");
        command.add(imageIndex++, "127.0.0.1:" + hostPort + ":" + runtimeConfiguration.containerPort());
        command.add(imageIndex++, "--label");
        command.add(imageIndex, RUNTIME_LABEL + "=" + runtimeLabel);
        return List.copyOf(command);
    }

    private void cleanupFailedStart(RuntimeException startFailure) {
        try {
            removeContainersWithRuntimeLabel();
            clearRunState();
        } catch (RuntimeException cleanupFailure) {
            startFailure.addSuppressed(cleanupFailure);
            logger.error("Docker startup failed and its container/port cleanup could not be confirmed.",
                    cleanupFailure);
        }
    }

    private void removeContainersWithRuntimeLabel() {
        Set<String> containerIds = new LinkedHashSet<>(listContainers(
                List.of("--filter", "label=" + RUNTIME_LABEL + "=" + runtimeLabel)));
        if (containerId != null) {
            containerIds.addAll(listContainers(List.of("--filter", "id=" + containerId)));
        }

        for (String id : containerIds) {
            commandExecutor.execute(
                    List.of("docker", "rm", "--force", id),
                    STOP_TIMEOUT);
        }

        Set<String> remainingContainers = new LinkedHashSet<>(listContainers(
                List.of("--filter", "label=" + RUNTIME_LABEL + "=" + runtimeLabel)));
        if (containerId != null) {
            remainingContainers.addAll(listContainers(List.of("--filter", "id=" + containerId)));
        }
        if (!remainingContainers.isEmpty()) {
            throw new IllegalStateException("Docker containers remain after cleanup: " + remainingContainers);
        }
    }

    private List<String> listContainers(List<String> filters) {
        List<String> command = new ArrayList<>(List.of("docker", "ps", "--all", "--quiet", "--no-trunc"));
        command.addAll(filters);
        DockerCommandResult result = commandExecutor.execute(command, STOP_TIMEOUT);
        if (!result.succeeded()) {
            throw new IllegalStateException(commandFailureMessage("docker ps", result));
        }

        if (result.output().isBlank()) {
            return List.of();
        }

        List<String> ids = new ArrayList<>();
        for (String line : result.output().split("\\R")) {
            String id = line.trim();
            if (!id.isEmpty()) {
                if (!id.matches("[0-9a-fA-F]{12,64}")) {
                    throw new IllegalStateException("Docker returned an invalid container ID while listing containers");
                }
                ids.add(id);
            }
        }
        return ids;
    }

    private static String findContainerId(String output) {
        if (output == null) {
            return null;
        }
        for (String line : output.split("\\R")) {
            String candidate = line.trim();
            if (candidate.matches("[0-9a-fA-F]{12,64}")) {
                return candidate;
            }
        }
        return null;
    }

    private static String commandFailureMessage(String operation, DockerCommandResult result) {
        String detail = result.output().isBlank() ? "no diagnostic output" : result.output().trim();
        if (result.timedOut()) {
            return operation + " timed out: " + detail;
        }
        return operation + " failed with exit code " + result.exitCode() + ": " + detail;
    }

    private void clearRunState() {
        if (portLease != null) {
            portLease.close();
            portLease = null;
        }
        containerId = null;
        runtimeLabel = null;
    }

    private static DockerCommandResult executeDockerCommand(List<String> command, Duration timeout) {
        Process commandProcess;
        try {
            commandProcess = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new DockerCommandException("Could not start the Docker CLI: " + e.getMessage(), e, false);
        }

        CompletableFuture<String> output = CompletableFuture.supplyAsync(
                () -> readCommandOutput(commandProcess.getInputStream()));
        try {
            if (!commandProcess.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                commandProcess.destroyForcibly();
                commandProcess.waitFor(5, TimeUnit.SECONDS);
                return new DockerCommandResult(-1, awaitCommandOutput(output), true);
            }
            return new DockerCommandResult(commandProcess.exitValue(), awaitCommandOutput(output), false);
        } catch (InterruptedException e) {
            commandProcess.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new DockerCommandException("Interrupted while waiting for the Docker CLI", e, true);
        }
    }

    private static String readCommandOutput(InputStream input) {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            int retained = 0;
            while ((read = input.read(buffer)) != -1) {
                int toRetain = Math.min(read, MAX_COMMAND_OUTPUT_BYTES - retained);
                if (toRetain > 0) {
                    output.write(buffer, 0, toRetain);
                    retained += toRetain;
                }
            }
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    private static String awaitCommandOutput(CompletableFuture<String> output) {
        try {
            return output.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DockerCommandException("Interrupted while reading Docker CLI output", e, true);
        } catch (ExecutionException | TimeoutException e) {
            throw new DockerCommandException("Failed to read Docker CLI output: " + e.getMessage(), e, true);
        }
    }

    private RuntimeConfiguration validateAndPrepareConfiguration() {
        String image = workload.dockerImage();
        if (image == null || image.isBlank() || !image.equals(image.trim()) || image.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Docker image must not be null, blank, or contain whitespace");
        }

        String tag = workload.tag();
        if (tag != null && !tag.isBlank() && !tag.matches(IMAGE_TAG_PATTERN)) {
            throw new IllegalArgumentException("Docker image tag is invalid: " + tag);
        }

        Integer containerPort = workload.port();
        if (containerPort == null || containerPort < 1 || containerPort > 65535) {
            throw new IllegalArgumentException("Container port must be between 1 and 65535");
        }

        String containerName = workload.containerName();
        if (containerName != null && !containerName.isBlank()
                && !containerName.matches(CONTAINER_NAME_PATTERN)) {
            throw new IllegalArgumentException("Docker container name is invalid: " + containerName);
        }

        Map<String, String> environment = workload.envVars();
        Map<String, String> sortedEnvironment = new TreeMap<>();
        if (environment != null) {
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                String key = entry.getKey();
                if (key == null || !key.matches(ENVIRONMENT_KEY_PATTERN)) {
                    throw new IllegalArgumentException("Docker environment variable name is invalid: " + key);
                }
                if (entry.getValue() == null) {
                    throw new IllegalArgumentException("Docker environment variable value cannot be null: " + key);
                }
                sortedEnvironment.put(key, entry.getValue());
            }
        }

        String imageReference = tag == null || tag.isBlank() ? image : image + ":" + tag;
        List<String> command = new ArrayList<>(List.of("docker", "run", "--detach", "--rm", "--pull=missing"));
        if (containerName != null && !containerName.isBlank()) {
            command.add("--name");
            command.add(containerName);
        }
        for (Map.Entry<String, String> entry : sortedEnvironment.entrySet()) {
            command.add("--env");
            command.add(entry.getKey() + "=" + entry.getValue());
        }
        command.add(imageReference);

        return new RuntimeConfiguration(imageReference, containerName, containerPort, List.copyOf(command));
    }

    @FunctionalInterface
    interface DockerCommandExecutor {
        DockerCommandResult execute(List<String> command, Duration timeout);
    }

    record DockerCommandResult(int exitCode, String output, boolean timedOut) {
        DockerCommandResult {
            output = output == null ? "" : output;
        }

        boolean succeeded() {
            return !timedOut && exitCode == 0;
        }
    }

    private static final class DockerCommandException extends RuntimeException {
        private final boolean commandStarted;

        private DockerCommandException(String message, Throwable cause, boolean commandStarted) {
            super(message, cause);
            this.commandStarted = commandStarted;
        }

        private boolean commandStarted() {
            return commandStarted;
        }
    }

    private record RuntimeConfiguration(
            String imageReference,
            String containerName,
            int containerPort,
            List<String> command) {
    }
}
