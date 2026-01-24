package org.qommons.collect;

import java.util.Collection;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

import org.qommons.*;
import org.qommons.Lockable.CoreId;

/** A collection-locking strategy using {@link StampedLock} */
public class StampedLockingStrategy implements CollectionLockingStrategy {
	private final ThreadConstraint theThreadConstraint;
	private final CausalLock theCausalLock;
	final Object theOwner;
	private final ThreadLocal<ThreadState> theStampCollection;
	final StampedLock theUpdateLocker;
	private volatile long theModCount;
	private int optimisticTries;

	/**
	 * If true, locking strategies of this type will keep a record of the thread that currently holds a write lock on them in
	 * {@link #updateWriteLocker}. This variable can be set by initializing the system property "qommons.locking.debug" to "true".
	 */
	public static boolean STORE_WRITERS = "true".equalsIgnoreCase(System.getProperty("qommons.locking.debug")); // A debug setting
	volatile Thread updateWriteLocker;

	/**
	 * Creates the locking strategy
	 * 
	 * @param owner The owner of this lock, for debugging
	 * @param threadConstraint The thread constraint for this lock to obey
	 */
	public StampedLockingStrategy(Object owner, ThreadConstraint threadConstraint) {
		this(1, owner, threadConstraint);
	}

	/**
	 * @param optimisticTries The number of optimistic tries to use in the optimistic methods before obtaining a lock
	 * @param owner The owner of this lock, for debugging
	 * @param threadConstraint The thread constraint for this lock to obey
	 */
	public StampedLockingStrategy(int optimisticTries, Object owner, ThreadConstraint threadConstraint) {
		this(new StampedLock(), optimisticTries, owner, threadConstraint);
	}

