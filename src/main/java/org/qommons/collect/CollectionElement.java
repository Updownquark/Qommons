package org.qommons.collect;

/**
 * Represents an element in a {@link BetterCollection} occupied by a (potentially null) value. In addition to very fast (usually
 * constant-time) access to the element's value, CollectionElements may be used to keep a parallel, ordered collection or map of a
 * collection's elements using the {@link #compareTo(Object) compareTo} method or via hashing, as a CollectionElement's {@link #hashCode()}
 * and {@link #equals(Object)} methods are for the element's position in the collection, not the value. The comparison properties of an
 * element are valid as long as the element remains {@link ElementId#isPresent() present} in the collection. The hash/equals properties
 * remain valid permanently, though a removed element may not equal anything but itself.
 * 
 * @param <E> The type of value in the element
 */
public interface CollectionElement<E> extends Comparable<CollectionElement<E>> {
	/** @return The ID of this element */
	ElementId getElementId();

	/** @return The current value of this element */
	E get();

	@Override
	default int compareTo(CollectionElement<E> other) {
		return getElementId().compareTo(other.getElementId());
	}

	/** @return An element identical to this, but whose {@link #getElementId() ID} is {@link ElementId#reverse() reversed} */
	default CollectionElement<E> reverse() {
		return new ReversedCollectionElement<>(this);
	}

	/**
	 * Implements {@link CollectionElement#reverse()}
	 * 
	 * @param <E> The type of the element
	 */
	class ReversedCollectionElement<E> implements CollectionElement<E> {
		private final CollectionElement<E> theWrapped;

		public ReversedCollectionElement(CollectionElement<E> wrapped) {
			theWrapped = wrapped;
		}

		protected CollectionElement<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public ElementId getElementId() {
			return theWrapped.getElementId().reverse();
		}

		@Override
		public E get() {
			return theWrapped.get();
		}

		@Override
		public CollectionElement<E> reverse() {
			return theWrapped;
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof CollectionElement && theWrapped.equals(((CollectionElement<?>) o).reverse());
		}

		@Override
		public String toString() {
			return "reverse(" + theWrapped + ")";
		}
	}

	/**
	 * @param <E> The type of the element
	 * @param element The element to reverse
	 * @return The given element, {@link CollectionElement#reverse() reversed}, or null if the given element is null
	 */
	static <E> CollectionElement<E> reverse(CollectionElement<E> element) {
		return element == null ? null : element.reverse();
	}

	/**
	 * @param element The element to get the ID for
	 * @return The given element's {@link #getElementId() ID}, or null if the element is null
	 */
	static ElementId getElementId(CollectionElement<?> element) {
		return element == null ? null : element.getElementId();
	}

	/**
	 * @param <E> The type of the element
	 * @param element The element to get the value of
	 * @return The element's {@link #get() value}, or null if the element is null
	 */
	static <E> E get(CollectionElement<E> element) {
		return element == null ? null : element.get();
	}
}
