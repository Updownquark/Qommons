package org.qommons.condition;

import java.util.Objects;

/**
 * A condition that tests the value of an entity's field against a {@link LiteralCondition literal} or some other value.
 *
 * @param <E> The entity type of the condition
 * @param <C> The sub-type of this condition
 * @param <A> The sub-type of the {@link All} condition supplied by this condition's {@link #all()} method
 * @param <F> The type of the field
 */
public abstract class ValueCondition<E, C extends Condition<E, C, A>, A extends All<E, C, A>, F> extends ConditionImpl<E, C, A> {
	/** The condition {@link #getSymbol() symbol} for equality, = */
	public static final char EQUALS = '=';
	/** The condition {@link #getSymbol() symbol} for inequality, ?(\u2260) */
	public static final char NOT_EQUALS = '\u2260';
	/** The condition {@link #getSymbol() symbol} for less than, &lt; */
	public static final char LESS = '<';
	/** The condition {@link #getSymbol() symbol} for less than or equal, ?(\u2264) */
	public static final char LESS_OR_EQUAL = '\u2264';
	/** The condition {@link #getSymbol() symbol} for greater than, &gt; */
	public static final char GREATER = '>';
	/** The condition {@link #getSymbol() symbol} for greater than or equal, ?(\u2265) */
	public static final char GREATER_OR_EQUAL = '\u2264';

	private final ConditionalValueAccess<E, F> theField;
	private final int theComparison;
	private final boolean isWithEqual;

	/**
	 * @param entityType The entity type for the condition
	 * @param field The field value to compare against
	 * @param comparison Whether to compare as less than (&lt;0), greater than (&gt;0), or equal (0)
	 * @param isWithEqual Modifies the condition to be either &lt;=, &gt;=, or == (when true); or &lt;, &gt;, or != (when false)
	 */
	protected ValueCondition(ConditionalEntity<E> entityType, ConditionalValueAccess<E, F> field, int comparison, boolean isWithEqual) {
		super(entityType);
		theField = field;
		theComparison = comparison;
		this.isWithEqual = isWithEqual;
	}

	/** @return The field to compare values of */
	public ConditionalValueAccess<E, F> getField() {
		return theField;
	}

	/** @return Whether to compare as less than (&lt;0), greater than (&gt;0), or equal (0) */
	public int getComparison() {
		return theComparison;
	}

	/** @return Modifies the condition to be either &lt;=, &gt;=, or == (when true); or &lt;, &gt;, or != (when false) */
	public boolean isWithEqual() {
		return isWithEqual;
	}

	/**
	 * @return ?(\u2264), &lt;, =, ?(\u2260), ?(\u2265), or &gt;
	 * @see #EQUALS
	 * @see #NOT_EQUALS
	 * @see #LESS
	 * @see #LESS_OR_EQUAL
	 * @see #GREATER
	 * @see #GREATER_OR_EQUAL
	 */
	public char getSymbol() {
		if (theComparison < 0) {
			if (isWithEqual)
				return LESS_OR_EQUAL;
			else
				return LESS;
		} else if (theComparison == 0) {
			if (isWithEqual)
				return EQUALS;
			else
				return NOT_EQUALS;
		} else {
			if (isWithEqual)
				return GREATER_OR_EQUAL;
			else
				return GREATER;
		}
	}

	/**
	 * @param other The value condition to compare to
	 * @return The comparison of this condition's value to the other's
	 */
	protected abstract int compareValue(ValueCondition<? super E, ?, ?, ? super F> other);

	@Override
	public int compareTo(Condition<E, ?, ?> o) {
		if (!(o instanceof ConditionImpl))
			return -o.compareTo(this);
		int comp = Integer.compare(getConditionType(), ((ConditionImpl<?, ?, ?>) o).getConditionType());
		ValueCondition<E, ?, ?, ?> other = (ValueCondition<E, ?, ?, ?>) o;
		if (comp == 0)
			comp = getField().compareTo(other.getField());
		if (comp == 0)
			comp = Integer.compare(getComparison(), other.getComparison());
		if (comp == 0)
			comp = Boolean.compare(isWithEqual, other.isWithEqual);
		if (comp == 0)
			comp = compareValue((ValueCondition<E, ?, ?, F>) other);
		return comp;
	}

	public boolean test(F conditionValue, F targetValue) {
		if (theComparison == 0) {
			boolean equal = Objects.equals(conditionValue, targetValue);
			return equal == isWithEqual;
		}
		int targetCompare = getField().compare(conditionValue, targetValue);
		if (targetCompare == 0) {
			return isWithEqual;
		} else if (targetCompare < 0) {
			return theComparison < 0;
		} else
			return theComparison > 0;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (!(obj instanceof LiteralCondition))
			return false;
		ValueCondition<?, ?, ?, ?> other = (ValueCondition<?, ?, ?, ?>) obj;
		return getField().equals(other.getField())//
			&& getComparison() == other.getComparison() && isWithEqual() == other.isWithEqual()//
			&& compareValue((ValueCondition<E, ?, ?, F>) other) == 0;
	}

	@Override
	public String toString() {
		return new StringBuilder().append(theField).append(getSymbol()).toString();
	}
}