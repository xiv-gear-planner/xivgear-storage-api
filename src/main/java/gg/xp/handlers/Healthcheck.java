package gg.xp.handlers;

import gg.xp.Cache;
import gg.xp.Stats;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import jakarta.inject.Singleton;

import java.time.temporal.ChronoUnit;

@Controller("/healthcheck")
@Singleton
public class Healthcheck {
	private final Stats stats;
	private final Cache cache;

	public Healthcheck(Stats stats, Cache cache) {
		this.stats = stats;
		this.cache = cache;
	}

	@Get(produces = MediaType.TEXT_PLAIN)
	public String health() {
		return "Health Check OK, uptime: %s, GETs: %s, POSTs: %s, errors: %s, cache entries: %s\n".formatted(
				stats.getUptime().truncatedTo(ChronoUnit.SECONDS),
				stats.getCount.get(),
				stats.postCount.get(),
				stats.errCount.get(),
				cache.cacheSize());
	}
}
