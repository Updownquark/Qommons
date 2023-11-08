package org.qommons;

/** Represents a set of operations after which the {@link #close()} method must be called */
@FunctionalInterface
public interface Transaction extends AutoCloseable {
	/** A transaction that does nothing */
	static Transaction NONE = () -> {
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
		return () -> {
			for (int i = ts.length - 1; i >= 0; i--) {
				Transaction t = ts[i];
				if (t != null)
					t.close();
			}
		};
	}
}
