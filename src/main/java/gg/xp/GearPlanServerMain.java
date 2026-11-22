package gg.xp;

import io.micronaut.runtime.Micronaut;

public final class GearPlanServerMain {

	private GearPlanServerMain() {
	}

	static void main(String[] args) {
		Micronaut.run(GearPlanServerMain.class, args);
	}
}
