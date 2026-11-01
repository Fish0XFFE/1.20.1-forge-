package com.fish.cpuoptimizer.os;

import com.fish.cpuoptimizer.Tuning;
import com.fish.cpuoptimizer.util.Command;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

public final class PowerTuner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern GUID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final String SUB_PROCESSOR = "54533251-82be-4824-96c1-47b60b740d00";
    private static final String PROCTHROTTLEMIN = "893dee8e-2bef-41e0-89c6-b55d0929964c";
    private static final String PROCTHROTTLEMAX = "bc5038f7-23e0-4960-96da-33abaf5935ec";
    private static final String PERFBOOSTMODE = "be337238-0d82-4146-a960-4f3749d470c7";
    private static final String CPMINCORES = "0cc5b647-c1df-4637-891a-dec35c318583";
    private static final String CPMAXCORES = "ea062031-0e34-4ff1-9b6d-eb1059334028";
    private static final String IDLEDISABLE = "5d76a2ca-e8c0-402f-a133-2158492d58ad";
    private static final String ULTIMATE = "e9a42b02-d5df-448d-aa00-03f14749eb61";
    private static final String HIGH_PERFORMANCE = "8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c";
    private static final String THROTTLING_KEY = "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Power\\PowerThrottling";
    private static final Path WINDOWS_STATE = FMLPaths.CONFIGDIR.get().resolve("cpuoptimizer-power.txt");
    private static final Path SCHEME_STATE = FMLPaths.CONFIGDIR.get().resolve("cpuoptimizer-scheme.txt");
    private static final Path LINUX_STATE = FMLPaths.CONFIGDIR.get().resolve("cpuoptimizer-governor.txt");

    private PowerTuner() {
    }

    public static void apply() {
        if (!Tuning.enabled) {
            return;
        }
        if (!Tuning.aggressivePower) {
            if (Tuning.restoreOnDisable) {
                restore();
            }
            return;
        }
        if (Command.WINDOWS) {
            applyWindows();
        } else if (Command.LINUX) {
            applyLinux();
        } else if (Command.MAC) {
            applyMac();
        }
    }

    private static void applyWindows() {
        boolean elevated = isElevated();
        String current = firstGuid(Command.run(6000, "powercfg", "/getactivescheme"));
        if (current != null) {
            saveState(WINDOWS_STATE, current);
        }
        String target = Tuning.ultimatePerformance ? existingScheme() : null;
        String label = "当前方案";
        if (target != null) {
            label = "已保存的高性能方案";
        } else if (Tuning.ultimatePerformance) {
            String duplicate = firstGuid(Command.run(15000, "powercfg", "-duplicatescheme", ULTIMATE));
            if (duplicate == null) {
                duplicate = firstGuid(Command.run(15000, "powercfg", "-duplicatescheme", HIGH_PERFORMANCE));
            }
            if (duplicate != null) {
                target = duplicate;
                label = "高性能方案";
                saveState(SCHEME_STATE, duplicate);
            }
        }
        if (target == null) {
            target = current;
        }
        StringBuilder script = new StringBuilder("$ErrorActionPreference='SilentlyContinue';");
        if (target != null) {
            script.append("powercfg /setactive ").append(target).append(';');
        }
        appendIndexes(script, "ac");
        if (Tuning.applyOnBattery) {
            appendIndexes(script, "dc");
        }
        script.append("powercfg /setactive SCHEME_CURRENT");
        String out = Command.run(25000, "powershell", "-NoProfile", "-NonInteractive", "-Command", script.toString());
        if (Tuning.disablePowerThrottling) {
            Command.fire(10000, "reg", "add", THROTTLING_KEY, "/v", "PowerThrottlingOff", "/t", "REG_DWORD", "/d", "1", "/f");
        }
        boolean denied = out.toLowerCase(Locale.ROOT).contains("拒绝访问") || out.toLowerCase(Locale.ROOT).contains("access is denied");
        if (denied || !elevated) {
            LOGGER.warn("CPU Optimizer: 电源策略写入需要管理员权限, 请以管理员身份启动后再试");
        }
        LOGGER.info("CPU Optimizer: 电源方案已切换到{}", label);
    }

    private static String existingScheme() {
        try {
            if (!Files.exists(SCHEME_STATE)) {
                return null;
            }
            String guid = Files.readString(SCHEME_STATE, StandardCharsets.UTF_8).trim();
            if (!GUID.matcher(guid).matches()) {
                return null;
            }
            String list = Command.run(8000, "powercfg", "-list").toLowerCase(Locale.ROOT);
            return list.contains(guid.toLowerCase(Locale.ROOT)) ? guid : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void appendIndexes(StringBuilder script, String scope) {
        index(script, scope, PROCTHROTTLEMIN, "100");
        index(script, scope, PROCTHROTTLEMAX, "100");
        index(script, scope, PERFBOOSTMODE, "2");
        index(script, scope, CPMINCORES, "100");
        index(script, scope, CPMAXCORES, "100");
        index(script, scope, IDLEDISABLE, "1");
    }

    private static void index(StringBuilder script, String scope, String setting, String value) {
        script.append("powercfg -set").append(scope).append("valueindex SCHEME_CURRENT ")
                .append(SUB_PROCESSOR).append(' ').append(setting).append(' ').append(value).append(';');
    }

    private static void applyLinux() {
        try {
            if (!Files.exists(LINUX_STATE)) {
                Path governorPath = Path.of("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
                if (Files.exists(governorPath)) {
                    saveState(LINUX_STATE, Files.readString(governorPath, StandardCharsets.UTF_8).trim());
                }
            }
            String script = "for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > $f 2>/dev/null; done;"
                    + "for f in /sys/devices/system/cpu/cpu*/cpufreq/energy_performance_preference; do echo performance > $f 2>/dev/null; done;"
                    + "for f in /sys/devices/system/cpu/cpuidle/current_governor; do echo teo > $f 2>/dev/null; done";
            Command.run(8000, "sh", "-c", script);
            LOGGER.info("CPU Optimizer: 已切换 CPU 调频策略为 performance");
        } catch (Throwable t) {
            LOGGER.warn("CPU Optimizer: 写入 CPU 调频策略失败", t);
        }
    }

    private static void applyMac() {
        Command.run(6000, "pmset", "-a", "lowpowermode", "0");
    }

    private static void restore() {
        try {
            if (Command.WINDOWS && Files.exists(WINDOWS_STATE)) {
                String original = Files.readString(WINDOWS_STATE, StandardCharsets.UTF_8).trim();
                if (GUID.matcher(original).matches()) {
                    Command.run(8000, "powercfg", "/setactive", original);
                }
                Command.fire(8000, "reg", "delete", THROTTLING_KEY, "/v", "PowerThrottlingOff", "/f");
                Files.delete(WINDOWS_STATE);
                if (Files.exists(SCHEME_STATE)) {
                    String scheme = Files.readString(SCHEME_STATE, StandardCharsets.UTF_8).trim();
                    if (GUID.matcher(scheme).matches()) {
                        Command.run(8000, "powercfg", "-delete", scheme);
                    }
                    Files.delete(SCHEME_STATE);
                }
                LOGGER.info("CPU Optimizer: 已恢复原始电源方案");
            }
            if (Command.LINUX && Files.exists(LINUX_STATE)) {
                String governor = Files.readString(LINUX_STATE, StandardCharsets.UTF_8).trim();
                if (!governor.isEmpty()) {
                    Command.run(8000, "sh", "-c", "for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo " + governor + " > $f 2>/dev/null; done");
                }
                Files.delete(LINUX_STATE);
                LOGGER.info("CPU Optimizer: 已恢复原始调频策略");
            }
        } catch (Throwable t) {
            LOGGER.warn("CPU Optimizer: 恢复电源设置失败", t);
        }
    }

    private static void saveState(Path path, String value) {
        try {
            if (Files.exists(path)) {
                return;
            }
            Files.createDirectories(path.getParent());
            Files.writeString(path, value + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            LOGGER.warn("CPU Optimizer: 保存原始电源状态失败", t);
        }
    }

    private static boolean isElevated() {
        String out = Command.run(4000, "net", "session");
        String lower = out.toLowerCase(Locale.ROOT);
        return !out.isBlank() && !lower.contains("拒绝访问") && !lower.contains("access is denied");
    }

    private static String firstGuid(String text) {
        Matcher matcher = GUID.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }
}
