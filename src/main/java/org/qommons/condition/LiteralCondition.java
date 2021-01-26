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
public abstract class LiteralCondition<E, C extends Condition<E, C, A>, A extends All<E, C, A>, F> extends ValueCondition<E, C, A, F> {
	private final F theValue;

	LiteralCondition(ConditionalEntity<E> entityType, ConditionalValueAccess<E, F> field, F value, int comparison, boolean isWithEqual) {
		super(entityType, field, comparison, isWithEqual);
		theValue = value;
	}

	@Override
	public int getConditionType() {
		return 1;
	}

	/** @return The value to compare against */
	public F getValue() {
		return theValue;
	}

	@Override
	public boolean contains(Condition<?, ?, ?> other) {
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
	protected int compareValue(ValueCondition<? super E, ?, ?, ? super F> other) {
		return getField().compare(theValue, ((LiteralCondition<E, ?, ?, F>) other).getValue());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getField(), theValue, getComparison(), isWithEqual());
	}

	@Override
	public String toString() {
		return super.toString() + theValue;
	}
}