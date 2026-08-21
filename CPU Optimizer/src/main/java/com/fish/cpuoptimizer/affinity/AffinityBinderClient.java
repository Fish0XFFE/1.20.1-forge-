package com.fish.cpuoptimizer.affinity;

import com.fish.cpuoptimizer.CpuOptimizerMod;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class AffinityBinderClient {
    private static boolean isIntel12_14Gen = false;
    private static int pCoreLogicalCount = 0;

    static {
        detectCPU();
    }

    private static void detectCPU() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String cpuName = "";
            if (os.contains("win")) {
                Process proc = Runtime.getRuntime().exec("wmic cpu get name");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().startsWith("Intel")) {
                            cpuName = line.trim();
                            break;
                        }
                    }
                }
            } else if (os.contains("linux")) {
                Process proc = Runtime.getRuntime().exec("cat /proc/cpuinfo | grep 'model name' | head -1");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null && line.contains("Intel")) {
                        cpuName = line;
                    }
                }
            }
            if (cpuName.contains("Intel")) {
                String[] parts = cpuName.split(" ");
                for (String p : parts) {
                    if (p.startsWith("12") || p.startsWith("13") || p.startsWith("14")) {
                        isIntel12_14Gen = true;
                        break;
                    }
                }
            }
            if (isIntel12_14Gen) {
                int totalCores = Runtime.getRuntime().availableProcessors();
                if (os.contains("win")) {
                    Process proc = Runtime.getRuntime().exec("wmic cpu get NumberOfCores");
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.trim().matches("\\d+")) {
                                int physicalCores = Integer.parseInt(line.trim());
                                pCoreLogicalCount = physicalCores * 2;
                                if (pCoreLogicalCount > totalCores) pCoreLogicalCount = totalCores;
                                break;
                            }
                        }
                    }
                } else {
                    Process proc = Runtime.getRuntime().exec("grep -c '^processor' /proc/cpuinfo");
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                        String line = reader.readLine();
                        if (line != null) {
                            int logical = Integer.parseInt(line.trim());
                            Process proc2 = Runtime.getRuntime().exec("grep -c '^physical id' /proc/cpuinfo | sort -u");
                            try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(proc2.getInputStream()))) {
                                String line2 = reader2.readLine();
                                if (line2 != null) {
                                    int physical = Integer.parseInt(line2.trim());
                                    pCoreLogicalCount = physical * 2;
                                    if (pCoreLogicalCount > logical) pCoreLogicalCount = logical;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            CpuOptimizerMod.LOGGER.warn("CPU检测失败，将使用全核心绑定");
        }
    }

    public static void tryAutoBind() {
        try {
            long pid = ProcessHandle.current().pid();
            String os = System.getProperty("os.name").toLowerCase();
            int mask;
            if (isIntel12_14Gen && pCoreLogicalCount > 0) {
                mask = (1 << pCoreLogicalCount) - 1;
                CpuOptimizerMod.LOGGER.info("检测到 Intel 12-14 代 CPU，绑定前 {} 个逻辑核心（P核+超线程）", pCoreLogicalCount);
            } else {
                int cores = Runtime.getRuntime().availableProcessors();
                mask = (cores >= 64) ? -1 : (1 << cores) - 1;
                CpuOptimizerMod.LOGGER.info("非 Intel 12-14 代或检测失败，绑定全部 {} 个逻辑核心", cores);
            }
            if (mask == 0) return;
            boolean success = false;
            if (os.contains("win")) {
                Process proc = Runtime.getRuntime().exec(new String[]{"powershell", "-Command",
                        "$p = Get-Process -Id " + pid + "; $p.ProcessorAffinity = " + mask});
                success = proc.waitFor() == 0;
                if (success) {
                    Runtime.getRuntime().exec("powercfg -setactive 8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c");
                    CpuOptimizerMod.LOGGER.info("已切换电源计划为高性能");
                }
            } else if (os.contains("linux")) {
                String hex = Integer.toHexString(mask);
                Process proc = Runtime.getRuntime().exec(new String[]{"taskset", "-p", hex, String.valueOf(pid)});
                success = proc.waitFor() == 0;
                try {
                    Runtime.getRuntime().exec("sudo cpupower frequency-set -g performance");
                } catch (Exception ignored) {}
            }
            if (success) {
                CpuOptimizerMod.LOGGER.info("CPU亲和性绑定成功，掩码: 0x" + Integer.toHexString(mask));
            } else {
                CpuOptimizerMod.LOGGER.warn("绑定失败，请手动设置");
            }
        } catch (Exception e) {
            CpuOptimizerMod.LOGGER.error("绑定异常", e);
        }
    }
}