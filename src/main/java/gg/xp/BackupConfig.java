package gg.xp;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("gear-planner.backup")
public record BackupConfig(
		@Bindable(defaultValue = "10.0") double iterateAllReadRatePercent,
		@Bindable(defaultValue = "600000") int iterateAllRequestTimeoutMs) {
}
