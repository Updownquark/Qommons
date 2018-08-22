package org.qommons.collect;

import org.qommons.Transactable;
import org.qommons.Transaction;

/** A strategy for collection thread safety. Some implementations of this class may not be completely thread-safe for performance. */
public interface CollectionLockingStrategy extends Transactable {
	@FunctionalInterface
	interface OptimisticOperation<T> {
		T apply(T init, OptimisticContext ctx);
	}

	@FunctionalInterface
	interface OptimisticIntOperation {
		int apply(int init, OptimisticContext ctx);
	}

	@Override
	default Transaction lock(boolean write, Object cause) {
		return lock(write, write, cause);
	}

	Transaction lock(boolean write, boolean structural, Object cause);

	long getStamp(boolean structural);

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
	 * @param allowUpdate Whether to allow updates within the operation
	 * @return The result of the operation
	 */
	<T> T doOptimistically(T init, OptimisticOperation<T> operation, boolean allowUpdate);

	int doOptimistically(int init, OptimisticIntOperation operation, boolean allowUpdate);
}
