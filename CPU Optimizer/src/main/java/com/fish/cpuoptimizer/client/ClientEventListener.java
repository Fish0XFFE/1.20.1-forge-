package com.fish.cpuoptimizer.client;

import com.fish.cpuoptimizer.Tuning;
import com.fish.cpuoptimizer.Workers;
import com.fish.cpuoptimizer.memory.MemoryGuard;
import com.fish.cpuoptimizer.stats.Stats;
import com.fish.cpuoptimizer.throttle.EntityThrottle;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ClientEventListener {
    private static final int SAMPLE_TICKS = 20;
    private static final int REPORT_TICKS = 6000;
    private int counter;
    private int reportCounter;
    private boolean inWorld;

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new ClientEventListener());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        try {
            tick(event);
        } catch (Throwable ignored) {
        }
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Stats.clientTickStart();
            return;
        }
        Stats.clientTickEnd();
        EntityThrottle.tickClient();
        if (++reportCounter >= REPORT_TICKS) {
            reportCounter = 0;
            if (inWorld) {
                Stats.report("运行中");
            }
        }
        if (++counter < SAMPLE_TICKS) {
            return;
        }
        counter = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            if (minecraft.screen != null) {
                MemoryGuard.compactIfNeeded("标题界面");
            }
            return;
        }
        int distance = minecraft.options.getEffectiveRenderDistance();
        if (minecraft.screen != null && minecraft.isSingleplayer()) {
            MemoryGuard.compactIfNeeded("暂停界面");
        }
        if (distance > 0) {
            double limit = (double) distance * 16.0D + 32.0D;
            EntityThrottle.clientRenderDistanceSq = limit * limit;
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null && minecraft.screen == null) {
                Stats.frameStart();
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        try {
            inWorld = true;
            reportCounter = 0;
            Stats.reset();
            EntityThrottle.reset();
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        try {
            if (!inWorld) {
                return;
            }
            inWorld = false;
            Stats.report("世界退出");
            if (Tuning.logDetail) {
                EntityThrottle.report();
            }
            Stats.reset();
            Workers.submit(() -> {
                try {
                    Workers.sleep(2500L);
                    MemoryGuard.compactIfNeeded("世界退出");
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }
}
