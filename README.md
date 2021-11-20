
# Caffeine experiments

These are some experiments with the [Caffeine](https://github.com/ben-manes/caffeine) cache Java library. See [unit tests](src/test/java/CaffeineTest.java) for the experiments themselves.

## Do cache accesses block one another?

The [docs](https://www.javadoc.io/doc/com.github.ben-manes.caffeine/caffeine/2.9.2/com/github/benmanes/caffeine/cache/Cache.html) are a bit confusing with respect to concurrent access to the cache, specifically on this part:

> Some attempted update operations on this cache by other threads may be blocked while the computation is in progress, so the computation should be short and simple, and must not attempt to update any other mappings of this cache.

I wanted to understand how does this actually work, so I wrote `testMultiConsumerSameKey()` and `testMultiConsumerDifferentKeys()`.

### When blocking is desired

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

### When blocking is not desired

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

Notice that both sense the miss at the same time, meaning the second one was not blocked by the first one. That doesn't prove it won't always block, however. Actually, the author mentions [here](https://github.com/ben-manes/caffeine/issues/192#issuecomment-337365618) how `ConcurrentHashMap` locks on the hash bin, and that lead me to believe Caffeine was being backed by such map. It becomes even more obvious when we read `ConcurrentHashMap`s `computeIfAbsent()` [documentation](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentHashMap.html#computeIfAbsent-K-java.util.function.Function-) and it says exactly the same as Caffeine's `get()` method. Putting a breakpoint on `get()` and navigating its guts proves this is true, as we eventually arrive at `computeIfAbsent()` as we go down the stack. 

### Concurrency eventually leads to contention

And the proof that one key will eventually block another comes with the test `testMultiConsumerManyDifferentKeys()`, which fetches 8 different keys at the same time, while the cache's initial capacity is set to 2:

```
01:29:43.785 [INFO] (Test worker) CaffeineTest: Starting...
01:29:43.785 [INFO] (pool-4-thread-1) CaffeineTest: Task is fetching id 1
01:29:43.786 [INFO] (pool-4-thread-3) CaffeineTest: Task is fetching id 3
01:29:43.785 [INFO] (pool-4-thread-2) CaffeineTest: Task is fetching id 2
01:29:43.786 [INFO] (pool-4-thread-1) CaffeineTest: Cache miss! Will fetch id 1 from remote server.
01:29:43.786 [INFO] (pool-4-thread-5) CaffeineTest: Task is fetching id 5
01:29:43.786 [INFO] (pool-4-thread-4) CaffeineTest: Task is fetching id 4
01:29:43.786 [INFO] (pool-4-thread-8) CaffeineTest: Task is fetching id 8
01:29:43.786 [INFO] (pool-4-thread-7) CaffeineTest: Task is fetching id 7
01:29:43.786 [INFO] (pool-4-thread-3) CaffeineTest: Cache miss! Will fetch id 3 from remote server.
01:29:43.786 [INFO] (pool-4-thread-6) CaffeineTest: Task is fetching id 6
01:29:43.787 [INFO] (pool-4-thread-2) CaffeineTest: Cache miss! Will fetch id 2 from remote server.
01:29:43.786 [INFO] (pool-4-thread-4) CaffeineTest: Cache miss! Will fetch id 4 from remote server.
01:29:44.789 [INFO] (pool-4-thread-2) CaffeineTest: Task completed with value: 2
01:29:44.790 [INFO] (pool-4-thread-5) CaffeineTest: Cache miss! Will fetch id 5 from remote server.
01:29:44.790 [INFO] (pool-4-thread-7) CaffeineTest: Cache miss! Will fetch id 7 from remote server.
01:29:44.789 [INFO] (pool-4-thread-6) CaffeineTest: Cache miss! Will fetch id 6 from remote server.
01:29:44.789 [INFO] (pool-4-thread-3) CaffeineTest: Task completed with value: 3
01:29:44.791 [INFO] (pool-4-thread-4) CaffeineTest: Task completed with value: 4
01:29:44.791 [INFO] (pool-4-thread-8) CaffeineTest: Cache miss! Will fetch id 8 from remote server.
01:29:45.794 [INFO] (pool-4-thread-5) CaffeineTest: Task completed with value: 5
01:29:45.794 [INFO] (pool-4-thread-6) CaffeineTest: Task completed with value: 6
01:29:45.795 [INFO] (pool-4-thread-1) CaffeineTest: Task completed with value: 1
01:29:45.795 [INFO] (pool-4-thread-7) CaffeineTest: Task completed with value: 7
01:29:45.794 [INFO] (pool-4-thread-8) CaffeineTest: Task completed with value: 8
01:29:45.796 [INFO] (Test worker) CaffeineTest: Done.
```

Notice how the 8 threads access the cache at the same time, but 4 of them only print the "cache miss" line one second after; although they were fetching different keys, they were blocked by the first 4.

### Avoiding contention with `AsynCache`

As suggested by Ben himself, one way to avoid the contention is to use `AsyncCache` instead. It is as close it as can get to a drop-in replacement for `Cache`. `get()` doesn't need to be changed with respect to the parameters it receives, but we now must handle the future it returns, of course.

Internally, `get()` is delegating the task to an executor, so that's why it can immediately return the execution to the caller, thus reducing the chance of contention. Here's the output of `testMultiConsumerManyDifferentKeysAsync()`, which runs the same logic as the previous test, but replaces the cache with its async version:

```
11:53:36.638 [INFO] (Test worker) CaffeineTest: Starting...
11:53:36.642 [INFO] (Test worker) CaffeineTest: Task is fetching id 1
11:53:36.646 [INFO] (ForkJoinPool.commonPool-worker-9) CaffeineTest: Cache miss! Will fetch id 1 from remote server.
11:53:36.649 [INFO] (Test worker) CaffeineTest: Task is fetching id 2
11:53:36.649 [INFO] (Test worker) CaffeineTest: Task is fetching id 3
11:53:36.649 [INFO] (Test worker) CaffeineTest: Task is fetching id 4
11:53:36.649 [INFO] (ForkJoinPool.commonPool-worker-2) CaffeineTest: Cache miss! Will fetch id 2 from remote server.
11:53:36.649 [INFO] (Test worker) CaffeineTest: Task is fetching id 5
11:53:36.649 [INFO] (ForkJoinPool.commonPool-worker-11) CaffeineTest: Cache miss! Will fetch id 3 from remote server.
11:53:36.649 [INFO] (ForkJoinPool.commonPool-worker-4) CaffeineTest: Cache miss! Will fetch id 4 from remote server.
11:53:36.649 [INFO] (ForkJoinPool.commonPool-worker-13) CaffeineTest: Cache miss! Will fetch id 5 from remote server.
11:53:36.650 [INFO] (Test worker) CaffeineTest: Task is fetching id 6
11:53:36.650 [INFO] (Test worker) CaffeineTest: Task is fetching id 7
11:53:36.650 [INFO] (ForkJoinPool.commonPool-worker-6) CaffeineTest: Cache miss! Will fetch id 6 from remote server.
11:53:36.650 [INFO] (Test worker) CaffeineTest: Task is fetching id 8
11:53:36.650 [INFO] (ForkJoinPool.commonPool-worker-15) CaffeineTest: Cache miss! Will fetch id 7 from remote server.
11:53:36.651 [INFO] (ForkJoinPool.commonPool-worker-8) CaffeineTest: Cache miss! Will fetch id 8 from remote server.
11:53:37.650 [INFO] (ForkJoinPool.commonPool-worker-2) CaffeineTest: Task completed with value: 2
11:53:37.650 [INFO] (ForkJoinPool.commonPool-worker-9) CaffeineTest: Task completed with value: 1
11:53:37.650 [INFO] (ForkJoinPool.commonPool-worker-11) CaffeineTest: Task completed with value: 3
11:53:37.650 [INFO] (ForkJoinPool.commonPool-worker-13) CaffeineTest: Task completed with value: 5
11:53:37.650 [INFO] (ForkJoinPool.commonPool-worker-4) CaffeineTest: Task completed with value: 4
11:53:37.651 [INFO] (ForkJoinPool.commonPool-worker-6) CaffeineTest: Task completed with value: 6
11:53:37.652 [INFO] (ForkJoinPool.commonPool-worker-15) CaffeineTest: Task completed with value: 7
11:53:37.652 [INFO] (ForkJoinPool.commonPool-worker-8) CaffeineTest: Task completed with value: 8
11:53:37.652 [INFO] (Test worker) CaffeineTest: Done.
```

It now works as one would hope, with no contention whatsoever.
