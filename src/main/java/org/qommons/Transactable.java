package org.qommons;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;

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
	 * Attempts to lock the transactable if it is one
	 * 
	 * @param lockable The (possibly) transactable object to lock
	 * @param write Whether to lock the object for write or read
	 * @param cause The cause of the lock
	 * @return The transaction to use to unlock the object, or null if the lock could not be obtained
	 */
	static Transaction tryLock(Object lockable, boolean write, Object cause) {
		if (lockable instanceof Transactable)
			return ((Transactable) lockable).tryLock(write, cause);
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
	 * @param debugInfo Information to use to debug locking
	 * @return A {@link Transactable} backed by the lock
	 */
	static Transactable transactable(ReentrantReadWriteLock lock, Object debugInfo) {
		return new RRWLTransactable(lock, debugInfo);
	}

	/**
	 * Combines the transactables into a single transactable that locks all of them safely
	 * 
	 * @param transactables The transactables to lock collectively
	 * @return The combined transactable
	 */
	static Transactable combine(Collection<? extends Transactable> transactables) {
		return combine(null, () -> transactables, x -> x);
	}

	/**
	 * Combines the transactables into a single transactable that locks all of them safely
	 * 
	 * @param first The first transactable to lock collectively
	 * @param others The other transactables to lock collectively
	 * @return The combined transactable
	 */
	static Transactable combine(Transactable first, Transactable... others) {
		return combine(first, () -> Arrays.asList(others), x -> x);
	}

	/**
	 * Combines the transactables into a single transactable that locks all of them safely
	 * 
	 * @param first The first transactable to lock collectively
	 * @param others The other transactables to lock collectively
	 * @return The combined transactable
	 */
	static Transactable combine(Transactable first, Collection<? extends Transactable> others) {
		return combine(first, () -> others, t -> t);
	}

	/**
	 * Combines the transactables into a single transactable that locks all of them safely
	 * 
	 * @param <X> The type of transactable to lock
	 * @param first The first transactable to lock collectively
	 * @param others The other transactables to lock collectively
	 * @param map The function to supply a Transactable for each non-null item among <code>first</code> and <code>others</code>
	 * @return The combined transactable
	 */
	static <X> Transactable combine(Transactable first, Supplier<? extends Collection<? extends X>> others,
		Function<? super X, ? extends Transactable> map) {
		return new CombinedTransactable<X>(first, others, map);
	}

	/**
	 * @param lockable The lockable
	 * @return A transactable that locks the lockable
	 */
	static Transactable transactable(Lockable lockable) {
		return new LockableTransactable(lockable);
	}

	/** Implements {@link Transactable#transactable(ReentrantReadWriteLock, Object)} */
	static class RRWLTransactable implements Transactable {
		private final ReentrantReadWriteLock theLock;
		private final Object theDebugInfo;

		RRWLTransactable(ReentrantReadWriteLock lock, Object debugInfo) {
			theLock = lock;
			theDebugInfo = debugInfo;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lock(theLock, theDebugInfo, write);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLock(theLock, theDebugInfo, write);
		}
	}

	/**
	 * Implements {@link Transactable#combine(Transactable, Supplier, Function)} and the other combine methods.
	 * 
	 * @param <X> The type of transactable value
	 */
	static class CombinedTransactable<X> implements Transactable {
		private final Transactable theFirst;
		private final Supplier<? extends Collection<? extends X>> theOthers;
		private final Function<? super X, ? extends Transactable> theMap;

		public CombinedTransactable(Transactable first, Supplier<? extends Collection<? extends X>> others,
			Function<? super X, ? extends Transactable> map) {
			theFirst = first;
			theOthers = others;
			theMap = map;
		}

		@Override
		public boolean isLockSupported() {
			if (theFirst != null && !theFirst.isLockSupported())
				return false;
			for (X lockable : theOthers.get())
				if (lockable != null && !theMap.apply(lockable).isLockSupported())
					return false;
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Lockable.lockAll(//
				Lockable.lockable(theFirst, write, cause), theOthers, x -> Lockable.lockable(theMap.apply(x), write, cause));
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Lockable.tryLockAll(//
				Lockable.lockable(theFirst, write, cause), theOthers, x -> Lockable.lockable(theMap.apply(x), write, cause));
		}

		@Override
		public String toString() {
			return "transactable(" + theFirst + ", " + theOthers.get() + ")";
		}
	}

	/** Implements {@link Transactable#transactable(Lockable)} */
	static class LockableTransactable implements Transactable {
		private final Lockable theLockable;

		public LockableTransactable(Lockable lockable) {
			theLockable = lockable;
		}

		@Override
		public boolean isLockSupported() {
			return theLockable.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theLockable.lock();
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theLockable.tryLock();
		}
	}
}
