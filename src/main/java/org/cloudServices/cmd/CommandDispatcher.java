package org.cloudServices.cmd;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cloudServices.shared.annotations.CLDAction;
import org.cloudServices.shared.annotations.CLDCommand;
import org.reflections.Reflections;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class CommandDispatcher {
    private static final Logger logger = LogManager.getLogger(CommandDispatcher.class);
    private static final Map<String, Map<String, CommandMethod>> registry = new HashMap<>();

    static class CommandMethod {
        Object instance;
        Method method;

        public CommandMethod(Object instance, Method method) {
            this.instance = instance;
            this.method = method;
        }
    }

    public static void init() {
        Reflections reflections = new Reflections("org.cloudServices");
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(CLDCommand.class);

        for (Class<?> clazz : classes) {
            try {
                CLDCommand cldCommand = clazz.getAnnotation(CLDCommand.class);
                String commandName = cldCommand.value().toLowerCase();

                Object controllerInstance = clazz.getDeclaredConstructor().newInstance();
                Map<String, CommandMethod> actionMap = registry.computeIfAbsent(commandName, k -> new HashMap<>());

                for (Method method : clazz.getDeclaredMethods()) {
                    CLDAction actionAnnotation = method.getAnnotation(CLDAction.class);
                    if (actionAnnotation == null) {
                        continue;
                    }
                    String actionName = actionAnnotation.value().toLowerCase();
                    actionMap.put(actionName, new CommandMethod(controllerInstance, method));
                }
                logger.info("Registered controller for command: " + commandName);

            } catch (Exception e) {
                logger.error("Failed to register controller: " + clazz.getName(), e);
            }
        }
    }

    public static String dispatch(String inputLine) {
        String[] parts = inputLine.trim().split("\\s+");
        if (parts.length < 3 || !parts[0].equalsIgnoreCase("cld")) {
            return "Invalid command format. Expected: cld <command> <action> [args]";
        }

        String commandName = parts[1].toLowerCase();
        String actionName = parts[2].toLowerCase();

        Map<String, CommandMethod> actionMap = registry.get(commandName);
        if (actionMap == null) {
            return "Unknown command: " + commandName;
        }

        CommandMethod cmdMethod = actionMap.get(actionName);
        if (cmdMethod == null) {
            return "Unknown action '" + actionName + "' for command '" + commandName + "'";
        }

        try {
            Object result = cmdMethod.method.invoke(cmdMethod.instance, (Object) parts);
            return result != null ? result.toString() : "Success";
        } catch (Exception e) {
            logger.error("Error executing command", e);
            Throwable cause = e.getCause();
            return "Error executing command: " + (cause != null ? cause.getMessage() : e.getMessage());
        }
    }
}
