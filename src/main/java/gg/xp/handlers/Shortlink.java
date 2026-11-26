package gg.xp.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.xp.Cache;
import gg.xp.Database;
import gg.xp.ShortlinkConfig;
import gg.xp.Stats;
import io.micronaut.context.annotation.Context;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

@Controller("/shortlink")
@Context
@ExecuteOn(TaskExecutors.BLOCKING)
public class Shortlink {

	private static final Logger log = LoggerFactory.getLogger(Shortlink.class);
	private final Database db;
	private final Cache cache;
	private final Stats stats;
	private final ObjectMapper mapper;
	private final ShortlinkConfig config;

	public Shortlink(Database db, Cache cache, Stats stats, ObjectMapper mapper, ShortlinkConfig config) {
		this.db = db;
		this.cache = cache;
		this.stats = stats;
		this.mapper = mapper;
		this.config = config;
	}

	@Post(produces = MediaType.TEXT_PLAIN, consumes = MediaType.ALL)
	public MutableHttpResponse<String> makeShortLink(@Body byte[] body) {
		if (body.length > config.maxUploadBytes()) {
			return HttpResponse.status(HttpStatus.valueOf(413));
		}
		try {
			JsonNode json = mapper.readTree(body);
			if (json == null) {
				return HttpResponse.badRequest();
			}
			if (!json.isObject()) {
				return HttpResponse.badRequest("Not a JSON object");
			}
			stats.postCount.incrementAndGet();
			UUID uuid = UUID.randomUUID();
			String stringed = json.toString();
			log.info("CREATED UUID: {}, data: {}", uuid, StringUtils.truncate(stringed, 100));
			db.putShortLink(uuid, stringed);
			cache.set(uuid, stringed);
			return HttpResponse.created(uuid.toString());
		}
		catch (IOException | RuntimeException e) {
			log.error("Error creating shortlink", e);
			stats.errCount.incrementAndGet();
			return HttpResponse.serverError();
		}
	}

	@Get(value = "/{uuid}", produces = MediaType.APPLICATION_JSON)
	public MutableHttpResponse<String> retrieveShortLink(@PathVariable("uuid") String uuidRaw) {
		return retrieve(uuidRaw);
	}

	private MutableHttpResponse<String> retrieve(String uuidRaw) {
		stats.getCount.incrementAndGet();
		final UUID uuid;
		try {
			uuid = UUID.fromString(uuidRaw);
		}
		catch (IllegalArgumentException ignored) {
			log.error("Bad UUID: {}", uuidRaw);
			return HttpResponse.badRequest();
		}
		String result = getRaw(uuid);
		if (result == null) {
			return HttpResponse.notFound();
		}
		return HttpResponse.ok(result)
				.header("cache-control", "max-age=7200, public, immutable");
	}

	public @Nullable String getRaw(UUID uuid) {
		String result = cache.computeIfAbsent(uuid, db::getShortlink);
		if (result == null) {
			log.info("UUID not found: {}", uuid);
		}
		return result;
	}

	public @Nullable String getRaw(String uuidRaw) {
		return getRaw(UUID.fromString(uuidRaw));
	}

}
