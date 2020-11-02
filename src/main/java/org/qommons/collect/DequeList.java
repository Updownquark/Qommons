package org.qommons.collect;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.qommons.Stamped;

/**
 * A {@link List} that is also a {@link Deque} and contains a few other enhancements as well
 * 
 * @param <E> The type of values in the list
 */
public interface DequeList<E> extends Deque<E>, RRList<E>, Stamped {
	/**
	 * @param start The lower bound of the iterator
	 * @param end The upper bound of the iterator
	 * @param next The {@link ListIterator#nextIndex() next index} position for the iterator
	 * @param forward Whether the iterator should move forward or backward
	 * @return The iterator
	 */
	ListIterator<E> iterator(int start, int end, int next, boolean forward);

	@Override
	default Iterator<E> iterator() {
		return iterator(0, size(), 0, true);
	}

	@Override
	default ListIterator<E> listIterator(int index) {
		return iterator(0, size(), index, true);
	}

	@Override
	default boolean contains(Object o) {
		return indexOf(o) >= 0;
	}

	/**
	 * @param c The collection
	 * @return Whether this collection contains any elements of the given collection
	 */
	boolean containsAny(Collection<?> c);

	@Override
	default boolean isEmpty() {
		return size() == 0;
	}

	@Override
	default Object[] toArray() {
		return toArray(new Object[size()]);
	}

	@Override
	default boolean addAll(Collection<? extends E> c) {
		return addAll(size(), c);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		if (c.isEmpty())
			return false;
		return removeIf(c::contains);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		if (c.isEmpty()) {
			boolean ret = !isEmpty();
			clear();
			return ret;
		}
		return removeIf(v -> !c.contains(v));
	}

	@Override
	default void addFirst(E e) {
		if (!offerFirst(e))
			throw new IllegalStateException("List is full");
	}

	@Override
	default void addLast(E e) {
		if (!offerLast(e))
			throw new IllegalStateException("List is full");
	}

	@Override
	default boolean add(E e) {
		return offerLast(e);
	}

	@Override
	default E removeFirst() {
		if (isEmpty())
			throw new NoSuchElementException("List is empty");
		E v = pollFirst();
		return v;
	}

	@Override
	default E removeLast() {
		if (isEmpty())
			throw new NoSuchElementException("List is empty");
		E v = pollLast();
		return v;
	}

	@Override
	default E getFirst() {
		long stamp = getStamp();
		if (isEmpty())
			throw new NoSuchElementException("List is empty");
		E v = peekFirst();
		if (stamp != getStamp())
			throw new ConcurrentModificationException("List was modified externally");
		return v;
	}

	@Override
	default E getLast() {
		long stamp = getStamp();
		if (isEmpty())
			throw new NoSuchElementException("List is empty");
		E v = peekLast();
		if (stamp != getStamp())
			throw new ConcurrentModificationException("List was modified externally");
		return v;
	}

	@Override
	default boolean removeFirstOccurrence(Object o) {
		return remove(o);
	}

	@Override
	default boolean offer(E e) {
		return offerLast(e);
	}

	@Override
	default E remove() {
		return removeFirst();
	}

	@Override
	default E poll() {
		return pollFirst();
	}

	@Override
	default E element() {
		return getFirst();
	}

	@Override
	default E peek() {
		return peekFirst();
	}

	@Override
	default void push(E e) {
		addFirst(e);
	}

	@Override
	default E pop() {
		return removeFirst();
	}

	@Override
	default Iterator<E> descendingIterator() {
		return iterator(0, size(), size(), false);
	}

	@Override
	DequeList<E> subList(int fromIndex, int toIndex);

	/**
	 * A view of a subset of a {@link DequeList}
	 * 
	 * @param <E> The type of elements in the list
	 */
	public class SubView<E> implements Stamped {
		private final DequeList<E> theRoot;
		private final SubView<E> theParent;
		private int theStart;
		private int theEnd;
		private long theLastKnownStamp;

		/**
		 * @param root The root list
		 * @param parent The parent view, if any
		 * @param start The lower bound of this view in the root list
		 * @param end The upper bound (exclusive) of this view in the root list
		 */
		public SubView(DequeList<E> root, SubView<E> parent, int start, int end) {
			theRoot = root;
			theParent = parent;
			if (theParent != null)
				theLastKnownStamp = parent.check(-1);
			else
				theLastKnownStamp = theRoot.getStamp();
			theStart = start;
			theEnd = end;
		}

		/** @return The root list that this is a view of */
		protected DequeList<E> getRoot() {
			return theRoot;
		}

		/** @return The lower bound of this view in the root list */
		protected int getStart() {
			return theStart;
		}

		/** @return The upper bound (exclusive) of this view in the root list */
		protected int getEnd() {
			return theEnd;
		}

		/** @param start The lower bound for this view */
		protected void setStart(int start) {
			theStart = start;
		}

