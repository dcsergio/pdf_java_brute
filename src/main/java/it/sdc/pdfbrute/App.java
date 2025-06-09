package it.sdc.pdfbrute;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {

    static final long SCRAMBLE_SEED = 42;
    static volatile long attempts = 0;
    static volatile boolean found = false;

    public static void main(String[] args) {
        boolean enableScrambling = true;
        String inputPath = null;
        String outputPath = null;
        int minLen = 1;
        int maxLen = 5;
        char[] allowedChars = "abc123".toCharArray();
        int numThreads = Runtime.getRuntime().availableProcessors() - 1;
        String prefixFile = "prefix.txt";
        String suffixFile = "suffix.txt";

        // Parse command-line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input":
                    inputPath = args[++i];
                    break;
                case "--output":
                    outputPath = args[++i];
                    break;
                case "--min":
                    minLen = Integer.parseInt(args[++i]);
                    break;
                case "--max":
                    maxLen = Integer.parseInt(args[++i]);
                    break;
                case "--chars":
                    allowedChars = args[++i].toCharArray();
                    break;
                case "--threads":
                    numThreads = Integer.parseInt(args[++i]);
                    break;
                case "--scramble":
                    enableScrambling = Boolean.parseBoolean(args[++i]);
                    break;
                case "--prefixfile":
                    prefixFile = args[++i];
                    break;
                case "--suffixfile":
                    suffixFile = args[++i];
                    break;
                default:
                    System.err.println("Unknown parameter: " + args[i]);
                    return;
            }
        }

        if (inputPath == null || outputPath == null) {
            System.err.printf("Usage: java -jar app.jar --input <file.pdf> --output <output.pdf> " +
                    "[--prefixfile prefix.txt] [--suffixfile suffix.txt] [--min 1] [--max 5] [--chars abc123] [--threads %d]%n", numThreads);
            return;
        }

        char[] scrambledChars = enableScrambling ? shuffleCharArray(allowedChars, SCRAMBLE_SEED) : allowedChars;
        List<String> prefixes = loadFile(prefixFile);
        List<String> suffixes = loadFile(suffixFile);

        long start = System.currentTimeMillis();
        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            for (String prefix : prefixes) {
                for (String suffix : suffixes) {
                    for (int len = minLen; len <= maxLen; len++) {
                        int bodyLength = len;
                        for (char c : scrambledChars) {
                            final char firstChar = c;
                            String finalInputPath = inputPath;
                            String finalOutputPath = outputPath;
                            executor.submit(() -> {
                                char[] body = new char[bodyLength];
                                body[0] = firstChar;
                                bruteForceRecursive(finalInputPath, finalOutputPath, body, 1, scrambledChars, prefix, suffix, start);
                            });
                        }
                    }
                }
            }
            executor.shutdown();
        }

        if (!found) {
            System.out.println("Password not found.");
        }
    }

    public static List<String> loadFile(String filePath) {
        if (filePath == null) return List.of("");
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8);
            return lines.isEmpty() ? List.of("") : lines;
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            return List.of("");
        }
    }

    public static void bruteForceRecursive(String inputPath, String outputPath,
                                           char[] body, int index, char[] allowedChars,
                                           String prefix, String suffix, long start) {
        if (found) return;

        if (index < body.length) {
            for (char c : allowedChars) {
                body[index] = c;
                bruteForceRecursive(inputPath, outputPath, body, index + 1, allowedChars, prefix, suffix, start);
                if (found) return;
            }
        } else {
            String attempt = prefix + new String(body) + suffix;
            long localAttempts = ++attempts;
            if (localAttempts % 5000 == 0) {
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                System.out.printf("Attempts: %d, Elapsed time: %.2f seconds, Attempt: %s%n", localAttempts, elapsed, attempt);
            }
            if (tryPassword(inputPath, outputPath, attempt, start)) {
                found = true;
            }
        }
    }

    public static char[] shuffleCharArray(char[] input, long seed) {
        List<Character> list = new ArrayList<>();
        for (char c : input) list.add(c);
        Collections.shuffle(list, new Random(seed));
        char[] shuffled = new char[input.length];
        for (int i = 0; i < shuffled.length; i++) {
            shuffled[i] = list.get(i);
        }
        return shuffled;
    }

    public static boolean tryPassword(String inputPath, String outputPath, String password, long start) {
        try (PDDocument document = PDDocument.load(new File(inputPath), password)) {
            document.setAllSecurityToBeRemoved(true);
            document.save(outputPath);
            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            System.out.println("PDF decrypted successfully!");
            System.out.println("Password: " + password);
            System.out.printf("Time taken: %.2f seconds%n", elapsed);
            System.out.println("Total attempts: " + attempts);
            return true;
        } catch (InvalidPasswordException e) {
            return false;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }
}
