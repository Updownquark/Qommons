package org.qommons.collect;

import java.time.Instant;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.qommons.IdentityKey;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.tree.BetterTreeList;

/**
 * <p>
 * A {@link Transactable} that keeps track of the threads acquiring locks against it for detection and debugging of deadlocks.
 * </p>
 * <p>
 * Any lock methods against a TrackableLocker instance will throw {@link DeadlockException} if waiting on the lock would result in deadlock.
 * </p>
 */
@SuppressWarnings({ "javadoc", "unused" })
/*public*/ interface TrackableLocker extends Transactable {
	/** Represents a lock against a {@link TrackableLocker} that is held by a thread or that a thread is waiting to acquire */
	public interface HeldLock {
		/** @return The locker that the lock is against */
		TrackableLocker getLocker();

		/** @return The thread that holds or is waiting for the lock */
		Thread getHolder();

		/** @return A description of the type of the lock */
		String getLockType();

		/**
		 * @return Whether the lock is:
		 *         <ul>
		 *         <li><b>Exclusive</b>, preventing any other threads from acquiring exclusive or inclusive locks of the same
		 *         {@link #getLockType() type} on the same locker</li>
		 *         <li><b>Inclusive</b>, preventing any other threads from acquiring exclusive (but allowing other inclusive) locks of the
		 *         same {@link #getLockType() type} on the same locker</li>
		 *         </ul>
		 */
		boolean isExclusive();

		/** @return Whether the holder is waiting to acquire the lock as opposed to already owning it */
		boolean isWaiting();

		/** @return The time at which the holder made the call to acquire the lock */
		Instant getAcquireAttemptTime();

		/**
		 * An implementation of {@link #toString()}
		 * 
		 * @param str A (possibly null) string builder to append this lock's display information to
		 * @return This lock's display information
		 */
		default StringBuilder display(StringBuilder str) {
			if (str == null)
				str = new StringBuilder();
			Thread holder = getHolder();
			return str.append(holder.getName()).append('(').append(holder.getId()).append("): ")//
				.append(isExclusive() ? "ex" : "in").append("clusive ")//
				.append(getLockType()).append(" lock against ").append(getLocker());
		}
	}

	/** An ordered list of locks */
	class LockList {
		public final List<HeldLock> locks;

		public LockList(List<HeldLock> path) {
			this.locks = path == null ? Collections.emptyList() : Collections.unmodifiableList(path);
		}

		@Override
		public String toString() {
			if (locks.isEmpty())
				return "none";
			String[] holderDisplays = new String[locks.size()];
			String[] holderTypes = new String[locks.size()];
			String[] lockers = new String[locks.size()];
			int maxHolderLength = 0;
			int maxTypeLength = 0;
			int maxLockerLength = 0;
			for (int i = 0; i < locks.size(); i++) {
				HeldLock lock = locks.get(i);
				Thread holder = lock.getHolder();
				holderDisplays[i] = holder.getName() + "(" + holder.getId() + ")";
				if (holderDisplays[i].length() > maxHolderLength)
					maxHolderLength = holderDisplays[i].length();

				holderTypes[i] = lock.getLockType();
				if (holderTypes[i].length() > maxTypeLength)
					maxTypeLength = holderTypes[i].length();

				lockers[i] = lock.getLocker().toString();
				if (lockers[i].length() > maxLockerLength)
					maxLockerLength = lockers[i].length();
			}

			StringBuilder str = new StringBuilder();
			for (int i = 0; i < locks.size(); i++) {
				if (i > 0)
					str.append('\n');
				str.append('\t');
				HeldLock lock = locks.get(i);
				pad(str, holderDisplays[i], maxHolderLength).append(": ")//
					.append(lock.isExclusive() ? "ex" : "in").append("clusive ");
				pad(str, holderTypes[i], maxTypeLength).append(" lock against ");
				pad(str, lockers[i], maxLockerLength).append(" at ")//
					.append(lock.getAcquireAttemptTime());
			}
			return str.toString();
		}

		private static StringBuilder pad(StringBuilder str, String value, int length) {
			str.append(value);
			for (int i = value.length(); i < length; i++)
				str.append(' ');
			return str;
		}
	}

	/**
	 * Thrown from lock-acquiring methods of {@link TrackableLocker} instances if attempting to acquiring the lock would result in deadlock
	 */
	class DeadlockException extends IllegalStateException {
		public final LockList locks;

		public DeadlockException(LockList locks) {
			super("Acquiring this lock would result in deadlock");
			this.locks = locks;
		}

		@Override
		public String toString() {
			return super.toString() + ": \n" + locks;
		}
	}

	/** @return All locks held against this locker */
	LockList getHeldLocks();

	/** @return All locks against this locker that threads are waiting to acquire */
	LockList getWaitingLocks();

	/** Keeps a registry of {@link HeldLock locks} against all {@link TrackableLocker lockers} by {@link HeldLock#getHolder() holder} */
	class Usage {
		public interface LockHoldTransaction extends Transaction {
			void acquired();
		}

		private static class ThreadLockHolder {
			final Thread thread;
			final BetterList<UsageLock> locks;

			// For deadlock detection
			final LinkedList<UsageLock> lockPath;

			ThreadLockHolder(Thread thread) {
				this.thread = thread;
				locks = new BetterTreeList<>(false);

				lockPath = new LinkedList<>();
			}

			boolean add(UsageLock lock) {
				UsageLock preLock = locks.peekFirst();
				if (preLock != null && preLock.isWaiting)
					throw new IllegalStateException("Cannot attempt another lock without first acquiring the last one");
				locks.addFirst(lock);
				return true; // The thread locker can't know if acquiring the lock might result in deadlock
			}

			UsageLock remove() {
				UsageLock lock = locks.removeFirst();
				if (locks.isEmpty())
					THREAD_LOCKS.remove(new IdentityKey<>(thread));
				return lock;
			}
		}

		private static class LockerLockHolder {
			final TrackableLocker locker;
			final BetterList<UsageLock> locks;
			int hasExclusive;

			LockerLockHolder(TrackableLocker locker) {
				this.locker = locker;
				locks = new BetterTreeList<>(false);
			}

			boolean add(UsageLock lock){
				UsageLock preLock = locks.peekFirst();
				if (preLock != null && preLock.isWaiting)
					throw new IllegalStateException("Cannot attempt another lock without first acquiring the last one");
				boolean locksEmpty=locks.isEmpty();
				locks.addFirst(lock);
				// TODO More aggressive ways to detect deadlock possibility to prevent unnecessary heavy deadlock detection
				return !locksEmpty; // If this is the first lock on the locker, there can't possibly be a problem
			}

			UsageLock remove() {
				UsageLock lock = locks.removeFirst();
				if (locks.isEmpty())
					LOCKER_LOCKS.remove(new IdentityKey<>(locker));
				return lock;
			}
		}

		private static class UsageLock {
			final HeldLock lock;
			final ThreadLockHolder threadLock;
			final LockerLockHolder lockerLock;
			boolean isWaiting;

			UsageLock(HeldLock lock, ThreadLockHolder threadLock, LockerLockHolder lockerLock) {
				this.lock = lock;
				this.threadLock = threadLock;
				this.lockerLock = lockerLock;
				isWaiting = true;
			}

			LockHoldTransaction use() {
				if (threadLock.add(this) && lockerLock.add(this)) {
					// Determine if this lock acquisition attempt will result in deadlock
					threadLock.lockPath.add(this);
					if (detectDeadlock(threadLock.lockPath, this)) {
						List<HeldLock> path = threadLock.lockPath.stream().map(l -> l.lock).collect(Collectors.toList());
						throw new DeadlockException(new LockList(path));
					}
					threadLock.lockPath.clear();
				}

				return new LockHoldTransaction() {
					@Override
					public void acquired() {
						if (isWaiting)
							throw new IllegalStateException("Lock already marked acquired");
						isWaiting = false;
					}

					@Override
					public void close() {
						if (isWaiting)
							throw new IllegalStateException("Lock not acquired");

						if (threadLock.remove() != lock)
							throw new IllegalStateException("Lock release order violated");
						if (lockerLock.remove() != lock)
							throw new IllegalStateException("Lock release order violated");
					}
				};
			}
		}

		private static Map<IdentityKey<Thread>, ThreadLockHolder> THREAD_LOCKS = new ConcurrentHashMap<>();
		private static Map<IdentityKey<TrackableLocker>, LockerLockHolder> LOCKER_LOCKS = new ConcurrentHashMap<>();

		/**
		 * Adds a held lock to the thread-accessible lock tracker. This MUST be called from the lock's holder thread.
		 * 
		 * @param lock The lock to push
		 * @return The transaction to {@link Transaction#close() close} to remove the held lock. Must be closed after any
		 *         subsequently-registered lock push transactions and before any previously-registered ones.
		 * @throws DeadlockException If attempting to acquire the given lock would result in deadlock
		 */
		public static LockHoldTransaction pushLockAttempt(HeldLock lock) throws DeadlockException {
			ThreadLockHolder threadLock = THREAD_LOCKS.computeIfAbsent(new IdentityKey<>(lock.getHolder()),
				k -> new ThreadLockHolder(k.value));
			LockerLockHolder lockerLock = LOCKER_LOCKS.computeIfAbsent(new IdentityKey<>(lock.getLocker()),
				k -> new LockerLockHolder(k.value));

			UsageLock usageLock = new UsageLock(lock, threadLock, lockerLock);
			return usageLock.use();
		}

		/**
		 * Gets locks held by the given thread. The resulting list is only guaranteed thread-safe for the given thread.
		 * 
		 * @param holder The thread to get locks for
		 * @return All registered locks held by the given thread, in reverse order of attainment (i.e. the most recently obtained lock is
		 *         the first in the list)
		 */
		public static List<HeldLock> getHeldLocks(Thread holder) {
			ThreadLockHolder locks = THREAD_LOCKS.get(holder);
			return locks == null ? Collections.emptyList()
				: Collections.unmodifiableList(locks.locks.stream().map(l -> l.lock).collect(Collectors.toList()));
		}

		private static boolean detectDeadlock(Deque<UsageLock> deadlock, UsageLock terminal) {
			UsageLock top = deadlock.peekLast();
			// TODO Auto-generated method stub
			return false;
		}
	}
}
