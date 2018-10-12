package org.qommons;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;

public interface Lockable {
	boolean isLockSupported();

	Transaction lock();

	Transaction tryLock();

	static Lockable lockable(Lock lock) {
		return new LockLockable(lock);
	}

	static Lockable lockable(ReentrantReadWriteLock lock, boolean write) {
		return lockable(write ? lock.writeLock() : lock.readLock());
	}

	static Lockable lockable(StructuredTransactable transactable) {
		return transactable == null ? null : new STLockable(transactable);
	}

	static Lockable lockable(StructuredTransactable transactable, boolean write, boolean structural, Object cause) {
		return transactable == null ? null : new STLockable2(transactable, write, structural, cause);
	}

	static Transaction lock(Lockable lockable) {
		return lockable == null ? Transaction.NONE : lockable.lock();
	}

	static Transaction tryLock(Lockable lockable) {
		return lockable == null ? Transaction.NONE : lockable.tryLock();
	}

	static boolean isLockSupported(Lockable... lockables) {
		return isLockSupported(Arrays.asList(lockables));
	}

	static boolean isLockSupported(Collection<? extends Lockable> lockables) {
		for (Lockable lockable : lockables)
			if (lockable != null && !lockable.isLockSupported())
				return false;
		return true;
	}

	static Transaction lockAll(Lockable... lockables) {
		return lockAll(Arrays.asList(lockables));
	}

	static Transaction lockAll(Collection<? extends Lockable> lockables) {
		return lockAll(null, lockables);
	}

	static Transaction lockAll(Lockable outer, Collection<? extends Lockable> lockables) {
		return lockAll(outer, lockables, l -> l);
	}

	static <X> Transaction lockAll(Lockable outer, Collection<? extends X> lockables, Function<? super X, ? extends Lockable> map) {
		if (outer == null && lockables.isEmpty())
			return Transaction.NONE;
		Transaction[] locks = new Transaction[lockables.size() + (outer == null ? 0 : 1)];
		reattempt: while (true) {
			boolean hasLock = false;
			if (outer != null) {
				hasLock = true;
				locks[0] = outer.lock();
			}
			int i = 0;
			for (X value : lockables) {
				Lockable lockable = map.apply(value);
				if (lockable == null) {} else if (!hasLock) {
					hasLock = true;
					locks[i + (outer == null ? 0 : 1)] = lockable.lock();
				} else {
					Transaction lock = lockable.tryLock();
					if (lock == null) {
						for (int j = i - 1; j >= 0; j--)
							locks[j].close();
						try {
							Thread.sleep(2);
						} catch (InterruptedException e) {}
						continue reattempt;
					}
					locks[i + (outer == null ? 0 : 1)] = lock;
				}
				i++;
			}
			return Transaction.and(locks);
		}
	}

	static Transaction tryLockAll(Lockable... lockables) {
		return tryLockAll(Arrays.asList(lockables));
	}

	static Transaction tryLockAll(Collection<? extends Lockable> lockables) {
		return tryLockAll(null, lockables);
	}

	static Transaction tryLockAll(Lockable outer, Collection<? extends Lockable> lockables) {
		return tryLockAll(outer, lockables, l -> l);
	}

	static <X> Transaction tryLockAll(Lockable outer, Collection<? extends X> lockables, Function<? super X, ? extends Lockable> map) {
		if (outer == null && lockables.isEmpty())
			return Transaction.NONE;
		Transaction[] locks = new Transaction[lockables.size()];
		int i = 0;
		for (X value : lockables) {
			Lockable lockable = map.apply(value);
			locks[i] = Lockable.tryLock(lockable);
			if (locks[i] == null) {
				for (int j = i - 1; j >= 0; j--)
					locks[j].close();
				return null;
			}
			i++;
		}
		return Transaction.and(locks);
	}

	static Transaction lock(Lockable outer, Supplier<Lockable> getInner) {
		while (true) {
			Transaction outerLock = outer.lock();
			Lockable inner = getInner.get();
			if (inner == null)
				return outerLock;
			Transaction innerLock = inner.tryLock();
			if (innerLock == null) {
				outerLock.close();
				try {
					Thread.sleep(2);
				} catch (InterruptedException e) {}
				continue;
			}
			return Transaction.and(outerLock, innerLock);
		}
	}

	static Transaction tryLock(Lockable outer, Supplier<Lockable> getInner) {
		Transaction outerLock = outer.tryLock();
		if (outerLock == null)
			return null;
		Lockable inner = getInner.get();
		if (inner == null)
			return outerLock;
		Transaction innerLock = inner.tryLock();
		if (innerLock == null) {
			outerLock.close();
			return null;
		}
		return Transaction.and(outerLock, innerLock);
	}

	static class LockLockable implements Lockable {
		private final Lock theLock;

		public LockLockable(Lock lock) {
			theLock = lock;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock() {
			theLock.lock();
			return theLock::unlock;
		}

		@Override
		public Transaction tryLock() {
			if (!theLock.tryLock())
				return null;
			return theLock::unlock;
		}

		@Override
		public int hashCode() {
			return theLock.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof LockLockable && theLock.equals(((LockLockable) obj).theLock);
		}

		@Override
		public String toString() {
			return theLock.toString();
		}
	}

	static class STLockable implements Lockable {
		private final StructuredTransactable theTransactable;

		public STLockable(StructuredTransactable transactable) {
			theTransactable = transactable;
		}

		@Override
		public boolean isLockSupported() {
			return theTransactable.isLockSupported();
		}

		@Override
		public Transaction lock() {
			return theTransactable.lock(false, false, null);
		}

		@Override
		public Transaction tryLock() {
			return theTransactable.tryLock(false, false, null);
		}

		@Override
		public int hashCode() {
			return theTransactable.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof STLockable && theTransactable.equals(((STLockable) obj).theTransactable);
		}

		@Override
		public String toString() {
			return theTransactable.toString();
		}
	}

	static class STLockable2 implements Lockable {
		private final StructuredTransactable theTransactable;
		private final boolean write;
		private final boolean structural;
		private final Object cause;

		public STLockable2(StructuredTransactable transactable, boolean write, boolean structural, Object cause) {
			theTransactable = transactable;
			this.write = write;
			this.structural = structural;
			this.cause = cause;
		}

		@Override
		public boolean isLockSupported() {
			return theTransactable.isLockSupported();
		}

		@Override
		public Transaction lock() {
			return theTransactable.lock(write, structural, cause);
		}

		@Override
		public Transaction tryLock() {
			return theTransactable.tryLock(write, structural, cause);
		}

		@Override
		public int hashCode() {
			return theTransactable.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof STLockable2 && theTransactable.equals(((STLockable2) obj).theTransactable);
		}

		@Override
		public String toString() {
			return theTransactable.toString();
		}
	}
}
