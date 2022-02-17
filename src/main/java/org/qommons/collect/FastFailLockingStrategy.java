package org.qommons.collect;

import java.util.function.LongSupplier;

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
		long[] stamp = new long[] { getStamp() };
		FFLSOptimisticContext ctx;
		do { // This locker does not have any way of preventing other threads from modifying, so this loop does not terminate until
			ctx = new FFLSOptimisticContext(() -> getStamp(), stamp);
			res = operation.apply(res, ctx);
		} while (ctx.failed);
		return res;
	}

	@Override
	public int doOptimistically(int init, OptimisticIntOperation operation) {
		int res = init;
		long[] stamp = new long[] { getStamp() };
		FFLSOptimisticContext ctx;
		do { // This locker does not have any way of preventing other threads from modifying, so this loop does not terminate until
			ctx = new FFLSOptimisticContext(() -> getStamp(), stamp);
			res = operation.apply(res, ctx);
		} while (ctx.failed);
		return res;
	}

	static class FFLSOptimisticContext implements OptimisticContext {
		private final LongSupplier stampGetter;
		private final long[] stamp;
		boolean failed;

		FFLSOptimisticContext(LongSupplier stampGetter, long[] stamp) {
			this.stampGetter = stampGetter;
			this.stamp = stamp;
		}

		@Override
		public boolean check() {
			if (failed)
				return false;
			long newStamp = stampGetter.getAsLong();
			if (newStamp != stamp[0]) {
				stamp[0] = newStamp;
				failed = true;
				return false;
			}
			return true;
		}
	}
}
