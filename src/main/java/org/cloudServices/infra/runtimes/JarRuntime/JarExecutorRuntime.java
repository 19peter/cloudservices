package org.cloudServices.infra.runtimes.JarRuntime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cloudServices.infra.runtimes.RuntimeInterface;
import org.cloudServices.infra.runtimes.Workload;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class JarExecutorRuntime implements RuntimeInterface {
    private static final Logger logger = LogManager.getLogger(JarExecutorRuntime.class);

    private Process process;
    private boolean booted = false;
    private JarWorkload workload;
    private ProcessBuilder processBuilder;

    public JarExecutorRuntime(JarWorkload workload) {
        this.workload = workload;
    }

    @Override
    public synchronized void boot() {
        if (booted) {
            logger.info("JarExecutorRuntime is already booted.");
            return;
        }

        if (this.workload == null) {
            throw new IllegalArgumentException("Workload cannot be null");
        }

        if (this.workload.jarPath() == null || this.workload.jarPath().isBlank()) {
            throw new IllegalArgumentException("Jar path must not be null or blank");
        }

        File jarFile = new File(this.workload.jarPath());
        if (!jarFile.exists()) {
            throw new IllegalArgumentException("Jar file not found at path: " + this.workload.jarPath());
        }

        if (process != null && process.isAlive()) {
            throw new IllegalStateException("A process is already running on this runtime (PID: " + process.pid() + ")");
        }

        List<String> command = new ArrayList<>();
        command.add(resolveJavaBinary());

        if (this.workload.jvmOptions() != null && !this.workload.jvmOptions().isEmpty()) {
            command.addAll(this.workload.jvmOptions());
        }

        command.add("-jar");
        command.add(jarFile.getAbsolutePath());

        if (this.workload.args() != null && !this.workload.args().isEmpty()) {
            command.addAll(this.workload.args());
        }

        this.processBuilder = new ProcessBuilder(command);

        if (this.workload.environment() != null && !this.workload.environment().isEmpty()) {
            this.processBuilder.environment().putAll(this.workload.environment());
        }

        this.booted = true;
        logger.info("JarExecutorRuntime booted successfully.");
    }

    @Override
    public synchronized String submit() {
        if (this.workload == null) {
            throw new IllegalArgumentException("Workload cannot be null");
        }

        if (!booted) {
            boot();
        }

        if (process != null && process.isAlive()) {
            throw new IllegalStateException("A process is already running on this runtime (PID: " + process.pid() + ")");
        }

        try {
            this.process = processBuilder.start();
            long pid = process.pid();
            logger.info("Started JarWorkload process with PID: {} for jar: {}", pid, this.workload.jarPath());
            return String.valueOf(pid);
        } catch (IOException e) {
            logger.error("Failed to start JarWorkload process for jar: " + this.workload.jarPath(), e);
            throw new RuntimeException("Failed to start JarWorkload process: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void stop() {
        if (process != null && process.isAlive()) {
            long pid = process.pid();
            logger.info("Terminating process with PID: {}", pid);
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    logger.warn("Process {} did not terminate within timeout; forcibly destroying.", pid);
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for process {} to stop; destroying forcibly.", pid);
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            logger.info("Process {} terminated.", pid);
        } else {
            logger.info("No active process to stop.");
        }
    }

    public synchronized Process getProcess() {
        return process;
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    public synchronized boolean isBooted() {
        return booted;
    }

    public synchronized JarWorkload getWorkload() {
        return workload;
    }

    public synchronized void setWorkload(JarWorkload workload) {
        this.workload = workload;
        this.booted = false;
    }

    private String resolveJavaBinary() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            Path binDir = Path.of(javaHome, "bin");
            Path exePath = binDir.resolve("java.exe");
            if (Files.exists(exePath)) {
                return exePath.toString();
            }
            Path binPath = binDir.resolve("java");
            if (Files.exists(binPath)) {
                return binPath.toString();
            }
        }
        return "java";
    }
}
