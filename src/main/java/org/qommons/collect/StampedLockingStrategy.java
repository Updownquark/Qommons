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

	/** Creates the locking strategy */
	public StampedLockingStrategy() {
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
	public long getStatus(boolean structural) {
		return structural ? theStructureChanges.get() : theModCount.get();
	}

	@Override
	public void changed(boolean structural) {
		theModCount.incrementAndGet();
		if (structural)
			theStructureChanges.incrementAndGet();
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
			boolean withStructLock = structural || !write;
			boolean withUpdateLock = !structural || write;

			Transaction structTrans = withStructLock ? structureStamp.lock(theStructureLocker, write) : Transaction.NONE;
			Transaction updateTrans = withUpdateLock ? updateStamp.lock(theUpdateLocker, write) : Transaction.NONE;
			return () -> {
				updateTrans.close();
				structTrans.close();
			};
		}
	}
}
