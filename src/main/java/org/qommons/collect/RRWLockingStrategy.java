package org.qommons.collect;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.qommons.Lockable.CoreId;
import org.qommons.Transactable;
import org.qommons.Transaction;

/** A locking strategy backed by a {@link ReentrantReadWriteLock} or, more generically, a {@link Transactable} */
public class RRWLockingStrategy implements CollectionLockingStrategy {
	private final Transactable theLock;
	private volatile long theStamp;

	/**
	 * Creates the locking strategy
	 * 
	 * @param owner The owner of the lock, for debugging
	 */
	public RRWLockingStrategy(Object owner) {
		this(new ReentrantReadWriteLock(), owner);
	}

	/**
	 * @param lock The lock to use
	 * @param owner The owner of the lock, for debugging
	 */
	public RRWLockingStrategy(ReentrantReadWriteLock lock, Object owner) {
		this(Transactable.transactable(lock, owner));
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
		return lock;
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		Transaction lock = theLock.tryLock(write, cause);
		return lock;
	}

	@Override
	public CoreId getCoreId() {
		return theLock.getCoreId();
	}

	@Override
	public long getStamp() {
		return theStamp;
	}

	@Override
	public void modified() {
		theStamp++;
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

	@Override
	public String toString() {
		return theLock.toString();
	}
}
