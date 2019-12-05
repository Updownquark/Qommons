package org.qommons;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Represents a mutable object whose modifications may possibly be batched for increased efficiency.
 * 
 * Some interfaces may extend this interface, but support implementations that do not support locking. Hence the {@link #isLockSupported()}.
 * Such implementations should return a {@link Transaction#NONE none} transaction or some such non-null transaction.
 */
public interface Transactable {
	/** A do-nothing transactable that always returns {@link Transaction#NONE} */
	static Transactable NONE = new Transactable() {
		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public boolean isLockSupported() {
			return false;
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public boolean equals(Object obj) {
			return obj == NONE;
		}

		@Override
		public String toString() {
			return "NONE";
		}
	};

	/**
	 * <p>
	 * Begins a transaction. Either an exclusive transaction in which modifications to this object may be batched and combined for increased
	 * efficiency, or a non-exclusive transaction during which exclusive locks cannot be obtained, allowing safe, stateful inspections.
	 * </p>
	 * <p>
	 * If a conflicting lock (either an exclusive lock or, when attempting to obtain an exclusive lock, any lock) is held by another thread,
	 * this method will block until it is able to obtain the lock. If a conflicting lock is held by this thread, this method may throw an
	 * exception or deadlock.
	 * </p>
	 *
	 * @param write Whether to lock this object for writing (prevents all access to controlled properties of the object outside of this
	 *        thread) or just for reading (prevents all modification to this object, this thread included).
	 * @param cause An object that may have caused the set of modifications to come. May be null, typically unused for read.
	 * @return The transaction to close when calling code is finished accessing or modifying this object
	 */
	Transaction lock(boolean write, Object cause);

	/**
	 * <p>
	 * Attempts to begin a transaction. See {@link #lock(boolean, Object)}.
	 * </p>
	 * <p>
	 * If a conflicting lock (either an exclusive lock or, when attempting to obtain an exclusive lock, any lock) is held by this or another
	 * thread, this method will fail by returning <code>null</code>.
	 * </p>
	 *
	 * @param write Whether to lock this object for writing (prevents all access to controlled properties of the object outside of this
	 *        thread) or just for reading (prevents all modification to this object, this thread included).
	 * @param cause An object that may have caused the set of modifications to come. May be null, typically unused for read.
	 * @return The transaction to close when calling code is finished accessing or modifying this object, or null if obtaining the lock
	 *         fails
	 */
	Transaction tryLock(boolean write, Object cause);

	/** @return Whether this object actually support locking */
	default boolean isLockSupported() {
		return true;
	}

	/**
	 * @param write Whether to lock this transactable for write or read
	 * @param cause The cause for the transaction
	 * @return A Lockable that locks this transactable
	 */
	default Lockable asLockable(boolean write, Object cause) {
		return Lockable.lockable(this, write, cause);
	}

	/**
	 * Locks the transactable if it is one
	 * 
	 * @param lockable The (possibly) transactable object to lock
	 * @param write Whether to lock the object for write or read
	 * @param cause The cause of the lock
	 * @return The transaction to use to unlock the object
	 */
	static Transaction lock(Object lockable, boolean write, Object cause) {
		if (lockable instanceof Transactable)
			return ((Transactable) lockable).lock(write, cause);
		else
			return Transaction.NONE;
	}

	/**
	 * @param lockable The potentially transactable item
	 * @return The item, if it is a {@link Transactable}, or {@link #NONE} otherwise
	 */
	static Transactable asTransactable(Object lockable) {
		if (lockable instanceof Transactable)
			return (Transactable) lockable;
		else
			return NONE;
	}

	/**
	 * Represents a {@link ReentrantReadWriteLock} as a {@link Transactable}
	 * 
	 * @param lock The lock to represent
	 * @return A {@link Transactable} backed by the lock
	 */
	static Transactable transactable(ReentrantReadWriteLock lock) {
		return new RRWLTransactable(lock);
	}

	/** Implements {@link Transactable#transactable(ReentrantReadWriteLock)} */
	static class RRWLTransactable implements Transactable {
		private final ReentrantReadWriteLock theLock;

		RRWLTransactable(ReentrantReadWriteLock lock) {
			theLock = lock;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lock(theLock, write);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLock(theLock, write);
		}
	}
}
