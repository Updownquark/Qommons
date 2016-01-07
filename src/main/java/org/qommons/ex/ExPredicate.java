package org.qommons.ex;

import java.util.function.Predicate;

public interface ExPredicate<T, E extends Throwable> {
	boolean test(T value) throws E;

	default Predicate<T> unsafe() {
		return value -> {
			try {
				return ExPredicate.this.test(value);
			} catch (Throwable e) {
				throw new CheckedExceptionWrapper(e);
			}
		};
	}

	static <T, E extends Throwable> ExPredicate<T, E> wrap(Predicate<T> p) {
		return value -> p.test(value);
	}
}
