package org.qommons.collect;

import java.util.function.BooleanSupplier;

public interface OptimisticContext extends BooleanSupplier {
	boolean check();

	@Override
	default boolean getAsBoolean() {
		return check();
	}

	OptimisticContext TRUE = () -> true;

	default OptimisticContext and(OptimisticContext other) {
		if (other == null)
			return this;
		return () -> this.check() && other.check();
	}

	static OptimisticContext and(OptimisticContext one, OptimisticContext two) {
		if (one == null)
			return two;
		else
			return one.and(two);
	}
}