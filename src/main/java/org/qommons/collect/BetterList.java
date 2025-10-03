package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.qommons.Identifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.ex.ExFunction;

/**
 * A {@link List} that is also a {@link BetterCollection}.
 * 
 * See <a href="https://github.com/Updownquark/Qommons/wiki/BetterCollection-API#betterList">the wiki</a> for more detail.
 * 
 * @param <E> The type of value in the list
 */
public interface BetterList<E> extends BetterCollection<E>, TransactableList<E>, DequeList<E> {
	/**
	 * @param index The index to get the element for
	 * @return The element in this list at the given index
	 * @throws IndexOutOfBoundsException If the given index is less than zero or &gt;={@link #size()}
	 */
	ListElement<E> getElement(int index) throws IndexOutOfBoundsException;

	/**
	 * <p>
	 * Although the contract of {@link List} states that the user (dev) has complete control over the content and placement of elements in a
	 * list, a BetterList may, in fact, only be index-accessible, not supporting addition or update of values at arbitrary positions.
	 * BetterCollection implementations can implement BetterList if their elements are stored in such a way as to be index-accessible
	 * efficiently, even if they do not allow List-style complete control.
	 * </p>
	 * 
	 * <p>
	 * This method allows BetterList implementations to expose whether or not they also follow the expressed intent of the List API,
	 * allowing value placement at arbitrary positions.
	 * </p>
	 * 
	 * @return Whether this list has constraints on its content or placement
	 */
	boolean isContentControlled();

	@Override
	ListElement<E> getElement(ElementId id);

	@Override
	ListElement<E> getElement(E value, boolean first);

	@Override
	ListElement<E> getTerminalElement(boolean first);

	@Override
	MutableListElement<E> mutableElement(ElementId id);

	@Override
	ListElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException;

	@Override
	ListElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException;

	@Override
	default ListElement<E> addElement(E value, boolean first) throws UnsupportedOperationException, IllegalArgumentException {
		return (ListElement<E>) BetterCollection.super.addElement(value, first);
	}

	@Override
	default ListElement<E> find(Predicate<? super E> search, boolean first) {
		return (ListElement<E>) BetterCollection.super.find(search, first);
	}

	@Override
	void clear();

	// Reconciling BetterCollection and DequeList

	@Override
	default E pop() {
		return BetterCollection.super.pop();
	}

	@Override
	default boolean removeLast(Object o) {
		return BetterCollection.super.removeLast(o);
	}

	@Override
	default boolean removeIf(Predicate<? super E> filter) {
		return BetterCollection.super.removeIf(filter);
	}

	@Override
	default void addFirst(E e) {
		BetterCollection.super.addFirst(e);
	}

	@Override
	default void addLast(E e) {
		BetterCollection.super.addLast(e);
	}

	@Override
	default boolean offerFirst(E e) {
		return BetterCollection.super.offerFirst(e);
	}

	@Override
	default boolean offerLast(E e) {
		return BetterCollection.super.offerLast(e);
	}

	@Override
	default E removeFirst() {
		return BetterCollection.super.removeFirst();
	}

	@Override
	default E removeLast() {
		return BetterCollection.super.removeLast();
	}

	@Override
	default E pollFirst() {
		return BetterCollection.super.pollFirst();
	}

	@Override
	default E pollLast() {
		return BetterCollection.super.pollLast();
	}

	@Override
	default E getFirst() {
		return BetterCollection.super.getFirst();
	}

	@Override
	default E getLast() {
		return BetterCollection.super.getLast();
	}

	@Override
	default E peekFirst() {
		return BetterCollection.super.peekFirst();
	}

	@Override
	default E peekLast() {
		return BetterCollection.super.peekLast();
	}

	@Override
	default boolean removeFirstOccurrence(Object o) {
		return BetterCollection.super.removeFirstOccurrence(o);
	}

	@Override
	default boolean removeLastOccurrence(Object o) {
		return BetterCollection.super.removeLastOccurrence(o);
	}

	@Override
	default boolean offer(E e) {
		return BetterCollection.super.offer(e);
	}

	@Override
	default E remove() {
		return BetterCollection.super.remove();
	}

	@Override
	default E poll() {
		return BetterCollection.super.poll();
	}

	@Override
	default E element() {
		return BetterCollection.super.element();
	}

	@Override
	default E peek() {
		return BetterCollection.super.peek();
	}

	@Override
	default void push(E e) {
		BetterCollection.super.push(e);
	}

	@Override
	default Iterator<E> descendingIterator() {
		return BetterCollection.super.descendingIterator();
	}

	@Override
	default Object[] toArray() {
		return BetterCollection.super.toArray();
	}

	@Override
	default <T> T[] toArray(T[] array) {
		return BetterCollection.super.toArray(array);
	}

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the first position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	@Override
	default int indexOf(Object value) {
		ListElement<E> element = getElement((E) value, true);
		return element == null ? -1 : element.getElementsBefore();
	}

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the last position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	@Override
	default int lastIndexOf(Object value) {
		ListElement<E> element = getElement((E) value, false);
		return element == null ? -1 : element.getElementsBefore();
	}

	/**
	 * @param index The index of the element to get
	 * @return The element of this collection at the given index
	 */
	@Override
	default E get(int index) {
		return getElement(index).get();
	}

	@Override
	default boolean contains(Object o) {
		return BetterCollection.super.contains(o);
	}

	@Override
	default boolean containsAny(Collection<?> c) {
		return BetterCollection.super.containsAny(c);
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		return BetterCollection.super.containsAll(c);
	}

	@Override
	default boolean addAll(int index, Collection<? extends E> c) {
		try (Transaction t = lock(true, null); Transaction ct = Transactable.lock(c, false, null)) {
			int sz = size();
			if (index < 0 || index > sz)
				throw new IndexOutOfBoundsException(index + " of " + sz);
			ElementId after = index == 0 ? null : getElement(index - 1).getElementId();
			ElementId before = index == sz ? null : getElement(index).getElementId();
			boolean modified = false;
			for (E v : c) {
				if (canAdd(v, after, before) == null) {
					addElement(v, after, before, false);
					modified = true;
				}
			}
			return modified;
		}
	}

