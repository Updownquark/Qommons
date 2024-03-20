package org.qommons.collect;

import org.qommons.CausalLock;
import org.qommons.Stamped;

/** A strategy for collection thread safety. Some implementations of this class may not be completely thread-safe for performance. */
public interface CollectionLockingStrategy extends CausalLock, Stamped {
	/** Increments the {@link #getStamp() stamp} */
	void modified();
}
