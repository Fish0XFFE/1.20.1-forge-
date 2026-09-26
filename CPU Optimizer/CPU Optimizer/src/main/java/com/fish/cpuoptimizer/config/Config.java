package com.fish.cpuoptimizer.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class Config {

    // ==================== 通用配置（服务端 + 客户端共享） ====================
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


    // ==================== 客户端配置（仅客户端生效） ====================
    public static class ClientConfig {
        public final ForgeConfigSpec.BooleanValue autoAffinity;
        public final ForgeConfigSpec.BooleanValue skipOldCpu;
        public final ForgeConfigSpec.IntValue maxCoresForBinding;
        public final ForgeConfigSpec.ConfigValue<String> affinityCoreRange;
        public final ForgeConfigSpec.ConfigValue<String> affinityMaskHex;

        ClientConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("客户端 CPU 亲和性绑定配置").push("client");
            autoAffinity = builder
                    .comment("是否自动绑定 CPU 亲和性（默认绑定所有核心）")
                    .define("autoAffinity", true);
            skipOldCpu = builder
                    .comment("当逻辑核心数 ≤ 此值时跳过绑定（保护老 CPU）")
                    .define("skipOldCpu", true);
            maxCoresForBinding = builder
                    .comment("（已弃用，保留兼容）")
                    .defineInRange("maxCoresForBinding", 0, 0, 64);
            affinityCoreRange = builder
                    .comment("手动指定核心范围（优先级高于自动），例如 '0-5' 或 '0,2,4,6'，留空则自动绑定所有核心")
                    .define("affinityCoreRange", "");
            affinityMaskHex = builder
                    .comment("手动指定十六进制掩码（优先级最高），例如 '0xFFFF' 表示绑定前16个核心")
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