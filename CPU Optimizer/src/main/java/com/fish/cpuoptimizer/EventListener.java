package com.fish.cpuoptimizer;

import com.fish.cpuoptimizer.config.Config;
import com.fish.cpuoptimizer.threading.ThreadPoolManager;
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
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private int tickCounter = 0;
    private boolean gcRunning = false;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        CacheCleaner.tick(server); // 异步执行，不阻塞主线程

        // ===== 每秒监控内存，但不再主动触发 GC =====
        if (++tickCounter % 20 != 0) return;
        tickCounter = 0;

        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long used = heap.getUsed();
        long max = heap.getMax();
        double freePercent = (1.0 - (double) used / max) * 100.0;

        int threshold = Config.COMMON.memoryThreshold.get();
        if (freePercent < threshold && !gcRunning) {
            CpuOptimizerMod.LOGGER.warn("⚠️ 内存剩余 {:.1f}%，即将在异步线程执行 GC", freePercent);
            gcRunning = true;
            // ===== 将 GC 移到异步线程，不阻塞主线程 =====
            ThreadPoolManager.submitIOTask(() -> {
                try {
                    System.gc();
                    System.runFinalization();
                    CpuOptimizerMod.LOGGER.debug("✅ 异步 GC 完成");
                } finally {
                    gcRunning = false;
                }
            });
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        // ===== 区块预加载仍然异步执行，但限制线程数 =====
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
                CpuOptimizerMod.LOGGER.debug("✅ 区块 {} 异步预加载完成", chunk.getPos());
            } catch (Exception e) {
                CpuOptimizerMod.LOGGER.error("异步预加载区块失败: {}", e.getMessage());
            }
        });
    }

    // ===== 客户端帧率优化（仅客户端，不影响服务器） =====
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (tickCounter == 0) {
            try {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    long pid = ProcessHandle.current().pid();
                    Runtime.getRuntime().exec("wmic process where processid=" + pid + " CALL setpriority 128");
                    CpuOptimizerMod.LOGGER.info("⚡ 已提升游戏进程优先级到 High");
                }
            } catch (Exception e) {
                CpuOptimizerMod.LOGGER.warn("无法提升进程优先级");
            }
        }
    }
}