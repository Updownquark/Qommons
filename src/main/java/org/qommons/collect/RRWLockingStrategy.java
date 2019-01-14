package org.qommons.collect;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.qommons.Transaction;

public class RRWLockingStrategy implements CollectionLockingStrategy {
	private final ReentrantReadWriteLock theLock;
	private volatile long theStamp;

	public RRWLockingStrategy() {
		this(new ReentrantReadWriteLock());
	}

	public RRWLockingStrategy(ReentrantReadWriteLock lock) {
		theLock = lock;
	}

	@Override
	public boolean isLockSupported() {
		return true;
	}

	@Override
	public Transaction lock(boolean write, boolean structural, Object cause) {
		Lock lock = write ? theLock.writeLock() : theLock.readLock();
		lock.lock();
		theStamp++;
		return lock::unlock;
	}

	@Override
	public Transaction tryLock(boolean write, boolean structural, Object cause) {
		Lock lock = write ? theLock.writeLock() : theLock.readLock();
		if (lock.tryLock()) {
			theStamp++;
			return lock::unlock;
		} else
			return null;
	}

	@Override
	public long getStamp(boolean structural) {
		return theStamp;
	}

	@Override
	public <T> T doOptimistically(T init, OptimisticOperation<T> operation, boolean allowUpdate) {
		// Optimism not supported
		try (Transaction t = lock(false, allowUpdate, null)) {
			return operation.apply(init, OptimisticContext.TRUE);
		}
	}

	@Override
	public int doOptimistically(int init, OptimisticIntOperation operation, boolean allowUpdate) {
		// Optimism not supported
		try (Transaction t = lock(false, allowUpdate, null)) {
			return operation.apply(init, OptimisticContext.TRUE);
		}
	}
}
