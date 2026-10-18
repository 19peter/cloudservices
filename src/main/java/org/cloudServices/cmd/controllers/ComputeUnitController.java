package org.cloudServices.cmd.controllers;

import org.cloudServices.global.db.Memdb;
import org.cloudServices.infra.computeUnit.ComputeUnit;
import org.cloudServices.infra.runtimes.JSRuntime.JSFileWorkload;
import org.cloudServices.infra.runtimes.JarRuntime.JarWorkload;
import org.cloudServices.infra.runtimes.Workload;
import org.cloudServices.shared.annotations.CLDAction;
import org.cloudServices.shared.annotations.CLDCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing ComputeUnit instances via the CLI command dispatcher.
 * Handles commands starting with 'cld cu <action> [args]'.
 */
@CLDCommand("cu")
public class ComputeUnitController {

    @CLDAction("create")
    public String create(String[] args) {
        try {
            Workload workload = null;
            if (args.length >= 4) {
                workload = parseWorkload(args, 3);
            }

            ComputeUnit cu = new ComputeUnit(workload);
            return "ComputeUnit created successfully. ID: " + cu.getId()
                    + " [State: " + cu.getState() + "]";
        } catch (Exception e) {
            return "Error creating compute unit: " + e.getMessage();
        }
    }

    @CLDAction("assign")
    public String assign(String[] args) {
        if (args.length < 5) {
            return "Usage: cld cu assign <unitId> [jar|js] <filePath> [args...]";
        }

        String unitId = args[3];
        ComputeUnit cu = Memdb.getInstance(unitId);
        if (cu == null) {
            return "Error: ComputeUnit not found with ID: " + unitId;
        }

        try {
            Workload workload = parseWorkload(args, 4);
            cu.assignWorkload(workload);
            return "Workload assigned to ComputeUnit " + unitId + ". State: " + cu.getState();
        } catch (Exception e) {
            return "Error assigning workload: " + e.getMessage();
        }
    }

    @CLDAction("run")
    public String run(String[] args) {
        if (args.length < 4) {
            return "Usage: cld cu run <unitId>";
        }

        String unitId = args[3];
        ComputeUnit cu = Memdb.getInstance(unitId);
        if (cu == null) {
            return "Error: ComputeUnit not found with ID: " + unitId;
        }

        try {
            String pid = cu.executeWorkload();
            return "ComputeUnit " + unitId + " running with PID: " + pid + " [State: " + cu.getState() + "]";
        } catch (Exception e) {
            return "Error executing compute unit: " + e.getMessage();
        }
    }

    @CLDAction("execute")
    public String execute(String[] args) {
        return run(args);
    }

    @CLDAction("stop")
    public String stop(String[] args) {
        if (args.length < 4) {
            return "Usage: cld cu stop <unitId>";
        }

        String unitId = args[3];
        ComputeUnit cu = Memdb.getInstance(unitId);
        if (cu == null) {
            return "Error: ComputeUnit not found with ID: " + unitId;
        }

        try {
            cu.terminateWorkload();
            return "ComputeUnit " + unitId + " workload stopped. [State: " + cu.getState() + "]";
        } catch (Exception e) {
            return "Error stopping compute unit: " + e.getMessage();
        }
    }

    @CLDAction("terminate")
    public String terminate(String[] args) {
        return stop(args);
    }

    @CLDAction("release")
    public String release(String[] args) {
        if (args.length < 4) {
            return "Usage: cld cu release <unitId>";
        }

        String unitId = args[3];
        ComputeUnit cu = Memdb.getInstance(unitId);
        if (cu == null) {
            return "Error: ComputeUnit not found with ID: " + unitId;
        }

        try {
            cu.releaseWorkload();
            return "ComputeUnit " + unitId + " workload released. [State: " + cu.getState() + "]";
        } catch (Exception e) {
            return "Error releasing compute unit: " + e.getMessage();
        }
    }

    @CLDAction("kill")
    public String kill(String[] args) {
        if (args.length < 4) {
            return "Usage: cld cu kill <unitId>";
        }

        String unitId = args[3];
        ComputeUnit cu = Memdb.getInstance(unitId);
        if (cu == null) {
            return "Error: ComputeUnit not found with ID: " + unitId;
        }

        try {
            cu.killInstance();
            return "ComputeUnit " + unitId + " killed and removed from registry.";
        } catch (Exception e) {
            return "Error killing compute unit: " + e.getMessage();
        }
    }

    @CLDAction("status")
    public String status(String[] args) {
        if (args.length < 4) {
            return "Usage: cld cu status <unitId>";
        }

        String unitId = args[3];
        ComputeUnit cu = Memdb.getInstance(unitId);
        if (cu == null) {
            return "Error: ComputeUnit not found with ID: " + unitId;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("ComputeUnit: ").append(cu.getId()).append("\n");
        sb.append("  State:      ").append(cu.getState()).append("\n");
        sb.append("  PID:        ").append(cu.getPid() != null ? cu.getPid() : "N/A").append("\n");
        sb.append("  Created:    ").append(cu.getCreatedAt()).append("\n");
        sb.append("  Workload:   ").append(describeWorkload(cu.getWorkload()));
        return sb.toString();
    }

    @CLDAction("list")
    public String list(String[] args) {
        Map<String, ComputeUnit> allUnits = Memdb.getAll();
        if (allUnits.isEmpty()) {
            return "No compute units registered.";
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
        return sb.toString().stripTrailing();
    }

    private Workload parseWorkload(String[] args, int startIndex) {
        if (args.length <= startIndex) {
            return null;
        }

        String typeOrPath = args[startIndex];

        if (typeOrPath.equalsIgnoreCase("jar")) {
            if (args.length <= startIndex + 1) {
                throw new IllegalArgumentException("Missing jar path after 'jar'");
            }
            String jarPath = args[startIndex + 1];
            List<String> programArgs = new ArrayList<>();
            for (int i = startIndex + 2; i < args.length; i++) {
                programArgs.add(args[i]);
            }
            return new JarWorkload(jarPath, programArgs, List.of(), Map.of());
        }

        if (typeOrPath.equalsIgnoreCase("js")) {
            if (args.length <= startIndex + 1) {
                throw new IllegalArgumentException("Missing file path after 'js'");
            }
            return new JSFileWorkload(args[startIndex + 1]);
        }

        if (typeOrPath.endsWith(".js")) {
            return new JSFileWorkload(typeOrPath);
        }

        List<String> programArgs = new ArrayList<>();
        for (int i = startIndex + 1; i < args.length; i++) {
            programArgs.add(args[i]);
        }
        return new JarWorkload(typeOrPath, programArgs, List.of(), Map.of());
    }

    private String describeWorkload(Workload workload) {
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
}
