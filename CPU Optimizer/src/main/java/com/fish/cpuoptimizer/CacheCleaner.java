package com.fish.cpuoptimizer;

import com.fish.cpuoptimizer.threading.ThreadPoolManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeManager;

import java.lang.reflect.Field;
import java.util.Map;

public class CacheCleaner {
    private static int tickCounter = 0;
    private static boolean cleaning = false;

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (cleaning) return;
        if (++tickCounter < 20 * 30) return; // 改为每30秒执行一次
        tickCounter = 0;

        // ===== 关键修改：将清理操作提交到异步线程池 =====
        ThreadPoolManager.submitIOTask(() -> {
            cleaning = true;
            try {
                // 清理 RecipeManager
                RecipeManager recipes = server.getRecipeManager();
                Field recipesField = RecipeManager.class.getDeclaredField("recipes");
                recipesField.setAccessible(true);
                Map<?, ?> recipesMap = (Map<?, ?>) recipesField.get(recipes);
                if (recipesMap != null && recipesMap.size() > 1000) {
                    recipesMap.clear();
                    CpuOptimizerMod.LOGGER.debug("✅ 异步清理了 RecipeManager 缓存");
                }

                // 触发 GC（放在异步线程中执行，不阻塞主线程）
                System.gc();
                System.runFinalization();
            } catch (Exception e) {
                CpuOptimizerMod.LOGGER.warn("异步缓存清理异常", e);
            } finally {
                cleaning = false;
            }
        });
    }
}