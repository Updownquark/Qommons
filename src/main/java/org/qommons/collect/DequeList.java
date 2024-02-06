package org.qommons.collect;

import java.util.*;

import org.qommons.Stamped;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A {@link List} that is also a {@link Deque} and contains a few other enhancements as well
 * 
 * @param <E> The type of values in the list
 */
public interface DequeList<E> extends Deque<E>, RRList<E>, Stamped {
	/**
	 * @param <E> The type of the list
	 * @return An immutable empty {@link DequeList} of the given type
	 */
	public static <E> DequeList<E> empty() {
		return (DequeList<E>) EMPTY;
	}

	/**
	 * @param <E> The type of the list
	 * @param value The value for the list's single element
	 * @return An immutable {@link DequeList} with only the given value
	 */
	public static <E> DequeList<E> singleton(E value) {
		return new SingletonDequeList<>(value);
	}

	/**
	 * @param <E> The type of the list
	 * @param values The values for the list
	 * @return An immutable {@link DequeList} with the given values
	 */
	public static <E> DequeList<E> of(E... values) {
		return of(Arrays.asList(values));
	}

	/**
	 * @param <E> The type of the list
	 * @param values The values for the list
	 * @return An immutable {@link DequeList} with the given values
	 */
	public static <E> DequeList<E> of(Collection<? extends E> values) {
		if (values.isEmpty())
			return empty();
		else if (values.size() == 1)
			return singleton(values.iterator().next());
		else
			return new SimpleImmutableList<>(values);
	}

	/**
	 * @param <E> The type of the list
	 * @param firstValues The values for the beginning of the list (may be null)
	 * @param lastValue The last value for the list
	 * @return A {@link DequeList} containing all elements in the given collection, followed by the given last value
	 */
	public static <E> DequeList<E> concat(Collection<? extends E> firstValues, E lastValue) {
		Object[] newValues = new Object[firstValues == null ? 1 : (firstValues.size() + 1)];
		if (firstValues != null)
			firstValues.toArray(newValues);
		newValues[newValues.length - 1] = lastValue;
		return (DequeList<E>) of(newValues);
	}

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

	/** An immutable {@link DequeList} with no values */
	static class EmptyDequeList implements DequeList<Object> {
		@Override
		public boolean offerFirst(Object e) {
			return false;
		}

		@Override
		public boolean offerLast(Object e) {
			return false;
		}

		@Override
		public Object pollFirst() {
			return null;
		}

		@Override
		public Object pollLast() {
			return null;
		}

		@Override
		public Object peekFirst() {
			return null;
		}

		@Override
		public Object peekLast() {
			return null;
		}

		@Override
		public boolean removeLastOccurrence(Object o) {
			return false;
		}

		@Override
		public boolean remove(Object o) {
			return false;
		}

		@Override
		public int size() {
			return 0;
		}

		@Override
		public boolean addAll(int index, Collection<? extends Object> c) {
			return false;
		}

		@Override
		public Object get(int index) {
			throw new IndexOutOfBoundsException(index + " of 0");
		}

		@Override
		public Object set(int index, Object element) {
			throw new IndexOutOfBoundsException(index + " of 0");
		}

		@Override
		public void add(int index, Object element) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public Object remove(int index) {
			throw new IndexOutOfBoundsException(index + " of 0");
		}

		@Override
		public int indexOf(Object o) {
			return -1;
		}

		@Override
		public int lastIndexOf(Object o) {
			return -1;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return a;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return c.isEmpty();
		}

		@Override
		public void clear() {
		}

		@Override
		public long getStamp() {
			return 0;
		}

		@Override
		public ListIterator<Object> iterator(int start, int end, int next, boolean forward) {
			if (start != 0 || end != 0)
				throw new IndexOutOfBoundsException(start + " to " + end + " of 0");
			return Collections.emptyListIterator();
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return false;
		}

		@Override
		public DequeList<Object> subList(int fromIndex, int toIndex) {
			if (fromIndex != 0 || toIndex != 0)
				throw new IndexOutOfBoundsException(fromIndex + " to " + toIndex + " of 0");
			return this;
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Collection && ((Collection<?>) obj).isEmpty();
		}

		@Override
		public String toString() {
			return "[]";
		}
	}

