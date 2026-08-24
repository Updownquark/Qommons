package org.qommons.collect;

import java.util.*;

import org.qommons.Stamped;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A {@link List} that is also a {@link Sequenced} {@link Deque} and contains a few other enhancements as well
 * 
 * @param <E> The type of values in the list
 */
public interface DequeList<E> extends SequencedDeque<E>, RRList<E> {
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
		if (values == null || values.length == 0)
			return empty();
		else if (values.length == 1)
			return singleton(values[0]);
		else
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

	@Override
	default ListSequence<E> sequence() {
		return sequence(0, Integer.MAX_VALUE, true);
	}

	@Override
	default ListSequence<E> sequence(boolean fromBeginning) {
		return sequence(0, Integer.MAX_VALUE, fromBeginning);
	}

	/**
	 * @param start The minimum index (inclusive) that the sequence will travel
	 * @param end The maximum index (exclusive) that the sequence will travel
	 * @param fromBeginning Whether the sequence should iterate forward or in the reverse direction of this list
	 * @return The sequence
	 */
	default ListSequence<E> sequence(int start, int end, boolean fromBeginning) {
		return sequence(start, end, fromBeginning ? (start - 1) : Math.min(end, size()), fromBeginning);
	}

	/**
	 * @param start The minimum index (inclusive) that the sequence will travel
	 * @param end The maximum index (exclusive) that the sequence will travel
	 * @param position The initial position for the sequence
	 * @param forward Whether the sequence should iterate forward or in the reverse direction of this list
	 * @return The sequence
	 */
	ListSequence<E> sequence(int start, int end, int position, boolean forward);

	/**
	 * @param start The lower bound of the iterator
	 * @param end The upper bound of the iterator
	 * @param next The {@link ListIterator#nextIndex() next index} position for the iterator
	 * @param forward Whether the iterator should move forward or backward
	 * @return The iterator
	 */
	default ListIterator<E> iterator(int start, int end, int next, boolean forward) {
		return new ListSequence.ListSequenceIterator<>(sequence(start, end, next, forward));
	}

	@Override
	default E getFirst() {
		return SequencedDeque.super.getFirst();
	}

	@Override
	default E getLast() {
		return SequencedDeque.super.getLast();
	}

	@Override
	default boolean add(E e) {
		return SequencedDeque.super.add(e);
	}

	@Override
	default void addFirst(E e) {
		SequencedDeque.super.addFirst(e);
	}

	@Override
	default void addLast(E e) {
		SequencedDeque.super.addLast(e);
	}

	@Override
	default Iterator<E> iterator() {
		return SequencedDeque.super.iterator();
	}

	@Override
	default ListIterator<E> listIterator(int index) {
		return iterator(0, size(), index, true);
	}

	@Override
	default boolean contains(Object o) {
		return indexOf(o) >= 0;
	}

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
	default E removeFirst() {
		return SequencedDeque.super.removeFirst();
	}

