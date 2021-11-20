import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dao.HighLatencyService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static util.Util.awaitAll;
import static util.Util.time;

public class CaffeineTest {

    private final Logger logger = LogManager.getLogger(CaffeineTest.class);
    private final int latencyInMillis = 1000;
    private final int maximumConcurrentTasks = 8;

    private final ExecutorService executor = Executors.newFixedThreadPool(maximumConcurrentTasks);
    private final List<Future<?>> futures = new ArrayList<>();

    private HighLatencyService service;
    private Cache<Integer, String> cache;

    @BeforeEach
    public void setup() {
        futures.clear();

        service = new HighLatencyService(latencyInMillis);
        cache = prepareCacheBuilder().build();
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
        assertTrue(elapsed < 2 * latencyInMillis, "Fetches are not supposed to happen sequentially");
    }

    /**
     * This test increases the number of concurrent threads to a number where locks because of one key start to affect
     * other keys.
     */
    @Test
    public void testMultiConsumerManyDifferentKeys() {
        logger.info("Starting...");
        long startTime = time();
        for (int i = 0; i < maximumConcurrentTasks; i++) {
            int index = i + 1;
            submit(() -> task(index));
        }
        waitForTasks();
        long elapsed = time() - startTime;
        logger.info("Done.");

        assertEquals(maximumConcurrentTasks, cache.stats().missCount());
        assertEquals(maximumConcurrentTasks, service.getAccessCount());
        assertTrue(elapsed > 2 * latencyInMillis, "Fetches WILL block one another here");
    }

    /**
     * This simulates the same scenario as `testMultiConsumerManyDifferentKeys()`, but avoids contention by replacing
     * `Cache` with `AsyncCache`.
     */
    @Test
    public void testMultiConsumerManyDifferentKeysAsync() {
        AsyncCache<Integer, String> cache = prepareCacheBuilder().buildAsync();

        logger.info("Starting...");
        long startTime = time();
        for (int i = 0; i < maximumConcurrentTasks; i++) {
            int index = i + 1;
            submitAsync(() -> taskAsync(index, cache));
        }
        waitForTasks();
        long elapsed = time() - startTime;
        logger.info("Done.");

        assertEquals(maximumConcurrentTasks, cache.synchronous().stats().missCount());
        assertEquals(maximumConcurrentTasks, service.getAccessCount());
        assertTrue(elapsed < 2 * latencyInMillis, "Fetches will NOT block one another here");
    }

    private void submit(Runnable runnable) {
        futures.add(executor.submit(runnable));
    }

    private void submitAsync(Supplier<CompletableFuture<String>> supplier) {
        futures.add(supplier.get());
    }

    private void task(int id) {
        logger.info("Task is fetching id " + id);
        String value = cache.get(id, k -> {
            logger.info(String.format("Cache miss! Will fetch id %d from remote server.", id));
            return service.fetchById(id);
        });
        logger.info("Task completed with value: " + value);
    }

    private CompletableFuture<String> taskAsync(int id, AsyncCache<Integer, String> asyncCache) {
        logger.info("Task is fetching id " + id);
        return asyncCache.get(id, k -> {
            logger.info(String.format("Cache miss! Will fetch id %d from remote server.", id));
            return service.fetchById(id);
        }).thenApply(value -> {
            logger.info("Task completed with value: " + value);
            return value;
        });
    }

    private void waitForTasks() {
        awaitAll(futures);
    }

    private Caffeine<Object, Object> prepareCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .maximumSize(10)
                .recordStats()
                .initialCapacity(2);
    }
}
