package org.qommons;

import java.util.Collection;
import java.util.LinkedList;

import org.qommons.Lockable.CoreId;

/** A lock that keeps track of the causes by which it is write-locked for eventing */
public class CausalLock implements Transactable {
	/**
	 * A tagging interface that instructs this class not to wrap a particular cause passed to {@link CausalLock#lock(boolean, Object)} in a
	 * causable
	 */
	public interface Cause {
	}

	private final Transactable theLock;
	private final LinkedList<Cause> theTransactionCauses;

	/** @param lock The backing for this lock */
	public CausalLock(Transactable lock) {
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
		} else {
			tCause = Causable.simpleCause(cause);
			causeFinish = ((Causable) tCause).use();
		}
		if (write && tCause != null)
			theTransactionCauses.add(tCause);
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
					theTransactionCauses.removeLastOccurrence(tCause);
				valueLock.close();
			}
		};
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		Transaction t = theLock.tryLock(write, cause);
		return t == null ? null : addCause(t, write, cause);
	}

	/** @return The currently active causes of write locks. This value is not unmodifiable for performance purposes. */
	public Collection<Cause> getCurrentCauses() {
		return theTransactionCauses;
	}

	@Override
	public CoreId getCoreId() {
		return theLock.getCoreId();
	}
}
