package gg.xp;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.oraclecloud.core.OracleCloudAuthConfigurationProperties;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
@Requires(notEnv = "test")
final class OracleNoSqlDbFactory {

	@Singleton
	@Named("primary")
	OracleNoSqlDb primary(
			DbConfig config,
			BackupConfig backupConfig,
			OracleCloudAuthConfigurationProperties authConfig) {
		return new OracleNoSqlDb(config, backupConfig, authConfig);
	}

	@Singleton
	@Named("secondary")
	@Requires(property = "oracle-nosql-secondary.endpoint")
	OracleNoSqlDb secondary(
			SecondaryDbConfig config,
			BackupConfig backupConfig,
			OracleCloudAuthConfigurationProperties authConfig) {
		return new OracleNoSqlDb(configToDbConfig(config), backupConfig, authConfig);
	}

	private static DbConfig configToDbConfig(SecondaryDbConfig config) {
		return new DbConfig(
				config.endpoint(),
				config.tableName(),
				config.compartmentId());
	}
}
