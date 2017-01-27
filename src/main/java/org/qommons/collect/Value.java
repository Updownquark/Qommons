package org.qommons.collect;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.qommons.TriFunction;

import com.google.common.reflect.TypeToken;

public interface Value<T> {
	TypeToken<T> getType();

	T get();

	default <R> Value<R> mapV(Function<? super T, R> map) {
		return mapV(map, false);
	}

	default <R> Value<R> mapV(Function<? super T, R> map, boolean mapNulls) {
		return mapV((TypeToken<R>) TypeToken.of(map.getClass()).resolveType(Function.class.getTypeParameters()[1]), map, mapNulls);
	}

	default <R> Value<R> mapV(TypeToken<R> type, Function<? super T, R> map) {
		return mapV(type, map, false);
	}

	default <R> Value<R> mapV(TypeToken<R> type, Function<? super T, R> map, boolean mapNulls) {
		return new MappedValue<>(type, this, map, mapNulls);
	}

	default <U, R> Value<R> combineV(Value<U> value2, BiFunction<? super T, ? super U, R> combination) {
		return combineV(value2, combination, false);
	}

	default <U, R> Value<R> combineV(Value<U> value2, BiFunction<? super T, ? super U, R> combination, boolean combineNulls) {
		return combineV((TypeToken<R>) TypeToken.of(combination.getClass()).resolveType(Function.class.getTypeParameters()[2]), value2,
			combination, combineNulls);
	}

	default <U, R> Value<R> combineV(TypeToken<R> type, Value<U> value2, BiFunction<? super T, ? super U, R> combination,
		boolean combineNulls) {
		return new ComposedValue<>(type, values -> combination.apply((T) values[0], (U) values[1]), combineNulls, this, value2);
	}

	default <U, V, R> Value<R> combineV(Value<U> value2, Value<V> value3, TriFunction<? super T, ? super U, ? super V, R> combination) {
		return combineV(value2, value3, combination, false);
	}

	default <U, V, R> Value<R> combineV(Value<U> value2, Value<V> value3, TriFunction<? super T, ? super U, ? super V, R> combination,
		boolean combineNulls) {
		return combineV((TypeToken<R>) TypeToken.of(combination.getClass()).resolveType(Function.class.getTypeParameters()[3]), value2,
			value3, combination, combineNulls);
	}

	default <U, V, R> Value<R> combineV(TypeToken<R> type, Value<U> value2, Value<V> value3,
		TriFunction<? super T, ? super U, ? super V, R> combination, boolean combineNulls) {
		return new ComposedValue<>(type, values -> combination.apply((T) values[0], (U) values[1], (V) values[2]), combineNulls, this,
			value2, value3);
	}

	/**
	 * A shortened version of {@link #constant(TypeToken, Object)}. The type of the object will be value's class. This is not always a good
	 * idea. If the variable passed to this method may have a value that is a subclass of the variable's type, there may be unintended
	 * consequences of using this method. Also, the type cannot be derived if the value is null, so an {@link IllegalArgumentException} will
	 * be thrown in this case.
	 *
	 * In general, this shorthand method should only be used if the value is a literal or a newly constructed value.
	 *
	 * @param <X> The type of the value to wrap
	 * @param value The value to wrap
	 * @return An observable that always returns the given value
	 */
	public static <X> Value<X> constant(final X value) {
		if (value == null)
			throw new IllegalArgumentException("Cannot call constant(value) with a null value.  Use constant(TypeToken<X>, X).");
		return new ConstantValue<>(TypeToken.of((Class<X>) value.getClass()), value);
	}

	/**
	 * @param <X> The compile-time type of the value to wrap
	 * @param type The run-time type of the value to wrap
	 * @param value The value to wrap
	 * @return An observable that always returns the given value
	 */
	public static <X> Value<X> constant(final TypeToken<X> type, final X value) {
		return new ConstantValue<>(type, value);
	}

	/**
	 * @param <T> The compile-time super type of all observables contained in the nested observable
	 * @param ov The nested observable
	 * @return An observable value whose value is the value of <code>ov.get()</code>
	 */
	public static <T> Value<T> flatten(Value<? extends Value<? extends T>> ov) {
		return flatten(ov, () -> null);
	}

	/**
	 * @param <T> The compile-time super type of all observables contained in the nested observable
	 * @param ov The nested observable
	 * @param defaultValue The default value supplier for when the outer observable is empty
	 * @return An observable value whose value is the value of <code>ov.get()</code>
	 */
	public static <T> Value<T> flatten(Value<? extends Value<? extends T>> ov, Supplier<? extends T> defaultValue) {
		return new FlattenedValue<>(ov, defaultValue);
	}

	/**
	 * Creates a value that reflects the value of the first value in the given sequence passing the given test, or the value given by the
	 * default if none of the values in the sequence pass.
	 *
	 * @param <T> The type of the value
	 * @param test The test to for the value. If null, <code>v->v!=null</code> will be used
	 * @param def Supplies a default value in the case that none of the values in the sequence pass the test. If null, a null default will
	 *        be used.
	 * @param values The sequence of values to get the first passing value of
	 * @return The observable for the first passing value in the sequence
	 */
	public static <T> Value<T> firstValue(Predicate<? super T> test, Supplier<? extends T> def, Value<? extends T>... values) {
		return new FirstValue<>(values, test, def);
	}

