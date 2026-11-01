package com.fish.cpuoptimizer.stats;

import com.fish.cpuoptimizer.Tuning;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;

public final class Stats {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double SLOW_FRAME_MS = 50.0D;
    private static final double LONG_TICK_MS = 50.0D;
    private static long lastFrameNanos;
    private static long frames;
    private static double frameTotalMs;
    private static double frameMaxMs;
    private static long hitches;
    private static long clientTickStartNanos;
    private static long clientTicks;
    private static double clientTickTotalMs;
    private static double clientTickMaxMs;
    private static long serverTickStartNanos;
    private static long serverTicks;
    private static double serverTickTotalMs;
    private static double serverTickMaxMs;
    private static long serverOverruns;

    private Stats() {
    }

    public static void frameStart() {
        long now = System.nanoTime();
        long last = lastFrameNanos;
        lastFrameNanos = now;
        if (last == 0L) {
            return;
        }
        double elapsed = (now - last) / 1000000.0D;
        if (elapsed > 2000.0D) {
            return;
        }
        frames++;
        frameTotalMs += elapsed;
        if (elapsed > frameMaxMs) {
            frameMaxMs = elapsed;
        }
        if (elapsed > SLOW_FRAME_MS) {
            hitches++;
        }
    }

    public static void clientTickStart() {
        clientTickStartNanos = System.nanoTime();
    }

    public static void clientTickEnd() {
        long start = clientTickStartNanos;
        if (start == 0L) {
            return;
        }
        double elapsed = (System.nanoTime() - start) / 1000000.0D;
        if (elapsed > 1000.0D) {
            return;
        }
        clientTicks++;
        clientTickTotalMs += elapsed;
        if (elapsed > clientTickMaxMs) {
            clientTickMaxMs = elapsed;
        }
    }

    public static void serverTickStart() {
        serverTickStartNanos = System.nanoTime();
    }

    public static double serverTickEnd() {
        long start = serverTickStartNanos;
        if (start == 0L) {
            return 0.0D;
        }
        double elapsed = (System.nanoTime() - start) / 1000000.0D;
        if (elapsed > 10000.0D) {
            return 0.0D;
        }
        serverTicks++;
        serverTickTotalMs += elapsed;
        if (elapsed > serverTickMaxMs) {
            serverTickMaxMs = elapsed;
        }
        if (elapsed > LONG_TICK_MS) {
            serverOverruns++;
        }
        return elapsed;
    }

    public static void reset() {
        lastFrameNanos = 0L;
        frames = 0L;
        frameTotalMs = 0.0D;
        frameMaxMs = 0.0D;
        hitches = 0L;
        clientTickStartNanos = 0L;
        clientTicks = 0L;
        clientTickTotalMs = 0.0D;
        clientTickMaxMs = 0.0D;
        serverTickStartNanos = 0L;
        serverTicks = 0L;
        serverTickTotalMs = 0.0D;
        serverTickMaxMs = 0.0D;
        serverOverruns = 0L;
    }

    public static void report(String where) {
        try {
            if (!Tuning.logStatistics) {
                return;
            }
            List<String> parts = new ArrayList<>();
            if (frames > 0L) {
                double average = frameTotalMs / (double) frames;
                double fps = average > 0.0D ? 1000.0D / average : 0.0D;
                parts.add("帧 平均 " + format(average) + " 毫秒 (" + format(fps) + " 帧/秒), 最长 " + format(frameMaxMs)
                        + " 毫秒, 卡顿 " + hitches + " 次");
            }
            if (clientTicks > 0L) {
                parts.add("客户端 tick 平均 " + format(clientTickTotalMs / (double) clientTicks) + " 毫秒, 最长 "
                        + format(clientTickMaxMs) + " 毫秒");
            }
            if (serverTicks > 0L) {
                parts.add("服务端 tick 平均 " + format(serverTickTotalMs / (double) serverTicks) + " 毫秒, 超时 "
                        + serverOverruns + " 次, 最长 " + format(serverTickMaxMs) + " 毫秒");
            }
            if (parts.isEmpty()) {
                return;
            }
            LOGGER.info("CPU Optimizer 性能统计[{}]: {}", where, String.join(" | ", parts));
        } catch (Throwable ignored) {
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