		/**
		 * Called after a modification has been executed against this view
		 * 
		 * @param against The stamp to verify against, or -1 to not verify against a given stamp
		 * @return The current stamp
		 * @throws ConcurrentModificationException If the current stamp does not match the given stamp (if given) or if the root collection
		 *         has changed independent of this view
		 */
		protected long check(long against) throws ConcurrentModificationException {
			long stamp = theLastKnownStamp;
			if (against != -1 && stamp != against)
				throw new ConcurrentModificationException("Collection has changed externally");
			if (stamp != theRoot.getStamp())
				throw new ConcurrentModificationException("Backing collection has changed externally");
			return stamp;
		}

		/**
		 * Called after a modification has been executed against this view
		 * 
		 * @param added The number of elements added (positive) or removed (negative) from the view
		 */
		protected void changed(int added) {
			if (theParent != null)
				theParent.changed(added);
			theEnd += added;
			theLastKnownStamp = theRoot.getStamp();
		}

		@Override
		public long getStamp() {
			return check(-1);
		}
	}

	/**
	 * A starting point for a {@link DequeList#subList(int, int) sub list}
	 * 
	 * @param <E> The type of elements in the list
	 */
	public abstract class AbstractSubDequeList<E> extends SubView<E> implements DequeList<E> {
		/**
		 * @param list The root list
		 * @param parent The parent view, if any
		 * @param start The lower bound of this sub-list in the root list
		 * @param end The upper bound (exclusive) of this sub-list in the root list
		 */
		public AbstractSubDequeList(DequeList<E> list, SubView<E> parent, int start, int end) {
			super(list, parent, start, end);
		}

		@Override
		public E pollFirst() {
			check(-1);
			if (isEmpty())
				return null;
			return remove(0);
		}

		@Override
		public E pollLast() {
			check(-1);
			if (isEmpty())
				return null;
			return remove(size() - 1);
		}

		@Override
		public E peekFirst() {
			check(-1);
			if (isEmpty())
				return null;
			return get(0);
		}

		@Override
		public E peekLast() {
			check(-1);
			if (isEmpty())
				return null;
			return get(size() - 1);
		}

		@Override
		public boolean remove(Object o) {
			check(-1);
			int index = indexOf(o);
			if (index < 0)
				return false;
			remove(index);
			return true;
		}

		@Override
		public int size() {
			check(-1);
			return getEnd() - getStart();
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			check(-1);
			if (index < 0 || index > size())
				throw new IndexOutOfBoundsException(index + " of " + size());
			int oldSize = getRoot().size();
			if (!getRoot().addAll(getStart() + index, c))
				return false;
			int newSize = getRoot().size();
			changed(newSize - oldSize);
			return true;
		}

		@Override
		public E get(int index) {
			check(-1);
			return getRoot().get(getStart() + index);
		}

		@Override
		public E set(int index, E element) {
			check(-1);
			if (index < 0 || index >= size())
				throw new IndexOutOfBoundsException(index + " of " + size());
			E v = getRoot().set(getStart() + index, element);
			changed(0);
			return v;
		}

		@Override
		public void add(int index, E element) {
			check(-1);
			if (index < 0 || index > size())
				throw new IndexOutOfBoundsException(index + " of " + size());
			int oldSize = getRoot().size();
			getRoot().add(getStart() + index, element);
			if (oldSize != getRoot().size())
				changed(1);
		}

		@Override
		public E remove(int index) {
			check(-1);
			if (index < 0 || index >= size())
				throw new IndexOutOfBoundsException(index + " of " + size());
			E v = getRoot().remove(getStart() + index);
			changed(-1);
			return v;
		}

		@Override
		public int indexOf(Object o) {
			long stamp = check(-1);
			Iterator<E> iter = iterator();
			int index = 0;
			while (iter.hasNext()) {
				check(stamp);
				if (Objects.equals(iter.next(), o))
					return index;
				index++;
			}
			return -1;
		}

		@Override
		public int lastIndexOf(Object o) {
			long stamp = check(-1);
			Iterator<E> iter = descendingIterator();
			int index = size() - 1;
			while (iter.hasNext()) {
				check(stamp);
				if (Objects.equals(iter.next(), o))
					return index;
				index--;
			}
			return -1;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			long stamp = check(-1);
			for (Object o : c) {
				check(stamp);
				if (!contains(o))
					return false;
			}
			return true;
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			long stamp = check(-1);
			for (Object o : c) {
				check(stamp);
				if (contains(o))
					return true;
			}
			return false;
		}

		@Override
		public boolean removeLastOccurrence(Object o) {
			long stamp = check(-1);
			Iterator<E> iter = descendingIterator();
			while (iter.hasNext()) {
				check(stamp);
				if (Objects.equals(iter.next(), o)) {
					iter.remove();
					return true;
				}
			}
			return false;
		}

		@Override
		public void clear() {
			check(-1);
			getRoot().removeRange(getStart(), getEnd());
			changed(-(getEnd() - getStart()));
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}
	}
}
