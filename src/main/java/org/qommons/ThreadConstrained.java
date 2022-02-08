package org.qommons;

import java.util.Arrays;
import java.util.function.Function;

/** Anything that may fire events of some kind */
public interface ThreadConstrained {
	/**
	 * The message for an {@link UnsupportedOperationException} to be fired when an attempt is made to write-lock or modify a constraint
	 * object on a thread that is not its {@link ThreadConstraint#isEventThread() event thread}
	 */
	public static final String WRONG_THREAD_MESSAGE = "This object cannot be write-locked or modified on the current thread";

	/** @return The thread constraints that this eventable obeys */
	ThreadConstraint getThreadConstraint();

	/**
	 * A builder for some kind of eventable
	 *
	 * @param <B> The type of the builder
	 */
	public interface Builder<B extends Builder<? extends B>> {
		/**
		 * @param threadConstraint The thread constraint for objects built with this builder to obey
		 * @return This builder
		 */
		B withThreadConstraint(ThreadConstraint threadConstraint);

		/**
		 * Identical to
		 * <code>{@link #withThreadConstraint(ThreadConstraint) withThreadConstraint}({@link ThreadConstraint#EDT ThreadConstraint.EDT})</code>
		 * 
		 * @return This builder
		 */
		default B onEdt() {
			return withThreadConstraint(ThreadConstraint.EDT);
		}
	}

	/**
	 * @param <X> The type of thread-constrained objects to inspect
	 * @param first The first thread-constrained object
	 * @param others Other thread-constrained objects
	 * @param map The map of the other thread-constrained objects to actual {@link ThreadConstrained} instances
	 * @return The {@link ThreadConstraint} common to all the thread-constrained objects, or {@link ThreadConstraint#ANY} if they do not all
	 *         have common a constraint
	 */
	public static <X> ThreadConstraint getThreadConstraint(ThreadConstrained first, Iterable<? extends X> others,
		Function<? super X, ? extends ThreadConstrained> map) {
		ThreadConstraint c = first == null ? ThreadConstraint.NONE : first.getThreadConstraint();
		for (X v : others) {
			if (v == null)
				continue;
			ThreadConstraint vc = map.apply(v).getThreadConstraint();
			if (vc != ThreadConstraint.NONE) {
				if (c == ThreadConstraint.NONE)
					c = vc;
				else if (vc != c)
					return ThreadConstraint.ANY;
			}
		}
		return c;
	}

	/**
	 * @param constrained The thread-constrained objects to inspect
	 * @return The {@link ThreadConstraint} common to all the thread-constrained objects, or {@link ThreadConstraint#ANY} if they do not all
	 *         have common a constraint
	 */
	public static ThreadConstraint getThreadConstraint(ThreadConstrained... constrained) {
		return getThreadConstraint(Arrays.asList(constrained));
	}

	/**
	 * @param constrained The thread-constrained objects to inspect
	 * @return The {@link ThreadConstraint} common to all the thread-constrained objects, or {@link ThreadConstraint#ANY} if they do not all
	 *         have common a constraint
	 */
	public static ThreadConstraint getThreadConstraint(Iterable<? extends ThreadConstrained> constrained) {
		ThreadConstraint c = ThreadConstraint.NONE;
		for (ThreadConstrained tc : constrained) {
			if (tc == null)
				continue;
			ThreadConstraint vc = tc.getThreadConstraint();
			if (vc != ThreadConstraint.NONE) {
				if (c == ThreadConstraint.NONE)
					c = vc;
				else if (vc != c)
					return ThreadConstraint.ANY;
			}
		}
		return c;
	}
}
