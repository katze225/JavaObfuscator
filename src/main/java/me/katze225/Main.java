package me.katze225;

import me.katze225.object.ObfuscatorSettings;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2 || isHelp(args[0])) {
            printUsage("Missing input and output arguments.");
            return;
        }

        ObfuscatorSettings settings = parseArgs(args);
        if (settings == null) {
            printUsage("Incorrect arguments.");
            return;
        }

        Obfuscator obfuscator = new Obfuscator(settings);

        System.out.println("// by @core2k21 (tg)");
        System.out.println("Starting... ");
        obfuscator.run();

        System.out.println("Done!");
    }

    private static ObfuscatorSettings parseArgs(String[] args) {
        if (args.length < 2) {
            return null;
        }

        String inputPath = args[0];
        String outputPath = args[1];

        int namesLength = 40;
        String zipCommentText = "by @core2k21 (tg)";

        boolean anyTransformerSpecified = false;

        boolean enabledNumbers = false;
        boolean enabledStrings = false;
        boolean enabledBooleans = false;
        boolean enabledFlow = false;
        boolean enabledDispatcher = false;
        boolean enabledShuffle = false;
        boolean enabledZipComment = false;

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg == null || arg.trim().isEmpty()) {
                continue;
            }

            if (isHelp(arg)) {
                return null;
            }

            if (!arg.startsWith("--")) continue;

            // аргументы с параметрами
            if (arg.equalsIgnoreCase("--names-length")) {
                if (i + 1 >= args.length) return null;

                try {
                    int parsedNum = Integer.parseInt(args[i++]);
                    if (parsedNum <= 0) return null;
                    namesLength = parsedNum;
                } catch (NumberFormatException e) {
                    printUsage("Invalid number for --names-length");
                    return null;
                }
            } else if (arg.equalsIgnoreCase("--zip-comment-text")) {
                if (i + 1 >= args.length) return null;

                String parsedStr = args[i++];
                if (parsedStr == null || parsedStr.trim().isEmpty()) return null;
                zipCommentText = parsedStr;
            }

            // без параметров
            if (arg.equalsIgnoreCase("--numbers")) {
                enabledNumbers = true;
                anyTransformerSpecified = true;
            } else if (arg.equalsIgnoreCase("--strings")) {
                enabledStrings = true;
                anyTransformerSpecified = true;
            } else if (arg.equalsIgnoreCase("--booleans")) {
                enabledBooleans = true;
                anyTransformerSpecified = true;
            } else if (arg.equalsIgnoreCase("--flow")) {
                enabledFlow = true;
                anyTransformerSpecified = true;
            } else if (arg.equalsIgnoreCase("--dispatcher")) {
                enabledDispatcher = true;
                anyTransformerSpecified = true;
            } else if (arg.equalsIgnoreCase("--shuffle")) {
                enabledShuffle = true;
                anyTransformerSpecified = true;
            } else if (arg.equalsIgnoreCase("--zip-comment")) {
                enabledZipComment = true;
                anyTransformerSpecified = true;
            }
        }

        boolean enableAll = !anyTransformerSpecified;

        return new ObfuscatorSettings(
                "none",
                namesLength,
                inputPath,
                outputPath,
                enableAll || enabledNumbers,
                enableAll || enabledStrings,
                enableAll || enabledBooleans,
                enableAll || enabledFlow,
                enableAll || enabledDispatcher,
                enableAll || enabledShuffle,
                enableAll || enabledZipComment,
                zipCommentText
        );
    }

    private static boolean isHelp(String command) {
        if (command == null) return false;
        return "--help".equals(command) || "-h".equals(command) || "-help".equals(command);
    }

    private static void printUsage(String error) {
        if (error != null && !error.trim().isEmpty()) {
            System.err.println(error);
        }
        System.out.println("Usage: java -jar JavaObfuscator.jar <input.jar> <output.jar> [options]");
        System.out.println("Options:");
        System.out.println("  --names-length <n>      Name length for generated identifiers (default 40)");
        System.out.println("  --numbers               NumberObfuscation");
        System.out.println("  --strings               StringEncryption");
        System.out.println("  --booleans              Booleans");
        System.out.println("  --flow                  Flow (complex boolean return expressions)");
        System.out.println("  --dispatcher            Dispatcher");
        System.out.println("  --shuffle               Shuffle");
        System.out.println("  --zip-comment           ZipComment (jar comment)");
        System.out.println("  --zip-comment-text <t>  Zip comment text");
        System.out.println("If no transformer options are provided, all transformers are enabled.");
    }
}