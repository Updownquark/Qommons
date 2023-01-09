package org.qommons;

import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.qommons.ex.ExFunction;

/** A bunch of utilities to easily make functions that are prettier and more useful than ordinary lambdas */
public class LambdaUtils {
	/** An identifier placeholder for <code>null</code> */
	public static final Object NULL_PLACEHOLDER = new Object() {
		@Override
		public String toString() {
			return "null";
		}
	};

	/** The {@link LambdaUtils#getIdentifier(Object) identifier} representing the identity function */
	public static final Object IDENTITY = new Object() {
		@Override
		public String toString() {
			return "IDENTITY";
		}
	};

	/** A {@link Comparator} for any type C that extends Comparable<C> */
	public static final Comparator<Comparable<?>> COMPARABLE_COMPARE = LambdaUtils.<Comparable<?>> printableComparator(//
		(v1, v2) -> ((Comparable<Object>) v1).compareTo(v2), //
		() -> "Comparable::compareTo", "Comparable::compareTo");

	private interface Identity {}

	/**
	 * @param o The lambda to check
	 * @return Whether the given lambda has been configured to print
	 */
	public static boolean isPrintable(Object o) {
		return o instanceof PrintableLambda;
	}

	/**
	 * @param o The lambda to get
	 * @return The identifier associated with the given lambda, if any
	 */
	public static Object getIdentifier(Object o) {
		if (o instanceof Identity)
			return IDENTITY;
		Object id = o instanceof PrintableLambda ? ((PrintableLambda<?>) o).identifier : null;
		return id == NULL_PLACEHOLDER ? null : id;
	}

	/**
	 * @param <T> The type for the function
	 * @return A function that returns its argument
	 */
	public static <T> Function<T, T> identity() {
		return (Function<T, T>) IdentityFunction.INSTANCE;
	}

	/**
	 * @param run The runnable to execute
	 * @param print The toString() value for the runnable
	 * @param identifier The identifier for the runnable's equality
	 * @return The printable runnable
	 */
	public static Runnable printableRunnable(Runnable run, String print, Object identifier) {
		return printableRunnable(run, print != null ? new ConstantSupply(print) : () -> String.valueOf(run), identifier);
	}

	/**
	 * @param run The runnable to execute
	 * @param print The toString() implementation for the runnable
	 * @param identifier The identifier for the runnable's equality
	 * @return The printable runnable
	 */
	public static Runnable printableRunnable(Runnable run, Supplier<String> print, Object identifier) {
		return new PrintableRunnable(run, print != null ? print : () -> String.valueOf(run), identifier);
	}

	/**
	 * @param <T> The type of value to accept
	 * @param consumer The consumer doing the action
	 * @param print The printed consumer representation
	 * @param identifier An identifier for the consumer
	 * @return The printable consumer
	 */
	public static <T> Consumer<T> printableConsumer(Consumer<T> consumer, String print, Object identifier) {
		return printableConsumer(consumer, print != null ? new ConstantSupply(print) : () -> String.valueOf(consumer), identifier);
	}

	/**
	 * @param <T> The type of value to accept
	 * @param consumer The consumer doing the action
	 * @param print The printed consumer representation
	 * @param identifier An identifier for the consumer
	 * @return The printable consumer
	 */
	public static <T> Consumer<T> printableConsumer(Consumer<T> consumer, Supplier<String> print, Object identifier) {
		return new PrintableConsumer<>(consumer, print != null ? print : () -> String.valueOf(consumer), identifier);
	}

	/**
	 * @param <T> The type of value to test
	 * @param pred The predicate doing the testing
	 * @param print The printed predicate representation
	 * @param identifier An identifier for the predicate
	 * @return The printable predicate
	 */
	public static <T> Predicate<T> printablePred(Predicate<T> pred, String print, Object identifier) {
		if (pred == null)
			return null;
		return printablePred(pred, () -> print, identifier);
	}

