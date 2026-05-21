package org.qommons;

import static org.qommons.Lockable.lockAll;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.fn.FunctionUtils;

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
 * Some implementations of this interface may not actually support locking. Such implementations should return a {@link Transaction#NONE
 * none} transaction or some such non-null transaction.
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

	/**
	 * Obtains a lock. If a conflicting lock is held by another thread, this method will either return null (if<code>tryOnly</code> is true)
	 * or block until it is able to obtain a lock (if <code>tryOnly</code> is false). If a conflicting lock is held by this thread, this
	 * method may throw an exception or deadlock.
	 *
	 * @param tryOnly Whether to abandon the attempt if the lock cannot be immediately obtained
	 * @return The transaction to close when calling code is finished accessing or modifying this object, or null if <code>tryOnly</code> is
	 *         true and the lock could not be immediately obtained
	 */
	Transaction lock(boolean tryOnly);

	/** @return A {@link CoreId} object containing information about all true locking cores used by this Lockable */
	CoreId getCoreId();

	/**
	 * Obtains a lock on a java {@link Lock} as a Transaction
	 * 
	 * @param lock The lock to lock--may be null, in which case {@link Transactable#NONE} will be returned
	 * @param tryOnly Whether to abandon the attempt if the lock cannot be immediately obtained
	 * @return The transaction to use to unlock the lock, or null if <code>tryOnly</code> is true and the lock could not be immediately
	 *         obtained
	 */
	static Transaction lock(Lock lock, boolean tryOnly) {
		if (lock == null)
			return Transaction.NONE;
		if (tryOnly) {
			if (!lock.tryLock())
				return null;
		} else
			lock.lock();
		return lock::unlock;
	}

	/**
	 * Locks the given lock
	 * 
	 * @param lock The lock to lock--may be null, in which case {@link Transactable#NONE} will be returned
	 * @param tryOnly Whether to abandon the attempt if the lock cannot be immediately obtained
	 * @param debugInfo The object to include for debugging
	 * @return The transaction to use to unlock the lock, or null if <code>tryOnly</code> is true and the lock could not be immediately
	 *         obtained
	 */
	static Transaction lock(ReentrantLock lock, boolean tryOnly, Object debugInfo) {
		if (lock == null)
			return Transaction.NONE;
		return LockDebug.debug(lock, debugInfo, true, false, () -> lock(lock, tryOnly));
	}

	/**
	 * Locks the given lock
	 * 
	 * @param lock The lock to lock--may be null, in which case {@link Transactable#NONE} will be returned
	 * @param write Whether to lock for write or read
	 * @param tryOnly Whether to abandon the attempt if the lock cannot be immediately obtained
	 * @param debugInfo The object to include for debugging
	 * @return The transaction to use to unlock the lock, or null if <code>tryOnly</code> is true and the lock could not be immediately
	 *         obtained
	 */
	static Transaction lock(ReentrantReadWriteLock lock, boolean write, boolean tryOnly, Object debugInfo) {
		if (lock == null)
			return Transaction.NONE;
		Lock readOrWriteLock = write ? lock.writeLock() : lock.readLock();
		return LockDebug.debug(lock, debugInfo, write, false, () -> lock(readOrWriteLock, tryOnly));
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
	 * Creates a Lockable that safely locks a collection of Lockables. If the {@link #lock(boolean)} method fails to lock any of the
	 * composite locks, all locks will be released and re-tried. This is very effective at preventing deadlock where multiple thread-safe
	 * resources are needed for an operation.
	 * 
	 * @param locks The locks to lock, any of which may be null
	 * @return A lockable that can safely lock all the given locks
	 */
	static Lockable collapse(Collection<? extends Lockable> locks) {
		if (locks.isEmpty())
			return NONE;
		return new CollapsedLockable(null, () -> locks, true);
	}

	/**
	 * Creates a Lockable that safely locks a collection of Lockables. If the {@link #lock(boolean)} method fails to lock any of the
	 * composite locks, all locks will be released and re-tried. This is very effective at preventing deadlock where multiple thread-safe
	 * resources are needed for an operation.
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
	 * @param tryOnly Whether to abandon the attempt if the lock cannot be immediately obtained
	 * @return The transaction to use to unlock the lock, or null if <code>tryOnly</code> is true and the lock could not be immediately
	 *         obtained
	 */
	static Transaction lockLockable(Object lockable, boolean tryOnly) {
		return lockable instanceof Lockable ? ((Lockable) lockable).lock(tryOnly) : Transaction.NONE;
	}

	/**
	 * Safely locks a set of lockables, blocking until a lock is obtained for every lockable. If this method fails to lock any of the
	 * composite locks, all locks will be released and re-tried. This is very effective at preventing deadlock where multiple thread-safe
	 * resources are needed for an operation.
	 * 
	 * @param lockables The lockables to lock, any of which may be null
	 * @return The transaction to use to unlock the lock, or null if <code>tryOnly</code> is true and the lock could not be immediately
	 *         obtained
	 */
	static Transaction lockAll(boolean tryOnly, Lockable... lockables) {
		return lockAll(//
			Arrays.asList(lockables), tryOnly);
	}

	/**
	 * Safely locks a set of lockables, blocking until a lock is obtained for every lockable. If this method fails to lock any of the
	 * composite locks, all locks will be released and re-tried. This is very effective at preventing deadlock where multiple thread-safe
	 * resources are needed for an operation.
	 * 
	 * @param lockables The lockables to lock, any of which may be null
	 * @return The transaction to close to release the lock
	 */
	static Transaction lockAll(Collection<? extends Lockable> lockables, boolean tryOnly) {
		return lockAll(null, lockables, tryOnly);
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
	static Transaction lockAll(Lockable outer, Collection<? extends Lockable> lockables, boolean tryOnly) {
		return lockAll(outer, //
			() -> lockables, FunctionUtils.identity(), tryOnly);
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
		Function<? super X, ? extends Lockable> map, boolean tryOnly) {
		reattempt: while (true) {
			Transaction outerLock = null;
			boolean hasLock = false;
			if (outer != null) {
				hasLock = true;
				outerLock = outer.lock(tryOnly);
				if (outerLock == null)
					return null;
			}
			Collection<? extends X> coll = lockables.get();
			if (coll == null || coll.isEmpty())
				return outerLock == null ? Transaction.NONE : outerLock;
			Object[] lockablesCopy = coll.toArray();
			Transaction[] locks = new Transaction[lockablesCopy.length + (outer == null ? 0 : 1)];
			if (outerLock != null)
				locks[0] = outerLock;
			try {
				int i = outerLock == null ? 0 : 1;
				for (Object value : lockablesCopy) {
					Lockable lockable = map.apply((X) value);
					if (lockable == null) {//
					} else if (!hasLock) {
						hasLock = true;
						locks[i] = lockable.lock(tryOnly);
						if (locks[i] == null)
							return null;
					} else {
						Transaction lock = lockable.lock(true);
						if (lock == null) {
							for (int j = i - 1; j >= 0; j--) {
								if (locks[j] != null)
									locks[j].close();
							}
							try {
								Thread.sleep(2);
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
							}
							if (tryOnly)
								return null;
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
			outerLock = outer.lock(true);
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
	 * @param outer The first lockable to lock
	 * @param getInner The additional lockable to lock--may supply null
	 * @return A lockable whose {@link #lock(boolean)} method works like {@link #lock(Lockable, Supplier)}
	 */
	static Lockable lockable(Lockable outer, Supplier<Lockable> getInner, boolean tryOnly) {
		return new CollapsedLockable(outer, () -> Collections.singleton(getInner.get()), false);
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
	static Transaction lock(Lockable outer, Supplier<Lockable> getInner, boolean tryOnly) {
		while (true) {
			Transaction outerLock = outer.lock(tryOnly);
			if (outerLock == null)
				return null;
			Lockable inner = getInner.get();
			if (inner == null)
				return outerLock;
			Transaction innerLock;
			try {
				innerLock = inner.lock(true);
			} catch (RuntimeException | Error e) {
				outerLock.close();
				throw e;
			}
			if (innerLock == null) {
				outerLock.close();
				if (tryOnly)
					return null;
				try {
					Thread.sleep(2);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				continue;
			}
			return Transaction.and(outerLock, innerLock);
		}
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
		public Transaction lock(boolean tryOnly) {
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

	/** Implements all the {@link Lockable#lockAll(boolean Lockable...)} methods */
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
			return ThreadConstrained.getThreadConstraint(theFirst, theLocks.get(), FunctionUtils.identity());
		}

		@Override
		public Transaction lock(boolean tryOnly) {
			return lockAll(theFirst, theLocks, l -> l, tryOnly);
		}

		@Override
		public Lockable.CoreId getCoreId() {
			// Best we can do here is capture a snapshot
			Lockable.CoreId cores = theFirst.getCoreId();
			try (Transaction t = theFirst.lock(false)) {
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
		public Transaction lock(boolean tryOnly) {
			if (!theThreadConstraint.isEventThread())
				throw new IllegalStateException(WRONG_THREAD_MESSAGE);
			return Lockable.lock(theLock, tryOnly, theDebugInfo);
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
		public Transaction lock(boolean tryOnly) {
			if (isWrite && !theThreadConstraint.isEventThread())
				throw new UnsupportedOperationException(WRONG_THREAD_MESSAGE);
			else
				return Lockable.lock(theLock, isWrite, tryOnly, theDebugInfo);
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
