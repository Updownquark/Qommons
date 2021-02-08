package org.qommons.condition;

import java.util.Objects;

/**
 * A condition that tests the value of an entity's field against a constant value
 *
 * @param <E> The entity type of the condition
 * @param <C> The sub-type of this condition
 * @param <A> The sub-type of the {@link All} condition supplied by this condition's {@link #all()} method
 * @param <F> The type of the field
 */
public interface LiteralCondition<E, C extends Condition<E, C, A>, A extends All<E, C, A>, F> extends ValueCondition<E, C, A, F> {
	@Override
	default int getConditionType() {
		return 1;
	}

	/** @return The value to compare against */
	F getValue();

	@Override
	default boolean contains(Condition<?, ?, ?> other) {
		if (other == this)
			return true;
		else if (other instanceof ConditionImpl.AndCondition) {
			for (Condition<?, ?, ?> c : ((ConditionImpl.AndCondition<?, ?, ?>) other).getComponents())
				if (c.contains(this))
					return true;
			return false;
		} else if (!(other instanceof LiteralCondition))
			return false;
		else if (!getField().isOverride(((LiteralCondition<? extends E, ?, ?, ?>) other).getField()))
			return false;

		LiteralCondition<? super E, ?, ?, ? super F> otherV = (LiteralCondition<? super E, ?, ?, ? super F>) other;
		if (getComparison() < 0) {
			if (otherV.getComparison() > 0)
				return false;
			else if (otherV.getComparison() < 0) {
				int comp = compareValue(otherV);
				if (comp > 0)
					return true;
				else if (comp == 0)
					return isWithEqual() || !otherV.isWithEqual();
				else
					return false;
			} else if (!isWithEqual() || !otherV.isWithEqual())
				return false;
			else
				return compareValue(otherV) == 0;
		} else if (getComparison() > 0) {
			if (otherV.getComparison() < 0)
				return false;
			else if (otherV.getComparison() > 0) {
				int comp = compareValue(otherV);
				if (comp < 0)
					return true;
				else if (comp == 0)
					return isWithEqual() || !otherV.isWithEqual();
				else
					return false;
			} else if (!isWithEqual() || !otherV.isWithEqual())
				return false;
			else
				return compareValue(otherV) == 0;
		} else {
			return otherV.getComparison() == 0 && isWithEqual() == otherV.isWithEqual() && compareValue(otherV) == 0;
		}
	}

	@Override
	default int compareValue(ValueCondition<? super E, ?, ?, ? super F> other) {
		return getField().compare(getValue(), ((LiteralCondition<E, ?, ?, F>) other).getValue());
	}

	@Override
	default int _hashCode() {
		return Objects.hash(getField(), getValue(), getComparison(), isWithEqual());
	}

	@Override
	default String _toString() {
		return ValueCondition.super._toString() + getValue();
	}
}