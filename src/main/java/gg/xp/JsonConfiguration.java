package gg.xp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

@Factory
public final class JsonConfiguration {
	@Singleton
	ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}
