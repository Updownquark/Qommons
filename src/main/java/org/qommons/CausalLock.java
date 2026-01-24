package org.qommons;

import java.util.Collection;
import java.util.Collections;

import org.qommons.collect.SimpleDeque;

/** A lock that keeps track of the causes by which it is write-locked for eventing */
public interface CausalLock extends Transactable {
	/**
	 * A tagging interface that instructs this class not to wrap a particular cause passed to {@link CausalLock#lock(boolean, Object)} in a
	 * causable
	 */
	public interface Cause {
	}

	/** @return The currently active causes of write locks. This value may not be unmodifiable for performance purposes. */
	Collection<Cause> getCurrentCauses();

	/** @return The currently active causes of write locks which are not {@link Causable#isFinished() finished} being fired */
	default Collection<Cause> getUnfinishedCauses() {
		/* Pretty often, there won't be any unfinished causes here.
		 * Most of the rest of the time, there will just be a single one.
		 * Most of the rest of the time after that, there will be a very few.
		 */
		Cause singleCause = null;
		Collection<Cause> causes = null;
		Collection<Cause> allCauses = getCurrentCauses();
		for (Cause cause : allCauses) {
			if (cause instanceof Causable && !((Causable) cause).isFinished()) {
				if (singleCause == null)
					singleCause = cause;
				else {
					if (causes == null) {
						causes = new SimpleDeque<>();
						causes.add(singleCause);
					}
					causes.add(cause);
				}
			}
		}
		if (causes != null)
			return causes;
		else if (singleCause != null)
			return Collections.singleton(singleCause);
		else
			return Collections.emptySet();
	}

	/** @return The first Causable in this lock's {@link #getCurrentCauses() current causes} */
	default Causable getRootCausable() {
		for (Cause cause : getCurrentCauses()) {
			if (cause instanceof Causable && !((Causable) cause).isTerminated())
				return (Causable) cause;
		}
		return null;
	}

	/**
	 * @return Whether this lock has any causes that are {@link Causable#isFinished() finished } but not yet {@link Causable#isTerminated()
	 *         terminated}
	 */
	default boolean hasFinishingCauses() {
		Collection<Cause> causes = getCurrentCauses();
		for (Cause cause : causes) {
			if (cause instanceof Causable) {
				Causable causable = (Causable) cause;
				if (causable.isFinished() && !causable.isTerminated())
					return true;
			}
		}
		return false;
	}
}
