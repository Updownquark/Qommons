package org.qommons.condition;

import java.util.Comparator;
import java.util.function.Function;

/**
 * This represents a {@link ConditionalFieldAccess field} or a {@link ConditionalChainAccess chain} of fields in a {@link ConditionalEntity
 * conditional entity} on which a {@link Condition} can be created.
 * 
 * @param <E> The type of the entity
 * @param <F> The type of the field value in the entity
 */
public interface ConditionalValueAccess<E, F> extends Comparable<ConditionalValueAccess<?, ?>>, Comparator<F> {
	/** @return The type of entity that this object can access information for */
	ConditionalEntity<E> getSourceEntity();

	/**
	 * @return The value type of this information, as an entity type, or null if this information does not describe an entity-mapped field
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
	default <T> ConditionalChainAccess<E, T> dot(Function<? super F, T> attr) {
		ConditionalEntity<F> target = getTargetEntity();
		if (target == null)
			throw new UnsupportedOperationException("The target type of this value access is not an entity");
		return dot(target.getField(attr));
	}

	/**
	 * @param field The name of a field of this value access's {@link #getTargetEntity() target entity}
	 * @return A new value access object to get the given information from this object's access on an entity
	 */
	default ConditionalChainAccess<E, ?> dot(String field) {
		ConditionalEntity<F> target = getTargetEntity();
		if (target == null)
			throw new UnsupportedOperationException("The target type of this value access is not an entity");
		return dot(target.getField(field));
	}

	/**
	 * @param <T> The type of the target field
	 * @param field A field of this value access's {@link #getTargetEntity() target entity}
	 * @return A new value access object to get the given information from this object's access on an entity
	 */
	<T> ConditionalChainAccess<E, T> dot(ConditionalFieldAccess<? super F, T> field);

	/**
	 * For entities that support inheritance and overriding fields, this method tests whether this field is the same as or overrides
	 * another. For entities without inheritance or without field overriding, this is the same as {@link #equals(Object)}.
	 * 
	 * @param field The other field to test
	 * @return Whether this field is the same as or an override of the given field in a parent entity type
	 */
	boolean isOverride(ConditionalValueAccess<? extends E, ?> field);
}
