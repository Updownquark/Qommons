package org.qommons.collect;

import org.qommons.Transaction;

/** A locking strategy that is not thread-safe, but it allows fail-fast behavior; that is, detecting changes in a thread-unsafe manner. */
public class FastFailLockingStrategy implements CollectionLockingStrategy {
	private volatile long theModCount = 0;
	private volatile long theStuctureChanges = 0;

	@Override
	public boolean isLockSupported() {
		return false;// We use the lock method a little, but let's don't advertise that we're thread-safe
	}

	@Override
	public Transaction lock(boolean write, boolean structural, Object cause) {
		return Transaction.NONE;
	}

	@Override
	public long getStatus(boolean structural) {
		return structural ? theStuctureChanges : theModCount;
	}

	@Override
	public void changed(boolean structural) {
		theModCount++;
		if (structural)
			theStuctureChanges++;
	}
}
