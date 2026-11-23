package gg.xp;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.oraclecloud.core.OracleCloudCoreFactory;
import io.micronaut.oraclecloud.core.TenancyIdProvider;
import jakarta.inject.Singleton;

/**
 * Supplies the tenancy bean without OCI credentials while the test context starts.
 */
@Factory
@BootstrapContextCompatible
final class TestOracleCloudFactory {
	@Singleton
	@BootstrapContextCompatible
	@Replaces(value = TenancyIdProvider.class, factory = OracleCloudCoreFactory.class)
	TenancyIdProvider tenancyIdProvider() {
		return () -> "test-tenant";
	}
}
