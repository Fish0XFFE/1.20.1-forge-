package com.fish.cpuoptimizer;

import com.fish.cpuoptimizer.threading.ThreadPoolManager;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EventListener {
    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        CacheCleaner.tick(event.getServer());
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        ThreadPoolManager.submitComputeTask(() -> {
            try {
                var blockEntities = chunk.getBlockEntities();
                if (blockEntities != null && !blockEntities.isEmpty()) {
                    blockEntities.values().forEach(be -> { if (be != null) be.getBlockState(); });
                }
                Level level = chunk.getLevel();
                if (level != null) {
                    level.getLightEngine();
                }
            } catch (Exception ignored) {}
        });
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        if (tickCounter == 0) {
            try {
                mc.options.ambientOcclusion().set(false);
                mc.options.entityDistanceScaling().set(0.5);
            } catch (Exception ignored) {}
            try {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    long pid = ProcessHandle.current().pid();
                    Runtime.getRuntime().exec("wmic process where processid=" + pid + " CALL setpriority 128");
                }
            } catch (Exception ignored) {}
        }
        tickCounter++;
    }
}