package org.qommons.collect;

/** Provides feedback to an {@link org.qommons.Transactable.OptimisticIntOperation} about whether the operation is still valid */
public interface OptimisticContext {
	/** @return Whether the current operation is still valid */
	boolean isOperationValid();

	/** A context that is always valid */
	OptimisticContext TRUE = () -> true;

	/**
	 * @param other The other context
	 * @return A context that is valid only when this and the other contexts are valid
	 */
	default OptimisticContext and(OptimisticContext other) {
		if (other == null)
			return this;
		return () -> this.isOperationValid() && other.isOperationValid();
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