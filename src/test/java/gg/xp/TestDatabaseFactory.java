package gg.xp;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
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
}
