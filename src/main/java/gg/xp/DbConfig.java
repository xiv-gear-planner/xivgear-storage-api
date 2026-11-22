package gg.xp;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.net.URL;

@ConfigurationProperties("oracle-nosql")
public record DbConfig(
		URL endpoint,
		String tableName,
		String compartmentId) {

	public DbConfig {
		if (tableName == null || tableName.isBlank()) {
			throw new IllegalArgumentException("oracle-nosql.table-name must be configured");
		}
		if (compartmentId != null && compartmentId.isBlank()) {
			compartmentId = null;
		}
	}
}
