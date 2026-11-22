package gg.xp;

import io.micronaut.oraclecloud.core.OracleCloudAuthConfigurationProperties;
import jakarta.annotation.PreDestroy;
import oracle.nosql.driver.NoSQLHandle;
import oracle.nosql.driver.NoSQLHandleConfig;
import oracle.nosql.driver.NoSQLHandleFactory;
import oracle.nosql.driver.iam.SignatureProvider;
import oracle.nosql.driver.ops.GetRequest;
import oracle.nosql.driver.ops.GetResult;
import oracle.nosql.driver.ops.PutRequest;
import oracle.nosql.driver.ops.QueryIterableResult;
import oracle.nosql.driver.ops.QueryRequest;
import oracle.nosql.driver.ops.QueryResult;
import oracle.nosql.driver.ops.GetTableRequest;
import oracle.nosql.driver.ops.TableLimits;
import oracle.nosql.driver.ops.TableResult;
import oracle.nosql.driver.util.SimpleRateLimiter;
import oracle.nosql.driver.values.JsonOptions;
import oracle.nosql.driver.values.MapValue;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

public class OracleNoSqlDb implements Database {

	private static final Logger log = LoggerFactory.getLogger(OracleNoSqlDb.class);

	private final NoSQLHandle handle;
	private final String tableName;
	private final BackupConfig backupConfig;

	public OracleNoSqlDb(
			DbConfig config,
			BackupConfig backupConfig,
			OracleCloudAuthConfigurationProperties authConfig) {
		handle = createHandle(config, authConfig);
		tableName = validateTableName(config.tableName());
		this.backupConfig = backupConfig;
	}

	private static NoSQLHandle createHandle(
			DbConfig config, OracleCloudAuthConfigurationProperties authConfig) {
		SignatureProvider authProvider = createSignatureProvider(authConfig);
		NoSQLHandleConfig handleConfig = new NoSQLHandleConfig(driverEndpoint(config.endpoint()))
				.setRequestTimeout(5_000)
				.setAuthorizationProvider(authProvider)
				.configureDefaultRetryHandler(5, 1_000);
		if (config.compartmentId() != null) {
			handleConfig.setDefaultCompartment(config.compartmentId());
		}
		return NoSQLHandleFactory.createNoSQLHandle(handleConfig);
	}

	private static String driverEndpoint(URL endpoint) {
		return endpoint.getProtocol() + "://" + endpoint.getAuthority();
	}

	private static SignatureProvider createSignatureProvider(
			OracleCloudAuthConfigurationProperties authConfig) {
		var auth = authConfig.getBuilder().build();
		try (InputStream privateKey = auth.getPrivateKey()) {
			if (privateKey == null) {
				throw new IllegalStateException("OCI private key must be configured");
			}
			String privateKeyPem = new String(privateKey.readAllBytes(), StandardCharsets.UTF_8);
			char[] passphrase = auth.getPassphraseCharacters();
			return new SignatureProvider(
					auth.getTenantId(),
					auth.getUserId(),
					auth.getFingerprint(),
					privateKeyPem,
					passphrase == null ? null : passphrase.clone());
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not read OCI private key", e);
		}
	}

	@PreDestroy
	void close() {
		handle.close();
	}

	public long countRows() {
		long total = 0;
		try (QueryRequest queryRequest = new QueryRequest()) {
			queryRequest.setStatement("SELECT count(*) AS ct FROM " + tableName);
			do {
				QueryResult res = handle.query(queryRequest);
				for (MapValue row : res.getResults()) {
					total = row.get("ct").getLong();
				}
			} while (!queryRequest.isDone());
		}
		log.info("Results: {}", total);
		return total;
	}

	@Override
	public @Nullable String getShortlink(UUID uuid) {
		GetResult linkuuid = handle.get(new GetRequest()
				.setTableName(tableName)
				.setKey(new MapValue().put("linkuuid", uuid.toString())));
		MapValue value = linkuuid.getValue();
		if (value == null) {
			return null;
		}
		MapValue dataMap = value.get("datavalue").asMap();
		dataMap.put("timestamp", linkuuid.getModificationTime());
		return dataMap.toString();
	}

	@Override
	public void putShortLink(UUID uuid, String payload) {
		handle.put(new PutRequest().setTableName(tableName).setValue(
				new MapValue()
						.put("linkuuid", uuid.toString())
						.put("hash", (long) payload.hashCode())
						.putFromJson("datavalue", payload, new JsonOptions())));
	}

	void iterateAll(Consumer<MapValue> consumer) {
		TableResult table = handle.getTable(new GetTableRequest().setTableName(tableName));
		TableLimits limits = table.getTableLimits();
		if (limits == null || limits.getMode() != TableLimits.CapacityMode.PROVISIONED) {
			throw new IllegalStateException("iterateAll requires a provisioned table with readable capacity");
		}

		double readRateLimit = limits.getReadUnits() * backupConfig.iterateAllReadRatePercent() / 100.0;
		log.info("iterateAll read rate limit: {} read units/sec ({}% of {} provisioned read units)",
				readRateLimit, backupConfig.iterateAllReadRatePercent(), limits.getReadUnits());

		QueryRequest query = new QueryRequest()
				.setStatement("select * from " + tableName)
				.setTimeout(backupConfig.iterateAllRequestTimeoutMs());
		query.setReadRateLimiter(new SimpleRateLimiter(readRateLimit));
		try (QueryIterableResult iter = handle.queryIterable(query)) {
			for (MapValue entry : iter) {
				consumer.accept(entry);
			}
		}
	}

	private static String validateTableName(String tableName) {
		if (tableName == null || !tableName.matches("[A-Za-z][A-Za-z0-9_]*")) {
			throw new IllegalArgumentException("Invalid Oracle NoSQL table name: " + tableName);
		}
		return tableName;
	}
}
