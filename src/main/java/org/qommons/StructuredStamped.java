package org.qommons;

/** A structure that provides a stamp that changes when it is modified in 2 different ways */
public interface StructuredStamped extends Stamped {
	/**
	 * <p>
	 * Obtains a stamp with the current status of modifications to this structure, either structural or all changes. Whenever this structure
	 * is modified, the stamp changes. Thus 2 stamps can be compared to determine whether a structure has changed in between 2 calls to this
	 * method. For more information on <b>structural</b> changes, see {@link StructuredTransactable#lock(boolean, boolean, Object)}.
	 * </p>
	 * <p>
	 * The value returned from this method is <b>ONLY</b> for comparison. The value itself is not guaranteed to reveal anything about this
	 * structure or its history, e.g. the actual number of times it has been modified. Also, if 2 stamps obtained from this method are
	 * different, this does not guarantee that the structure was actually changed in any way, only that it might have been. It also cannot
	 * be 100% guaranteed that no modification (of the corresponding type) has been made to the structure if 2 stamps match; but an effort
	 * shall be made so that stamps never repeat during the lifetime of an application and to avoid changing the stamps when no modification
	 * is performed, if possible.
	 * </p>
	 * <p>
	 * No relationship is specified between stamps obtained with different parameters (structural/update).
	 * </p>
	 * 
	 * @param structuralOnly Whether to monitor only structural changes or all changes.
	 * @return The stamp for comparison
	 */
	long getStamp(boolean structuralOnly);

	@Override
	default long getStamp() {
		return getStamp(false);
	}
}
