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
