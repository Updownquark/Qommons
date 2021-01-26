package org.qommons.condition;

import java.util.Comparator;
import java.util.function.Function;

public interface ConditionalValueAccess<E, F> extends Comparable<ConditionalValueAccess<?, ?>>, Comparator<F> {
	/** @return The type of entity that this object can access information for */
	ConditionalEntity<E> getSourceEntity();

	/**
	 * @return The value type of this information, as an entity type. May be null if this information does not describe an entity-mapped
	 *         field.
	 */
	ConditionalEntity<F> getTargetEntity();

	/**
	 * @param entity The entity to get the information from
	 * @return The information specified by this access
	 */
	F get(E entity);

	/**
	 * @param <T> The type of the target field or attribute
	 * @param attr A recognized attribute of this object's type to get
	 * @return A new value access object to get the given information from this object's access on an entity
	 */
	<T> ConditionalValueAccess<E, T> dot(Function<? super F, T> attr);

	/**
	 * @param <T> The type of the target field
	 * @param field A field of this value access's {@link #getTargetEntity() target entity}
	 * @return A new value access object to get the given information from this object's access on an entity
	 */
	<T> ConditionalValueAccess<E, T> dot(ConditionalValueAccess<? super F, T> field);

	/**
	 * @param field The name of a field of this value access's {@link #getTargetEntity() target entity}
	 * @return A new value access object to get the given information from this object's access on an entity
	 */
	ConditionalValueAccess<E, ?> dot(String field);

	boolean isOverride(ConditionalValueAccess<? extends E, ?> field);
}
