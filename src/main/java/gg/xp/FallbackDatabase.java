package gg.xp;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Named;

import java.util.UUID;

/**
 * Presents the primary and optional secondary stores as one application database.
 *
 * <p>Reads use the secondary store only when the primary store has no matching
 * shortlink. Writes always go to the primary store, so the secondary database
 * remains a read-only fallback.</p>
 */
@Context
@Primary
@Requires(notEnv = "test")
public class FallbackDatabase implements Database {

	private final OracleNoSqlDb primary;
	private final OracleNoSqlDb secondary;

	/**
	 * Creates a database wrapper around the qualified primary and optional
	 * secondary Oracle NoSQL stores.
	 *
	 * @param primary   the store used for all writes and attempted first for reads
	 * @param secondary the optional store used when the primary has no shortlink
	 */
	public FallbackDatabase(
			@Named("primary") OracleNoSqlDb primary,
			@Nullable @Named("secondary") OracleNoSqlDb secondary) {
		this.primary = primary;
		this.secondary = secondary;
	}

	/**
	 * Looks up a shortlink in the primary store and falls back to the secondary
	 * store when the primary lookup returns no result.
	 *
	 * @param uuid the shortlink identifier to look up
	 * @return the stored shortlink payload, or {@code null} when neither store
	 * contains the identifier
	 */
	@Override
	public @Nullable String getShortlink(UUID uuid) {
		String result = primary.getShortlink(uuid);
		return result == null && secondary != null ? secondary.getShortlink(uuid) : result;
	}

	/**
	 * Stores a shortlink in the primary database only.
	 *
	 * @param uuid    the identifier to assign to the shortlink
	 * @param payload the shortlink payload
	 */
	@Override
	public void putShortLink(UUID uuid, String payload) {
		primary.putShortLink(uuid, payload);
	}
}
