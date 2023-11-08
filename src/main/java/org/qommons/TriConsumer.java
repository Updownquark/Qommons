package org.qommons;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A consumer of 3 arguments
 * 
 * @param <T> The type of the first argument
 * @param <U> The type of the second argument
 * @param <V> The type of the third argument
 */
public interface TriConsumer<T, U, V> {
	/**
	 * Accepts the arguments
	 * 
	 * @param arg1 The first argument
	 * @param arg2 The second argument
	 * @param arg3 The third argument
	 */
	void accept(T arg1, U arg2, V arg3);

	/**
	 * @param arg1 The first argument to this consumer
	 * @return A binary consumer that calls this ternary consumer with a constant first argument
	 */
	default BiConsumer<U, V> curry1(T arg1) {
		return new TriConsumerCurry1<>(this, arg1);
	}

	/**
	 * @param arg2 The second argument to this consumer
	 * @return A binary consumer that calls this ternary consumer with a constant second argument
	 */
	default BiConsumer<T, V> curry2(U arg2) {
		return new TriConsumerCurry2<>(this, arg2);
	}

	/**
	 * @param arg3 The third argument to this consumer
	 * @return A binary consumer that calls this ternary consumer with a constant third argument
	 */
	default BiConsumer<T, U> curry3(V arg3) {
		return new TriConsumerCurry3<>(this, arg3);
	}

	/**
	 * Implements {@link TriConsumer#curry1(Object)}
	 * 
	 * @param <T> The type of the first argument to the ternary consumer
	 * @param <U> The type of the second argument to the ternary consumer
	 * @param <V> The type of the second argument to the ternary consumer
	 */
	class TriConsumerCurry1<T, U, V> implements BiConsumer<U, V> {
		private final TriConsumer<T, U, V> theSource;
		private final T theArg1;

		TriConsumerCurry1(TriConsumer<T, U, V> source, T arg1) {
			theSource = source;
			theArg1 = arg1;
		}

		@Override
		public void accept(U arg2, V arg3) {
			theSource.accept(theArg1, arg2, arg3);
		}

		TriConsumer<T, U, V> getSource() {
			return theSource;
		}

		T getArg1() {
			return theArg1;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 1, theArg1);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof TriConsumerCurry1))
				return false;
			TriConsumerCurry1<?, ?, ?> other = (TriConsumerCurry1<?, ?, ?>) obj;
			return getSource().equals(other.getSource()) && theArg1.equals(other.getArg1());
		}

		@Override
		public String toString() {
			return theSource + ".curry1(" + theArg1 + ")";
		}
	}

	/**
	 * Implements {@link TriConsumer#curry2(Object)}
	 * 
	 * @param <T> The type of the first argument to the ternary consumer
	 * @param <U> The type of the second argument to the ternary consumer
	 * @param <V> The type of the second argument to the ternary consumer
	 */
	class TriConsumerCurry2<T, U, V> implements BiConsumer<T, V> {
		private final TriConsumer<T, U, V> theSource;
		private final U theArg2;

		TriConsumerCurry2(TriConsumer<T, U, V> source, U arg2) {
			theSource = source;
			theArg2 = arg2;
		}

		@Override
		public void accept(T arg1, V arg3) {
			theSource.accept(arg1, theArg2, arg3);
		}

		TriConsumer<T, U, V> getSource() {
			return theSource;
		}

		U getArg2() {
			return theArg2;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 2, theArg2);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof TriConsumerCurry2))
				return false;
			TriConsumerCurry2<?, ?, ?> other = (TriConsumerCurry2<?, ?, ?>) obj;
			return getSource().equals(other.getSource()) && theArg2.equals(other.getArg2());
		}

		@Override
		public String toString() {
			return theSource + ".curry2(" + theArg2 + ")";
		}
	}

	/**
	 * Implements {@link TriConsumer#curry3(Object)}
	 * 
	 * @param <T> The type of the first argument to the ternary consumer
	 * @param <U> The type of the second argument to the ternary consumer
	 * @param <V> The type of the second argument to the ternary consumer
	 */
	class TriConsumerCurry3<T, U, V> implements BiConsumer<T, U> {
		private final TriConsumer<T, U, V> theSource;
		private final V theArg3;

		TriConsumerCurry3(TriConsumer<T, U, V> source, V arg3) {
			theSource = source;
			theArg3 = arg3;
		}

		@Override
		public void accept(T arg1, U arg2) {
			theSource.accept(arg1, arg2, theArg3);
		}

		TriConsumer<T, U, V> getSource() {
			return theSource;
		}

		V getArg3() {
			return theArg3;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theSource, 3, theArg3);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof TriConsumerCurry3))
				return false;
			TriConsumerCurry3<?, ?, ?> other = (TriConsumerCurry3<?, ?, ?>) obj;
			return getSource().equals(other.getSource()) && theArg3.equals(other.getArg3());
		}

		@Override
		public String toString() {
			return theSource + ".curry3(" + theArg3 + ")";
		}
	}
}
