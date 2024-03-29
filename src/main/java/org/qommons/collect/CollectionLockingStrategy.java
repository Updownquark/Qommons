package org.qommons.collect;

import org.qommons.Stamped;
import org.qommons.Transactable;

/** A strategy for collection thread safety. Some implementations of this class may not be completely thread-safe for performance. */
public interface CollectionLockingStrategy extends Transactable, Stamped {
	/** Increments the {@link #getStamp() stamp} */
	void modified();

	/**
	 * A read-only operation that can be done optimistically, without obtaining a lock
	 * 
	 * @param <T> The type of the operation's result
	 */
	@FunctionalInterface
	interface OptimisticOperation<T> {
		T apply(T init, OptimisticContext ctx);
	}

	/** A read-only operation that can be done optimistically, without obtaining a lock */
	@FunctionalInterface
	interface OptimisticIntOperation {
		int apply(int init, OptimisticContext ctx);
	}

	/**
	 * Performs a safe, read-only operation, potentially without obtaining any locks.
	 * 
	 * The operation must have no side effects (i.e. it must not modify fields or values outside of the scope of the operation). A typical
	 * operation will:
	 * <ol>
	 * <li>Atomically copy the set of fields it needs into local variables</li>
	 * <li>Check the stamp to ensure that the copied values are consistent</li>
	 * <li>Perform one or more operations on the local variables which only affect local variables declared in the scope, checking the stamp
	 * in between operations to continuously ensure consistency and to avoid unnecessary work</li>
	 * <li>Compile and return a value that can be used outside the operation scope</li>
	 * </ol>
	 * 
	 * @param <T> The type of value produced by the operation
	 * @param init The initial value to feed to the operation
	 * @param operation The operation to perform
	 * @return The result of the operation
	 */
	<T> T doOptimistically(T init, OptimisticOperation<T> operation);

	/**
	 * Same as {@link #doOptimistically(Object, OptimisticOperation)} but for a primitive integer, since this is a common use-case
	 * 
	 * @param init The initial value to feed to the operation
	 * @param operation The operation to perform
	 * @return The result of the operation
	 */
	int doOptimistically(int init, OptimisticIntOperation operation);
}
