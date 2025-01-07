package org.qommons.condition;

import java.util.function.Function;

/**
 * Represent an entity type on which a {@link Condition} can be created, which evaluates an entity of this type by its field values
 * 
 * @param <E> The java type of the entity
 */
public interface ConditionalEntity<E> {
	/**
	 * @param <F> The type of the field
	 * @param fieldGetter The getter for the field in the java type
	 * @return The field in this value type represented by the given java getter
	 * @throws UnsupportedOperationException If field access by getter is not supported for this conditional entity
	 * @throws IllegalArgumentException If the given field does not represent a field getter in this value type
	 */
	<F> ConditionalFieldAccess<E, F> getField(Function<? super E, F> fieldGetter)
		throws UnsupportedOperationException, IllegalArgumentException;

	/**
	 * @param fieldName The name of the field to get
	 * @return The field in this entity with the given name
	 * @throws UnsupportedOperationException If named fields are not supported by this conditional entity
	 * @throws IllegalArgumentException If no such field exists in this entity
	 */
	ConditionalFieldAccess<E, ?> getField(String fieldName) throws UnsupportedOperationException, IllegalArgumentException;

	/**
	 * @param field The field to test
	 * @return Whether this entity type is the owner of the given field
	 */
	boolean owns(ConditionalValueAccess<?, ?> field);

	/**
	 * @param other The other entity type to test
	 * @return Whether this entity type is the same as, or a super-type of, the given entity
	 */
	boolean isAssignableFrom(ConditionalEntity<?> other);

	/**
	 * @param conditionalFieldAccess The field access to get the override for
	 * @return The override field access in this entity type of the given field access in this entity type or a super-type
	 */
	ConditionalFieldAccess<?, ?> getOverriddenField(ConditionalFieldAccess<?, ?> conditionalFieldAccess);

	/** @return The {@link All} condition, from which other, more interesting conditions may be created */
	All<E, ?, ?> select();
}
