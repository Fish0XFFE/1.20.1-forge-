package com.fish.cpuoptimizer.threading;

import com.fish.cpuoptimizer.CpuOptimizerMod;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class ThreadPoolManager {
    private static final int ALL_CORES = Runtime.getRuntime().availableProcessors();
    // ===== 服务器端限制并发度，避免过度抢占 CPU =====
    private static final boolean IS_SERVER = !System.getProperty("sun.java.command", "").contains("client");
    private static final int MAX_PARALLELISM = IS_SERVER
            ? Math.max(2, ALL_CORES / 2)  // 服务器只用一半核心，给主线程留余地
            : Math.max(ALL_CORES, 16);     // 客户端用全部核心

    static {
        CpuOptimizerMod.LOGGER.info("🚀 检测到 {} 个逻辑核心，{} 模式并行度设为 {}",
                ALL_CORES, IS_SERVER ? "服务端" : "客户端", MAX_PARALLELISM);
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", String.valueOf(MAX_PARALLELISM));
    }

    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(MAX_PARALLELISM / 2, 2),
            r -> {
                Thread t = new Thread(r, "CpuOpt-IO");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
    );

    private static final ForkJoinPool COMPUTE_POOL = new ForkJoinPool(
            Math.max(MAX_PARALLELISM / 2, 2),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            (thread, ex) -> CpuOptimizerMod.LOGGER.error("计算池异常", ex),
            true
    );

    public static void submitIOTask(Runnable task) {
        IO_EXECUTOR.submit(task);
    }

    public static void submitComputeTask(Runnable task) {
        COMPUTE_POOL.submit(task);
    }

    public static ForkJoinPool getComputePool() {
        return COMPUTE_POOL;
    }

    public static int getParallelism() {
        return MAX_PARALLELISM;
    }
}