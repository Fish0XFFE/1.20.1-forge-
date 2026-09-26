package com.fish.cpuoptimizer;

import com.fish.cpuoptimizer.config.Config;
import com.fish.cpuoptimizer.affinity.AffinityBinderClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("cpuoptimizer")
public class CpuOptimizerMod {
    public static final String MOD_ID = "cpuoptimizer";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public CpuOptimizerMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        MinecraftForge.EVENT_BUS.register(new EventListener());
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> AffinityBinderClient::tryAutoBind);
        LOGGER.info("✅ CPU Optimizer 模组加载完成！");
    }
}