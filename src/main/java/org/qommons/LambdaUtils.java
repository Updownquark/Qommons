package org.qommons;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** A bunch of utilities to easily make functions that are prettier and more useful than ordinary lambdas */
public class LambdaUtils {
	private static final Map<PrintableLambda<?>, PrintableLambda<?>> IDENTIFIED_LAMBDAS = new ConcurrentHashMap<>();
	public static final Object NULL_PLACEHOLDER = new Object() {
		@Override
		public String toString() {
			return "null";
		}
	};

	public static boolean isPrintable(Object o) {
		return o instanceof PrintableLambda;
	}

	public static Object getIdentifier(Object o) {
		Object id = o instanceof PrintableLambda ? ((PrintableLambda<?>) o).identifier : null;
		return id == NULL_PLACEHOLDER ? null : id;
	}

	public static <T> Function<T, T> identity() {
		return (Function<T, T>) IdentityFunction.INSTANCE;
	}

	public static <T> Predicate<T> printablePred(Predicate<T> pred, String print, Object identifier) {
		PrintablePredicate<T> p = new PrintablePredicate<>(pred, print, identifier);
		if (identifier != null) {
			p = (PrintablePredicate<T>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
	}

	public static <T, X> Function<T, X> constantFn(X value, String print, Object identifier) {
		return constantFn(value, print != null ? new ConstantSupply(print) : () -> String.valueOf(value), identifier);
	}

	public static <T, X> Function<T, X> constantFn(X value, Supplier<String> print, Object identifier) {
		PrintableFunction<T, X> p = new PrintableFunction<>(t -> value, print != null ? print : () -> String.valueOf(value), identifier);
		if (identifier != null) {
			p = (PrintableFunction<T, X>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
	}

	public static <T, V, X> BiFunction<T, V, X> constantBiFn(X value, String print, Object identifier) {
		PrintableBiFunction<T, V, X> p = new PrintableBiFunction<>((t, v) -> value,
			print != null ? new ConstantSupply(print) : () -> String.valueOf(value), identifier);
		if (identifier != null) {
			p = (PrintableBiFunction<T, V, X>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
	}

	public static <T> Supplier<T> constantSupplier(T value, String print, Object identifier) {
		return constantSupplier(value, print != null ? new ConstantSupply(print) : () -> String.valueOf(value), identifier);
	}

	public static <T> Supplier<T> constantSupplier(T value, Supplier<String> print, Object identifier) {
		PrintableSupplier<T> p = new PrintableSupplier<>(() -> value, print != null ? print : () -> String.valueOf(value), identifier);
		if (identifier != null) {
			p = (PrintableSupplier<T>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
	}

	public static <T, X> Function<T, X> printableFn(Function<T, X> fn, String print, Object identifier) {
		PrintableFunction<T, X> p = new PrintableFunction<>(fn, new ConstantSupply(print), identifier);
		if (identifier != null) {
			p = (PrintableFunction<T, X>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
	}

	public static <T, X> Function<T, X> printableFn(Function<T, X> fn, Supplier<String> print) {
		return new PrintableFunction<>(fn, print);
	}

	public static <T, U, X> BiFunction<T, U, X> printableBiFn(BiFunction<T, U, X> fn, Supplier<String> print) {
		return new PrintableBiFunction<>(fn, print);
	}

	public static <T, U, X> BiFunction<T, U, X> printableBiFn(BiFunction<T, U, X> fn, String print, Object identifier) {
		PrintableBiFunction<T, U, X> p = new PrintableBiFunction<>(fn, print, identifier);
		if (identifier != null) {
			p = (PrintableBiFunction<T, U, X>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
	}

	public static <T, U, V, X> TriFunction<T, U, V, X> printableTriFn(TriFunction<T, U, V, X> fn, String print, Object identifier) {
		PrintableTriFunction<T, U, V, X> p = new PrintableTriFunction<>(fn, print, identifier);
		if (identifier != null) {
			p = (PrintableTriFunction<T, U, V, X>) IDENTIFIED_LAMBDAS.computeIfAbsent(p, x -> x);
		}
		return p;
	}

	static class IdentityFunction<T> implements Function<T, T> {
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
}
