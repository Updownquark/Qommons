package org.qommons;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.qommons.Lockable.CoreId;
import org.qommons.ProgramTracker.TrackNode;

/** A lock that keeps track of the causes by which it is write-locked for eventing */
public class DefaultCausalLock implements CausalLock {
	private final Transactable theLock;
	private final LinkedList<CauseSupplier> theTransactionCauses;

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
		ProgramTracker tracker = ProgramTracker.getThreadTracker();
		try (TrackNode root = tracker.start("lock")) {
			TrackNode node = tracker.start("coreLock");
			Transaction t = theLock.lock(write, cause);
			node.close();
			return addCause(t, write, cause, tracker);
		}
	}

	private Transaction addCause(Transaction valueLock, boolean write, Object cause, ProgramTracker tracker) {
		TrackNode root = tracker.start("addCause");
		CauseSupplier tCause;
		Transaction causeFinish;
		if (cause == null && (!write || hasCause(tracker))) {
			causeFinish = null;
			tCause = null;
		} else if (cause instanceof Cause) {
			TrackNode node = tracker.start("CC");
			tCause = new ConstantCause((Cause) cause);
			node.close();
			causeFinish = null;
		} else if (write) {
			TrackNode node = tracker.start("LC");
			tCause = new LazyCause(cause);
			node.close();
			causeFinish = ((LazyCause) tCause)::close;
		} else {
			tCause = null;
			causeFinish = null;
		}
		if (write && tCause != null) {
			TrackNode node = tracker.start("tcAdd");
			theTransactionCauses.add(tCause);
			node.close();
		}
		root.close();
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
		ProgramTracker tracker = ProgramTracker.getThreadTracker();
		try (TrackNode root = tracker.start("lock")) {
			TrackNode node = tracker.start("coreLock");
			Transaction t = theLock.tryLock(write, cause);
			node.close();
			return t == null ? null : addCause(t, write, cause, tracker);
		}
	}

	private boolean hasCause(ProgramTracker tracker) {
		TrackNode node = tracker.start("hasCause");
		Iterator<CauseSupplier> causeIter = theTransactionCauses.iterator();
		boolean hasCause = false;
		while (causeIter.hasNext()) {
			CauseSupplier cause = causeIter.next();
			if (cause.isTerminated())
				causeIter.remove();
			else
				hasCause = true;
		}
		node.close();
		return hasCause;
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return new CurrentCauses(theTransactionCauses);
	}

	@Override
	public CoreId getCoreId() {
		return theLock.getCoreId();
	}

	@Override
	public <T> T doOptimistically(T init, OptimisticOperation<T> operation) {
		return theLock.doOptimistically(init, operation);
	}

	@Override
	public int doOptimistically(int init, OptimisticIntOperation operation) {
		return theLock.doOptimistically(init, operation);
	}

	private interface CauseSupplier {
		Cause get();

		boolean isTerminated();
	}

	static class CurrentCauses extends AbstractCollection<Cause> {
		private final Collection<CauseSupplier> theCauses;

		CurrentCauses(Collection<CauseSupplier> causes) {
			theCauses = causes;
		}

		@Override
		public Iterator<Cause> iterator() {
			Iterator<CauseSupplier> causesIter = theCauses.iterator();
			return new Iterator<Cause>() {
				@Override
				public boolean hasNext() {
					return causesIter.hasNext();
				}

				@Override
				public Cause next() {
					return causesIter.next().get();
				}
			};
		}

		@Override
		public int size() {
			return theCauses.size();
		}
	}

	static class ConstantCause implements CauseSupplier {
		private final Cause theCause;

		ConstantCause(Cause cause) {
			theCause = cause;
		}

		@Override
		public Cause get() {
			return theCause;
		}

		@Override
		public boolean isTerminated() {
			return theCause instanceof Causable && ((Causable) theCause).isTerminated();
		}

		@Override
		public String toString() {
			return theCause.toString();
		}
	}

	static class LazyCause implements CauseSupplier, Transaction {
		private final Object theSource;
		private Causable theCause;
		private Transaction theFinish;

		LazyCause(Object source) {
			theSource = source;
		}

		@Override
		public Cause get() {
			if (theCause == null) {
				theCause = Causable.simpleCause(theSource);
				theFinish = theCause.use();
			}
			return theCause;
		}

		@Override
		public boolean isTerminated() {
			return theCause != null && theCause.isTerminated();
		}

		@Override
		public void close() {
			if (theFinish != null) {
				theFinish.close();
				theFinish = null;
			}
		}

		@Override
		public String toString() {
			return "LazyCause:" + theSource;
		}
	}
}
