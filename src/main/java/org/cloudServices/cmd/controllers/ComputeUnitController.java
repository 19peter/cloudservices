package org.cloudServices.cmd.controllers;

import org.cloudServices.builder.ComputeUnitBuilder;
import org.cloudServices.global.db.Memdb;
import org.cloudServices.infra.computeUnit.ComputeUnit;
import org.cloudServices.infra.computeUnit.ComputeUnitCapacity;
import org.cloudServices.infra.runtimes.JSRuntime.JSFileWorkload;
import org.cloudServices.infra.runtimes.JarRuntime.JarWorkload;
import org.cloudServices.infra.runtimes.Workload;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        @Parameters(arity = "0..*", paramLabel = "ARGS", description = "Optional name, workload, and workload arguments.")
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
        @Parameters(arity = "1..*", paramLabel = "ARGS", description = "<unitId> [jar|js] <filePath> [args...]")
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
                respond(spec, "ComputeUnit " + unitId + " running with PID: " + pid + " [State: " + cu.getState() + "]");
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
            sb.append("  PID:        ").append(cu.getPid() != null ? cu.getPid() : "N/A").append("\n");
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
            if (allUnits.isEmpty()) {
                respond(spec, "No compute units registered.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Active Compute Units (").append(allUnits.size()).append("):\n");
            for (ComputeUnit cu : allUnits.values()) {
                sb.append(String.format("  [%s] State: %-10s PID: %-8s Workload: %s\n",
                        cu.getId(),
                        cu.getState(),
                        cu.getPid() != null ? cu.getPid() : "N/A",
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

        if (typeOrPath.endsWith(".js")) {
            return new JSFileWorkload(typeOrPath);
        }

        List<String> programArgs = new ArrayList<>(args.subList(startIndex + 1, args.size()));
        return new JarWorkload(typeOrPath, programArgs, List.of(), Map.of());
    }

    private static boolean isWorkloadSpec(String value) {
        return value.equalsIgnoreCase("jar")
                || value.equalsIgnoreCase("js")
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
        return workload.getClass().getSimpleName();
    }

    private static void respond(CommandSpec spec, String message) {
        spec.commandLine().getOut().println(message);
    }
}