	/**
	 * @param <T> The type of value to test
	 * @param pred The predicate doing the testing
	 * @param print The printed predicate representation
	 * @param identifier An identifier for the predicate
	 * @return The printable predicate
	 */
	public static <T> Predicate<T> printablePred(Predicate<T> pred, Supplier<String> print, Object identifier) {
		if (pred == null)
			return null;
		return new PrintablePredicate<>(pred, print, identifier);
	}

	/**
	 * @param <T> The argument type of the function
	 * @param <X> The result type of the function
	 * @param value The value to return from the function
	 * @param print The printed function representation
	 * @param identifier The identifier for the function
	 * @return A function that always returns the given value
	 */
	public static <T, X> Function<T, X> constantFn(X value, String print, Object identifier) {
		return constantFn(value, print != null ? new ConstantSupply(print) : () -> String.valueOf(value), identifier);
	}

	/**
	 * @param <T> The argument type of the function
	 * @param <X> The result type of the function
	 * @param value The value to return from the function
	 * @param print The printed function representation
	 * @param identifier The identifier for the function
	 * @return A function that always returns the given value
	 */
	public static <T, X> Function<T, X> constantFn(X value, Supplier<String> print, Object identifier) {
		return new PrintableFunction<>(t -> value, print != null ? print : () -> String.valueOf(value), identifier);
	}

	/**
	 * @param <T> The argument type of the function
	 * @param <X> The result type of the function
	 * @param <E> The exception type that might have been thrown
	 * @param value The value to return from the function
	 * @param print The printed function representation
	 * @param identifier The identifier for the function
	 * @return A function that always returns the given value
	 */
	public static <T, X, E extends Throwable> ExFunction<T, X, E> constantExFn(X value, String print, Object identifier) {
		return constantExFn(value, print != null ? new ConstantSupply(print) : () -> String.valueOf(value), identifier);
	}

	/**
	 * @param <T> The argument type of the function
	 * @param <X> The result type of the function
	 * @param <E> The exception type that might have been thrown
	 * @param value The value to return from the function
	 * @param print The printed function representation
	 * @param identifier The identifier for the function
	 * @return A function that always returns the given value
	 */
	public static <T, X, E extends Throwable> ExFunction<T, X, E> constantExFn(X value, Supplier<String> print, Object identifier) {
		return new PrintableExFunction<>(t -> value, print != null ? print : () -> String.valueOf(value), identifier);
	}

	/**
	 * @param <T> The first argument type of the function
	 * @param <V> The second argument type of the function
	 * @param <X> The result type of the function
	 * @param value The value to return from the function
	 * @param print The printed function representation
	 * @param identifier The identifier for the function
	 * @return A function that always returns the given value
	 */
	public static <T, V, X> BiFunction<T, V, X> constantBiFn(X value, String print, Object identifier) {
		return new PrintableBiFunction<>((t, v) -> value,
			print != null ? new ConstantSupply(print) : () -> String.valueOf(value), identifier);
	}

	/**
	 * @param <T> The type of the supplier
	 * @param supplier Supplier for the value to supply
	 * @param print The printed supplier representation
	 * @param identifier The identifier for the supplier
	 * @return A supplier that always returns the given value
	 */
	public static <T> Supplier<T> printableSupplier(Supplier<T> supplier, Supplier<String> print, Object identifier) {
		return new PrintableSupplier<>(supplier, print != null ? print : supplier::toString, identifier);
	}

	/**
	 * @param <T> The type of the supplier
	 * @param value The value to supply
	 * @param print The printed supplier representation
	 * @param identifier The identifier for the supplier
	 * @return A supplier that always returns the given value
	 */
	public static <T> Supplier<T> constantSupplier(T value, String print, Object identifier) {
		return constantSupplier(value, print != null ? new ConstantSupply(print) : () -> String.valueOf(value), identifier);
	}

	/**
	 * @param <T> The type of the supplier
	 * @param value The value to supply
	 * @param print The printed supplier representation
	 * @param identifier The identifier for the supplier
	 * @return A supplier that always returns the given value
	 */
	public static <T> Supplier<T> constantSupplier(T value, Supplier<String> print, Object identifier) {
		return new PrintableSupplier<>(() -> value, print != null ? print : () -> String.valueOf(value), identifier);
	}

