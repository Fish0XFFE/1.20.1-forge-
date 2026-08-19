package com.fish.cpuoptimizer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeManager;

import java.lang.reflect.Field;
import java.util.Map;

public class CacheCleaner {
    private static int tickCounter = 0;

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (++tickCounter < 20 * 10) return; // 每10秒执行一次
        tickCounter = 0;

        try {
            RecipeManager recipes = server.getRecipeManager();
            Field recipesField = RecipeManager.class.getDeclaredField("recipes");
            recipesField.setAccessible(true);
            Map<?, ?> recipesMap = (Map<?, ?>) recipesField.get(recipes);
            if (recipesMap != null && recipesMap.size() > 1000) {
                recipesMap.clear();
                CpuOptimizerMod.LOGGER.debug("✅ 清理了 RecipeManager 缓存");
            }

            for (ServerLevel level : server.getAllLevels()) {
                try {
                    Field entityStorageField = level.getClass().getDeclaredField("entityStorage");
                    entityStorageField.setAccessible(true);
                    Object storage = entityStorageField.get(level);
                    try {
                        storage.getClass().getMethod("compact").invoke(storage);
                    } catch (NoSuchMethodException ignored) {}
                } catch (Exception ignored) {}

                try {
                    Field poiManagerField = level.getClass().getDeclaredField("poiManager");
                    poiManagerField.setAccessible(true);
                    Object poiManager = poiManagerField.get(level);
                    Field poiStorageField = poiManager.getClass().getDeclaredField("poiStorage");
                    poiStorageField.setAccessible(true);
                    Object poiStorage = poiStorageField.get(poiManager);
                    poiStorage.getClass().getMethod("compact").invoke(poiStorage);
                } catch (Exception ignored) {}
            }

            System.gc();
            System.runFinalization();
        } catch (Exception e) {
            CpuOptimizerMod.LOGGER.warn("缓存清理异常", e);
        }
    }
}