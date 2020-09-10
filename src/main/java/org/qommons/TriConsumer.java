package org.qommons;

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
}
