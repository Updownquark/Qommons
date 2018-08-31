package org.qommons.collect;

import java.util.function.LongSupplier;

import org.qommons.Transaction;

/** A locking strategy that is not thread-safe, but it allows fail-fast behavior; that is, detecting changes in a thread-unsafe manner. */
public class FastFailLockingStrategy implements CollectionLockingStrategy {
	private volatile long theModCount = 0;
	private volatile long theStuctureChanges = 0;

	@Override
	public boolean isLockSupported() {
		return false; // We use the lock method a little, but let's don't advertise that we're thread-safe
	}

	@Override
	public Transaction lock(boolean write, boolean structural, Object cause) {
		if (write) {
			if (structural)
				theStuctureChanges++;
			else
				theModCount++;
		}
		return Transaction.NONE;
	}

	@Override
	public Transaction tryLock(boolean write, boolean structural, Object cause) {
		return lock(write, structural, cause);
	}

	@Override
	public long getStamp(boolean structural) {
		return structural ? theStuctureChanges : theModCount;
	}

	@Override
	public <T> T doOptimistically(T init, OptimisticOperation<T> operation, boolean allowUpdate) {
		T res = init;
		long[] stamp = new long[] { getStamp(allowUpdate) };
		FFLSOptimisticContext ctx;
		do { // This locker does not have any way of preventing other threads from modifying, so this loop does not terminate until
			ctx = new FFLSOptimisticContext(() -> getStamp(allowUpdate), stamp);
			res = operation.apply(res, ctx);
		} while (ctx.failed);
		return res;
	}

	@Override
	public int doOptimistically(int init, OptimisticIntOperation operation, boolean allowUpdate) {
		int res = init;
		long[] stamp = new long[] { getStamp(allowUpdate) };
		FFLSOptimisticContext ctx;
		do { // This locker does not have any way of preventing other threads from modifying, so this loop does not terminate until
			ctx = new FFLSOptimisticContext(() -> getStamp(allowUpdate), stamp);
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
