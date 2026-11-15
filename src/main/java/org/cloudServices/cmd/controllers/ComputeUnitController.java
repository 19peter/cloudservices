package org.cloudServices.cmd.controllers;

import org.cloudServices.builder.ComputeUnitBuilder;
import org.cloudServices.global.db.Memdb;
import org.cloudServices.infra.computeUnit.ComputeUnit;
import org.cloudServices.infra.computeUnit.ComputeUnitCapacity;
import org.cloudServices.infra.runtimes.DockerRuntime.DockerWorkload;
import org.cloudServices.infra.runtimes.JSRuntime.JSFileWorkload;
import org.cloudServices.infra.runtimes.JarRuntime.JarWorkload;
import org.cloudServices.infra.runtimes.Workload;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Command(
        name = "cu",
        description = "Create and manage compute units.",
        subcommands = {
                ComputeUnitController.CreateCommand.class,
                ComputeUnitController.AssignCommand.class,
                ComputeUnitController.RunCommand.class,
                ComputeUnitController.StopCommand.class,
                ComputeUnitController.ReleaseCommand.class,
                ComputeUnitController.KillCommand.class,
                ComputeUnitController.StatusCommand.class,
                ComputeUnitController.ListCommand.class
        },
        mixinStandardHelpOptions = true
)
public class ComputeUnitController implements Runnable {
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    @Command(
            name = "create",
            description = "Create a compute unit, optionally with a workload.",
            mixinStandardHelpOptions = true
    )
    public static class CreateCommand implements Runnable {
        @Option(names = "--name", paramLabel = "NAME", description = "Name for the compute unit.")
        private String name;

        @Option(
                names = "--capacity",
                paramLabel = "CAPACITY",
                description = "Capacity: MICRO, MEDIUM, LARGE, or ULTRA."
        )
        private ComputeUnitCapacity capacity;

        @Parameters(
                arity = "0..*",
                paramLabel = "ARGS",
                description = "Optional name, workload, and workload arguments. Docker: docker <image> port=<port> [name=<container>] [tag=<tag>] [env.<key>=<value>...]"
        )
        private List<String> args = new ArrayList<>();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            try {
                respond(spec, create());
            } catch (Exception e) {
                respond(spec, "Error creating compute unit: " + e.getMessage());
            }
        }

