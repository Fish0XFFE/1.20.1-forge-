package com.fish.cpuoptimizer.threading;

import com.fish.cpuoptimizer.CpuOptimizerMod;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadPoolManager {
    private static final boolean IS_SERVER = !System.getProperty("sun.java.command", "").contains("client");
    private static final int POOL_SIZE = IS_SERVER ? 2 : Math.max(4, Runtime.getRuntime().availableProcessors() / 2);

    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(POOL_SIZE, r -> {
        Thread t = new Thread(r, "CpuOpt-Background");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
    });

    public static void submitIOTask(Runnable task) {
        IO_EXECUTOR.submit(task);
    }

    public static void submitComputeTask(Runnable task) {
        IO_EXECUTOR.submit(task);
    }

    public static void shutdown() {
        IO_EXECUTOR.shutdown();
        try {
            if (!IO_EXECUTOR.awaitTermination(2, TimeUnit.SECONDS)) {
                IO_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            IO_EXECUTOR.shutdownNow();
        }
    }
}