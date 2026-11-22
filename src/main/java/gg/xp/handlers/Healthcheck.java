package gg.xp.handlers;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import jakarta.inject.Singleton;

@Controller("/healthcheck")
@Singleton
public final class Healthcheck {

	private Healthcheck() {
	}

	@Get(produces = MediaType.TEXT_PLAIN)
	public static String health() {
		return "Health Check OK\n";
	}
}
