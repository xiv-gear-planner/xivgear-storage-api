package gg.xp;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("gear-planner.cache")
public record GzipCacheConfig(
		@Bindable(defaultValue = "5000") int maxCacheSize,
		@Bindable(defaultValue = "10000") int wakeupInterval,
		@Bindable(defaultValue = "0.80") float worstCompressRatio) {
}
