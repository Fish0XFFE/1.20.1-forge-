package com.fish.cpuoptimizer;

import com.fish.cpuoptimizer.threading.ThreadPoolManager;
import net.minecraft.server.MinecraftServer;

public class CacheCleaner {
    private static int tickCounter = 0;
    private static long lastGcTime = 0;
    private static volatile boolean gcQueued = false;

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (++tickCounter < 20 * 45) return;
        tickCounter = 0;

        long now = System.currentTimeMillis();
        if (now - lastGcTime < 45000) return;

        queueGc(1500);
    }

    public static void forceClean() {
        queueGc(500);
    }

    private static void queueGc(long delayMs) {
        if (gcQueued) return;
        gcQueued = true;
        ThreadPoolManager.submitIOTask(() -> {
            try {
                Thread.sleep(delayMs);
                System.gc();
                Thread.sleep(30);
                System.runFinalization();
                Thread.sleep(10);
                System.gc();
                lastGcTime = System.currentTimeMillis();
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                CpuOptimizerMod.LOGGER.warn("后台GC异常，请自行查看有没有毛病: {}", e.getMessage());
            } finally {
                gcQueued = false;
            }
        });
    }
}