	/** Singleton empty {@link DequeList} */
	static final EmptyDequeList EMPTY = new EmptyDequeList();

	/**
	 * An immutable {@link DequeList} with a single value
	 * 
	 * @param <E> The type of the value in the list
	 */
	static class SingletonDequeList<E> implements DequeList<E> {
		private final E theValue;

		public SingletonDequeList(E value) {
			theValue = value;
		}

		@Override
		public boolean offerFirst(E e) {
			return false;
		}

		@Override
		public boolean offerLast(E e) {
			return false;
		}

		@Override
		public E pollFirst() {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public E pollLast() {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public E peekFirst() {
			return theValue;
		}

		@Override
		public E peekLast() {
			return theValue;
		}

		@Override
		public boolean removeLastOccurrence(Object o) {
			if (Objects.equals(theValue, o))
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return false;
		}

		@Override
		public boolean remove(Object o) {
			if (contains(0))
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return false;
		}

		@Override
		public int size() {
			return 1;
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			if (!c.isEmpty())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return false;
		}

		@Override
		public E get(int index) {
			if (index == 0)
				return theValue;
			else
				throw new IndexOutOfBoundsException(index + " of 1");
		}

		@Override
		public E set(int index, E element) {
			if (index == 0)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else
				throw new IndexOutOfBoundsException(index + " of 1");
		}

		@Override
		public void add(int index, E element) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public E remove(int index) {
			if (index == 0)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else
				throw new IndexOutOfBoundsException(index + " of 1");
		}

		@Override
		public int indexOf(Object o) {
			if (Objects.equals(theValue, o))
				return 0;
			else
				return -1;
		}

		@Override
		public int lastIndexOf(Object o) {
			if (Objects.equals(theValue, o))
				return 0;
			else
				return -1;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			if (a.length == 0)
				a = Arrays.copyOf(a, 1);
			a[0] = (T) theValue;
			return a;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			for (Object v : c) {
				if (!Objects.equals(theValue, v))
					return false;
			}
			return true;
		}

		@Override
		public void clear() {
		}

		@Override
		public long getStamp() {
			return 0;
		}

		@Override
		public ListIterator<E> iterator(int start, int end, int next, boolean forward) {
			if (start < 0 || end > 1 || start > end)
				throw new IndexOutOfBoundsException(start + " to " + end + " of 1");
			else if (start == end)
				return Collections.emptyListIterator();
			else
				return new SingletonIterator(!forward, start == 0);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return c.contains(theValue);
		}

		@Override
		public DequeList<E> subList(int fromIndex, int toIndex) {
			if (fromIndex == toIndex)
				return empty();
			else if (fromIndex == 0 && toIndex == 1)
				return this;
			else
				throw new IndexOutOfBoundsException(fromIndex + " to " + toIndex + " of 1");
		}

		@Override
		public int hashCode() {
			return theValue == null ? 0 : theValue.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof Collection))
				return false;
			Collection<?> other = (Collection<?>) obj;
			return other.size() == 1 && Objects.equals(theValue, other.iterator().next());
		}

		@Override
		public String toString() {
			return new StringBuilder("[").append(theValue).append(']').toString();
		}

		class SingletonIterator implements ListIterator<E> {
			private final boolean isReversed;
			private boolean isBefore;

			SingletonIterator(boolean isReversed, boolean isBefore) {
				this.isReversed = isReversed;
				this.isBefore = isBefore;
			}

			@Override
			public boolean hasNext() {
				return isReversed ^ isBefore;
			}

			@Override
			public E next() {
				if (!hasNext())
					throw new NoSuchElementException();
				isBefore = isReversed;
				return theValue;
			}

			@Override
			public boolean hasPrevious() {
				return !isReversed ^ isBefore;
			}

			@Override
			public E previous() {
				if (!hasPrevious())
					throw new NoSuchElementException();
				isBefore = !isReversed;
				return theValue;
			}

			@Override
			public int nextIndex() {
				if (hasNext())
					return 0;
				else
					return 1;
			}

			@Override
			public int previousIndex() {
				if (hasPrevious())
					return 0;
				else
					return -1;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public void set(E e) {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public void add(E e) {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}
		}
	}
}
