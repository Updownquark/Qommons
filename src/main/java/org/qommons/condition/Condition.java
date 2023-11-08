package org.qommons.condition;

import java.util.function.Function;

/**
 * <p>
 * Represents a boolean condition against an entity of some kind.
 * </p>
 * <p>
 * This API makes it very easy to create conditions
 * 
 * @param <E> The type of the entity against which this condition can be evaluated
 * @param <C> The sub-type of this condition
 * @param <A> The sub-type of the {@link All} condition supplied by this condition's {@link #all()} method
 */
public interface Condition<E, C extends Condition<E, C, A>, A extends All<E, C, A>> extends Comparable<Condition<E, ?, ?>> {
	/** @return A descriptor for the entity type that this condition can be evaluated against */
	ConditionalEntity<E> getEntityType();

	/** @return A condition that is true for all values */
	A all();

	/**
	 * @param condition Produces a condition to OR with this condition
	 * @return A condition that is true when either this condition or the new condition matches an entity
	 */
	C or(Function<? super A, ? extends C> condition);

	/**
	 * @param condition Produces a condition to AND with this condition
	 * @return A condition that is true when both this condition and the new condition matches an entity
	 */
	C and(Function<? super A, ? extends C> condition);

	/**
	 * @param other The other condition to test
	 * @return Whether this condition is always true when the other condition is true
	 */
	boolean contains(Condition<?, ?, ?> other);

	/**
	 * @param transform A transform function to apply to this condition
	 * @return The transformed condition
	 */
	default C transform(Function<? super C, ? extends C> transform) {
		return transform.apply((C) this);
	}
}
