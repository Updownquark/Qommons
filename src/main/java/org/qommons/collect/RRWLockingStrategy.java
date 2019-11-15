package org.qommons.collect;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.qommons.Transactable;
import org.qommons.Transaction;

/** A locking strategy backed by a {@link ReentrantReadWriteLock} or, more generically, a {@link Transactable} */
public class RRWLockingStrategy implements CollectionLockingStrategy {
	private final Transactable theLock;
	private volatile long theStamp;

	/** Creates the locking strategy */
	public RRWLockingStrategy() {
		this(new ReentrantReadWriteLock());
	}

	/** @param lock The lock to use */
	public RRWLockingStrategy(ReentrantReadWriteLock lock) {
		this(Transactable.transactable(lock));
	}

	/** @param lock The lock to use */
	public RRWLockingStrategy(Transactable lock) {
		theLock = lock;
	}

	@Override
	public boolean isLockSupported() {
		return theLock.isLockSupported();
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
