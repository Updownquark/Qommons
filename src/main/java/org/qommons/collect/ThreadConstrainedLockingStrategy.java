package org.qommons.collect;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.qommons.ThreadConstraint;
import org.qommons.Transaction;

/**
 * <p>
 * A {@link CollectionLockingStrategy} that provides thread safety by restricting write lock acquisition to a single thread, called the
 * "event thread".
 * </p>
 * <p>
 * This class supports obtaining a read lock from any thread, but a non-try-only attempt to obtain a write lock on a thread other than the
 * event thread will result in an {@link IllegalStateException}.
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
 * <p>
 * Unlike most other lock implementations, this lock supports the safe, reliable upgrade of a read lock to a write lock (like all write lock
 * acquisitions, only on the event thread).
 * </p>
 */
public class ThreadConstrainedLockingStrategy extends FastFailLockingStrategy {
	/**
	 * If this is set (due to the presence of the -Dqommons.tcls.track.unclosed.writes=true VM argument), this class will track write lock
	 * transactions (via the {@link Object#finalize()} mechanism) and print a message when a write lock transaction is garbage-collected
	 * without being closed.
	 * 
	 * This behavior is expensive (the call site that obtained the lock is tracked), so it should be turned off in production.
	 */
	private static final boolean TRACK_UNCLOSED_WRITES = "true".equalsIgnoreCase(System.getProperty("qommons.tcls.track.unclosed.writes"));

	/**
	 * If this is set (due to the presence of the -Dqommons.tcls.track.unclosed.reads=true VM argument), this class will track read lock
	 * transactions (via the {@link Object#finalize()} mechanism) and print a message when a read lock transaction obtained off of the event
	 * thread is garbage-collected without being closed.
	 * 
	 * This behavior is expensive (the call site that obtained the lock is tracked), so it should be turned off in production.
	 */
	private static final boolean TRACK_UNCLOSED_READS = "true".equalsIgnoreCase(System.getProperty("qommons.tcls.track.unclosed.reads"));

	private final ThreadConstraint theThreadConstraint;
	// These 2 fields are package-private for lock release performance
	final AtomicInteger theReadLock;
	volatile int theWriteLock;

	/** @param threading The ThreadConstraint defining on which thread exclusive (write) locks may be obtained */
	private ThreadConstrainedLockingStrategy(ThreadConstraint threading) {
		this(threading, null);
	}

	/**
	 * @param threading The ThreadConstraint defining on which thread exclusive (write) locks may be obtained. Only
	 *        {@link ThreadConstraint#isDedicated() dedicated} threading--that is, threading where all tasks happen on a single, dedicated
	 *        thread--are supported.
	 * @param onInitialWriteLock An optional task that will run each time an exclusive lock is initially obtained--i.e. each time the lock
	 *        changes from being NOT exclusively held to being exclusively held
	 */
	private ThreadConstrainedLockingStrategy(ThreadConstraint threading, Runnable onInitialWriteLock) {
		if (!threading.isDedicated())
			throw new IllegalArgumentException(ThreadConstrainedLockingStrategy.class.getSimpleName()
				+ " can only be used for dedicated thread constraints, not " + threading);
		theThreadConstraint = threading;
		theReadLock = new AtomicInteger();
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theThreadConstraint;
	}

	@Override
	public Transaction lock(boolean tryOnly) {
		return lock(false, tryOnly, null);
	}

	@Override
	public Transaction lockWrite(boolean tryOnly, Object cause) {
		return lock(true, tryOnly, cause);
	}

	private Transaction lock(boolean write, boolean tryOnly, Object cause) {
		if (theThreadConstraint.isEventThread()) {
			if (write)
				return getWriteLock(tryOnly, cause);
			else {
				/* Read locks on the event thread have no effect.
				 * This lock "supports" upgrading from a read to a write lock,
				 * and write locks can only be obtained on the dedicated event thread,
				 * so there's no point even keeping track of them. */
				return Transaction.NONE;
			}
		} else if (write) {
			throw new IllegalStateException(ThreadConstraint.MOD_ON_WRONG_THREAD);
		} else {
			if (tryOnly && theWriteLock > 0)
				return null;
			theReadLock.getAndIncrement();
			if (tryOnly) { // Fail if write lock is held
				if (theWriteLock > 0) {
					theReadLock.decrementAndGet();
					return null;
				}
			} else { // May need to wait while the write lock is held
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
			}
			// Success
			if (TRACK_UNCLOSED_READS)
				return new TrackingReadLockRelease();
			else
				return new ReadLockRelease();
		}
	}

