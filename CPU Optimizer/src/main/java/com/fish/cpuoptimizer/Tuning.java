package com.fish.cpuoptimizer;

import com.fish.cpuoptimizer.config.BindMode;
import com.fish.cpuoptimizer.config.Config;
import com.fish.cpuoptimizer.config.EntityProfile;
import com.fish.cpuoptimizer.config.PriorityMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

public final class Tuning {
    public static volatile boolean enabled = true;
    public static volatile boolean logDetail = true;
    public static volatile boolean logStatistics = true;
    public static volatile boolean crashGuard = true;
    public static volatile BindMode bindMode = BindMode.AUTO;
    public static volatile String affinityMask = "";
    public static volatile PriorityMode processPriority = PriorityMode.ABOVE_NORMAL;
    public static volatile boolean tuneThreads = true;
    public static volatile boolean deprioritizeBackground = true;
    public static volatile boolean aggressivePower = true;
    public static volatile boolean ultimatePerformance = true;
    public static volatile boolean disablePowerThrottling = true;
    public static volatile boolean applyOnBattery = true;
    public static volatile boolean restoreOnDisable = true;
    public static volatile boolean throttleEntities = true;
    public static volatile EntityProfile entityProfile = EntityProfile.EXTREME;
    public static volatile double nearDistanceSq = 1024.0D;
    public static volatile double throttleDistanceSq = 4096.0D;
    public static volatile double farDistanceSq = 16384.0D;
    public static volatile int tickInterval = 2;
    public static volatile int farInterval = 4;
    public static volatile boolean throttleClient = true;
    public static volatile boolean throttleItems = true;
    public static volatile boolean skipNamed = true;
    public static volatile Set<EntityType<?>> whitelist = Set.of();
    public static volatile boolean compactAtLoading = true;
    public static volatile boolean memoryAggressive = true;
    public static volatile int compactThreshold = 75;
    public static volatile long compactIntervalMillis = 30000L;
    private static volatile List<String> rawWhitelist = List.of();

    private Tuning() {
    }

    public static void refresh() {
        try {
            enabled = Config.ENABLED.get();
            logDetail = Config.LOG_DETAIL.get();
            logStatistics = Config.LOG_STATISTICS.get();
            crashGuard = Config.CRASH_GUARD.get();
            bindMode = Config.BIND_MODE.get();
            affinityMask = Config.AFFINITY_MASK.get();
            processPriority = Config.PROCESS_PRIORITY.get();
            tuneThreads = Config.TUNE_THREADS.get();
            deprioritizeBackground = Config.DEPRIORITIZE_BACKGROUND.get();
            aggressivePower = Config.AGGRESSIVE_POWER.get();
            ultimatePerformance = Config.ULTIMATE_PERFORMANCE.get();
            disablePowerThrottling = Config.DISABLE_POWER_THROTTLING.get();
            applyOnBattery = Config.APPLY_ON_BATTERY.get();
            restoreOnDisable = Config.RESTORE_ON_DISABLE.get();
            throttleEntities = Config.THROTTLE_ENTITIES.get();
            entityProfile = Config.ENTITY_PROFILE.get();
            int near = Config.NEAR_DISTANCE.get();
            int throttle = Config.THROTTLE_DISTANCE.get();
            int far = Config.FAR_DISTANCE.get();
            int interval = Config.TICK_INTERVAL.get();
            int farTicks = Config.FAR_INTERVAL.get();
            if (entityProfile == EntityProfile.EXTREME) {
                near = Math.min(near, 24);
                throttle = Math.min(throttle, 48);
                far = Math.min(far, 96);
                interval = Math.max(interval, 3);
                farTicks = Math.max(farTicks, 6);
            }
            nearDistanceSq = square(near);
            throttleDistanceSq = square(throttle);
            farDistanceSq = square(far);
            tickInterval = interval;
            farInterval = farTicks;
            throttleClient = Config.THROTTLE_CLIENT.get();
            throttleItems = Config.THROTTLE_ITEMS.get();
            skipNamed = Config.SKIP_NAMED.get();
            compactAtLoading = Config.COMPACT_AT_LOADING.get();
            memoryAggressive = Config.MEMORY_AGGRESSIVE.get();
            compactThreshold = Config.COMPACT_THRESHOLD.get();
            compactIntervalMillis = Config.COMPACT_INTERVAL.get() * 1000L;
            if (memoryAggressive) {
                compactThreshold = Math.min(compactThreshold, 70);
                compactIntervalMillis = Math.min(compactIntervalMillis, 20000L);
            }
        } catch (Throwable ignored) {
        }
        refreshWhitelist();
    }

    private static void refreshWhitelist() {
        try {
            List<? extends String> configured = Config.WHITELIST.get();
            List<String> copy = new ArrayList<>(configured);
            if (copy.equals(rawWhitelist)) {
                return;
            }
            Set<EntityType<?>> resolved = new HashSet<>();
            for (int i = 0; i < copy.size(); i++) {
                String id = copy.get(i);
                if (id == null) {
                    continue;
                }
                ResourceLocation key = ResourceLocation.tryParse(id.trim().toLowerCase(Locale.ROOT));
                if (key == null) {
                    continue;
                }
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(key);
                if (type != null) {
                    resolved.add(type);
                }
            }
            if (!copy.isEmpty() && resolved.size() < copy.size()) {
                return;
            }
            whitelist = resolved;
            rawWhitelist = copy;
        } catch (Throwable ignored) {
        }
    }

    private static double square(int value) {
        return value <= 0 ? 0.0D : (double) value * (double) value;
    }
}
