package gg.xp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;

@Factory
public final class JsonConfiguration {
	@Context
	ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}
