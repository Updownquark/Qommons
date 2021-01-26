package org.qommons.condition;

import java.util.function.Function;

/**
 * A {@link Condition} that is always true against any entity. This type also serves as the starting point for creating non-trivial
 * conditions via the {@link #where(ConditionalValueAccess)} methods.
 * 
 * @param <E> The type of the entity against which this condition can be evaluated
 * @param <C> The sub-type of this condition
 * @param <A> The sub-type of the {@link All} condition supplied by this condition's {@link #all()} method
 */
public interface All<E, C extends Condition<E, C, A>, A extends All<E, C, A>> extends Condition<E, C, A> {
	@Override
	default A all() {
		return (A) this;
	}

	@Override
	default C or(Function<? super A, ? extends C> condition) {
		return (C) this;
	}

	@Override
	default C and(Function<? super A, ? extends C> condition) {
		return condition.apply((A) this);
	}

	/**
	 * @param <F> The type of the field for the condition
	 * @param field The value access to use to derive a value to compare against the condition value (specified later)
	 * @return An intermediate to select the type of the comparison
	 */
	<F> ConditionIntermediate1<E, C, A, F> where(ConditionalValueAccess<E, F> field);

	/**
	 * @param <F> The type of the field for the condition
	 * @param field A function calling the entity field to use to derive a value to compare against the condition value (specified later).
	 *        Field sequences are not supported using this method.
	 * @return An intermediate to select the type of the comparison
	 */
	default <F> ConditionIntermediate1<E, C, A, F> where(Function<? super E, F> field) {
		return where(getEntityType().getField(field));
	}

	/**
	 * @param <I> The type of the field of this condition's entity to use as a starting point for the derivation chain
	 * @param <F> The type of the field for the condition
	 * @param init The function calling the entity field to serve as a starting point for the value access to use to derive a value to
	 *        compare against the condition value (specified later)
	 * @param field Creates a field sequence starting from the field (see {@link ConditionalValueAccess#dot(Function)}
	 * @return An intermediate to select the type of the comparison
	 */
	default <I, F> ConditionIntermediate1<E, C, A, F> where(Function<? super E, I> init,
		Function<ConditionalValueAccess<E, I>, ConditionalValueAccess<E, F>> field) {
		return where(//
			field.apply(//
				getEntityType().getField(init)));
	}

	@Override
	default C transform(Function<? super C, ? extends C> transform) {
		return transform.apply((C) this);
	}

	/**
	 * Returned from {@link All#where(ConditionalValueAccess)} to create a value-based condition
	 *
	 * @param <E> The type of entity this condition applies to
	 * @param <C> The sub-type of condition to produce
	 * @param <A> The type of the All condition
	 * @param <F> The type of the value the new condition will be based on
	 */
	public interface ConditionIntermediate1<E, C extends Condition<E, C, A>, A extends All<E, C, A>, F> {
		/** @return The source condition */
		public A getSource();

		/** @return The field to use to derive a value from the entity to compare against the condition value (specified later) */
		public ConditionalValueAccess<E, F> getField();

		/**
		 * @param ltEqGt The type of the comparison to make:
		 *        <ul>
		 *        <li>&lt;0 for less than or less-than-or-equal</li>
		 *        <li>0 for equal or not-equal</li>
		 *        <li>&gt;0 for greater than or greater-than-or-equal</li>
		 *        </ul>
		 * @param withEqual True for less-than-or-equal, equal, or greater-than-or-equal. False for less than, not-equal, or greater than.
		 * @return An intermediate to specify the condition value
		 */
		public ConditionIntermediate2<E, C, A, F> compare(int ltEqGt, boolean withEqual);

		/**
		 * For making an equals comparison
		 *
		 * @return An intermediate to specify the condition value
		 */
		default ConditionIntermediate2<E, C, A, F> equal() {
			return compare(0, true);
		}

		/**
		 * For making an not-equals comparison
		 *
		 * @return An intermediate to specify the condition value
		 */
		default ConditionIntermediate2<E, C, A, F> notEqual() {
			return compare(0, false);
		}

		/**
		 * For making a less than comparison
		 *
		 * @return An intermediate to specify the condition value
		 */
		default ConditionIntermediate2<E, C, A, F> lessThan() {
			return compare(-1, false);
		}

		/**
		 * For making a less than or equal comparison
		 *
		 * @return An intermediate to specify the condition value
		 */
		default ConditionIntermediate2<E, C, A, F> lessThanOrEqual() {
			return compare(-1, true);
		}

		/**
		 * For making a greater than comparison
		 *
		 * @return An intermediate to specify the condition value
		 */
		default ConditionIntermediate2<E, C, A, F> greaterThan() {
			return compare(1, false);
		}

