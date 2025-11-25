package org.qommons.collect;

import java.util.Collection;

import org.qommons.CausalLock;
import org.qommons.DefaultCausalLock;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;

/** A locking strategy that is not thread-safe, but it allows fail-fast behavior; that is, detecting changes in a thread-unsafe manner. */
public class FastFailLockingStrategy implements CollectionLockingStrategy {
	private final CausalLock theCausalLock;
	private volatile long theStamp = 0;

	/** Creates the lock */
	public FastFailLockingStrategy() {
		theCausalLock = new DefaultCausalLock(new TransactableCore());
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return ThreadConstraint.ANY;
	}

	@Override
	public boolean isLockSupported() {
		return false; // We use the lock method a little, but let's don't advertise that we're thread-safe
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theCausalLock.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theCausalLock.tryLock(write, cause);
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return theCausalLock.getCurrentCauses();
	}

	@Override
	public CoreId getCoreId() {
		return CoreId.EMPTY;
	}

	@Override
	public long getStamp() {
		return theStamp;
	}

	@Override
	public void modified() {
		theStamp++;
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

	class TransactableCore implements Transactable {
		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstraint.ANY;
		}

		@Override
		public boolean isLockSupported() {
			return false; // We use the lock method a little, but let's don't advertise that we're thread-safe
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return lock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return CoreId.EMPTY;
		}
	}

	class FFLSOptimisticContext implements OptimisticContext {
		private long stamp;
		boolean failed;

		FFLSOptimisticContext(long stamp) {
			this.stamp = stamp;
		}

		@Override
		public boolean isOperationValid() {
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
