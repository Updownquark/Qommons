package org.qommons.collect;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.qommons.Stamped;
import org.qommons.Subscription;

/**
 * Basically an abstraction of {@link ListenerList}
 * 
 * @param <E> The type of values in the queue
 */
public interface ListenerQueue<E> extends PureQueue<E>, Stamped {
	/**
	 * An element in a {@link ListenerQueue}
	 * 
	 * @param <E> The type of value in the element
	 */
	public interface Element<E> extends Supplier<E>, Runnable, Subscription {
		/** @param value The new value for the element */
		void set(E value);

		/** @return Whether this element is still present in the queue */
		boolean isPresent();

		/**
		 * Removes this element from the list
		 * 
		 * @return True if the element was removed as a result of this call, false if it had been removed already
		 */
		boolean remove();

		/** Same as {@link #remove()} */
		@Override
		default void run() {
			remove();
		}

		@Override
		default void unsubscribe() {
			remove();
		}
	}

	/**
	 * @param value The value to add
	 * @return A runnable that will remove the value
	 */
	Element<E> addNew(E value);

	/**
	 * Applies a specified action to each value in this queue
	 * 
	 * @param action The action to perform on each value in this queue
	 */
	void forEach(Consumer<? super E> action);

	/**
	 * @return Whether {@link #forEach(Consumer)} is currently being called. It is implementation-dependent whether this is specific to the
	 *         current thread
	 */
	boolean isFiring();

	/**
	 * Increment's this queue's {@link #getStamp() stamp} without firing any listeners
	 * 
	 * @return The new stamp
	 */
	long incrementStamp();

	/**
	 * Clears this queue, providing each value to a consumer before it is removed
	 * 
	 * @param consumer The consumer to accept each value in this queue
	 * @return The number of items that were found in the queue
	 */
	int dumpAndClear(Consumer<E> consumer);
}