		/**
		 * For making a greater than or equal comparison
		 *
		 * @return An intermediate to specify the condition value
		 */
		default ConditionIntermediate2<E, C, A, F> greaterThanOrEqual() {
			return compare(1, true);
		}

		/**
		 * For making an equals comparison. Shortcut for {@link #equal()}
		 *
		 * @return An intermediate to specify the condition value
		 */
		default ConditionIntermediate2<E, C, A, F> eq() {
			return equal();
		}

		/**
		 * For making an not-equals comparison. Shortcut for {@link #notEqual()}
		 *
		 * @return An intermediate to specify the condition value
		 */
		default ConditionIntermediate2<E, C, A, F> neq() {
			return notEqual();
		}

		/**
		 * For making a less than comparison. Shortcut for {@link #lessThan()}
		 *
		 * @return An intermediate to specify the condition value
		 */
		default ConditionIntermediate2<E, C, A, F> lt() {
			return lessThan();
		}

		/**
		 * For making a less than or equal comparison. Shortcut for {@link #lessThanOrEqual()}
		 *
		 * @return An intermediate to specify the condition value
		 */
		default ConditionIntermediate2<E, C, A, F> lte() {
			return lessThanOrEqual();
		}

		/**
		 * For making a greater than comparison. Shortcut for {@link #greaterThan()}
		 *
		 * @return An intermediate to specify the condition value
		 */
		default ConditionIntermediate2<E, C, A, F> gt() {
			return greaterThan();
		}

		/**
		 * For making a greater than or equal comparison. Shortcut for {@link #greaterThanOrEqual()}
		 *
		 * @return An intermediate to specify the condition value
		 */
		default ConditionIntermediate2<E, C, A, F> gte() {
			return greaterThanOrEqual();
		}

		/**
		 * For checking for a null value
		 *
		 * @return A condition that passes when the entity-derived value is null
		 */
		default C NULL() {
			return compare(0, true).value(null);
		}

		/**
		 * For checking for a non-null value
		 *
		 * @return A condition that passes when the entity-derived value is not null
		 */
		default C notNull() {
			return compare(0, false).value(null);
		}

		/**
		 * For checking for a null value
		 *
		 * @param isNull True if the condition should pass when the value is null; false if it should pass for NOT NULL
		 * @return A condition that passes when the entity-derived value is or is not null
		 */
		default C NULL(boolean isNull) {
			return compare(0, isNull).value(null);
		}
	}

	/**
	 * Returned from most of the methods in {@link All.ConditionIntermediate1} which is returned from
	 * {@link All#where(ConditionalValueAccess)}. Allows specification of the value or variable to compare the entity-derived value against.
	 *
	 * @param <E> The type of entity this condition applies to
	 * @param <C> The sub-type of this condition
	 * @param <A> The sub-type of the {@link All} condition supplied by this condition's {@link #all()} method
	 * @param <F> The type of the value the new condition will be based on
	 */
	public interface ConditionIntermediate2<E, C extends Condition<E, C, A>, A extends All<E, C, A>, F> {
		/**
		 * @return The precursor defining the field chain off of the source entity that the condition will apply to
		 */
		ConditionIntermediate1<E, C, A, F> getPrecursor();

		/**
		 * @return The comparison nature of the comparison of the entity-derived value to the target value of the condition. Combined with
		 *         the {@link #isWithEqual() equality} nature, this determines whether the comparison will be:
		 *         <ul>
		 *         <li>{@link ValueCondition#EQUALS equal to}</li>
		 *         <li>{@link ValueCondition#NOT_EQUALS not equal to}</li>
		 *         <li>{@link ValueCondition#LESS less than}</li>
		 *         <li>{@link ValueCondition#LESS_OR_EQUAL less than or equal}</li>
		 *         <li>{@link ValueCondition#GREATER greater than} or</li>
		 *         <li>{@link ValueCondition#GREATER_OR_EQUAL greater than or equal}</li>
		 *         </ul>
		 */
		int getComparison();

		/**
		 * @return The equality nature of the comparison of the entity-derived value to the target value of the condition. Combined with the
		 *         {@link #getComparison() comparison} nature, this determines whether the comparison will be:
		 *         <ul>
		 *         <li>{@link ValueCondition#EQUALS equal to}</li>
		 *         <li>{@link ValueCondition#NOT_EQUALS not equal to}</li>
		 *         <li>{@link ValueCondition#LESS less than}</li>
		 *         <li>{@link ValueCondition#LESS_OR_EQUAL less than or equal}</li>
		 *         <li>{@link ValueCondition#GREATER greater than} or</li>
		 *         <li>{@link ValueCondition#GREATER_OR_EQUAL greater than or equal}</li>
		 *         </ul>
		 */
		boolean isWithEqual();

		/**
		 * @param value The literal value to compare the entity-derived value against
		 * @return The new condition
		 */
		C value(F value);
	}
}
