package org.qommons.collect;

/**
 * Represents an element in a collection that contains a value (retrieved via {@link #get()}) that may {@link #isAcceptable(Object)
 * possibly} be {@link #set(Object) replaced} or (again {@link #canRemove() possibly}) {@link #remove() removed} during iteration.
 * 
 * @param <E> The type of the value in the element
 */
public interface MutableCollectionElement<E> extends CollectionElement<E> {
	/** Standard messages returned by this class */
	interface StdMsg {
		static String BAD_TYPE = "Object is the wrong type for this collection";
		static String UNSUPPORTED_OPERATION = "Unsupported Operation";
		static String NULL_DISALLOWED = "Null is not allowed";
		static String ELEMENT_EXISTS = "Element already exists";
		static String ILLEGAL_ELEMENT_POSITION = "Element is not allowed there";
		static String GROUP_EXISTS = "Group already exists";
		static String WRONG_GROUP = "Item does not belong to this group";
		static String NOT_FOUND = "No such item found";
		static String ILLEGAL_ELEMENT = "Element is not allowed";
		static String ELEMENT_REMOVED = "Element has been removed";
	}

	@Override
	MutableCollectionElement<E> getAdjacent(boolean next);

	/** @return null if {@link #set(Object)} may succeed for some values, or a message describing why it is unsupported */
	String isEnabled();

	/**
	 * Tests whether a given value could replace this element's current value in the collection
	 * 
	 * @param value The value to check
	 * @return null if {@link #set(Object)} would succeed for the given value, or a message describing why it would fail
	 */
	String isAcceptable(E value);

	/**
	 * Replaces this element's value with the given value
	 * 
	 * @param value The value to replace this element's value with
	 * @throws UnsupportedOperationException If the operation is unsupported, independent of the argument
	 * @throws IllegalArgumentException If some property of the argument prevents it being a replacement for this element's value
	 */
	void set(E value) throws UnsupportedOperationException, IllegalArgumentException;

	/** @return null if this element can be removed. Non-null indicates a message describing why removal is prevented. */
	String canRemove();

	/**
	 * Removes this element from the source collection
	 * 
	 * @throws UnsupportedOperationException If the element cannot be removed
	 * @see #canRemove()
	 */
	void remove() throws UnsupportedOperationException;

	/** @return An immutable observable element backed by this mutable element's data */
	default CollectionElement<E> immutable() {
		return new ImmutableCollectionElement<>(this);
	}

	@Override
	default MutableCollectionElement<E> reverse() {
		return new ReversedMutableElement<>(this);
	}

	/**
	 * Implements {@link MutableCollectionElement#immutable()}
	 * 
	 * @param <E> The type of the element
	 */
	class ImmutableCollectionElement<E> implements CollectionElement<E>{
		private final MutableCollectionElement<E> theWrapped;

		public ImmutableCollectionElement(MutableCollectionElement<E> wrapped) {
			theWrapped = wrapped;
		}
		
		protected MutableCollectionElement<E> getWrapped(){
			return theWrapped;
		}

		@Override
		public ElementId getElementId() {
			return theWrapped.getElementId();
		}

		@Override
		public E get(){
			return theWrapped.get();
		}

		@Override
		public CollectionElement<E> getAdjacent(boolean next) {
			return immutable(theWrapped.getAdjacent(next));
		}

		@Override
		public int compareTo(CollectionElement<E> o) {
			return theWrapped.compareTo(strip(o));
		}

		private CollectionElement<E> strip(CollectionElement<E> o) {
			if(o instanceof ImmutableCollectionElement)
				o=((ImmutableCollectionElement<E>) o).getWrapped();
			return o;
		}
		
		@Override
		public int hashCode(){
			return theWrapped.hashCode();
		}
		
		@Override
		public boolean equals(Object o){
			return o instanceof CollectionElement && theWrapped.equals(strip((CollectionElement<E>) o));
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link MutableCollectionElement#reverse()}
	 * 
	 * @param <E> The type of the element
	 */
	class ReversedMutableElement<E> extends CollectionElement.ReversedCollectionElement<E> implements MutableCollectionElement<E> {
		public ReversedMutableElement(MutableCollectionElement<E> wrapped) {
			super(wrapped);
		}

		@Override
		protected MutableCollectionElement<E> getWrapped() {
			return (MutableCollectionElement<E>) super.getWrapped();
		}

		@Override
		public MutableCollectionElement<E> getAdjacent(boolean next) {
			return MutableCollectionElement.reverse(getWrapped().getAdjacent(!next));
		}

		@Override
		public String isEnabled() {
			return getWrapped().isEnabled();
		}

		@Override
		public String isAcceptable(E value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			getWrapped().set(value);
		}

		@Override
		public String canRemove() {
			return getWrapped().canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			getWrapped().remove();
		}

		@Override
		public MutableCollectionElement<E> reverse() {
			return (MutableCollectionElement<E>) super.reverse();
		}
	}

	/**
	 * @param <E> The type of the element
	 * @param element The element to reverse
	 * @return The given element, {@link MutableCollectionElement#reverse() reversed}, or null if the given element is null
	 */
	static <E> MutableCollectionElement<E> reverse(MutableCollectionElement<E> element) {
		return element == null ? null : element.reverse();
	}

	/**
	 * @param <E> The type of the element
	 * @param element The element to get an immutable copy of
	 * @return An {@link MutableCollectionElement#immutable() immutable} copy of the given element, or null if the given element is null
	 */
	static <E> CollectionElement<E> immutable(MutableCollectionElement<E> element) {
		return element == null ? null : element.immutable();
	}
}