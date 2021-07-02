package org.qommons.collect;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;

import org.qommons.LockDebug;
import org.qommons.Lockable.CoreId;
import org.qommons.Transaction;

/** A collection-locking strategy using {@link StampedLock} */
public class StampedLockingStrategy implements CollectionLockingStrategy {
	private final Object theOwner;
	private final ThreadLocal<ThreadState> theStampCollection;
	private final StampedLock theUpdateLocker;
	private final AtomicLong theModCount;
	private int optimisticTries;

	/**
	 * If true, locking strategies of this type will keep a record of the thread that currently holds a write lock on them in
	 * {@link #updateWriteLocker}. This variable can be set at run time by setting the system property "qommons.locking.debug" to "true".
	 */
	@SuppressWarnings("javadoc")
	public static boolean STORE_WRITERS = "true".equalsIgnoreCase(System.getProperty("qommons.locking.debug")); // A debug setting
	volatile Thread updateWriteLocker;

	/**
	 * Creates the locking strategy
	 * 
	 * @param owner The owner of this lock, for debugging
	 */
	public StampedLockingStrategy(Object owner) {
		this(1, owner);
	}

	/**
	 * @param optimisticTries The number of optimistic tries to use in the optimistic methods before obtaining a lock
	 * @param owner The owner of this lock, for debugging
	 */
	public StampedLockingStrategy(int optimisticTries, Object owner) {
		this(new StampedLock(), optimisticTries, owner);
	}

	/**
	 * @param lock The stamped lock to use
	 * @param optimisticTries The number of optimistic tries to use in the optimistic methods before obtaining a lock
	 * @param owner The owner of this lock, for debugging
	 */
	public StampedLockingStrategy(StampedLock lock, int optimisticTries, Object owner) {
		theOwner = owner;
		theStampCollection = new ThreadLocal<ThreadState>() {
			@Override
			protected ThreadState initialValue() {
				return new ThreadState();
			}
		};
		theUpdateLocker = new StampedLock();
		theModCount = new AtomicLong(0);
		this.optimisticTries = optimisticTries;
	}

	@Override
	public boolean isLockSupported() {
		return true;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theStampCollection.get().obtain(write);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theStampCollection.get().tryObtain(write);
	}

	@Override
	public CoreId getCoreId() {
		return new CoreId(theUpdateLocker);
	}

	@Override
	public long getStamp() {
		return theModCount.get();
	}

	@Override
	public void modified() {
		theModCount.getAndIncrement();
	}

	@Override
	public <T> T doOptimistically(T init, OptimisticOperation<T> operation) {
		if (optimisticTries == 0)
			try (Transaction t = lock(false, null)) {
				return operation.apply(init, OptimisticContext.TRUE);
			}
		T res = init;
		long[] updateStamp = new long[] { theUpdateLocker.tryOptimisticRead() };
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
		if (optimisticTries == 0)
			try (Transaction t = lock(false, null)) {
				return operation.apply(init, OptimisticContext.TRUE);
			}
		int res = init;
		long[] updateStamp = new long[] { theUpdateLocker.tryOptimisticRead() };
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

	class LockStamp {
		long stamp;
		boolean write;

		Transaction lock(StampedLock locker, boolean forWrite) {
			if (stamp > 0) {
				if (forWrite && !this.write) {
					// Alright, I'll try
					long newStamp = locker.tryConvertToWriteLock(stamp);
					if (newStamp == 0)
						throw new IllegalStateException("Could not upgrade to write lock");
					// Got lucky
					lockedWrite();
					stamp = newStamp;
					return () -> {
						unlockedWrite();
						stamp = locker.tryConvertToReadLock(stamp);
						this.write = false;
					};
				} else
					return Transaction.NONE;
			} else {
				stamp = forWrite ? locker.writeLock() : locker.readLock();
				if (forWrite)
					lockedWrite();
				this.write = forWrite;
				return () -> {
					if (forWrite) {
						unlockedWrite();
						locker.unlockWrite(stamp);
					} else
						locker.unlockRead(stamp);
					stamp = 0;
				};
			}
		}

		Transaction tryLock(StampedLock locker, boolean forWrite) {
			if (stamp > 0) {
				if (forWrite && !this.write) {
					// Alright, I'll try
					long newStamp = locker.tryConvertToWriteLock(stamp);
					if (newStamp == 0)
						return null;
					// Got lucky
					lockedWrite();
					stamp = newStamp;
					return () -> {
						stamp = locker.tryConvertToReadLock(stamp);
						this.write = false;
					};
				} else
					return Transaction.NONE;
			} else {
				stamp = forWrite ? locker.tryWriteLock() : locker.tryReadLock();
				if (stamp == 0)
					return null;
				if (forWrite)
					lockedWrite();
				this.write = forWrite;
				return () -> {
					if (forWrite) {
						unlockedWrite();
						locker.unlockWrite(stamp);
					} else
						locker.unlockRead(stamp);
					stamp = 0;
				};
			}
		}

		private void lockedWrite() {
			if (STORE_WRITERS)
				updateWriteLocker = Thread.currentThread();
		}

		private void unlockedWrite() {
			if (STORE_WRITERS)
				updateWriteLocker = null;
		}
	}

	class ThreadState {
		private final LockStamp updateStamp;

		ThreadState() {
			updateStamp = new LockStamp();
		}

		Transaction obtain(boolean write) {
			return LockDebug.debug(theUpdateLocker, theOwner, write, false,
				() -> updateStamp.lock(theUpdateLocker, write));
		}

		Transaction tryObtain(boolean write) {
			Transaction updateTrans = LockDebug.debug(theUpdateLocker, theOwner, write, true,
				() -> updateStamp.tryLock(theUpdateLocker, write));
			return updateTrans;
		}
	}
}
