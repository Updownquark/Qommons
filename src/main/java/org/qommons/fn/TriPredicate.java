package org.qommons.fn;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * A predicate of 3 arguments
 * 
 * @param <T> The type of the first argument
 * @param <U> The type of the second argument
 * @param <V> The type of the third argument
 */
public interface TriPredicate<T, U, V> {
	/**
	 * @param arg1 The first argument
	 * @param arg2 The second argument
	 * @param arg3 The third argument
	 * @return Whether this predicate's test passes for the given 3 arguments
	 */
	boolean test(T arg1, U arg2, V arg3);

	/**
	 * @param arg1 The first argument to this predicate
	 * @return A binary predicate that calls this ternary predicate with a constant first argument
	 */
	default BiPredicate<U, V> curry1(T arg1) {
		return new TriPredicateCurry1<>(this, arg1);
	}

	/**
	 * @param arg2 The second argument to this predicate
	 * @return A binary predicate that calls this ternary predicate with a constant second argument
	 */
	default BiPredicate<T, V> curry2(U arg2) {
		return new TriPredicateCurry2<>(this, arg2);
	}

	/**
	 * @param arg3 The third argument to this predicate
	 * @return A binary predicate that calls this ternary predicate with a constant third argument
	 */
	default BiPredicate<T, U> curry3(V arg3) {
		return new TriPredicateCurry3<>(this, arg3);
	}

	/**
	 * @param <A1> The type of the first argument to the ternary predicate
	 * @param <A2> The type of the second argument to the ternary predicate
	 * @param <A3> The type of the third argument to the ternary predicate
	 * @param function The implementation
	 * @param print The string for the wrapped predicate's {@link #toString()}
	 * @param identity The identity for the predicate's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable ternary predicate
	 */
	static <A1, A2, A3> TriPredicate<A1, A2, A3> printable(TriPredicate<? super A1, ? super A2, ? super A3> function, String print,
		Object identity) {
		return printable(function, () -> print, identity);
	}

	/**
	 * @param <A1> The type of the first argument to the ternary predicate
	 * @param <A2> The type of the second argument to the ternary predicate
	 * @param <A3> The type of the third argument to the ternary predicate
	 * @param function The implementation
	 * @param print Supplies the string for the wrapped predicate's {@link #toString()}
	 * @param identity The identity for the predicate's {@link #hashCode()} and {@link #equals(Object)} methods
	 * @return The wrapped, printable ternary predicate
	 */
	static <A1, A2, A3> TriPredicate<A1, A2, A3> printable(TriPredicate<? super A1, ? super A2, ? super A3> function,
		Supplier<String> print, Object identity) {
		return new PrintableTriPredicate<>(function, print, identity);
	}

	/**
	 * Implements {@link TriPredicate#curry1(Object)}
	 * 
	 * @param <T> The type of the first argument to the ternary predicate
	 * @param <U> The type of the second argument to the ternary predicate
	 * @param <V> The type of the second argument to the ternary predicate
	 */
	class TriPredicateCurry1<T, U, V> implements BiPredicate<U, V> {
		private final TriPredicate<T, U, V> theSource;
		private final T theArg1;

		TriPredicateCurry1(TriPredicate<T, U, V> source, T arg1) {
			theSource = source;
			theArg1 = arg1;
		}

		@Override
		public boolean test(U arg2, V arg3) {
			return theSource.test(theArg1, arg2, arg3);
		}

		TriPredicate<T, U, V> getSource() {
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
			else if (!(obj instanceof TriPredicateCurry1))
				return false;
			TriPredicateCurry1<?, ?, ?> other = (TriPredicateCurry1<?, ?, ?>) obj;
			return getSource().equals(other.getSource()) && theArg1.equals(other.getArg1());
		}

		@Override
		public String toString() {
			return theSource + ".curry1(" + theArg1 + ")";
		}
	}

	/**
	 * Implements {@link TriPredicate#curry2(Object)}
	 * 
	 * @param <T> The type of the first argument to the ternary predicate
	 * @param <U> The type of the second argument to the ternary predicate
	 * @param <V> The type of the second argument to the ternary predicate
	 */
	class TriPredicateCurry2<T, U, V> implements BiPredicate<T, V> {
		private final TriPredicate<T, U, V> theSource;
		private final U theArg2;

		TriPredicateCurry2(TriPredicate<T, U, V> source, U arg2) {
			theSource = source;
			theArg2 = arg2;
		}

		@Override
		public boolean test(T arg1, V arg3) {
			return theSource.test(arg1, theArg2, arg3);
		}

		TriPredicate<T, U, V> getSource() {
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
			else if (!(obj instanceof TriPredicateCurry2))
				return false;
			TriPredicateCurry2<?, ?, ?> other = (TriPredicateCurry2<?, ?, ?>) obj;
			return getSource().equals(other.getSource()) && theArg2.equals(other.getArg2());
		}

		@Override
		public String toString() {
			return theSource + ".curry2(" + theArg2 + ")";
		}
	}

	/**
	 * Implements {@link TriPredicate#curry3(Object)}
	 * 
	 * @param <T> The type of the first argument to the ternary predicate
	 * @param <U> The type of the second argument to the ternary predicate
	 * @param <V> The type of the second argument to the ternary predicate
	 */
	class TriPredicateCurry3<T, U, V> implements BiPredicate<T, U> {
		private final TriPredicate<T, U, V> theSource;
		private final V theArg3;

		TriPredicateCurry3(TriPredicate<T, U, V> source, V arg3) {
			theSource = source;
			theArg3 = arg3;
		}

		@Override
		public boolean test(T arg1, U arg2) {
			return theSource.test(arg1, arg2, theArg3);
		}

		TriPredicate<T, U, V> getSource() {
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
			else if (!(obj instanceof TriPredicateCurry3))
				return false;
			TriPredicateCurry3<?, ?, ?> other = (TriPredicateCurry3<?, ?, ?>) obj;
			return getSource().equals(other.getSource()) && theArg3.equals(other.getArg3());
		}

		@Override
		public String toString() {
			return theSource + ".curry3(" + theArg3 + ")";
		}
	}

	/**
	 * Implements {@link TriPredicate#printable(TriPredicate, String, Object)}
	 * 
	 * @param <A1> The first argument type
	 * @param <A2> The second argument type
	 * @param <A3> The third argument type
	 */
	class PrintableTriPredicate<A1, A2, A3> extends FunctionUtils.PrintableLambda<TriPredicate<? super A1, ? super A2, ? super A3>>
		implements TriPredicate<A1, A2, A3> {
		public PrintableTriPredicate(TriPredicate<? super A1, ? super A2, ? super A3> lambda, Supplier<String> print, Object identifier) {
			super(lambda, print, identifier);
		}

		@Override
		public boolean isTrivial() {
			return false;
		}

		@Override
		public boolean test(A1 arg1, A2 arg2, A3 arg3) {
			return getLambda().test(arg1, arg2, arg3);
		}
	}
}
