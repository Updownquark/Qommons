package org.qommons;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import org.qommons.Lockable.CoreId;

/** A lock that keeps track of the causes by which it is write-locked for eventing */
public class DefaultCausalLock implements CausalLock {
	private final Transactable theLock;
	private final LinkedList<Cause> theTransactionCauses;

	/** @param lock The backing for this lock */
	public DefaultCausalLock(Transactable lock) {
		theLock = lock;
		theTransactionCauses = new LinkedList<>();
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theLock.getThreadConstraint();
	}

	@Override
	public boolean isLockSupported() {
		return theLock.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		Transaction t = theLock.lock(write, cause);
		return addCause(t, write, cause);
	}

	private Transaction addCause(Transaction valueLock, boolean write, Object cause) {
		Cause tCause;
		Transaction causeFinish;
		if (cause == null && !theTransactionCauses.isEmpty()) {
			causeFinish = null;
			tCause = null;
		} else if (cause instanceof Cause) {
			tCause = (Cause) cause;
			causeFinish = null;
		} else if (write) {
			tCause = Causable.simpleCause(cause);
			causeFinish = ((Causable) tCause).use();
		} else {
			tCause = null;
			causeFinish = null;
		}
		if (write && tCause != null)
			theTransactionCauses.addFirst(tCause);
		return new Transaction() {
			private boolean isClosed;

			@Override
			public void close() {
				if (isClosed)
					return;
				isClosed = true;
				if (causeFinish != null) {
					try {
						causeFinish.close();
					} catch (RuntimeException | Error e) {
						e.printStackTrace();
					}
				}
				if (write && tCause != null)
					theTransactionCauses.removeFirstOccurrence(tCause);
				valueLock.close();
			}
		};
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		Transaction t = theLock.tryLock(write, cause);
		return t == null ? null : addCause(t, write, cause);
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return Collections.unmodifiableList(theTransactionCauses);
	}

	@Override
	public CoreId getCoreId() {
		return theLock.getCoreId();
	}
}
