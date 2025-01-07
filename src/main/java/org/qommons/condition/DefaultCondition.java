package org.qommons.condition;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.qommons.QommonsUtils;
import org.qommons.condition.All.ConditionIntermediate1;
import org.qommons.condition.All.ConditionIntermediate2;

/**
 * Abstract {@link Condition} implementation
 * 
 * @param <E> The type of entity this condition is for
 */
public abstract class DefaultCondition<E> implements ConditionImpl<E, DefaultCondition<E>, DefaultCondition.DefaultAll<E>> {
	private final ConditionalEntity<E> theEntity;

	/** @param entity The entity this condition is for */
	protected DefaultCondition(ConditionalEntity<E> entity) {
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

	/**
	 * Default {@link All} implementation
	 * 
	 * @param <E> The type of entity this condition is for
	 */
	public static class DefaultAll<E> extends DefaultCondition<E> implements All<E, DefaultCondition<E>, DefaultAll<E>> {
		/** @param entity The entity this condition is for */
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

	/**
	 * Default {@link All.ConditionIntermediate1} implementation
	 * 
	 * @param <E> The type of entity to create the condition for
	 * @param <F> The type of field that the condition operates on
	 */
	public static class DefaultCI1<E, F> implements ConditionIntermediate1<E, DefaultCondition<E>, DefaultAll<E>, F> {
		private final DefaultAll<E> theSource;
		private final ConditionalValueAccess<E, F> theField;

		/**
		 * @param source The {@link All} source for this condition intermediate
		 * @param field The field that the condition will operate on
		 */
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

	/**
	 * Default {@link All.ConditionIntermediate2} implementation
	 * 
	 * @param <E> The type of entity to create the condition for
	 * @param <F> The type of field that the condition operates on
	 */
	public static class DefaultCI2<E, F> implements ConditionIntermediate2<E, DefaultCondition<E>, DefaultAll<E>, F> {
		private final DefaultCI1<E, F> thePrecursor;
		private final int theComparison;
		private final boolean isWithEqual;

		/**
		 * @param precursor The precursor to this second-stage condition intermediate
		 * @param comparison The comparison for the condition (-1 for &lt; or &lt;=, 0 for == or !=, 1 for &gt; or &gte;)
		 * @param isWithEqual Whether equality results in true
		 */
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

	/**
	 * A literal condition
	 * 
	 * @param <E> The type of entity this condition is for
	 * @param <F> The type of field that the condition operates on
	 */
	public static class DefaultLiteralCondition<E, F> extends DefaultCondition<E>
		implements LiteralCondition<E, DefaultCondition<E>, DefaultAll<E>, F> {
		private final DefaultCI2<E, F> theIntermediate;
		private final F theValue;

		/**
		 * @param intermediate The stage 2 intermediate producing this condition
		 * @param value The literal value to compare the field value against
		 */
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

	/**
	 * Abstract OR/AND condition implementation
	 * 
	 * @param <E> The type of entity this condition is for
	 */
	public static abstract class DefaultCompositeCondition<E> extends DefaultCondition<E>
		implements ConditionImpl.CompositeCondition<E, DefaultCondition<E>, DefaultAll<E>> {
		private final List<DefaultCondition<E>> theComponents;

		/** @param components The components of this condition */
		protected DefaultCompositeCondition(Collection<? extends DefaultCondition<E>> components) {
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

	/**
	 * Default OR implementation
	 * 
	 * @param <E> The type of entity this condition is for
	 */
	public static class DefaultOrCondition<E> extends DefaultCompositeCondition<E>
		implements ConditionImpl.OrCondition<E, DefaultCondition<E>, DefaultAll<E>> {
		/** @param components The components of this condition */
		public DefaultOrCondition(Collection<? extends DefaultCondition<E>> components) {
			super(components);
		}
	}

	/**
	 * Default AND implementation
	 * 
	 * @param <E> The type of entity this condition is for
	 */
	public static class DefaultAndCondition<E> extends DefaultCompositeCondition<E>
		implements ConditionImpl.AndCondition<E, DefaultCondition<E>, DefaultAll<E>> {
		/** @param components The components of this condition */
		public DefaultAndCondition(Collection<? extends DefaultCondition<E>> components) {
			super(components);
		}
	}
}