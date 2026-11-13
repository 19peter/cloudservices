package org.cloudServices.cmd;

import org.cloudServices.cmd.controllers.ComputeUnitController;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandDispatcher {

    @Command(
            name = "cld",
            description = "Manage cloud service compute units.",
            subcommands = ComputeUnitController.class,
            mixinStandardHelpOptions = true
    )
    static class RootCommand implements Runnable {
        @Spec
        private CommandSpec spec;

        @Override
        public void run() {
            spec.commandLine().usage(spec.commandLine().getOut());
        }
    }

    public static String dispatch(String inputLine) {
        final String[] parts;
        try {
            parts = tokenize(inputLine);
        } catch (IllegalArgumentException e) {
            return "Invalid command line: " + e.getMessage();
        }

        if (parts.length == 0 || !parts[0].equalsIgnoreCase("cld")) {
            return "Invalid command format. Expected: cld <command> <action> [args]";
        }

        StringWriter response = new StringWriter();
        PrintWriter writer = new PrintWriter(response);
        CommandLine commandLine = new CommandLine(new RootCommand());
        commandLine.setOut(writer);
        commandLine.setErr(writer);
        commandLine.setParameterExceptionHandler((exception, arguments) -> {
            writer.println("Error: " + exception.getMessage());
            writer.print(exception.getCommandLine().getUsageMessage());
            return 2;
        });
        commandLine.setExecutionExceptionHandler((exception, command, parseResult) -> {
            writer.println("Error: " + exception.getMessage());
            return 1;
        });

        int exitCode = commandLine.execute(Arrays.copyOfRange(parts, 1, parts.length));
        writer.flush();
        String output = response.toString().stripTrailing();
        if (!output.isEmpty()) {
            return output;
        }
        return exitCode == 0 ? "Success" : "Command failed";
    }

    private static String[] tokenize(String inputLine) {
        List<String> arguments = new ArrayList<>();
        StringBuilder argument = new StringBuilder();
        char quote = 0;
        boolean argumentStarted = false;

        for (int i = 0; i < inputLine.length(); i++) {
            char current = inputLine.charAt(i);
            if (current == '\\' && i + 1 < inputLine.length()) {
                char next = inputLine.charAt(i + 1);
                if (next == '\\' || next == '"' || next == '\'' || Character.isWhitespace(next)) {
                    argument.append(next);
                    argumentStarted = true;
                    i++;
                    continue;
                }
            }

            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                } else {
                    argument.append(current);
                }
                argumentStarted = true;
            } else if (current == '"' || current == '\'') {
                quote = current;
                argumentStarted = true;
            } else if (Character.isWhitespace(current)) {
                if (argumentStarted) {
                    arguments.add(argument.toString());
                    argument.setLength(0);
                    argumentStarted = false;
                }
            } else {
                argument.append(current);
                argumentStarted = true;
            }
        }

        if (quote != 0) {
            throw new IllegalArgumentException("Unterminated quoted argument");
        }
        if (argumentStarted) {
            arguments.add(argument.toString());
        }
        return arguments.toArray(new String[0]);
    }
}
