package org.qommons.ex;

/** An exception for declarations which cannot actually ever be thrown */
public final class NeverThrown extends RuntimeException {
	private NeverThrown() {
		super();
	}
}
