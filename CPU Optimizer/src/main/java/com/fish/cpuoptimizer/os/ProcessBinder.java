package com.fish.cpuoptimizer.os;

import com.fish.cpuoptimizer.Tuning;
import com.fish.cpuoptimizer.config.BindMode;
import com.fish.cpuoptimizer.config.PriorityMode;
import com.fish.cpuoptimizer.cpu.CpuTopology;
import com.fish.cpuoptimizer.util.Command;
import com.mojang.logging.LogUtils;
import java.util.Locale;
import org.slf4j.Logger;

public final class ProcessBinder {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean pCoreBound;

    private ProcessBinder() {
    }

    public static void apply(CpuTopology topology) {
        if (topology == null || !Tuning.enabled) {
            return;
        }
        long pid = ProcessHandle.current().pid();
        BindMode mode = Tuning.bindMode == null ? BindMode.AUTO : Tuning.bindMode;
        if (mode == BindMode.AUTO) {
            mode = autoMode(topology);
        }
        long mask = parseMask(Tuning.affinityMask);
        if (Tuning.enabled) {
            if (mode == BindMode.P_CORES && mask == 0L) {
                mask = topology.pMask;
            }
            if (mask != 0L && (topology.logical > 63 || Long.bitCount(mask) >= topology.logical)) {
                mask = 0L;
            }
        }
        if (mode == BindMode.OFF) {
            mask = 0L;
        }
        boolean bind = mask != 0L;
        if (Command.WINDOWS) {
            windows(pid, mask, Tuning.processPriority);
        } else if (Command.LINUX) {
            linux(pid, mask);
        } else if (Command.MAC) {
            if (bind) {
                LOGGER.info("CPU Optimizer: macOS 不支持核心绑定, 已跳过绑定");
            }
        }
        if (bind) {
            pCoreBound = mode == BindMode.P_CORES;
            LOGGER.info("CPU Optimizer: 已绑定 {} 个逻辑处理器, 掩码 0x{}", Long.bitCount(mask), Long.toHexString(mask));
            if (pCoreBound) {
                LOGGER.info("CPU Optimizer: P 核绑定生效, 区块生成与渲染缓冲区线程将在 P 核内与游戏线程竞争, 已准备下调后台线程优先级");
            }
        } else if (!topology.hybrid) {
            LOGGER.info("CPU Optimizer: 未检测到大小核架构, 全部核心可用, 不锁定核心");
        } else {
            LOGGER.info("CPU Optimizer: 不锁定核心, 全部核心参与区块生成和内存回收");
        }
    }

    private static BindMode autoMode(CpuTopology topology) {
        if (!topology.hybrid) {
            LOGGER.info("CPU Optimizer: 绑定模式 自动 -> 全核运行, 原因 未检测到大小核");
            return BindMode.ALL;
        }
        if (topology.pCores > 0 && topology.logical <= 63) {
            LOGGER.info("CPU Optimizer: 绑定模式 自动 -> P 核锁定, 原因 检测到 {} 个 P 核, 游戏线程与内存回收优先占用 P 核", topology.pCores);
            return BindMode.P_CORES;
        }
        LOGGER.info("CPU Optimizer: 绑定模式 自动 -> 全核运行, 原因 无法取得 P 核掩码或逻辑处理器超过 63 个");
        return BindMode.ALL;
    }

    private static void windows(long pid, long mask, PriorityMode priority) {
        StringBuilder script = new StringBuilder("$ErrorActionPreference='SilentlyContinue';$p=Get-Process -Id ");
        script.append(pid).append(';');
        if (mask != 0L) {
            script.append("$p.ProcessorAffinity=[IntPtr][int64]").append(mask).append(';');
        }
        if (priority != PriorityMode.DEFAULT) {
            script.append("$p.PriorityClass='");
            script.append(priority == PriorityMode.HIGH ? "High" : "AboveNormal");
            script.append("';");
        }
        String out = Command.run(15000, "powershell", "-NoProfile", "-NonInteractive", "-Command", script.toString());
        String lower = out.toLowerCase(Locale.ROOT);
        if (lower.contains("拒绝访问") || lower.contains("access is denied") || lower.contains("exception")) {
            LOGGER.warn("CPU Optimizer: 进程绑定或优先级设置失败, 输出: {}", out.trim());
        }
    }

    private static void linux(long pid, long mask) {
        if (mask == 0L) {
            return;
        }
        String hex = Long.toHexString(mask);
        String out = Command.run(8000, "taskset", "-pc", hex, Long.toString(pid));
        if (out.toLowerCase(Locale.ROOT).contains("failed")) {
            LOGGER.warn("CPU Optimizer: taskset 绑定失败: {}", out.trim());
        }
    }

    public static long parseMask(String text) {
        if (text == null) {
            return 0L;
        }
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("0x")) {
            value = value.substring(2);
        }
        if (value.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseUnsignedLong(value, 16);
        } catch (Throwable t) {
            return 0L;
        }
    }
}
