package org.qommons.collect;

import java.util.concurrent.locks.StampedLock;

import org.qommons.Transactable;
import org.qommons.Transaction;

public class StampedLockingStrategy implements Transactable {
	private static final int MAX_OPTIMISTIC_TRIES = 3;
	@FunctionalInterface
	interface OptimisticOperation<T> {
		T apply(T init, long stamp);
	}
	private final ThreadLocal<ThreadState> theStampCollection;
	private final StampedLock theLocker;

	public StampedLockingStrategy() {
		theStampCollection = new ThreadLocal<ThreadState>() {
			@Override
			protected ThreadState initialValue() {
				return new ThreadState();
			}
		};
		theLocker = new StampedLock();
	}

	@Override
	public boolean isLockSupported() {
		return true;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		ThreadState state = theStampCollection.get();
		if (state.stamp > 0) {
			if (write && !state.isWrite) {
				// Alright, I'll try
				long stamp = theLocker.tryConvertToWriteLock(state.stamp);
				if (stamp == 0)
					throw new IllegalStateException("Could not upgrade to write lock");
				state.stamp = stamp; // Got lucky
			}
			return Transaction.NONE;
		} else {
			state.set(write ? theLocker.writeLock() : theLocker.readLock(), write);
			return () -> {
				if (write)
					theLocker.unlockWrite(state.stamp);
				else
					theLocker.unlockRead(state.stamp);
				state.stamp = 0;
			};
		}
	}

	public <T> T doOptimistically(T init, OptimisticOperation<T> operation) {
		ThreadState state = theStampCollection.get();
		if (state.stamp > 0) // Already holding a lock
			return operation.apply(init, state.stamp);
		T res = init;
		for (int rep = 0; rep < MAX_OPTIMISTIC_TRIES; rep++) {
			long stamp = theLocker.tryOptimisticRead();
			if (stamp == 0) // Write lock is taken. Wait for readability.
				break;
			res = operation.apply(res, stamp);
			if (theLocker.validate(stamp))
				return res;
		}
		try (Transaction t = lock(false, null)) {
			return operation.apply(res, state.stamp);
		}
	}

	public long getStamp() {
		return theLocker.tryOptimisticRead();
	}

	public boolean check(long stamp) {
		return theLocker.validate(stamp);
	}

	static class ThreadState {
		long stamp;
		boolean isWrite;

		void set(long stamp, boolean write) {
			this.stamp = stamp;
			isWrite = write;
		}
	}
}
