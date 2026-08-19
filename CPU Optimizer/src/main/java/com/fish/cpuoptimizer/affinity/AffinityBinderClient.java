package com.fish.cpuoptimizer.affinity;

import com.fish.cpuoptimizer.CpuOptimizerMod;
import com.fish.cpuoptimizer.config.Config;

public class AffinityBinderClient {
    public static void tryAutoBind() {
        if (!Config.CLIENT.autoAffinity.get()) {
            CpuOptimizerMod.LOGGER.info("客户端自动绑定已禁用");
            return;
        }

        int cores = Runtime.getRuntime().availableProcessors();
        if (Config.CLIENT.skipOldCpu.get() && cores <= 8) {
            CpuOptimizerMod.LOGGER.info("核心数 {} (≤8)，判定为老CPU，跳过绑定", cores);
            return;
        }

        long pid = ProcessHandle.current().pid();
        String os = System.getProperty("os.name").toLowerCase();
        int mask = 0;

        // 1. 优先使用手动掩码（用户覆盖）
        String maskHex = Config.CLIENT.affinityMaskHex.get();
        if (maskHex != null && !maskHex.trim().isEmpty()) {
            try {
                mask = Integer.parseInt(maskHex.trim().replace("0x", ""), 16);
                CpuOptimizerMod.LOGGER.info("使用手动掩码: {}", maskHex);
            } catch (NumberFormatException e) {
                CpuOptimizerMod.LOGGER.warn("手动掩码格式错误，忽略");
            }
        }

        // 2. 其次使用核心范围
        if (mask == 0) {
            String range = Config.CLIENT.affinityCoreRange.get();
            if (range != null && !range.trim().isEmpty()) {
                try {
                    mask = parseCoreRange(range);
                    CpuOptimizerMod.LOGGER.info("使用核心范围: {}", range);
                } catch (Exception e) {
                    CpuOptimizerMod.LOGGER.warn("核心范围解析失败: {}", e.getMessage());
                }
            }
        }

        // 3. 自动计算：绑定所有逻辑核心（解决绑定E核问题）
        if (mask == 0) {
            // 绑定所有核心（即掩码全1）
            if (cores >= 64) {
                // 防止掩码溢出（超过64位）
                mask = -1; // 全1
            } else {
                mask = (1 << cores) - 1;
            }
            CpuOptimizerMod.LOGGER.info("自动绑定所有 {} 个逻辑核心 (掩码 0x{})", cores, Integer.toHexString(mask));
            CpuOptimizerMod.LOGGER.info("💡 若想只绑定P核（性能核），请在 config/cpuoptimizer-client.toml 中设置 affinityCoreRange");
        }

        if (mask == 0) {
            CpuOptimizerMod.LOGGER.info("掩码为0，跳过绑定");
            return;
        }

        CpuOptimizerMod.LOGGER.info("尝试绑定进程 {} 到掩码 0x{}", pid, Integer.toHexString(mask));

        boolean success = false;
        try {
            if (os.contains("win")) {
                Process proc = Runtime.getRuntime().exec(new String[]{"powershell", "-Command",
                        "$p = Get-Process -Id " + pid + "; $p.ProcessorAffinity = " + mask});
                success = proc.waitFor() == 0;
            } else if (os.contains("linux")) {
                String hex = Integer.toHexString(mask);
                Process proc = Runtime.getRuntime().exec(new String[]{"taskset", "-p", hex, String.valueOf(pid)});
                success = proc.waitFor() == 0;
            } else {
                CpuOptimizerMod.LOGGER.warn("非Windows/Linux，跳过绑定");
                return;
            }
        } catch (Exception e) {
            CpuOptimizerMod.LOGGER.error("绑定失败: {}", e.getMessage());
        }

        if (success) {
            CpuOptimizerMod.LOGGER.info("✅ CPU亲和性绑定成功！");
        } else {
            CpuOptimizerMod.LOGGER.warn("❌ 绑定失败，请手动在任务管理器设置相关性");
        }
    }

    // 解析核心范围，如 "0-5" 或 "0,2,4,6"
    private static int parseCoreRange(String range) throws Exception {
        range = range.trim();
        int mask = 0;
        if (range.contains("-")) {
            String[] parts = range.split("-");
            if (parts.length != 2) throw new IllegalArgumentException("范围格式错误");
            int start = Integer.parseInt(parts[0].trim());
            int end = Integer.parseInt(parts[1].trim());
            for (int i = start; i <= end; i++) {
                if (i >= 63) break;
                mask |= (1L << i);
            }
        } else if (range.contains(",")) {
            String[] parts = range.split(",");
            for (String p : parts) {
                int core = Integer.parseInt(p.trim());
                if (core >= 63) continue;
                mask |= (1L << core);
            }
        } else {
            int core = Integer.parseInt(range.trim());
            if (core < 63) mask |= (1L << core);
        }
        return mask;
    }
}