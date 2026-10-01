package com.fish.cpuoptimizer.chunk;

import com.fish.cpuoptimizer.CpuOptimizerMod;
import com.fish.cpuoptimizer.threading.ThreadPoolManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncChunkEngine {
    private static final ConcurrentHashMap<ServerLevel, ConcurrentLinkedQueue<ChunkTask>> QUEUES = new ConcurrentHashMap<>();
    private static final AtomicInteger PENDING = new AtomicInteger(0);
    private static final int MAX_PENDING = 8192;

    private record ChunkTask(int x, int z, int priority) {}

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() instanceof ServerLevel level) {
                enqueueAround(level, player, 12);
            }
        }
        QUEUES.forEach((level, queue) -> {
            if (!queue.isEmpty()) process(level, queue);
        });
    }

    public static void preloadAroundPlayer(ServerLevel level, ServerPlayer player, int radius) {
        enqueueAround(level, player, radius);
        process(level, QUEUES.computeIfAbsent(level, k -> new ConcurrentLinkedQueue<>()));
    }

    private static void enqueueAround(ServerLevel level, ServerPlayer player, int radius) {
        int px = player.chunkPosition().x;
        int pz = player.chunkPosition().z;
        ConcurrentLinkedQueue<ChunkTask> queue = QUEUES.computeIfAbsent(level, k -> new ConcurrentLinkedQueue<>());
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = px + dx;
                int cz = pz + dz;
                if (!level.hasChunk(cx, cz)) {
                    queue.offer(new ChunkTask(cx, cz, Math.abs(dx) + Math.abs(dz)));
                }
            }
        }
    }

    private static void process(ServerLevel level, ConcurrentLinkedQueue<ChunkTask> queue) {
        int batchSize = Math.min(queue.size(), ThreadPoolManager.getChunkPoolSize() * 16);
        List<ChunkTask> batch = new ArrayList<>(batchSize);
        ChunkTask t;
        while (batch.size() < batchSize && (t = queue.poll()) != null) batch.add(t);
        if (batch.isEmpty()) return;
        batch.sort(Comparator.comparingInt(ChunkTask::priority));

        for (ChunkTask task : batch) {
            if (PENDING.get() >= MAX_PENDING) {
                queue.offer(task);
                continue;
            }
            PENDING.incrementAndGet();
            ThreadPoolManager.submitChunkTask(() -> {
                try {
                    CompletableFuture<ChunkAccess> future = level.getChunkSource()
                            .getChunkFuture(task.x(), task.z(), ChunkStatus.FULL, true)
                            .thenApply(either -> either.left().orElse(null));
                    future.whenComplete((chunk, ex) -> {
                        PENDING.decrementAndGet();
                        if (ex != null) {
                            CpuOptimizerMod.LOGGER.debug("区块预取失败 [{},{}]: {}", task.x(), task.z(), ex.getMessage());
                        }
                    });
                } catch (Exception e) {
                    PENDING.decrementAndGet();
                    CpuOptimizerMod.LOGGER.error("区块预取异常 [{},{}]", task.x(), task.z(), e);
                }
            });
        }
    }

    public static int getPending() { return PENDING.get(); }
    public static void clear() { QUEUES.clear(); PENDING.set(0); }
}