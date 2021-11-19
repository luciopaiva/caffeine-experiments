import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dao.HighLatencyService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

public class SingleConsumerExample {

    private static final Logger logger = LogManager.getLogger(SingleConsumerExample.class);
    private static final HighLatencyService service = new HighLatencyService();

    public static void main(String ...args) {
        Cache<Integer, String> cache = Caffeine.newBuilder()
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .maximumSize(10)
                .recordStats()
                .build();

        logger.info("Starting...");
        String value = cache.get(1, k -> service.fetchById(1));
        logger.info(value);
    }
}
