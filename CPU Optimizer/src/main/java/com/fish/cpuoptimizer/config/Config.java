package com.fish.cpuoptimizer.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {

    public static class CommonConfig {
        public final ForgeConfigSpec.IntValue memoryThreshold;

        CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("通用性能优化配置").push("common");
            memoryThreshold = builder
                    .comment("内存剩余百分比低于此值时触发 GC（建议 10-30）")
                    .defineInRange("memoryThreshold", 20, 5, 50);
            builder.pop();
        }
    }

    public static final CommonConfig COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        Pair<CommonConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder()
                .configure(CommonConfig::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }


    public static class ClientConfig {
        public final ForgeConfigSpec.BooleanValue autoAffinity;
        public final ForgeConfigSpec.BooleanValue skipOldCpu;
        public final ForgeConfigSpec.IntValue maxCoresForBinding;
        public final ForgeConfigSpec.ConfigValue<String> affinityCoreRange;
        public final ForgeConfigSpec.ConfigValue<String> affinityMaskHex;

        ClientConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("客户端CPU亲和性绑定配置").push("client");
            autoAffinity = builder
                    .comment("是否自动绑定CPU亲和性")
                    .define("autoAffinity", true);
            skipOldCpu = builder
                    .comment("核心数小于等于n时默认全核")
                    .define("skipOldCpu", true);
            maxCoresForBinding = builder
                    .comment("这个可以滚了，不要动他！")
                    .defineInRange("maxCoresForBinding", 0, 0, 64);
            affinityCoreRange = builder
                    .comment("手动指定核心范围")
                    .define("affinityCoreRange", "");
            affinityMaskHex = builder
                    .comment("手动指定掩码")
                    .define("affinityMaskHex", "");
            builder.pop();
        }
    }

    public static final ClientConfig CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        Pair<ClientConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder()
                .configure(ClientConfig::new);
        CLIENT = pair.getLeft();
        CLIENT_SPEC = pair.getRight();
    }
}