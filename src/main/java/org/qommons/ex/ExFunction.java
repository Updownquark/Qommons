package org.qommons.ex;

import java.util.function.Function;

@FunctionalInterface
public interface ExFunction<F, T, E extends Throwable> {
	T apply(F value) throws E;

	default Function<F, T> unsafe() {
		return value -> {
			try {
				return ExFunction.this.apply(value);
			} catch(Throwable e) {
				throw new CheckedExceptionWrapper(e);
			}
		};
	}

	static <F, T, E extends Throwable> ExFunction<F, T, E> of(Function<F, T> f) {
		return value -> f.apply(value);
	}
}
