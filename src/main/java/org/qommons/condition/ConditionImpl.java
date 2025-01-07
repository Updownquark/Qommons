package org.qommons.condition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.qommons.StringUtils;

/**
 * Partially handles {@link Condition#or(Function)} and {@link Condition#and(Function)}
 * 
 * @param <E> The type of entities this condition is for
 * @param <C> The super-type of conditions in this condition's domain
 * @param <A> The type of the {@link All} condition in this condition's domain
 */
public interface ConditionImpl<E, C extends Condition<E, C, A>, A extends All<E, C, A>> extends Condition<E, C, A> {
	/** @return An integer representing the type of this condition */
	int getConditionType();

	/**
	 * @param conditions The conditions to OR
	 * @return The or condition instance
	 */
	C createOrCondition(Collection<? extends C> conditions);

	/**
	 * @param conditions The conditions to AND
	 * @return The and condition instance
	 */
	C createAndCondition(Collection<? extends C> conditions);

	/**
	 * @param condition Produces a condition to OR with this condition
	 * @return A condition that is true when either this condition or the new condition matches an entity
	 */
	@Override
	default C or(Function<? super A, ? extends C> condition) {
		C c = condition.apply(all());
		if (c instanceof All)
			return (C) this;
		else if (c instanceof OrCondition) {
			List<C> conditions = new ArrayList<>(((CompositeCondition<E, C, A>) c).getComponents().size() + 1);
			conditions.add((C) this);
			conditions.addAll(((CompositeCondition<E, C, A>) c).getComponents());
			return createOrCondition(conditions);
		} else
			return createOrCondition(Arrays.asList((C) this, c));
	}

	/**
	 * @param condition Produces a condition to AND with this condition
	 * @return A condition that is true when both this condition and the new condition matches an entity
	 */
	@Override
	default C and(Function<? super A, ? extends C> condition) {
		C c = condition.apply(all());
		if (c instanceof All)
			return (C) this;
		else if (c instanceof AndCondition) {
			List<C> conditions = new ArrayList<>(((CompositeCondition<E, C, A>) c).getComponents().size() + 1);
			conditions.add((C) this);
			conditions.addAll(((CompositeCondition<E, C, A>) c).getComponents());
			return createOrCondition(conditions);
		} else
			return createOrCondition(Arrays.asList((C) this, c));
	}

	/**
	 * Either an {@link ConditionImpl.OrCondition OR} or an {@link ConditionImpl.AndCondition AND} condition
	 *
	 * @param <E> The entity type of the condition
	 * @param <C> The super-type of conditions in this condition's domain
	 * @param <A> The type of the {@link All} condition in this condition's domain
	 */
	public interface CompositeCondition<E, C extends Condition<E, C, A>, A extends All<E, C, A>> extends ConditionImpl<E, C, A> {
		/** @return The component conditions of this composite */
		List<C> getComponents();

		@Override
		default A all() {
			return getComponents().get(0).all();
		}

		@Override
		default int compareTo(Condition<E, ?, ?> o) {
			if (this == o)
				return 0;
			else if (!(o instanceof ConditionImpl))
				return -o.compareTo(this);
			int comp = Integer.compare(getConditionType(), ((ConditionImpl<E, ?, ?>) o).getConditionType());
			if (comp != 0)
				return comp;
			CompositeCondition<E, ?, ?> other = (CompositeCondition<E, ?, ?>) o;
			for (int c = 0; c < getComponents().size() && c < other.getComponents().size(); c++) {
				comp = getComponents().get(c).compareTo(other.getComponents().get(c));
				if (comp != 0)
					return comp;
			}
			return Integer.compare(getComponents().size(), other.getComponents().size());
		}

		/**
		 * We can't implement {@link Object#hashCode()} here, so this method is available for implementations
		 * 
		 * @return The hash code for this composite condition
		 */
		default int _hashCode() {
			int hash = getClass().hashCode();
			hash = hash * 31 + getComponents().hashCode();
			return hash;
		}

		/**
		 * We can't implement {@link Object#equals(Object)} here, so this method is available for implementations
		 * 
		 * @param obj The object to compare to
		 * @return Whether this condition is equal to the given object
		 */
		default boolean _equals(Object obj) {
			if (this == obj)
				return true;
			else if (obj == null || obj.getClass() != getClass())
				return false;
			CompositeCondition<E, ?, ?> other = (CompositeCondition<E, ?, ?>) obj;
			for (C c : getComponents()) {
				if (!other.getComponents().contains(c))
					return false;
			}
			return true;
		}

