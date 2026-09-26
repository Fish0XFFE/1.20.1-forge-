package com.fish.cpuoptimizer;

import com.fish.cpuoptimizer.threading.ThreadPoolManager;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

public class EventListener {
    private int tickCounter = 0;
    private int memCheckCounter = 0;
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        CacheCleaner.tick(event.getServer());

        if (++memCheckCounter < 100) return;
        memCheckCounter = 0;

        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        double freePercent = (1.0 - (double) heap.getUsed() / heap.getMax()) * 100.0;
        if (freePercent < 8) {
            CacheCleaner.forceClean();
            CpuOptimizerMod.LOGGER.warn("内存仅剩 {}%，已排队强制清理", String.format("%.1f", freePercent));
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        ThreadPoolManager.submitComputeTask(() -> {
            try {
                var blockEntities = chunk.getBlockEntities();
                if (blockEntities != null && !blockEntities.isEmpty()) {
                    blockEntities.values().forEach(be -> {
                        if (be != null) be.getBlockState();
                    });
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
        if (mc == null) return;

        if (tickCounter == 0) {
            boostProcessPriority();
        }
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
                CpuOptimizerMod.LOGGER.info("游戏进程优先级已提升到 High");
            }
        } catch (Exception ignored) {}
    }
}