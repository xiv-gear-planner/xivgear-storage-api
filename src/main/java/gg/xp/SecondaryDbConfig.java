package gg.xp;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;

import java.net.URL;

@ConfigurationProperties("oracle-nosql-secondary")
@Requires(property = "oracle-nosql-secondary.endpoint")
public record SecondaryDbConfig(
		URL endpoint,
		String tableName,
		String compartmentId) {

	public SecondaryDbConfig {
		if (tableName == null || tableName.isBlank()) {
			throw new IllegalArgumentException("oracle-nosql-secondary.table-name must be configured");
		}
		if (compartmentId != null && compartmentId.isBlank()) {
			compartmentId = null;
		}
	}
}
