package org.qommons.ex;

public interface ExBiFunction<T, U, R, E extends Throwable> {
	R apply(T t, U u) throws E;
}