	/**
	 * @param <T> The argument type of the function
	 * @param <X> The return type of the function
	 * @param fn The function
	 * @param print The printed representation of the function
	 * @param identifier The identifier for the function
	 * @return The printable function
	 */
	public static <T, X> Function<T, X> printableFn(Function<T, X> fn, String print, Object identifier) {
		if (fn == null)
			return null;
		return new PrintableFunction<>(fn, new ConstantSupply(print), identifier);
	}

	/**
	 * @param <T> The argument type of the function
	 * @param <X> The return type of the function
	 * @param fn The function
	 * @param print The printed representation of the function
	 * @return The printable function
	 */
	public static <T, X> Function<T, X> printableFn(Function<T, X> fn, Supplier<String> print) {
		if (fn == null)
			return null;
		return new PrintableFunction<>(fn, print);
	}

	/**
	 * @param <T> The argument type of the function
	 * @param <X> The return type of the function
	 * @param fn The function
	 * @param print The printed representation of the function
	 * @param identifier The identifier for the function
	 * @return The printable function
	 */
	public static <T, X> Function<T, X> printableFn(Function<T, X> fn, Supplier<String> print, Object identifier) {
		if (fn == null)
			return null;
		return new PrintableFunction<>(fn, print, identifier);
	}

	/**
	 * @param <T> The first argument type of the function
	 * @param <U> The second argument type of the function
	 * @param <X> The return type of the function
	 * @param fn The function
	 * @param print The printed representation of the function
	 * @param identifier The identifier for the function
	 * @return The printable function
	 */
	public static <T, U, X> BiFunction<T, U, X> printableBiFn(BiFunction<T, U, X> fn, String print, Object identifier) {
		if (fn == null)
			return null;
		return printableBiFn(fn, () -> print, identifier);
	}

	/**
	 * @param <T> The first argument type of the function
	 * @param <U> The second argument type of the function
	 * @param <X> The return type of the function
	 * @param fn The function
	 * @param print The printed representation of the function
	 * @param identifier The identifier for the function
	 * @return The printable function
	 */
	public static <T, U, X> BiFunction<T, U, X> printableBiFn(BiFunction<T, U, X> fn, Supplier<String> print, Object identifier) {
		if (fn == null)
			return null;
		return new PrintableBiFunction<>(fn, print, identifier);
	}

	/**
	 * @param <T> The first argument type of the consumer
	 * @param <U> The second argument type of the consumer
	 * @param fn The consumer
	 * @param print The printed representation of the consumer
	 * @param identifier The identifier for the consumer
	 * @return The printable consumer
	 */
	public static <T, U> BiConsumer<T, U> printableBiConsumer(BiConsumer<T, U> fn, Supplier<String> print, Object identifier) {
		if (fn == null)
			return null;
		return new PrintableBiConsumer<>(fn, print, identifier);
	}

	/**
	 * Makes a bi-function out of a function, ignoring the second argument
	 * 
	 * @param <T> The type of the first parameter (the one passed to the input function)
	 * @param <U> The type of the second parameter (ignored)
	 * @param <X> The type of the output
	 * @param map The function to back the bi-function
	 * @return The binary function
	 */
	public static <T, U, X> BiFunction<T, U, X> toBiFunction1(Function<? super T, ? extends X> map) {
		if (map == null)
			return null;
		return new MappedBiFunction1<>(map);
	}

	/**
	 * Makes a bi-function out of a function, ignoring the first argument
	 * 
	 * @param <T> The type of the first parameter (ignored)
	 * @param <U> The type of the second parameter (the one passed to the input function)
	 * @param <X> The type of the output
	 * @param map The function to back the bi-function
	 * @return The binary function
	 */
	public static <T, U, X> BiFunction<T, U, X> toBiFunction2(Function<? super U, ? extends X> map) {
		if (map == null)
			return null;
		return new MappedBiFunction2<>(map);
	}

