package at.fhv.solver.validation;

// runs in the background and samples JVM memory usage every 5 ms
// for space complexity (peak memory consumption)
public class MemorySampler extends Thread {
    private static final long INTERVAL = 5;
    private final Runtime runtime = Runtime.getRuntime();
    private volatile boolean running = true;
    private volatile long peak;

    @Override
    public void run() {
        while (running) {
            long used = runtime.totalMemory() - runtime.freeMemory();
            if (used > peak) { peak = used; }

            try {
                Thread.sleep(INTERVAL);
            } catch (InterruptedException exception) {
                break;
            }
        }
    }

    public void _stop() {
        running = false;
        interrupt();
    }

    public long getPeak() {
        return peak/(1024 * 1024);
    }
}