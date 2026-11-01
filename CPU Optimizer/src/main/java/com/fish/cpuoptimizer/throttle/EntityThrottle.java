package com.fish.cpuoptimizer.throttle;

import com.fish.cpuoptimizer.Tuning;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

public final class EntityThrottle {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SELF_CHECK_TICKS = 400;
    private static final boolean LOW_END = Runtime.getRuntime().availableProcessors() <= 4;
    private static volatile long serverTicks;
    private static volatile long clientTicks;
    private static volatile long consideredServer;
    private static volatile long skippedServer;
    private static volatile long consideredClient;
    private static volatile long skippedClient;
    private static volatile boolean selfChecked;
    private static volatile boolean broken;
    private static volatile int overloadScore;
    private static volatile boolean overloaded;
    public static volatile double clientRenderDistanceSq;

    private EntityThrottle() {
    }

    public static void tickServer() {
        long ticks = serverTicks + 1L;
        serverTicks = ticks;
        if (ticks == SELF_CHECK_TICKS && !selfChecked) {
            selfChecked = true;
            if (consideredServer == 0L) {
                LOGGER.warn("CPU Optimizer: 实体降频没有收到任何实体, 可能与其他模组冲突, 该功能已自动失效");
            }
        }
    }

    public static void tickClient() {
        clientTicks++;
    }

    public static void observeServerTick(double millis) {
        if (!Tuning.enabled || !Tuning.throttleEntities) {
            return;
        }
        int score = overloadScore;
        if (millis > 40.0D) {
            score = Math.min(100, score + 4);
        } else if (millis < 25.0D) {
            score = Math.max(0, score - 1);
        }
        overloadScore = score;
        boolean raised = score >= 60;
        if (raised != overloaded) {
            overloaded = raised;
            if (Tuning.logDetail) {
                if (raised) {
                    LOGGER.info("CPU Optimizer: 服务端 tick 偏慢, 实体降频自动加一档");
                } else {
                    LOGGER.info("CPU Optimizer: 服务端 tick 恢复, 实体降频回到常规档位");
                }
            }
        }
    }

    public static void reset() {
        serverTicks = 0L;
        clientTicks = 0L;
        consideredServer = 0L;
        skippedServer = 0L;
        consideredClient = 0L;
        skippedClient = 0L;
        overloadScore = 0;
        overloaded = false;
    }

    public static boolean shouldSkip(Entity entity) {
        if (broken || entity == null || !Tuning.enabled || !Tuning.throttleEntities) {
            return false;
        }
        try {
            return skip(entity);
        } catch (Throwable error) {
            broken = true;
            LOGGER.warn("CPU Optimizer: 实体降频出现异常, 已在本次运行中关闭该功能", error);
            return false;
        }
    }

    private static boolean skip(Entity entity) {
        boolean client = entity.level().isClientSide();
        if (client) {
            consideredClient++;
        } else {
            consideredServer++;
        }
        int interval = interval(entity, client);
        if (interval <= 1) {
            return false;
        }
        long ticks = client ? clientTicks : serverTicks;
        if ((ticks + entity.getId()) % (long) interval != 0L) {
            if (client) {
                skippedClient++;
            } else {
                skippedServer++;
            }
            return true;
        }
        return false;
    }

    private static int interval(Entity entity, boolean client) {
        if (client && !Tuning.throttleClient) {
            return 1;
        }
        if (entity instanceof Player || entity.isVehicle()) {
            return 1;
        }
        if (entity instanceof EnderDragon || entity instanceof WitherBoss) {
            return 1;
        }
        if (entity.tickCount < 40) {
            return 1;
        }
        if (Tuning.skipNamed && entity.hasCustomName()) {
            return 1;
        }
        if (!Tuning.whitelist.isEmpty() && Tuning.whitelist.contains(entity.getType())) {
            return 1;
        }
        List<? extends Player> players = entity.level().players();
        int count = players.size();
        if (count == 0) {
            return 1;
        }
        double nearest = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            Player player = players.get(i);
            double distance = entity.distanceToSqr(player.getX(), player.getY(), player.getZ());
            if (distance < nearest) {
                nearest = distance;
            }
        }
        if (client && clientRenderDistanceSq > 0.0D && nearest > clientRenderDistanceSq) {
            return 64;
        }
        int interval;
        if (nearest < Tuning.nearDistanceSq) {
            interval = 1;
        } else if (nearest < Tuning.throttleDistanceSq) {
            interval = Tuning.tickInterval;
        } else if (nearest < farLimit()) {
            interval = Math.max(Tuning.tickInterval, Tuning.farInterval);
        } else {
            interval = Math.min(8, Math.max(Tuning.tickInterval, Tuning.farInterval) + 1);
        }
        if (Tuning.throttleItems && interval < 4 && nearest > 64.0D && (entity instanceof ItemEntity || entity instanceof ExperienceOrb)) {
            interval = 4;
        }
        if (LOW_END && interval > 1 && interval < 8) {
            interval = Math.min(8, interval + 2);
        }
        if (overloaded && interval < 8) {
            interval = Math.min(8, interval + 1);
        }
        return interval;
    }

    private static double farLimit() {
        double far = Tuning.farDistanceSq;
        return far > Tuning.throttleDistanceSq ? far : Double.MAX_VALUE;
    }

    public static void report() {
        try {
            long server = Math.max(1L, serverTicks);
            long client = Math.max(1L, clientTicks);
            if (consideredServer > 0L) {
                LOGGER.info("CPU Optimizer 实体统计: 服务端每刻处理约 {} 个实体, 跳过约 {} 个", consideredServer / server, skippedServer / server);
            }
            if (consideredClient > 0L) {
                LOGGER.info("CPU Optimizer 实体统计: 客户端每刻处理约 {} 个实体, 跳过约 {} 个", consideredClient / client, skippedClient / client);
            }
        } catch (Throwable ignored) {
        }
    }
}