	/**
	 * Makes a tri-function out of a function, ignoring the second and third arguments
	 * 
	 * @param <T> The type of the first parameter (the one passed to the input function)
	 * @param <U> The type of the second parameter (ignored)
	 * @param <V> The type of the third parameter (ignored)
	 * @param <X> The type of the output
	 * @param map The function to back the tri-function
	 * @return The ternary function
	 */
	public static <T, U, V, X> TriFunction<T, U, V, X> toTriFunction1(Function<? super T, ? extends X> map) {
		if (map == null)
			return null;
		return new MappedTriFunction1<>(map);
	}

	/**
	 * Makes a tri-function out of a function, ignoring the first and third arguments
	 * 
	 * @param <T> The type of the first parameter (ignored)
	 * @param <U> The type of the second parameter (the one passed to the input function)
	 * @param <V> The type of the third parameter (ignored)
	 * @param <X> The type of the output
	 * @param map The function to back the tri-function
	 * @return The ternary function
	 */
	public static <T, U, V, X> TriFunction<T, U, V, X> toTriFunction2(Function<? super U, ? extends X> map) {
		if (map == null)
			return null;
		return new MappedTriFunction2<>(map);
	}

	/**
	 * Makes a tri-function out of a function, ignoring the first and second arguments
	 * 
	 * @param <T> The type of the first parameter (ignored)
	 * @param <U> The type of the second parameter (ignored)
	 * @param <V> The type of the third parameter (the one passed to the input function)
	 * @param <X> The type of the output
	 * @param map The function to back the tri-function
	 * @return The ternary function
	 */
	public static <T, U, V, X> TriFunction<T, U, V, X> toTriFunction3(Function<? super V, ? extends X> map) {
		if (map == null)
			return null;
		return new MappedTriFunction3<>(map);
	}

	/**
	 * Makes a tri-function out of a bi-function, ignoring the last argument
	 * 
	 * @param <T> The type of the first parameter (the first one passed to the input function)
	 * @param <U> The type of the second parameter (the second one passed to the input function)
	 * @param <V> The type of the third parameter (ignored)
	 * @param <X> The type of the output
	 * @param map The bi-function to back the tri-function
	 * @return The ternary function
	 */
	public static <T, U, V, X> TriFunction<T, U, V, X> toTriFunction1And2(BiFunction<? super T, ? super U, ? extends X> map) {
		if (map == null)
			return null;
		return new BiMappedTriFunction12<>(map);
	}

	/**
	 * Makes a tri-function out of a bi-function, ignoring the second argument
	 * 
	 * @param <T> The type of the first parameter (the first one passed to the input function)
	 * @param <U> The type of the second parameter (ignored)
	 * @param <V> The type of the third parameter (the second one passed to the input function)
	 * @param <X> The type of the output
	 * @param map The bi-function to back the tri-function
	 * @return The ternary function
	 */
	public static <T, U, V, X> TriFunction<T, U, V, X> toTriFunction1And3(BiFunction<? super T, ? super V, ? extends X> map) {
		if (map == null)
			return null;
		return new BiMappedTriFunction13<>(map);
	}

	/**
	 * Makes a tri-function out of a bi-function, ignoring the first argument
	 * 
	 * @param <T> The type of the first parameter (ignored)
	 * @param <U> The type of the second parameter (the first one passed to the input function)
	 * @param <V> The type of the third parameter (the second one passed to the input function)
	 * @param <X> The type of the output
	 * @param map The bi-function to back the tri-function
	 * @return The ternary function
	 */
	public static <T, U, V, X> TriFunction<T, U, V, X> toTriFunction2And3(BiFunction<? super U, ? super V, ? extends X> map) {
		if (map == null)
			return null;
		return new BiMappedTriFunction23<>(map);
	}

