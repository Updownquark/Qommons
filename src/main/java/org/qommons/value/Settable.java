package org.qommons.value;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.TriFunction;

import com.google.common.reflect.TypeToken;

public interface Settable<T> extends Value<T> {
	/**
	 * @param <V> The type of the value to set
	 * @param value The value to assign to this value
	 * @param cause Something that may have caused this change
	 * @return The value that was previously set for in this container
	 * @throws IllegalArgumentException If the value is not acceptable or setting it fails
	 * @throws UnsupportedOperationException If this operation is not supported (e.g. because this value is {@link #isEnabled() disabled}
	 */
	<V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException;

	/**
	 * @param <V> The type of the value to check
	 * @param value The value to check
	 * @return null if the value is not known to be unacceptable for this value, or an error text if it is known to be unacceptable. A null
	 *         value returned from this method does not guarantee that a call to {@link #set(Object, Object)} for the same value will not
	 *         throw an IllegalArgumentException
	 */
	<V extends T> String isAcceptable(V value);

	/** @return An observable whose value reports null if this value can be set directly, or a string describing why it cannot */
	Value<String> isEnabled();

	default Value<T> unsettable() {
		return new Value<T>() {
			@Override
			public TypeToken<T> getType() {
				return Settable.this.getType();
			}

			@Override
			public T get() {
				return Settable.this.get();
			}

			@Override
			public String toString() {
				return Settable.this.toString();
			}
		};
	}

