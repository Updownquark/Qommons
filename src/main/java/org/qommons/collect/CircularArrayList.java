package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.qommons.ReversibleCollection;
import org.qommons.Transaction;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

/**
 * A list/deque that uses an array that is indexed circularly. This allows performance improvements due to not having to move array contents
 * when items are removed from the beginning of the list
 * 
 * @param <E> The type of elements in the list
 */
public class CircularArrayList<E> implements BetterCollection<E>, ReversibleCollection<E>, TransactableList<E>, Deque<E> {
	private static final Object[] EMPTY_ARRAY = new Object[0];
	/**
	 * The maximum size of array to allocate. Some VMs reserve some header words in an array. Attempts to allocate larger arrays may result
	 * in OutOfMemoryError: Requested array size exceeds VM limit
	 */
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	private Object[] theArray;
	private final boolean isCapLocked;
	private final StampedLockingStrategy theLocker;
	private int theOffset;
	private int theSize;

	public CircularArrayList() {
		this(0, true);
	}

	public CircularArrayList(int initCap, boolean adjustCapacity) {
		theArray = initCap == 0 ? EMPTY_ARRAY : new Object[initCap];
		isCapLocked = adjustCapacity;
		theLocker = new StampedLockingStrategy();
	}

	@Override
	public boolean isLockSupported() {
		return true;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theLocker.lock(write, cause);
	}

	public long getStamp() {
		return theLocker.getStamp();
	}

	public boolean check(long stamp) {
		return theLocker.check(stamp);
	}

	@Override
	public int size() {
		return theSize;
	}

	@Override
	public boolean isEmpty() {
		return theSize == 0;
	}

	@Override
	public Betterator<E> iterator() {
		return BetterCollection.super.iterator();
	}

	@Override
	public ElementSpliterator<E> spliterator() {
		return spliterator(true);
	}

	@Override
	public boolean contains(Object o) {
		return theLocker.doOptimistically(false, (bool, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			int t = offset;
			for (int i = 0; i < size; i++) {
				Object v = array[t];
				if (!theLocker.check(stamp))
					return false;
				if (Objects.equals(v, o))
					return true;
				t++;
				if (t == array.length)
					t = 0;
			}
			return false;
		});
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return theLocker.doOptimistically(false, (bool, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			for (Object o : c) {
				boolean found = false;
				int t = offset;
				for (int i = 0; i < size; i++) {
					Object v = array[t];
					if (!theLocker.check(stamp))
						return false;
					if (Objects.equals(v, o)) {
						found = true;
						break;
					}
					t++;
					if (t == array.length)
						t = 0;
				}
				if (!found)
					return false;
			}
			return true;
		});
	}

