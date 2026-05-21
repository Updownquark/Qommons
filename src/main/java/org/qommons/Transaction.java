package org.qommons;

/** Represents a set of operations after which the {@link #close()} method must be called */
@FunctionalInterface
public interface Transaction extends AutoCloseable {
	/** A transaction that does nothing */
	static Transaction NONE = new Transaction() {
		@Override
		public void close() {
		}

		@Override
		public String toString() {
			return "NONE";
		}
	};

	@Override
	void close();

	/**
	 * @param ts The transactions to combine with this one
	 * @return A combined transaction that closes this transaction and all those given
	 */
	default Transaction combine(Transaction... ts) {
		if (ts.length == 0)
			return this;
		return () -> {
			this.close();
			for (Transaction t : ts) {
				if (t != null)
					t.close();
			}
		};
	}

	/**
	 * @param ts All the transactions to group
	 * @return A transaction whose {@link #close()} method closes all non-null transactions in the given list
	 */
	static Transaction and(Transaction... ts) {
		return new CombinedTransaction(ts);
	}

	/** A Transaction that will only execute its close action the first time it is {@link #close() closed} */
	public static class ReleaseOnceTransaction implements Transaction {
		private final Runnable theCloseAction;
		private boolean isClosed;

		/** @param closeAction The action to take when the transaction is closed */
		public ReleaseOnceTransaction(Runnable closeAction) {
			theCloseAction = closeAction;
		}

		/** @return Whether this transaction has been {@link #close()}d */
		public boolean isClosed() {
			return isClosed;
		}

		@Override
		public void close() {
			if (isClosed)
				return;
			synchronized (this) {
				if (isClosed)
					return;
				isClosed = true;
			}
			theCloseAction.run();
		}

		@Override
		public int hashCode() {
			return theCloseAction.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ReleaseOnceTransaction && theCloseAction.equals(((ReleaseOnceTransaction) obj).theCloseAction);
		}

		@Override
		public String toString() {
			return theCloseAction.toString();
		}
	}

	public static class CombinedTransaction implements Transaction {
		private final Transaction[] theComponents;

		public CombinedTransaction(Transaction[] components) {
			theComponents = components;
		}

		@Override
		public Transaction combine(Transaction... ts) {
			Transaction[] combined = new Transaction[theComponents.length + ts.length];
			int i = 0;
			for (Transaction t : ts)
				combined[i++] = t;
			for (Transaction t : theComponents)
				combined[i++] = t;
			return new CombinedTransaction(combined);
		}

		@Override
		public void close() {
			for (int i = theComponents.length - 1; i >= 0; i--) {
				Transaction t = theComponents[i];
				if (t != null) {
					t.close();
					theComponents[i] = null;
				}
			}
		}
	}
}