	/**
	 * @param <T> The first argument type of the predicate
	 * @param <U> The second argument type of the predicate
	 * @param test The predicate
	 * @param print The printed representation of the predicate
	 * @return The printable predicate
	 */
	public static <T, U> BiPredicate<T, U> printableBiPredicate(BiPredicate<T, U> test, Supplier<String> print) {
		return printableBiPredicate(test, print, null);
	}

	/**
	 * @param <T> The first argument type of the predicate
	 * @param <U> The second argument type of the predicate
	 * @param test The predicate
	 * @param print The printed representation of the predicate
	 * @param identifier The identifier for the predicate
	 * @return The printable predicate
	 */
	public static <T, U> BiPredicate<T, U> printableBiPredicate(BiPredicate<T, U> test, Supplier<String> print, Object identifier) {
		if (test == null)
			return null;
		return new PrintableBiPredicate<>(test, print, identifier);
	}

	/**
	 * @param <T> The first argument type of the function
	 * @param <U> The second argument type of the function
	 * @param <V> The third argument type of the function
	 * @param <X> The return type of the function
	 * @param fn The function
	 * @param print The printed representation of the function
	 * @param identifier The identifier for the function
	 * @return The printable function
	 */
	public static <T, U, V, X> TriFunction<T, U, V, X> printableTriFn(TriFunction<T, U, V, X> fn, String print, Object identifier) {
		return printableTriFn(fn, () -> print, identifier);
	}

	/**
	 * @param <T> The first argument type of the function
	 * @param <U> The second argument type of the function
	 * @param <V> The third argument type of the function
	 * @param <X> The return type of the function
	 * @param fn The function
	 * @param print The printed representation of the function
	 * @param identifier The identifier for the function
	 * @return The printable function
	 */
	public static <T, U, V, X> TriFunction<T, U, V, X> printableTriFn(TriFunction<T, U, V, X> fn, Supplier<String> print,
		Object identifier) {
		if (fn == null)
			return null;
		return new PrintableTriFunction<>(fn, print, identifier);
	}

	/**
	 * @param <T> The type to compare
	 * @param compare The comparable to wrap
	 * @param print The toString for the comparable
	 * @return The printable comparable
	 */
	public static <T> Comparable<T> printableComparable(Comparable<T> compare, Supplier<String> print) {
		if (compare == null)
			return null;
		return new PrintableComparable<>(compare, print);
	}

	/**
	 * @param <T> The type to compare
	 * @param compare The comparator to wrap
	 * @param print The toString for the comparator
	 * @return The printable comparator
	 */
	public static <T> Comparator<T> printableComparator(Comparator<T> compare, Supplier<String> print) {
		return printableComparator(compare, print, null);
	}

	/**
	 * @param <T> The type to compare
	 * @param compare The comparator to wrap
	 * @param print The toString for the comparator
	 * @param identifier The identifier to use for the comparator
	 * @return The printable comparator
	 */
	public static <T> Comparator<T> printableComparator(Comparator<T> compare, Supplier<String> print, Object identifier) {
		if (compare == null)
			return null;
		return new PrintableComparator<>(compare, print, identifier);
	}

	static class IdentityFunction<T> implements Function<T, T>, Identity {
		static final IdentityFunction<?> INSTANCE = new IdentityFunction<>();

		@Override
		public T apply(T t) {
			return t;
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof IdentityFunction;
		}

		@Override
		public String toString() {
			return "identity";
		}
	}

	/** This class is only used for the {@link PrintableLambda#thePrint} field and doesn't extend PrintableLambda to avoid circularity */
	private static class ConstantSupply implements Supplier<String> {
		private final String theString;

		ConstantSupply(String string) {
			theString = string;
		}

		@Override
		public String get() {
			return theString;
		}

		@Override
		public int hashCode() {
			return theString.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ConstantSupply && theString.equals(((ConstantSupply) obj).theString);
		}

		@Override
		public String toString() {
			return theString;
		}
	}

	private static class PrintableLambda<L> {
		private final L theLambda;
		private final Supplier<String> thePrint;
		private final Object identifier;
		private final int hashCode;

