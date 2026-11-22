package gg.xp;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class CacheTest {

	private static GzipCacheImpl createCache() {
		return new GzipCacheImpl(new GzipCacheConfig(100, 1000, 0.80f));
	}

	/**
	 * Test that the cache will be pruned above a certain size after waiting
	 *
	 * @throws InterruptedException unused
	 */
	@Test
	void testCachePruning() throws InterruptedException {
		GzipCacheImpl cache = createCache();
		Thread.sleep(50);
		AtomicInteger counter = new AtomicInteger();
		Function<UUID, String> conv = uuid -> {
			counter.incrementAndGet();
			return uuid.toString();
		};
		// One specific instance we can use to track evictions
		UUID first = new UUID(0, 0);
		String firstResultBefore = cache.computeIfAbsent(first, conv);
		// Put a total of 75 entries into the cache
		for (int i = 1; i < 75; i++) {
			UUID uuid = new UUID(0, i);
			cache.computeIfAbsent(uuid, conv);
		}
		assertEquals(75, cache.cacheSize());
		assertEquals(75, counter.get());
		// Put 75 more into the cache. It is now overfull.
		for (int i = 0; i < 75; i++) {
			UUID uuid = new UUID(1, i);
			cache.computeIfAbsent(uuid, conv);
		}
		String firstResultMid = cache.computeIfAbsent(first, conv);
		// This should be 150, rather than 151, because the above calc is cached.
		assertEquals(150, counter.get());
		// Wait a bit so LRU can do its thing
		Thread.sleep(50);
		for (int i = 0; i < 75; i++) {
			UUID uuid = new UUID(1, i);
			// Also access thrice so that LFU can also do its thing
			cache.computeIfAbsent(uuid, conv);
		}
		// Still 150
		assertEquals(150, counter.get());
		// However, it does not get pruned until the right time.
		assertEquals(150, cache.cacheSize());
		Thread.sleep(1100);
		// Now it should be pruned
		assertEquals(75, cache.cacheSize());

		String firstResultAfter = cache.computeIfAbsent(first, conv);
		// Value should be same
		assertEquals(firstResultBefore, firstResultMid);
		assertEquals(firstResultBefore, firstResultAfter);
		// Instance should be different for all 3. It's different at mid because we're pulling a value from cache.
		// It's different at end because the cache has been evicted at that point.
		assertNotSame(firstResultBefore, firstResultMid);
		assertNotSame(firstResultBefore, firstResultAfter);
		assertNotSame(firstResultMid, firstResultAfter);
		cache.stop();

	}

	/**
	 * Test that the cache will not be pruned if it does not hit the size threshold
	 *
	 * @throws InterruptedException unused
	 */
	@Test
	void testCacheNoPrune() throws InterruptedException {
		GzipCacheImpl cache = createCache();
		Thread.sleep(50);
		AtomicInteger counter = new AtomicInteger();
		Function<UUID, String> conv = uuid -> {
			counter.incrementAndGet();
			return uuid.toString();
		};
		for (int i = 0; i < 75; i++) {
			UUID uuid = new UUID(0, i);
			cache.computeIfAbsent(uuid, conv);
		}
		assertEquals(75, cache.cacheSize());
		assertEquals(75, counter.get());
		Thread.sleep(1100);
		assertEquals(75, cache.cacheSize());
		for (int i = 0; i < 75; i++) {
			UUID uuid = new UUID(0, i);
			cache.computeIfAbsent(uuid, conv);
		}
		assertEquals(75, counter.get());
		cache.stop();
	}


	@Test
	void testCacheAfterPassiveSet() {
		GzipCacheImpl cache = createCache();
		AtomicInteger counter = new AtomicInteger();
		Function<UUID, String> conv = uuid -> {
			counter.incrementAndGet();
			return uuid.toString();
		};
		String s1 = cache.computeIfAbsent(new UUID(1234567890, 1234567890), conv);
		assertEquals(1, counter.get());
		String s2 = cache.computeIfAbsent(new UUID(1234567890, 1234567890), conv);
		assertEquals(1, counter.get());
		assertEquals(s1, s2);
		// Made it non-compressible on purpose so this should work
		assertSame(s2, s1);
	}

	@Test
	void testCacheAfterActiveSet() {
		GzipCacheImpl cache = createCache();
		AtomicInteger counter = new AtomicInteger();
		Function<UUID, String> conv = uuid -> {
			counter.incrementAndGet();
			return uuid.toString();
		};
		String val = "foo";
		cache.set(new UUID(0, 0), val);
		assertEquals(0, counter.get());
		String s2 = cache.computeIfAbsent(new UUID(0, 0), conv);
		assertEquals(0, counter.get());
		assertEquals(val, s2);
		// 'foo' isn't long enough to compress, so don't
		assertSame(val, s2);
	}

	/**
	 * Test that large entries are compressed
	 */
	@Test
	void testCacheCompression() {
		String input = StringUtils.repeat('a', 20_000);
		GzipCacheImpl cache = createCache();
		UUID uuid = new UUID(0, 0);
		AtomicInteger counter = new AtomicInteger();
		Function<UUID, String> func = ignored -> {
			counter.incrementAndGet();
			return input;
		};
		String result1 = cache.computeIfAbsent(uuid, func);
		// The first time, the result should be returned as-is, and the counter should be increased
		assertEquals(1, counter.get());
		assertEquals(result1, input);
		assertSame(result1, input);

		// This should have been compressed and decompressed. Thus, the result should be an identical string, but not
		// the exact same string instance.
		String result2 = cache.computeIfAbsent(uuid, func);
		assertEquals(1, counter.get());
		assertEquals(result2, input);
		assertNotSame(result2, input);
	}

	/**
	 * Test that small entries are not compressed
	 */
	@Test
	void testCacheCompressionBypass() {
		// Short string, not worth compressing
		String input = "foo";
		GzipCacheImpl cache = createCache();
		UUID uuid = new UUID(0, 0);
		String result = cache.computeIfAbsent(uuid, ignored -> input);
		// This should not get compressed. It should be the exact same string instance.
		assertEquals(input, result);
		assertSame(input, result);
	}

	@Test
	void testCacheIgnoresNull() {
		GzipCacheImpl cache = createCache();
		String out = cache.computeIfAbsent(UUID.randomUUID(), ignored -> null);
		assertNull(out);
		assertEquals(0, cache.cacheSize());
	}
}
