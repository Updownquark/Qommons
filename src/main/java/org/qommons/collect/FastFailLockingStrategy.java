package org.qommons.collect;

import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;

/** A locking strategy that is not thread-safe, but it allows fail-fast behavior; that is, detecting changes in a thread-unsafe manner. */
public class FastFailLockingStrategy implements CollectionLockingStrategy {
	private final ThreadConstraint theThreadConstraint;
	private volatile long theModCount = 0;

	/** @param threadConstraint The thread constraint for this lock to obey */
	public FastFailLockingStrategy(ThreadConstraint threadConstraint) {
		theThreadConstraint = threadConstraint;
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theThreadConstraint;
	}

	@Override
	public boolean isLockSupported() {
		return false; // We use the lock method a little, but let's don't advertise that we're thread-safe
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		if (write && !theThreadConstraint.isEventThread())
			throw new IllegalStateException(WRONG_THREAD_MESSAGE);
		return Transaction.NONE;
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		if (write && !theThreadConstraint.isEventThread())
			throw new IllegalStateException(WRONG_THREAD_MESSAGE);
		return lock(write, cause);
	}

	@Override
	public CoreId getCoreId() {
		return CoreId.EMPTY;
	}

	@Override
	public long getStamp() {
		return theModCount;
	}

	@Override
	public void modified() {
		theModCount++;
	}

	@Override
	public <T> T doOptimistically(T init, OptimisticOperation<T> operation) {
		T res = init;
		FFLSOptimisticContext ctx = new FFLSOptimisticContext(getStamp());
		// This locker does not have any way of preventing other threads from modifying, so this loop does not terminate until it succeeds
		do {
			ctx.failed = false;
			res = operation.apply(res, ctx);
		} while (ctx.failed);
		return res;
	}

	@Override
	public int doOptimistically(int init, OptimisticIntOperation operation) {
		int res = init;
		FFLSOptimisticContext ctx = new FFLSOptimisticContext(getStamp());
		// This locker does not have any way of preventing other threads from modifying, so this loop does not terminate until it succeeds
		do {
			ctx.failed = false;
			res = operation.apply(res, ctx);
		} while (ctx.failed);
		return res;
	}

	class FFLSOptimisticContext implements OptimisticContext {
		private long stamp;
		boolean failed;

		FFLSOptimisticContext(long stamp) {
			this.stamp = stamp;
		}

		@Override
		public boolean getAsBoolean() {
			if (failed)
				return false;
			long newStamp = getStamp();
			if (newStamp != stamp) {
				stamp = newStamp;
				failed = true;
				return false;
			}
			return true;
		}
	}
}
