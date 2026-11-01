package com.fish.cpuoptimizer.crash;

import com.fish.cpuoptimizer.Tuning;
import com.fish.cpuoptimizer.Workers;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

public final class CrashWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final long[] DELAYS = {10000L, 20000L, 90000L, 180000L, 360000L};
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
    private static int counter;

    private CrashWatch() {
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Workers.submit(() -> {
            try {
                long elapsed = 0L;
                for (int i = 0; i < DELAYS.length; i++) {
                    Workers.sleep(DELAYS[i] - elapsed);
                    elapsed = DELAYS[i];
                    if (!Tuning.crashGuard) {
                        return;
                    }
                    wrapAll();
                }
            } catch (Throwable ignored) {
            }
        });
    }

    public static void wrapAll() {
        try {
            ThreadGroup root = Thread.currentThread().getThreadGroup();
            while (root.getParent() != null) {
                root = root.getParent();
            }
            Thread[] threads = new Thread[root.activeCount() + 64];
            int count = root.enumerate(threads, true);
            for (int i = 0; i < count; i++) {
                Thread thread = threads[i];
                if (thread == null || thread == Thread.currentThread()) {
                    continue;
                }
                String name = thread.getName();
                if (name == null || name.startsWith("cpuoptimizer-")) {
                    continue;
                }
                Thread.UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
                if (handler instanceof Guard) {
                    continue;
                }
                thread.setUncaughtExceptionHandler(new Guard(handler));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void capture(Thread thread, Throwable error) {
        try {
            if (!Tuning.crashGuard || !(error instanceof ReportedException)) {
                return;
            }
            CrashReport report = ((ReportedException) error).getReport();
            if (report == null) {
                return;
            }
            Path directory = FMLPaths.GAMEDIR.get().resolve("crash-reports");
            Files.createDirectories(directory);
            String stamp = FORMAT.format(LocalDateTime.now());
            synchronized (CrashWatch.class) {
                counter++;
            }
            Path file = directory.resolve("crash-" + stamp + "-cpuoptimizer-" + counter + ".txt");
            Files.writeString(file, report.getFriendlyReport(), StandardCharsets.UTF_8);
            LOGGER.error("CPU Optimizer: {} 线程崩溃, 崩溃报告已补写到 {}", thread.getName(), file);
        } catch (Throwable ignored) {
        }
    }

    private static final class Guard implements Thread.UncaughtExceptionHandler {
        private final Thread.UncaughtExceptionHandler delegate;

        private Guard(Thread.UncaughtExceptionHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable error) {
            capture(thread, error);
            if (delegate != null) {
                try {
                    delegate.uncaughtException(thread, error);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
