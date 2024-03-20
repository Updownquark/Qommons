package org.qommons;

import java.util.Collection;

/** A lock that keeps track of the causes by which it is write-locked for eventing */
public interface CausalLock extends Transactable {
	/**
	 * A tagging interface that instructs this class not to wrap a particular cause passed to {@link CausalLock#lock(boolean, Object)} in a
	 * causable
	 */
	public interface Cause {
	}

	/** @return The currently active causes of write locks. This value is not unmodifiable for performance purposes. */
	public Collection<Cause> getCurrentCauses();

	/** @return The currently active causes of write locks which are not {@link Causable#isFinished() finished} being fired */
	default Collection<Cause> getUnfinishedCauses() {
		// Most of the time, there won't be any unfinished causes here, so the most performant way to do this is to check first
		Collection<Cause> causes = getCurrentCauses();
		boolean anyUnfinished = false;
		for (Cause cause : causes) {
			if (cause instanceof Causable && ((Causable) cause).isFinished()) {
				anyUnfinished = true;
				break;
			}
		}
		if (!anyUnfinished)
			return causes;
		return QommonsUtils.filterMap(getCurrentCauses(), c -> !(c instanceof Causable) || !((Causable) c).isFinished(), null);
	}

	/** @return The first Causable in this lock's {@link #getCurrentCauses() current causes} */
	default Causable getRootCausable() {
		for (Cause cause : getCurrentCauses()) {
			if (cause instanceof Causable && !((Causable) cause).isTerminated())
				return (Causable) cause;
		}
		return null;
	}
}
