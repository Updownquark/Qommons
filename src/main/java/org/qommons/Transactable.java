package org.qommons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.Lockable.CoreId;

/**
 * Represents a mutable object whose modifications may possibly be batched for increased efficiency.
 * 
 * Some interfaces may extend this interface, but support implementations that do not support locking. Hence the {@link #isLockSupported()}.
 * Such implementations should return a {@link Transaction#NONE none} transaction or some such non-null transaction.
 */
public interface Transactable extends ThreadConstrained {
	/**
	 * A do-nothing transactable that always returns {@link Transaction#NONE} and has no thread constraint ({@link ThreadConstraint#ANY
	 * ANY})
	 */
	static Transactable NONE = noLock(ThreadConstraint.ANY);

	/**
	 * A do-nothing transactable that always returns {@link Transaction#NONE} and represents an eventable that cannot fire events
	 * ({@link ThreadConstraint#NONE NONE})
	 */
	static Transactable IMMUTABLE = noLock(ThreadConstraint.NONE);

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

	/** @return A {@link CoreId} object containing all true locking cores used by this Transactable */
	Lockable.CoreId getCoreId();

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
	 * <p>
	 * Attempts to secure a write lock on a transactable that is owned by a lockable structure.
	 * </p>
	 * <p>
	 * The obvious way of doing this is to just obtain a read lock on the owner, retrieve the target transactable, and lock it for write.
	 * But this may cause problems if both locks have a common source, because it is not always possible to upgrade a lock from read-only to
	 * write mode.
	 * </p>
	 * <p>
	 * This method attempts to detect the relation between the two locks (via {@link Transactable#getCoreId()
	 * CoreId}.{@link CoreId#intersects(CoreId) intersects(CoreId)} , and if so attempts the obvious first, but only with a
	 * {@link Transactable#tryLock(boolean, Object) tryLock} operation on the inner transactable. If this fails, the outer read-only lock is
	 * released, and a write lock is obtained on both transactables.
	 * </p>
	 * 
	 * @param owner The owner of the target transactable to lock for write
	 * @param lock Supplies the target transactable after the owner is locked
	 * @param cause The cause of the write lock
	 * @return A transaction to release the locks
	 */
	static Transaction writeLockWithOwner(Transactable owner, Supplier<Transactable> lock, Object cause) {
		Transaction ownerT = owner.lock(false, cause);
		boolean success = false, ownerTLocked = true;
		try {
			Transactable realLock = lock.get();
			if (realLock == null) {
				success = true;
				return ownerT;
			} else {
				Transaction innerT;
				if (!realLock.getCoreId().intersects(owner.getCoreId()))
					innerT = realLock.lock(true, cause);
				else
					innerT = realLock.tryLock(true, cause);
				if (innerT != null) {
					success = true;
					return () -> {
						innerT.close();
						ownerT.close();
					};
				}
				ownerTLocked = false;
				ownerT.close();
				Transaction ownerT2 = owner.lock(true, cause);
				realLock = lock.get();
				if (realLock == null) {
					success = true;
					return ownerT2;
				}
				Transaction innerT2;
				try {
					innerT2 = realLock.lock(true, cause);
					success = true;
				} finally {
					if (!success)
						ownerT2.close();
				}
				return () -> {
					innerT2.close();
					ownerT2.close();
				};
			}
		} finally {
			if (!success && ownerTLocked)
				ownerT.close();
		}
	}

	/**
	 * Like {@link #writeLockWithOwner(Transactable, Supplier, Object)}, but for the try-only case.
	 * 
	 * @param owner The owner of the target transactable to lock for write
	 * @param lock Supplies the target transactable after the owner is locked
	 * @param cause The cause of the write lock
	 * @return A transaction to release the locks, or null if the lock cannot be obtained
	 */
	static Transaction tryWriteLockWithOwner(Transactable owner, Supplier<Transactable> lock, Object cause) {
		Transaction ownerT = owner.tryLock(false, cause);
		if (ownerT == null)
			return null;
		boolean success = false, ownerTLocked = true;
		try {
			Transactable realLock = lock.get();
			if (realLock == null) {
				success = true;
				return ownerT;
			} else {
				Transaction innerT = realLock.tryLock(true, cause);
				if (innerT != null) {
					success = true;
					return () -> {
						innerT.close();
						ownerT.close();
					};
				} else if (!realLock.getCoreId().intersects(owner.getCoreId()))
					return null; // Let the finally block unlock ownerT
				ownerTLocked = false;
				ownerT.close();
				Transaction ownerT2 = owner.tryLock(true, cause);
				if (ownerT2 == null)
					return null;
				realLock = lock.get();
				if (realLock == null) {
					success = true;
					return ownerT2;
				}
				Transaction innerT2;
				try {
					innerT2 = realLock.tryLock(true, cause);
					if (innerT2 == null)
						return null; // Let the finally block unlock ownerT2
					success = true;
				} finally {
					if (!success)
						ownerT2.close();
				}
				return () -> {
					innerT2.close();
					ownerT2.close();
				};
			}
		} finally {
			if (!success && ownerTLocked)
				ownerT.close();
		}
	}

	/**
	 * Like {@link #writeLockWithOwner(Transactable, Supplier, Object)}, but for the case where the owner cannot be locked for write
	 * directly.
	 * 
	 * @param owner The owner of the target transactable to lock for write
	 * @param lock Supplies the target transactable after the owner is locked
	 * @param cause The cause of the write lock
	 * @return A transaction to release the locks, or null if the lock cannot be obtained
	 */
	static Transaction writeLockWithOwner(Lockable owner, Supplier<Transactable> lock, Object cause) {
		Transaction ownerT = owner.lock();
		boolean success = false, ownerTLocked = true;
		try {
			Transactable realLock = lock.get();
			if (realLock == null) {
				success = true;
				return ownerT;
			} else {
				Transaction innerT;
				if (!realLock.getCoreId().intersects(owner.getCoreId()))
					innerT = realLock.lock(true, cause);
				else
					innerT = realLock.tryLock(true, cause);
				if (innerT != null) {
					success = true;
					return () -> {
						innerT.close();
						ownerT.close();
					};
				}
				ownerTLocked = false;
				ownerT.close();
				Transaction innerT2 = realLock.lock(true, cause);
				Transaction ownerT2;
				try {
					ownerT2 = owner.lock();
					success = true;
				} finally {
					if (!success)
						innerT2.close();
				}
				return () -> {
					ownerT2.close();
					innerT2.close();
				};
			}
		} finally {
			if (!success && ownerTLocked)
				ownerT.close();
		}
	}

	/**
	 * Like {@link #writeLockWithOwner(Transactable, Supplier, Object)}, but for the try-only case where the owner cannot be locked for
	 * write directly.
	 * 
	 * @param owner The owner of the target transactable to lock for write
	 * @param lock Supplies the target transactable after the owner is locked
	 * @param cause The cause of the write lock
	 * @return A transaction to release the locks, or null if the lock cannot be obtained
	 */
	static Transaction tryWriteLockWithOwner(Lockable owner, Supplier<Transactable> lock, Object cause) {
		Transaction ownerT = owner.tryLock();
		if (ownerT == null)
			return null;
		boolean success = false, ownerTLocked = true;
		try {
			Transactable realLock = lock.get();
			if (realLock == null) {
				success = true;
				return ownerT;
			} else {
				Transaction innerT = realLock.tryLock(true, cause);
				if (innerT != null) {
					success = true;
					return () -> {
						innerT.close();
						ownerT.close();
					};
				} else if (!realLock.getCoreId().intersects(owner.getCoreId()))
					return null; // Let the finally block unlock ownerT
				ownerTLocked = false;
				ownerT.close();
				Transaction innerT2 = realLock.tryLock(true, cause);
				if (innerT2 == null)
					return null;
				Transaction ownerT2;
				try {
					ownerT2 = owner.tryLock();
					if (ownerT2 == null)
						return null; // Let the finally block unlock innerT2
					success = true;
				} finally {
					if (!success)
						innerT2.close();
				}
				return () -> {
					ownerT2.close();
					innerT2.close();
				};
			}
		} finally {
			if (!success && ownerTLocked)
				ownerT.close();
		}
	}

	/**
	 * Gets the core ID for a set of transactables
	 * 
	 * @param lockables The transactables
	 * @return A CoreId containing core information about all transactables
	 */
	static CoreId getCoreId(Transactable... lockables) {
		return getCoreId(Arrays.asList(lockables));
	}

	/**
	 * Gets the core ID for a set of transactables
	 * 
	 * @param lockables The transactables
	 * @return A CoreId containing core information about all transactables
	 */
	static CoreId getCoreId(Collection<? extends Transactable> lockables) {
		CoreId first = null;
		List<CoreId> others = null;
		for (Transactable lockable : lockables) {
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
	 * Gets the core ID for a couple of transactables
	 * 
	 * @param outer The first transactable
	 * @param lockables Potentially produces another transactable after the first transactable is locked
	 * @return A CoreId containing core information about both transactables
	 */
	static CoreId getCoreId(Transactable outer, Collection<? extends Transactable> lockables) {
		return getCoreId(outer, () -> lockables, l -> l);
	}

	/**
	 * Gets the core ID for a set of transactables
	 * 
	 * @param <X> The type of transactable structures
	 * @param outer The first transactable
	 * @param lockables The additional transactable structures
	 * @param map The map to produce transactables from each item in the list
	 * @return A CoreId containing core information about all given transactables
	 */
	static <X> CoreId getCoreId(Transactable outer, Supplier<? extends Collection<? extends X>> lockables,
		Function<? super X, ? extends Transactable> map) {
		Transaction outerLock;
		if (outer != null) {
			outerLock = outer.tryLock(false, null);
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
				Transactable lock = map.apply(other);
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
	 * @param constraint The thread constraint for the transactable to obey
	 * @return A {@link Transactable} backed by the lock
	 */
	static Transactable transactable(ReentrantReadWriteLock lock, Object debugInfo, ThreadConstraint constraint) {
		return new RRWLTransactable(lock, debugInfo, constraint);
	}

	/**
	 * Combines the transactables into a single transactable that locks all of them safely
	 * 
	 * @param transactables The transactables to lock collectively
	 * @return The combined transactable
	 */
	static Transactable combine(Collection<? extends Transactable> transactables) {
		return new CombinedTransactable<>(null, LambdaUtils.constantSupplier(transactables, transactables::toString, null),
			LambdaUtils.identity(), true);
	}

	/**
	 * Combines the transactables into a single transactable that locks all of them safely
	 * 
	 * @param first The first transactable to lock collectively
	 * @param others The other transactables to lock collectively
	 * @return The combined transactable
	 */
	static Transactable combine(Transactable first, Transactable... others) {
		return new CombinedTransactable<>(first, LambdaUtils.constantSupplier(Arrays.asList(others), () -> Arrays.toString(others), null),
			LambdaUtils.identity(), true);
	}

	/**
	 * Combines the transactables into a single transactable that locks all of them safely
	 * 
	 * @param first The first transactable to lock collectively
	 * @param others The other transactables to lock collectively
	 * @return The combined transactable
	 */
	static Transactable combine(Transactable first, Collection<? extends Transactable> others) {
		return new CombinedTransactable<>(first, LambdaUtils.constantSupplier(others, others::toString, others), LambdaUtils.identity(),
			true);
	}

	/**
	 * Combines the transactables into a single transactable that locks all of them safely
	 * 
	 * @param <X> The type of transactable to lock
	 * @param first The first transactable to lock collectively
	 * @param others The other transactables to lock collectively
	 * @param map The function to supply a Transactable for each non-null item among <code>first</code> and <code>others</code>
	 * @param constant Whether the <code>others</code> and <code>map</code> parameters always return the same values with the same input
	 * @return The combined transactable
	 */
	static <X> Transactable combine(Transactable first, Supplier<? extends Collection<? extends X>> others,
		Function<? super X, ? extends Transactable> map) {
		return new CombinedTransactable<>(first, others, map, false);
	}

	/**
	 * @param lockable The lockable
	 * @return A transactable that locks the lockable
	 */
	static Transactable transactable(Lockable lockable) {
		return new LockableTransactable(lockable);
	}

	/**
	 * @param threadConstraint The thread constraint for the transactable to obey
	 * @return A transactable that obeys the given thread constraint but provides no thread safety and always returns
	 *         {@link Transaction#NONE}
	 */
	static Transactable noLock(ThreadConstraint threadConstraint) {
		return new NullTransactable(threadConstraint);
	}

	/** Implements {@link Transactable#noLock(ThreadConstraint)} */
	static class NullTransactable implements Transactable {
		private final ThreadConstraint theThreadConstraint;

		public NullTransactable(ThreadConstraint threadConstraint) {
			theThreadConstraint = threadConstraint;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theThreadConstraint;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			if (write && !theThreadConstraint.isEventThread())
				throw new UnsupportedOperationException(WRONG_THREAD_MESSAGE);
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			if (write && !theThreadConstraint.isEventThread())
				throw new UnsupportedOperationException(WRONG_THREAD_MESSAGE);
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
			return obj instanceof NullTransactable && theThreadConstraint.equals(((NullTransactable) obj).theThreadConstraint);
		}

		@Override
		public String toString() {
			if (theThreadConstraint == ThreadConstraint.NONE)
				return "IMMUTABLE";
			else if (theThreadConstraint == ThreadConstraint.ANY)
				return "NONE";
			else
				return "transactable(" + theThreadConstraint + ")";
		}
	}

	/** Implements {@link Transactable#transactable(ReentrantReadWriteLock, Object, ThreadConstraint)} */
	static class RRWLTransactable implements Transactable {
		private final ReentrantReadWriteLock theLock;
		private final Object theDebugInfo;
		private final ThreadConstraint theThreadConstraint;

		RRWLTransactable(ReentrantReadWriteLock lock, Object debugInfo, ThreadConstraint threadConstraint) {
			theLock = lock;
			theDebugInfo = debugInfo;
			theThreadConstraint = threadConstraint;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theThreadConstraint;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			if (write && !theThreadConstraint.isEventThread())
				throw new UnsupportedOperationException(WRONG_THREAD_MESSAGE);
			return Lockable.lock(theLock, theDebugInfo, write);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			if (write && !theThreadConstraint.isEventThread())
				throw new UnsupportedOperationException(WRONG_THREAD_MESSAGE);
			return Lockable.tryLock(theLock, theDebugInfo, write);
		}

		@Override
		public Lockable.CoreId getCoreId() {
			return new Lockable.CoreId(theLock);
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
		private final boolean isConstant;

		public CombinedTransactable(Transactable first, Supplier<? extends Collection<? extends X>> others,
			Function<? super X, ? extends Transactable> map, boolean constant) {
			theFirst = first;
			theOthers = others;
			theMap = map;
			isConstant = constant;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			if (!isConstant)
				return ThreadConstraint.NONE; // Can't know
			return ThreadConstrained.getThreadConstraint(theFirst, theOthers.get(), theMap);
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
		public Lockable.CoreId getCoreId() {
			// Best we can do here is capture a snapshot
			Lockable.CoreId cores = theFirst.getCoreId();
			try (Transaction t = theFirst.lock(false, null)) {
				Collection<? extends X> others = theOthers.get();
				if (others != null) {
					Lockable.CoreId[] otherCores = new Lockable.CoreId[others.size()];
					int i = 0;
					for (X other : others) {
						Transactable otherT = theMap.apply(other);
						if (otherT != null)
							otherCores[i++] = otherT.getCoreId();
					}
					cores = cores.and(otherCores);
				}
			}
			return cores;
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
		public ThreadConstraint getThreadConstraint() {
			return theLockable.getThreadConstraint();
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

		@Override
		public CoreId getCoreId() {
			return theLockable.getCoreId();
		}
	}
}
