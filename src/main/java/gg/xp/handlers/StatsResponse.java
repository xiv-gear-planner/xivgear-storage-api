package gg.xp.handlers;

import gg.xp.CacheStats;
import gg.xp.DatabaseStats;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public record StatsResponse(
		String uptime,
		Traffic traffic,
		CacheStats cache,
		Map<String, DatabaseStats> database) {

	@Serdeable
	public record Traffic(int gets, int posts, int errors) {
	}
}