	@Override
	public Object[] toArray() {
		return theLocker.doOptimistically((Object[]) null, (a, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!theLocker.check(stamp))
				return a;
			if (a == null || a.length != size)
				a = new Object[size];
			internalArrayCopy(array, offset, size, a);
			return a;
		});
	}

	@Override
	public <T> T[] toArray(final T[] a) {
		return theLocker.doOptimistically(a, (arr, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!theLocker.check(stamp))
				return arr;
			if (arr.length == size) {
			} else if (arr.length > size && arr == a) {
			} else
				arr = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
			if (!theLocker.check(stamp))
				return arr;
			internalArrayCopy(array, offset, size, arr);
			return a;
		});
	}

	@Override
	public E get(int index) {
		return theLocker.doOptimistically(null, (v, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!theLocker.check(stamp))
				return null;
			return (E) array[translateToInternalIndex(array, offset, size, index)];
		});
	}

	@Override
	public E getFirst() {
		return theLocker.doOptimistically(null, (v, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!theLocker.check(stamp))
				return null;
			if (size == 0)
				throw new NoSuchElementException();
			return (E) array[offset];
		});
	}

	@Override
	public E getLast() {
		return theLocker.doOptimistically(null, (v, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!theLocker.check(stamp))
				return null;
			if (size == 0)
				throw new NoSuchElementException();
			return (E) array[translateToInternalIndex(array, offset, size, size - 1)];
		});
	}

	@Override
	public E peekFirst() {
		return theLocker.doOptimistically(null, (v, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!theLocker.check(stamp))
				return null;
			return size == 0 ? null : (E) array[offset];
		});
	}

	@Override
	public E peekLast() {
		return theLocker.doOptimistically(null, (v, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!theLocker.check(stamp))
				return null;
			return size == 0 ? null : (E) array[translateToInternalIndex(array, offset, size, size - 1)];
		});
	}

	@Override
	public E element() {
		return getFirst();
	}

	@Override
	public E peek() {
		return peekFirst();
	}

	@Override
	public int indexOf(Object o) {
		return theLocker.doOptimistically(-1, (idx, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			int t = offset;
			for (int i = 0; i < size; i++) {
				Object v = array[t];
				if (!theLocker.check(stamp))
					return -1;
				if (Objects.equals(v, o))
					return i;
				t++;
				if (t == array.length)
					t = 0;
			}
			return -1;
		});
	}

	@Override
	public int lastIndexOf(Object o) {
		return theLocker.doOptimistically(-1, (idx, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			int t = offset;
			for (int i = size - 1; i >= 0; i--) {
				Object v = array[t];
				if (!theLocker.check(stamp))
					return -1;
				if (Objects.equals(v, o))
					return i;
				t--;
				if (t < 0)
					t = array.length - 1;
			}
			return -1;
		});
	}

	@Override
	public E set(int index, E element) {
		try (Transaction t = lock(true, null)) {
			int translated = translateToInternalIndex(index, false);
			E old = (E) theArray[translated];
			theArray[translated] = element;
			return old;
		}
	}

	@Override
	public boolean add(E e) {
		try (Transaction t = lock(true, null)) {
			theArray[translateToInternalIndex(theSize, true)] = e;
			theSize++;
		}
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		if (c.isEmpty())
			return false;
		try (Transaction t = lock(true, null)) {
			ensureCapacity(theSize + c.size());
			if (theSize == 0) {
				c.toArray(theArray);
				theOffset = 0;
				theSize = c.size();
			} else {
				theSize += c.size();
				int idx = translateToInternalIndex(theSize, false);
				for (E e : c) {
					theArray[idx] = e;
					idx++;
					if (idx == theArray.length)
						idx = 0;
				}
			}
		}
		return true;
	}

	@Override
	public void add(int index, E element) {
		try (Transaction t = lock(true, null)) {
			internalAdd(index, element);
		}
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		if (index == 0)
			return addAll(c);
		if (c.isEmpty())
			return false;
		try (Transaction t = lock(true, null)) {
			makeRoom(index, c.size());
			int ti = translateToInternalIndex(index, false);
			for (E e : c) {
				theArray[ti] = e;
				ti++;
				if (ti == theArray.length)
					ti = 0;
			}
		}
		return true;
	}

	@Override
	public void addFirst(E e) {
		add(0, e);
	}

	@Override
	public void addLast(E e) {
		add(e);
	}

	@Override
	public boolean offerFirst(E e) {
		addFirst(e);
		return true;
	}

	@Override
	public boolean offerLast(E e) {
		return add(e);
	}

	@Override
	public boolean offer(E e) {
		return add(e);
	}

	@Override
	public void push(E e) {
		addFirst(e);
	}

	@Override
	public boolean remove(Object o) {
		try (Transaction t = lock(true, null)) {
			ElementSpliterator<E> iter = spliterator();
			boolean[] found = new boolean[1];
			while (!found[0] && iter.tryAdvanceElement(el -> {
				if (Objects.equals(el.get(), o)) {
					found[0] = true;
					el.remove();
				}
			})) {
			}
			return found[0];
		}
	}

	@Override
	public void removeRange(int fromIndex, int toIndex) {
		int count = toIndex - fromIndex;
		if (count == 0)
			return;
		if (count < 0)
			throw new IllegalArgumentException(fromIndex + ">" + toIndex);
		if (fromIndex < 0)
			throw new IndexOutOfBoundsException("" + fromIndex);
		if (toIndex > theSize)
			throw new IndexOutOfBoundsException(toIndex + " of " + theSize);
		try (Transaction t = lock(true, null)) {
			if (fromIndex < theSize - toIndex) {
				moveContents(theOffset, fromIndex, count);
				clearEntries(theOffset, fromIndex);
				theOffset = (theOffset + count) % theArray.length;
				theSize -= count;
			}
		}
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		if (c.isEmpty())
			return false;
		return removeIf(o -> c.contains(o));
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		if (c.isEmpty()) {
			boolean modified = theSize > 0;
			clear();
			return modified;
		}
		return removeIf(o -> !c.contains(o));
	}

	@Override
	public boolean removeIf(Predicate<? super E> filter) {
		if (theSize == 0)
			return false;
		int removed = 0;
		try (Transaction t = lock(true, null)) {
			int cap = theArray.length;
			int inspect = theOffset;
			int copyTo = inspect;
			int dest = (theOffset + theSize) % cap;
			while (inspect != dest) {
				E value = (E) theArray[inspect];
				if (filter.test(value)) {
					removed++;
				} else {
					if (removed > 0)
						theArray[copyTo] = value;
					copyTo++;
					if (copyTo == cap)
						copyTo = 0;
				}
				inspect++;
				if (inspect == cap)
					inspect = 0;
			}
			clearEntries(copyTo, removed);
			theSize -= removed;
		}
		return removed > 0;
	}

	@Override
	public void clear() {
		try (Transaction t = lock(true, null)) {
			Arrays.fill(theArray, theOffset, Math.min(theOffset + theSize, theArray.length), null);
			if (theOffset + theSize > theArray.length)
				Arrays.fill(theArray, 0, theOffset + theSize - theArray.length, null);
			theSize = 0;
			theOffset = 0;
		}
	}

	@Override
	public E remove(int index) {
		try (Transaction t = lock(true, null)) {
			return internalRemove(index, translateToInternalIndex(index, false));
		}
	}

	@Override
	public E removeFirst() {
		return remove(0);
	}

	@Override
	public E removeLast() {
		try (Transaction t = lock(true, null)) {
			return internalRemove(theSize - 1, translateToInternalIndex(theSize - 1, false));
		}
	}

	@Override
	public E pollFirst() {
		try (Transaction t = lock(true, null)) {
			if (theSize == 0)
				return null;
			return internalRemove(0, translateToInternalIndex(0, false));
		}
	}

	@Override
	public E pollLast() {
		try (Transaction t = lock(true, null)) {
			if (theSize == 0)
				return null;
			return internalRemove(theSize - 1, translateToInternalIndex(theSize - 1, false));
		}
	}

	@Override
	public boolean removeFirstOccurrence(Object o) {
		return remove(o);
	}

	@Override
	public boolean removeLastOccurrence(Object o) {
		try (Transaction t = lock(true, null)) {
			ElementSpliterator<E> iter = spliterator(false);
			boolean[] found = new boolean[1];
			while (!found[0] && iter.tryAdvanceElement(el -> {
				if (Objects.equals(el.get(), o)) {
					found[0] = true;
					el.remove();
				}
			})) {
			}
			return found[0];
		}
	}

	@Override
	public E remove() {
		return remove(0);
	}

	@Override
	public E poll() {
		try (Transaction t = lock(true, null)) {
			if (theSize == 0)
				return null;
			return internalRemove(0, translateToInternalIndex(0, false));
		}
	}

	@Override
	public E pop() {
		return removeFirst();
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		return new ListIter(new ArraySpliterator(0, -1, index, true, theLocker.subLock()));
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		if (toIndex == fromIndex)
			return Collections.emptyList();
		return new SubList(fromIndex, toIndex, theLocker.subLock());
	}

	@Override
	public Iterable<E> descending() {
		return () -> descendingIterator();
	}

	@Override
	public Betterator<E> descendingIterator() {
		return new ElementSpliterator.SpliteratorBetterator<>(spliterator(false));
	}

	public boolean ensureCapacity(int size) {
		if (theArray.length < size) {
			// overflow-conscious code
			int oldCapacity = theArray.length;
			int newCapacity = oldCapacity + (oldCapacity >> 1);
			if (newCapacity - size < 0)
				newCapacity = size;
			if (newCapacity - MAX_ARRAY_SIZE > 0)
				newCapacity = hugeCapacity(size);
			Object[] newArray = new Object[newCapacity];
			if (theSize > 0) {
				internalArrayCopy(newArray);
			}
			theOffset = 0;
			theArray = newArray;
			return true;
		} else
			return false;
	}

	private static int hugeCapacity(int minCapacity) {
		if (minCapacity < 0) // overflow
			throw new OutOfMemoryError();
		return (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
	}

	public ReversibleSpliterator<E> spliterator(boolean forward) {
		return new ArraySpliterator(0, theSize, forward ? 0 : theSize, forward, theLocker.subLock());
	}

	protected final int translateToInternalIndex(int index, boolean adding) {
		if (adding && index == theSize)
			ensureCapacity(theSize + 1);
		return translateToInternalIndex(theArray, theOffset, theSize, index);
	}

	private static final int translateToInternalIndex(Object[] array, int offset, int size, int index) {
		if (index < 0)
			throw new ArrayIndexOutOfBoundsException(index);
		else if (index > size)
			throw new ArrayIndexOutOfBoundsException(index + " of " + size);
		else if (index == size)
			throw new ArrayIndexOutOfBoundsException(index + " of " + size);
		return (index + offset) % array.length;
	}

	private void makeRoom(int index, int spaces) {
		ensureCapacity(theSize + spaces);
		if (index < theSize - 1)
			moveContents(theOffset, index, -spaces);
		else
			moveContents(theOffset + index, theSize - index, spaces);
	}

	private void moveContents(int sourceStart, int count, int offset) {
		int cap = theArray.length;
		int sourceEnd = sourceStart + count;
		int destStart = sourceStart + offset;
		int destEnd = destStart + count;
		if (destStart < 0) {
			destStart += cap;
			destEnd += cap;
		}

		// Either the source interval or the destination interval or neither may be wrapped, but the other must be contiguous
		// because count+offset<=capacity
		if (sourceEnd < 0 || sourceEnd > cap) {
			// The source interval is wrapped
			if (destStart > cap) {
				destStart -= cap;
			}
			int firstSectionLength = cap - sourceStart;
			System.arraycopy(theArray, sourceStart, theArray, destStart, firstSectionLength);
			System.arraycopy(theArray, 0, theArray, destStart + firstSectionLength, count - firstSectionLength);
		} else if (destEnd > cap) {
			// The destination interval is wrapped
			int firstSectionLength = cap - destStart;
			System.arraycopy(theArray, sourceStart, theArray, destStart, firstSectionLength);
			System.arraycopy(theArray, sourceStart + firstSectionLength, theArray, 0, count - firstSectionLength);
		} else {
			// Neither interval is wrapped
			System.arraycopy(theArray, sourceStart, theArray, destStart, count);
		}
		theLocker.indexChanged();
	}

	private void clearEntries(int from, int count) {
		int end = from + count;
		int cap = theArray.length;
		int overshoot = end - cap;
		if (overshoot > 0) {
			Arrays.fill(theArray, from, cap, null);
			Arrays.fill(theArray, 0, overshoot, null);
		} else
			Arrays.fill(theArray, from, end, null);
	}

	protected final void internalArrayCopy(Object[] a) {
		internalArrayCopy(theArray, theOffset, theSize, a);
	}

	private static final void internalArrayCopy(Object[] src, int offset, int size, Object[] dest) {
		System.arraycopy(src, offset, dest, 0, Math.min(size, src.length - offset));
		if (offset + size > src.length)
			System.arraycopy(src, 0, dest, src.length - offset, size - src.length + offset);
	}

	protected final void internalAdd(int index, E element) {
		makeRoom(index, 1);
		theArray[translateToInternalIndex(theSize, true)] = element;
		theLocker.indexChanged();
		theSize++;
	}

	protected final E internalRemove(int listIndex, int translatedIndex) {
		E removed = (E) theArray[translatedIndex];
		// Figure out the optimum way to move array elements
		if (theSize == 1) {
			theArray[theOffset] = null; // Remove reference
			theOffset = theSize = 0;
		} else if (listIndex < theSize / 2) {
			moveContents(theOffset, listIndex, 1);
			theArray[theOffset] = null; // Remove reference
			theOffset++;
			theSize--;
		} else {
			moveContents(theOffset + listIndex + 1, theSize - listIndex - 1, -1);
			theArray[(theOffset + theSize) % theArray.length] = null; // Remove reference
			theSize--;
		}
		theLocker.indexChanged();
		return removed;
	}

	private class ArraySpliterator implements ReversibleSpliterator<E> {
		private final boolean isForward;
		private int theStart;
		private int theEnd;
		private int theCursor; // The index of the element that would be given to the consumer for tryAdvance()
		private int theTranslatedCursor;
		private boolean elementExists;
		private final CollectionElement<E> element;
		private final StampedLockingStrategy.SubLockingStrategy theSubLock;

		ArraySpliterator(int start, int end, int initIndex, boolean forward, StampedLockingStrategy.SubLockingStrategy subLock) {
			int size = theSize;
			subLock.check();
			if (end < 0)
				end = size;
			if (start < 0)
				throw new IndexOutOfBoundsException("" + start);
			if (end > size)
				throw new IndexOutOfBoundsException(end + " of " + size);
			if (start > end)
				throw new IndexOutOfBoundsException(start + ">" + end);
			isForward = forward;
			theStart = start;
			theEnd = end;
			theCursor = initIndex;
			theSubLock = subLock;
			theTranslatedCursor = (theCursor + theOffset) % theArray.length;
			element = new CollectionElement<E>() {
				@Override
				public TypeToken<E> getType() {
					return ArraySpliterator.this.getType();
				}

				@Override
				public Value<String> isEnabled() {
					return Value.constant(TypeToken.of(String.class), !elementExists ? "Element has been removed" : null);
				}

				@Override
				public <V extends E> String isAcceptable(V value) {
					if (!elementExists)
						return "Element has been removed";
					return null;
				}

				@Override
				public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
					if (!elementExists)
						throw new IllegalArgumentException("Element has been removed");
					E old;
					try (Transaction t = theSubLock.lockForWrite()) {
						old = (E) theArray[theTranslatedCursor];
						theArray[theTranslatedCursor] = value;
					}
					return old;
				}

				@Override
				public E get() {
					if (!elementExists)
						throw new IllegalStateException("Element has been removed");
					return theSubLock.doOptimistically(null, (v, stamp) -> {
						Object[] array = theArray;
						return theTranslatedCursor < array.length ? (E) array[theTranslatedCursor] : null;
					});
				}

				@Override
				public String canRemove() {
					if (!elementExists)
						return "Element is already removed";
					return null;
				}

				@Override
				public void remove() throws IllegalArgumentException {
					if (!elementExists)
						throw new IllegalArgumentException("Element is already removed");
					try (Transaction t = theSubLock.lockForWrite()) {
						internalRemove(theCursor, theTranslatedCursor);
						theEnd--;
						theSubLock.indexChanged(-1);
					}
					elementExists = false;
				}
			};
		}

		@Override
		public TypeToken<E> getType() {
			return (TypeToken<E>) TypeToken.of(Object.class);
		}

		@Override
		public long estimateSize() {
			return theEnd - theStart;
		}

		@Override
		public int characteristics() {
			return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
		}

		@Override
		public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
			return tryElement(action, true);
		}

		@Override
		public boolean tryReverseElement(Consumer<? super CollectionElement<E>> action) {
			return tryElement(action, false);
		}

		private boolean tryElement(Consumer<? super CollectionElement<E>> action, boolean advance) {
			if (!isForward)
				advance = !advance;
			if (advance) {
				if (theCursor > theEnd)
					return false;
				if (elementExists) { // Advance the cursor beyond the current element
					if (theCursor == theEnd)
						return false;
					theCursor++;
					theTranslatedCursor++;
					if (theTranslatedCursor == theArray.length)
						theTranslatedCursor = 0;
				}
			} else {
				if (theCursor == theStart)
					return false;
				theCursor--;
				theTranslatedCursor--;
				if (theTranslatedCursor < 0)
					theTranslatedCursor = theArray.length - 1;
			}
			elementExists = true;
			action.accept(element);
			return true;
		}

		@Override
		public ReversibleSpliterator<E> trySplit() {
			if (theEnd - theStart <= 1)
				return null;
			int mid = (theStart + theEnd) / 2;
			ArraySpliterator split;
			if (theCursor <= mid) {
				split = new ArraySpliterator(mid, theEnd, mid, isForward, theSubLock.siblingLock());
				theEnd = mid;
			} else {
				split = new ArraySpliterator(theStart, mid, mid, isForward, theSubLock.siblingLock());
				theStart = mid;
			}
			return split;
		}

		int getIndex() {
			return theCursor;
		}
	}

	class ListIter extends ReversibleSpliterator.PartialListIterator<E> {
		ListIter(ArraySpliterator backing) {
			super(backing);
		}

		@Override
		public int nextIndex() {
			return ((ArraySpliterator) backing).getIndex() + 1;
		}

		@Override
		public int previousIndex() {
			return ((ArraySpliterator) backing).getIndex();
		}

		@Override
		public void add(E e) {
			try (Transaction t = ((ArraySpliterator) backing).theSubLock.lockForWrite()) {
				CircularArrayList.this.add(((ArraySpliterator) backing).getIndex(), e);
				next(); // Skip over the element just inserted
				((ArraySpliterator) backing).theSubLock.indexChanged(1);
			}
		}
	}

	class SubList implements BetterCollection<E>, ReversibleCollection<E>, RRList<E> {
		private final int theStart;
		private int theEnd;
		private final StampedLockingStrategy.SubLockingStrategy theSubLock;

		SubList(int start, int end, StampedLockingStrategy.SubLockingStrategy subLock) {
			int size = theSize;
			subLock.check();
			if (start < 0)
				throw new IndexOutOfBoundsException("" + start);
			if (end > size)
				throw new IndexOutOfBoundsException(end + " of " + size);
			if (start > end)
				throw new IndexOutOfBoundsException(start + ">" + end);
			this.theStart = start;
			this.theEnd = end;
			theSubLock = subLock;
		}

		@Override
		public int size() {
			int start = theStart;
			int end = theEnd;
			theSubLock.check();
			return end - start;
		}

		@Override
		public boolean isEmpty() {
			int start=theStart;
			int end=theEnd;
			theSubLock.check();
			return start == end;
		}

		@Override
		public boolean contains(Object o) {
			return theSubLock.doOptimistically(false, (bool, stamp) -> {
				Object[] array = theArray;
				int offset = theOffset;
				int size = theSize;
				int index = theStart;
				int end = theEnd;
				int t = translateToInternalIndex(array, offset, size, index);
				for (; index < end; index++) {
					Object v = array[t];
					if (!theSubLock.check(stamp))
						return false;
					if (Objects.equals(v, o))
						return true;
					t++;
					if (t == array.length)
						t = 0;
				}
				return false;
			});
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return theSubLock.doOptimistically(false, (bool, stamp) -> {
				Object[] array = theArray;
				int offset = theOffset;
				int size = theSize;
				int start = theStart;
				int end = theEnd;
				for (Object o : c) {
					boolean found = false;
					int index = start;
					int t = translateToInternalIndex(array, offset, size, index);
					for (; index < end; index++) {
						Object v = array[t];
						if (!theSubLock.check(stamp))
							return false;
						if (Objects.equals(v, o)) {
							found = true;
							break;
						}
						t++;
						if (t == array.length)
							t = 0;
					}
					if (!found)
						return false;
				}
				return true;
			});
		}

		@Override
		public Object[] toArray() {
			return theSubLock.doOptimistically((Object[]) null, (a, stamp) -> {
				Object[] array = theArray;
				int offset = theOffset;
				int size = theSize;
				int start = theStart;
				int end = theEnd;
				if (!theSubLock.check(stamp))
					return a;

				if (a == null || a.length != end - start)
					a = new Object[end - start];
				internalArrayCopy(array, translateToInternalIndex(array, offset, size, start), end - start, a);
				return a;
			});
		}

		@Override
		public <T> T[] toArray(final T[] a) {
			return theSubLock.doOptimistically(a, (arr, stamp) -> {
				Object[] array = theArray;
				int offset = theOffset;
				int size = theSize;
				int start = theStart;
				int end = theEnd;
				if (!theSubLock.check(stamp))
					return arr;
				if (arr.length == end - start) {
				} else if (arr.length > end - start && arr == a) {
				} else
					arr = (T[]) Array.newInstance(a.getClass().getComponentType(), end - start);
				if (!theSubLock.check(stamp))
					return arr;
				internalArrayCopy(array, translateToInternalIndex(array, offset, size, start), end - start, arr);
				return a;
			});
		}

		@Override
		public E get(int index) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			return theSubLock.doOptimistically(null, (v, stamp) -> {
				Object[] array = theArray;
				int offset = theOffset;
				int start = theStart;
				int end = theEnd;
				if (!theSubLock.check(stamp))
					return null;
				if (index >= (end - start))
					throw new IndexOutOfBoundsException(index + " of " + (end - start));
				int t = offset + start + index;
				if (t >= array.length)
					t -= array.length;
				if (t >= array.length) // Multiple offsets could push the translated index out 2 lengths
					t -= array.length;
				return (E) array[t];
			});
		}

		@Override
		public int indexOf(Object o) {
			return theSubLock.doOptimistically(-1, (idx, stamp) -> {
				Object[] array = theArray;
				int offset = theOffset;
				int size = theSize;
				int start = theStart;
				int end = theStart;
				if (!theSubLock.check(stamp))
					return -1;
				int index = start;
				int t = translateToInternalIndex(array, offset, size, index);
				for (; index < end; index++) {
					Object v = array[t];
					if (!theSubLock.check(stamp))
						return -1;
					if (Objects.equals(v, o))
						return index - start;
					t++;
					if (t == array.length)
						t = 0;
				}
				return -1;
			});
		}

		@Override
		public int lastIndexOf(Object o) {
			return theSubLock.doOptimistically(-1, (idx, stamp) -> {
				Object[] array = theArray;
				int offset = theOffset;
				int size = theSize;
				int start = theStart;
				int end = theStart;
				if (!theSubLock.check(stamp))
					return -1;
				int index = end - 1;
				int t = translateToInternalIndex(array, offset, size, index);
				for (; index >= start; index--) {
					Object v = array[t];
					if (!theSubLock.check(stamp))
						return -1;
					if (Objects.equals(v, o))
						return index - start;
					t--;
					if (t < 0)
						t = array.length - 1;
				}
				return -1;
			});
		}

		@Override
		public Betterator<E> iterator() {
			return new ElementSpliterator.SpliteratorBetterator<>(spliterator());
		}

		@Override
		public Iterable<E> descending() {
			return () -> new ElementSpliterator.SpliteratorBetterator<>(spliterator(false));
		}

		@Override
		public ReversibleSpliterator<E> spliterator() {
			return spliterator(true);
		}

		public ReversibleSpliterator<E> spliterator(boolean forward) {
			return new ArraySpliterator(theStart, theEnd, theStart, forward, theSubLock.subLock(added -> theEnd += added));
		}

		@Override
		public boolean add(E e) {
			try (Transaction t = theSubLock.lockForWrite()) {
				CircularArrayList.this.add(theEnd, e);
				theEnd++;
				theSubLock.indexChanged(1);
			}
			return true;
		}

		@Override
		public void add(int index, E element) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			try (Transaction t = theSubLock.lockForWrite()) {
				if (index > theEnd - theStart)
					throw new IndexOutOfBoundsException(index + " of " + (theEnd - theStart));
				internalAdd(theStart + index, element);
				theSubLock.indexChanged(1);
			}
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			if (c.isEmpty())
				return false;
			try (Transaction t = theSubLock.lockForWrite()) {
				int oldSize = theSize;
				CircularArrayList.this.addAll(theEnd, c);
				if (theSize != oldSize) {
					theEnd += theSize - oldSize;
					theSubLock.indexChanged(theSize - oldSize);
					return true;
				} else
					return false;
			}
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			if(index<0)
				throw new IndexOutOfBoundsException(""+index);
			if (c.isEmpty())
				return false;
			try (Transaction t = theSubLock.lockForWrite()) {
				if (index > theEnd - theStart)
					throw new IndexOutOfBoundsException(index + " of " + (theEnd - theStart));
				int oldSize = theSize;
				CircularArrayList.this.addAll(theStart + index, c);
				if (theSize != oldSize) {
					theEnd += theSize - oldSize;
					theSubLock.indexChanged(theSize - oldSize);
					return true;
				} else
					return false;
			}
		}

		@Override
		public boolean remove(Object o) {
			try (Transaction t = theSubLock.lockForWrite()) {
				int index = theStart;
				int ti = translateToInternalIndex(index, false);
				for (; index < theEnd; index++) {
					if (Objects.equals(theArray[ti], o)) {
						internalRemove(index, ti);
						theEnd--;
						theSubLock.indexChanged(-1);
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public E remove(int index) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			try (Transaction t = theSubLock.lockForWrite()) {
				if (index >= theEnd - theStart)
					throw new IndexOutOfBoundsException(index + " of " + (theEnd - theStart));
				E old = internalRemove(index, translateToInternalIndex(theStart + index, false));
				theSubLock.indexChanged(-1);
				return old;
			}
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			if (fromIndex < 0)
				throw new IndexOutOfBoundsException("" + fromIndex);
			else if (fromIndex > toIndex)
				throw new IndexOutOfBoundsException(fromIndex + ">" + toIndex);
			else if (fromIndex == toIndex)
				return;
			try (Transaction t = theSubLock.lockForWrite()) {
				if (toIndex > theEnd - theStart)
					throw new IndexOutOfBoundsException(toIndex + " of " + (theEnd - theStart));
				CircularArrayList.this.removeRange(theStart + fromIndex, theStart + toIndex);
				theEnd -= toIndex - fromIndex;
				theSubLock.indexChanged(fromIndex - toIndex);
			}
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			if (c.isEmpty())
				return false;
			return removeIf(o -> c.contains(o));
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			if (c.isEmpty()) {
				boolean modified = theSize > 0;
				clear();
				return modified;
			}
			return removeIf(o -> !c.contains(o));
		}

		@Override
		public boolean removeIf(Predicate<? super E> filter) {
			if (theSize == 0)
				return false;
			int removed = 0;
			try (Transaction t = theSubLock.lockForWrite()) {
				int cap = theArray.length;
				int inspect = translateToInternalIndex(theStart, false);
				int copyTo = inspect;
				int dest = (theOffset + theEnd) % cap;
				while (inspect != dest) {
					E value = (E) theArray[inspect];
					if (filter.test(value)) {
						removed++;
					} else {
						if (removed > 0)
							theArray[copyTo] = value;
						copyTo++;
						if (copyTo == cap)
							copyTo = 0;
					}
					inspect++;
					if (inspect == cap)
						inspect = 0;
				}
				clearEntries(copyTo, removed);
				theSize -= removed;
				theEnd -= removed;
				theSubLock.indexChanged(-removed);
			}
			return removed > 0;
		}

		@Override
		public void clear() {
			try (Transaction t = theSubLock.lockForWrite()) {
				int size = theEnd - theStart;
				CircularArrayList.this.removeRange(theStart, theEnd);
				theEnd = theStart;
				theSubLock.indexChanged(-size);
			}
		}

		@Override
		public E set(int index, E element) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			try (Transaction t = theSubLock.lockForWrite()) {
				if (index >= theEnd - theStart)
					throw new IndexOutOfBoundsException(index + " of " + (theEnd - theStart));
				int ti = translateToInternalIndex(theStart + index, false);
				E old = (E) theArray[ti];
				theArray[ti] = element;
				return old;
			}
		}

		@Override
		public ListIterator<E> listIterator() {
			return listIterator(0);
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			int start = theStart;
			int end = theEnd;
			theSubLock.check();
			if (index > end - start)
				throw new IndexOutOfBoundsException(index + " of " + (end - start));
			return new ListIter(new ArraySpliterator(start, end, start + index, true, theSubLock.subLock(added -> theEnd += added)));
		}

		@Override
		public List<E> subList(int fromIndex, int toIndex) {
			if (fromIndex < 0)
				throw new IndexOutOfBoundsException("" + fromIndex);
			if (fromIndex == toIndex)
				return Collections.emptyList();
			if (fromIndex > toIndex)
				throw new IndexOutOfBoundsException(fromIndex + ">" + toIndex);
			int start = theStart;
			int end = theEnd;
			theSubLock.check();
			if (toIndex > end - start)
				throw new IndexOutOfBoundsException(toIndex + " of " + (end - start));
			int from = start + fromIndex;
			int to = start + toIndex;
			return new SubList(from, to, theSubLock.subLock(added -> theEnd += added));
		}
	}

	static class ThreadState {
		long stamp;
		boolean isWrite;

		void set(long stamp, boolean write) {
			this.stamp = stamp;
			isWrite = write;
		}
	}
}
