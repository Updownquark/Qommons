package org.qommons.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

import org.qommons.ThreadConstraint;
import org.qommons.Transaction;

/**
 * A {@link BetterCollection} that allows control over attempted modifications and listening for such modifications
 * 
 * @param <E> The type of values in the collection
 * @param <C> The sub type of this collection
 */
public class ModControlledCollection<E, C extends BetterCollection<E>> implements BetterCollection<E> {
	/**
	 * Interface determining which collection modifications are allowed
	 * 
	 * @param <E> The type of elements in the controlled collection
	 */
	public interface CollectionModificationControl<E> {
		/**
		 * Implementation for {@link BetterCollection#canAdd(Object, ElementId, ElementId)}
		 * 
		 * @param value The value to be added
		 * @param after The element to add the value after
		 * @param before The element to add the value before
		 * @return Whether the value can be added
		 */
		String canAdd(E value, ElementId after, ElementId before);

		/**
		 * Implementation for {@link MutableCollectionElement#canRemove()}
		 * 
		 * @param element The element to remove
		 * @return Whether the element can be removed
		 */
		String canRemove(CollectionElement<E> element);

		/**
		 * Implementation for {@link MutableCollectionElement#isEnabled()}
		 * 
		 * @param element The element to modify
		 * @return Whether the element can be modified
		 */
		String isModifiable(CollectionElement<E> element);

		/**
		 * Implementation for {@link MutableCollectionElement#isAcceptable(Object)}
		 * 
		 * @param element The element to modify
		 * @param newValue The value to set for the element
		 * @return Whether the element can be modified with the given value
		 */
		String isAcceptable(CollectionElement<E> element, E newValue);

		/**
		 * Implementation for {@link BetterCollection#canMove(ElementId, ElementId, ElementId)}
		 * 
		 * @param element The element to move
		 * @param after The element to move the element after
		 * @param before The element to move the element before
		 * @return Whether the element can be moved
		 */
		String canMove(CollectionElement<E> element, ElementId after, ElementId before);
	}


	/**
	 * Interface reporting any modifications to a collection
	 * 
	 * @param <E> The type of elements in the controlled collection
	 */
	public interface CollectionModificationListener<E> {
		/** @param element The element that was added */
		void elementAdded(CollectionElement<E> element);

		/**
		 * Called before an element is removed from a collection. This method may do the removal itself, or it may do nothing.
		 * 
		 * @param element The element to be removed
		 */
		void elementPreRemove(MutableCollectionElement<E> element);

		/** @param element The element that was removed */
		void elementRemoved(CollectionElement<E> element);

		/**
		 * @param element The element that was replaced with {@link MutableCollectionElement#set(Object)}
		 * @param previousValue The previous value in the element
		 */
		void elementReplaced(CollectionElement<E> element, E previousValue);

		/**
		 * Called before an element is moved in the collection
		 * 
		 * @param element The element that will be moved
		 * @param after The element that the target element will be moved after
		 * @param before The element that the target element will be moved before
		 * @return Data that will be passed to {@link #elementMoved(CollectionElement, Object)} after the move operation
		 */
		Object elementPreMove(CollectionElement<E> element, ElementId after, ElementId before);

		/**
		 * @param element The element that was moved
		 * @param moveData The data returned from {@link #elementPreMove(CollectionElement, ElementId, ElementId)} before the move
		 */
		void elementMoved(CollectionElement<E> element, Object moveData);
	}

	/**
	 * Creates a collection that is modification-controlled
	 * 
	 * @param <E> The type of elements in the collection
	 * @param <C> The sub-type of the collection
	 * @param collection The collection to control
	 * @param control The optional modification controller
	 * @param listener The optional modification listener
	 * @return The modification-controlled collection
	 */
	public static <E, C extends BetterCollection<E>> C controlCollection(C collection, CollectionModificationControl<E> control,
		CollectionModificationListener<E> listener) {
		if (collection instanceof BetterSortedSet)
			return (C) new ModControlledSortedSet<>((BetterSortedSet<E>) collection, control, listener);
		else if (collection instanceof BetterSortedList)
			return (C) new ModControlledSortedList<>((BetterSortedList<E>) collection, control, listener);
		else if (collection instanceof BetterList)
			return (C) new ModControlledList<>((BetterList<E>) collection, control, listener);
		else if (collection instanceof BetterSet)
			return (C) new ModControlledSet<>((BetterSet<E>) collection, control, listener);
		else
			return (C) new ModControlledCollection<>(collection, control, listener);
	}

