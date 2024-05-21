package org.qommons.collect;

import java.util.concurrent.atomic.AtomicInteger;

import org.qommons.ThreadConstraint;
import org.qommons.Transaction;

/**
 * <p>
 * A {@link CollectionLockingStrategy} that uses no actual locking, but provides thread safety by restricting modification to a single
 * thread.
 * </p>
 * <p>
 * This class supports obtaining a read lock from any thread, but ANY attempt to obtain a write lock on a thread other than the event thread
 * of the configured {@link ThreadConstraint} (even from {@link #tryLock(boolean, Object)}) will result in an {@link IllegalStateException}.
 * </p>
 * <p>
 * Beyond this constraint, this lock behaves as expected:
 * <ul>
 * <li>Unlimited read (non-exclusive) locks are available from any threads simultaneously while no exclusive (write) lock is held.</li>
 * <li>An unlimited number of exclusive (write) locks may be obtained from the event thread.</li>
 * <li>The {@link Transaction}s obtained by successful lock calls on the event thread must be released on the thread they are obtained from.
 * This is not checked for performance reasons, but failure to do this could result in the lock becoming unusable and blocking forever.</li>
 * </ul>
 * </p>
 */
public class ThreadConstrainedLockingStrategy extends FastFailLockingStrategy {
	// These are package-private for lock release performance
	final AtomicInteger theReadLock;
	int theSafeReadLock;
	volatile int theWriteLock;
	private final Runnable onInitialWriteLock;

	/** @param threading The ThreadConstraint defining on which thread exclusive (write) locks may be obtained */
	public ThreadConstrainedLockingStrategy(ThreadConstraint threading) {
		this(threading, null);
	}

	/**
	 * @param threading The ThreadConstraint defining on which thread exclusive (write) locks may be obtained
	 * @param onInitialWriteLock An optional task that will run each time an exclusive lock is initially obtained--i.e. each time the lock
	 *        changes from being NOT exclusively held to being exclusively held
	 */
	public ThreadConstrainedLockingStrategy(ThreadConstraint threading, Runnable onInitialWriteLock) {
		super(threading);
		if (threading == ThreadConstraint.ANY)
			throw new IllegalArgumentException(
				ThreadConstrainedLockingStrategy.class.getSimpleName() + " cannot be used with ThreadConstraint.ANY");
		this.onInitialWriteLock = onInitialWriteLock;
		theReadLock = new AtomicInteger();
	}

	@Override
	public boolean isLockSupported() {
		return true;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		boolean onPublicThread = getThreadConstraint().isEventThread();
		if (write) {
			if (!onPublicThread)
				throw new IllegalStateException(ThreadConstraint.MOD_ON_WRONG_THREAD);
			else
				return getWriteLock(false, cause);
		} else if (onPublicThread) {
			theSafeReadLock++;
			return new IntReadLockRelease();
		} else {
			theReadLock.getAndIncrement();
			while (theWriteLock > 0) {
				// Release our non-exclusive lock and wait for the exclusive lock(s) to be released
				theReadLock.decrementAndGet();
				do {
					try {
						Thread.sleep(5);
					} catch (InterruptedException e) {
					}
				} while (theWriteLock > 0);
				// The exclusive lock is released.
				// Make another attempt to obtain a non-exclusive lock and see if we got it before another exclusive lock was obtained
				theReadLock.getAndIncrement();
			}
			// Success
			return new ExtReadLockRelease();
		}
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		boolean onPublicThread = getThreadConstraint().isEventThread();
		if (write) {
			if (onPublicThread)
				return getWriteLock(true, cause);
			else
				throw new IllegalStateException(ThreadConstraint.MOD_ON_WRONG_THREAD);
		} else if (onPublicThread) {
			theSafeReadLock++;
			return new IntReadLockRelease();
		} else {// Read lock off of the public thread
			if (theWriteLock > 0)
				return null;
			theReadLock.getAndIncrement();
			if (theWriteLock > 0) {
				theReadLock.decrementAndGet();
				return null;
			}
			return new ExtReadLockRelease();
		}
	}

	/**
	 * Obtains a write lock. This call assumes we're on the thread constraint's event thread.
	 * 
	 * @param cause The cause for changes that may occur within the lock
	 * @return The transaction to release the lock
	 */
	private Transaction getWriteLock(boolean tryOnly, Object cause) {
		// Almost all locks share the constraint that it is impossible to safely upgrade from a read (non-exclusive) lock
		// to a write (exclusive) lock.
		// But in this lock we can do this safely because unlike those other locks, non-exclusive locks obtained on the event thread
		// are fundamentally different from those obtained from other threads.
		// So we can wait for all non-exclusive locks from other threads to be released without needing to concern ourselves
		// with non-exclusive locks obtained on the event thread, which is the current thread.
		// if (theSafeReadLock > 0)
		// throw new IllegalStateException("Attempting to upgrade from a read lock to a write lock");
		theWriteLock++;
		boolean initial = theWriteLock == 1;
		if (initial && theReadLock.get() != 0) {
			if (tryOnly)
				return null; // Can't obtain an exclusive lock immediately as requested
			// Wait for all external read locks to be released
			// Keep our exclusive lock so no new non-exclusive locks can be obtained. Wait until all current read locks have been released.
			while (theReadLock.get() != 0) {
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
				}
			}
		}
		Transaction t = new WriteLockRelease(super.lock(true, cause));
		if (initial && onInitialWriteLock != null) {
			try {
				onInitialWriteLock.run();
			} catch (RuntimeException | Error e) {
				t.close();
				throw e;
			}
		}
		return t;
	}

	@Override
	public void modified() {
		if (theWriteLock == 0)
			throw new IllegalStateException("Not write locked");
		else if (!getThreadConstraint().isEventThread())
			throw new IllegalStateException(ThreadConstraint.MOD_ON_WRONG_THREAD);
		else
			super.modified();
	}

	class ExtReadLockRelease implements Transaction {
		private boolean isClosed;

		@Override
		public void close() {
			if (isClosed)
				return;
			isClosed = true;
			theReadLock.decrementAndGet();
		}

		@Override
		public String toString() {
			return "TCLS external read release";
		}
	}

	class IntReadLockRelease implements Transaction {
		private boolean isClosed;

		@Override
		public void close() {
			if (isClosed)
				return;
			isClosed = true;
			theSafeReadLock--;
		}

		@Override
		public String toString() {
			return "TCLS internal read release";
		}
	}

	class WriteLockRelease implements Transaction {
		private final Transaction theSuperTransaction;

		WriteLockRelease(Transaction superTransaction) {
			theSuperTransaction = superTransaction;
		}

		private boolean isClosed;

		@Override
		public void close() {
			if (isClosed)
				return;
			isClosed = true;
			theWriteLock--;
			theSuperTransaction.close();
		}

		@Override
		public String toString() {
			return "TCLS write release";
		}
	}
}
