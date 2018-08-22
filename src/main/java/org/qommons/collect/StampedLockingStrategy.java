package org.qommons.collect;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;

import org.qommons.Transaction;

/** A collection-locking strategy using {@link StampedLock} */
public class StampedLockingStrategy implements CollectionLockingStrategy {
	private final ThreadLocal<ThreadState> theStampCollection;
	private final StampedLock theStructureLocker;
	private final StampedLock theUpdateLocker;
	private final AtomicLong theModCount;
	private final AtomicLong theStructureChanges;
	private int optimisticTries;

	/** Creates the locking strategy */
	public StampedLockingStrategy() {
		this(1);
	}

	public StampedLockingStrategy(int optimisticTries) {
		this(new StampedLock(), optimisticTries);
	}

	public StampedLockingStrategy(StampedLock lock, int optimisticTries) {
		theStampCollection = new ThreadLocal<ThreadState>() {
			@Override
			protected ThreadState initialValue() {
				return new ThreadState();
			}
		};
		theStructureLocker = new StampedLock();
		theUpdateLocker = new StampedLock();
		theModCount = new AtomicLong(0);
		theStructureChanges = new AtomicLong(0);
		this.optimisticTries = optimisticTries;
	}

	@Override
	public boolean isLockSupported() {
		return true;
	}

	@Override
	public Transaction lock(boolean write, boolean structural, Object cause) {
		return theStampCollection.get().obtain(write, structural);
	}

	@Override
	public long getStamp(boolean structural) {
		return structural ? theStructureChanges.get() : theModCount.get();
	}

	@Override
	public <T> T doOptimistically(T init, OptimisticOperation<T> operation, boolean allowUpdate) {
		if (optimisticTries == 0)
			try (Transaction t = lock(false, allowUpdate, null)) {
				return operation.apply(init, OptimisticContext.TRUE);
			}
		T res = init;
		long[] structStamp = new long[] { theStructureLocker.tryOptimisticRead() };
		long[] updateStamp = allowUpdate ? null : new long[] { theUpdateLocker.tryOptimisticRead() };
		boolean[] keepTrying = new boolean[] { true };
		for (int i = 0; keepTrying[0] && structStamp[0] != 0 && (allowUpdate || updateStamp[0] != 0) && i < optimisticTries; i++) {
			keepTrying[0] = false;
			res = operation.apply(res, //
				() -> {
					if (!theStructureLocker.validate(structStamp[0]) || (!allowUpdate && !theUpdateLocker.validate(updateStamp[0]))) {
						keepTrying[0] = true;
						structStamp[0] = theStructureLocker.tryOptimisticRead();
						if (allowUpdate)
							updateStamp[0] = theUpdateLocker.tryOptimisticRead();
						return false;
					}
					return true;
				});
		}
		if (keepTrying[0]) {
			try (Transaction t = lock(false, allowUpdate, null)) {
				res = operation.apply(init, OptimisticContext.TRUE);
			}
		}
		return res;
	}

	@Override
	public int doOptimistically(int init, OptimisticIntOperation operation, boolean allowUpdate) {
		if (optimisticTries == 0)
			try (Transaction t = lock(false, allowUpdate, null)) {
				return operation.apply(init, OptimisticContext.TRUE);
			}
		int res = init;
		long[] structStamp = new long[] { theStructureLocker.tryOptimisticRead() };
		long[] updateStamp = allowUpdate ? null : new long[] { theUpdateLocker.tryOptimisticRead() };
		boolean[] keepTrying = new boolean[] { true };
		for (int i = 0; keepTrying[0] && structStamp[0] != 0 && (allowUpdate || updateStamp[0] != 0) && i < optimisticTries; i++) {
			keepTrying[0] = false;
			res = operation.apply(res, //
				() -> {
					if (!theStructureLocker.validate(structStamp[0]) || (!allowUpdate && !theUpdateLocker.validate(updateStamp[0]))) {
						keepTrying[0] = true;
						structStamp[0] = theStructureLocker.tryOptimisticRead();
						if (allowUpdate)
							updateStamp[0] = theUpdateLocker.tryOptimisticRead();
						return false;
					}
					return true;
				});
		}
		if (keepTrying[0]) {
			try (Transaction t = lock(false, allowUpdate, null)) {
				res = operation.apply(init, OptimisticContext.TRUE);
			}
		}
		return res;
	}

	static class LockStamp {
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
					stamp = newStamp;
					return () -> {
						stamp = locker.tryConvertToReadLock(stamp);
						this.write = false;
					};
				} else
					return Transaction.NONE;
			} else {
				stamp = forWrite ? locker.writeLock() : locker.readLock();
				this.write = forWrite;
				return () -> {
					if (forWrite)
						locker.unlockWrite(stamp);
					else
						locker.unlockRead(stamp);
					stamp = 0;
				};
			}
		}
	}

	class ThreadState {
		private final LockStamp structureStamp;
		private final LockStamp updateStamp;

		ThreadState() {
			structureStamp = new LockStamp();
			updateStamp = new LockStamp();
		}

		Transaction obtain(boolean write, boolean structural) {
			if (write) {
				if (structural && structureStamp.stamp == 0) {
					theStructureChanges.getAndIncrement();
					theModCount.getAndIncrement();
				} else if (updateStamp.stamp == 0)
					theModCount.getAndIncrement();
			}
			Transaction structTrans = structureStamp.lock(theStructureLocker, write && structural);
			Transaction updateTrans;
			try {
				if (structural && !write)
					updateTrans = Transaction.NONE;// Allow update operations concurrent with the read
				else
					updateTrans = updateStamp.lock(theUpdateLocker, write);
			} catch (RuntimeException e) {
				// If the update lock fails, leave the lock in the state it was in previous to this method call
				structTrans.close();
				throw e;
			}
			return () -> {
				updateTrans.close();
				structTrans.close();
			};
		}
	}
}
