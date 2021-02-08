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
public interface ValueCondition<E, C extends Condition<E, C, A>, A extends All<E, C, A>, F> extends ConditionImpl<E, C, A> {
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

	/** @return The field to compare values of */
	ConditionalValueAccess<E, F> getField();

	/** @return Whether to compare as less than (&lt;0), greater than (&gt;0), or equal (0) */
	int getComparison();

	/** @return Modifies the condition to be either &lt;=, &gt;=, or == (when true); or &lt;, &gt;, or != (when false) */
	boolean isWithEqual();

	/**
	 * @return ?(\u2264), &lt;, =, ?(\u2260), ?(\u2265), or &gt;
	 * @see #EQUALS
	 * @see #NOT_EQUALS
	 * @see #LESS
	 * @see #LESS_OR_EQUAL
	 * @see #GREATER
	 * @see #GREATER_OR_EQUAL
	 */
	default char getSymbol() {
		if (getComparison() < 0) {
			if (isWithEqual())
				return LESS_OR_EQUAL;
			else
				return LESS;
		} else if (getComparison() == 0) {
			if (isWithEqual())
				return EQUALS;
			else
				return NOT_EQUALS;
		} else {
			if (isWithEqual())
				return GREATER_OR_EQUAL;
			else
				return GREATER;
		}
	}

	/**
	 * @param other The value condition to compare to
	 * @return The comparison of this condition's value to the other's
	 */
	int compareValue(ValueCondition<? super E, ?, ?, ? super F> other);

	@Override
	default int compareTo(Condition<E, ?, ?> o) {
		if (!(o instanceof ConditionImpl))
			return -o.compareTo(this);
		int comp = Integer.compare(getConditionType(), ((ConditionImpl<?, ?, ?>) o).getConditionType());
		ValueCondition<E, ?, ?, ?> other = (ValueCondition<E, ?, ?, ?>) o;
		if (comp == 0)
			comp = getField().compareTo(other.getField());
		if (comp == 0)
			comp = Integer.compare(getComparison(), other.getComparison());
		if (comp == 0)
			comp = Boolean.compare(isWithEqual(), other.isWithEqual());
		if (comp == 0)
			comp = compareValue((ValueCondition<E, ?, ?, F>) other);
		return comp;
	}

	default boolean test(F conditionValue, F targetValue) {
		if (getComparison() == 0) {
			boolean equal = Objects.equals(conditionValue, targetValue);
			return equal == isWithEqual();
		}
		int targetCompare = getField().compare(conditionValue, targetValue);
		if (targetCompare == 0) {
			return isWithEqual();
		} else if (targetCompare < 0) {
			return getComparison() < 0;
		} else
			return getComparison() > 0;
	}

	int _hashCode();

	default boolean _equals(Object obj) {
		if (obj == this)
			return true;
		else if (!(obj instanceof LiteralCondition))
			return false;
		ValueCondition<?, ?, ?, ?> other = (ValueCondition<?, ?, ?, ?>) obj;
		return getField().equals(other.getField())//
			&& getComparison() == other.getComparison() && isWithEqual() == other.isWithEqual()//
			&& compareValue((ValueCondition<E, ?, ?, F>) other) == 0;
	}

	default String _toString() {
		return new StringBuilder().append(getField()).append(getSymbol()).toString();
	}
}