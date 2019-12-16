package org.qommons.collect;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.qommons.Transactable;
import org.qommons.Transaction;

/** A reentrancy-enabled read/write lock with the capability of upgrading from a read to a write lock */
public class UpgradableReadWriteLock implements Transactable {
	static class Locking {
		final Thread thread;
		int readLocks;
		int writeLocks;

		Locking() {
			thread = Thread.currentThread();
		}
	}

	private final ThreadLocal<Locking> theLocking;
	private final AtomicInteger theReadLocks;
	private final AtomicReference<Locking> theWriteLock;

	/** Creates the lock */
	public UpgradableReadWriteLock() {
		theLocking = ThreadLocal.withInitial(Locking::new);
		theReadLocks = new AtomicInteger();
		theWriteLock = new AtomicReference<>();
	}

	/**
	 * @return The number of threads that have unexclusive (read-only) locks on this lock. If this value is 1, the lock may still be
	 *         exclusively (write) locked by the same thread that also has an unexclusive lock on it.
	 */
	public int getReadLockedThreads() {
		return theReadLocks.get();
	}

	/** @return The thread with an exclusive (write) lock on this lock, or null if the lock is not exclusively held */
	public Thread getWriteLock() {
		Locking locking = theWriteLock.get();
		if (locking == null || locking.writeLocks == 0)
			return null;
		else
			return locking.thread;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		Locking locking = theLocking.get();
		if (write) {
			if (locking.writeLocks == 0)
				acquireWriteLock(locking);
			locking.writeLocks++;
			return new Unlock(locking, true);
		} else {
			if (locking.writeLocks == 0 && locking.readLocks == 0)
				acquireReadLock();
			else if (locking.readLocks == 0)
				acquireReadLockSafe();
			locking.readLocks++;
			return new Unlock(locking, false);
		}
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		Locking locking = theLocking.get();
		if (write) {
			if (locking.writeLocks == 0 && !tryAcquireWriteLock(locking))
				return null;
			locking.writeLocks++;
			return new Unlock(locking, true);
		} else {
			if (locking.writeLocks == 0 && locking.readLocks == 0) {
				if (!tryAcquireReadLock())
					return null;
			} else if (locking.readLocks == 0)
				acquireReadLockSafe();
			locking.readLocks++;
			return new Unlock(locking, false);
		}
	}

	private void acquireReadLock() {
		boolean acquired = false;
		while (!acquired) {
			while (theWriteLock.get() != null)
				waitASec();
			theReadLocks.getAndIncrement();
			if (theWriteLock.get() != null)
				unlockRead();
			else
				acquired = true;
		}
	}

	private void acquireReadLockSafe() {
		// This method is only called from a thread that has an exclusive lock but no inclusive lock
		if (theReadLocks.getAndIncrement() > 0)
			throw new IllegalStateException();
	}

	private void acquireWriteLock(Locking locking) {
		while (!theWriteLock.compareAndSet(null, locking))
			waitASec();
		int lockCountNeeded = locking.readLocks == 0 ? 0 : 1;
		while (theReadLocks.get() != lockCountNeeded)
			waitASec();
	}

	private boolean tryAcquireReadLock() {
		if (theWriteLock.get() != null)
			return false;
		theReadLocks.getAndIncrement();
		if (theWriteLock.get() != null) {
			unlockRead();
			return false;
		}
		return true;
	}

	private boolean tryAcquireWriteLock(Locking locking) {
		if (!theWriteLock.compareAndSet(null, locking))
			return false;
		int lockCountNeeded = locking.readLocks == 0 ? 0 : 1;
		if (theReadLocks.get() != lockCountNeeded)
			return false;
		return true;
	}

	void unlockRead() {
		if (theReadLocks.decrementAndGet() < 0)
			throw new IllegalStateException();
	}

	void unlockWrite() {
		theWriteLock.set(null);
	}

	private static void waitASec() {
		try {
			Thread.sleep(2);
		} catch (InterruptedException e) {}
	}

	class Unlock implements Transaction {
		private final Locking locking;
		private final boolean write;
		private final int lockCount;
		private boolean closed;

		Unlock(Locking locking, boolean write) {
			this.locking = locking;
			this.write = write;
			this.lockCount = write ? locking.writeLocks : locking.readLocks;
		}

		@Override
		public void close() {
			if (closed)
				throw new IllegalStateException("This transaction has already been closed");
			Locking threadLocking = theLocking.get();
			if (threadLocking != locking)
				throw new IllegalStateException("A lock can only be unlocked from the same thread that locked it");
			else if (lockCount != (write ? locking.writeLocks : locking.readLocks))
				throw new IllegalStateException("Reentrant locks must be unlocked in the reverse of the order they were locked."
					+ "I.e. the most recently locked lock must be unlocked before any others can be unlocked.");
			closed = true;
			if (write) {
				locking.writeLocks--;
				if (locking.writeLocks == 0)
					unlockWrite();
			} else {
				locking.readLocks--;
				if (locking.readLocks == 0)
					unlockRead();
			}
		}
	}
}
