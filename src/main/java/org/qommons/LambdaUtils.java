package org.qommons;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** A bunch of utilities to easily make functions that are prettier and more useful than ordinary lambdas */
public class LambdaUtils {
	private static final Map<PrintableLambda<?>, PrintableLambda<?>> IDENTIFIED_LAMBDAS = new ConcurrentHashMap<>();
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
	 * @param <T> The type of value to test
	 * @param pred The predicate doing the testing
	 * @param print The printed predicate representation
	 * @param identifier An identifier for the predicate
	 * @return The printable predicate
	 */
	public static <T> Predicate<T> printablePred(Predicate<T> pred, String print, Object identifier) {
		PrintablePredicate<T> p = new PrintablePredicate<>(pred, print, identifier);
		if (identifier != null) {
			p = (PrintablePredicate<T>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
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
		PrintableFunction<T, X> p = new PrintableFunction<>(t -> value, print != null ? print : () -> String.valueOf(value), identifier);
		if (identifier != null) {
			p = (PrintableFunction<T, X>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
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
		PrintableBiFunction<T, V, X> p = new PrintableBiFunction<>((t, v) -> value,
			print != null ? new ConstantSupply(print) : () -> String.valueOf(value), identifier);
		if (identifier != null) {
			p = (PrintableBiFunction<T, V, X>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
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
		PrintableSupplier<T> p = new PrintableSupplier<>(() -> value, print != null ? print : () -> String.valueOf(value), identifier);
		if (identifier != null) {
			p = (PrintableSupplier<T>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
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
		PrintableFunction<T, X> p = new PrintableFunction<>(fn, new ConstantSupply(print), identifier);
		if (identifier != null) {
			p = (PrintableFunction<T, X>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
	}

	/**
	 * @param <T> The argument type of the function
	 * @param <X> The return type of the function
	 * @param fn The function
	 * @param print The printed representation of the function
	 * @return The printable function
	 */
	public static <T, X> Function<T, X> printableFn(Function<T, X> fn, Supplier<String> print) {
		return new PrintableFunction<>(fn, print);
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
		PrintableBiFunction<T, U, X> p = new PrintableBiFunction<>(fn, print, identifier);
		if (identifier != null) {
			p = (PrintableBiFunction<T, U, X>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
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
		return new MappedBiFunction2<>(map);
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
		PrintableBiPredicate<T, U> p = new PrintableBiPredicate<>(test, print, identifier);
		if (identifier != null) {
			p = (PrintableBiPredicate<T, U>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
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
		PrintableTriFunction<T, U, V, X> p = new PrintableTriFunction<>(fn, print, identifier);
		if (identifier != null) {
			p = (PrintableTriFunction<T, U, V, X>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
	}

	/**
	 * @param <T> The type to compare
	 * @param compare The comparable to wrap
	 * @param print The toString for the comparable
	 * @return The printable comparable
	 */
	public static <T> Comparable<T> printableComparable(Comparable<T> compare, Supplier<String> print) {
		return new PrintableComparable<>(compare, print);
	}

	/**
	 * @param <T> The type to compare
	 * @param compare The comparator to wrap
	 * @param print The toString for the comparator
	 * @return The printable comparator
	 */
	public static <T> Comparator<T> printableComparator(Comparator<T> compare, Supplier<String> print) {
		return new PrintableComparator<>(compare, print);
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
		PrintablePredicate(Predicate<T> pred, String print, Object identifier) {
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

	static class MappedBiFunction1<T, U, X> implements BiFunction<T, U, X> {
		private final Function<? super T, ? extends X> theMap;

		MappedBiFunction1(Function<? super T, ? extends X> map) {
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
		public PrintableComparator(Comparator<T> lambda, Supplier<String> print) {
			super(lambda, print);
		}

		@Override
		public int compare(T o1, T o2) {
			return getLambda().compare(o1, o2);
		}
	}
}
