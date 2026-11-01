package com.fish.cpuoptimizer.cpu;

import com.fish.cpuoptimizer.util.Command;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CpuTopology {
    public final String os;
    public final String arch;
    public final String brand;
    public final String vendor;
    public final String isa;
    public final String source;
    public final int logical;
    public final int physical;
    public final int pCores;
    public final int eCores;
    public final long pMask;
    public final boolean hybrid;

    private CpuTopology(String os, String arch, String brand, String vendor, String isa, String source,
                        int logical, int physical, int pCores, int eCores, long pMask, boolean hybrid) {
        this.os = os;
        this.arch = arch;
        this.brand = brand;
        this.vendor = vendor;
        this.isa = isa;
        this.source = source;
        this.logical = logical;
        this.physical = physical;
        this.pCores = pCores;
        this.eCores = eCores;
        this.pMask = pMask;
        this.hybrid = hybrid;
    }

    public static CpuTopology detect() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        int logical = Math.max(1, Runtime.getRuntime().availableProcessors());
        if (arch.contains("aarch64") || arch.contains("arm64") || arch.startsWith("arm")) {
            return detectArm(os, arch, logical);
        }
        if (os.contains("win")) {
            return detectWindows(arch, logical);
        }
        if (os.contains("linux")) {
            return detectLinux(arch, logical);
        }
        if (os.contains("mac")) {
            return detectMac(arch, logical);
        }
        return build(os, arch, "未知处理器", "未知", "SSE2", "默认", logical, logical, -1, 0, 0L);
    }

    private static CpuTopology detectWindows(String arch, int available) {
        String brand = System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "");
        String vendor = "";
        int logical = available;
        int physical = available;
        String source = "系统默认";
        String[] parts = queryWindowsProcessor();
        if (parts != null) {
            String name = parts[0].trim();
            int cores = parseInt(parts[1], 0);
            int threads = parseInt(parts[2], 0);
            if (!name.isEmpty() && cores > 0 && threads > 0) {
                brand = name;
                physical = cores;
                logical = threads;
                vendor = parts[3].trim();
                source = "PowerShell 查询";
            }
        }
        if (brand.isEmpty()) {
            brand = arch.isEmpty() ? "未知处理器" : arch;
        }
        String isa = inferIsa(brand, vendor, arch, source);
        return build("windows", arch, brand, vendor, isa, source, logical, physical, -1, 0, 0L);
    }

    private static CpuTopology detectLinux(String arch, int available) {
        String brand = "";
        String vendor = "";
        String flags = "";
        int physical = available;
        int logical = available;
        String source = "/proc/cpuinfo";
        try {
            List<String> lines = Files.readAllLines(Path.of("/proc/cpuinfo"), StandardCharsets.UTF_8);
            Set<String> cores = new HashSet<>();
            String physicalId = "0";
            int count = 0;
            for (String line : lines) {
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (key.equals("processor")) {
                    count++;
                } else if ((key.equals("model name") || key.equals("Model")) && brand.isEmpty()) {
                    brand = value;
                } else if ((key.equals("vendor_id") || key.equals("CPU implementer")) && vendor.isEmpty()) {
                    vendor = value;
                } else if (key.equals("flags")) {
                    flags = value;
                } else if (key.equals("Features") && flags.isEmpty()) {
                    flags = value;
                } else if (key.equals("physical id")) {
                    physicalId = value;
                } else if (key.equals("core id")) {
                    cores.add(physicalId + ":" + value);
                }
            }
            if (count > 0) {
                logical = count;
            }
            if (!cores.isEmpty()) {
                physical = cores.size();
            }
        } catch (Throwable ignored) {
        }
        long capacityMask = capacityMask(logical);
        int forcedP = Long.bitCount(capacityMask);
        int forcedE = capacityMask == 0L ? 0 : logical - forcedP;
        String isa = isaFromFlags(flags);
        if (isa.isEmpty()) {
            isa = inferIsa(brand, vendor, arch, source);
        }
        return build("linux", arch, brand, vendor, isa, source, logical, physical, forcedP > 0 && forcedE > 0 ? forcedP : -1, forcedE, capacityMask);
    }

    private static CpuTopology detectMac(String arch, int available) {
        boolean arm = arch.contains("arm");
        int p = parseInt(Command.run(3000, "sysctl", "-n", "hw.perflevel0.logicalcpu"), 0);
        int e = parseInt(Command.run(3000, "sysctl", "-n", "hw.perflevel1.logicalcpu"), 0);
        String brand = arm ? "Apple Silicon" : parseString(Command.run(3000, "sysctl", "-n", "machdep.cpu.brand_string"));
        String isa = arm ? "NEON, AES, CRC32" : inferIsa(brand, "intel", arch, "sysctl");
        int logical = p > 0 && e > 0 ? p + e : available;
        return build("mac", arch, brand, "apple", isa, "sysctl", logical, logical, p > 0 ? p : -1, e, 0L);
    }

    private static CpuTopology detectArm(String os, String arch, int logical) {
        String brand = System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", arch);
        String isa = "NEON, AES, CRC32";
        String source = "系统属性";
        int p = -1;
        int e = 0;
        long mask = 0L;
        if (os.contains("win")) {
            String[] parts = queryWindowsProcessor();
            if (parts != null && !parts[0].isEmpty()) {
                brand = parts[0];
                source = "PowerShell 查询";
            }
        } else if (os.contains("mac")) {
            String chip = Command.run(3000, "sysctl", "-n", "machdep.cpu.brand_string").trim();
            brand = chip.isEmpty() ? "Apple Silicon" : chip;
            p = parseInt(Command.run(3000, "sysctl", "-n", "hw.perflevel0.logicalcpu"), 0);
            e = parseInt(Command.run(3000, "sysctl", "-n", "hw.perflevel1.logicalcpu"), 0);
            source = "sysctl";
        } else {
            source = "/proc/cpuinfo";
            try {
                List<String> lines = Files.readAllLines(Path.of("/proc/cpuinfo"), StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line.startsWith("model name") || line.startsWith("Hardware") || line.startsWith("Model")) {
                        int colon = line.indexOf(':');
                        if (colon > 0) {
                            brand = line.substring(colon + 1).trim();
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            mask = capacityMask(logical);
            if (mask != 0L) {
                p = Long.bitCount(mask);
                e = Math.max(0, logical - p);
            }
        }
        if (isDomestic(brand)) {
            isa = isa + " (国产 ARM64)";
        }
        return build(os, arch, brand, "arm", isa, source, logical, logical, p, e, mask);
    }

    private static String[] queryWindowsProcessor() {
        String script = "$c=Get-CimInstance Win32_Processor;$f=$c|Select-Object -First 1;"
                + "$t=($c|Measure-Object -Property NumberOfCores -Sum).Sum;"
                + "$l=($c|Measure-Object -Property NumberOfLogicalProcessors -Sum).Sum;"
                + "Write-Output ($f.Name+'|'+$t+'|'+$l+'|'+$f.Manufacturer)";
        String out = Command.run(10000, "powershell", "-NoProfile", "-NonInteractive", "-Command", script).trim();
        String[] parts = out.split("\\|");
        return parts.length >= 4 ? parts : null;
    }

    private static boolean isDomestic(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("hygon") || lower.contains("海光") || lower.contains("zhaoxin") || lower.contains("兆芯")
                || lower.contains("phytium") || lower.contains("飞腾") || lower.contains("kunpeng") || lower.contains("鲲鹏")
                || lower.contains("loongson") || lower.contains("龙芯") || lower.contains("sunway") || lower.contains("申威");
    }

    private static CpuTopology build(String os, String arch, String brand, String vendor, String isa, String source,
                                     int logical, int physical, int forcedP, int forcedE, long mask) {
        int total = Math.max(1, logical);
        if (physical < 1 || physical > total) {
            physical = total;
        }
        int p;
        int e;
        boolean hybrid;
        if (forcedP > 0) {
            p = Math.min(forcedP, total);
            e = Math.max(0, forcedE);
            hybrid = e > 0 && p < total;
        } else {
            int guessP = total - physical;
            int guessE = 2 * physical - total;
            hybrid = guessP > 0 && guessE > 0 && guessE < physical;
            if (hybrid) {
                p = guessP;
                e = guessE;
            } else {
                p = physical;
                e = 0;
            }
        }
        long finalMask = hybrid ? (mask != 0L ? mask : (2 * p < 64 ? lowBits(2 * p) : 0L)) : 0L;
        return new CpuTopology(os, arch, brand, vendor, isa, source, total, physical,
                hybrid ? p : Math.min(p, total), hybrid ? e : 0, finalMask, hybrid);
    }

    private static long capacityMask(int logical) {
        int max = 0;
        int min = Integer.MAX_VALUE;
        int[] caps = new int[logical];
        for (int cpu = 0; cpu < logical; cpu++) {
            caps[cpu] = readInt(Path.of("/sys/devices/system/cpu/cpu" + cpu + "/cpu_capacity"));
            if (caps[cpu] > max) {
                max = caps[cpu];
            }
            if (caps[cpu] > 0 && caps[cpu] < min) {
                min = caps[cpu];
            }
        }
        if (max <= 0 || min == max) {
            return 0L;
        }
        long mask = 0L;
        for (int cpu = 0; cpu < logical && cpu < 64; cpu++) {
            if (caps[cpu] == max) {
                mask |= 1L << cpu;
            }
        }
        return mask;
    }

    private static long lowBits(int count) {
        if (count <= 0) {
            return 0L;
        }
        if (count >= 64) {
            return -1L;
        }
        return (1L << count) - 1L;
    }

    private static String isaFromFlags(String flags) {
        if (flags.isEmpty()) {
            return "";
        }
        Set<String> set = new HashSet<>(List.of(flags.split("\\s+")));
        StringBuilder builder = new StringBuilder();
        append(builder, set.contains("avx512f"), "AVX-512");
        append(builder, set.contains("avx2"), "AVX2");
        append(builder, set.contains("avx"), "AVX");
        append(builder, set.contains("fma"), "FMA3");
        append(builder, set.contains("sse4_2"), "SSE4.2");
        append(builder, set.contains("asimd") || set.contains("neon"), "NEON");
        append(builder, set.contains("sve"), "SVE");
        append(builder, set.contains("lsx"), "LSX");
        append(builder, set.contains("lasx"), "LASX");
        append(builder, set.contains("rvv"), "RVV");
        append(builder, set.contains("sha1") || set.contains("sha2"), "SHA");
        append(builder, set.contains("aes"), "AES");
        append(builder, set.contains("crc32"), "CRC32");
        return builder.toString();
    }

    private static void append(StringBuilder builder, boolean present, String name) {
        if (!present) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(name);
    }

    private static String inferIsa(String brand, String vendor, String arch, String source) {
        String text = (brand + " " + vendor).toLowerCase(Locale.ROOT);
        String suffix = source.contains("PowerShell") ? " (推断)" : "";
        if (text.contains("hygon") || text.contains("海光")) {
            return "AVX2, FMA3 (国产 x86)" + suffix;
        }
        if (text.contains("zhaoxin") || text.contains("兆芯")) {
            return "AVX2 (国产 x86)" + suffix;
        }
        if (text.contains("phytium") || text.contains("飞腾") || text.contains("kunpeng") || text.contains("鲲鹏")) {
            return "NEON, AES, CRC32 (国产 ARM64)" + suffix;
        }
        if (text.contains("loongson") || text.contains("龙芯") || arch.contains("loongarch")) {
            return "LSX, LASX (国产 LoongArch)" + suffix;
        }
        if (text.contains("sunway") || text.contains("申威")) {
            return "256 位向量 (国产)" + suffix;
        }
        if (arch.contains("riscv")) {
            return "RVV" + suffix;
        }
        if (text.contains("snapdragon") || text.contains("qualcomm") || text.contains("mediatek") || text.contains("dimensity")) {
            return "NEON, AES, CRC32 (ARM64)" + suffix;
        }
        if (arch.contains("arm") || text.contains("apple")) {
            return "NEON, AES, CRC32" + suffix;
        }
        if (text.contains("intel")) {
            int generation = intelGeneration(text);
            if (generation >= 12) {
                return "AVX2, FMA3, F16C" + suffix;
            }
            if (generation >= 11) {
                return "AVX-512, AVX2, FMA3" + suffix;
            }
            if (generation >= 4) {
                return "AVX2, FMA3, F16C" + suffix;
            }
            if (generation >= 2) {
                return "AVX, SSE4.2" + suffix;
            }
            return "SSE4.2" + suffix;
        }
        if (text.contains("amd") || text.contains("ryzen") || text.contains("threadripper") || text.contains("epyc")) {
            int series = amdSeries(text);
            if (series >= 7000 || text.contains("epyc")) {
                return "AVX-512, AVX2, FMA3" + suffix;
            }
            return "AVX2, FMA3" + suffix;
        }
        return "AVX2" + suffix;
    }

    private static int intelGeneration(String text) {
        Matcher generation = Pattern.compile("(\\d{1,2})(?:th|nd|rd|st) gen").matcher(text);
        if (generation.find()) {
            return parseInt(generation.group(1), 0);
        }
        if (text.contains("core ultra")) {
            return 15;
        }
        Matcher model = Pattern.compile("i[3579]-(\\d{3,5})").matcher(text);
        if (model.find()) {
            String digits = model.group(1);
            if (digits.length() == 5) {
                return parseInt(digits.substring(0, 2), 0);
            }
            int first = parseInt(digits.substring(0, 1), 0);
            return first == 1 ? 10 : first;
        }
        return 0;
    }

    private static int amdSeries(String text) {
        Matcher matcher = Pattern.compile("(?:ryzen|threadripper)\\s+\\S+\\s+(\\d{4})").matcher(text);
        if (matcher.find()) {
            return parseInt(matcher.group(1), 0);
        }
        return 0;
    }

    private static int readInt(Path path) {
        try {
            return Integer.parseInt(new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim());
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static String parseString(String text) {
        String value = text.trim();
        return value.isEmpty() ? "未知处理器" : value;
    }

    public boolean canBind() {
        return hybrid && pMask != 0L && logical <= 63;
    }

    public String summary() {
        StringBuilder builder = new StringBuilder();
        builder.append(brand.isEmpty() ? "未知处理器" : brand);
        builder.append(" | ").append(isa);
        builder.append(" | 逻辑 ").append(logical).append(" 线程, 物理 ").append(physical).append(" 核");
        if (hybrid) {
            builder.append(", P 核 ").append(pCores).append(", E 核 ").append(eCores);
        }
        builder.append(" | 来源 ").append(source);
        return builder.toString();
    }
}
