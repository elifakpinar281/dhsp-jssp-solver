package at.fhv.solver.validation;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.ArrayList;
import java.util.List;

// runs in the background and samples JVM memory usage every 5 ms
// for space complexity (peak memory consumption)
public class MemorySampler implements Runnable {
    private static final long INTERVAL = 5;
    private final Runtime runtime = Runtime.getRuntime();
    private final List<MemoryPoolMXBean> heapPools = new ArrayList<>();
    private volatile boolean running = true;
    private volatile long peakUsed;
    private volatile long peakAfterGc;
    private Thread thread;

    public MemorySampler() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP && pool.getCollectionUsage() != null) {
                heapPools.add(pool);
            }
        }
    }

    @Override
    public void run() {
        while (running) {
            sample();
            try {
                Thread.sleep(INTERVAL);
            } catch (InterruptedException exception) {
                break;
            }
        }
        sample();
    }

    private void sample() {
        long used = runtime.totalMemory() - runtime.freeMemory();
        if (used > peakUsed) { peakUsed = used; }

        long afterGc = 0;
        for (MemoryPoolMXBean pool : heapPools) {
            afterGc = afterGc + pool.getCollectionUsage().getUsed();
        }

        if (afterGc > peakAfterGc) {peakAfterGc = afterGc; }
    }

    public void start() {
        for (MemoryPoolMXBean pool : heapPools) {
            pool.resetPeakUsage();
        }

        thread = new Thread(this, "memory-sampler");
        thread.setDaemon(true);
        thread.start();
    }

    public void shutdown() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public long getPeakKb() {
        long bytes = peakAfterGc > 0 ? peakAfterGc : peakUsed;
        return bytes / 1024;
    }

    public boolean isAfterGc() { return peakAfterGc > 0; }
    public long getPeak() { return peakUsed / (1024 * 1024); }
}