	private final C theBacking;
	private final CollectionModificationControl<E> theControl;
	private final CollectionModificationListener<E> theListener;

	/**
	 * @param backing The collection to control
	 * @param control The optional modification controller
	 * @param listener The optional modification listener
	 */
	protected ModControlledCollection(C backing, CollectionModificationControl<E> control, CollectionModificationListener<E> listener) {
		theBacking = backing;
		theControl = control;
		theListener = listener;
	}

	/** @return The modifiable content of this collection */
	protected C getBacking() {
		return theBacking;
	}

	/** @return The modification controller for this collection. May be null if this collection is not actually modification-restricted. */
	public CollectionModificationControl<E> getControl() {
		return theControl;
	}

	/** @return The modification listener for this collection. May be null */
	protected CollectionModificationListener<E> getListener() {
		return theListener;
	}

	@Override
	public Object getIdentity() {
		return theBacking.getIdentity();
	}

	@Override
	public BetterCollection<E> alias(String alias) {
		theBacking.alias(alias);
		return this;
	}

	@Override
	public Set<String> getAliases() {
		return theBacking.getAliases();
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return theBacking.getCurrentCauses();
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theBacking.getThreadConstraint();
	}

	@Override
	public Transaction lock(boolean tryOnly) {
		return theBacking.lock(tryOnly);
	}

	@Override
	public Transaction lockWrite(boolean tryOnly, Object cause) {
		return theBacking.lockWrite(tryOnly, cause);
	}

	@Override
	public CoreId getCoreId() {
		return theBacking.getCoreId();
	}

	@Override
	public long getStamp() {
		return theBacking.getStamp();
	}

	@Override
	public int size() {
		return theBacking.size();
	}

	@Override
	public boolean isEmpty() {
		return theBacking.isEmpty();
	}

	@Override
	public CollectionElement<E> getElement(E value, boolean first) {
		return theBacking.getElement(value, first);
	}

	@Override
	public CollectionElement<E> getElement(ElementId id) {
		return theBacking.getElement(id);
	}

	@Override
	public CollectionElement<E> getTerminalElement(boolean first) {
		return theBacking.getTerminalElement(first);
	}

	@Override
	public MutableCollectionElement<E> mutableElement(ElementId id) {
		return wrapMutable(theBacking.mutableElement(id));
	}

