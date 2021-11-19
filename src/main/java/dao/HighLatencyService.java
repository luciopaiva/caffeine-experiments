package dao;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class HighLatencyService {

    private final Map<Integer, String> stringById = new HashMap<>();
    private final AtomicInteger accessCount = new AtomicInteger();
    private final int latencyInMillis;

    public HighLatencyService(int latencyInMillis) {
        this.latencyInMillis = latencyInMillis;

        stringById.put(1, "foo");
        stringById.put(2, "bar");
    }

    public String fetchById(int id) {
        sleep(latencyInMillis);
        accessCount.incrementAndGet();
        return stringById.get(id);
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
