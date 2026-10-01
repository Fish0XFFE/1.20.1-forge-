package com.fish.cpuoptimizer.threading;

import com.fish.cpuoptimizer.CpuOptimizerMod;
import java.util.concurrent.*;

public class ThreadPoolManager {
    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();
    private static final int CHUNK_POOL_SIZE = Math.max(6, CPU_CORES * 3 / 4);
    private static final int IO_POOL_SIZE = Math.max(2, CPU_CORES / 8);

    private static final ForkJoinPool CHUNK_POOL = new ForkJoinPool(
            CHUNK_POOL_SIZE,
            pool -> {
                ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                t.setName("CpuOpt-Chunk-" + t.getPoolIndex());
                t.setPriority(Thread.MAX_PRIORITY);
                return t;
            },
            (t, e) -> CpuOptimizerMod.LOGGER.error("区块池异常", e),
            true
    );

    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(
            IO_POOL_SIZE,
            r -> {
                Thread t = new Thread(r, "CpuOpt-Bg-" + System.nanoTime() % 10000);
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }
    );

    public static ForkJoinPool getChunkPool() { return CHUNK_POOL; }
    public static void submitIOTask(Runnable task) { IO_EXECUTOR.submit(task); }
    public static void submitChunkTask(Runnable task) { CHUNK_POOL.submit(task); }
    public static int getChunkPoolSize() { return CHUNK_POOL_SIZE; }

    public static void shutdown() {
        CHUNK_POOL.shutdown();
        IO_EXECUTOR.shutdown();
        try {
            CHUNK_POOL.awaitTermination(3, TimeUnit.SECONDS);
            IO_EXECUTOR.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}