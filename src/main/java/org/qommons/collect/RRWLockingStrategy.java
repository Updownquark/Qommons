package org.qommons.collect;

import java.util.Collection;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.qommons.CausalLock;
import org.qommons.DefaultCausalLock;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;

/** A locking strategy backed by a {@link ReentrantReadWriteLock} or, more generically, a {@link Transactable} */
public class RRWLockingStrategy implements CollectionLockingStrategy {
	private final CausalLock theLock;
	private volatile long theStamp;

	/**
	 * Creates the locking strategy
	 * 
	 * @param owner The owner of the lock, for debugging
	 * @param threadConstraint The thread constraint for the lock to obey
	 */
	public RRWLockingStrategy(Object owner, ThreadConstraint threadConstraint) {
		this(new ReentrantReadWriteLock(), owner, threadConstraint);
	}

	/**
	 * @param lock The lock to use
	 * @param owner The owner of the lock, for debugging
	 * @param threadConstraint The thread constraint for the lock to obey
	 */
	public RRWLockingStrategy(ReentrantReadWriteLock lock, Object owner, ThreadConstraint threadConstraint) {
		this(Transactable.transactable(lock, owner, threadConstraint));
	}

	/** @param lock The lock to use */
	public RRWLockingStrategy(Transactable lock) {
		if (lock instanceof CausalLock)
			theLock = (CausalLock) lock;
		else
			theLock = new DefaultCausalLock(lock);
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theLock.getThreadConstraint();
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
	public Collection<Cause> getCurrentCauses() {
		return theLock.getCurrentCauses();
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
		return theLock.doOptimistically(init, operation);
	}

	@Override
	public int doOptimistically(int init, OptimisticIntOperation operation) {
		return theLock.doOptimistically(init, operation);
	}

	@Override
	public String toString() {
		return theLock.toString();
	}
}
