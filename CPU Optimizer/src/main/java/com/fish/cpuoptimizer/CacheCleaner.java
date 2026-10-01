package com.fish.cpuoptimizer;

import com.fish.cpuoptimizer.threading.ThreadPoolManager;
import net.minecraft.server.MinecraftServer;

public class CacheCleaner {
    private static int tickCounter = 0;
    private static long lastGcTime = 0;
    private static volatile boolean gcQueued = false;

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (++tickCounter < 20 * 30) return;
        tickCounter = 0;
        if (System.currentTimeMillis() - lastGcTime < 30000) return;
        queueGc(1000);
    }

    public static void forceClean() {
        queueGc(200);
    }

    private static void queueGc(long delayMs) {
        if (gcQueued) return;
        gcQueued = true;
        ThreadPoolManager.submitIOTask(() -> {
            try {
                Thread.sleep(delayMs);
                System.gc();
                Thread.sleep(20);
                System.runFinalization();
                Thread.sleep(10);
                System.gc();
                lastGcTime = System.currentTimeMillis();
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                CpuOptimizerMod.LOGGER.warn("后台GC异常: {}", e.getMessage());
            } finally {
                gcQueued = false;
            }
        });
    }
}