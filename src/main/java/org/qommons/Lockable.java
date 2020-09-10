package org.qommons;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * <p>
 * A concurrent interface that can be locked.
 * </p>
 * <p>
 * The meaning of the lock is context-dependent. For example, the Lockable obtained from
 * {@link #lockable(ReentrantReadWriteLock, Object, boolean)} with an argument of true will only allow a single thread to obtain a lock at
 * any given time. But with an argument of false, any number of threads may obtain a lock simultaneously, as long as no thread holds a write
 * lock on the lock argument. Interfaces exposing or implementing Lockables should generally advertise the purpose and behavior of the lock
 * in the documentation.
 * </p>
 * <p>
 * Instances of this class may not actually support thread-safe locking (see {@link #isLockSupported()}).
 * </p>
 */
public interface Lockable {
	/** A do-nothing lockable that always returns {@link Transaction#NONE} */
	static Lockable NONE = new Lockable() {
		@Override
		public boolean isLockSupported() {
			return false;
		}

		@Override
		public Transaction lock() {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock() {
			return Transaction.NONE;
		}
	};

	/** @return Whether this object actually support locking */
	boolean isLockSupported();

	/**
	 * Begins a transaction. If a conflicting lock is held by another thread, this method will block until it is able to obtain the lock. If
	 * a conflicting lock is held by this thread, this method may throw an exception or deadlock.
	 *
	 * @param write Whether to lock this object for writing (prevents all access to controlled properties of the object outside of this
	 *        thread) or just for reading (prevents all modification to this object, this thread included).
	 * @param cause An object that may have caused the set of modifications to come. May be null, typically unused for read.
	 * @return The transaction to close when calling code is finished accessing or modifying this object
	 */
	Transaction lock();

	/**
	 * Attempts to begin a transaction. If a conflicting lock (either an exclusive lock or, when attempting to obtain an exclusive lock, any
	 * lock) is held by this or another thread, this method will fail by returning <code>null</code>.
	 *
	 * @param write Whether to lock this object for writing (prevents all access to controlled properties of the object outside of this
	 *        thread) or just for reading (prevents all modification to this object, this thread included).
	 * @param cause An object that may have caused the set of modifications to come. May be null, typically unused for read.
	 * @return The transaction to close when calling code is finished accessing or modifying this object, or null if obtaining the lock
	 *         fails
	 */
	Transaction tryLock();

	/**
	 * Obtains a lock on a java {@link Lock} as a Transaction
	 * 
	 * @param lock The lock to lock--may be null, in which case {@link Transactable#NONE} will be returned
	 * @return A transaction to use to unlock the lock
	 */
	static Transaction lock(Lock lock) {
		if (lock == null)
			return Transaction.NONE;
		lock.lock();
		return lock::unlock;
	}

	/**
	 * Attempts to obtain a lock on a java {@link Lock} as a Transaction
	 * 
	 * @param lock The lock to lock--may be null, in which case {@link Transactable#NONE} will be returned
	 * @return A transaction to use to unlock the lock, or null if the lock could not be obtained
	 */
	static Transaction tryLock(Lock lock) {
		if (lock == null)
			return Transaction.NONE;
		if (lock.tryLock())
			return lock::unlock;
		return null;
	}

	/**
	 * Locks the given lock
	 * 
	 * @param lock The lock to lock--may be null, in which case {@link Transactable#NONE} will be returned
	 * @param debugInfo The object to include for debugging
	 * @return The transaction to use to unlock the lock
	 */
	static Transaction lock(ReentrantLock lock, Object debugInfo) {
		if (lock == null)
			return Transaction.NONE;
		else
			return LockDebug.debug(lock, debugInfo, true, false, () -> lock(lock));
	}

	/**
	 * Attempts to lock the given lock
	 * 
	 * @param lock The lock to lock--may be null, in which case {@link Transactable#NONE} will be returned
	 * @param debugInfo The object to include for debugging
	 * @return The transaction to use to unlock the lock, or null if the lock could not be obtained
	 */
	static Transaction tryLock(ReentrantLock lock, Object debugInfo) {
		if (lock == null)
			return Transaction.NONE;
		else
			return LockDebug.debug(lock, debugInfo, true, true, () -> tryLock(lock));
	}

	/**
	 * Locks the given lock
	 * 
	 * @param lock The lock to lock--may be null, in which case {@link Transactable#NONE} will be returned
	 * @param debugInfo The object to include for debugging
	 * @param write Whether to lock for write or read
	 * @return The transaction to use to unlock the lock
	 */
	static Transaction lock(ReentrantReadWriteLock lock, Object debugInfo, boolean write) {
		if (lock == null)
			return Transaction.NONE;
		else
			return LockDebug.debug(lock, debugInfo, write, false, () -> lock(write ? lock.writeLock() : lock.readLock()));
	}

	/**
	 * Attempts to lock the given lock
	 * 
	 * @param lock The lock to lock--may be null, in which case {@link Transactable#NONE} will be returned
	 * @param debugInfo The object to include for debugging
	 * @param write Whether to lock for write or read
	 * @return The transaction to use to unlock the lock, or null if the lock could not be obtained
	 */
	static Transaction tryLock(ReentrantReadWriteLock lock, Object debugInfo, boolean write) {
		if (lock == null)
			return Transaction.NONE;
		else
			return LockDebug.debug(lock, debugInfo, write, true, () -> tryLock(write ? lock.writeLock() : lock.readLock()));
	}

	/**
	 * @param lock The lock to represent--may be null, in which case {@link #NONE} will be returned
	 * @return A Lockable representing the lock
	 */
	static Lockable lockable(Lock lock) {
		return lock == null ? NONE : new LockLockable(lock);
	}

	/**
	 * @param lock The lock to represent
	 * @param debugInfo Information to make available with debugging
	 * @return A Lockable representing the lock
	 */
	static Lockable lockable(ReentrantLock lock, Object debugInfo) {
		if (lock == null)
			return NONE;
		else
			return new ReentrantLockLockable(lock, debugInfo);
	}

	/**
	 * @param lock The lock to represent
	 * @param debugInfo Information to make available with debugging
	 * @param write Whether the Lockable should lock the lock for write or read--may be null, in which case {@link #NONE} will be returned
	 * @return A Lockable representing the lock
	 */
	static Lockable lockable(ReentrantReadWriteLock lock, Object debugInfo, boolean write) {
		if (lock == null)
			return NONE;
		else
			return new RRWLLockable(lock, debugInfo, write);
	}

	/**
	 * @param transactable The transactable to lock--may be null, in which case {@link #NONE} will be returned
	 * @return A Lockable that locks the transactable for read
	 */
	static Lockable lockable(Transactable transactable) {
		return transactable == null ? NONE : lockable(transactable, false, null);
	}

	/**
	 * @param transactable The transactable to lock--may be null, in which case {@link #NONE} will be returned
	 * @param write Whether to lock for write or read
	 * @param cause The cause for the transaction
	 * @return A lockable that locks the transactable
	 */
	static Lockable lockable(Transactable transactable, boolean write, Object cause) {
		return transactable == null ? null : new STLockable(transactable, write, cause);
	}

	/**
	 * Creates a Lockable that safely locks a collection of Lockables. If the {@link #lock()} method fails to lock any of the composite
	 * locks, all locks will be released and re-tried. This is very effective at preventing deadlock where multiple thread-safe resources
	 * are needed for an operation.
	 * 
	 * @param locks The locks to lock, any of which may be null
	 * @return A lockable that can safely lock all the given locks
	 */
	static Lockable collapse(Collection<? extends Lockable> locks) {
		return new CollapsedLockable(null, () -> locks);
	}

	/**
	 * Creates a Lockable that safely locks a collection of Lockables. If the {@link #lock()} method fails to lock any of the composite
	 * locks, all locks will be released and re-tried. This is very effective at preventing deadlock where multiple thread-safe resources
	 * are needed for an operation.
	 * 
	 * @param first The first lockable to lock, may be null
	 * @param locks The additional locks to lock, any of which may be null
	 * @return A lockable that can safely lock all the given locks
	 */
	static Lockable collapse(Lockable first, Supplier<? extends Collection<? extends Lockable>> locks) {
		return new CollapsedLockable(first, locks);
	}

	/**
	 * @param lockable The lockable to lock--may be null, in which case {@link #NONE} will be returned
	 * @return The transaction to close to release the lock
	 */
	static Transaction lock(Lockable lockable) {
		return lockable == null ? Transaction.NONE : lockable.lock();
	}

	/**
	 * @param lockable The lockable to lock--may be null, in which case {@link #NONE} will be returned
	 * @return The transaction to close to release the lock, or null if the lock could not be obtained
	 */
	static Transaction tryLock(Lockable lockable) {
		return lockable == null ? Transaction.NONE : lockable.tryLock();
	}

	/**
	 * @param lockables The lockables to check
	 * @return Whether every non-null lockable in the list {@link #isLockSupported() supports locking}
	 */
	static boolean isLockSupported(Lockable... lockables) {
		return isLockSupported(Arrays.asList(lockables));
	}

	/**
	 * @param lockables The lockables to check
	 * @return Whether every non-null lockable in the list {@link #isLockSupported() supports locking}
	 */
	static boolean isLockSupported(Collection<? extends Lockable> lockables) {
		for (Lockable lockable : lockables)
			if (lockable != null && !lockable.isLockSupported())
				return false;
		return true;
	}

	/**
	 * @param first The first lockable to check
	 * @param lockables The additional lockables to check
	 * @return Whether every non-null lockable in the list {@link #isLockSupported() supports locking}
	 */
	static boolean isLockSupported(Lockable first, Collection<? extends Lockable> lockables) {
		if (first != null && !first.isLockSupported())
			return false;
		for (Lockable lockable : lockables)
			if (lockable != null && !lockable.isLockSupported())
				return false;
		return true;
	}

	/**
	 * Safely locks a set of lockables, blocking until a lock is obtained for every lockable. If this method fails to lock any of the
	 * composite locks, all locks will be released and re-tried. This is very effective at preventing deadlock where multiple thread-safe
	 * resources are needed for an operation.
	 * 
	 * @param lockables The lockables to lock, any of which may be null
	 * @return The transaction to close to release the lock
	 */
	static Transaction lockAll(Lockable... lockables) {
		return lockAll(//
			Arrays.asList(lockables));
	}

	/**
	 * Safely locks a set of lockables, blocking until a lock is obtained for every lockable. If this method fails to lock any of the
	 * composite locks, all locks will be released and re-tried. This is very effective at preventing deadlock where multiple thread-safe
	 * resources are needed for an operation.
	 * 
	 * @param lockables The lockables to lock, any of which may be null
	 * @return The transaction to close to release the lock
	 */
	static Transaction lockAll(Collection<? extends Lockable> lockables) {
		return lockAll(null, lockables);
	}

	/**
	 * Safely locks a set of lockables, blocking until a lock is obtained for every lockable. If this method fails to lock any of the
	 * composite locks, all locks will be released and re-tried. This is very effective at preventing deadlock where multiple thread-safe
	 * resources are needed for an operation.
	 * 
	 * @param outer The first lockable to lock
	 * @param lockables The additional lockables to lock, any of which may be null
	 * @return The transaction to close to release the lock
	 */
	static Transaction lockAll(Lockable outer, Collection<? extends Lockable> lockables) {
		return lockAll(outer, //
			() -> lockables, l -> l);
	}

	/**
	 * Safely locks a set of lockables, blocking until a lock is obtained for every lockable. If this method fails to lock any of the
	 * composite locks, all locks will be released and re-tried. This is very effective at preventing deadlock where multiple thread-safe
	 * resources are needed for an operation.
	 * 
	 * @param <X> The type of structures to lock
	 * @param outer The first lockable to lock
	 * @param lockables The additional structures to lock, any of which may be null
	 * @param map The map to produce Lockables from each item in the list
	 * @return The transaction to close to release the lock
	 */
	static <X> Transaction lockAll(Lockable outer, Supplier<? extends Collection<? extends X>> lockables,
		Function<? super X, ? extends Lockable> map) {
		reattempt: while (true) {
			Transaction outerLock = null;
			boolean hasLock = false;
			if (outer != null) {
				hasLock = true;
				outerLock = outer.lock();
			}
			Collection<? extends X> coll = lockables.get();
			if (coll == null || coll.isEmpty())
				return outerLock == null ? Transaction.NONE : outerLock;
			Transaction[] locks = new Transaction[coll.size() + (outer == null ? 0 : 1)];
			if (outerLock != null)
				locks[0] = outerLock;
			try {
				int i = outerLock == null ? 0 : 1;
				for (X value : lockables.get()) {
					Lockable lockable = map.apply(value);
					if (lockable == null) {//
					} else if (!hasLock) {
						hasLock = true;
						locks[i] = lockable.lock();
					} else {
						Transaction lock = lockable.tryLock();
						if (lock == null) {
							for (int j = i - 1; j >= 0; j--) {
								if (locks[j] != null)
									locks[j].close();
							}
							try {
								Thread.sleep(2);
							} catch (InterruptedException e) {}
							continue reattempt;
						}
						locks[i] = lock;
					}
					i++;
				}
				return Transaction.and(locks);
			} catch (RuntimeException | Error e) {
				Transaction.and(locks).close();
				throw e;
			}
		}
	}

	/**
	 * Attempts to obtain a lock for a set of lockables. If any lock is unobtainable, all locks are released and null is returned.
	 * 
	 * @param lockables The lockables to lock
	 * @return A transaction to close to release the locks, or null if the lock could not be obtained.
	 */
	static Transaction tryLockAll(Lockable... lockables) {
		return tryLockAll(Arrays.asList(lockables));
	}

	/**
	 * Attempts to obtain a lock for a set of lockables. If any lock is unobtainable, all locks are released and null is returned.
	 * 
	 * @param lockables The lockables to lock
	 * @return A transaction to close to release the locks, or null if the lock could not be obtained.
	 */
	static Transaction tryLockAll(Collection<? extends Lockable> lockables) {
		return tryLockAll(null, lockables);
	}

	/**
	 * Attempts to obtain a lock for a set of lockables. If any lock is unobtainable, all locks are released and null is returned.
	 * 
	 * @param outer The first lockable to lock
	 * @param lockables The additional lockables to lock
	 * @return A transaction to close to release the locks, or null if the lock could not be obtained.
	 */
	static Transaction tryLockAll(Lockable outer, Collection<? extends Lockable> lockables) {
		return tryLockAll(outer, () -> lockables);
	}

	/**
	 * Attempts to obtain a lock for a set of lockables. If any lock is unobtainable, all locks are released and null is returned.
	 * 
	 * @param outer The first lockable to lock
	 * @param lockables The additional lockables to lock
	 * @return A transaction to close to release the locks, or null if the lock could not be obtained.
	 */
	static Transaction tryLockAll(Lockable outer, Supplier<? extends Collection<? extends Lockable>> lockables) {
		return tryLockAll(outer, lockables, l -> l);
	}

	/**
	 * Attempts to obtain a lock for a set of lockables. If any lock is unobtainable, all locks will be released and null returned.
	 * 
	 * @param <X> The type of structures to lock
	 * @param outer The first lockable to lock
	 * @param lockables The additional structures to lock
	 * @param map The map to produce Lockables from each item in the list
	 * @return A transaction to close to release the locks, or null if the lock could not be obtained.
	 */
	static <X> Transaction tryLockAll(Lockable outer, Supplier<? extends Collection<? extends X>> lockables,
		Function<? super X, ? extends Lockable> map) {
		Transaction outerLock;
		if (outer != null) {
			outerLock = outer.tryLock();
			if (outerLock == null)
				return null;
		} else
			outerLock = null;
		Collection<? extends X> coll = lockables.get();
		if (coll == null || coll.isEmpty())
			return outerLock == null ? Transaction.NONE : outerLock;
		Transaction[] locks = new Transaction[(outerLock == null ? 0 : 1) + coll.size()];
		if (outerLock != null)
			locks[0] = outerLock;
		try {
			int i = outerLock == null ? 0 : 1;
			for (X value : coll) {
				Lockable lockable = map.apply(value);
				locks[i] = Lockable.tryLock(lockable);
				if (locks[i] == null) {
					for (int j = i - 1; j >= 0; j--)
						locks[j].close();
					return null;
				}
				i++;
			}
			return Transaction.and(locks);
		} catch (RuntimeException | Error e) {
			Transaction.and(locks).close();
			throw e;
		}
	}

	/**
	 * Safely locks a pair of lockables, blocking until a lock is obtained for both. If this method fails to lock either of the composite
	 * locks, all locks will be released and re-tried. This is very effective at preventing deadlock where multiple thread-safe resources
	 * are needed for an operation.
	 * 
	 * @param outer The first lockable to lock
	 * @param getInner The additional lockable to lock--may supply null
	 * @return The transaction to close to release the lock
	 */
	static Transaction lock(Lockable outer, Supplier<Lockable> getInner) {
		while (true) {
			Transaction outerLock = outer.lock();
			Lockable inner = getInner.get();
			if (inner == null)
				return outerLock;
			Transaction innerLock;
			try {
				innerLock = inner.tryLock();
			} catch (RuntimeException | Error e) {
				outerLock.close();
				throw e;
			}
			if (innerLock == null) {
				outerLock.close();
				try {
					Thread.sleep(2);
				} catch (InterruptedException e) {}
				continue;
			}
			return Transaction.and(outerLock, innerLock);
		}
	}

	/**
	 * Attempts to obtain a lock for a pair of lockables. If either lock is unobtainable, all locks will be released and null returned
	 * 
	 * @param outer The first lockable to lock
	 * @param getInner The additional lockable to lock--may supply null
	 * @return A transaction to close to release the locks, or null if the lock could not be obtained.
	 */
	static Transaction tryLock(Lockable outer, Supplier<Lockable> getInner) {
		Transaction outerLock = outer.tryLock();
		if (outerLock == null)
			return null;
		Lockable inner = getInner.get();
		if (inner == null)
			return outerLock;
		Transaction innerLock = inner.tryLock();
		if (innerLock == null) {
			outerLock.close();
			return null;
		}
		return Transaction.and(outerLock, innerLock);
	}

	/** Implements {@link Lockable#lockable(Lock)} */
	static class LockLockable implements Lockable {
		private final Lock theLock;

		public LockLockable(Lock lock) {
			theLock = lock;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock() {
			theLock.lock();
			return theLock::unlock;
		}

		@Override
		public Transaction tryLock() {
			if (!theLock.tryLock())
				return null;
			return theLock::unlock;
		}

		@Override
		public int hashCode() {
			return theLock.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof LockLockable && theLock.equals(((LockLockable) obj).theLock);
		}

		@Override
		public String toString() {
			return theLock.toString();
		}
	}

	/** Implements {@link Lockable#lockable(Transactable)} */
	static class STLockable implements Lockable {
		private final Transactable theTransactable;
		private final boolean write;
		private final Object cause;

		public STLockable(Transactable transactable, boolean write, Object cause) {
			theTransactable = transactable;
			this.write = write;
			this.cause = cause;
		}

		@Override
		public boolean isLockSupported() {
			return theTransactable.isLockSupported();
		}

		@Override
		public Transaction lock() {
			return theTransactable.lock(write, cause);
		}

		@Override
		public Transaction tryLock() {
			return theTransactable.tryLock(write, cause);
		}

		@Override
		public int hashCode() {
			return theTransactable.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof STLockable && theTransactable.equals(((STLockable) obj).theTransactable)//
				&& write == ((STLockable) obj).write && Objects.equals(cause, ((STLockable) obj).cause);
		}

		@Override
		public String toString() {
			return theTransactable.toString() + ".lock(" + write + ")";
		}
	}

	/** Implements all the {@link Lockable#lockAll(Lockable...)} methods */
	static class CollapsedLockable implements Lockable {
		private final Lockable theFirst;
		private final Supplier<? extends Collection<? extends Lockable>> theLocks;

		public CollapsedLockable(Lockable first, Supplier<? extends Collection<? extends Lockable>> locks) {
			theFirst = first;
			theLocks = locks;
		}

		@Override
		public boolean isLockSupported() {
			return Lockable.isLockSupported(theFirst, theLocks.get());
		}

		@Override
		public Transaction lock() {
			return lockAll(theFirst, theLocks, l -> l);
		}

		@Override
		public Transaction tryLock() {
			return tryLockAll(theFirst, theLocks, l -> l);
		}
	}

	/** Implements {@link Lockable#lockable(ReentrantLock, Object)} */
	static class ReentrantLockLockable implements Lockable {
		private final ReentrantLock theLock;
		private final Object theDebugInfo;

		public ReentrantLockLockable(ReentrantLock lock, Object debugInfo) {
			theLock = lock;
			theDebugInfo = debugInfo;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock() {
			return Lockable.lock(theLock, theDebugInfo);
		}

		@Override
		public Transaction tryLock() {
			return Lockable.tryLock(theLock, theDebugInfo);
		}
	}

	/** Implements {@link Lockable#lockable(ReentrantReadWriteLock, Object, boolean)} */
	static class RRWLLockable implements Lockable {
		private final ReentrantReadWriteLock theLock;
		private final Object theDebugInfo;
		private final boolean isWrite;

		public RRWLLockable(ReentrantReadWriteLock lock, Object debugInfo, boolean write) {
			theLock = lock;
			theDebugInfo = debugInfo;
			isWrite = write;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock() {
			return Lockable.lock(theLock, theDebugInfo, isWrite);
		}

		@Override
		public Transaction tryLock() {
			return Lockable.tryLock(theLock, theDebugInfo, isWrite);
		}
	}
}
