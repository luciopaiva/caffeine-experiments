import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dao.HighLatencyService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static util.Util.awaitAll;
import static util.Util.time;

public class CaffeineTest {

    private final Logger logger = LogManager.getLogger(CaffeineTest.class);
    private final int latencyInMillis = 1000;
    private final int maximumConcurrentTasks = 5;

    private final ExecutorService executor = Executors.newFixedThreadPool(maximumConcurrentTasks);
    private final List<Future<?>> futures = new ArrayList<>();

    private HighLatencyService service;
    private Cache<Integer, String> cache;

    @BeforeEach
    public void setup() {
        futures.clear();

        service = new HighLatencyService(latencyInMillis);
        cache = Caffeine.newBuilder()
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .maximumSize(10)
                .recordStats()
                .build();
    }

    @Test
    public void testSingleConsumer() {
        logger.info("Starting...");
        String value = cache.get(1, k -> service.fetchById(1));
        logger.info(value);
    }

    /**
     * In this test, we see that two concurrent requests for the same key will only yield one miss. One of the
     * requests will trigger a server fetch, but the other will block and wait. In the end, only a single miss will
     * be recorded in the stats.
     */
    @Test
    public void testMultiConsumerSameKey() {
        logger.info("Starting...");
        submit(() -> task(1));
        submit(() -> task(1));
        waitForTasks();
        logger.info("Done.");

        assertEquals(1, cache.stats().missCount());
        assertEquals(1, service.getAccessCount());
    }

    /**
     * In this test, two concurrent requests for different keys should not block each other.
     */
    @Test
    public void testMultiConsumerDifferentKeys() {
        logger.info("Starting...");
        long startTime = time();
        submit(() -> task(1));
        submit(() -> task(2));
        waitForTasks();
        long elapsed = time() - startTime;
        logger.info("Done.");

        assertEquals(2, cache.stats().missCount());
        assertEquals(2, service.getAccessCount());
        assertTrue(elapsed < 2 * latencyInMillis, "Fetches are not supposed to be sequential");
    }

    private void submit(Runnable runnable) {
        futures.add(executor.submit(runnable));
    }

    private void waitForTasks() {
        awaitAll(futures);
    }

    private void task(int id) {
        logger.info("Task is fetching id " + id);
        String value = cache.get(id, k -> {
            logger.info(String.format("Cache miss! Will fetch id %d from remote server.", id));
            return service.fetchById(id);
        });
        logger.info("Task completed with value: " + value);
    }
}