	@Override
	default E removeLast() {
		return SequencedDeque.super.removeLast();
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
	void clear();

	@Override
	DequeList<E> subList(int fromIndex, int toIndex);

	default DequeList<E> reversed() {
		return new ReversedDequeList<>(this);
	}

	public class ReversedDequeList<E> implements DequeList<E> {
		private final DequeList<E> theWrapped;

		public ReversedDequeList(DequeList<E> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			return theWrapped.containsAny(c);
		}

		@Override
		public boolean offer(E e) {
			return theWrapped.offerFirst(e);
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			// Would be better to do reversed() on the collection, but I want this to compile in Java 8 still
			return theWrapped.addAll(size() - index, c);
		}

		@Override
		public E get(int index) {
			return theWrapped.get(size() - index - 1);
		}

		@Override
		public E set(int index, E element) {
			return theWrapped.set(size() - index - 1, element);
		}

		@Override
		public void add(int index, E element) {
			theWrapped.add(size() - index, element);
		}

		@Override
		public E remove(int index) {
			return theWrapped.remove(size() - index - 1);
		}

		@Override
		public int indexOf(Object o) {
			return theWrapped.lastIndexOf(o);
		}

		@Override
		public int lastIndexOf(Object o) {
			return theWrapped.indexOf(o);
		}

		@Override
		public boolean offerFirst(E e) {
			return theWrapped.offerLast(e);
		}

		@Override
		public E pollFirst() {
			return theWrapped.pollLast();
		}

		@Override
		public E pollLast() {
			return theWrapped.pollFirst();
		}

		@Override
		public E peekFirst() {
			return theWrapped.peekLast();
		}

		@Override
		public E peekLast() {
			return theWrapped.peekFirst();
		}

		@Override
		public boolean removeLastOccurrence(Object o) {
			return theWrapped.removeFirstOccurrence(o);
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			int size = size();
			if (a.length < size)
				a = Arrays.copyOf(a, size);
			int i = 0;
			for (Object o : this)
				a[i++] = (T) o;
			return a;
		}

		@Override
		public boolean remove(Object o) {
			return theWrapped.removeLastOccurrence(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theWrapped.containsAll(c);
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		public ListSequence<E> sequence(int start, int end, int position, boolean forward) {
			int size = size();
			return theWrapped.sequence(size - end, size - start, size - position, !forward);
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}

		@Override
		public DequeList<E> subList(int fromIndex, int toIndex) {
			int size = size();
			return new ReversedDequeList<>(theWrapped.subList(size - toIndex, size - fromIndex));
		}

		@Override
		public DequeList<E> reversed() {
			return theWrapped;
		}

		@Override
		public int hashCode() {
			int hashCode = 1;
			for (Object e : this)
				hashCode = 31 * hashCode + (e == null ? 0 : e.hashCode());
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Collection))
				return false;
			Iterator<?> e1 = iterator();
			Iterator<?> e2 = ((Collection<?>) obj).iterator();
			while (e1.hasNext() && e2.hasNext()) {
				Object o1 = e1.next();
				Object o2 = e2.next();
				if (!Objects.equals(o1, o2))
					return false;
			}
			return !(e1.hasNext() || e2.hasNext());
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder("[");
			boolean first = true;
			for (Object value : this) {
				if (!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
			ret.append(']');
			return ret.toString();
		}
	}

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

		/**
		 * Wraps a sequence so actions on the sequence don't invalidate this view
		 * 
		 * @param sequence The sequence to wrap
		 * @return The wrapped sequence
		 */
		protected ListSequence<E> wrap(ListSequence<E> sequence) {
			return new WrappedSequence(sequence);
		}

		class WrappedSequence implements ListSequence<E> {
			private final ListSequence<E> theWrapped;

			WrappedSequence(ListSequence<E> sequence) {
				theWrapped = sequence;
			}

			@Override
			public boolean advance(boolean forward) {
				return theWrapped.advance(forward);
			}

			@Override
			public boolean has(boolean next) {
				return theWrapped.has(next);
			}

			@Override
			public boolean exists() {
				return theWrapped.exists();
			}

			@Override
			public E get() throws NoSuchElementException {
				return theWrapped.get();
			}

			@Override
			public int getIndex() throws IllegalStateException {
				return theWrapped.getIndex();
			}

			@Override
			public String canRemove() {
				return theWrapped.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException, IllegalStateException {
				theWrapped.remove();
				changed(-1);
			}

			@Override
			public String isSettable() {
				return theWrapped.isSettable();
			}

			@Override
			public String isAcceptable(E newValue) {
				return theWrapped.isAcceptable(newValue);
			}

			@Override
			public void set(E newValue) throws UnsupportedOperationException, IllegalArgumentException, IllegalStateException {
				theWrapped.set(newValue);
				changed(0);
			}

			@Override
			public String canAdd(E value, boolean before) {
				return theWrapped.canAdd(value, before);
			}

			@Override
			public void add(E newValue, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				theWrapped.add(newValue, before);
				changed(1);
			}
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
		protected AbstractSubDequeList(DequeList<E> list, SubView<E> parent, int start, int end) {
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
		public ListSequence<E> sequence(int fromIndex, int toIndex, int position, boolean reverse) {
			int size = size();
			if (fromIndex < 0 || fromIndex > toIndex || fromIndex > size)
				throw new IndexOutOfBoundsException(fromIndex + " to " + toIndex + " of " + size);
			if (toIndex > size)
				toIndex = size;
			int end = getStart() + toIndex;
			if (end < 0)
				end = Integer.MAX_VALUE;
			return wrap(getRoot().sequence(getStart() + fromIndex, end, getStart() + position, reverse));
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
		public boolean offer(Object e) {
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
		public ListSequence<Object> sequence(int start, int end, int position, boolean reverse) {
			if (start < 0 || start > end || position < start - 1 || position > end || position > size())
				throw new IllegalArgumentException(position + " in " + start + " to " + end);
			return ListSequence.empty();
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
	 * Default index-based implementation of {@link DequeList#sequence(int, int, int, boolean)}
	 * 
	 * @param <E> The type of values in the sequence
	 */
	static class IndexedSequence<E> implements ListSequence<E> {
		private final List<E> theList;
		private int theStart;
		private int theEnd;
		private final boolean isReversed;
		private int theIndex;
		private E theValue;

		public IndexedSequence(List<E> list, int start, int end, int position, boolean forward) {
			if (start < 0 || start > end)
				throw new IndexOutOfBoundsException(start + " to " + end);
			if (position < start - 1 || position > end)
				throw new IndexOutOfBoundsException(position + " between" + start + " and " + end);
			theList = list;
			theStart = start;
			theEnd = end;
			isReversed = !forward;

			theIndex = position;
			theValue = exists() ? list.get(position) : null;
		}

		@Override
		public boolean exists() {
			int index = theIndex;
			return index >= theStart && index < theEnd && index < theList.size();
		}

		@Override
		public E get() {
			if (!exists())
				throw new NoSuchElementException();
			return theValue;
		}

		@Override
		public boolean advance(boolean forward) {
			boolean realForward = forward ^ isReversed;
			int index = theIndex;
			int size = theList.size();
			if (size > theEnd)
				size = theEnd;
			if (realForward) {
				if (index < theStart - 1)
					index = theStart - 1;
				if (index >= size - 1)
					return false;
				index++;
			} else {
				if (index > size)
					index = size;
				if (index <= theStart)
					return false;
				index--;
			}
			theIndex = index;
			if (exists()) {
				try {
					theValue = theList.get(index);
				} catch (IndexOutOfBoundsException e) {
					theValue = null;
				}
			}
			return true;
		}

		@Override
		public boolean has(boolean next) {
			boolean realForward = next ^ isReversed;
			int index = theIndex;
			if (realForward)
				return index < theEnd - 1 && index < theList.size() - 1;
			else
				return index > theStart;
		}

		@Override
		public int getIndex() {
			if (isReversed)
				return theEnd - theIndex - 1;
			else
				return theIndex - theStart;
		}

		@Override
		public String canRemove() {
			if (!exists())
				return NO_ELEMENT_AT_POSTION;
			return null; // Can't know till we try
		}

		@Override
		public void remove() throws UnsupportedOperationException, IllegalStateException {
			if (!exists())
				throw new IllegalStateException(NO_ELEMENT_AT_POSTION);
			int index = theIndex;
			theList.remove(index);
			theEnd--;
			if (isReversed && index > theStart)
				index--;
			if (exists())
				theValue = theList.get(index);
		}

		@Override
		public String isSettable() {
			if (!exists())
				return NO_ELEMENT_AT_POSTION;
			return null; // Can't know till we try
		}

		@Override
		public String isAcceptable(E newValue) {
			if (!exists())
				return NO_ELEMENT_AT_POSTION;
			return null; // Can't know till we try
		}

		@Override
		public void set(E newValue) throws UnsupportedOperationException, IllegalArgumentException, IllegalStateException {
			if (!exists())
				throw new IllegalStateException(NO_ELEMENT_AT_POSTION);
			int index = theIndex;
			theList.set(index, newValue);
			theValue = newValue;
		}

		@Override
		public String canAdd(E value, boolean before) {
			if (theIndex + (before ? 0 : 1) > theList.size())
				return NO_ELEMENT_AT_POSTION;
			return null; // Can't know till we try
		}

		@Override
		public void add(E newValue, boolean before) throws UnsupportedOperationException, IllegalArgumentException, IllegalStateException {
			if (theStart == theEnd) {
				theList.add(theStart, newValue);
				theEnd++;
				theIndex = before ? theStart + 1 : theStart;
				return;
			}
			int index = theIndex;
			int size = theList.size();
			if (size > theEnd)
				size = theEnd;
			if (index < theStart) {
				index = theStart;
				if (index >= theEnd)
					throw new IllegalStateException(NO_ELEMENT_AT_POSTION);
			} else if (index >= theEnd) {
				index = theEnd - 1;
				if (index < theStart)
					throw new IllegalStateException(NO_ELEMENT_AT_POSTION);
			}
			if (!before && index < size)
				index++;
			int preSize = theList.size();
			theList.add(index, newValue);
			if (theList.size() > preSize) {
				if (before)
					theIndex++;
				theEnd++;
			}
		}
	}

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
		public boolean offer(E e) {
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
		public ListSequence<E> sequence(int start, int end, int position, boolean reverse) {
			if (start < 0 || start > end || position < start - 1 || position > end || position > size())
				throw new IndexOutOfBoundsException(position + " in " + start + " to " + end);
			if (start == 0 && end >= 1)
				return ListSequence.single(theValue, position);
			else
				return ListSequence.empty();
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
	}
}