	/**
	 * Obtains a write lock. This call assumes we're on the thread constraint's event thread.
	 * 
	 * @param cause The cause for changes that may occur within the lock
	 * @return The transaction to release the lock
	 */
	private Transaction getWriteLock(boolean tryOnly, Object cause) {
		/* Almost all locks share the constraint that it is impossible to safely and reliably upgrade from a read (non-exclusive) lock
		 * to a write (exclusive) lock.
		 * But this class can because unlike most other locks, non-exclusive locks obtained from this class on the event thread
		 * are tracked differently from those obtained from other threads.
		 * So we can wait for all non-exclusive locks from other threads to be released without needing to concern ourselves
		 * with non-exclusive locks obtained on the event thread, which is the current thread.
		 * if (theSafeReadLock > 0) // This class used to keep track of event thread read locks
		 * throw new IllegalStateException("Attempting to upgrade from a read lock to a write lock");
		 */

		theWriteLock++;
		boolean initial = theWriteLock == 1;
		if (initial && theReadLock.get() != 0) {
			if (tryOnly) {
				theWriteLock = 0;
				return null; // Can't obtain an exclusive lock immediately as requested
			}
			/* I had thought that here I could grab the write lock and merely wait for all the external read locks to release,
			 * without releasing the lock.
			 * This would give write locks high priority, and only wait for threads that already held read locks to release them.
			 * The external read lock attempts release the lock while waiting, so I thought this was safe.
			 * But eventually this system encountered a deadlock, when an external thread was trying to obtain a *reentrant* read lock.
			 * The thread was releasing the deeper lock while waiting for the write lock to release,
			 * but the shallower read lock was still held lower down on the stack.
			 * So actually, I do have to release the write lock here while waiting for read locks to release,
			 * as this is the best way to allow reentrant read locks to do their work and then be released.
			 */
			// Wait for all non-event thread read locks to be released
			while (theReadLock.get() != 0) {
				theWriteLock = 0;
				try {
					Thread.sleep(5);
				} catch (InterruptedException e) {
				}
				theWriteLock = 1;
			}
		}
		Transaction superLock = super.lockWrite(false, cause);
		if (TRACK_UNCLOSED_WRITES)
			return new TrackingWriteLockRelease(superLock);
		else
			return new WriteLockRelease(superLock);
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

	@Override
	public CoreId getCoreId() {
		return new ThreadSafeCore(theThreadConstraint);
	}

	@Override
	public String toString() {
		return "Safe on " + getThreadConstraint();
	}

	/** A thread-safe lock core */
	public static class ThreadSafeCore extends CoreId {
		private final ThreadConstraint theThreadConstraint;

		/** @param threadConstraint The thread constraint of the core */
		public ThreadSafeCore(ThreadConstraint threadConstraint) {
			theThreadConstraint = threadConstraint;
		}

		/** @return The thread constraint of the core */
		public ThreadConstraint getThreadConstraint() {
			return theThreadConstraint;
		}

		@Override
		public int hashCode() {
			return theThreadConstraint.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ThreadSafeCore && theThreadConstraint.equals(((ThreadSafeCore) obj).theThreadConstraint);
		}

		@Override
		public String toString() {
			return "Safe:" + theThreadConstraint;
		}
	}

	class ReadLockRelease implements Transaction {
		private boolean isClosed;

		boolean isClosed() {
			return isClosed;
		}

		@Override
		public void close() {
			if (isClosed)
				return;
			isClosed = true;
			theReadLock.decrementAndGet();
		}

		@Override
		public String toString() {
			return "TCLS read release";
		}
	}

	class TrackingReadLockRelease extends ReadLockRelease {
		private final Exception theCallSite;

		TrackingReadLockRelease() {
			theCallSite = new Exception();
			theCallSite.fillInStackTrace();
		}

		@Override
		protected void finalize() throws Throwable {
			if (!isClosed()) {
				System.out.println("Failed to close " + this);
				theCallSite.printStackTrace();
			}
			super.finalize();
		}
	}

	class WriteLockRelease implements Transaction {
		private final Transaction theSuperTransaction;
		// This is for debugging
		// private final int myWriteLock;

		WriteLockRelease(Transaction superTransaction) {
			theSuperTransaction = superTransaction;
			// myWriteLock = theWriteLock;
		}

		private boolean isClosed;

		boolean isClosed() {
			return isClosed;
		}

		@Override
		public void close() {
			if (isClosed)
				return;
			isClosed = true;
			theWriteLock--;
			// System.out.println("Write unlock " + myWriteLock + "->" + theWriteLock);
			theSuperTransaction.close();
		}

		@Override
		public int hashCode() {
			return super.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof WithInitialWriteLock)
				return equals(((WithInitialWriteLock) obj).theBacking);
			return super.equals(obj);
		}

		@Override
		public String toString() {
			return getThreadConstraint() + " TCLS write release";
		}
	}

