
# Caffeine experiments

These are some experiments with the Caffeine cache Java library. See unit tests for details.

## Do cache accesses block each other?

The [docs](https://www.javadoc.io/doc/com.github.ben-manes.caffeine/caffeine/2.9.2/com/github/benmanes/caffeine/cache/Cache.html) are a bit confusing with respect to concurrent access to the cache, specifically on this part:

> Some attempted update operations on this cache by other threads may be blocked while the computation is in progress, so the computation should be short and simple, and must not attempt to update any other mappings of this cache.

I wanted to understand how does this actually work, so I wrote `testMultiConsumerSameKey()` and `testMultiConsumerDifferentKeys()`.

The first one is the simplest to visualize: if two requests for the same key arrive concurrently, the first one will trigger the mapping function, while the second one will block, waiting for the result of the mapping function call. This is exactly what is seen when the test runs:

```
22:27:52.070 [INFO] (Test worker) CaffeineTest: Starting...
22:27:52.070 [INFO] (pool-3-thread-1) CaffeineTest: Task is fetching id 1
22:27:52.071 [INFO] (pool-3-thread-2) CaffeineTest: Task is fetching id 1
22:27:52.071 [INFO] (pool-3-thread-1) CaffeineTest: Cache miss! Will fetch id 1 from remote server.
22:27:53.076 [INFO] (pool-3-thread-1) CaffeineTest: Task completed with value: foo
22:27:53.076 [INFO] (pool-3-thread-2) CaffeineTest: Task completed with value: foo
22:27:53.076 [INFO] (Test worker) CaffeineTest: Done.
```

Now the second test brings a different scenario where each cache access is looking for a different key. The question here was: will a second thread be blocked by the first one if it's requesting a different key? The docs suggest that it may be possible, but for the scenario designed in the test, both accesses always occur independently of each other:

```
22:27:51.059 [INFO] (Test worker) CaffeineTest: Starting...
22:27:51.061 [INFO] (pool-2-thread-1) CaffeineTest: Task is fetching id 1
22:27:51.061 [INFO] (pool-2-thread-2) CaffeineTest: Task is fetching id 2
22:27:51.062 [INFO] (pool-2-thread-2) CaffeineTest: Cache miss! Will fetch id 2 from remote server.
22:27:51.062 [INFO] (pool-2-thread-1) CaffeineTest: Cache miss! Will fetch id 1 from remote server.
22:27:52.063 [INFO] (pool-2-thread-1) CaffeineTest: Task completed with value: foo
22:27:52.063 [INFO] (pool-2-thread-2) CaffeineTest: Task completed with value: bar
22:27:52.063 [INFO] (Test worker) CaffeineTest: Done.
```

Notice that both sense the miss at the same time, meaning the second one was not blocked by the first one.