	@Override
	public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return BetterList.single(getElement(sourceEl));
		return theBacking.getElementsBySource(sourceEl, sourceCollection);
	}

	@Override
	public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return BetterList.single(localElement);
		return theBacking.getSourceElements(localElement, sourceCollection);
	}

	@Override
	public ElementId getEquivalentElement(ElementId equivalentEl) {
		return theBacking.getEquivalentElement(equivalentEl);
	}

	@Override
	public String canAdd(E value, ElementId after, ElementId before) {
		String msg = theControl == null ? null : theControl.canAdd(value, after, before);
		if (msg == null)
			msg = theBacking.canAdd(value, after, before);
		return msg;
	}

	@Override
	public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		String msg = theControl == null ? null : theControl.canAdd(value, after, before);
		if (msg != null)
			throw new UnsupportedOperationException(msg);
		CollectionElement<E> added = theBacking.addElement(value, after, before, first);
		if (added != null && theListener != null)
			theListener.elementAdded(added);
		return added;
	}

	@Override
	public String canMove(ElementId valueEl, ElementId after, ElementId before) {
		String msg = theControl == null ? null : theControl.canMove(getElement(valueEl), after, before);
		if (msg == null)
			msg = theBacking.canMove(valueEl, after, before);
		return msg;
	}

	@Override
	public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		Object moveData;
		if (theControl != null || theListener != null) {
			CollectionElement<E> from = getElement(valueEl);
			String msg = theControl == null ? null : theControl.canMove(from, after, before);
			if (msg != null)
				throw new UnsupportedOperationException(msg);
			moveData = theListener == null ? null : theListener.elementPreMove(from, after, before);
		} else
			moveData = null;
		CollectionElement<E> moved = theBacking.move(valueEl, after, before, first, afterRemove);
		if (theListener != null)
			theListener.elementMoved(moved, moveData);
		return moved;
	}

	@Override
	public void clear() {
		if (theControl == null && theListener == null) {
			theBacking.clear();
			return;
		}
		for (CollectionElement<E> element = getTerminalElement(true); element != null; element = element.getAdjacent(true)) {
			MutableCollectionElement<E> mutable = mutableElement(element.getElementId());
			if (mutable.canRemove() == null)
				mutable.remove();
		}
	}

	@Override
	public boolean contains(Object o) {
		return theBacking.contains(o);
	}

	@Override
	public int hashCode() {
		return theBacking.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return theBacking.equals(obj);
	}

	@Override
	public String toString() {
		return theBacking.toString();
	}

	/**
	 * @param backingElement The source mutable element
	 * @return The wrapped modification-controlled element
	 */
	protected MutableCollectionElement<E> wrapMutable(MutableCollectionElement<E> backingElement) {
		return new MutableElementWrapper(backingElement);
	}

	/** Default implementation for {@link ModControlledCollection#wrapMutable(MutableCollectionElement)} */
	public class MutableElementWrapper implements MutableCollectionElement<E> {
		private final MutableCollectionElement<E> theBackingElement;

		/** @param backingElement The source mutable element */
		protected MutableElementWrapper(MutableCollectionElement<E> backingElement) {
			theBackingElement = backingElement;
		}

		/** @return The source mutable element */
		protected MutableCollectionElement<E> getBackingElement() {
			return theBackingElement;
		}

		@Override
		public ElementId getElementId() {
			return theBackingElement.getElementId();
		}

		@Override
		public E get() {
			return theBackingElement.get();
		}

		@Override
		public MutableCollectionElement<E> getAdjacent(boolean next) {
			MutableCollectionElement<E> adj = theBackingElement.getAdjacent(next);
			return adj == null ? null : wrapMutable(adj);
		}

		@Override
		public String isEnabled() {
			String msg = theControl == null ? null : theControl.isModifiable(theBackingElement);
			if (msg == null)
				msg = theBackingElement.isEnabled();
			return msg;
		}

		@Override
		public String isAcceptable(E value) {
			String msg = theControl == null ? null : theControl.isAcceptable(theBackingElement, value);
			if (msg == null)
				msg = theBackingElement.isAcceptable(value);
			return msg;
		}

		@Override
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			if (theControl == null && theListener == null) {
				theBackingElement.set(value);
				return;
			}
			try (Transaction t = lockWrite(false, null)) {
				String msg = theControl == null ? null : theControl.isAcceptable(theBackingElement, value);
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				E prev = theBackingElement.get();
				theBackingElement.set(value);
				if (theListener != null)
					theListener.elementReplaced(theBackingElement, prev);
			}
		}

		@Override
		public String canRemove() {
			String msg = theControl == null ? null : theControl.canRemove(theBackingElement);
			if (msg == null)
				msg = theBackingElement.canRemove();
			return msg;
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			if (theControl == null && theListener == null) {
				theBackingElement.remove();
				return;
			}
			try (Transaction t = lockWrite(false, null)) {
				String msg = theControl == null ? null : theControl.canRemove(theBackingElement);
				if (msg == null)
					theBackingElement.canRemove();
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				if (theListener != null)
					theListener.elementPreRemove(theBackingElement);
				if (theBackingElement.getElementId().isPresent()) // Allow the controller to do it if it wants
					theBackingElement.remove();
				if (theListener != null)
					theListener.elementRemoved(theBackingElement);
			}
		}

		@Override
		public String toString() {
			return theBackingElement.toString();
		}
	}

	/**
	 * List implementation of {@link ModControlledCollection}
	 * 
	 * @param <E> The type of elements in the list
	 * @param <C> The sub-type of the list
	 */
	public static class ModControlledList<E, C extends BetterList<E>> extends ModControlledCollection<E, C> implements BetterList<E> {
		/**
		 * @param backing The list to control
		 * @param control The optional modification controller
		 * @param listener The optional modification listener
		 */
		public ModControlledList(C backing, CollectionModificationControl<E> control, CollectionModificationListener<E> listener) {
			super(backing, control, listener);
		}

		@Override
		protected MutableListElement<E> wrapMutable(MutableCollectionElement<E> backingElement) {
			return new MutableListElementWrapper((MutableListElement<E>) backingElement);
		}

		@Override
		public ListElement<E> getElement(E value, boolean first) {
			return (ListElement<E>) super.getElement(value, first);
		}

		@Override
		public ListElement<E> getElement(ElementId id) {
			return (ListElement<E>) super.getElement(id);
		}

		@Override
		public ListElement<E> getTerminalElement(boolean first) {
			return (ListElement<E>) super.getTerminalElement(first);
		}

		@Override
		public MutableListElement<E> mutableElement(ElementId id) {
			return wrapMutable(getBacking().mutableElement(id));
		}

		@Override
		public ListElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<E>) super.addElement(value, after, before, first);
		}

		@Override
		public ListElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<E>) super.move(valueEl, after, before, first, afterRemove);
		}

		@Override
		public ListElement<E> getElement(int index) throws IndexOutOfBoundsException {
			return getBacking().getElement(index);
		}

		@Override
		public boolean isContentControlled() {
			return getBacking().isContentControlled();
		}

		/** Default mod-controlled list element implementation */
		public class MutableListElementWrapper extends MutableElementWrapper implements MutableListElement<E> {
			/** @param backingElement The source element to control */
			protected MutableListElementWrapper(MutableListElement<E> backingElement) {
				super(backingElement);
			}

			@Override
			protected MutableListElement<E> getBackingElement() {
				return (MutableListElement<E>) super.getBackingElement();
			}

			@Override
			public MutableListElement<E> getAdjacent(boolean next) {
				MutableListElement<E> adj = getBackingElement().getAdjacent(next);
				return adj == null ? null : wrapMutable(adj);
			}

			@Override
			public int getElementsBefore() {
				return getBackingElement().getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return getBackingElement().getElementsAfter();
			}
		}
	}

	/**
	 * Set implementation of {@link ModControlledCollection}
	 * 
	 * @param <E> The type of elements in the set
	 * @param <C> The sub-type of the set
	 */
	public static class ModControlledSet<E, C extends BetterSet<E>> extends ModControlledCollection<E, C> implements BetterSet<E> {
		/**
		 * @param backing The set to control
		 * @param control The optional modification controller
		 * @param listener The optional modification listener
		 */
		public ModControlledSet(C backing, CollectionModificationControl<E> control, CollectionModificationListener<E> listener) {
			super(backing, control, listener);
		}

		@Override
		public CollectionElement<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
			CollectionModificationControl<E> control = getControl();
			CollectionModificationListener<E> listener = getListener();
			if (control == null && listener == null)
				return getBacking().getOrAdd(value, after, before, first, preAdd, postAdd);
			try (Transaction t = lockWrite(false, null)) {
				CollectionElement<E> found = getElement(value, first);
				if (found != null)
					return found;
				String msg = control == null ? null : control.canAdd(value, after, before);
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				if (preAdd != null)
					preAdd.run();
				CollectionElement<E> added = getBacking().addElement(value, after, before, first);
				if (listener != null)
					listener.elementAdded(added);
				if (postAdd != null)
					postAdd.run();
				return added;
			}
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return getBacking().isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			return getBacking().checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			if (!isConsistent(element))
				throw new UnsupportedOperationException("Set repair is not supported here");
			return false;
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			if (checkConsistency())
				throw new UnsupportedOperationException("Set repair is not supported here");
			return false;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return super.toArray(a);
		}
	}

	/**
	 * Sorted list implementation of {@link ModControlledCollection}
	 * 
	 * @param <E> The type of elements in the sorted list
	 * @param <C> The sub-type of the sorted list
	 */
	public static class ModControlledSortedList<E, C extends BetterSortedList<E>> extends ModControlledList<E, C>
		implements BetterSortedList<E> {
		/**
		 * @param backing The sorted list to control
		 * @param control The optional modification controller
		 * @param listener The optional modification listener
		 */
		public ModControlledSortedList(C backing, CollectionModificationControl<E> control, CollectionModificationListener<E> listener) {
			super(backing, control, listener);
		}

		@Override
		public Comparator<? super E> comparator() {
			return getBacking().comparator();
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return getBacking().isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			return getBacking().checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			if (!isConsistent(element))
				throw new UnsupportedOperationException("Set repair is not supported here");
			return false;
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			if (checkConsistency())
				throw new UnsupportedOperationException("Set repair is not supported here");
			return false;
		}

		@Override
		public ListElement<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
			return getBacking().search(search, filter);
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			return getBacking().indexFor(search);
		}
	}

	/**
	 * Sorted set implementation of {@link ModControlledCollection}
	 * 
	 * @param <E> The type of elements in the sorted set
	 * @param <C> The sub-type of the sorted set
	 */
	public static class ModControlledSortedSet<E, C extends BetterSortedSet<E>> extends ModControlledSortedList<E, C>
		implements BetterSortedSet<E> {
		/**
		 * @param backing The sorted set to control
		 * @param control The optional modification controller
		 * @param listener The optional modification listener
		 */
		public ModControlledSortedSet(C backing, CollectionModificationControl<E> control, CollectionModificationListener<E> listener) {
			super(backing, control, listener);
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return super.toArray(a);
		}
	}
}
