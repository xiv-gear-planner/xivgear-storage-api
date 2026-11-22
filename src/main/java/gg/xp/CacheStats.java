package gg.xp;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CacheStats(int size, long hits, long misses, long pruned) {
}
