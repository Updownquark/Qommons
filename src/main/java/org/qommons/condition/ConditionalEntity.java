package org.qommons.condition;

import java.util.function.Function;

public interface ConditionalEntity<E> {
	/**
	 * @param <F> The type of the field
	 * @param fieldGetter The getter for the field in the java type
	 * @return The field type in this value type represented by the given java field
	 * @throws IllegalArgumentException If the given field does not represent a field getter in this value type
	 */
	<F> ConditionalValueAccess<E, F> getField(Function<? super E, F> fieldGetter) throws IllegalArgumentException;
}
