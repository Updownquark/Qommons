package org.qommons.collect;

import java.util.function.BooleanSupplier;

import org.qommons.Transactable;
import org.qommons.Transaction;

/** A strategy for collection thread safety. Some implementations of this class may not be completely thread-safe for performance. */
public interface CollectionLockingStrategy extends Transactable {
	/** The interface to ensure that the context in which an {@link OptimisticOperation} is executing is still valid */
	interface OptimisticContext extends BooleanSupplier {
		boolean check();

		@Override
		default boolean getAsBoolean() {
			return check();
		}
	}

	/**
	 * An operation to execute {@link CollectionLockingStrategy#doOptimistically(Object, OptimisticOperation, boolean) optimistically}
	 * 
	 * @param <T> The type of value the operation produces
	 */
	@FunctionalInterface
	interface OptimisticOperation<T> {
		/**
		 * @param init The initial value
		 * @param ctx The optimistic context for the operation
		 * @return The result
		 */
		T apply(T init, OptimisticContext ctx);
	}

	@Override
	default Transaction lock(boolean write, Object cause) {
		return lock(write, write, cause);
	}

	/**
	 * Obtains a lock on the collection
	 * 
	 * @param write Whether to obtain a write lock
	 * @param structural Whether to obtain a structural lock
	 * @param cause The cause of the operation (for write)
	 * @return The transaction to {@link Transaction#close()} to release the lock
	 * @see TransactableCollection
	 */
	Transaction lock(boolean write, boolean structural, Object cause);

	/**
	 * @param structural Whether to get the status of the collection for structural changes only
	 * @return
	 *         <ul>
	 *         <li>If <code>structural</code>, a stamp that will change whenever the collection changes in a structural way
	 *         (addition/removal)</li>
	 *         <li>otherwise, a stamp that changes when the collection changes in any way</li>
	 *         </ul>
	 * @see BetterCollection#getStamp(boolean)
	 */
	long getStatus(boolean structural);

	/**
	 * Marks the collection as changed
	 * 
	 * @param structural Whether the change was structural or not
	 * @see BetterCollection#getStamp(boolean)
	 */
	void changed(boolean structural);

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
	default <T> T doOptimistically(T init, OptimisticOperation<T> operation, boolean allowUpdate) {
		T res = init;
		long status = getStatus(allowUpdate);
		if (status != 0) {
			res = operation.apply(res, //
				() -> status == getStatus(allowUpdate));
			if (status == getStatus(allowUpdate))
				return res;
		} // else Write lock is taken. Wait for readability and do this reliably.
		try (Transaction t = lock(false, allowUpdate, null)) {
			return operation.apply(res, //
				() -> true);
		}
	}
}
