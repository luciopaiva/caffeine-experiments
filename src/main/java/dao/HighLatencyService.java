package dao;

import java.util.HashMap;
import java.util.Map;

public class HighLatencyService {

    Map<Integer, String> stringById = new HashMap<>();

    public HighLatencyService() {
        stringById.put(1, "foo");
        stringById.put(2, "bar");
    }

    public String fetchById(int id) {
        sleep(3000);
        return stringById.get(id);
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
