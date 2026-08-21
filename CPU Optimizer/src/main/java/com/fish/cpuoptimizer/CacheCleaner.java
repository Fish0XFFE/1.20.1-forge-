package com.fish.cpuoptimizer;

import com.fish.cpuoptimizer.threading.ThreadPoolManager;
import net.minecraft.server.MinecraftServer;

public class CacheCleaner {
    private static int tickCounter = 0;

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (++tickCounter < 20 * 120) return;
        tickCounter = 0;
        ThreadPoolManager.submitIOTask(() -> {
            try {
                System.gc();
                System.runFinalization();
            } catch (Exception e) {
                CpuOptimizerMod.LOGGER.warn("后台GC异常", e);
            }
        });
    }
}