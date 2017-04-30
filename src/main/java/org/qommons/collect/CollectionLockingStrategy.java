package org.qommons.collect;

import java.util.ConcurrentModificationException;
import java.util.function.Consumer;

import org.qommons.Transactable;
import org.qommons.Transaction;

/** A strategy for collection thread safety. Some implementations of this class may not be completely thread-safe for performance. */
public interface CollectionLockingStrategy extends Transactable {
	@FunctionalInterface
	interface OptimisticOperation<T> {
		T apply(T init, long stamp);
	}

	/**
	 * Performs a safe, read-only operation, potentially without obtaining any locks.
	 * 
	 * The operation must have no side effects (i.e. it must not modify fields or values outside of the scope of the operation). A typical
	 * operation will:
	 * <ol>
	 * <li>Atomically copy the set of values it needs into local variables</li>
	 * <li>Check the stamp to ensure that the copied values are consistent</li>
	 * <li>Perform one or more atomic operations on the local variables which only affect local variables declared in the scope, checking
	 * the stamp in between operations to continuously ensure consistency and to avoid unnecessary work</li>
	 * <li>Compile and return a value that can be used outside the scope</li>
	 * </ol>
	 * 
	 * @param <T> The type of value produced by the operation
	 * @param init The initial value to feed to the operation
	 * @param operation The operation to perform
	 * @return The result of the operation
	 */
	default <T> T doOptimistically(T init, OptimisticOperation<T> operation) {
		T res = init;
		long stamp = getStamp();
		if (stamp != 0) {
			res = operation.apply(res, stamp);
			if (check(stamp))
				return res;
		} // else Write lock is taken. Wait for readability.
		try (Transaction t = lock(false, null)) {
			return operation.apply(res, 1);
		}
	}

	long getStamp();

	boolean check(long stamp);

	void indexChanged(int added);

	SubLockingStrategy subLock();

	interface SubLockingStrategy extends CollectionLockingStrategy {
		int check() throws ConcurrentModificationException;

		SubLockingStrategy subLock(Consumer<Integer> indexChangeCallback);

		SubLockingStrategy siblingLock();
	}
}