		/**
		 * We can't implement {@link Object#toString()} here, so this method is available for implementations
		 * 
		 * @return The string representation of this condition
		 */
		default String _toString() {
			return StringUtils.print(new StringBuilder('('), ",", getComponents(), null).append(')').toString();
		}
	}

	/**
	 * A condition that passes if any one of its component conditions does
	 *
	 * @param <E> The entity type of the condition
	 * @param <C> The super-type of conditions in this condition's domain
	 * @param <A> The type of the {@link All} condition in this condition's domain
	 */
	public interface OrCondition<E, C extends Condition<E, C, A>, A extends All<E, C, A>> extends CompositeCondition<E, C, A> {
		@Override
		default int getConditionType() {
			return 10;
		}

		@Override
		default C or(Function<? super A, ? extends C> condition) {
			C other = condition.apply(all());
			if (getComponents().contains(other))
				return (C) this;
			List<C> conditions;
			if (other instanceof OrCondition) {
				conditions = new ArrayList<>(getComponents().size() + ((CompositeCondition<E, C, ?>) other).getComponents().size());
				conditions.addAll(getComponents());
				conditions.addAll(((CompositeCondition<E, C, ?>) other).getComponents());
			} else {
				conditions = new ArrayList<>(getComponents().size() + 1);
				conditions.addAll(getComponents());
				conditions.add(other);
			}
			return createOrCondition(conditions);
		}

		@Override
		default boolean contains(Condition<?, ?, ?> other) {
			for (C c : getComponents())
				if (c.contains(other))
					return true;
			if (other instanceof OrCondition) {
				for (Condition<E, ?, ?> c : ((CompositeCondition<E, ?, ?>) other).getComponents()) {
					if (!contains(c))
						return false;
				}
				return true;
			} else
				return false;
		}

		@Override
		default C transform(Function<? super C, ? extends C> transform) {
			List<C> conditions = new ArrayList<>();
			boolean different = false;
			for (C c : getComponents()) {
				C transformed = c.transform(transform);
				conditions.add(transformed);
				different |= transformed != c;
			}
			if (!different)
				return (C) this;
			else
				return createOrCondition(conditions);
		}

		@Override
		default String _toString() {
			return "OR" + CompositeCondition.super._toString();
		}
	}

	/**
	 * A condition that passes if all of its component conditions do
	 *
	 * @param <E> The entity type of the condition
	 * @param <C> The super-type of conditions in this condition's domain
	 * @param <A> The type of the {@link All} condition in this condition's domain
	 */
	public interface AndCondition<E, C extends Condition<E, C, A>, A extends All<E, C, A>> extends CompositeCondition<E, C, A> {
		@Override
		default int getConditionType() {
			return 11;
		}

		@Override
		default C and(Function<? super A, ? extends C> condition) {
			C other = condition.apply(all());
			if (getComponents().contains(other))
				return (C) this;
			List<C> conditions;
			if (other instanceof AndCondition) {
				conditions = new ArrayList<>(getComponents().size() + ((CompositeCondition<E, C, ?>) other).getComponents().size());
				conditions.addAll(getComponents());
				conditions.addAll(((CompositeCondition<E, C, ?>) other).getComponents());
			} else {
				conditions = new ArrayList<>(getComponents().size() + 1);
				conditions.addAll(getComponents());
				conditions.add(other);
			}
			return createAndCondition(conditions);
		}

		@Override
		default boolean contains(Condition<?, ?, ?> other) {
			boolean allContain = true;
			for (C condition : getComponents()) {
				if (!condition.contains(other)) {
					allContain = false;
					break;
				}
			}
			if (allContain)
				return true;
			if (other instanceof AndCondition) {
				for (Condition<E, ?, ?> condition : ((CompositeCondition<E, ?, ?>) other).getComponents()) {
					if (!condition.contains(this))
						return false;
				}
				return true;
			} else
				return false;
		}

		@Override
		default C transform(Function<? super C, ? extends C> transform) {
			List<C> conditions = new ArrayList<>();
			boolean different = false;
			for (C c : getComponents()) {
				C transformed = c.transform(transform);
				conditions.add(transformed);
				different |= transformed != c;
			}
			if (!different)
				return (C) this;
			else
				return createAndCondition(conditions);
		}

		@Override
		default String _toString() {
			return "AND" + CompositeCondition.super.toString();
		}
	}
}
