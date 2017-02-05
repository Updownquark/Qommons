package org.qommons;

/**
 * Represents a mutable object whose modifications may possibly be batched for increased efficiency.
 * 
 * Some interfaces may extend this interface, but support implementations that do not support locking. Hence the {@link #isLockSupported()}.
 * Such implementations should return a {@link Transaction#NONE none} transaction or some such non-null transaction.
 */
public interface Transactable {
	/**
	 * Begins a transaction in which inspections and/or modifications to this object may be batched and combined for increased efficiency.
	 *
	 * @param write Whether to lock this object for writing (prevents all access to controlled properties of the object outside of this
	 *            thread) or just for reading (prevents all modification to this object, this thread included).
	 * @param cause An object that may have caused the set of modifications to come. May be null.
	 * @return The transaction to close when calling code is finished accessing or modifying this object
	 */
	Transaction lock(boolean write, Object cause);

	/** @return Whether this object actually support locking */
	default boolean isLockSupported() {
		return true;
	}
}
