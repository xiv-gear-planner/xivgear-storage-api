package gg.xp;

import io.micronaut.context.annotation.Context;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.IntSummaryStatistics;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Context
public class GzipCacheImpl implements Cache {

	private static final Logger log = LoggerFactory.getLogger(GzipCacheImpl.class);

	private final Map<UUID, CacheEntry> map = new ConcurrentHashMap<>();
	private final float worstCompressRatio;
	private final AtomicLong hits = new AtomicLong();
	private final AtomicLong misses = new AtomicLong();
	private final AtomicLong prunedEntries = new AtomicLong();
	private final Thread pruner;
	private volatile boolean stop;

	@Inject
	public GzipCacheImpl(GzipCacheConfig config) {
		this(config.maxCacheSize(), config.wakeupInterval(), config.worstCompressRatio());
	}

	private GzipCacheImpl(int maxCacheSize, int wakeupInterval, float worstCompressRatio) {
		this.worstCompressRatio = worstCompressRatio;
		pruner = Thread.startVirtualThread(() -> {
			while (!stop) {
				try {
					int sizeBefore = map.size();
					if (sizeBefore > maxCacheSize) {
						log.info("Pruning cache");
						IntSummaryStatistics lfuStats = map.values().stream().mapToInt(e -> e.usedCount).summaryStatistics();
						LongSummaryStatistics lruStats = map.values().stream().mapToLong(CacheEntry::lastUsed).filter(l -> l > 0).summaryStatistics();
						long lruCutoff = (long) lruStats.getAverage();
						int lfuCutoff = (int) lfuStats.getAverage();
						long removedCount = 0;
						for (var entry : map.entrySet()) {
							CacheEntry val = entry.getValue();
							long lastUsed = val.lastUsed();
							if (((lastUsed > 0 && lastUsed < lruCutoff) || val.usedCount < lfuCutoff)
							    && map.remove(entry.getKey(), val)) {
								removedCount++;
							}
						}
						prunedEntries.addAndGet(removedCount);
						log.info("Pruning cache pruned ({} => {})", sizeBefore, map.size());
					}
				}
				catch (Throwable t) {
					log.error("Error pruning cache", t);
				}
				finally {
					try {
						Thread.sleep(wakeupInterval);
					}
					catch (InterruptedException ignored) {
						// Shutdown interrupts the virtual pruner thread.
					}
				}
			}
		});
	}

	@PreDestroy
	void stop() {
		stop = true;
		pruner.interrupt();
	}

	private static final class CacheEntry {
		// Approximate counters are sufficient for pruning and avoid per-request locks.
		int usedCount;
		long lastUsed;
		final byte[] compressed;
		final String uncompressed;

		private CacheEntry(String value, float worstCompressRatio) {
			byte[] compressed = compressStringToBytes(value);
			if (compressed.length < (value.length() * worstCompressRatio)) {
				this.compressed = compressed;
				this.uncompressed = null;
			}
			else {
				this.compressed = null;
				this.uncompressed = value;
			}
			update();
		}

		private void update() {
			usedCount++;
			lastUsed = System.currentTimeMillis();
		}

		private String getValue() {
			return uncompressed != null ? uncompressed : uncompressBytesToString(compressed);
		}

		private long lastUsed() {
			return lastUsed;
		}
	}

	private static byte[] compressStringToBytes(String value) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (OutputStream gzip = new GZIPOutputStream(output)) {
			gzip.write(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not compress cache entry", e);
		}
		return output.toByteArray();
	}

	private static String uncompressBytesToString(byte[] compressed) {
		try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
			return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not decompress cache entry", e);
		}
	}

	@Override
	public @Nullable String computeIfAbsent(UUID key, Function<UUID, @Nullable String> getter) {
		String[] result = new String[1];
		map.compute(key, (uuid, current) -> {
			// No current cache entry
			if (current == null) {
				misses.incrementAndGet();
				String newValue = getter.apply(uuid);
				// Not found in live lookup
				if (newValue == null) {
					return null;
				}
				// Found in live lookup
				result[0] = newValue;
				return new CacheEntry(newValue, worstCompressRatio);
			}
			// Already cached - bump timestamp and return it
			hits.incrementAndGet();
			current.update();
			result[0] = current.getValue();
			return current;
		});
		return result[0];
	}

	@Override
	public void set(UUID key, @NotNull String value) {
		map.put(key, new CacheEntry(value, worstCompressRatio));
	}

	@Override
	public int cacheSize() {
		return map.size();
	}

	@Override
	public CacheStats getStats() {
		return new CacheStats(cacheSize(), hits.get(), misses.get(), prunedEntries.get());
	}

	void clear() {
		map.clear();
	}
}
