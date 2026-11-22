package gg.xp;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record DatabaseStats(long retrievals, long creations) {
}
