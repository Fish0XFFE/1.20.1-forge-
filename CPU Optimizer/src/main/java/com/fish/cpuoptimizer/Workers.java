package com.fish.cpuoptimizer;

import com.mojang.logging.LogUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;

public final class Workers {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, new Factory());

    private Workers() {
    }

    public static void submit(Runnable task) {
        try {
            POOL.execute(task);
        } catch (Throwable t) {
            LOGGER.warn("CPU Optimizer 后台任务提交失败", t);
        }
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Factory implements ThreadFactory {
        private int index;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(null, runnable, "cpuoptimizer-worker-" + (++index), 262144L);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }
    }
}
