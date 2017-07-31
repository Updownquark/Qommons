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
	ElementId getElementId();

	E get();

	@Override
	default int compareTo(CollectionElement<E> other) {
		return getElementId().compareTo(other.getElementId());
	}

	default CollectionElement<E> reverse() {
		return new ReversedCollectionElement<>(this);
	}

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
	}

	static <E> CollectionElement<E> reverse(CollectionElement<E> element) {
		return element == null ? null : element.reverse();
	}
}
