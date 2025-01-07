package org.qommons.condition;

/**
 * Represents the ability to get the value of a field from an entity of a given type
 * 
 * @param <E> The type of the entity this field access is for
 * @param <F> The type of the field
 */
public interface ConditionalFieldAccess<E, F> extends ConditionalValueAccess<E, F> {
	@Override
	default <T> ConditionalChainAccess<E, T> dot(ConditionalFieldAccess<? super F, T> field) {
		return new ConditionalChainAccess<>(this, field);
	}
}
