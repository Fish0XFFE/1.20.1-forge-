package com.fish.cpuoptimizer.config;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED = B.define("general.enabled", true);
    public static final ForgeConfigSpec.BooleanValue LOG_DETAIL = B.define("general.logDetail", true);
    public static final ForgeConfigSpec.BooleanValue LOG_STATISTICS = B.define("general.logStatistics", true);
    public static final ForgeConfigSpec.BooleanValue CRASH_GUARD = B.define("general.crashGuard", true);

    public static final ForgeConfigSpec.EnumValue<BindMode> BIND_MODE = B.defineEnum("cpu.bindMode", BindMode.AUTO);
    public static final ForgeConfigSpec.ConfigValue<String> AFFINITY_MASK = B.define("cpu.affinityMask", "");
    public static final ForgeConfigSpec.EnumValue<PriorityMode> PROCESS_PRIORITY = B.defineEnum("cpu.processPriority", PriorityMode.ABOVE_NORMAL);
    public static final ForgeConfigSpec.BooleanValue TUNE_THREADS = B.define("cpu.tuneThreadPriority", true);
    public static final ForgeConfigSpec.BooleanValue DEPRIORITIZE_BACKGROUND = B.define("cpu.deprioritizeBackground", true);

    public static final ForgeConfigSpec.BooleanValue AGGRESSIVE_POWER = B.define("power.aggressive", true);
    public static final ForgeConfigSpec.BooleanValue ULTIMATE_PERFORMANCE = B.define("power.ultimatePerformance", true);
    public static final ForgeConfigSpec.BooleanValue DISABLE_POWER_THROTTLING = B.define("power.disablePowerThrottling", true);
    public static final ForgeConfigSpec.BooleanValue APPLY_ON_BATTERY = B.define("power.applyOnBattery", true);
    public static final ForgeConfigSpec.BooleanValue RESTORE_ON_DISABLE = B.define("power.restoreOnDisable", true);

    public static final ForgeConfigSpec.BooleanValue THROTTLE_ENTITIES = B.define("entity.throttleEnabled", true);
    public static final ForgeConfigSpec.EnumValue<EntityProfile> ENTITY_PROFILE = B.defineEnum("entity.profile", EntityProfile.EXTREME);
    public static final ForgeConfigSpec.IntValue NEAR_DISTANCE = B.defineInRange("entity.nearDistance", 32, 8, 128);
    public static final ForgeConfigSpec.IntValue THROTTLE_DISTANCE = B.defineInRange("entity.throttleDistance", 64, 16, 256);
    public static final ForgeConfigSpec.IntValue FAR_DISTANCE = B.defineInRange("entity.farDistance", 128, 32, 512);
    public static final ForgeConfigSpec.IntValue TICK_INTERVAL = B.defineInRange("entity.tickInterval", 2, 1, 8);
    public static final ForgeConfigSpec.IntValue FAR_INTERVAL = B.defineInRange("entity.farInterval", 4, 1, 16);
    public static final ForgeConfigSpec.BooleanValue THROTTLE_CLIENT = B.define("entity.throttleClient", true);
    public static final ForgeConfigSpec.BooleanValue THROTTLE_ITEMS = B.define("entity.throttleItems", true);
    public static final ForgeConfigSpec.BooleanValue SKIP_NAMED = B.define("entity.skipNamedEntities", true);
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WHITELIST = B.defineList("entity.whitelist", List.of(), o -> o instanceof String);

    public static final ForgeConfigSpec.BooleanValue COMPACT_AT_LOADING = B.define("memory.compactAtLoading", true);
    public static final ForgeConfigSpec.BooleanValue MEMORY_AGGRESSIVE = B.define("memory.aggressive", true);
    public static final ForgeConfigSpec.IntValue COMPACT_THRESHOLD = B.defineInRange("memory.compactThreshold", 75, 40, 98);
    public static final ForgeConfigSpec.IntValue COMPACT_INTERVAL = B.defineInRange("memory.compactIntervalSeconds", 30, 5, 600);

    public static final ForgeConfigSpec SPEC = B.build();

    private Config() {
    }
}
