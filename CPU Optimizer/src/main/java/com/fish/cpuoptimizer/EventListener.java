package com.fish.cpuoptimizer;

import com.fish.cpuoptimizer.chunk.AsyncChunkEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EventListener {
    private int tickCounter = 0;
    private int memCheckCounter = 0;
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final Map<UUID, ChunkPos> lastPlayerPos = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.getPlayerCount() == 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ChunkPos current = player.chunkPosition();
            ChunkPos last = lastPlayerPos.get(player.getUUID());
            if (last != null) {
                int dx = current.x - last.x;
                int dz = current.z - last.z;
                if (dx * dx + dz * dz > 64) {
                    if (player.level() instanceof ServerLevel level) {
                        AsyncChunkEngine.preloadAroundPlayer(level, player, 24);
                        CpuOptimizerMod.LOGGER.info("检测到玩家 {} 远距离传送",
                                player.getName().getString());
                    }
                }
            }
            lastPlayerPos.put(player.getUUID(), current);
        }

        AsyncChunkEngine.tick(server);
        CacheCleaner.tick(server);

        if (++memCheckCounter >= 100) {
            memCheckCounter = 0;
            MemoryUsage heap = memoryBean.getHeapMemoryUsage();
            double freePercent = (1.0 - (double) heap.getUsed() / heap.getMax()) * 100.0;
            if (freePercent < 6) {
                CacheCleaner.forceClean();
                CpuOptimizerMod.LOGGER.warn("内存仅剩 {}%，强制清理", String.format("%.1f", freePercent));
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (tickCounter == 0) boostProcessPriority();
        tickCounter++;
    }

    private void boostProcessPriority() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                long pid = ProcessHandle.current().pid();
                Runtime.getRuntime().exec(new String[]{
                        "powershell", "-NoProfile", "-Command",
                        "(Get-Process -Id " + pid + ").PriorityClass = 'High'"
                });
            }
        } catch (Exception ignored) {}
    }
}