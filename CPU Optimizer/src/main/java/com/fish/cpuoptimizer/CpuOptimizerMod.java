package com.fish.cpuoptimizer;

import com.fish.cpuoptimizer.client.ClientBootstrap;
import com.fish.cpuoptimizer.config.Config;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CpuOptimizerMod.MOD_ID)
public final class CpuOptimizerMod {
    public static final String MOD_ID = "cpuoptimizer";

    public CpuOptimizerMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::onCommonSetup);
        bus.addListener(this::onConfig);
        MinecraftForge.EVENT_BUS.register(new EventListener());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientBootstrap::register);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Tuning.refresh();
            CpuBootstrap.run();
        });
    }

    private void onConfig(ModConfigEvent event) {
        try {
            if (event.getConfig().getSpec() == Config.SPEC) {
                Tuning.refresh();
            }
        } catch (Throwable ignored) {
        }
    }
}
