package gg.xp;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.oraclecloud.core.TenancyIdProvider;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Factory
final class TestDatabaseFactory {
	private final Map<UUID, String> backing = new ConcurrentHashMap<>();

	@Singleton
	@Replaces(Database.class)
	Database database() {
		return new Database() {
			@Override
			public @Nullable String getShortlink(UUID uuid) {
				return backing.get(uuid);
			}

			@Override
			public void putShortLink(UUID uuid, String payload) {
				backing.put(uuid, payload);
			}
		};
	}

	// For some reason, pipelines are failing because it tries to load the tenancy ID from application.yaml, which
	// doesn't work because the unit tests aren't supposed to actually be cconneccting to OCI.
	@Singleton
	@Replaces(TenancyIdProvider.class)
	TenancyIdProvider tenancyIdProvider() {
		return () -> "test-tenant";
	}
}
