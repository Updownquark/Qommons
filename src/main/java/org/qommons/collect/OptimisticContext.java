package org.qommons.collect;

import java.util.function.BooleanSupplier;

/** Provides feedback to an {@link CollectionLockingStrategy.OptimisticIntOperation} about whether the operation is still valid */
public interface OptimisticContext extends BooleanSupplier {
	/** @return Whether the current operation is still valid */
	boolean check();

	@Override
	default boolean getAsBoolean() {
		return check();
	}

	/** A context that is always valid */
	OptimisticContext TRUE = () -> true;

	/**
	 * @param other The other context
	 * @return A context that is valid only when this and the other contexts are valid
	 */
	default OptimisticContext and(OptimisticContext other) {
		if (other == null)
			return this;
		return () -> this.check() && other.check();
	}

	/**
	 * @param one The first context
	 * @param two The second context
	 * @return A context that is valid only when both given contexts are valid. If one or both contexts are null, they are assumed to be
	 *         always valid.
	 */
	static OptimisticContext and(OptimisticContext one, OptimisticContext two) {
		if (one == null)
			return two;
		else
			return one.and(two);
	}
}