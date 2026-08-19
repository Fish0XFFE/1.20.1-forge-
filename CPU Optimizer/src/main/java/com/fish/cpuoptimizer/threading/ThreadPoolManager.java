package com.fish.cpuoptimizer.threading;

import com.fish.cpuoptimizer.CpuOptimizerMod;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public class ThreadPoolManager {
    // ===== 使用所有逻辑核心（包括超线程） =====
    private static final int ALL_CORES = Runtime.getRuntime().availableProcessors();
    private static final int MAX_PARALLELISM = Math.max(ALL_CORES, 16); // 至少16线程

    static {
        CpuOptimizerMod.LOGGER.info("🚀 检测到 {} 个逻辑核心（含超线程），并行度设为 {}", ALL_CORES, MAX_PARALLELISM);
        // 尝试将 Java 的 ForkJoinPool 公用池并行度也拉满
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", String.valueOf(MAX_PARALLELISM));
    }

    // 用于 I/O 密集型任务（区块保存、网络）
    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(MAX_PARALLELISM / 2, 4),
            r -> {
                Thread t = new Thread(r, "CpuOpt-IO");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 2);
                return t;
            }
    );

    // 用于 CPU 密集型计算（区块光照、实体碰撞、AI 计算）
    private static final ForkJoinPool COMPUTE_POOL = new ForkJoinPool(
            MAX_PARALLELISM,
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            (thread, ex) -> CpuOptimizerMod.LOGGER.error("计算池异常", ex),
            true // 启用异步模式
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