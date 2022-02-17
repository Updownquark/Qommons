package org.qommons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
 * {@link #lockable(ReentrantReadWriteLock, Object, boolean, ThreadConstraint)} with an argument of true will only allow a single thread to
 * obtain a lock at any given time. But with an argument of false, any number of threads may obtain a lock simultaneously, as long as no
 * thread holds a write lock on the lock argument. Interfaces exposing or implementing Lockables should generally advertise the purpose and
 * behavior of the lock in the documentation.
 * </p>
 * <p>
 * Instances of this class may not actually support thread-safe locking (see {@link #isLockSupported()}).
 * </p>
 */
public interface Lockable extends ThreadConstrained {
	/**
	 * A do-nothing lockable that always returns {@link Transaction#NONE} and has no thread constraint ({@link ThreadConstraint#ANY ANY})
	 */
	static Lockable NONE = noLock(ThreadConstraint.ANY, false);

	/**
	 * A do-nothing lockable that always returns {@link Transaction#NONE} and represents an eventable that cannot fire events
	 * ({@link ThreadConstraint#NONE NONE})
	 */
	static Lockable IMMUTABLE = noLock(ThreadConstraint.NONE, false);

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

	/** @return A {@link CoreId} object containing all true locking cores used by this Lockable */
	CoreId getCoreId();

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
	 * @param lock The lock to represent
	 * @param debugInfo Information to make available with debugging
	 * @param threadConstraint The thread constraint for the lockable
	 * @return A Lockable representing the lock
	 */
	static Lockable lockable(ReentrantLock lock, Object debugInfo, ThreadConstraint threadConstraint) {
		if (lock == null)
			return noLock(threadConstraint, true);
		else
			return new ReentrantLockLockable(lock, debugInfo, threadConstraint);
	}

	/**
	 * @param lock The lock to represent
	 * @param debugInfo Information to make available with debugging
	 * @param write Whether the Lockable should lock the lock for write or read--may be null, in which case {@link #NONE} will be returned
	 * @param threadConstraint The thread constraint for the lockable
	 * @return A Lockable representing the lock
	 */
	static Lockable lockable(ReentrantReadWriteLock lock, Object debugInfo, boolean write, ThreadConstraint threadConstraint) {
		if (lock == null)
			return noLock(threadConstraint, write);
		else
			return new RRWLLockable(lock, debugInfo, write, threadConstraint);
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
		return new CollapsedLockable(null, () -> locks, true);
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
		return new CollapsedLockable(first, locks, false);
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
	 * Gets the core ID for a set of lockables
	 * 
	 * @param lockables The lockables
	 * @return A CoreId containing core information about all lockables
	 */
	static CoreId getCoreId(Lockable... lockables) {
		return getCoreId(Arrays.asList(lockables));
	}

	/**
	 * Gets the core ID for a set of lockables
	 * 
	 * @param lockables The lockables
	 * @return A CoreId containing core information about all lockables
	 */
	static CoreId getCoreId(Collection<? extends Lockable> lockables) {
		CoreId first = null;
		List<CoreId> others = null;
		for (Lockable lockable : lockables) {
			if (lockable == null)
				continue;
			if (first == null)
				first = lockable.getCoreId();
			else {
				if (others == null)
					others = new ArrayList<>(lockables.size() - 1);
				others.add(lockable.getCoreId());
			}
		}
		if (first == null)
			return CoreId.EMPTY;
		else if (others == null)
			return first;
		else
			return first.and(others);
	}

	/**
	 * Gets the core ID for a couple of lockables
	 * 
	 * @param outer The first lockable
	 * @param inner Potentially produces another lockable after the first lockable is locked
	 * @return A CoreId containing core information about both lockables
	 */
	static CoreId getCoreId(Lockable outer, Supplier<? extends Lockable> inner) {
		return getCoreId(outer, () -> Arrays.asList(inner.get()), l -> l);
	}

	/**
	 * Gets the core ID for a set of lockables
	 * 
	 * @param outer The first lockable
	 * @param lockables The additional lockables
	 * @return A CoreId containing core information about all given lockables
	 */
	static CoreId getCoreId(Lockable outer, Collection<? extends Lockable> lockables) {
		return getCoreId(outer, () -> lockables, l -> l);
	}

	/**
	 * Gets the core ID for a set of lockables
	 * 
	 * @param <X> The type of lockable structures
	 * @param outer The first lockable
	 * @param lockables The additional lockable structures
	 * @param map The map to produce Lockables from each item in the list
	 * @return A CoreId containing core information about all given lockables
	 */
	static <X> CoreId getCoreId(Lockable outer, Supplier<? extends Collection<? extends X>> lockables,
		Function<? super X, ? extends Lockable> map) {
		Transaction outerLock;
		if (outer != null) {
			outerLock = outer.tryLock();
		} else
			outerLock = null;
		try {
			CoreId core = outer == null ? CoreId.EMPTY : outer.getCoreId();
			Collection<? extends X> others = lockables.get();
			if (others == null)
				return core;
			CoreId[] otherCores = new CoreId[others.size()];
			int i = 0;
			for (X other : others) {
				Lockable lock = map.apply(other);
				if (lock != null)
					otherCores[i++] = lock.getCoreId();
			}
			return core.and(otherCores);
		} finally {
			if (outerLock != null)
				outerLock.close();
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

	/**
	 * @param threadConstraint The thread constraint for the lockable to obey
	 * @param write Whether exclusive locks attempted away from the thread constraint's event thread should throw an
	 *        {@link UnsupportedOperationException}
	 * @return A lockable that obeys the given thread constraint but provides no thread safety and always returns {@link Transaction#NONE}
	 */
	static Lockable noLock(ThreadConstraint threadConstraint, boolean write) {
		return new NullLockable(threadConstraint, write);
	}

	/** Implements {@link Lockable#noLock(ThreadConstraint, boolean)} */
	static class NullLockable implements Lockable {
		private final ThreadConstraint theThreadConstraint;
		private final boolean isWrite;

		NullLockable(ThreadConstraint threadConstraint, boolean write) {
			theThreadConstraint = threadConstraint;
			isWrite = write;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theThreadConstraint;
		}

		@Override
		public boolean isLockSupported() {
			return false;
		}

		@Override
		public Transaction lock() {
			if (isWrite && !theThreadConstraint.isEventThread())
				throw new IllegalStateException(WRONG_THREAD_MESSAGE);
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock() {
			if (isWrite && !theThreadConstraint.isEventThread())
				throw new IllegalStateException(WRONG_THREAD_MESSAGE);
			return Transaction.NONE;
		}

		@Override
		public CoreId getCoreId() {
			return CoreId.EMPTY;
		}

		@Override
		public int hashCode() {
			return theThreadConstraint.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof NullLockable && theThreadConstraint.equals(((NullLockable) obj).theThreadConstraint);
		}

		@Override
		public String toString() {
			if (theThreadConstraint == ThreadConstraint.NONE)
				return "IMMUTABLE";
			else if (theThreadConstraint == ThreadConstraint.ANY)
				return "NONE";
			else
				return "lockable(" + theThreadConstraint + ")";
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
		public ThreadConstraint getThreadConstraint() {
			return theTransactable.getThreadConstraint();
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
		public CoreId getCoreId() {
			return theTransactable.getCoreId();
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
		private final boolean isConstant;

		public CollapsedLockable(Lockable first, Supplier<? extends Collection<? extends Lockable>> locks, boolean constant) {
			theFirst = first;
			theLocks = locks;
			isConstant = constant;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			if (!isConstant)
				return ThreadConstraint.ANY; // Can't know
			return ThreadConstrained.getThreadConstraint(theFirst, theLocks.get(), LambdaUtils.identity());
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

		@Override
		public Lockable.CoreId getCoreId() {
			// Best we can do here is capture a snapshot
			Lockable.CoreId cores = theFirst.getCoreId();
			try (Transaction t = theFirst.lock()) {
				Collection<? extends Lockable> others = theLocks.get();
				if (others != null) {
					Lockable.CoreId[] otherCores = new Lockable.CoreId[others.size()];
					int i = 0;
					for (Lockable other : others) {
						if (other != null)
							otherCores[i++] = other.getCoreId();
					}
					cores = cores.and(otherCores);
				}
			}
			return cores;
		}
	}

	/** Implements {@link Lockable#lockable(ReentrantLock, Object, ThreadConstraint)} */
	static class ReentrantLockLockable implements Lockable {
		private final ReentrantLock theLock;
		private final Object theDebugInfo;
		private final ThreadConstraint theThreadConstraint;

		public ReentrantLockLockable(ReentrantLock lock, Object debugInfo, ThreadConstraint threadConstraint) {
			theLock = lock;
			theDebugInfo = debugInfo;
			theThreadConstraint = threadConstraint;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theThreadConstraint;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock() {
			if (!theThreadConstraint.isEventThread())
				throw new IllegalStateException(WRONG_THREAD_MESSAGE);
			return Lockable.lock(theLock, theDebugInfo);
		}

		@Override
		public Transaction tryLock() {
			if (!theThreadConstraint.isEventThread())
				throw new IllegalStateException(WRONG_THREAD_MESSAGE);
			return Lockable.tryLock(theLock, theDebugInfo);
		}

		@Override
		public CoreId getCoreId() {
			return new CoreId(theLock);
		}
	}

	/** Implements {@link Lockable#lockable(ReentrantReadWriteLock, Object, boolean, ThreadConstraint)} */
	static class RRWLLockable implements Lockable {
		private final ReentrantReadWriteLock theLock;
		private final ThreadConstraint theThreadConstraint;
		private final Object theDebugInfo;
		private final boolean isWrite;

		public RRWLLockable(ReentrantReadWriteLock lock, Object debugInfo, boolean write, ThreadConstraint threadConstraint) {
			theLock = lock;
			theDebugInfo = debugInfo;
			isWrite = write;
			theThreadConstraint = threadConstraint;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theThreadConstraint;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock() {
			if (isWrite && !theThreadConstraint.isEventThread())
				throw new UnsupportedOperationException(WRONG_THREAD_MESSAGE);
			else
				return Lockable.lock(theLock, theDebugInfo, isWrite);
		}

		@Override
		public Transaction tryLock() {
			if (isWrite && !theThreadConstraint.isEventThread())
				throw new UnsupportedOperationException(WRONG_THREAD_MESSAGE);
			else
				return Lockable.tryLock(theLock, theDebugInfo, isWrite);
		}

		@Override
		public CoreId getCoreId() {
			return new CoreId(theLock);
		}
	}

	/** Contains information about the source of a {@link Lockable} or {@link Transactable}'s ability to ensure thread safety */
	class CoreId {
		public static final CoreId EMPTY=new CoreId();
		
		private final Set<Object> theCores;
	
		/** @param cores The cores to wrap */
		public CoreId(Object... cores) {
			this(new HashSet<>(Arrays.asList(cores)));
		}
	
		private CoreId(Set<Object> cores) {
			theCores = cores;
		}

		public CoreId and(CoreId... others) {
			return and(Arrays.asList(others));
		}

		/**
		 * @param others The other cores to combine
		 * @return A CoreId containing all information contained in this or any of the other given cores
		 */
		public CoreId and(Collection<? extends CoreId> others) {
			if (others.isEmpty())
				return this;
			Set<Object> newCores=null;
			for(CoreId other : others) {
				if(other==null)
					continue;
				for(Object core : other.theCores) {
					if(newCores!=null)
						newCores.add(core);
					else if(!theCores.contains(core)) {
						newCores=new HashSet<>(theCores);
						newCores.add(core);
					}
				}
			}
			if(newCores==null)
				return this;
			else
				return new CoreId(newCores);
		}
	
		public boolean intersects(CoreId other) {
			if (theCores.size() <= other.theCores.size()) {
				for (Object core : theCores)
					if (other.theCores.contains(core))
						return true;
			} else {
				for (Object core : other.theCores)
					if (theCores.contains(core))
						return true;
			}
			return false;
		}
	
		@Override
		public int hashCode() {
			return theCores.hashCode();
		}
	
		@Override
		public boolean equals(Object obj) {
			return obj instanceof CoreId && theCores.equals(((CoreId) obj).theCores);
		}
	
		@Override
		public String toString() {
			return theCores.toString();
		}
	}
}
