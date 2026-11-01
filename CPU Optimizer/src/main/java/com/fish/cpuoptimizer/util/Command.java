package com.fish.cpuoptimizer.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class Command {
    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    public static final boolean WINDOWS = OS.contains("win");
    public static final boolean LINUX = OS.contains("linux");
    public static final boolean MAC = OS.contains("mac");

    private Command() {
    }

    public static String run(long timeoutMillis, String... args) {
        Process process = null;
        try {
            process = new ProcessBuilder(args).redirectErrorStream(true).start();
            Process target = process;
            Thread watchdog = new Thread(() -> {
                try {
                    if (!target.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                        target.destroyForcibly();
                    }
                } catch (InterruptedException ignored) {
                }
            }, "cpuoptimizer-command-timeout");
            watchdog.setDaemon(true);
            watchdog.setPriority(Thread.MIN_PRIORITY);
            watchdog.start();
            InputStream stream = process.getInputStream();
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            watchdog.interrupt();
            return text;
        } catch (Throwable t) {
            if (process != null) {
                process.destroyForcibly();
            }
            return "";
        }
    }

    public static void fire(long timeoutMillis, String... args) {
        run(timeoutMillis, args);
    }
}
