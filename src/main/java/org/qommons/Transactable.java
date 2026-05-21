package org.qommons;

import static org.qommons.Transactable.asWriteLockable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.collect.OptimisticContext;
import org.qommons.fn.FunctionUtils;

/**
 * <p>
 * Represents a mutable object whose modifications may possibly be batched for increased efficiency.
 * </p>
 * <p>
 * For a {@link Transactable}, the {@link Lockable#lock(boolean)} methods represent the ability to obtain a non-exclusive transaction during
 * which exclusive locks cannot be obtained, allowing safe, stateful inspections.
 * </p>
 * 
 * As with {@link Lockable} Some implementations of this interface may not actually support locking. Such implementations should return a
 * {@link Transaction#NONE none} transaction or some such non-null transaction.
 */
public interface Transactable extends Lockable {
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
	 * Obtains an exclusive lock in which modifications to this object may be batched and combined for increased efficiency. If any lock
	 * (exclusive or not) is held by another thread this method will either return null (if<code>tryOnly</code> is true) or block until it
	 * is able to obtain a lock (if <code>tryOnly</code> is false).Either an exclusive transaction in which modifications to this object may
	 * be batched and combined for increased efficiency, or a non-exclusive transaction during which exclusive locks cannot be obtained,
	 * allowing safe, stateful inspections.
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
	Transaction lockWrite(boolean tryOnly, Object cause);

	static Lockable asWriteLockable(Transactable transactable, Object cause) {
		if (transactable == null)
			return Lockable.NONE;
		return new WriteLockable(transactable, cause);
	}

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
	 * <li>Copy the set of fields it needs into local variables</li>
	 * <li>Check the stamp to ensure that the copied values are consistent</li>
	 * <li>Perform one or more operations on the local variables which only affect local variables declared in the scope, checking the stamp
	 * in between operations to continuously ensure consistency and to avoid unnecessary work</li>
	 * <li>Compile and return a value that can be used outside the operation scope</li>
	 * </ol>
	 * The default implementation of this method does not do anything optimistically, but merely performs the operation inside of a read
	 * lock. Implementations that support optimism may override this method.
	 * 
	 * @param <T> The type of value produced by the operation
	 * @param init The initial value to feed to the operation
	 * @param operation The operation to perform
	 * @return The result of the operation
	 */
	default <T> T doOptimistically(T init, OptimisticOperation<T> operation) {
		// Optimism is not supported by default
		try (Transaction t = lock(false)) {
			return operation.apply(init, OptimisticContext.TRUE);
		}
	}

	/**
	 * Same as {@link #doOptimistically(Object, OptimisticOperation)} but for a primitive integer, to avoid wrapping/unwrapping since this
	 * is a common use-case
	 * 
	 * @param init The initial value to feed to the operation
	 * @param operation The operation to perform
	 * @return The result of the operation
	 */
	default int doOptimistically(int init, OptimisticIntOperation operation) {
		// Optimism is not supported by default
		try (Transaction t = lock(false)) {
			return operation.apply(init, OptimisticContext.TRUE);
		}
	}

