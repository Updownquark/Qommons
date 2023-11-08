package org.qommons.condition;

public interface ConditionalFieldAccess<E, F> extends ConditionalValueAccess<E, F> {
	@Override
	default <T> ConditionalChainAccess<E, T> dot(ConditionalFieldAccess<? super F, T> field) {
		return new ConditionalChainAccess<>(this, field);
	}
}
