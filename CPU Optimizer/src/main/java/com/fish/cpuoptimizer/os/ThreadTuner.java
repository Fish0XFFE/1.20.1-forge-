package com.fish.cpuoptimizer.os;

import com.fish.cpuoptimizer.Tuning;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

public final class ThreadTuner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[] HOT_THREADS = {"Render thread", "Server thread", "Client thread"};
    private static final String[] BACKGROUND_PREFIXES = {
        "Worker-Main-", "IO-Worker-", "Chunk Render Task Executor", "Netty ", "Download-", "Timer hack", "Snooper Timer"
    };
    private static final String BACKGROUND_MAIN = "Main";

    private ThreadTuner() {
    }

    public static void tune() {
        if (!Tuning.enabled || !Tuning.tuneThreads) {
            return;
        }
        try {
            ThreadGroup root = Thread.currentThread().getThreadGroup();
            while (root.getParent() != null) {
                root = root.getParent();
            }
            int target = Math.min(Thread.MAX_PRIORITY, Thread.NORM_PRIORITY + 1);
            int background = Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1);
            boolean lowerBackground = Tuning.deprioritizeBackground && ProcessBinder.pCoreBound;
            List<String> tuned = new ArrayList<>();
            int lowered = 0;
            Thread[] threads = new Thread[root.activeCount() + 64];
            int count = root.enumerate(threads, true);
            for (int i = 0; i < count; i++) {
                Thread thread = threads[i];
                if (thread == null) {
                    continue;
                }
                String name = thread.getName();
                if (name == null) {
                    continue;
                }
                if (isHot(name)) {
                    if (thread.getPriority() < target) {
                        try {
                            thread.setPriority(target);
                            tuned.add(name);
                        } catch (Throwable ignored) {
                        }
                    }
                    continue;
                }
                if (lowerBackground && isBackground(name) && thread.getPriority() > background) {
                    try {
                        thread.setPriority(background);
                        lowered++;
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (!tuned.isEmpty()) {
                LOGGER.info("CPU Optimizer: 已把 {} 提升线程优先级", tuned);
            }
            if (lowered > 0 && Tuning.logDetail) {
                LOGGER.info("CPU Optimizer: 已把 {} 个后台线程降一档优先级, 让渲染与服务端线程优先占用 P 核", lowered);
            }
        } catch (Throwable t) {
            LOGGER.warn("CPU Optimizer: 提升线程优先级失败", t);
        }
    }

    private static boolean isHot(String name) {
        for (int i = 0; i < HOT_THREADS.length; i++) {
            if (name.equals(HOT_THREADS[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBackground(String name) {
        if (name.equals(BACKGROUND_MAIN)) {
            return true;
        }
        for (int i = 0; i < BACKGROUND_PREFIXES.length; i++) {
            if (name.startsWith(BACKGROUND_PREFIXES[i])) {
                return true;
            }
        }
        return false;
    }
}
