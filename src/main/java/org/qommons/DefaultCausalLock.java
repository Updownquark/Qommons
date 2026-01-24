package org.qommons;

import java.util.*;

import org.qommons.Lockable.CoreId;
import org.qommons.collect.SimpleDeque;

/** A lock that keeps track of the causes by which it is write-locked for eventing */
public class DefaultCausalLock implements CausalLock {
	private final Transactable theLock;
	private final Deque<CauseSupplier> theTransactionCauses;

	/** @param lock The backing for this lock */
	public DefaultCausalLock(Transactable lock) {
		theLock = lock;
		theTransactionCauses = new SimpleDeque<>();
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
		CauseSupplier tCause;
		Transaction causeFinish;
		if (cause == null && (!write || hasCause())) {
			causeFinish = null;
			tCause = null;
		} else if (cause instanceof Cause) {
			tCause = new ConstantCause((Cause) cause);
			causeFinish = null;
		} else if (write) {
			tCause = new LazyCause(cause);
			causeFinish = ((LazyCause) tCause)::close;
		} else {
			tCause = null;
			causeFinish = null;
		}
		if (write && tCause != null) {
			theTransactionCauses.add(tCause);
		}
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

	private boolean hasCause() {
		boolean exception = false;
		do {
			try {
				Iterator<CauseSupplier> causeIter = theTransactionCauses.iterator();
				while (causeIter.hasNext()) {
					CauseSupplier cause = causeIter.next();
					if (cause.isTerminated())
						causeIter.remove();
					else
						return true;
				}
			} catch (ConcurrentModificationException e) {
				exception = true;
			}
		} while (exception);
		return false;
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return new CurrentCauses(theTransactionCauses);
	}

	@Override
	public Causable getRootCausable() {
		Iterator<CauseSupplier> causeIter = theTransactionCauses.iterator();
		while (causeIter.hasNext()) {
			CauseSupplier tCause = causeIter.next();
			if (tCause.isTerminated())
				causeIter.remove();
			else {
				Cause cause = tCause.get();
				if (cause instanceof Causable)
					return (Causable) cause;
			}
		}
		return null;
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

		@Override
		public Object[] toArray() {
			Object[] array = theCauses.toArray();
			for (int i = 0; i < array.length; i++)
				array[i] = ((CauseSupplier) array[i]).get();
			return array;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			Object[] array = toArray();
			if (a.length < array.length)
				a = Arrays.copyOf(a, array.length);
			System.arraycopy(array, 0, a, 0, array.length);
			return a;
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
