package org.qommons.collect;

/**
 * Although not every ObservableCollection must be indexed, all ObservableCollections must have some notion of order. All change events and
 * spliterator elements from ObservableCollections provide an ElementId that not only uniquely identifies the element in the collection, but
 * allows the element's order relative to other elements to be determined.
 *
 * The equivalence and ordering of ElementIds may not change with its contents or with any other property of the collection. The ElementId
 * must remain valid until the element is removed from the collection and thereafter until the collection is changed again.
 *
 * A collection's iteration must follow this ordering scheme as well, i.e. the ID of each element from the
 * {@link BetterCollection#spliterator()} method must be successively greater than the previous element
 *
 * @see CollectionElement#getElementId()
 */
public interface ElementId extends Comparable<ElementId> {
	/** @return Whether the element with this ID is still present in the collection */
	boolean isPresent();

	/**
	 * @param other The element that may be a source of this element
	 * @return Whether this element is derived from the other
	 */
	boolean isDerivedFrom(ElementId other);

	/** @return An element ID that behaves like this one, but orders in reverse */
	default ElementId reverse() {
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
			public boolean isDerivedFrom(ElementId other) {
				return theWrapped.isDerivedFrom(other);
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
		return new ReversedElementId(this);
	}

	/**
	 * @param id The element ID to reverse
	 * @return The {@link #reverse() reversed} element ID, or null if the given ID was null
	 */
	static ElementId reverse(ElementId id) {
		return id == null ? null : id.reverse();
	}
}
