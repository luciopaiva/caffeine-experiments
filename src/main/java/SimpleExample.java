import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

public class SimpleExample {

    private static final Logger logger = LogManager.getLogger(SimpleExample.class);

    public static void main(String ...args) {
        Cache<Integer, String> cache = Caffeine.newBuilder()
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .maximumSize(10)
                .recordStats()
                .build();

        String value = cache.get(1, k -> "foo");
        logger.info(value);
    }
}
