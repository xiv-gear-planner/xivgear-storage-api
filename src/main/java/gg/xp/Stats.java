package gg.xp;

import io.micronaut.context.annotation.Context;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Context
public class Stats {

	private final Instant startedAt = Instant.now();

	public Duration getUptime() {
		return Duration.between(startedAt, Instant.now());
	}

	public final AtomicInteger errCount = new AtomicInteger(0);
	public final AtomicInteger getCount = new AtomicInteger(0);
	public final AtomicInteger postCount = new AtomicInteger(0);
}
