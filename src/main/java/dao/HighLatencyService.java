package dao;

import java.util.concurrent.atomic.AtomicInteger;

public class HighLatencyService {

    private final AtomicInteger accessCount = new AtomicInteger();
    private final int latencyInMillis;

    public HighLatencyService(int latencyInMillis) {
        this.latencyInMillis = latencyInMillis;
    }

    public String fetchById(int id) {
        sleep(latencyInMillis);
        accessCount.incrementAndGet();
        return String.valueOf(id);
    }

    public int getAccessCount() {
        return accessCount.get();
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
