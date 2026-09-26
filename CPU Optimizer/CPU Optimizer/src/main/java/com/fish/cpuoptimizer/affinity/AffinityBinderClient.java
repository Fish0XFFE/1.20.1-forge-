package com.fish.cpuoptimizer.affinity;

import com.fish.cpuoptimizer.CpuOptimizerMod;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class AffinityBinderClient {
    private static String cpuVendor = "Unknown";
    private static String cpuName = "";
    private static boolean isHybrid = false;
    private static int pCoreThreads = 0;

    static {
        detectCPU();
    }

    private static void detectCPU() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                String[] cmd = {"powershell", "-NoProfile", "-Command",
                        "Get-CimInstance -ClassName Win32_Processor | Select-Object -First 1 | ForEach-Object { $_.Name + '|' + $_.NumberOfCores + '|' + $_.NumberOfLogicalProcessors }"};
                Process proc = Runtime.getRuntime().exec(cmd);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.contains("|")) {
                            String[] parts = line.split("\\|");
                            if (parts.length >= 3) {
                                cpuName = parts[0].trim();
                                int physical = Integer.parseInt(parts[1].trim());
                                int logical = Integer.parseInt(parts[2].trim());
                                if (cpuName.contains("Intel")) cpuVendor = "Intel";
                                else if (cpuName.contains("AMD")) cpuVendor = "AMD";
                                else if (cpuName.contains("Ultra")) cpuVendor = "IntelUltra";
                                if (logical > physical) {
                                    isHybrid = true;
                                    pCoreThreads = (logical - physical) * 2;
                                    if (pCoreThreads <= 0) pCoreThreads = logical - physical;
                                }
                            }
                        }
                    }
                }
            } else {
                Process proc = Runtime.getRuntime().exec("cat /proc/cpuinfo | grep 'model name' | head -1");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line = reader.readLine();
                    if (line != null) cpuName = line;
                }
                if (cpuName.contains("Intel")) cpuVendor = "Intel";
                else if (cpuName.contains("AMD")) cpuVendor = "AMD";
            }
        } catch (Exception e) {
            CpuOptimizerMod.LOGGER.warn("CPU检测失败: {}", e.getMessage());
        }
    }

    public static void tryAutoBind() {
        try {
            long pid = ProcessHandle.current().pid();
            String os = System.getProperty("os.name").toLowerCase();
            int mask = 0;
            int cores = Runtime.getRuntime().availableProcessors();

            if (cpuVendor.equals("AMD")) {
                mask = (cores >= 64) ? -1 : (1 << cores) - 1;
                CpuOptimizerMod.LOGGER.info("AMD平台，绑定全部 {} 个逻辑核心", cores);
            } else if (isHybrid && pCoreThreads > 0) {
                mask = (1 << pCoreThreads) - 1;
                CpuOptimizerMod.LOGGER.info("检测到混合架构，P核线程数={}，绑定掩码: 0x{}",
                        pCoreThreads, Integer.toHexString(mask));
            } else if (cpuVendor.equals("IntelUltra")) {
                mask = (1 << Math.min(cores, 16)) - 1;
                CpuOptimizerMod.LOGGER.info("Intel Ultra平台，绑定前 {} 个核心", Math.min(cores, 16));
            } else {
                int bindCount = Math.min(8, cores);
                mask = (1 << bindCount) - 1;
                CpuOptimizerMod.LOGGER.info("非混合架构，绑定前 {} 个核心", bindCount);
            }

            if (mask == 0) return;

            boolean success = false;
            if (os.contains("win")) {
                Process proc = Runtime.getRuntime().exec(new String[]{"powershell", "-NoProfile", "-Command",
                        "$p = Get-Process -Id " + pid + "; $p.ProcessorAffinity = " + mask});
                success = proc.waitFor() == 0;
                if (success) {
                    Runtime.getRuntime().exec("powercfg -duplicatescheme e9a42b02-d5df-448d-aa00-03f14749eb61");
                    Runtime.getRuntime().exec("powercfg -setactive e9a42b02-d5df-448d-aa00-03f14749eb61");
                    Runtime.getRuntime().exec("powercfg /setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMIN 100");
                    Runtime.getRuntime().exec("powercfg /setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMAX 100");
                    Runtime.getRuntime().exec("powercfg /setacvalueindex SCHEME_CURRENT SUB_PROCESSOR CPMINCORES 100");
                    Runtime.getRuntime().exec("powercfg /setacvalueindex SCHEME_CURRENT SUB_PROCESSOR CPMAXCORES 100");
                    Runtime.getRuntime().exec("powercfg /setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PERFINCPOL 2");
                    Runtime.getRuntime().exec("powercfg /setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PERFDECPOL 1");
                    Runtime.getRuntime().exec("powercfg /setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PERFINCTHRESHOLD 10");
                    Runtime.getRuntime().exec("powercfg /setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PERFDECTHRESHOLD 8");
                    Runtime.getRuntime().exec("powercfg /setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PERFBOOSTMODE 2");
                    Runtime.getRuntime().exec("powercfg /setactive SCHEME_CURRENT");
                    CpuOptimizerMod.LOGGER.info("已切换卓越性能，P核优先调度，Boost Mode=Aggressive");
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
                CpuOptimizerMod.LOGGER.info("CPU亲和性绑定成功，掩码: 0x{}", Integer.toHexString(mask));
            } else {
                CpuOptimizerMod.LOGGER.warn("绑定失败，请检查管理员权限");
            }
        } catch (Exception e) {
            CpuOptimizerMod.LOGGER.error("绑定异常", e);
        }
    }
}