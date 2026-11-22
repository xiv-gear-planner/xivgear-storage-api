package gg.xp;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("gear-planner.shortlink")
public record ShortlinkConfig(
		@Bindable(defaultValue = "50000") int maxUploadBytes) {
}