		PrintableLambda(L lambda, String print, Object identifier) {
			this(lambda, print != null ? new ConstantSupply(print) : lambda::toString, identifier);
		}

		PrintableLambda(L lambda, Supplier<String> print) {
			this(lambda, print, null);
		}

		private PrintableLambda(L lambda, Supplier<String> print, Object identifier) {
			theLambda = lambda;
			thePrint = print;
			this.identifier = identifier;
			if (identifier != null) {
				hashCode = identifier.hashCode();
			} else {
				hashCode = 0;
			}
		}

		protected L getLambda() {
			return theLambda;
		}

		@Override
		public int hashCode() {
			if (identifier != null) {
				return hashCode;
			} else {
				return theLambda.hashCode();
			}
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			} else if (obj == null || obj.getClass() != getClass()) {
				return false;
			} else if (identifier != null) {
				if (hashCode != obj.hashCode()) {
					return false;
				}
				return identifier.equals(((PrintableLambda<?>) obj).identifier);
			} else {
				return ((PrintableLambda<?>) obj).theLambda.equals(theLambda);
			}
		}

		@Override
		public String toString() {
			return thePrint.get();
		}
	}

	static class PrintableRunnable extends PrintableLambda<Runnable> implements Runnable {
		PrintableRunnable(Runnable run, Supplier<String> print, Object identifier) {
			super(run, print, identifier);
		}

		@Override
		public void run() {
			getLambda().run();
		}
	}

	static class PrintableConsumer<T> extends PrintableLambda<Consumer<T>> implements Consumer<T> {
		PrintableConsumer(Consumer<T> lambda, Supplier<String> print, Object identifier) {
			super(lambda, print, identifier);
		}

		PrintableConsumer(Consumer<T> lambda, Supplier<String> print) {
			super(lambda, print);
		}

		@Override
		public void accept(T t) {
			getLambda().accept(t);
		}
	}

	static class PrintableSupplier<T> extends PrintableLambda<Supplier<T>> implements Supplier<T> {
		PrintableSupplier(Supplier<T> lambda, Supplier<String> print, Object identifier) {
			super(lambda, print, identifier);
		}

		PrintableSupplier(Supplier<T> lambda, Supplier<String> print) {
			super(lambda, print);
		}

		@Override
		public T get() {
			return getLambda().get();
		}
	}

	static class PrintablePredicate<T> extends PrintableLambda<Predicate<T>> implements Predicate<T> {
		PrintablePredicate(Predicate<T> pred, Supplier<String> print, Object identifier) {
			super(pred, print, identifier);
		}

		PrintablePredicate(Predicate<T> pred, Supplier<String> print) {
			super(pred, print);
		}

		@Override
		public boolean test(T t) {
			return getLambda().test(t);
		}
	}

	static class PrintableBiPredicate<T, U> extends PrintableLambda<BiPredicate<T, U>> implements BiPredicate<T, U> {
		PrintableBiPredicate(BiPredicate<T, U> function, String print, Object identifier) {
			super(function, print, identifier);
		}

		PrintableBiPredicate(BiPredicate<T, U> function, Supplier<String> print, Object identifier) {
			super(function, print, identifier);
		}

		PrintableBiPredicate(BiPredicate<T, U> function, Supplier<String> print) {
			super(function, print);
		}

		@Override
		public boolean test(T t, U u) {
			return getLambda().test(t, u);
		}
	}

	static class PrintableFunction<T, X> extends PrintableLambda<Function<T, X>> implements Function<T, X> {
		PrintableFunction(Function<T, X> function, Supplier<String> print, Object identifier) {
			super(function, print, identifier);
		}

		PrintableFunction(Function<T, X> function, Supplier<String> print) {
			super(function, print);
		}

		@Override
		public X apply(T t) {
			return getLambda().apply(t);
		}
	}

	static class PrintableExFunction<T, X, E extends Throwable> extends PrintableLambda<ExFunction<T, X, E>>
		implements ExFunction<T, X, E> {
		PrintableExFunction(ExFunction<T, X, E> function, Supplier<String> print, Object identifier) {
			super(function, print, identifier);
		}

		PrintableExFunction(ExFunction<T, X, E> function, Supplier<String> print) {
			super(function, print);
		}

		@Override
		public X apply(T t) throws E {
			return getLambda().apply(t);
		}
	}

	static class PrintableBiFunction<T, U, X> extends PrintableLambda<BiFunction<T, U, X>> implements BiFunction<T, U, X> {
		PrintableBiFunction(BiFunction<T, U, X> function, String print, Object identifier) {
			super(function, print, identifier);
		}

		PrintableBiFunction(BiFunction<T, U, X> function, Supplier<String> print, Object identifier) {
			super(function, print, identifier);
		}

		PrintableBiFunction(BiFunction<T, U, X> function, Supplier<String> print) {
			super(function, print);
		}

		@Override
		public X apply(T t, U u) {
			return getLambda().apply(t, u);
		}
	}

	static class PrintableBiConsumer<T, U> extends PrintableLambda<BiConsumer<T, U>> implements BiConsumer<T, U> {
		PrintableBiConsumer(BiConsumer<T, U> function, Supplier<String> print, Object identifier) {
			super(function, print, identifier);
		}

		@Override
		public void accept(T t, U u) {
			getLambda().accept(t, u);
		}
	}

	static class MappedBiFunction1<T, U, X> implements BiFunction<T, U, X> {
		private final Function<? super T, ? extends X> theMap;

		MappedBiFunction1(Function<? super T, ? extends X> map) {
			if (map == null)
				throw new NullPointerException();
			theMap = map;
		}

		@Override
		public X apply(T t, U u) {
			return theMap.apply(t);
		}

		@Override
		public int hashCode() {
			return theMap.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof MappedBiFunction1))
				return false;
			return theMap.equals(((MappedBiFunction1<?, ?, ?>) obj).theMap);
		}

		@Override
		public String toString() {
			return theMap.toString();
		}
	}

	static class MappedBiFunction2<T, U, X> implements BiFunction<T, U, X> {
		private final Function<? super U, ? extends X> theMap;

		MappedBiFunction2(Function<? super U, ? extends X> map) {
			theMap = map;
		}

		@Override
		public X apply(T t, U u) {
			return theMap.apply(u);
		}

		@Override
		public int hashCode() {
			return theMap.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof MappedBiFunction2))
				return false;
			return theMap.equals(((MappedBiFunction1<?, ?, ?>) obj).theMap);
		}

		@Override
		public String toString() {
			return theMap.toString();
		}
	}

	static class MappedTriFunction1<T, U, V, X> implements TriFunction<T, U, V, X> {
		private final Function<? super T, ? extends X> theMap;

		MappedTriFunction1(Function<? super T, ? extends X> map) {
			theMap = map;
		}

		@Override
		public X apply(T t, U u, V v) {
			return theMap.apply(t);
		}

		@Override
		public int hashCode() {
			return theMap.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof MappedTriFunction1))
				return false;
			return theMap.equals(((MappedTriFunction1<?, ?, ?, ?>) obj).theMap);
		}

		@Override
		public String toString() {
			return theMap.toString();
		}
	}

	static class MappedTriFunction2<T, U, V, X> implements TriFunction<T, U, V, X> {
		private final Function<? super U, ? extends X> theMap;

		MappedTriFunction2(Function<? super U, ? extends X> map) {
			theMap = map;
		}

		@Override
		public X apply(T t, U u, V v) {
			return theMap.apply(u);
		}

		@Override
		public int hashCode() {
			return theMap.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof MappedTriFunction2))
				return false;
			return theMap.equals(((MappedTriFunction2<?, ?, ?, ?>) obj).theMap);
		}

		@Override
		public String toString() {
			return theMap.toString();
		}
	}

	static class MappedTriFunction3<T, U, V, X> implements TriFunction<T, U, V, X> {
		private final Function<? super V, ? extends X> theMap;

		MappedTriFunction3(Function<? super V, ? extends X> map) {
			theMap = map;
		}

		@Override
		public X apply(T t, U u, V v) {
			return theMap.apply(v);
		}

		@Override
		public int hashCode() {
			return theMap.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof MappedTriFunction3))
				return false;
			return theMap.equals(((MappedTriFunction3<?, ?, ?, ?>) obj).theMap);
		}

		@Override
		public String toString() {
			return theMap.toString();
		}
	}

	static class BiMappedTriFunction12<T, U, V, X> implements TriFunction<T, U, V, X> {
		private final BiFunction<? super T, ? super U, ? extends X> theMap;

		BiMappedTriFunction12(BiFunction<? super T, ? super U, ? extends X> map) {
			theMap = map;
		}

		@Override
		public X apply(T arg1, U arg2, V arg3) {
			return theMap.apply(arg1, arg2);
		}

		@Override
		public int hashCode() {
			return theMap.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof BiMappedTriFunction12))
				return false;
			return theMap.equals(((BiMappedTriFunction12<?, ?, ?, ?>) obj).theMap);
		}

		@Override
		public String toString() {
			return theMap.toString();
		}
	}

	static class BiMappedTriFunction13<T, U, V, X> implements TriFunction<T, U, V, X> {
		private final BiFunction<? super T, ? super V, ? extends X> theMap;

		BiMappedTriFunction13(BiFunction<? super T, ? super V, ? extends X> map) {
			theMap = map;
		}

		@Override
		public X apply(T arg1, U arg2, V arg3) {
			return theMap.apply(arg1, arg3);
		}

		@Override
		public int hashCode() {
			return theMap.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof BiMappedTriFunction13))
				return false;
			return theMap.equals(((BiMappedTriFunction13<?, ?, ?, ?>) obj).theMap);
		}

		@Override
		public String toString() {
			return theMap.toString();
		}
	}

	static class BiMappedTriFunction23<T, U, V, X> implements TriFunction<T, U, V, X> {
		private final BiFunction<? super U, ? super V, ? extends X> theMap;

		BiMappedTriFunction23(BiFunction<? super U, ? super V, ? extends X> map) {
			theMap = map;
		}

		@Override
		public X apply(T arg1, U arg2, V arg3) {
			return theMap.apply(arg2, arg3);
		}

		@Override
		public int hashCode() {
			return theMap.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof BiMappedTriFunction23))
				return false;
			return theMap.equals(((BiMappedTriFunction23<?, ?, ?, ?>) obj).theMap);
		}

		@Override
		public String toString() {
			return theMap.toString();
		}
	}

	static class PrintableTriFunction<T, U, V, X> extends PrintableLambda<TriFunction<T, U, V, X>> implements TriFunction<T, U, V, X> {
		PrintableTriFunction(TriFunction<T, U, V, X> function, String print, Object identifier) {
			super(function, print, identifier);
		}

		PrintableTriFunction(TriFunction<T, U, V, X> function, Supplier<String> print, Object identifier) {
			super(function, print, identifier);
		}

		PrintableTriFunction(TriFunction<T, U, V, X> function, Supplier<String> print) {
			super(function, print);
		}

		@Override
		public X apply(T t, U u, V v) {
			return getLambda().apply(t, u, v);
		}
	}

	static class PrintableComparable<T> extends PrintableLambda<Comparable<T>> implements Comparable<T> {
		public PrintableComparable(Comparable<T> lambda, Supplier<String> print) {
			super(lambda, print);
		}

		@Override
		public int compareTo(T o1) {
			return getLambda().compareTo(o1);
		}
	}

	static class PrintableComparator<T> extends PrintableLambda<Comparator<T>> implements Comparator<T> {
		public PrintableComparator(Comparator<T> lambda, Supplier<String> print, Object identifier) {
			super(lambda, print, identifier);
		}

		@Override
		public int compare(T o1, T o2) {
			return getLambda().compare(o1, o2);
		}
	}
}
