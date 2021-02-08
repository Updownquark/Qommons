package org.qommons.condition;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.qommons.QommonsUtils;
import org.qommons.condition.All.ConditionIntermediate1;
import org.qommons.condition.All.ConditionIntermediate2;

public abstract class DefaultCondition<E> implements ConditionImpl<E, DefaultCondition<E>, DefaultCondition.DefaultAll<E>> {
	private final ConditionalEntity<E> theEntity;

	public DefaultCondition(ConditionalEntity<E> entity) {
		theEntity = entity;
	}

	@Override
	public ConditionalEntity<E> getEntityType() {
		return theEntity;
	}

	@Override
	public DefaultCondition<E> createOrCondition(Collection<? extends DefaultCondition<E>> conditions) {
		return new DefaultOrCondition<>(conditions);
	}

	@Override
	public DefaultCondition<E> createAndCondition(Collection<? extends DefaultCondition<E>> conditions) {
		return new DefaultAndCondition<>(conditions);
	}

	public static class DefaultAll<E> extends DefaultCondition<E> implements All<E, DefaultCondition<E>, DefaultAll<E>> {
		public DefaultAll(ConditionalEntity<E> entity) {
			super(entity);
		}

		@Override
		public int getConditionType() {
			return 0;
		}

		@Override
		public int compareTo(Condition<E, ?, ?> o) {
			if (o instanceof All)
				return 0;
			return -1;
		}

		@Override
		public <F> ConditionIntermediate1<E, DefaultCondition<E>, DefaultAll<E>, F> where(ConditionalValueAccess<E, F> field) {
			return new DefaultCI1<>(this, field);
		}

		@Override
		public DefaultCondition<E> and(Function<? super DefaultAll<E>, ? extends DefaultCondition<E>> condition) {
			return All.super.and(condition);
		}

		@Override
		public DefaultCondition<E> or(Function<? super DefaultAll<E>, ? extends DefaultCondition<E>> condition) {
			return All.super.or(condition);
		}
	}

	public static class DefaultCI1<E, F> implements ConditionIntermediate1<E, DefaultCondition<E>, DefaultAll<E>, F> {
		private final DefaultAll<E> theSource;
		private final ConditionalValueAccess<E, F> theField;

		public DefaultCI1(DefaultAll<E> source, ConditionalValueAccess<E, F> field) {
			theSource = source;
			theField = field;
		}

		@Override
		public DefaultAll<E> getSource() {
			return theSource;
		}

		@Override
		public ConditionalValueAccess<E, F> getField() {
			return theField;
		}

		@Override
		public ConditionIntermediate2<E, DefaultCondition<E>, DefaultAll<E>, F> compare(int ltEqGt, boolean withEqual) {
			return new DefaultCI2<>(this, ltEqGt, withEqual);
		}
	}

	public static class DefaultCI2<E, F> implements ConditionIntermediate2<E, DefaultCondition<E>, DefaultAll<E>, F> {
		private final DefaultCI1<E, F> thePrecursor;
		private final int theComparison;
		private final boolean isWithEqual;

		public DefaultCI2(DefaultCI1<E, F> precursor, int comparison, boolean isWithEqual) {
			thePrecursor = precursor;
			theComparison = comparison;
			this.isWithEqual = isWithEqual;
		}

		@Override
		public ConditionIntermediate1<E, DefaultCondition<E>, DefaultAll<E>, F> getPrecursor() {
			return thePrecursor;
		}

		@Override
		public int getComparison() {
			return theComparison;
		}

		@Override
		public boolean isWithEqual() {
			return isWithEqual;
		}

		@Override
		public DefaultLiteralCondition<E, F> value(F value) {
			return new DefaultLiteralCondition<>(this, value);
		}
	}

	public static class DefaultLiteralCondition<E, F> extends DefaultCondition<E>
		implements LiteralCondition<E, DefaultCondition<E>, DefaultAll<E>, F> {
		private final DefaultCI2<E, F> theIntermediate;
		private final F theValue;

		public DefaultLiteralCondition(DefaultCI2<E, F> intermediate, F value) {
			super(intermediate.getPrecursor().getSource().getEntityType());
			theIntermediate = intermediate;
			theValue = value;
		}

		@Override
		public ConditionalValueAccess<E, F> getField() {
			return theIntermediate.getPrecursor().getField();
		}

		@Override
		public int getComparison() {
			return theIntermediate.getComparison();
		}

		@Override
		public boolean isWithEqual() {
			return theIntermediate.isWithEqual();
		}

		@Override
		public DefaultAll<E> all() {
			return theIntermediate.getPrecursor().getSource();
		}

		@Override
		public F getValue() {
			return theValue;
		}
	}

	public static abstract class DefaultCompositeCondition<E> extends DefaultCondition<E>
		implements ConditionImpl.CompositeCondition<E, DefaultCondition<E>, DefaultAll<E>> {
		private final List<DefaultCondition<E>> theComponents;

		public DefaultCompositeCondition(Collection<? extends DefaultCondition<E>> components) {
			super(components.iterator().next().getEntityType());
			theComponents = QommonsUtils.unmodifiableCopy(components);
		}

		@Override
		public List<DefaultCondition<E>> getComponents() {
			return theComponents;
		}

		@Override
		public int hashCode() {
			return _hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return _equals(obj);
		}

		@Override
		public String toString() {
			return _toString();
		}
	}

	public static class DefaultOrCondition<E> extends DefaultCompositeCondition<E>
		implements ConditionImpl.OrCondition<E, DefaultCondition<E>, DefaultAll<E>> {
		public DefaultOrCondition(Collection<? extends DefaultCondition<E>> components) {
			super(components);
		}
	}

	public static class DefaultAndCondition<E> extends DefaultCompositeCondition<E>
		implements ConditionImpl.AndCondition<E, DefaultCondition<E>, DefaultAll<E>> {
		public DefaultAndCondition(Collection<? extends DefaultCondition<E>> components) {
			super(components);
		}
	}
}