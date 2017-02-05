package org.qommons;

/** Represents a set of operations after which the {@link #close()} method must be called */
@FunctionalInterface
public interface Transaction extends AutoCloseable {
	/** A transaction that does nothing */
	static Transaction NONE = () -> {
	};

	@Override
	void close();
}
