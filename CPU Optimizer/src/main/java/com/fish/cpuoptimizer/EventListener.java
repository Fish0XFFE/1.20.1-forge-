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

    // ===== 内存监控与三级回收 =====
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (gcRunning) return;

        MinecraftServer server = event.getServer();
        CacheCleaner.tick(server); // 每10秒执行深度缓存清理

        if (++tickCounter % 20 != 0) return;
        tickCounter = 0;

        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long used = heap.getUsed();
        long max = heap.getMax();
        double freePercent = (1.0 - (double) used / max) * 100.0;

        int threshold = Config.COMMON.memoryThreshold.get();
        if (freePercent < threshold) {
            CpuOptimizerMod.LOGGER.warn("⚠️ 内存剩余 {:.1f}%，启动三级回收", freePercent);
            gcRunning = true;
            // 第一级：常规 GC
            System.gc();
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            // 第二级：清理堆外内存（DirectBuffer）
            try {
                java.nio.ByteBuffer.allocateDirect(0);
                System.runFinalization();
            } catch (Exception ignored) {}
            // 第三级：如果依然紧张，紧急双重 GC
            MemoryUsage after = memoryBean.getHeapMemoryUsage();
            double afterFree = (1.0 - (double) after.getUsed() / after.getMax()) * 100.0;
            if (afterFree < 5) {
                CpuOptimizerMod.LOGGER.warn("⚠️ 内存依然紧张，执行紧急双重GC");
                System.gc();
                System.gc();
            }
            gcRunning = false;
        }
    }

    // ===== 多核异步区块加载（预加载方块实体、光照） =====
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        ThreadPoolManager.submitComputeTask(() -> {
            try {
                // 预加载方块实体（触发内部初始化）
                var blockEntities = chunk.getBlockEntities();
                if (blockEntities != null && !blockEntities.isEmpty()) {
                    blockEntities.values().forEach(be -> {
                        if (be != null) be.getBlockState();
                    });
                }
                // 触发光照引擎初始化
                Level level = chunk.getLevel();
                if (level != null) {
                    level.getLightEngine();
                }
                CpuOptimizerMod.LOGGER.debug("✅ 区块 {} 异步预加载完成", chunk.getPos());
            } catch (Exception e) {
                CpuOptimizerMod.LOGGER.error("异步预加载区块失败: {}", e.getMessage());
            }
        });

        // ===== 区块加载后，如果内存使用率 > 70%，触发 GC 降低峰值 =====
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        double usedPercent = (double) heap.getUsed() / heap.getMax() * 100.0;
        if (usedPercent > 70) {
            // 使用独立线程执行 GC，避免阻塞主线程
            ThreadPoolManager.submitComputeTask(() -> {
                System.gc();
                CpuOptimizerMod.LOGGER.debug("⚡ 区块加载后触发 GC（内存使用 {}%）", String.format("%.1f", usedPercent));
            });
        }
    }

    // ===== 客户端帧率优化：提升进程优先级（仅 Windows） =====
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