	/**
	 * @param lock The stamped lock to use
	 * @param optimisticTries The number of optimistic tries to use in the optimistic methods before obtaining a lock
	 * @param owner The owner of this lock, for debugging
	 * @param threadConstraint The thread constraint for this lock to obey
	 */
	public StampedLockingStrategy(StampedLock lock, int optimisticTries, Object owner, ThreadConstraint threadConstraint) {
		theThreadConstraint = threadConstraint;
		theOwner = owner;
		theCausalLock = new DefaultCausalLock(new TransactableCore());
		theStampCollection = ThreadLocal.withInitial(ThreadState::new);
		theUpdateLocker = new StampedLock();
		this.optimisticTries = optimisticTries;
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
	public Transaction lock(boolean write, Object cause) {
		return theCausalLock.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theCausalLock.tryLock(write, cause);
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return theCausalLock.getCurrentCauses();
	}

	@Override
	public CoreId getCoreId() {
		return new CoreId(theUpdateLocker);
	}

	@Override
	public long getStamp() {
		return theModCount;
	}

	@Override
	public void modified() {
		theModCount++;
	}

	@Override
	public <T> T doOptimistically(T init, OptimisticOperation<T> operation) {
		if (optimisticTries == 0) {
			try (Transaction t = lock(false, null)) {
				return operation.apply(init, OptimisticContext.TRUE);
			}
		}
		T res = init;
		long[] updateStamp = new long[] { theUpdateLocker.tryOptimisticRead() };
		if (updateStamp[0] == 0) { // Write lock held
			ThreadState thread = theStampCollection.get();
			if (thread.write) // We might own it
				return operation.apply(init, OptimisticContext.TRUE);
			else { // Someone else owns it. Nothing to do but wait.
				try (Transaction t = thread.obtain(true, false)) {
					return operation.apply(init, OptimisticContext.TRUE);
				}
			}
		}

		boolean[] keepTrying = new boolean[] { true };
		for (int i = 0; keepTrying[0] && updateStamp[0] != 0 && i < optimisticTries; i++) {
			keepTrying[0] = false;
			res = operation.apply(res, //
				() -> {
					if (!theUpdateLocker.validate(updateStamp[0])) {
						keepTrying[0] = true;
						updateStamp[0] = theUpdateLocker.tryOptimisticRead();
						return false;
					}
					return true;
				});
		}
		if (keepTrying[0]) {
			try (Transaction t = lock(false, null)) {
				res = operation.apply(init, OptimisticContext.TRUE);
			}
		}
		return res;
	}

	@Override
	public int doOptimistically(int init, OptimisticIntOperation operation) {
		if (optimisticTries == 0) {
			try (Transaction t = lock(false, null)) {
				return operation.apply(init, OptimisticContext.TRUE);
			}
		}
		int res = init;
		long[] updateStamp = new long[] { theUpdateLocker.tryOptimisticRead() };
		if (updateStamp[0] == 0) { // Write lock held
			ThreadState thread = theStampCollection.get();
			if (thread.write) // We might own it
				return operation.apply(init, OptimisticContext.TRUE);
			else { // Someone else owns it. Nothing to do but wait.
				try (Transaction t = thread.obtain(true, false)) {
					return operation.apply(init, OptimisticContext.TRUE);
				}
			}
		}

		boolean[] keepTrying = new boolean[] { true };
		for (int i = 0; keepTrying[0] && updateStamp[0] != 0 && i < optimisticTries; i++) {
			keepTrying[0] = false;
			res = operation.apply(res, //
				() -> {
					if (!theUpdateLocker.validate(updateStamp[0])) {
						keepTrying[0] = true;
						updateStamp[0] = theUpdateLocker.tryOptimisticRead();
						return false;
					}
					return true;
				});
		}
		if (keepTrying[0]) {
			try (Transaction t = lock(false, null)) {
				res = operation.apply(init, OptimisticContext.TRUE);
			}
		}
		return res;
	}

	@Override
	public String toString() {
		return theOwner + " Stamped Lock";
	}

	class TransactableCore implements Transactable {
		@Override
		public ThreadConstraint getThreadConstraint() {
			return theThreadConstraint;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			if (write && !theThreadConstraint.isEventThread())
				throw new IllegalStateException(WRONG_THREAD_MESSAGE);
			return theStampCollection.get().obtain(write, false);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			if (write && !theThreadConstraint.isEventThread())
				throw new IllegalStateException(WRONG_THREAD_MESSAGE);
			return theStampCollection.get().obtain(write, true);
		}

		@Override
		public CoreId getCoreId() {
			return new CoreId(theUpdateLocker);
		}
	}

	class ThreadState {
		long stamp;
		boolean write;

		// Avoid creating so many lambdas all the time
		private final Supplier<Transaction> LOCK_READ = () -> lock(false, false);
		private final Supplier<Transaction> LOCK_WRITE = () -> lock(true, false);
		private final Supplier<Transaction> TRY_LOCK_READ = () -> lock(false, true);
		private final Supplier<Transaction> TRY_LOCK_WRITE = () -> lock(true, true);
		private final Transaction theReadUnlock = this::unlockRead;
		private final Transaction theWriteUnlock = this::unlockWrite;
		private final Transaction theWriteDowngrade = this::writeToRead;

		Transaction obtain(boolean forWrite, boolean justTry) {
			Supplier<Transaction> core;
			if (justTry)
				core = forWrite ? TRY_LOCK_WRITE : TRY_LOCK_READ;
			else
				core = forWrite ? LOCK_WRITE : LOCK_READ;
			return LockDebug.debug(theUpdateLocker, theOwner, forWrite, false, core);
		}

		Transaction lock(boolean forWrite, boolean justTry) {
			if (stamp > 0) {
				if (forWrite && !this.write) {
					// We have a read lock
					// Alright, I'll try
					long newStamp = theUpdateLocker.tryConvertToWriteLock(stamp);
					if (newStamp == 0) {
						if (justTry)
							return null;
						else
							throw new IllegalStateException("Could not upgrade to write lock");
					}
					// Got lucky
					lockedWrite();
					stamp = newStamp;
					this.write = true;
					return theWriteDowngrade;
				} else // Already have what we need
					return Transaction.NONE;
			} else {
				if (justTry) {
					stamp = forWrite ? theUpdateLocker.tryWriteLock() : theUpdateLocker.tryReadLock();
					if (stamp == 0)
						return null;
				} else
					stamp = forWrite ? theUpdateLocker.writeLock() : theUpdateLocker.readLock();
				if (forWrite)
					lockedWrite();
				this.write = forWrite;
				return forWrite ? theWriteUnlock : theReadUnlock;
			}
		}

		private void lockedWrite() {
			if (STORE_WRITERS)
				updateWriteLocker = Thread.currentThread();
		}

		private void unlockRead() {
			theUpdateLocker.unlockRead(stamp);
			stamp = 0;
		}

		private void unlockWrite() {
			if (STORE_WRITERS)
				updateWriteLocker = null;
			theUpdateLocker.unlockWrite(stamp);
			stamp = 0;
		}

		private void writeToRead() {
			if (STORE_WRITERS)
				updateWriteLocker = null;
			stamp = theUpdateLocker.tryConvertToReadLock(stamp);
			this.write = false;
		}
	}
}
