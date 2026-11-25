package gg.xp.handlers;

import gg.xp.Cache;
import gg.xp.DatabaseStats;
import gg.xp.OracleNoSqlDb;
import gg.xp.Stats;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import java.util.Map;
import java.util.TreeMap;

@Controller("/statsz")
@Context
public class StatsController {
	private final Stats stats;
	private final Cache cache;
	private final BeanContext beanContext;

	public StatsController(Stats stats, Cache cache, BeanContext beanContext) {
		this.stats = stats;
		this.cache = cache;
		this.beanContext = beanContext;
	}

	@Get(produces = MediaType.APPLICATION_JSON)
	public StatsResponse stats() {
		Map<String, DatabaseStats> databaseStats = new TreeMap<>();
		for (OracleNoSqlDb database : beanContext.getBeansOfType(OracleNoSqlDb.class)) {
			databaseStats.put(database.databaseName(), database.getStats());
		}
		return new StatsResponse(
				stats.getUptime().toString(),
				new StatsResponse.Traffic(stats.getCount.get(), stats.postCount.get(), stats.errCount.get()),
				cache.getStats(),
				databaseStats);
	}
}
