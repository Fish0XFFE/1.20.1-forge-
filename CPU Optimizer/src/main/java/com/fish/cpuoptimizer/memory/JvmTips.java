package com.fish.cpuoptimizer.memory;

import com.fish.cpuoptimizer.cpu.CpuTopology;
import com.mojang.logging.LogUtils;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

public final class JvmTips {
    private static final Logger LOGGER = LogUtils.getLogger();

    private JvmTips() {
    }

    public static void write(CpuTopology topology) {
        try {
            long totalMb = totalMemoryMb();
            int heapMb = recommendHeap(totalMb);
            String heap = Integer.toString(heapMb / 1024);
            StringBuilder builder = new StringBuilder();
            builder.append("CPU Optimizer 推荐启动参数").append(System.lineSeparator());
            builder.append("处理器: ").append(topology.summary()).append(System.lineSeparator());
            builder.append("物理内存: ").append(totalMb > 0 ? totalMb + " MB" : "未知").append(System.lineSeparator());
            builder.append("建议堆内存: ").append(heapMb).append(" MB").append(System.lineSeparator());
            builder.append(System.lineSeparator());
            builder.append("-Xms").append(heap).append("G -Xmx").append(heap).append("G")
                    .append(" -XX:+UseG1GC -XX:MaxGCPauseMillis=30")
                    .append(" -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=45")
                    .append(" -XX:G1HeapRegionSize=8M -XX:MaxTenuringThreshold=1")
                    .append(" -XX:SurvivorRatio=32 -XX:+AlwaysPreTouch -XX:+ParallelRefProcEnabled")
                    .append(" -XX:+UseStringDeduplication -XX:SoftRefLRUPolicyMSPerMB=64")
                    .append(" -XX:+PerfDisableSharedMem")
                    .append(" -XX:+ExplicitGCInvokesConcurrent -XX:G1PeriodicGCInterval=60000")
                    .append(" -XX:+G1PeriodicGCInvokesConcurrent")
                    .append(" -Dmax.bg.threads=").append(backgroundThreads(topology))
                    .append(isaFlag(topology))
                    .append(System.lineSeparator());
            builder.append(System.lineSeparator());
            builder.append("说明").append(System.lineSeparator());
            builder.append("1. -Xms 与 -Xmx 保持一致, 避免运行中扩容造成卡顿").append(System.lineSeparator());
            builder.append("2. 堆超过 12G 时可以把 G1GC 换成 ZGC").append(System.lineSeparator());
            builder.append("3. 指令集参数与处理器不匹配会导致启动失败, 换机器时删掉该参数").append(System.lineSeparator());
            builder.append("4. 服务端可以额外加上 -Dfile.encoding=UTF-8 与 -Djava.awt.headless=true").append(System.lineSeparator());
            builder.append("5. 内存不足的机器不要加大堆, 加大会让回收时间变长").append(System.lineSeparator());
            builder.append("6. -Dmax.bg.threads 限制区块生成与缓冲区构建的线程数, 锁定 P 核之后线程过多只会互相抢占").append(System.lineSeparator());
            builder.append("7. -XX:+ExplicitGCInvokesConcurrent 让主动内存整理走并发回收, 不会整段停顿").append(System.lineSeparator());
            builder.append("8. 堆上限超过 32G 会关闭 -XX:+UseCompressedOops, 对象引用占用反而变大, 不要盲目加大堆").append(System.lineSeparator());
            long currentMb = Runtime.getRuntime().maxMemory() / 1048576L;
            if (currentMb < 2048L) {
                builder.append(System.lineSeparator());
                builder.append("警告: 当前实际堆上限只有 ").append(currentMb).append(" MB, 进入大型存档后回收会非常频繁, 建议至少给到 ")
                        .append(heapMb).append(" MB").append(System.lineSeparator());
            } else if (currentMb > 12288L) {
                builder.append(System.lineSeparator());
                builder.append("警告: 当前实际堆上限为 ").append(currentMb).append(" MB, 堆越大单次回收越久, 且对象跨代存活更久, ")
                        .append("建议改成 ").append(heapMb).append(" MB 左右").append(System.lineSeparator());
            }
            Path path = FMLPaths.CONFIGDIR.get().resolve("cpuoptimizer-jvm.txt");
            Files.createDirectories(path.getParent());
            Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
            LOGGER.info("CPU Optimizer: 推荐 JVM 参数已写入 {}", path);
        } catch (Throwable t) {
            LOGGER.warn("CPU Optimizer: 写入 JVM 参数建议失败", t);
        }
    }

    private static String isaFlag(CpuTopology topology) {
        String isa = topology.isa == null ? "" : topology.isa;
        if (isa.contains("AVX-512")) {
            return " -XX:UseAVX=3";
        }
        if (isa.contains("AVX2")) {
            return " -XX:UseAVX=2";
        }
        if (isa.contains("AVX")) {
            return " -XX:UseAVX=1";
        }
        return "";
    }

    private static int backgroundThreads(CpuTopology topology) {
        int limit = Runtime.getRuntime().availableProcessors() - 1;
        if (topology != null && topology.hybrid && topology.pCores > 0) {
            limit = Math.min(limit, Math.max(4, topology.pCores * 2));
        }
        return Math.max(2, limit);
    }

    private static int recommendHeap(long totalMb) {
        if (totalMb <= 0L) {
            return 4096;
        }
        long heap = totalMb / 4L;
        heap = Math.max(2048L, Math.min(16384L, heap));
        heap = heap / 512L * 512L;
        return (int) heap;
    }

    public static int recommendedHeapMb() {
        return recommendHeap(totalMemoryMb());
    }

    public static long totalMemoryMb() {
        try {
            java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean extended) {
                return extended.getTotalMemorySize() / 1048576L;
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }
}
