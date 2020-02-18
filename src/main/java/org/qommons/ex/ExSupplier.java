package org.qommons.ex;

public interface ExSupplier<T, E extends Throwable> {
	T get() throws E;
}