	default Settable<T> filterAccept(Function<? super T, String> accept) {
		Settable<T> outer = this;
		return new Settable<T>() {
			@Override
			public TypeToken<T> getType() {
				return outer.getType();
			}

			@Override
			public T get() {
				return outer.get();
			}

			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
				String error = accept.apply(value);
				if (error != null)
					throw new IllegalArgumentException(error);
				return outer.set(value, cause);
			}

			@Override
			public <V extends T> String isAcceptable(V value) {
				String error = accept.apply(value);
				if (error != null)
					return error;
				return outer.isAcceptable(value);
			}

			@Override
			public Value<String> isEnabled() {
				return outer.isEnabled();
			}

			@Override
			public String toString() {
				return Settable.this.toString();
			}
		};
	}

	/**
	 * Allows an action to be performed each time {@link #set(Object, Object)} on this value is called.
	 *
	 * @param onSetAction The action to invoke just before {@link #set(Object, Object)} is called
	 * @return The settable
	 */
	default Settable<T> onSet(Consumer<T> onSetAction) {
		Settable<T> outer = this;
		return new Settable<T>() {
			@Override
			public TypeToken<T> getType() {
				return outer.getType();
			}

			@Override
			public T get() {
				return outer.get();
			}

			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
				onSetAction.accept(value);
				return outer.set(value, cause);
			}

			@Override
			public <V extends T> String isAcceptable(V value) {
				return outer.isAcceptable(value);
			}

			@Override
			public Value<String> isEnabled() {
				return outer.isEnabled();
			}

			@Override
			public String toString() {
				return outer.toString();
			}
		};
	}

	/**
	 * @param <R> The type of the new settable value to create
	 * @param type The run-time type of the new value
	 * @param function The function to map this value to another
	 * @param reverse The function to map the other value to this one
	 * @param combineNull Whether to apply the combination function if the arguments are null. If false and any arguments are null, the
	 *        result will be null.
	 * @return The mapped settable value
	 */
	public default <R> Settable<R> mapV(TypeToken<R> type, Function<? super T, R> function, Function<? super R, ? extends T> reverse,
		boolean combineNull) {
		Settable<T> root = this;
		return new ComposedSettable<R>(type, args -> {
			return function.apply((T) args[0]);
		}, combineNull, this) {
			@Override
			public <V extends R> R set(V value, Object cause) throws IllegalArgumentException {
				T old = root.set(reverse.apply(value), cause);
				if (old != null || combineNull)
					return function.apply(old);
				return null;
			}

			@Override
			public <V extends R> String isAcceptable(V value) {
				return root.isAcceptable(reverse.apply(value));
			}

			@Override
			public Value<String> isEnabled() {
				return root.isEnabled();
			}
		};
	}

	/**
	 * Composes this settable value with another observable value
	 *
	 * @param <U> The type of the value to compose this value with
	 * @param <R> The type of the new settable value to create
	 * @param type The run-time type of the new value
	 * @param function The function to combine the values into another value
	 * @param arg The value to combine this value with
	 * @param reverse The function to reverse the transformation
	 * @param combineNull Whether to apply the combination function if the arguments are null. If false and any arguments are null, the
	 *        result will be null.
	 * @return The composed settable value
	 */
	public default <U, R> Settable<R> combineV(TypeToken<R> type, BiFunction<? super T, ? super U, R> function, Value<U> arg,
		BiFunction<? super R, ? super U, ? extends T> reverse, boolean combineNull) {
		Settable<T> root = this;
		return new ComposedSettable<R>(type, args -> {
			return function.apply((T) args[0], (U) args[1]);
		}, combineNull, this, arg) {
			@Override
			public <V extends R> R set(V value, Object cause) throws IllegalArgumentException {
				U argVal = arg.get();
				T old = root.set(reverse.apply(value, argVal), cause);
				if (old != null || combineNull)
					return function.apply(old, argVal);
				else
					return null;
			}

			@Override
			public <V extends R> String isAcceptable(V value) {
				return root.isAcceptable(reverse.apply(value, arg.get()));
			}

			@Override
			public Value<String> isEnabled() {
				return root.isEnabled();
			}
		};
	}

	/**
	 * Composes this settable value with another observable value
	 *
	 * @param <U> The type of the value to compose this value with
	 * @param <R> The type of the new settable value to create
	 * @param type The run-time type of the new value
	 * @param function The function to combine the values into another value
	 * @param arg The value to combine this value with
	 * @param accept The function to filter acceptance of values for the new value
	 * @param reverse The function to reverse the transformation
	 * @param combineNull Whether to apply the filter to null values or simply preserve the null
	 * @return The composed settable value
	 */
	public default <U, R> Settable<R> combineV(TypeToken<R> type, BiFunction<? super T, ? super U, R> function, Value<U> arg,
		BiFunction<? super R, ? super U, String> accept, BiFunction<? super R, ? super U, ? extends T> reverse, boolean combineNull) {
		Settable<T> root = this;
		return new ComposedSettable<R>(type, args -> {
			return function.apply((T) args[0], (U) args[1]);
		}, combineNull, this, arg) {
			@Override
			public <V extends R> R set(V value, Object cause) throws IllegalArgumentException {
				U argVal = arg.get();
				T old = root.set(reverse.apply(value, argVal), cause);
				if (old != null || combineNull)
					return function.apply(old, argVal);
				else
					return null;
			}

			@Override
			public <V extends R> String isAcceptable(V value) {
				U argVal = arg.get();
				String ret = accept.apply(value, argVal);
				if (ret == null)
					ret = root.isAcceptable(reverse.apply(value, arg.get()));
				return ret;
			}

			@Override
			public Value<String> isEnabled() {
				return root.isEnabled();
			}
		};
	}

	/**
	 * Composes this settable value with 2 other observable values
	 *
	 * @param <U> The type of the first value to compose this value with
	 * @param <V> The type of the second value to compose this value with
	 * @param <R> The type of the new settable value to create
	 * @param type The run-time type of the new value
	 * @param function The function to combine the values into another value
	 * @param arg2 The first other value to combine this value with
	 * @param arg3 The second other value to combine this value with
	 * @param reverse The function to reverse the transformation
	 * @param combineNull Whether to apply the combination function if the arguments are null. If false and any arguments are null, the
	 *        result will be null.
	 * @return The composed settable value
	 */
	public default <U, V, R> Settable<R> combineV(TypeToken<R> type, TriFunction<? super T, ? super U, ? super V, R> function,
		Value<U> arg2, Value<V> arg3,
		TriFunction<? super R, ? super U, ? super V, ? extends T> reverse, boolean combineNull) {
		Settable<T> root = this;
		return new ComposedSettable<R>(type, args -> {
			return function.apply((T) args[0], (U) args[1], (V) args[2]);
		}, combineNull, this, arg2, arg3) {
			@Override
			public <V2 extends R> R set(V2 value, Object cause) throws IllegalArgumentException {
				U arg2Val = arg2.get();
				V arg3Val = arg3.get();
				T old = root.set(reverse.apply(value, arg2Val, arg3Val), cause);
				if (old != null || combineNull)
					return function.apply(old, arg2Val, arg3Val);
				else
					return null;
			}

			@Override
			public <V2 extends R> String isAcceptable(V2 value) {
				return root.isAcceptable(reverse.apply(value, arg2.get(), arg3.get()));
			}

			@Override
			public Value<String> isEnabled() {
				return root.isEnabled();
			}
		};
	}

	/**
	 * @param <T> The type of the inner value
	 * @param value An observable value that supplies settable values
	 * @return A settable value that represents the current value in the inner observable
	 */
	public static <T> Settable<T> flatten(Value<Settable<T>> value) {
		return flatten(value, () -> null);
	}

	/**
	 * @param <T> The type of the inner value
	 * @param value An observable value that supplies settable values
	 * @param defaultValue The default value supplier for when the outer observable is empty
	 * @return A settable value that represents the current value in the inner observable
	 */
	public static <T> Settable<T> flatten(Value<Settable<T>> value, Supplier<? extends T> defaultValue) {
		return new SettableFlattenedValue<>(value, defaultValue);
	}

	/**
	 * @param <T> The type of the inner value
	 * @param value An observable value that supplies observable values that may possibly be settable
	 * @param defaultValue The default value supplier for when the outer observable is empty
	 * @return A settable value that represents the current value in the inner observable
	 */
	public static <T> Settable<T> flattenAsSettable(Value<? extends Value<T>> value, Supplier<? extends T> defaultValue) {
		return new SettableFlattenedValue<>(value, defaultValue);
	}

	/**
	 * Implements the Settable.combine methods
	 *
	 * @param <T> The type of the value
	 */
	abstract class ComposedSettable<T> extends ComposedValue<T> implements Settable<T> {
		public ComposedSettable(TypeToken<T> type, Function<Object[], T> function, boolean combineNull, Value<?>... composed) {
			super(type, function, combineNull, composed);
		}
	}

	/**
	 * Implements {@link Settable#flatten(Value)}
	 *
	 * @param <T> The type of the value
	 */
	class SettableFlattenedValue<T> extends FlattenedValue<T> implements Settable<T> {
		protected SettableFlattenedValue(Value<? extends Value<? extends T>> value, Supplier<? extends T> defaultValue) {
			super(value, defaultValue);
		}

		@Override
		public Value<String> isEnabled() {
			Value<Value<String>> wrapE = getWrapped().mapV(sv -> {
				if (sv == null)
					return Value.constant("No wrapped value to set");
				else if (sv instanceof Settable)
					return ((Settable<? extends T>) sv).isEnabled();
				else
					return Value.constant("Wrapped value is not settable");
			});
			return Value.flatten(wrapE);
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
			Value<? extends T> sv = getWrapped().get();
			if (sv == null)
				return "No wrapped value to set";
			else if (sv instanceof Settable)
				return ((Settable<T>) sv).isAcceptable(value);
			else
				return "Wrapped value is not settable";
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
			Value<? extends T> sv = getWrapped().get();
			if (sv == null)
				throw new IllegalArgumentException("No wrapped value to set");
			else if (sv instanceof Settable)
				return ((Settable<T>) sv).set(value, cause);
			else
				throw new IllegalArgumentException("Wrapped value is not settable");
		}
	}
}
