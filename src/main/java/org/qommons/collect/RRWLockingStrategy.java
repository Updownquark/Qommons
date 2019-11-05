package org.qommons.collect;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.qommons.Transactable;
import org.qommons.Transaction;

public class RRWLockingStrategy implements CollectionLockingStrategy {
	private final Transactable theLock;
	private volatile long theStamp;

	public RRWLockingStrategy() {
		this(new ReentrantReadWriteLock());
	}

	public RRWLockingStrategy(ReentrantReadWriteLock lock) {
		this(Transactable.transactable(lock));
	}

	public RRWLockingStrategy(Transactable lock) {
		theLock = lock;
	}

	@Override
	public boolean isLockSupported() {
		return true;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		Transaction lock = theLock.lock(write, cause);
		theStamp++;
		return lock;
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		Transaction lock = theLock.tryLock(write, cause);
		if (lock != null)
			theStamp++;
		return lock;
	}

	@Override
	public long getStamp() {
		return theStamp;
	}

	@Override
	public <T> T doOptimistically(T init, OptimisticOperation<T> operation) {
		// Optimism not supported
		try (Transaction t = lock(false, null)) {
			return operation.apply(init, OptimisticContext.TRUE);
		}
	}

	@Override
	public int doOptimistically(int init, OptimisticIntOperation operation) {
		// Optimism not supported
		try (Transaction t = lock(false, null)) {
			return operation.apply(init, OptimisticContext.TRUE);
		}
	}
}