	/**
	 * Locks the transactable if it is one
	 * 
	 * @param lockable The (possibly) transactable object to lock
	 * @param cause The cause of the lock
	 * @return The transaction to use to unlock the object
	 */
	static Transaction lockWrite(Object lockable, boolean tryOnly, Object cause) {
		if (lockable instanceof Transactable)
			return ((Transactable) lockable).lockWrite(tryOnly, cause);
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
	 * CoreId}.{@link Lockable.CoreId#intersects(CoreId) intersects(CoreId)} , and if so attempts the obvious first, but only with a
	 * {@link Transactable#tryLock(boolean, Object) tryLock} operation on the inner transactable. If this fails, the outer read-only lock is
	 * released, and a write lock is obtained on both transactables.
	 * </p>
	 * 
	 * @param owner The owner of the target transactable to lock for write
	 * @param lock Supplies the target transactable after the owner is locked
	 * @param cause The cause of the write lock
	 * @return A transaction to release the locks
	 */
	static Transaction writeLockWithOwner(Transactable owner, Supplier<Transactable> lock, boolean tryOnly, Object cause) {
		Transaction ownerT = owner.lock(tryOnly);
		if (ownerT == null)
			return null;
		boolean success = false, ownerTLocked = true;
		try {
			Transactable realLock = lock.get();
			if (realLock == null) {
				success = true;
				return ownerT;
			} else {
				Transaction innerT;
				if (!realLock.getCoreId().intersects(owner.getCoreId())) {
					innerT = realLock.lockWrite(tryOnly, cause);
					if (innerT == null) {
						ownerT.close();
						return null;
					}
				} else
					innerT = realLock.lockWrite(true, cause);
				if (innerT != null) {
					success = true;
					return () -> {
						innerT.close();
						ownerT.close();
					};
				}
				ownerTLocked = false;
				ownerT.close();
				// That didn't work, but it may be because the inner and outer locks share a core
				// that can't be write-locked (with the inner lock) after being read-locked (by the outer lock).
				// See if we can successfully obtain the lock by locking the outer lock for write instead.
				Transaction ownerT2 = owner.lockWrite(tryOnly, cause);
				if (ownerT2 == null)
					return null;
				realLock = lock.get();
				if (realLock == null) {
					success = true;
					return ownerT2;
				}
				Transaction innerT2;
				try {
					innerT2 = realLock.lockWrite(false, cause);
					success = innerT2 != null;
				} finally {
					if (!success) {
						ownerT2.close();
						return null;
					}
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
	 * Like {@link #writeLockWithOwner(Transactable, Supplier, boolean, Object)}, but for the case where the owner cannot be locked for
	 * write directly.
	 * 
	 * @param owner The owner of the target transactable to lock for write
	 * @param lock Supplies the target transactable after the owner is locked
	 * @param cause The cause of the write lock
	 * @return A transaction to release the locks, or null if the lock cannot be obtained
	 */
	static Transaction writeLockWithOwner(Lockable owner, Supplier<Transactable> lock, boolean tryOnly, Object cause) {
		Transaction ownerT = owner.lock(tryOnly);
		if (ownerT == null)
			return null;
		boolean success = false, ownerTLocked = true;
		try {
			Transactable realLock = lock.get();
			if (realLock == null) {
				success = true;
				return ownerT;
			} else {
				Transaction innerT;
				if (!realLock.getCoreId().intersects(owner.getCoreId())) {
					innerT = realLock.lockWrite(tryOnly, cause);
					if (innerT == null) {
						ownerT.close();
						return null;
					}
				} else
					innerT = realLock.lockWrite(true, cause);
				if (innerT != null) {
					success = true;
					return () -> {
						innerT.close();
						ownerT.close();
					};
				}
				ownerTLocked = false;
				ownerT.close();
				Transaction innerT2 = realLock.lockWrite(tryOnly, cause);
				if (innerT2 == null)
					return null;
				Transaction ownerT2;
				try {
					ownerT2 = owner.lock(tryOnly);
					success = ownerT2 != null;
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
	static Transactable transactable(ReentrantReadWriteLock lock, ThreadConstraint constraint) {
		return new RRWLTransactable(lock, constraint);
	}

	/**
	 * Combines the transactables into a single transactable that locks all of them safely
	 * 
	 * @param transactables The transactables to lock collectively
	 * @return The combined transactable
	 */
	static Transactable combine(Collection<? extends Transactable> transactables) {
		return new CombinedTransactable<>(null, FunctionUtils.constantSupplier(transactables, transactables::toString, null),
			FunctionUtils.identity(), true);
	}

	/**
	 * Combines the transactables into a single transactable that locks all of them safely
	 * 
	 * @param first The first transactable to lock collectively
	 * @param others The other transactables to lock collectively
	 * @return The combined transactable
	 */
	static Transactable combine(Transactable first, Transactable... others) {
		return new CombinedTransactable<>(first, FunctionUtils.constantSupplier(Arrays.asList(others), () -> Arrays.toString(others), null),
			FunctionUtils.identity(), true);
	}

	/**
	 * Combines the transactables into a single transactable that locks all of them safely
	 * 
	 * @param first The first transactable to lock collectively
	 * @param others The other transactables to lock collectively
	 * @return The combined transactable
	 */
	static Transactable combine(Transactable first, Collection<? extends Transactable> others) {
		return new CombinedTransactable<>(first, FunctionUtils.constantSupplier(others, others::toString, others), FunctionUtils.identity(),
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

	/** Implements {@link Lockable#lockable(Transactable)} */
	static class WriteLockable implements Lockable {
		private final Transactable theTransactable;
		private final Object cause;

		public WriteLockable(Transactable transactable, Object cause) {
			theTransactable = transactable;
			this.cause = cause;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theTransactable.getThreadConstraint();
		}

		@Override
		public Transaction lock(boolean tryOnly) {
			return theTransactable.lockWrite(tryOnly, cause);
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
			return obj instanceof WriteLockable && theTransactable.equals(((WriteLockable) obj).theTransactable)//
				&& Objects.equals(cause, ((WriteLockable) obj).cause);
		}

		@Override
		public String toString() {
			return theTransactable.toString() + ".lockWrite()";
		}
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
		public Transaction lock(boolean tryOnly) {
			return Transaction.NONE;
		}

		@Override
		public Transaction lockWrite(boolean tryOnly, Object cause) {
			if (!theThreadConstraint.isEventThread()) {
				if (tryOnly)
					return null;
				throw new IllegalStateException(WRONG_THREAD_MESSAGE);
			}
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
		private final ThreadConstraint theThreadConstraint;

		RRWLTransactable(ReentrantReadWriteLock lock, ThreadConstraint threadConstraint) {
			if (lock == null || threadConstraint == null)
				throw new NullPointerException();
			theLock = lock;
			theThreadConstraint = threadConstraint;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theThreadConstraint;
		}

		@Override
		public Transaction lock(boolean tryOnly) {
			return Lockable.lock(theLock.readLock(), tryOnly);
		}

		@Override
		public Transaction lockWrite(boolean tryOnly, Object cause) {
			if (!theThreadConstraint.isEventThread()) {
				if (tryOnly)
					return null;
				throw new IllegalStateException(WRONG_THREAD_MESSAGE);
			}
			return Lockable.lock(theLock.writeLock(), tryOnly);
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
		public Transaction lock(boolean tryOnly) {
			return Lockable.lockAll(theFirst, theOthers, theMap, tryOnly);
		}

		@Override
		public Transaction lockWrite(boolean tryOnly, Object cause) {
			return Lockable.lockAll(asWriteLockable(theFirst, cause), //
				theOthers, x -> asWriteLockable(theMap.apply(x), cause), tryOnly);
		}

		@Override
		public Lockable.CoreId getCoreId() {
			return Lockable.getCoreId(theFirst, theOthers, theMap);
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
		public Transaction lock(boolean tryOnly) {
			return theLockable.lock(tryOnly);
		}

		@Override
		public Transaction lockWrite(boolean tryOnly, Object cause) {
			return theLockable.lock(tryOnly);
		}

		@Override
		public CoreId getCoreId() {
			return theLockable.getCoreId();
		}
	}

	/** A simple {@link OptimisticContext} for a {@link Stamped} subject */
	static class StampedContext implements OptimisticContext {
		private final Stamped theSubject;
		private long theStamp;
		private boolean failed;

		public StampedContext(Stamped subject) {
			theSubject = subject;
			theStamp = theSubject.getStamp();
		}

		public boolean isValidOrReset() {
			long stamp = theSubject.getStamp();
			if (theStamp == stamp)
				return true;
			else {
				theStamp = stamp;
				failed = false;
				return false;
			}
		}

		@Override
		public boolean isOperationValid() {
			if (failed)
				return false;
			else if (theSubject.getStamp() == theStamp)
				return true;
			failed = true;
			return false;
		}
	}
}