	/**
	 * @param index The index to add the value at
	 * @param element The value to add
	 * @return The reason why the given value cannot be added to this list at the given position, or null if it can
	 */
	default String canAdd(int index, E element) {
		if (index < 0 || index > size())
			throw new IndexOutOfBoundsException(index + " of " + size());
		else if (isEmpty())
			return canAdd(element);
		else if (index == 0) {
			CollectionElement<E> first = getTerminalElement(false);
			return canAdd(element, null, first.getElementId());
		} else if (index == size()) {
			CollectionElement<E> last = getTerminalElement(false);
			return canAdd(element, last.getElementId(), null);
		} else {
			CollectionElement<E> before = getElement(index);
			CollectionElement<E> after = before.getAdjacent(false);
			return canAdd(element, after.getElementId(), before.getElementId());
		}
	}

	@Override
	default boolean add(E value) {
		return BetterCollection.super.add(value);
	}

	@Override
	default void add(int index, E element) {
		addElement(index, element);
	}

	/**
	 * @param index The index at which to add the element
	 * @param element The new value to add
	 * @return The element at which the value was added
	 */
	default ListElement<E> addElement(int index, E element) {
		try (Transaction t = lock(true, null)) {
			int sz = size();
			if (index < 0 || index > sz)
				throw new IndexOutOfBoundsException(index + " of " + sz);
			CollectionElement<E> afterEl;
			CollectionElement<E> beforeEl;
			if (index == 0) {
				afterEl = null;
				beforeEl = getTerminalElement(true);
			} else {
				afterEl = getElement(index - 1);
				beforeEl = afterEl.getAdjacent(true);
			}
			ElementId after = afterEl == null ? null : afterEl.getElementId();
			ElementId before = beforeEl == null ? null : beforeEl.getElementId();
			return addElement(element, after, before, true);
		}
	}

	@Override
	default boolean addAll(Collection<? extends E> c) {
		return BetterCollection.super.addAll(c);
	}

	@Override
	default BetterList<E> with(E... values) {
		BetterCollection.super.with(values);
		return this;
	}

	@Override
	default BetterList<E> withAll(Collection<? extends E> values) {
		addAll(values);
		return this;
	}

	@Override
	default boolean remove(Object o) {
		return BetterCollection.super.remove(o);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return BetterCollection.super.removeAll(c);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return BetterCollection.super.retainAll(c);
	}

