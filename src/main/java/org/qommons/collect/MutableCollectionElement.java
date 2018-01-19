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
	}

	BetterCollection<E> getCollection();

	String isEnabled();

	String isAcceptable(E value);

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

	default String canAdd(E value, boolean before) {
		BetterCollection<E> coll = getCollection();
		ElementId id = getElementId();
		return coll.canAdd(value, //
			before ? CollectionElement.getElementId(coll.getAdjacentElement(id, false)) : id,
			before ? id : CollectionElement.getElementId(coll.getAdjacentElement(id, true)));
	}

	default ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
		BetterCollection<E> coll = getCollection();
		ElementId id = getElementId();
		CollectionElement<E> added = coll.addElement(value, //
			before ? CollectionElement.getElementId(coll.getAdjacentElement(id, false)) : id,
			before ? id : CollectionElement.getElementId(coll.getAdjacentElement(id, true)), !before);
		if (added != null)
			return added.getElementId();
		String msg = coll.canAdd(value, //
			before ? CollectionElement.getElementId(coll.getAdjacentElement(id, false)) : id,
			before ? id : CollectionElement.getElementId(coll.getAdjacentElement(id, true)));
		if (msg == null)
			throw new UnsupportedOperationException();
		else if (msg.equals(StdMsg.UNSUPPORTED_OPERATION))
			throw new UnsupportedOperationException(msg);
		else
			throw new IllegalArgumentException(msg);
	}

	/** @return An immutable observable element backed by this mutable element's data */
	default CollectionElement<E> immutable() {
		return new ImmutableCollectionElement<>(this);
	}

	@Override
	default MutableCollectionElement<E> reverse() {
		return new ReversedMutableElement<>(this);
	}

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

	class ReversedMutableElement<E> extends CollectionElement.ReversedCollectionElement<E> implements MutableCollectionElement<E> {
		public ReversedMutableElement(MutableCollectionElement<E> wrapped) {
			super(wrapped);
		}

		@Override
		protected MutableCollectionElement<E> getWrapped() {
			return (MutableCollectionElement<E>) super.getWrapped();
		}

		@Override
		public BetterCollection<E> getCollection() {
			return getWrapped().getCollection().reverse();
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
		public String canAdd(E value, boolean before) {
			return getWrapped().canAdd(value, !before);
		}

		@Override
		public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
			return getWrapped().add(value, !before).reverse();
		}

		@Override
		public MutableCollectionElement<E> reverse() {
			return (MutableCollectionElement<E>) super.reverse();
		}
	}

	static <E> MutableCollectionElement<E> reverse(MutableCollectionElement<E> element) {
		return element == null ? null : element.reverse();
	}

	static <E> CollectionElement<E> immutable(MutableCollectionElement<E> element) {
		return element == null ? null : element.immutable();
	}
}