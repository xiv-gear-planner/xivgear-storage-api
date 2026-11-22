package gg.xp;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;

import java.util.Arrays;
import java.util.List;

/**
 * Micronaut's built-in CORS support echoes the request origin and adds Origin to Vary. This API uses a fixed
 * wildcard response, so neither header should make responses vary by the request origin.
 * The filter removes Origin from Vary while preserving unrelated Vary values.
 * <p>
 * This filter replaces the generated Access-Control-Allow-Origin with a literal '*' and removes the
 * Access-Control-Allow-Credentials header, which is incompatible with a wildcard origin.
 */
@ServerFilter(Filter.MATCH_ALL_PATTERN)
final class CorsResponseFilter implements Ordered {

	@ResponseFilter
	void setCorsHeaders(MutableHttpResponse<?> response) {

		var headers = response.getHeaders();
		headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
		headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);

		// Account for a 'Vary' header being either a single comma-delimited header, or multiple distinct headers.
		List<String> originalVary = headers.getAll(HttpHeaders.VARY);
		headers.remove(HttpHeaders.VARY);
		originalVary.stream()
				.flatMap(value -> Arrays.stream(value.split(",")))
				.map(String::trim)
				.filter(value -> !value.isEmpty() && !value.equalsIgnoreCase(HttpHeaders.ORIGIN))
				.forEach(value -> headers.add(HttpHeaders.VARY, value));
	}

	@Override
	public int getOrder() {
		// Response filters run in reverse order, so this runs after Micronaut's CORS filter.
		return ServerFilterPhase.FIRST.order();
	}
}
