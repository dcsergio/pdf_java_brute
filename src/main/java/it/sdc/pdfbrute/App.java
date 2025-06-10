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
import java.util.concurrent.atomic.AtomicLong;

public class App {

    static final long SCRAMBLE_SEED = 42;
    static final int WARMUP_ATTEMPTS = 1000;
    static volatile boolean found = false;
    static AtomicLong attemptCounter = new AtomicLong(0);

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
                            "[--prefixfile prefix.txt] [--suffixfile suffix.txt] [--min %d] [--max %d] [--chars %s] [--threads %d]%n",
                    minLen, maxLen, new String(allowedChars), numThreads);
            return;
        }

        char[] scrambledChars = enableScrambling ? shuffleCharArray(allowedChars, SCRAMBLE_SEED) : allowedChars;
        List<String> prefixes = loadFile(prefixFile);
        List<String> suffixes = loadFile(suffixFile);

        // Calculate total combinations
        long totalCombinations = calculateTotalCombinations(prefixes, suffixes, minLen, maxLen, scrambledChars.length);

        System.out.println("=== PDF Brute Force Attack ===");
        System.out.println("Input file: " + inputPath);
        System.out.println("Output file: " + outputPath);
        System.out.println("Password length range: " + minLen + "-" + maxLen);
        System.out.println("Character set: " + new String(scrambledChars));
        System.out.println("Threads: " + numThreads);
        System.out.println("Total combinations to try: " + String.format("%,d", totalCombinations));
        System.out.println();

        // Perform warm-up test
        System.out.println("Performing warm-up test with " + WARMUP_ATTEMPTS + " attempts...");
        double avgTimePerAttempt = performWarmup(inputPath, scrambledChars, prefixes, suffixes, minLen);

        if (avgTimePerAttempt < 0) {
            System.err.println("Warm-up failed. Cannot proceed.");
            return;
        }

        // Calculate time estimates
        double worstCaseSeconds = totalCombinations * avgTimePerAttempt;
        double estimatedSeconds = worstCaseSeconds / 2.0; // Average case (50% of worst case)

        System.out.printf("Average time per attempt: %.3f ms%n", avgTimePerAttempt * 1000);
        System.out.println("Time estimates:");
        System.out.println("  Worst case: " + formatTime(worstCaseSeconds));
        System.out.println("  Estimated (average): " + formatTime(estimatedSeconds));
        System.out.println();

        // Ask user for confirmation
        if (!getUserConfirmation()) {
            System.out.println("Operation cancelled by user.");
            return;
        }

        // Proceed with actual brute force attack
        System.out.println("Starting brute force attack...");
        long start = System.currentTimeMillis();

        try (ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
            for (String prefix : prefixes) {
                System.out.println("prefix: " + prefix);
                for (String suffix : suffixes) {
                    System.out.println("suffix: " + suffix);
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
            System.out.println("Password not found after trying all combinations.");
        }
    }

    public static double performWarmup(String inputPath, char[] allowedChars,
                                       List<String> prefixes, List<String> suffixes, int minLen) {
        System.out.println("Generating warm-up passwords...");
        List<String> warmupPasswords = generateWarmupPasswords(allowedChars, prefixes, suffixes, minLen, WARMUP_ATTEMPTS);

        System.out.println("Testing " + warmupPasswords.size() + " passwords...");
        long startTime = System.nanoTime();

        for (String password : warmupPasswords) {
            if (tryPasswordWarmup(inputPath, password)) {
                System.out.println("Password found during warm-up: " + password);
                found = true;
                return -1; // Password found, no need to continue
            }
        }

        long endTime = System.nanoTime();
        double totalTimeSeconds = (endTime - startTime) / 1_000_000_000.0;
        double avgTimePerAttempt = totalTimeSeconds / warmupPasswords.size();

        System.out.printf("Warm-up completed: %d attempts in %.2f seconds%n", warmupPasswords.size(), totalTimeSeconds);

        return avgTimePerAttempt;
    }

    public static List<String> generateWarmupPasswords(char[] allowedChars, List<String> prefixes,
                                                       List<String> suffixes, int minLen, int maxAttempts) {
        List<String> passwords = new ArrayList<>();
        Random random = new Random(SCRAMBLE_SEED);

        while (passwords.size() < maxAttempts) {
            String prefix = prefixes.get(random.nextInt(prefixes.size()));
            String suffix = suffixes.get(random.nextInt(suffixes.size()));

            // Random length between minLen and a reasonable upper bound
            int length = minLen + random.nextInt(Math.min(3, 8 - minLen));

            StringBuilder body = new StringBuilder();
            for (int i = 0; i < length; i++) {
                body.append(allowedChars[random.nextInt(allowedChars.length)]);
            }

            passwords.add(prefix + body.toString() + suffix);
        }

        return passwords;
    }

    public static boolean tryPasswordWarmup(String inputPath, String password) {
        try (PDDocument document = PDDocument.load(new File(inputPath), password)) {
            return true; // Password is correct
        } catch (InvalidPasswordException e) {
            return false; // Wrong password
        } catch (Exception e) {
            return false; // Other error
        }
    }

    public static long calculateTotalCombinations(List<String> prefixes, List<String> suffixes,
                                                  int minLen, int maxLen, int charsetSize) {
        long total = 0;
        for (int len = minLen; len <= maxLen; len++) {
            total += (long) Math.pow(charsetSize, len);
        }
        return total * prefixes.size() * suffixes.size();
    }

    public static String formatTime(double seconds) {
        if (seconds < 60) {
            return String.format("%.1f seconds", seconds);
        } else if (seconds < 3600) {
            return String.format("%.1f minutes", seconds / 60);
        } else if (seconds < 86400) {
            return String.format("%.1f hours", seconds / 3600);
        } else if (seconds < 31536000) {
            return String.format("%.1f days", seconds / 86400);
        } else {
            return String.format("%.1f years", seconds / 31536000);
        }
    }

    public static boolean getUserConfirmation() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Do you want to proceed with the brute force attack? (y/n): ");
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.equals("y") || input.equals("yes")) {
                return true;
            } else if (input.equals("n") || input.equals("no")) {
                return false;
            } else {
                System.out.println("Please enter 'y' or 'n'.");
            }
        }
    }

    public static List<String> loadFile(String filePath) {
        if (filePath == null) return List.of("");
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8);
            lines.forEach(line -> System.out.println("line: " + line));
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
            long currentAttempts = attemptCounter.incrementAndGet();

            // Show progress every 10000 attempts
            if (currentAttempts % 10000 == 0) {
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                System.out.printf("Attempts: %,d | Time elapsed: %.1f seconds%n", currentAttempts, elapsed);
                System.out.println("pw: " + attempt);
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
            long totalAttempts = attemptCounter.get();
            System.out.println("\n=== SUCCESS ===");
            System.out.println("PDF decrypted successfully!");
            System.out.println("Password: " + password);
            System.out.printf("Total attempts: %,d%n", totalAttempts);
            System.out.printf("Time taken: %.2f seconds%n", elapsed);
            return true;
        } catch (InvalidPasswordException e) {
            return false;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return false;
        }
    }
}