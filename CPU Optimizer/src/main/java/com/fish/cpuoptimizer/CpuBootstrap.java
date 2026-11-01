package com.fish.cpuoptimizer;

import com.fish.cpuoptimizer.cpu.CpuTopology;
import com.fish.cpuoptimizer.crash.CrashWatch;
import com.fish.cpuoptimizer.memory.JvmTips;
import com.fish.cpuoptimizer.os.PowerTuner;
import com.fish.cpuoptimizer.os.ProcessBinder;
import com.fish.cpuoptimizer.os.ThreadTuner;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class CpuBootstrap {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile CpuTopology topology;

    private CpuBootstrap() {
    }

    public static void run() {
        Workers.submit(() -> {
            try {
                CrashWatch.install();
                CpuTopology detected = CpuTopology.detect();
                topology = detected;
                LOGGER.info("CPU Optimizer: {}", detected.summary());
                if (detected.logical <= 4 || JvmTips.totalMemoryMb() <= 8192L) {
                    LOGGER.info("CPU Optimizer: 检测到低配设备, 实体降频间隔与内存整理按低配备档运行");
                }
                if (!Tuning.enabled) {
                    LOGGER.info("CPU Optimizer: 总开关已关闭, 只保留检测结果");
                    return;
                }
                if (Tuning.logDetail) {
                    LOGGER.info("CPU Optimizer: 绑定模式 {}, 进程优先级 {}, 激进高频 {}, 实体降频 {}",
                            Tuning.bindMode, Tuning.processPriority, onOff(Tuning.aggressivePower), onOff(Tuning.throttleEntities));
                    LOGGER.info("CPU Optimizer: 实体档位 {}, 内存激进模式 {}, 后台线程降级 {}",
                            Tuning.entityProfile, onOff(Tuning.memoryAggressive), onOff(Tuning.deprioritizeBackground));
                }
                long maxMb = Runtime.getRuntime().maxMemory() / 1048576L;
                if (maxMb > 12288L) {
                    LOGGER.info("CPU Optimizer: 堆上限 {} MB 偏大, 建议改成 {} MB 左右, 详见 config/cpuoptimizer-jvm.txt", maxMb, JvmTips.recommendedHeapMb());
                }
                ProcessBinder.apply(detected);
                Workers.submit(() -> {
                    Workers.sleep(6000L);
                    ThreadTuner.tune();
                    Workers.sleep(20000L);
                    ThreadTuner.tune();
                    Workers.sleep(40000L);
                    ThreadTuner.tune();
                });
                Workers.sleep(1500L);
                PowerTuner.apply();
                JvmTips.write(detected);
            } catch (Throwable t) {
                LOGGER.warn("CPU Optimizer: 初始化失败", t);
            }
        });
    }

    private static String onOff(boolean value) {
        return value ? "开启" : "关闭";
    }
}