	class MappedValue<T, R> implements Value<R> {
		private final TypeToken<R> theType;
		private final Value<T> theWrapped;
		private final Function<? super T, R> theMap;
		private final boolean mapNulls;

		public MappedValue(TypeToken<R> type, Value<T> wrap, Function<? super T, R> map, boolean mapNulls) {
			theType = type;
			theWrapped = wrap;
			theMap = map;
			this.mapNulls = mapNulls;
		}

		@Override
		public TypeToken<R> getType() {
			return theType;
		}

		protected Value<T> getWrapped() {
			return theWrapped;
		}

		protected Function<? super T, R> getMap() {
			return theMap;
		}

		@Override
		public R get() {
			T wrap = theWrapped.get();
			if (wrap == null && !mapNulls)
				return null;
			return theMap.apply(wrap);
		}
	}

	/**
	 * An observable that depends on the values of other observables
	 *
	 * @param <T> The type of the composed observable
	 */
	public class ComposedValue<T> implements Value<T> {
		private final TypeToken<T> theType;
		private final List<? extends Value<?>> theComposed;
		private final Function<Object[], T> theFunction;
		private final boolean combineNulls;

		/**
		 * @param type The type of the value
		 * @param function The function that operates on the argument values to produce this value's value
		 * @param combineNull Whether to apply the combination function if the arguments are null. If false and any arguments are null, the
		 *        result will be null.
		 * @param composed The argument values whose values are passed to the function
		 */
		public ComposedValue(TypeToken<T> type, Function<Object[], T> function, boolean combineNull, Value<?>... composed) {
			theType = type;
			theFunction = function;
			combineNulls = combineNull;
			theComposed = java.util.Collections.unmodifiableList(java.util.Arrays.asList(composed));
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		/** @return The values that compose this value */
		public List<? extends Value<?>> getComposed() {
			return theComposed;
		}

		/** @return The function used to map this value's composed values into its return value */
		public Function<Object[], T> getFunction() {
			return theFunction;
		}

		/**
		 * @return Whether the combination function will be applied if the arguments are null. If false and any arguments are null, the
		 *         result will be null.
		 */
		public boolean isNullCombined() {
			return combineNulls;
		}

		@Override
		public T get() {
			Object[] composed = new Object[theComposed.size()];
			for (int i = 0; i < composed.length; i++)
				composed[i] = theComposed.get(i).get();
			return combine(composed);
		}

		/**
		 * @param args The arguments to combine
		 * @return The combined value
		 */
		protected T combine(Object[] args) {
			if (!combineNulls) {
				for (Object arg : args)
					if (arg == null)
						return null;
			}
			return theFunction.apply(args.clone());
		}

		@Override
		public String toString() {
			return theComposed.toString();
		}
	}

	class ConstantValue<T> implements Value<T> {
		private final TypeToken<T> theType;
		private final T theValue;

		public ConstantValue(TypeToken<T> type, T value) {
			theType = type;
			theValue = value;
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public T get() {
			return theValue;
		}
	}

	/**
	 * Implements {@link Value#flatten(Value)}
	 *
	 * @param <T> The type of the value
	 */
	class FlattenedValue<T> implements Value<T> {
		private final TypeToken<T> theType;
		private final Value<? extends Value<? extends T>> theValue;
		private final Supplier<? extends T> theDefaultValue;

		protected FlattenedValue(Value<? extends Value<? extends T>> value, Supplier<? extends T> defaultValue) {
			if (value == null)
				throw new NullPointerException("Null observable");
			theType = (TypeToken<T>) value.getType().resolveType(Value.class.getTypeParameters()[0]);
			theValue = value;
			theDefaultValue = defaultValue;
		}

		protected Value<? extends Value<? extends T>> getWrapped() {
			return theValue;
		}

		/** @return The supplier of the default value, in case the outer observable is empty */
		protected Supplier<? extends T> getDefaultValue() {
			return theDefaultValue;
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public T get() {
			return get(theValue.get());
		}

		protected T get(Value<? extends T> value) {
			return value != null ? value.get() : theDefaultValue.get();
		}

		@Override
		public String toString() {
			return "flat(" + theValue + ")";
		}
	}

	/**
	 * Implements {@link Value#firstValue(Predicate, Supplier, Value...)}
	 *
	 * @param <T> The type of the value
	 */
	class FirstValue<T> implements Value<T> {
		private final TypeToken<T> theType;
		private final Value<? extends T>[] theValues;
		private final Predicate<? super T> theTest;
		private final Supplier<? extends T> theDefault;

		protected FirstValue(Value<? extends T>[] values, Predicate<? super T> test, Supplier<? extends T> def) {
			theValues = values;
			theTest = test == null ? v -> v != null : test;
			theDefault = def == null ? () -> null : def;
		}

		@Override
		public T get() {
			T value = null;
			for (Value<? extends T> v : theValues) {
				value = v.get();
				if (theTest.test(value))
					return value;
			}
			return theDefault == null ? null : theDefault.get();
		}
	}
}
