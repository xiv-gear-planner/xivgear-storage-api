package gg.xp.handlers;

import io.micronaut.context.annotation.Context;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/healthcheck")
@Context
public final class Healthcheck {

	@SuppressWarnings("MethodMayBeStatic")
	@Get(produces = MediaType.TEXT_PLAIN)
	public String health() {
		return "Health Check OK\n";
	}
}
