package org.qommons.collect;

/**
 * <p>
 * Although not every BetterCollection must be indexed, all BetterCollections must have a consistent of order. Each element in a
 * BetterCollection has an ElementId that not only uniquely identifies the element in the collection, but allows the element's order to be
 * determined relative to other elements in the collection.
 * </p>
 *
 * <p>
 * The equivalence and ordering of ElementIds may not change with its value or with any other property of the collection. The ElementId must
 * remain comparable to other elements in the collection until the element is removed from the collection <b>and afterward</b> until the
 * collection is changed again.
 * </p>
 * 
 * <p>
 * A collection may create new ElementId instances each time one is requested, but different ElementId instances for a common element must
 * return 0 from {@link #compareTo(ElementId)} for equivalent elements. They must also {@link #equals(Object)} each other (and have a common
 * {@link #hashCode()}) forever, no matter how long ago the element was removed from the collection or how much it has changed since.
 * </p>
 *
 * <p>
 * A collection's iteration must follow this ordering scheme as well, iteration must go in order of the ID collection's elements.
 * </p>
 *
 * @see CollectionElement#getElementId()
 */
public interface ElementId extends Comparable<ElementId> {
	/** @return Whether the element with this ID is still present in the collection */
	boolean isPresent();

	/** @return An element ID that behaves like this one, but orders in reverse */
	default ElementId reverse() {
		return new ReversedElementId(this);
	}

	/** Implements {@link ElementId#reverse()} */
	class ReversedElementId implements ElementId {
		private final ElementId theWrapped;

		ReversedElementId(ElementId wrap) {
			theWrapped = wrap;
		}

		@Override
		public boolean isPresent() {
			return theWrapped.isPresent();
		}

		@Override
		public int compareTo(ElementId o) {
			return -theWrapped.compareTo(((ReversedElementId) o).theWrapped);
		}

		@Override
		public ElementId reverse() {
			return theWrapped;
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof ReversedElementId && theWrapped.equals(((ReversedElementId) obj).theWrapped);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * @param id The element ID to reverse
	 * @return The {@link #reverse() reversed} element ID, or null if the given ID was null
	 */
	static ElementId reverse(ElementId id) {
		return id == null ? null : id.reverse();
	}
}