	@Override
	default E remove(int index) {
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> el = getElement(index);
			E value = el.get();
			mutableElement(el.getElementId())//
				.remove();
			return value;
		}
	}

	@Override
	default void removeRange(int fromIndex, int toIndex) {
		try (Transaction t = lock(true, null)) {
			if (fromIndex == size() || fromIndex == toIndex)
				return;
			CollectionElement<E> el = getElement(fromIndex);
			ElementId end = toIndex == size() ? null : getElement(toIndex).getElementId();
			while (el != null && (end == null || el.getElementId().compareTo(end) < 0)) {
				MutableCollectionElement<E> mutableEl = mutableElement(el.getElementId());
				if (mutableEl.canRemove() == null)
					mutableEl.remove();
				el = el.getAdjacent(true);
			}
		}
	}

	@Override
	default void replaceAll(UnaryOperator<E> op) {
		BetterCollection.super.replaceAll(op);
	}

	@Override
	default E set(int index, E element) {
		try (Transaction t = lock(true, null)) {
			CollectionElement<E> el = getElement(index);
			E value = el.get();
			mutableElement(el.getElementId())//
				.set(element);
			return value;
		}
	}

	@Override
	default BetterListSequence<E> sequence() {
		return sequence(true);
	}

	@Override
	default BetterListSequence<E> sequence(boolean fromBeginning) {
		return sequence(null, null, fromBeginning, null, fromBeginning);
	}

	@Override
	default BetterListSequence<E> sequence(ElementId after, ElementId before, boolean forward) {
		return sequence(after, before, forward, null, forward);
	}

	@Override
	default BetterListSequence<E> sequence(ElementId after, ElementId before, boolean forward, ElementId position, boolean atStart) {
		return new BetterListSequence<>(this, after, before, forward, position, atStart);
	}

	@Override
	default ListSequence<E> sequence(int start, int end, int position, boolean forward) {
		if (start < 0 || start > end)
			throw new IndexOutOfBoundsException(start + " to " + end);
		int size = size();
		if (position < start - 1 || position > end || position > size)
			throw new IndexOutOfBoundsException(position + " of " + start + " to " + end);
		CollectionElement<E> startEl, endEl;
		switch (start) {
		case 0:
			startEl = null;
			break;
		case 1:
			startEl = getTerminalElement(true);
			break;
		default:
			startEl = getElement(start).getAdjacent(false);
			break;
		}
		if (end >= size)
			endEl = null;
		else if (end == size - 1)
			endEl = getTerminalElement(false);
		else
			endEl = getElement(end).getAdjacent(true);
		ElementId positionEl;
		boolean atStart;
		if (position == -1) {
			positionEl = null;
			atStart = true;
		} else if (position == end || position == size) {
			positionEl = null;
			atStart = false;
		} else {
			positionEl = getElement(position).getElementId();
			atStart = true; // Ignored
		}
		return sequence(//
			CollectionElement.getElementId(startEl), CollectionElement.getElementId(endEl), forward, positionEl, atStart);
	}

	@Override
	default BetterList<E> reverse() {
		return new ReversedList<>(this);
	}

	@Override
	default Iterator<E> iterator() {
		return new Sequence.SequenceIterator<>(sequence());
	}

	@Override
	default ListIterator<E> listIterator(int index) {
		return DequeList.super.listIterator(index);
	}

	@Override
	default BetterList<E> subList(int fromIndex, int toIndex) {
		return new SubList<>(this, fromIndex, toIndex);
	}

	@Override
	default BetterList<? extends ListElement<E>> elements() {
		return elementsBetween(null, true, null, true);
	}

	/**
	 * Creates a sub-list of collection elements backed by this list
	 * 
	 * @param low The low bound of the list (may be null)
	 * @param lowIncluded Whether the low bound should be included in the list
	 * @param high The high bound of the list (may be null)
	 * @param highIncluded Whether the high bound should be included in the list
	 * @return The sub-list
	 */
	default BetterList<? extends ListElement<E>> elementsBetween(ElementId low, boolean lowIncluded, ElementId high, boolean highIncluded) {
		return new ElementList<>(this, low == null ? null : getElement(low), lowIncluded, high == null ? null : getElement(high),
			highIncluded);
	}

	/**
	 * @param filter The filter to use to trim the list
	 * @return A new, immutable list containing all elements of this list that match the given filter
	 */
	default BetterList<E> quickFilter(Predicate<? super E> filter) {
		try (Transaction t = lock(false, null)) {
			ArrayList<E> copy = new ArrayList<>();
			for (E value : this) {
				if (filter.test(value))
					copy.add(value);
			}
			return of(copy);
		}
	}

	/** Singleton empty better list */
	public static final BetterList<Object> EMPTY = new EmptyList<>();

	/**
	 * @param <E> The type of the list
	 * @return An immutable, empty list
	 */
	public static <E> BetterList<E> empty() {
		return (BetterList<E>) (BetterList<?>) EMPTY;
	}

	/**
	 * @param <E> The type of the list
	 * @param value The value for the list
	 * @return An immutable list with the given value
	 */
	public static <E> BetterList<E> single(E value) {
		return new SingletonList<>(value);
	}

	/**
	 * @param <E> The type for the list
	 * @param values The values for the list
	 * @return An immutable list containing the given values
	 */
	public static <E> BetterList<E> of(E... values) {
		if (values == null || values.length == 0)
			return empty();
		else if (values.length == 1)
			return new SingletonList<>(values[0]);
		return new BetterArrayList<>(values);
	}

	/**
	 * @param <E> The type for the list
	 * @param values The values for the list
	 * @return An immutable list containing the given values
	 */
	public static <E> BetterList<E> of(Collection<? extends E> values) {
		if (values == null || values.isEmpty())
			return empty();
		else if (values.size() == 1) {
			if (values instanceof Sequenced) {
				Sequence<E> sequence = ((Sequenced<E>) values).sequence();
				if (sequence.next())
					return new SingletonList<>(sequence.get());
				else
					return empty();
			} else
				return new SingletonList<>(values.iterator().next());
		}
		return new BetterArrayList<>(values.toArray());
	}

	/**
	 * @param <E> The type for the list
	 * @param values The stream to supply values for the list
	 * @return An immutable list containing the values from the given stream
	 */
	public static <E> BetterList<E> of(Stream<? extends E> values) {
		ArrayList<E> list = new ArrayList<>();
		values.collect(Collectors.toCollection(() -> list));
		return of(list);
	}

	/**
	 * Creates a list from a flattened stream of streams
	 * 
	 * @param <T> The type of the objects in the primary stream
	 * @param <E> The type of values for the list
	 * @param <X> An exception type that may be thrown when accessing the streams of objects in the primary stream
	 * @param values The primary stream
	 * @param map The function to produce streams of list values from objects in the primary stream
	 * @return A constant list containing all values mapped from the stream
	 * @throws X If the map function throws an exception
	 */
	public static <T, E, X extends Throwable> BetterList<E> of(Stream<? extends T> values,
		ExFunction<T, ? extends Stream<? extends E>, X> map) throws X {
		try {
			return of(values.flatMap(v -> {
				try {
					return map.apply(v);
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Throwable x) {
					throw new CheckedExceptionWrapper(x);
				}
			}));
		} catch (CheckedExceptionWrapper e) {
			throw (X) e.getCause();
		}
	}

	/**
	 * Creates a list from a mapped stream of streams
	 * 
	 * @param <T> The type of the objects in the stream
	 * @param <E> The type of values for the list
	 * @param <X> An exception type that may be thrown when mapping from the stream objects to list objects
	 * @param values The stream
	 * @param map The function to produce list values from objects in the primary stream
	 * @return A constant list containing all values mapped from the stream
	 * @throws X If the map function throws an exception
	 */
	public static <T, E, X extends Throwable> BetterList<E> of2(Stream<? extends T> values, ExFunction<T, ? extends E, X> map) throws X {
		try {
			return of(values.map(v -> {
				try {
					return map.apply(v);
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Throwable x) {
					throw new CheckedExceptionWrapper(x);
				}
			}));
		} catch (CheckedExceptionWrapper e) {
			throw (X) e.getCause();
		}
	}

	/**
	 * Implements {@link BetterList#reverse()}
	 *
	 * @param <E> The type of elements in the list
	 */
	class ReversedList<E> extends ReversedCollection<E> implements BetterList<E> {
		protected ReversedList(BetterList<E> wrap) {
			super(wrap);
		}

		@Override
		protected BetterList<E> getWrapped() {
			return (BetterList<E>) super.getWrapped();
		}

		@Override
		public boolean isContentControlled() {
			return getWrapped().isContentControlled();
		}

		@Override
		public ListElement<E> getElement(int index) {
			try (Transaction t = lock(false, null)) {
				return getWrapped().getElement(reflect(index, false)).reverse();
			}
		}

		@Override
		public ListElement<E> getTerminalElement(boolean first) {
			return (ListElement<E>) super.getTerminalElement(first);
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
		public ListElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<E>) super.addElement(value, after, before, first);
		}

		@Override
		public ListElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove) {
			return (ListElement<E>) super.move(valueEl, after, before, first, afterRemove);
		}

		protected int reflect(int index, boolean terminalInclusive) {
			int size = getWrapped().size();
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			if (index > size || (!terminalInclusive && index == size))
				throw new IndexOutOfBoundsException(index + " of " + size);
			int reflected = size - index;
			if (!terminalInclusive)
				reflected--;
			return reflected;
		}

		@Override
		public MutableListElement<E> mutableElement(ElementId id) {
			return (MutableListElement<E>) super.mutableElement(id);
		}

		@Override
		public BetterList<E> reverse() {
			if (BetterCollections.simplifyDuplicateOperations())
				return getWrapped();
			else
				return BetterList.super.reverse();
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

	/**
	 * An immutable {@link BetterList} that has no content
	 * 
	 * @param <E> The type of the list
	 */
	class EmptyList<E> extends EmptyCollection<E> implements BetterList<E> {
		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public ListElement<E> getTerminalElement(boolean first) {
			return null;
		}

		@Override
		public ListElement<E> getElement(E value, boolean first) {
			return null;
		}

		@Override
		public ListElement<E> getElement(ElementId id) {
			throw new NoSuchElementException();
		}

		@Override
		public ListElement<E> getElement(int index) {
			throw new IndexOutOfBoundsException(index + " of 0");
		}

		@Override
		public ListElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<E>) super.addElement(value, after, before, first);
		}

		@Override
		public ListElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove) {
			throw new NoSuchElementException();
		}

		@Override
		public MutableListElement<E> mutableElement(ElementId id) {
			throw new NoSuchElementException();
		}
	}

	/**
	 * An immutable {@link BetterList} with a single value
	 * 
	 * @param <E> The type of the list
	 */
	class SingletonList<E> extends SingletonCollection<E> implements BetterList<E> {
		SingletonList(E value) {
			super(value);
		}

		@Override
		protected SingletonElement createElement(E value) {
			return new SingletonListElement(value);
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public ListElement<E> getTerminalElement(boolean first) {
			return (ListElement<E>) super.getTerminalElement(first);
		}

		@Override
		public ListElement<E> getElement(int index) {
			if (index == 0)
				return getTerminalElement(true);
			throw new IndexOutOfBoundsException(index + " of 1");
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
		public MutableListElement<E> mutableElement(ElementId id) {
			return (MutableListElement<E>) super.mutableElement(id);
		}

		class SingletonListElement extends SingletonElement implements MutableListElement<E> {
			SingletonListElement(E value) {
				super(value);
			}

			@Override
			public MutableListElement<E> getAdjacent(boolean next) {
				return null;
			}

			@Override
			public int getElementsBefore() {
				return 0;
			}

			@Override
			public int getElementsAfter() {
				return 0;
			}
		}
	}

	/**
	 * {@link BetterCollection.BetterSequence}/{@link ListSequence} combination for {@link BetterList}s
	 * 
	 * @param <E> The type of values in the sequence
	 */
	public class BetterListSequence<E> extends BetterSequence<E> implements ListSequence<E> {
		/**
		 * @param collection The collection to iterate over
		 * @param lowBound The minimum element to iterate over
		 * @param highBound The maximum element to iterate over
		 * @param forward Whether to iterate forward as opposed to reversed
		 * @param position The initial position for the sequence
		 * @param atStart Whether, if <code>position</code> is null, to start before the beginning or after the end of the sequence (by this
		 *        collection's reckoning, regardless of the <code>forward</code> parameter)
		 */
		public BetterListSequence(BetterList<E> collection, ElementId lowBound, ElementId highBound, boolean forward, ElementId position,
			boolean atStart) {
			super(collection, lowBound, highBound, forward, position, atStart);
		}

		@Override
		public ListElement<E> getCurrent() throws NoSuchElementException {
			return (ListElement<E>) super.getCurrent();
		}

		@Override
		protected BetterList<E> getCollection() {
			return (BetterList<E>) super.getCollection();
		}

		@Override
		public int getIndex() throws IllegalStateException {
			return getCurrent().getElementsBefore();
		}
	}

	/**
	 * Implements {@link BetterList#subList(int, int)}
	 * 
	 * @param <E> The type of values in the list
	 */
	class SubList<E> extends AbstractIdentifiable implements BetterList<E> {
		private final BetterList<E> theWrapped;
		private int theStart;
		private int theEnd;
		private long theStamp;

		public SubList(BetterList<E> wrapped, int start, int end) {
			QommonsUtils.assertThat(end <= wrapped.size(), IndexOutOfBoundsException::new, end, " of ", wrapped.size());
			QommonsUtils.assertThat(start >= 0, IndexOutOfBoundsException::new, start);
			QommonsUtils.assertThat(start <= end, IndexOutOfBoundsException::new, start, ">", end);
			theWrapped = wrapped;
			theStart = start;
			theEnd = end;
			theStamp = wrapped.getStamp();
		}

		/** @return The BetterList that this is a sub-list of */
		protected BetterList<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theWrapped.getThreadConstraint();
		}

		@Override
		protected Object createIdentity() {
			// Since this sub-list's start and end values can change as modifications are made via the sub-list,
			// different sub-lists created from the same base list with the same initial parameters can diverge later.
			// This means that identity cannot be represented as a function of the wrapped list's identity,
			// but that this list must be its own identity.
			return this;
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			check();
			Transaction t = theWrapped.lock(write, cause);
			if (write) {
				updated();
				return () -> {
					updated();
					t.close();
				};
			} else
				return t;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			check();
			Transaction t = theWrapped.tryLock(write, cause);
			if (t == null)
				return null;
			if (write) {
				updated();
				return () -> {
					updated();
					t.close();
				};
			} else
				return t;
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return theWrapped.getCurrentCauses();
		}

		@Override
		public CoreId getCoreId() {
			return theWrapped.getCoreId();
		}

		void check() {
			if (theWrapped.getStamp() != theStamp)
				throw new ConcurrentModificationException(BACKING_COLLECTION_CHANGED);
		}

		void updated() {
			theStamp = theWrapped.getStamp();
		}

		@Override
		public long getStamp() {
			long stamp = theWrapped.getStamp();
			if (theStamp != stamp)
				throw new ConcurrentModificationException(BACKING_COLLECTION_CHANGED);
			return stamp;
		}

		@Override
		public boolean isContentControlled() {
			return theWrapped.isContentControlled();
		}

		@Override
		public ListElement<E> getTerminalElement(boolean first) {
			if (theEnd == theStart)
				return null;
			if (first) {
				if (theStart == 0)
					return wrapElement(theWrapped.getTerminalElement(first));
				else if (theStart < theWrapped.size())
					return wrapElement(theWrapped.getElement(theStart));
				else
					return null;
			} else {
				if (theEnd == theWrapped.size())
					return wrapElement(theWrapped.getTerminalElement(first));
				else
					return wrapElement(theWrapped.getElement(theEnd - 1));
			}
		}

		@Override
		public ListElement<E> getElement(E value, boolean first) {
			try (Transaction t = lock(false, null)) {
				if (isEmpty())
					return null;
				ListElement<E> firstMatch = theWrapped.getElement(value, first);
				if (firstMatch == null)
					return null;
				int index = firstMatch.getElementsBefore();
				if ((first && index >= theEnd) || (!first && index < theStart))
					return null;
				if ((first && index >= theStart) || (!first && index < theEnd))
					return wrapElement(firstMatch);
				ListElement<E> el = getTerminalElement(first);
				if (first) {
					index = theStart;
					while (index < theEnd && el != null) {
						if (Objects.equals(el.get(), value))
							return el;
						el = el.getAdjacent(first);
						index++;
					}
				} else {
					index = theEnd - 1;
					while (index >= theStart && el != null) {
						if (Objects.equals(el.get(), value))
							return el;
						el = el.getAdjacent(first);
						index--;
					}
				}
				return null;
			}
		}

		@Override
		public ListElement<E> getElement(ElementId id) {
			try (Transaction t = lock(false, null)) {
				ListElement<E> wrapped = theWrapped.getElement(id);
				int index = wrapped.getElementsBefore();
				if (index < theStart || index >= theEnd)
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return wrapElement(wrapped);
			}
		}

		@Override
		public MutableListElement<E> mutableElement(ElementId id) {
			try (Transaction t = lock(false, null)) {
				MutableListElement<E> wrapped = theWrapped.mutableElement(id);
				int index = wrapped.getElementsBefore();
				if (index < theStart || index >= theEnd)
					throw new IllegalArgumentException(StdMsg.NOT_FOUND);
				return wrapElement(wrapped);
			}
		}

		@Override
		public ListElement<E> getElement(int index) {
			try (Transaction t = lock(false, null)) {
				return wrapElement(theWrapped.getElement(theStart + checkIndex(index, false)));
			}
		}

		@Override
		public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return QommonsUtils.filterMap(theWrapped.getElementsBySource(sourceEl, sourceCollection), el -> {
				int index = ((ListElement<E>) el).getElementsBefore();
				return index >= theStart && index < theEnd;
			}, el -> wrapElement((ListElement<E>) el));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (this == sourceCollection)
				return BetterList.of(localElement);
			return theWrapped.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			return theWrapped.getEquivalentElement(equivalentEl);
		}

		@Override
		public int size() {
			int sz = theWrapped.size();
			if (sz <= theStart)
				return 0;
			return Math.min(sz, theEnd) - theStart;
		}

		@Override
		public boolean isEmpty() {
			return Math.min(theEnd, theWrapped.size()) <= theStart;
		}

		@Override
		public Object[] toArray() {
			try (Transaction t = lock(false, null)) {
				Object[] array = new Object[size()];
				for (int i = 0; i < array.length; i++)
					array[i] = get(i);
				return array;
			}
		}

		@Override
		public <T> T[] toArray(T[] a) {
			try (Transaction t = lock(false, null)) {
				T[] array = a.length >= size() ? a : (T[]) Array.newInstance(a.getClass().getComponentType(), size());
				for (int i = 0; i < array.length; i++)
					array[i] = (T) get(i);
				return array;
			}
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

		protected ListElement<E> wrapElement(ListElement<E> el) {
			return el == null ? null : new SubListElement(el);
		}

		protected MutableListElement<E> wrapElement(MutableListElement<E> el) {
			return el == null ? null : new MutableSubListElement(el);
		}

		protected class SubListElement implements ListElement<E> {
			private final ListElement<E> theWrappedEl;

			protected SubListElement(ListElement<E> wrappedEl) {
				theWrappedEl = wrappedEl;
			}

			protected SubList<E> getSubList() {
				return SubList.this;
			}

			protected ListElement<E> getWrappedEl() {
				return theWrappedEl;
			}

			@Override
			public ElementId getElementId() {
				return theWrappedEl.getElementId();
			}

			@Override
			public E get() {
				return theWrappedEl.get();
			}

			@Override
			public ListElement<E> getAdjacent(boolean next) {
				ListElement<E> adj = theWrappedEl.getAdjacent(next);
				if (adj == null)
					return null;
				else if (next) {
					if (adj.getElementsBefore() >= theEnd)
						return null;
				} else {
					if (adj.getElementsBefore() < theStart)
						return null;
				}
				return wrapAdj(adj);
			}

			protected ListElement<E> wrapAdj(ListElement<E> adj) {
				return new SubListElement(adj);
			}

			@Override
			public int getElementsBefore() {
				return theWrappedEl.getElementsBefore() - theStart;
			}

			@Override
			public int getElementsAfter() {
				return theEnd - theWrappedEl.getElementsBefore() - 1;
			}

			@Override
			public int hashCode() {
				return theWrappedEl.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj)
					return true;
				else if (!(obj instanceof SubList.SubListElement))
					return false;
				SubList<?>.SubListElement other = (SubList<?>.SubListElement) obj;
				return getWrappedEl().equals(other.getWrappedEl()) && getSubList().theStart == other.getSubList().theStart
					&& getSubList().theEnd == other.getSubList().theEnd;
			}

			@Override
			public String toString() {
				return new StringBuilder().append('[').append(getElementsBefore()).append("]:").append(get()).toString();
			}
		}

		protected class MutableSubListElement extends SubListElement implements MutableListElement<E> {
			protected MutableSubListElement(MutableListElement<E> wrappedEl) {
				super(wrappedEl);
			}

			@Override
			protected MutableListElement<E> getWrappedEl() {
				return (MutableListElement<E>) super.getWrappedEl();
			}

			@Override
			public MutableListElement<E> getAdjacent(boolean next) {
				return (MutableListElement<E>) super.getAdjacent(next);
			}

			@Override
			public MutableListElement<E> wrapAdj(ListElement<E> adj) {
				return new MutableSubListElement((MutableListElement<E>) adj);
			}

			@Override
			public String isEnabled() {
				return getWrappedEl().isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				return getWrappedEl().isAcceptable(value);
			}

			@Override
			public void set(E value) throws IllegalArgumentException, UnsupportedOperationException {
				getWrappedEl().set(value);
				updated();
			}

			@Override
			public String canRemove() {
				return getWrappedEl().canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				try (Transaction t = lock(true, null)) {
					getWrappedEl().remove();
					theEnd--;
					updated();
				}
			}
		}

		private int checkIndex(int index, boolean includeTerminus) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			if (index > theEnd - theStart || (index == theEnd - theStart && !includeTerminus))
				throw new IndexOutOfBoundsException(index + " of " + (theEnd - theStart));
			return index;
		}

		@Override
		public E get(int index) {
			try (Transaction t = lock(false, null)) {
				return theWrapped.get(checkIndex(index, false) + theStart);
			}
		}

		@Override
		public String canAdd(E value, ElementId after, ElementId before) {
			try (Transaction t = lock(false, null)) {
				if (after == null && theStart > 0)
					after = theWrapped.getElement(theStart - 1).getElementId();
				int wrapSize = theWrapped.size();
				if (before == null && theEnd < wrapSize)
					before = theWrapped.getElement(theEnd).getElementId();
				return theWrapped.canAdd(value, after, before);
			}
		}

		@Override
		public ListElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			ListElement<E> newEl;
			int wrapSize;
			try (Transaction t = lock(true, null)) {
				if (after == null && theStart > 0)
					after = theWrapped.getElement(theStart - 1).getElementId();
				wrapSize = theWrapped.size();
				if (before == null && theEnd < wrapSize)
					before = theWrapped.getElement(theEnd).getElementId();
				newEl = theWrapped.addElement(value, after, before, first);
				if (newEl != null) {
					theEnd++;
				}
			}
			if (newEl == null && (theStart > 0 || theEnd < wrapSize) && !contains(value)) {
				// The contract of Collection says that the only way this method can return null is in the case that
				// the value is already in the collection and may not be added in duplicate.
				// If the underlying list does rejects the add for this reason but the element is out of the bounds of this sub list,
				// we need to throw an exception
				throw new IllegalArgumentException(StdMsg.ELEMENT_EXISTS);
			}
			return wrapElement(newEl);
		}

		@Override
		public ListElement<E> addElement(int index, E element) {
			ListElement<E> newEl;
			try (Transaction t = lock(true, null)) {
				newEl = theWrapped.addElement(theStart + checkIndex(index, true), element);
				if (newEl != null) {
					theEnd++;
				}
			}
			if (newEl == null && (theStart > 0 || theEnd < theWrapped.size()) && !contains(element)) {
				// The contract of Collection says that the only way this method can return null is in the case that
				// the value is already in the collection and may not be added in duplicate.
				// If the underlying list does rejects the add for this reason but the element is out of the bounds of this sub list,
				// we need to throw an exception
				throw new IllegalArgumentException(StdMsg.ELEMENT_EXISTS);
			}
			return wrapElement(newEl);
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			try (Transaction t = lock(false, null)) {
				if (after == null && theStart > 0)
					after = theWrapped.getElement(theStart - 1).getElementId();
				int wrapSize = theWrapped.size();
				if (before == null && theEnd < wrapSize)
					before = theWrapped.getElement(theEnd).getElementId();
				return theWrapped.canMove(valueEl, after, before);
			}
		}

		@Override
		public ListElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				if (after == null && theStart > 0)
					after = theWrapped.getElement(theStart - 1).getElementId();
				int wrapSize = theWrapped.size();
				if (before == null && theEnd < wrapSize)
					before = theWrapped.getElement(theEnd).getElementId();
				ListElement<E> newEl = theWrapped.move(valueEl, after, before, first, afterRemove);
				return wrapElement(newEl);
			}
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			try (Transaction t = lock(true, null)) {
				int preSize = theWrapped.size();
				if (!theWrapped.addAll(theStart + checkIndex(index, true), c))
					return false;
				theEnd += theWrapped.size() - preSize;
				return true;
			}
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				int sz = theWrapped.size();
				if (sz <= theStart)
					return;
				int end = theEnd;
				if (sz < end)
					end = sz;
				if (theStart == 0 && end == sz)
					theWrapped.clear();
				theWrapped.removeRange(theStart, end);
				theEnd -= sz - theWrapped.size();
			}
		}
	}

	/**
	 * Implements {@link BetterList#elementsBetween(ElementId, boolean, ElementId, boolean)}
	 * 
	 * @param <E> The type of the backing list
	 * @param <LE> The sub-type of list element provided by the list API
	 */
	class ElementList<E, LE extends ListElement<E>> extends ElementCollection<E, LE> implements BetterList<LE> {
		private final ListElement<E> theLowBound;
		private final boolean isLowIncluded;
		private final ListElement<E> theHighBound;
		private final boolean isHighIncluded;

		public ElementList(BetterList<E> collection, ListElement<E> lowBound, boolean lowIncluded, ListElement<E> highBound,
			boolean highIncluded) {
			super(collection);
			if (lowBound != null && highBound != null && lowBound.compareTo(highBound) > 0)
				throw new IllegalArgumentException("Low bound (" + lowBound + ") is after high bound (" + highBound + ")");
			theLowBound = lowBound;
			isLowIncluded = lowIncluded;
			theHighBound = highBound;
			isHighIncluded = highIncluded;
		}

		@Override
		protected BetterList<E> getCollection() {
			return (BetterList<E>) super.getCollection();
		}

		/** @return The low bound of this sub-collection */
		public ListElement<E> getLowBound() {
			return theLowBound;
		}

		/** @return Whether the low bound of this sub-collection is included or excluded from this sub-collection */
		public boolean isLowIncluded() {
			return isLowIncluded;
		}

		/** @return The high bound of this sub-collection */
		public ListElement<E> getHighBound() {
			return theHighBound;
		}

		/** @return Whether the high bound of this sub-collection is included or excluded from this sub-collection */
		public boolean isHighIncluded() {
			return isHighIncluded;
		}

		protected boolean check(ElementId toCheck, boolean low, boolean high) {
			if (low && theLowBound != null) {
				int comp = toCheck.compareTo(theLowBound.getElementId());
				if (comp < 0 || (comp == 0 && !isLowIncluded))
					return false;
			}
			if (high && theHighBound != null) {
				int comp = toCheck.compareTo(theHighBound.getElementId());
				if (comp > 0 || (comp == 0 && !isHighIncluded))
					return false;
			}
			return true;
		}

		@Override
		public boolean isEmpty() {
			return size() == 0;
		}

		@Override
		public int size() {
			int size = super.size();
			if (theLowBound != null) {
				size -= theLowBound.getElementsBefore();
				if (!isLowIncluded)
					size--;
			}
			if (theHighBound != null) {
				size -= theHighBound.getElementsAfter();
				if (!isHighIncluded)
					size--;
			}
			return size;
		}

		@Override
		public ListElement<LE> getElement(LE value, boolean first) {
			if (value == null || !check(value.getElementId(), true, true))
				return null;
			return getElement(value.getElementId());
		}

		@Override
		public ListElement<LE> getElement(ElementId id) {
			if (!check(id, true, true))
				throw new NoSuchElementException("Element is not included in this sub-list: " + id);
			return (ListElement<LE>) super.getElement(id);
		}

		@Override
		public ListElement<LE> getTerminalElement(boolean first) {
			CollectionElement<E> el;
			if (first) {
				if (theLowBound != null) {
					if (isLowIncluded)
						el = theLowBound;
					else
						el = theLowBound.getAdjacent(true);
				} else
					el = getCollection().getTerminalElement(first);
				if (el != null && !check(el.getElementId(), false, true))
					return null;
			} else {
				if (theHighBound != null) {
					if (isHighIncluded)
						el = theHighBound;
					else
						el = theHighBound.getAdjacent(false);
				} else
					el = getCollection().getTerminalElement(first);
				if (el != null && !check(el.getElementId(), true, false))
					return null;
			}
			return wrap(el);
		}

		@Override
		public MutableListElement<LE> mutableElement(ElementId id) {
			if (!check(id, true, true))
				throw new NoSuchElementException("Element is not included in this sub-list: " + id);
			return (MutableListElement<LE>) super.mutableElement(id);
		}

		@Override
		public BetterList<CollectionElement<LE>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return QommonsUtils.filterMap(super.getElementsBySource(sourceEl, sourceCollection), el -> check(el.getElementId(), true, true),
				null);
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (!check(localElement, true, true))
				throw new NoSuchElementException("Element is not included in this sub-list: " + localElement);
			return super.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public ListElement<LE> addElement(LE value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<LE>) super.addElement(value, after, before, first);
		}

		@Override
		public ListElement<LE> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove) {
			if (!check(valueEl, true, true))
				throw new NoSuchElementException("Element is not included in this sub-list: " + valueEl);
			if (after != null && !check(after, true, true))
				throw new NoSuchElementException("Element is not included in this sub-list: " + after);
			if (before != null && !check(before, true, true))
				throw new NoSuchElementException("Element is not included in this sub-list: " + before);
			return (ListElement<LE>) super.move(valueEl, after, before, first, afterRemove);
		}

		@Override
		public ListElement<LE> getElement(int index) throws IndexOutOfBoundsException {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int index2 = index;
			if (theLowBound != null) {
				index2 += theLowBound.getElementsBefore();
				if (!isLowIncluded)
					index2++;
			}
			CollectionElement<E> el = getCollection().getElement(index2);
			if (!check(el.getElementId(), false, true))
				throw new IndexOutOfBoundsException(index + " of " + size());
			return wrap(el);
		}

		@Override
		public boolean isContentControlled() {
			return getCollection().isContentControlled();
		}

		@Override
		public void clear() {
			if (theLowBound == null && theHighBound == null)
				super.clear();
			else {
				int low;
				if (theLowBound == null)
					low = 0;
				else {
					low = theLowBound.getElementsBefore();
					if (!isLowIncluded)
						low++;
				}
				int high;
				if (theHighBound == null)
					high = getCollection().size();
				else {
					high = theHighBound.getElementsBefore();
					if (isHighIncluded)
						high++;
				}
				getCollection().removeRange(low, high);
			}
		}

		@Override
		protected ListElement<LE> wrap(CollectionElement<E> el) {
			return el == null ? null : new WrappedListElement((ListElement<E>) el);
		}

		@Override
		protected MutableListElement<LE> wrapMutable(CollectionElement<E> el) {
			return new MutableWrappedListElement((MutableListElement<E>) el);
		}

		class WrappedListElement extends WrappedCollectionElement implements ListElement<LE> {
			WrappedListElement(ListElement<E> element) {
				super(element);
			}

			@Override
			public ListElement<LE> getAdjacent(boolean next) {
				return wrap(getWrapped().getAdjacent(next));
			}

			@Override
			public int getElementsBefore() {
				return getWrapped().getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return getWrapped().getElementsAfter();
			}
		}

		class MutableWrappedListElement extends MutableWrappedCollectionElement implements MutableListElement<LE> {
			MutableWrappedListElement(ListElement<E> element) {
				super(element);
			}

			@Override
			public MutableListElement<LE> getAdjacent(boolean next) {
				return (MutableListElement<LE>) super.getAdjacent(next);
			}

			@Override
			public int getElementsBefore() {
				return getWrapped().getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return getWrapped().getElementsAfter();
			}
		}
	}

	/**
	 * An immutable {@link BetterList}
	 * 
	 * @param <E> The type of values in the list
	 */
	abstract class AbstractConstantList<E> extends AbstractIdentifiable implements BetterList<E> {
		@Override
		public ThreadConstraint getThreadConstraint() {
			return ThreadConstraint.NONE;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return Collections.emptyList();
		}

		@Override
		public CoreId getCoreId() {
			return CoreId.EMPTY;
		}

		@Override
		public long getStamp() {
			return 0;
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public abstract E get(int index);

		@Override
		public ListElement<E> getTerminalElement(boolean first) {
			if (isEmpty())
				return null;
			return elementFor(first ? 0 : size() - 1);
		}

		@Override
		public ListElement<E> getElement(int index) {
			return elementFor(index);
		}

		private ListElement<E> elementFor(int index) {
			if (index < 0 || index >= size())
				throw new IndexOutOfBoundsException(index + " of " + size());
			return new ListElement<E>() {
				@Override
				public ElementId getElementId() {
					return new IndexElementId(index);
				}

				@Override
				public E get() {
					return AbstractConstantList.this.get(index);
				}

				@Override
				public ListElement<E> getAdjacent(boolean next) {
					int adjIndex;
					if (next) {
						adjIndex = index + 1;
						if (adjIndex == size())
							return null;
					} else {
						if (index == 0)
							return null;
						adjIndex = index - 1;
					}
					return elementFor(adjIndex);
				}

				@Override
				public int getElementsBefore() {
					return index;
				}

				@Override
				public int getElementsAfter() {
					return size() - index - 1;
				}

				@Override
				public String toString() {
					return new StringBuilder()//
						.append('[').append(index).append(']').append('=').append(get())//
						.toString();
				}
			};
		}

		@Override
		public ListElement<E> getElement(E value, boolean first) {
			int size = size();
			for (int i = 0; i < size; i++)
				if (Objects.equals(get(i), value))
					return elementFor(i);
			return null;
		}

		@Override
		public ListElement<E> getElement(ElementId id) {
			return elementFor(((IndexElementId) id).index);
		}

		@Override
		public MutableListElement<E> mutableElement(ElementId id) {
			return mutableElementFor(((IndexElementId) id).index);
		}

		private MutableListElement<E> mutableElementFor(int index) {
			if (index < 0 || index >= size())
				throw new IndexOutOfBoundsException(index + " of " + size());
			return new MutableListElement<E>() {
				@Override
				public ElementId getElementId() {
					return new IndexElementId(index);
				}

				@Override
				public E get() {
					return AbstractConstantList.this.get(index);
				}

				@Override
				public MutableListElement<E> getAdjacent(boolean next) {
					int adjIndex;
					if (next) {
						adjIndex = index + 1;
						if (adjIndex == size())
							return null;
					} else {
						if (index == 0)
							return null;
						adjIndex = index - 1;
					}
					return mutableElementFor(adjIndex);
				}

				@Override
				public int getElementsBefore() {
					return index;
				}

				@Override
				public int getElementsAfter() {
					return size() - index - 1;
				}

				@Override
				public String isEnabled() {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public String isAcceptable(E value) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public String canRemove() {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public String toString() {
					return new StringBuilder()//
						.append('[').append(index).append(']').append('=').append(get())//
						.toString();
				}
			};
		}

		@Override
		public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return BetterList.empty();
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this) {
				if (!(localElement instanceof AbstractConstantList<?>.IndexElementId) || ((IndexElementId) localElement).getList() != this)
					throw new IllegalArgumentException(localElement + " is not an element of this list");
				return BetterList.of(localElement);
			}
			return BetterList.empty();
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			if (equivalentEl instanceof AbstractConstantList.IndexElementId && ((IndexElementId) equivalentEl).getList() == this)
				return equivalentEl;
			return null;
		}

		@Override
		public E getFirst() {
			if (isEmpty())
				throw new NoSuchElementException();
			return get(0);
		}

		@Override
		public E getLast() {
			if (isEmpty())
				throw new NoSuchElementException();
			return get(size() - 1);
		}

		@Override
		public E peekFirst() {
			return isEmpty() ? null : get(0);
		}

		@Override
		public E peekLast() {
			return isEmpty() ? null : get(size() - 1);
		}

		@Override
		public E element() {
			if (isEmpty())
				throw new NoSuchElementException();
			return get(0);
		}

		@Override
		public E peek() {
			return isEmpty() ? null : get(0);
		}

		@Override
		public String canAdd(E value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ListElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return null;
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			if (after != null && valueEl.compareTo(after) < 0)
				return StdMsg.UNSUPPORTED_OPERATION;
			else if (before != null && valueEl.compareTo(before) > 0)
				return StdMsg.UNSUPPORTED_OPERATION;
			return null;
		}

		@Override
		public ListElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			if (after != null && valueEl.compareTo(after) < 0)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else if (before != null && valueEl.compareTo(before) > 0)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return getElement(valueEl);
		}

		@Override
		public void clear() {
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

		private class IndexElementId implements ElementId {
			final int index;

			IndexElementId(int index) {
				this.index = index;
			}

			AbstractConstantList<E> getList() {
				return AbstractConstantList.this;
			}

			@Override
			public int compareTo(ElementId o) {
				return index - ((IndexElementId) o).index;
			}

			@Override
			public boolean isPresent() {
				return true;
			}

			@Override
			public int hashCode() {
				return index;
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof AbstractConstantList<?>.IndexElementId && index == ((IndexElementId) obj).index//
					&& getList().getIdentity().equals(((IndexElementId) obj).getList().getIdentity());
			}

			@Override
			public String toString() {
				return new StringBuilder("[").append(index).append("]=").append(get(index)).toString();
			}
		}
	}

	/**
	 * A constant BetterList backed by an array
	 * 
	 * @param <E> The type of values in the list
	 */
	class BetterArrayList<E> extends AbstractConstantList<E> {
		private final Object[] theValues;

		public BetterArrayList(Object[] values) {
			theValues = values;
		}

		@Override
		protected Object createIdentity() {
			List<Object> identities = QommonsUtils.map(Arrays.asList(theValues), v -> {
				if (v instanceof Identifiable)
					return ((Identifiable) v).getIdentity();
				else
					return v;
			}, true);
			return Identifiable.idFor(identities, identities::toString, identities::hashCode, identities::equals);
		}

		@Override
		public int size() {
			return theValues.length;
		}

		@Override
		public boolean isEmpty() {
			return theValues.length == 0;
		}

		@Override
		public E get(int index) {
			return (E) theValues[index];
		}
	}

	/**
	 * An immutable {@link BetterList}
	 * 
	 * @param <E> The type of values in the list
	 */
	class ConstantList<E> extends AbstractConstantList<E> implements BetterList<E> {
		private final List<? extends E> theValues;

		/** @param values The values for this list. The backing list should never be modified. */
		public ConstantList(List<? extends E> values) {
			if (values == null)
				throw new NullPointerException();
			theValues = values;
		}

		@Override
		protected Object createIdentity() {
			List<Object> identities = QommonsUtils.map(theValues, v -> {
				if (v instanceof Identifiable)
					return ((Identifiable) v).getIdentity();
				else
					return v;
			}, true);
			return Identifiable.idFor(identities, identities::toString, identities::hashCode, identities::equals);
		}

		@Override
		public int size() {
			return theValues.size();
		}

		@Override
		public boolean isEmpty() {
			return theValues.isEmpty();
		}

		@Override
		public E get(int index) {
			return theValues.get(index);
		}

		@Override
		public boolean contains(Object o) {
			return theValues.contains(o);
		}
	}
}
