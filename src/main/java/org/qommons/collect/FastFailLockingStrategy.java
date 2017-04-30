package org.qommons.collect;

import java.util.ConcurrentModificationException;
import java.util.function.Consumer;

import org.qommons.Transaction;

/**
 * A locking strategy that is not thread-safe, but it is fast fail. It will attempt to detect index-affecting modifications of sub-views and
 * throw a {@link ConcurrentModificationException} if a collection has changed from underneath a sub-view.
 */
public class FastFailLockingStrategy implements CollectionLockingStrategy {
	private static final String CO_MOD_MSG = "Use\n"//
		+ "try(Transaction t=lock(forWrite, null)){\n"//
		+ "\t//iteration\n" //
		+ "}\n" //
		+ "to ensure this does not happen.";

	private volatile int theModCount = 1;
	private volatile int theIndexChanges = 1;

	@Override
	public boolean isLockSupported() {
		return false;// We use the lock method a little, but let's don't advertise that we're thread-safe
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		if (write)
			return () -> theModCount++;
		else
			return Transaction.NONE;
	}

	@Override
	public long getStamp() {
		return theModCount;
	}

	@Override
	public boolean check(long stamp) {
		return theModCount == stamp;
	}

	@Override
	public void indexChanged(int added) {
		theIndexChanges++;
	}

	@Override
	public SubLockingStrategy subLock() {
		return new FastFailSubLockingStrategy(this);
	}

	class FastFailSubLockingStrategy implements CollectionLockingStrategy.SubLockingStrategy {
		private final FastFailLockingStrategy theTop;
		private final FastFailSubLockingStrategy theParent;
		private final Consumer<Integer> theIndexChangeCallback;
		private volatile int theSubIndexChanges;

		private FastFailSubLockingStrategy(FastFailLockingStrategy top) {
			theTop = top;
			theParent = null;
			theSubIndexChanges = top.theIndexChanges;
			theIndexChangeCallback = null;
		}

		private FastFailSubLockingStrategy(FastFailSubLockingStrategy parent, Consumer<Integer> indexChangeCallback) {
			theTop = parent.theTop;
			theParent = parent;
			theSubIndexChanges = parent.theSubIndexChanges;
			theIndexChangeCallback = indexChangeCallback;
		}

		@Override
		public int check() throws ConcurrentModificationException {
			return theTop.doOptimistically(0, (v, stamp) -> _check());
		}

		private int _check() throws ConcurrentModificationException {
			int val = checkParent();
			if (val != theSubIndexChanges)
				throw new ConcurrentModificationException("Collection has changed underneath this view.  " + CO_MOD_MSG);
			return val;
		}

		private int checkParent() {
			if (theParent != null)
				return theParent._check();
			else
				return theTop.theIndexChanges;
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
			theSubIndexChanges++;
			if (theIndexChangeCallback != null)
				theIndexChangeCallback.accept(added);
			if (theParent != null)
				theParent.indexChanged(added);
		}

		@Override
		public SubLockingStrategy subLock(Consumer<Integer> indexChangeCallback) {
			return new FastFailSubLockingStrategy(this, indexChangeCallback);
		}

		@Override
		public SubLockingStrategy subLock() {
			return subLock(idx -> {
			});
		}

		@Override
		public SubLockingStrategy siblingLock() {
			return theParent != null ? new FastFailSubLockingStrategy(theParent, theIndexChangeCallback)
				: new FastFailSubLockingStrategy(theTop);
		}
	}
}
