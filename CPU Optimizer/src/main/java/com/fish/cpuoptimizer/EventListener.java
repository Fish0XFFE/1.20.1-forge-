package com.fish.cpuoptimizer;

import com.fish.cpuoptimizer.memory.MemoryGuard;
import com.fish.cpuoptimizer.stats.Stats;
import com.fish.cpuoptimizer.throttle.EntityThrottle;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class EventListener {
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        try {
            if (event.phase == TickEvent.Phase.START) {
                Stats.serverTickStart();
                EntityThrottle.tickServer();
            } else {
                double elapsed = Stats.serverTickEnd();
                if (elapsed > 0.0D) {
                    EntityThrottle.observeServerTick(elapsed);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        try {
            Stats.reset();
            EntityThrottle.reset();
            MinecraftServer server = event.getServer();
            Workers.submit(() -> {
                try {
                    Workers.sleep(30000L);
                    if (server.getPlayerCount() == 0) {
                        MemoryGuard.compactIfNeeded("服务端启动后");
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        try {
            if (Tuning.logStatistics) {
                Stats.report("服务端关闭");
            }
            if (Tuning.logDetail) {
                EntityThrottle.report();
            }
            Workers.submit(() -> {
                try {
                    Workers.sleep(1500L);
                    MemoryGuard.compactIfNeeded("服务端关闭");
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
