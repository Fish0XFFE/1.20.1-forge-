package com.fish.cpuoptimizer.memory;

import com.fish.cpuoptimizer.Tuning;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class MemoryGuard {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile long lastCompactMillis;

    private MemoryGuard() {
    }

    public static int usedPercent() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        if (max <= 0L) {
            return 0;
        }
        long used = runtime.totalMemory() - runtime.freeMemory();
        return (int) Math.min(100L, used * 100L / max);
    }

    public static long usedMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
    }

    public static long maxMb() {
        return Runtime.getRuntime().maxMemory() / 1048576L;
    }

    public static void compactIfNeeded(String reason) {
        if (!Tuning.enabled || !Tuning.compactAtLoading) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCompactMillis < Math.max(5000L, Tuning.compactIntervalMillis)) {
            return;
        }
        int percent = usedPercent();
        if (percent < Tuning.compactThreshold) {
            return;
        }
        lastCompactMillis = now;
        Runtime runtime = Runtime.getRuntime();
        long before = runtime.totalMemory() - runtime.freeMemory();
        System.gc();
        long after = runtime.totalMemory() - runtime.freeMemory();
        long freed = Math.max(0L, (before - after) / 1048576L);
        if (Tuning.logDetail) {
            LOGGER.info("CPU Optimizer: {} 内存整理, 占用 {}% -> {}%, 释放 {} MB, 堆 {} MB / 上限 {} MB",
                    reason, percent, usedPercent(), freed, after / 1048576L, runtime.maxMemory() / 1048576L);
        }
    }
}
