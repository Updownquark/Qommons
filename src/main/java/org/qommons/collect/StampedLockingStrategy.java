package org.qommons.collect;

import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

import org.qommons.Transaction;

/** A collection-locking strategy using {@link StampedLock} */
public class StampedLockingStrategy implements CollectionLockingStrategy {
	private static final int MAX_OPTIMISTIC_TRIES = 3;
	private static final String CO_MOD_MSG = "Use\n"//
		+ "try(Transaction t=lock(forWrite, null)){\n"//
		+ "\t//iteration\n" //
		+ "}\n" //
		+ "to ensure this does not happen.";

	private final ThreadLocal<ThreadState> theStampCollection;
	private final StampedLock theLocker;
	private final AtomicInteger theIndexChanges;

	/** Creates the locking strategy */
	public StampedLockingStrategy() {
		theStampCollection = new ThreadLocal<ThreadState>() {
			@Override
			protected ThreadState initialValue() {
				return new ThreadState();
			}
		};
		theLocker = new StampedLock();
		theIndexChanges = new AtomicInteger();
	}

	@Override
	public boolean isLockSupported() {
		return true;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		ThreadState state = theStampCollection.get();
		if (state.stamp > 0) {
			if (write && !state.isWrite) {
				// Alright, I'll try
				long stamp = theLocker.tryConvertToWriteLock(state.stamp);
				if (stamp == 0)
					throw new IllegalStateException("Could not upgrade to write lock");
				// Got lucky
				state.stamp = stamp;
				return () -> state.stamp = theLocker.tryConvertToReadLock(state.stamp);
			}
			return Transaction.NONE;
		} else {
			state.set(write ? theLocker.writeLock() : theLocker.readLock(), write);
			return () -> {
				if (write)
					theLocker.unlockWrite(state.stamp);
				else
					theLocker.unlockRead(state.stamp);
				state.stamp = 0;
			};
		}
	}

	@Override
	public <T> T doOptimistically(T init, OptimisticOperation<T> operation) {
		ThreadState state = theStampCollection.get();
		if (state.stamp > 0) // Already holding a lock
			return operation.apply(init, state.stamp);
		T res = init;
		for (int rep = 0; rep < MAX_OPTIMISTIC_TRIES; rep++) {
			long stamp = theLocker.tryOptimisticRead();
			if (stamp == 0) // Write lock is taken. Wait for readability.
				break;
			res = operation.apply(res, stamp);
			if (theLocker.validate(stamp))
				return res;
		}
		try (Transaction t = lock(false, null)) {
			return operation.apply(res, state.stamp);
		}
	}

	@Override
	public void indexChanged(int added) {
		theIndexChanges.getAndIncrement();
	}

	@Override
	public long getStamp() {
		return theLocker.tryOptimisticRead();
	}

	@Override
	public boolean check(long stamp) {
		return theLocker.validate(stamp);
	}

	@Override
	public SubStampedLockingStrategy subLock() {
		return new SubStampedLockingStrategy(this);
	}

	private static class SubStampedLockingStrategy implements CollectionLockingStrategy.SubLockingStrategy {
		private final StampedLockingStrategy theTop;
		private final SubStampedLockingStrategy theParent;
		private final AtomicInteger theSubIndexChanges;
		private final Consumer<Integer> theIndexChangeCallback;

		private SubStampedLockingStrategy(StampedLockingStrategy top) {
			theTop = top;
			theParent = null;
			theSubIndexChanges = new AtomicInteger();
			theSubIndexChanges.set(top.doOptimistically(0, (v, stamp) -> top.theIndexChanges.get()));
			theIndexChangeCallback = null;
		}

		private SubStampedLockingStrategy(SubStampedLockingStrategy parent, Consumer<Integer> indexChangeCallback) {
			theTop = parent.theTop;
			theParent = parent;
			theSubIndexChanges = new AtomicInteger(parent.check());
			theIndexChangeCallback = indexChangeCallback;
		}

		@Override
		public int check() throws ConcurrentModificationException {
			return theTop.doOptimistically(0, (v, stamp) -> _check());
		}

		private int _check() throws ConcurrentModificationException {
			int val = checkParent();
			if (val != theSubIndexChanges.get())
				throw new ConcurrentModificationException("Collection has changed underneath this view.  " + CO_MOD_MSG);
			return val;
		}

		private int checkParent() {
			if (theParent != null)
				return theParent._check();
			else
				return theTop.theIndexChanges.get();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction t = theTop.lock(write, cause);
			_check();
			return t;
		}

		@Override
		public <T> T doOptimistically(T init, OptimisticOperation<T> operation) {
			return theTop.doOptimistically(init, (value, stamp) -> {
				_check();
				return operation.apply(value, stamp);
			});
		}

		@Override
		public long getStamp() {
			return theTop.getStamp();
		}

		@Override
		public boolean check(long stamp) {
			if (!theTop.check(stamp))
				return false;
			_check();
			return true;
		}

		@Override
		public void indexChanged(int added) {
			theSubIndexChanges.getAndIncrement();
			if (theIndexChangeCallback != null)
				theIndexChangeCallback.accept(added);
			if (theParent != null)
				theParent.indexChanged(added);
		}

		@Override
		public SubLockingStrategy subLock(Consumer<Integer> indexChangeCallback) {
			return new SubStampedLockingStrategy(this, indexChangeCallback);
		}

		@Override
		public SubLockingStrategy subLock() {
			return subLock(idx -> {
			});
		}

		@Override
		public SubLockingStrategy siblingLock() {
			return theParent != null ? new SubStampedLockingStrategy(theParent, theIndexChangeCallback)
				: new SubStampedLockingStrategy(theTop);
		}
	}

	static class ThreadState {
		long stamp;
		boolean isWrite;

		void set(long stamp, boolean write) {
			this.stamp = stamp;
			isWrite = write;
		}
	}
}