        private String create() {
            String unitName = name;
            Workload workload = null;
            if (!args.isEmpty()) {
                if (name != null || isWorkloadSpec(args.get(0))) {
                    workload = parseWorkload(args, 0);
                } else {
                    unitName = args.get(0);
                    if (args.size() > 1) {
                        workload = parseWorkload(args, 1);
                    }
                }
            }

            ComputeUnitBuilder builder = new ComputeUnitBuilder()
                    .workload(workload)
                    .name(unitName);
            if (capacity != null) {
                builder.capacity(capacity);
            }

            ComputeUnit cu = builder.build();
            return "ComputeUnit created successfully. ID: " + cu.getId()
                    + (unitName != null ? " [Name: " + unitName + "]" : "")
                    + " [State: " + cu.getState() + "]";
        }
    }

    @Command(
            name = "assign",
            description = "Assign a workload to an available compute unit.",
            mixinStandardHelpOptions = true
    )
    public static class AssignCommand implements Runnable {
        @Parameters(
                arity = "1..*",
                paramLabel = "ARGS",
                description = "<unitId> [jar|js] <filePath> [args...] | <unitId> docker <image> port=<port> [name=<container>] [tag=<tag>] [env.<key>=<value>...]"
        )
        private List<String> args = new ArrayList<>();

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            if (args.size() < 2) {
                respond(spec, "Usage: cld cu assign <unitId> [jar|js] <filePath> [args...]");
                return;
            }

            String unitId = args.get(0);
            ComputeUnit cu = findUnit(unitId);
            if (cu == null) {
                respond(spec, "Error: ComputeUnit not found with ID: " + unitId);
                return;
            }

            try {
                Workload workload = parseWorkload(args, 1);
                cu.assignWorkload(workload);
                respond(spec, "Workload assigned to ComputeUnit " + unitId + ". State: " + cu.getState());
            } catch (Exception e) {
                respond(spec, "Error assigning workload: " + e.getMessage());
            }
        }
    }

    @Command(
            name = "run",
            aliases = "execute",
            description = "Execute the workload assigned to a compute unit.",
            mixinStandardHelpOptions = true
    )
    public static class RunCommand implements Runnable {
        @Parameters(index = "0", paramLabel = "unitId")
        private String unitId;

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            ComputeUnit cu = findUnit(unitId);
            if (cu == null) {
                respond(spec, "Error: ComputeUnit not found with ID: " + unitId);
                return;
            }

            try {
                String pid = cu.executeWorkload();
                respond(spec, "ComputeUnit " + unitId + " running with runtime ID: " + pid + " [State: " + cu.getState() + "]");
            } catch (Exception e) {
                respond(spec, "Error executing compute unit: " + e.getMessage());
            }
        }
    }

    @Command(
            name = "stop",
            aliases = "terminate",
            description = "Stop a compute unit's active workload.",
            mixinStandardHelpOptions = true
    )
    public static class StopCommand implements Runnable {
        @Parameters(index = "0", paramLabel = "unitId")
        private String unitId;

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            ComputeUnit cu = findUnit(unitId);
            if (cu == null) {
                respond(spec, "Error: ComputeUnit not found with ID: " + unitId);
                return;
            }

            try {
                cu.terminateWorkload();
                respond(spec, "ComputeUnit " + unitId + " workload stopped. [State: " + cu.getState() + "]");
            } catch (Exception e) {
                respond(spec, "Error stopping compute unit: " + e.getMessage());
            }
        }
    }

    @Command(
            name = "release",
            description = "Release a compute unit's assigned workload.",
            mixinStandardHelpOptions = true
    )
    public static class ReleaseCommand implements Runnable {
        @Parameters(index = "0", paramLabel = "unitId")
        private String unitId;

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            ComputeUnit cu = findUnit(unitId);
            if (cu == null) {
                respond(spec, "Error: ComputeUnit not found with ID: " + unitId);
                return;
            }

            try {
                cu.releaseWorkload();
                respond(spec, "ComputeUnit " + unitId + " workload released. [State: " + cu.getState() + "]");
            } catch (Exception e) {
                respond(spec, "Error releasing compute unit: " + e.getMessage());
            }
        }
    }

    @Command(
            name = "kill",
            description = "Terminate and remove a compute unit.",
            mixinStandardHelpOptions = true
    )
    public static class KillCommand implements Runnable {
        @Parameters(index = "0", paramLabel = "unitId")
        private String unitId;

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            ComputeUnit cu = findUnit(unitId);
            if (cu == null) {
                respond(spec, "Error: ComputeUnit not found with ID: " + unitId);
                return;
            }

            try {
                cu.killInstance();
                respond(spec, "ComputeUnit " + unitId + " killed and removed from registry.");
            } catch (Exception e) {
                respond(spec, "Error killing compute unit: " + e.getMessage());
            }
        }
    }

    @Command(
            name = "status",
            description = "Display a compute unit's state and workload.",
            mixinStandardHelpOptions = true
    )
    public static class StatusCommand implements Runnable {
        @Parameters(index = "0", paramLabel = "unitId")
        private String unitId;

        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            ComputeUnit cu = findUnit(unitId);
            if (cu == null) {
                respond(spec, "Error: ComputeUnit not found with ID: " + unitId);
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("ComputeUnit: ").append(cu.getId()).append("\n");
            sb.append("  State:      ").append(cu.getState()).append("\n");
            sb.append("  Runtime ID: ").append(cu.getRuntimeInstanceId() != null ? cu.getRuntimeInstanceId() : "N/A").append("\n");
            sb.append("  Created:    ").append(cu.getCreatedAt()).append("\n");
            sb.append("  Workload:   ").append(describeWorkload(cu.getWorkload()));
            respond(spec, sb.toString());
        }
    }

    @Command(
            name = "list",
            description = "List all active compute units.",
            mixinStandardHelpOptions = true
    )
    public static class ListCommand implements Runnable {
        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            Map<String, ComputeUnit> allUnits = Memdb.getAll();
            Map<UUID, ComputeUnit> uniqueUnits = new LinkedHashMap<>();
            for (ComputeUnit cu : allUnits.values()) {
                uniqueUnits.put(cu.getId(), cu);
            }

            if (uniqueUnits.isEmpty()) {
                respond(spec, "No compute units registered.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Active Compute Units (").append(uniqueUnits.size()).append("):\n");
            for (ComputeUnit cu : uniqueUnits.values()) {
                String name = cu.getName() == null || cu.getName().isBlank() ? "N/A" : cu.getName();
                sb.append(String.format("  [%s] Name: %-20s State: %-10s Runtime ID: %-20s Workload: %s\n",
                        cu.getId(),
                        name,
                        cu.getState(),
                        cu.getRuntimeInstanceId() != null ? cu.getRuntimeInstanceId() : "N/A",
                        describeWorkload(cu.getWorkload())));
            }
            respond(spec, sb.toString().stripTrailing());
        }
    }

    private static ComputeUnit findUnit(String unitId) {
        return Memdb.getInstance(unitId);
    }

    private static Workload parseWorkload(List<String> args, int startIndex) {
        if (args.size() <= startIndex) {
            return null;
        }

        String typeOrPath = args.get(startIndex);
        if (typeOrPath.equalsIgnoreCase("jar")) {
            if (args.size() <= startIndex + 1) {
                throw new IllegalArgumentException("Missing jar path after 'jar'");
            }
            String jarPath = args.get(startIndex + 1);
            List<String> programArgs = new ArrayList<>(args.subList(startIndex + 2, args.size()));
            return new JarWorkload(jarPath, programArgs, List.of(), Map.of());
        }

        if (typeOrPath.equalsIgnoreCase("js")) {
            if (args.size() <= startIndex + 1) {
                throw new IllegalArgumentException("Missing file path after 'js'");
            }
            return new JSFileWorkload(args.get(startIndex + 1));
        }

        if (typeOrPath.equalsIgnoreCase("docker")) {
            return parseDockerWorkload(args, startIndex + 1);
        }

        if (typeOrPath.endsWith(".js")) {
            return new JSFileWorkload(typeOrPath);
        }

        List<String> programArgs = new ArrayList<>(args.subList(startIndex + 1, args.size()));
        return new JarWorkload(typeOrPath, programArgs, List.of(), Map.of());
    }

    private static DockerWorkload parseDockerWorkload(List<String> args, int startIndex) {
        if (args.size() <= startIndex) {
            throw new IllegalArgumentException("Missing Docker image after 'docker'");
        }

        String image = args.get(startIndex);
        String containerName = null;
        String tag = null;
        Integer port = null;
        HashMap<String, String> envVars = new HashMap<>();
        for (int i = startIndex + 1; i < args.size(); i++) {
            String setting = args.get(i);
            int separator = setting.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("Invalid Docker setting '" + setting
                        + "'. Expected port=<port>, name=<container>, tag=<tag>, or env.<key>=<value>");
            }

            String key = setting.substring(0, separator);
            String value = setting.substring(separator + 1);
            if (value.isBlank() && !key.startsWith("env.")) {
                throw new IllegalArgumentException("Docker setting '" + key + "' cannot be blank");
            }

            switch (key) {
                case "port" -> {
                    if (port != null) {
                        throw new IllegalArgumentException("Docker port may only be specified once");
                    }
                    try {
                        port = Integer.valueOf(value);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid Docker port: " + value, e);
                    }
                }
                case "name" -> {
                    if (containerName != null) {
                        throw new IllegalArgumentException("Docker container name may only be specified once");
                    }
                    containerName = value;
                }
                case "tag" -> {
                    if (tag != null) {
                        throw new IllegalArgumentException("Docker image tag may only be specified once");
                    }
                    tag = value;
                }
                default -> {
                    if (!key.startsWith("env.") || key.length() == "env.".length()) {
                        throw new IllegalArgumentException("Unknown Docker setting: " + key);
                    }
                    String envKey = key.substring("env.".length());
                    if (envVars.putIfAbsent(envKey, value) != null) {
                        throw new IllegalArgumentException("Docker environment variable may only be specified once: "
                                + envKey);
                    }
                }
            }
        }

        if (port == null) {
            throw new IllegalArgumentException("Missing required Docker setting: port=<port>");
        }
        return new DockerWorkload(image, containerName, tag, port, envVars);
    }

    private static boolean isWorkloadSpec(String value) {
        return value.equalsIgnoreCase("jar")
                || value.equalsIgnoreCase("js")
                || value.equalsIgnoreCase("docker")
                || value.endsWith(".jar")
                || value.endsWith(".js");
    }

    private static String describeWorkload(Workload workload) {
        if (workload == null) {
            return "None";
        }
        if (workload instanceof JarWorkload jar) {
            return "JAR: " + jar.jarPath();
        }
        if (workload instanceof JSFileWorkload js) {
            return "JS: " + js.filePath();
        }
        if (workload instanceof DockerWorkload docker) {
            String image = docker.dockerImage()
                    + (docker.tag() == null || docker.tag().isBlank() ? "" : ":" + docker.tag());
            return "Docker: " + image + " (port " + docker.port() + ")";
        }
        return workload.getClass().getSimpleName();
    }

    private static void respond(CommandSpec spec, String message) {
        spec.commandLine().getOut().println(message);
    }
}