	class TrackingWriteLockRelease extends WriteLockRelease {
		private final Exception theCallSite;

		TrackingWriteLockRelease(Transaction superTransaction) {
			super(superTransaction);
			theCallSite = new Exception();
			theCallSite.fillInStackTrace();
		}

		@Override
		protected void finalize() throws Throwable {
			if (!isClosed()) {
				System.out.println("Failed to close " + this);
				theCallSite.printStackTrace();
			}
			super.finalize();
		}
	}

	private static final ConcurrentHashMap<ThreadConstraint, ThreadConstrainedLockingStrategy> LOCKS = new ConcurrentHashMap<>();

	/**
	 * @param constraint The thread constraint to get the locking for
	 * @return The locking strategy for the given thread constraint
	 */
	public static ThreadConstrainedLockingStrategy get(ThreadConstraint constraint) {
		return (ThreadConstrainedLockingStrategy) get(constraint, null);
	}

	/**
	 * @param constraint The thread constraint to get the locking for
	 * @param onInitialWriteLock An optional task to be executed each time this particular lock is initially locked for write
	 * @return The locking strategy for the given thread constraint
	 */
	public static CollectionLockingStrategy get(ThreadConstraint constraint, Runnable onInitialWriteLock) {
		ThreadConstrainedLockingStrategy tcls = LOCKS.computeIfAbsent(constraint, ThreadConstrainedLockingStrategy::new);
		if (onInitialWriteLock == null)
			return tcls;
		else
			return new WithInitialWriteLock(tcls, onInitialWriteLock);
	}

	static class WithInitialWriteLock implements CollectionLockingStrategy {
		private final ThreadConstrainedLockingStrategy theBacking;
		private final Runnable onInitialWriteLock;
		private int theWriteLockCount;

		WithInitialWriteLock(ThreadConstrainedLockingStrategy backing, Runnable onInitialWriteLock) {
			theBacking = backing;
			this.onInitialWriteLock = onInitialWriteLock;
		}

		@Override
		public Transaction lock(boolean tryOnly) {
			return theBacking.lock(tryOnly);
		}

		@Override
		public Transaction lockWrite(boolean tryOnly, Object cause) {
			Transaction lock = theBacking.lockWrite(tryOnly, cause);
			if (lock == null)
				return null;
			if (0 == theWriteLockCount++) {
				try {
					onInitialWriteLock.run();
				} catch (RuntimeException | Error e) {
					lock.close();
					throw e;
				}
			}
			return new Transaction.ReleaseOnceTransaction(() -> {
				theWriteLockCount--;
				lock.close();
			});
		}

		@Override
		public CoreId getCoreId() {
			return theBacking.getCoreId();
		}

		@Override
		public long getStamp() {
			return theBacking.getStamp();
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return theBacking.getCurrentCauses();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theBacking.getThreadConstraint();
		}

		@Override
		public void modified() {
			theBacking.modified();
		}

		@Override
		public int hashCode() {
			return theBacking.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theBacking.equals(obj);
		}

		@Override
		public String toString() {
			return theBacking.toString();
		}
	}
}
