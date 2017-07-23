package org.qommons.collect;

import org.qommons.value.Settable;

/**
 * Represents an element in a collection that contains a value (retrieved via {@link Settable#get()}) that may {@link #isAcceptable(Object)
 * possibly} be {@link #set(Object) replaced} or (again {@link #canRemove() possibly}) {@link #remove() removed} during iteration.
 * 
 * @param <E> The type of the value in the element
 */
public interface MutableElementHandle<E> extends ElementHandle<E> {
	/** Standard messages returned by this class */
	interface StdMsg {
		static String BAD_TYPE = "Object is the wrong type for this collection";
		static String UNSUPPORTED_OPERATION = "Unsupported Operation";
		static String NULL_DISALLOWED = "Null is not allowed";
		static String ELEMENT_EXISTS = "Element already exists";
		static String GROUP_EXISTS = "Group already exists";
		static String WRONG_GROUP = "Item does not belong to this group";
		static String NOT_FOUND = "No such item found";
		static String ILLEGAL_ELEMENT = "Element is not allowed";
	}

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

	String canAdd(E value, boolean before);
	void add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException;

	/** @return An immutable observable element backed by this mutable element's data */
	default ElementHandle<E> immutable() {
		return new ElementHandle<E>() {
			@Override
			public E get() {
				return MutableElementHandle.this.get();
			}

			@Override
			public ElementId getElementId() {
				return MutableElementHandle.this.getElementId();
			}

			@Override
			public String toString() {
				return MutableElementHandle.this.toString();
			}
		};
	}

	@Override
	default MutableElementHandle<E> reverse() {
		return new ReversedMutableElement<>(this);
	}

	class ReversedMutableElement<E> extends ElementHandle.ReversedElementHandle<E>
		implements MutableElementHandle<E> {
		public ReversedMutableElement(MutableElementHandle<E> wrapped) {
			super(wrapped);
		}

		@Override
		protected MutableElementHandle<E> getWrapped() {
			return (MutableElementHandle<E>) super.getWrapped();
		}

		@Override
		public ElementId getElementId() {
			return getWrapped().getElementId().reverse();
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
		public void add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
			getWrapped().add(value, !before);
		}

		@Override
		public MutableElementHandle<E> reverse() {
			return (MutableElementHandle<E>) super.reverse();
		}
	}
}