package gg.xp;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_ENTITY_TOO_LARGE;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
public class PersistenceTest {
	private static final String TEST_ORIGIN = "https://example.com";

	@Inject
	@Client("/")
	HttpClient http;

	@Inject
	GzipCacheImpl cache;

	@Inject
	GzipCacheConfig cacheConfig;

	@Inject
	ShortlinkConfig shortlinkConfig;

	@Inject
	DbConfig dbConfig;

	@BeforeEach
	void clearCache() {
		cache.clear();
	}

	@Test
	void gearSheetTest() throws IOException {
		String sheetJson = resource("/test_sheet.json");
		String setUuid = createShortlink(sheetJson);
		assertStored(setUuid, sheetJson);

		assertSharePage(setUuid,
				"<meta property=\"og:title\" content=\"6.5 Sage DSR Sets copy - XivGear - FFXIV Gear Planner\"/>",
				"<meta property=\"og:description\" content=\"Sage BiS sheet for Dragonsong's Reprise, updated for 6.5\n\nXivGear is an advanced and easy-to-use FFXIV gear planner/set builder with built-in simulation support.\"/>");
	}

	@Test
	void gearSetTest() throws IOException {
		String setJson = resource("/test_set.json");
		String setUuid = createShortlink(setJson);
		assertStored(setUuid, setJson);

		assertSharePage(setUuid,
				"<meta property=\"og:title\" content=\"6.55 DSR 611 - Crit/Det Relic - XivGear - FFXIV Gear Planner\"/>",
				"<meta property=\"og:description\" content=\"This set is intended for you to be doing the 611 strat as it saves a lot of mana requiring little to none. If you want to lose extra Piety you can replace your piety ring with a 660 augmented tome ri&hellip;\n\nXivGear is an advanced and easy-to-use FFXIV gear planner/set builder with built-in simulation support.\"/>");
	}

	@Test
	void endpointValidationTest() {
		HttpResponse<String> invalidUuid = exchangeAllowingError(HttpRequest.GET("/shortlink/not-a-uuid"));
		assertEquals(HTTP_BAD_REQUEST, invalidUuid.code());
		assertCorsHeaders(invalidUuid);

		String missingUuid = UUID.randomUUID().toString();
		HttpResponse<String> missing = exchangeAllowingError(HttpRequest.GET("/shortlink/" + missingUuid));
		assertEquals(HTTP_NOT_FOUND, missing.code());
		assertCorsHeaders(missing);

		HttpResponse<String> invalidJson = exchangeAllowingError(HttpRequest.POST("/shortlink/", "not json")
				.contentType(MediaType.APPLICATION_JSON_TYPE));
		assertEquals(HTTP_INTERNAL_ERROR, invalidJson.code());
		assertCorsHeaders(invalidJson);

		String oversizedJson = "{\"payload\":\"" + "x".repeat(shortlinkConfig.maxUploadBytes()) + "\"}";
		HttpResponse<String> oversized = exchangeAllowingError(HttpRequest.POST("/shortlink/", oversizedJson)
				.contentType(MediaType.APPLICATION_JSON_TYPE));
		assertEquals(HTTP_ENTITY_TOO_LARGE, oversized.code());
		assertCorsHeaders(oversized);
	}

	@Test
	void shareAndHealthEndpointsTest() {
		HttpResponse<String> invalidShare = exchangeAllowingError(HttpRequest.GET("/share/not-a-uuid"));
		assertEquals(HTTP_NOT_FOUND, invalidShare.code());

		HttpResponse<String> missingShare = exchangeAllowingError(HttpRequest.GET("/share/" + UUID.randomUUID()));
		assertEquals(HTTP_NOT_FOUND, missingShare.code());

		HttpResponse<String> health = http.toBlocking().exchange(HttpRequest.GET("/healthcheck"), String.class);
		assertEquals(HTTP_OK, health.code());
		assertTrue(health.body().contains("Health Check OK"));
		assertTrue(health.body().contains("GETs:"));
		assertTrue(health.body().contains("POSTs:"));
	}

	@Test
	void configurationIsBound() {
		assertEquals(5_000, cacheConfig.maxCacheSize());
		assertEquals(10_000, cacheConfig.wakeupInterval());
		assertEquals(50_000, shortlinkConfig.maxUploadBytes());
		assertEquals("https://example.invalid", dbConfig.endpoint().toString());
		assertEquals("test-shortlinks", dbConfig.tableName());
		assertEquals("test-compartment", dbConfig.compartmentId());
	}

	private String createShortlink(String json) {
		HttpRequest<String> request = HttpRequest.POST("/shortlink/", json)
				.contentType(MediaType.APPLICATION_JSON_TYPE);
		HttpResponse<String> response = http.toBlocking().exchange(request, String.class);
		assertEquals(HTTP_CREATED, response.code());
		String uuid = response.body();
		assertNotNull(uuid);
		UUID.fromString(uuid);
		return uuid;
	}

	private void assertStored(String uuid, String expected) {
		HttpRequest<?> request = HttpRequest.GET("/shortlink/" + uuid).header("Origin", TEST_ORIGIN);
		HttpResponse<String> response = http.toBlocking().exchange(request, String.class);
		assertEquals(HTTP_OK, response.code());
		assertEquals(expected, response.body());
		assertEquals("max-age=7200, public, immutable", response.header("cache-control"));
		assertCorsHeaders(response);

		cache.clear();
		response = http.toBlocking().exchange(HttpRequest.GET("/shortlink/" + uuid + "/"), String.class);
		assertEquals(HTTP_OK, response.code());
		assertEquals(expected, response.body());
	}

	private void assertSharePage(String uuid, String title, String description) {
		HttpResponse<String> response = http.toBlocking().exchange(HttpRequest.GET("/share/" + uuid), String.class);
		assertEquals(HTTP_OK, response.code());
		assertTrue(response.header("Content-Type").startsWith(MediaType.TEXT_HTML));
		assertContains(response.body(), title, description, uuid);

		cache.clear();
		response = http.toBlocking().exchange(HttpRequest.GET("/share/" + uuid + "/"), String.class);
		assertEquals(HTTP_OK, response.code());
		assertContains(response.body(), title, description, uuid);
	}

	private static void assertContains(String body, String title, String description, String uuid) {
		MatcherAssert.assertThat(body, Matchers.containsString(title));
		MatcherAssert.assertThat(body, Matchers.containsString(description));
		MatcherAssert.assertThat(body, Matchers.containsString("<meta http-equiv=\"Refresh\" content=\"0; url='https://xivgear.app/#/sl/" + uuid + "'\" />"));
	}

	private static void assertCorsHeaders(HttpResponse<?> response) {
		assertEquals("*", response.header("Access-Control-Allow-Origin"));
		assertNull(response.header("Access-Control-Allow-Credentials"));
		assertTrue(response.getHeaders().getAll("Vary").stream()
				.flatMap(value -> Arrays.stream(value.split(",")))
				.map(String::trim)
				.noneMatch(value -> value.equalsIgnoreCase("Origin")));
	}

	@SuppressWarnings("unchecked")
	private HttpResponse<String> exchangeAllowingError(MutableHttpRequest<?> request) {
		request.header("Origin", TEST_ORIGIN);
		try {
			return http.toBlocking().exchange(request, String.class);
		}
		catch (HttpClientResponseException e) {
			return (HttpResponse<String>) (HttpResponse<?>) e.getResponse();
		}
	}

	private static String resource(String name) throws IOException {
		return new String(Objects.requireNonNull(PersistenceTest.class.getResourceAsStream(name)).readAllBytes());
	}
}
