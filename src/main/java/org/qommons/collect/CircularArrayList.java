package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.junit.Assert;
import org.qommons.Transaction;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

/**
 * A list/deque that uses an array that is indexed circularly. This allows performance improvements due to not having to move array contents
 * when items are removed from the beginning of the list.
 * 
 * This class also supports a {@link #setMaxCapacity(int) max capacity} option which will drop elements to maintain a maximum size.
 * 
 * {@link ToDo} TODO
 * <ul>
 * <li>What happens with add(index, e) or addAll(index, c) when capacity is reached?</li>
 * <li>Make reverse() thread-safe</li>
 * <li>Add optional capacity reduction (and trimToCapacity())</li>
 * </ul>
 * 
 * @param <E> The type of elements in the list
 */
public class CircularArrayList<E> implements ReversibleList<E>, TransactableList<E>, Deque<E> {
	/** Builds a {@link CircularArrayList} */
	public static class Builder implements Cloneable {
		private int theInitCapacity;
		private int theMaxCapacity;
		private boolean isThreadSafe;

		private Builder() {
			theInitCapacity = 0;
			theMaxCapacity = MAX_ARRAY_SIZE;
			isThreadSafe = true;
		}

		/**
		 * @param initCap The initial capacity for the list
		 * @return This builder
		 */
		public Builder withInitCapacity(int initCap) {
			if (initCap < 0)
				throw new IllegalArgumentException(initCap + "<0");
			theInitCapacity = initCap;
			return this;
		}

		/**
		 * @param maxCap The maximum capacity for the list
		 * @return This builder
		 * @see CircularArrayList#setMaxCapacity(int)
		 */
		public Builder withMaxCapacity(int maxCap) {
			if (maxCap <= 0)
				throw new IllegalArgumentException("Max capacity must be at least 1");
			theMaxCapacity = maxCap;
			return this;
		}

		/**
		 * Makes the list thread-unsafe, but slightly more performant.
		 * 
		 * 30Apr2017: Unsafe is ~10% faster
		 * 
		 * @return This builder
		 */
		public Builder unsafe() {
			isThreadSafe = false;
			return this;
		}

		/**
		 * Makes the list thread-safe at a slight performance cost
		 * 
		 * @return This builder
		 */
		public Builder safe() {
			isThreadSafe = true;
			return this;
		}

		/** @return A copy of this builder */
		public Builder copy() {
			Builder builder;
			try {
				builder = (Builder) super.clone();
			} catch (CloneNotSupportedException e) {
				throw new IllegalStateException(e);
			}
			return builder;
		}

		/** @return The initial capacity for the list */
		public int getInitCapacity() {
			return theInitCapacity;
		}

		/**
		 * @return The maximum capacity for the list
		 * @see CircularArrayList#setMaxCapacity(int)
		 */
		public int getMaxCapacity() {
			return theMaxCapacity;
		}

		/** @return Whether the collection will be thread-safe */
		public boolean isThreadSafe() {
			return isThreadSafe;
		}

		/** @return The locking strategy for the list */
		public CollectionLockingStrategy makeLockingStrategy() {
			return isThreadSafe ? new StampedLockingStrategy() : new FastFailLockingStrategy();
		}

		/**
		 * @param <E> The type of the list
		 * @return The built list
		 */
		public <E> CircularArrayList<E> build() {
			return new CircularArrayList<>(this, true);
		}
	}

	/** @return A builder to build a customized {@link CircularArrayList} */
	public static Builder build() {
		return new Builder();
	}

	private static final Object[] EMPTY_ARRAY = new Object[0];
	/**
	 * The maximum size of array to allocate. Some VMs reserve some header words in an array. Attempts to allocate larger arrays may result
	 * in OutOfMemoryError: Requested array size exceeds VM limit
	 */
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	private Object[] theArray;
	private final CollectionLockingStrategy theLocker;
	private final boolean wasBuilt;
	private int theOffset;
	private int theSize;
	private int theMaxCapacity;

	private int theAdvanced;
	private int theAdded;

	/** Creates an empty list */
	public CircularArrayList() {
		this(build(), false);
	}

	/** @param initCap The initial capacity for the list */
	private CircularArrayList(Builder builder, boolean built) {
		theArray = builder.getInitCapacity() == 0 ? EMPTY_ARRAY : new Object[builder.getInitCapacity()];
		theMaxCapacity = builder.getMaxCapacity();
		theLocker = builder.makeLockingStrategy();
		wasBuilt = built;
	}

	/**
	 * For unit tests. Ensures the integrity of the list. In particular, ensures only elements contained in the list are referenced by the
	 * list
	 */
	void check() {
		Assert.assertTrue(theSize <= theArray.length);
		Assert.assertTrue(theOffset < theArray.length);
		Assert.assertTrue(theArray.length <= theMaxCapacity);
		
		int t = theOffset + theSize;
		if (t >= theArray.length)
			t -= theArray.length;
		while (t != theOffset) {
			if (theArray[t] != null)
				Assert.assertNull(theArray[t]);
			t++;
			if (t == theArray.length)
				t = 0;
		}
	}

	/**
	 * Sets this list's maximum capacity. When items are added to a list whose size equals its maximum capacity, items at the beginning (low
	 * index) of the list will be removed from the list to maintain the list's size at the maximum capacity.
	 * 
	 * @param maxCap The maximum capacity for this list
	 * @return This list
	 */
	public CircularArrayList<E> setMaxCapacity(int maxCap) {
		if (wasBuilt)
			throw new IllegalStateException("This list was built with build() and cannot have its feature set changed directly");
		if (maxCap <= 0)
			throw new IllegalArgumentException("Illegal max capacity: " + maxCap);
		try (Transaction t = lock(true, null)) {
			if (maxCap < theSize)
				removeRange(0, theSize - maxCap);
			if (theArray.length > maxCap) {
				Object[] newArray = new Object[maxCap];
				internalArrayCopy(newArray);
				theArray = newArray;
				theOffset = 0;
			}
			theMaxCapacity = maxCap;
		}
		return this;
	}

	/**
	 * @return This list's maximum capacity
	 * @see #setMaxCapacity(int)
	 */
	public int getMaxCapacity() {
		return theMaxCapacity;
	}

	@Override
	public boolean isLockSupported() {
		return true;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theLocker.lock(write, cause);
	}

	/**
	 * @return A stamp that can be checked with {@link #check(long)} to determine if this list has been changed since the stamp was
	 *         obtained. The stamp returned will be zero (and {@link #check(long)} will return false) if this list's
	 *         {@link #lock(boolean, Object) write lock} is currently held.
	 */
	public long getStamp() {
		return theLocker.getStamp();
	}

	/**
	 * @param stamp The stamp returned by {@link #getStamp()} to check
	 * @return True if this collection has not been modified since the stamp was obtained. False if this list's
	 *         {@link #lock(boolean, Object) write lock} has been obtained since (or was currently held when) the stamp was obtained. A
	 *         value of false does not necessarily imply that the collection was actually changed, but a value of true guarantees that it
	 *         has not been.
	 */
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
		return ReversibleList.super.iterator();
	}

	@Override
	public Betterator<E> descendingIterator() {
		return ReversibleList.super.descendingIterator();
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
	public boolean containsAny(Collection<?> c) {
		return theLocker.doOptimistically(false, (bool, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			for (Object o : c) {
				int t = offset;
				for (int i = 0; i < size; i++) {
					Object v = array[t];
					if (!theLocker.check(stamp))
						return false;
					if (Objects.equals(v, o)) {
						return true;
					}
					t++;
					if (t == array.length)
						t = 0;
				}
			}
			return false;
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
			return arr;
		});
	}

	@Override
	public E get(int index) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
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
			if (size == 0 || !theLocker.check(stamp))
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
			if (size == 0 || !theLocker.check(stamp))
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
			if (!theLocker.check(stamp))
				return -1;
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
			if (size == 0 || !theLocker.check(stamp))
				return -1;
			int index = size - 1;
			int t = translateToInternalIndex(array, offset, size, index);
			for (; index >= 0; index--) {
				Object v = array[t];
				if (!theLocker.check(stamp))
					return -1;
				if (Objects.equals(v, o))
					return index;
				t--;
				if (t < 0)
					t = array.length - 1;
			}
			return -1;
		});
	}

	@Override
	public E set(int index, E element) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		try (Transaction t = lock(true, null)) {
			int translated = translateToInternalIndex(index);
			E old = (E) theArray[translated];
			theArray[translated] = element;
			return old;
		}
	}

	@Override
	public boolean add(E e) {
		try (Transaction t = lock(true, null)) {
			if (theSize == theMaxCapacity) {
				ensureCapacity(theMaxCapacity);
				theArray[theOffset] = e;
				theOffset++;
				if (theOffset == theArray.length)
					theOffset = 0;
				theAdvanced = 1;
			} else {
				int oldSize = theSize;
				int newSize = theSize + 1;
				ensureCapacity(newSize);
				theSize = newSize;
				theArray[translateToInternalIndex(oldSize)] = e;
			}
			theAdded = 1;
			theLocker.indexChanged(1);
		}
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		if (c.isEmpty())
			return false;
		try (Transaction t = lock(true, null)) {
			// Very little we can do cheaply here to protect against c changing during the operation, but if it did it could do some damage
			int cSize = c.size();
			if (cSize + theSize > theMaxCapacity) {
				ensureCapacity(theMaxCapacity);
				if (cSize >= theMaxCapacity) {
					theOffset = 0;
					theAdvanced = theSize;
					theAdded = theMaxCapacity;
					theSize = theMaxCapacity;
					Iterator<? extends E> iter = c.iterator();
					for (int i = cSize; i > theMaxCapacity; i++)
						iter.next(); // Bleed off items that would be dropped due to capacity
					for (int i = 0; i < theMaxCapacity; i++)
						theArray[i] = iter.next();
				} else {
					theAdvanced = cSize + theSize - theMaxCapacity;
					theAdded = cSize;
					int start = translateToInternalIndex(theSize % theArray.length);
					theOffset += theAdvanced;
					if (theOffset >= theArray.length)
						theOffset -= theArray.length;
					theSize = theMaxCapacity;
					for (E e : c)
						theArray[start++] = e;
				}
			} else {
				ensureCapacity(theSize + cSize);
				int preSize = theSize;
				theSize += cSize;
				int idx = translateToInternalIndex(preSize);
				for (E e : c) {
					theArray[idx] = e;
					idx++;
					if (idx == theArray.length)
						idx = 0;
				}
			}
			theLocker.indexChanged(0); // This value should not matter for the root locker
		}
		return true;
	}

	@Override
	public void add(int index, E element) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		try (Transaction t = lock(true, null)) {
			if (index > theSize)
				throw new IndexOutOfBoundsException(index + " of " + theSize);
			internalAdd(index, element);
		}
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		if (c.isEmpty())
			return false;
		try (Transaction t = lock(true, null)) {
			int cSize = c.size();
			int spaces = makeRoom(index, cSize);
			int ti = translateToInternalIndex(index);
			Iterator<? extends E> iter = c.iterator();
			int i;
			for (i = 0; i < cSize - spaces; i++)
				iter.next(); // Bleed off items that would be dropped due to capacity
			for (; i < cSize; i++) {
				theArray[ti] = iter.next();
				ti++;
				if (ti == theArray.length)
					ti = 0;
			}
			theOffset += theAdvanced;
			if (theOffset >= theArray.length)
				theOffset -= theArray.length;
			theAdded = spaces;
			theLocker.indexChanged(0); // This value should not matter for the root locker
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
	public boolean removeLast(Object o) {
		try (Transaction t = lock(true, null)) {
			ReversibleSpliterator<E> iter = spliterator(false);
			boolean[] found = new boolean[1];
			while (!found[0] && iter.tryReverseElement(el -> {
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
		try (Transaction t = lock(true, null)) {
			if (toIndex > theSize)
				throw new IndexOutOfBoundsException(toIndex + " of " + theSize);
			if (fromIndex < theSize - toIndex) {
				moveContents(theOffset, fromIndex, count);
				clearEntries(theOffset, count);
				theOffset = (theOffset + count) % theArray.length;
			} else {
				moveContents(theOffset + toIndex, theSize - toIndex, -count);
				clearEntries(translateToInternalIndex(theSize - count), count);
			}
			theSize -= count;
			theLocker.indexChanged(-count);
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
			if (theSize == 0)
				return false;
			int cap = theArray.length;
			int inspect = theOffset;
			int copyTo = inspect;
			int dest = (theOffset + theSize + cap) % cap;
			do {
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
			} while (inspect != dest);
			clearEntries(copyTo, removed);
			theSize -= removed;
			if (removed > 0)
				theLocker.indexChanged(-removed);
		}
		return removed > 0;
	}

	@Override
	public void clear() {
		try (Transaction t = lock(true, null)) {
			Arrays.fill(theArray, theOffset, Math.min(theOffset + theSize, theArray.length), null);
			if (theOffset + theSize > theArray.length)
				Arrays.fill(theArray, 0, theOffset + theSize - theArray.length, null);
			int preSize = theSize;
			theSize = 0;
			theOffset = 0;
			theLocker.indexChanged(-preSize);
		}
	}

	@Override
	public E remove(int index) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		try (Transaction t = lock(true, null)) {
			return internalRemove(index, translateToInternalIndex(index));
		}
	}

	@Override
	public E removeFirst() {
		return remove(0);
	}

	@Override
	public E removeLast() {
		try (Transaction t = lock(true, null)) {
			if (theSize == 0)
				throw new NoSuchElementException();
			return internalRemove(theSize - 1, translateToInternalIndex(theSize - 1));
		}
	}

	@Override
	public E pollFirst() {
		try (Transaction t = lock(true, null)) {
			if (theSize == 0)
				return null;
			return internalRemove(0, translateToInternalIndex(0));
		}
	}

	@Override
	public E pollLast() {
		try (Transaction t = lock(true, null)) {
			if (theSize == 0)
				return null;
			return internalRemove(theSize - 1, translateToInternalIndex(theSize - 1));
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
			return internalRemove(0, translateToInternalIndex(0));
		}
	}

	@Override
	public E pop() {
		return removeFirst();
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		return new ListIter(new ArraySpliterator(0, -1, index, theLocker.subLock()));
	}

	@Override
	public SubList subList(int fromIndex, int toIndex) {
		return new SubList(fromIndex, toIndex, theLocker.subLock());
	}

	@Override
	public int hashCode() {
		return theLocker.doOptimistically(0, (h, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!theLocker.check(stamp))
				return -1;
			int hash = 0;
			int t = offset;
			for (int i = 0; i < size; i++) {
				E v = (E) array[t];
				if (!theLocker.check(stamp))
					return -1;
				hash += v == null ? 0 : v.hashCode();
				t++;
				if (t == array.length)
					t = 0;
			}
			return hash;
		});
	}

	@Override
	public boolean equals(Object o){
		if(!(o instanceof Collection))
			return false;
		Collection<?> c=(Collection<?>) o;
		return theLocker.doOptimistically(false, (b, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!theLocker.check(stamp))
				return false;
			if (c.size() != size)
				return false;
			int t = offset;
			Iterator<?> iter = c.iterator();
			for (int i = 0; i < size; i++) {
				if (!iter.hasNext())
					return false;
				Object co = iter.next();
				E v = (E) array[t];
				if (!theLocker.check(stamp))
					return false;
				if (!Objects.equals(v, co))
					return false;
				t++;
				if (t == array.length)
					t = 0;
			}
			return true;
		});
	}

	@Override
	public String toString() {
		return theLocker.doOptimistically(new StringBuilder(), (str, stamp) -> {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!theLocker.check(stamp))
				return str;
			str.setLength(0);
			str.append('[');
			int t = offset;
			for (int i = 0; i < size; i++) {
				E v = (E) array[t];
				if (!theLocker.check(stamp))
					return str;
				if (i > 0)
					str.append(", ");
				str.append(v);
				t++;
				if (t == array.length)
					t = 0;
			}
			str.append(']');
			return str;
		}).toString();
	}

	/**
	 * Ensures that this list has at least the given capacity.
	 * 
	 * This method does not affect the list's {@link #getMaxCapacity() max capacity}. If size>maxCapacity, the capacity ensured will be
	 * maxCapacity.
	 * 
	 * @param capacity The minimum capacity to ensure for this list
	 * @return Whether this list's internal array was switched as a result of this call
	 */
	public boolean ensureCapacity(int capacity) {
		// overflow-conscious code
		if (capacity - theMaxCapacity > 0)
			capacity = theMaxCapacity;
		if (capacity - theArray.length > 0) {
			try (Transaction t = theLocker.lock(true, null)) {
				int oldCapacity = theArray.length;
				int newCapacity = oldCapacity == 0 ? 10 : oldCapacity + (oldCapacity >> 1);
				if (newCapacity - capacity < 0)
					newCapacity = capacity;
				if (newCapacity - MAX_ARRAY_SIZE > 0)
					throw new OutOfMemoryError("Cannot allocate an array of size " + newCapacity);
				Object[] newArray = new Object[newCapacity];
				if (theSize > 0)
					internalArrayCopy(newArray);
				theOffset = 0;
				theArray = newArray;
			}
			return true;
		} else
			return false;
	}

	@Override
	public ReversibleSpliterator<E> spliterator(boolean forward) {
		return new ArraySpliterator(0, theSize, forward ? 0 : theSize, theLocker.subLock());
	}

	private final int translateToInternalIndex(int index) {
		return translateToInternalIndex(theArray, theOffset, theSize, index);
	}

	private static final int translateToInternalIndex(Object[] array, int offset, int size, int index) {
		if (index >= size)
			throw new ArrayIndexOutOfBoundsException(index + " of " + size);
		int t = index + offset;
		if (t >= array.length)
			t -= array.length;
		return t;
	}

	private int makeRoom(int index, int spaces) {
		if (theSize + spaces > theMaxCapacity) {
			ensureCapacity(theMaxCapacity);
			if (spaces >= theMaxCapacity) {
				theAdvanced = theSize;
				theOffset = 0;
				theSize = theMaxCapacity;
				return theMaxCapacity;
			} else {
				theAdvanced = theSize + spaces - theMaxCapacity;
				if (index <= theSize / 2)
					moveContents(theOffset + theAdvanced, index, -spaces);
				else
					moveContents(theOffset + index, theSize - index, spaces);
				theSize = theMaxCapacity;
				return spaces;
			}
		} else {
			ensureCapacity(theSize + spaces);
			if (index <= theSize / 2) {
				moveContents(theOffset, index, -spaces);
				theOffset -= spaces;
				if (theOffset < 0)
					theOffset += theArray.length;
			} else
				moveContents(theOffset + index, theSize - index, spaces);
			theSize += spaces;
			return spaces;
		}
	}

	private void moveContents(int sourceStart, int count, int offset) {
		if (count == 0)
			return;
		int cap = theArray.length;
		if (sourceStart > cap)
			sourceStart -= cap;
		int sourceEnd = sourceStart + count;
		int destStart = sourceStart + offset;
		int destEnd = destStart + count;
		if (destStart < 0) {
			destStart += cap;
			destEnd += cap;
		} else if (destStart >= cap) {
			destStart -= cap;
			destEnd -= cap;
		}

		if (sourceEnd > cap) {
			// The source interval is wrapped
			int firstSectionLength = cap - sourceStart;
			if (destEnd > cap) {
				// The destination interval is also wrapped
				int destSL = cap - destStart;
				if (destSL < firstSectionLength)
					firstSectionLength = destSL;
				if (offset > 0) {
					System.arraycopy(theArray, 0, theArray, offset, count - firstSectionLength - offset);
					System.arraycopy(theArray, sourceStart + firstSectionLength, theArray, 0, offset);
					System.arraycopy(theArray, sourceStart, theArray, destStart, firstSectionLength);
				} else {
					System.arraycopy(theArray, sourceStart, theArray, destStart, firstSectionLength);
					System.arraycopy(theArray, 0, theArray, destStart + firstSectionLength, -offset);
					System.arraycopy(theArray, -offset, theArray, 0, count - firstSectionLength + offset);
				}
			} else {
				if (offset > 0) {
					System.arraycopy(theArray, 0, theArray, destStart + firstSectionLength, count - firstSectionLength);
					System.arraycopy(theArray, sourceStart, theArray, destStart, firstSectionLength);
				} else {
					System.arraycopy(theArray, sourceStart, theArray, destStart, firstSectionLength);
					System.arraycopy(theArray, 0, theArray, destStart + firstSectionLength, count - firstSectionLength);
				}
			}
		} else if (destEnd > cap) {
			// The destination interval is wrapped
			int firstSectionLength = cap - destStart;
			if (offset > 0) {
				System.arraycopy(theArray, sourceStart + firstSectionLength, theArray, 0, count - firstSectionLength);
				System.arraycopy(theArray, sourceStart, theArray, destStart, firstSectionLength);
			} else {
				System.arraycopy(theArray, sourceStart, theArray, destStart, firstSectionLength);
				System.arraycopy(theArray, sourceStart + firstSectionLength, theArray, 0, count - firstSectionLength);
			}
		} else {
			// Neither interval is wrapped
			System.arraycopy(theArray, sourceStart, theArray, destStart, count);
		}
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

	/**
	 * Copies this list's contents into the given array
	 * 
	 * @param a The array to copy this list's contents into
	 */
	protected final void internalArrayCopy(Object[] a) {
		internalArrayCopy(theArray, theOffset, theSize, a);
	}

	private static final void internalArrayCopy(Object[] src, int offset, int size, Object[] dest) {
		System.arraycopy(src, offset, dest, 0, Math.min(size, src.length - offset));
		if (offset + size > src.length)
			System.arraycopy(src, 0, dest, src.length - offset, size - src.length + offset);
	}

	private final void internalAdd(int index, E element) {
		makeRoom(index, 1);
		theArray[translateToInternalIndex(index)] = element;
		theOffset += theAdvanced;
		if (theOffset >= theArray.length)
			theOffset -= theArray.length;
		theAdded = 1;
		theLocker.indexChanged(1);
	}

	private final E internalRemove(int listIndex, int translatedIndex) {
		E removed = (E) theArray[translatedIndex];
		// Figure out the optimum way to move array elements
		if (theSize == 1) {
			theArray[theOffset] = null; // Remove reference
			theOffset = theSize = 0;
		} else if (listIndex < theSize / 2) {
			moveContents(theOffset, listIndex, 1);
			theArray[theOffset] = null; // Remove reference
			theOffset++;
			if (theOffset == theArray.length)
				theOffset = 0;
			theSize--;
		} else {
			moveContents(translatedIndex + 1, theSize - listIndex - 1, -1);
			theArray[translateToInternalIndex(theSize - 1)] = null; // Remove reference
			theSize--;
		}
		theLocker.indexChanged(-1);
		return removed;
	}

	private class ArraySpliterator implements ReversibleSpliterator<E> {
		private int theStart;
		private int theEnd;
		private int theCursor; // The index of the element that would be given to the consumer for tryAdvance()
		private int theCurrentIndex; // The index of the element last returned by this iterator from tryAdvance or tryReverse()
		private int theTranslatedIndex;
		private boolean elementExists;
		private final CollectionElement<E> element;
		private final CollectionLockingStrategy.SubLockingStrategy theSubLock;

		ArraySpliterator(int start, int end, int initIndex, CollectionLockingStrategy.SubLockingStrategy subLock) {
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
			theStart = start;
			theEnd = end;
			theCursor = initIndex;
			theCurrentIndex = initIndex; // Just for toString()
			theSubLock = subLock;
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
					try (Transaction t = theSubLock.lock(true, null)) {
						old = (E) theArray[theTranslatedIndex];
						theArray[theTranslatedIndex] = value;
					}
					return old;
				}

				@Override
				public E get() {
					if (!elementExists)
						throw new IllegalStateException("Element has been removed");
					return theSubLock.doOptimistically(null, (v, stamp) -> {
						Object[] array = theArray;
						return theTranslatedIndex < array.length ? (E) array[theTranslatedIndex] : null;
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
					try (Transaction t = theSubLock.lock(true, null)) {
						if (!elementExists)
							throw new IllegalArgumentException("Element is already removed");
						internalRemove(theCurrentIndex, theTranslatedIndex);
						theEnd--;
						if (theCursor > theCurrentIndex)
							theCursor--;
						elementExists = false;
						theSubLock.indexChanged(-1);
					}
				}

				@Override
				public String toString() {
					if (!elementExists)
						return "(removed)";
					else
						return String.valueOf(get());
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
			if (advance) {
				if (theCursor >= theEnd)
					return false;
				theCurrentIndex = theCursor;
				theCursor++;
			} else {
				if (theCursor == theStart)
					return false;
				theCursor--;
				theCurrentIndex = theCursor;
			}
			theTranslatedIndex = translateToInternalIndex(theCurrentIndex);
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
				split = new ArraySpliterator(mid, theEnd, mid, theSubLock.siblingLock());
				theEnd = mid;
			} else {
				split = new ArraySpliterator(theStart, mid, mid, theSubLock.siblingLock());
				theStart = mid;
			}
			return split;
		}

		int getCursor() {
			return theCursor;
		}

		int getRelativeCursor() {
			return theCursor - theStart;
		}

		void setCursor(int cursor) {
			theCursor = cursor;
		}

		@Override
		public String toString() {
			return theSubLock.doOptimistically(new StringBuilder(), (str, stamp) -> {
				Object[] array = theArray;
				int offset = theOffset;
				int size = theSize;
				int start = theStart;
				int end = theEnd;
				int cursor = theCurrentIndex;
				boolean ee = elementExists;
				if (!theSubLock.check(stamp))
					return str;
				str.setLength(0);
				str.append('<');
				int index = start;
				int t = translateToInternalIndex(array, offset, size, index);
				boolean first = true;
				for (; index < end; index++) {
					Object v = array[t];
					if (!theSubLock.check(stamp))
						return str;
					if (!first)
						str.append(", ");
					first = false;
					if (index == cursor) {
						str.append('*');
						if (!ee)
							str.append('*');
					}
					str.append(v);
					if (ee && index == cursor)
						str.append('*');
					t++;
					if (t == array.length)
						t = 0;
				}
				if (index == cursor) {
					str.append('*');
					str.append('*');
				}
				str.append('>');
				return str;
			}).toString();
		}
	}

	class ListIter extends ReversibleSpliterator.PartialListIterator<E> {
		ListIter(ArraySpliterator backing) {
			super(backing);
		}

		@Override
		public int nextIndex() {
			return ((ArraySpliterator) backing).getRelativeCursor() - getSpliteratorCursorOffset();
		}

		@Override
		public int previousIndex() {
			return nextIndex() - 1;
		}

		@Override
		public void remove() {
			super.remove();
		}

		@Override
		public void add(E e) {
			ArraySpliterator ab = (ArraySpliterator) backing;
			try (Transaction t = ab.theSubLock.lock(true, null)) {
				int cursor = ((ArraySpliterator) backing).getCursor() - getSpliteratorCursorOffset();
				CircularArrayList.this.add(cursor, e);
				if (theAdvanced > 0) {
					if (ab.theStart > 0)
						ab.theStart--;
					else if (ab.theEnd < theSize)
						ab.theEnd++;
				} else
					ab.theEnd++;
				ab.theSubLock.indexChanged(1);
				((ArraySpliterator) backing).setCursor(cursor);
				clearCache();
				next();
			}
		}
	}

	/**
	 * The type of list returned from {@link CircularArrayList#subList(int, int)}. This list's {@link #subList(int, int)} also returns a
	 * list of this type.
	 */
	public class SubList implements ReversibleList<E>, RRList<E> {
		private int theStart;
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
		public Betterator<E> iterator() {
			return ReversibleList.super.iterator();
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
		public boolean containsAny(Collection<?> c) {
			return theLocker.doOptimistically(false, (bool, stamp) -> {
				Object[] array = theArray;
				int offset = theOffset;
				int size = theSize;
				int start = theStart;
				int end = theEnd;
				for (Object o : c) {
					int index = start;
					int t = translateToInternalIndex(array, offset, size, index);
					for (; index < end; index++) {
						Object v = array[t];
						if (!theSubLock.check(stamp))
							return false;
						if (Objects.equals(v, o)) {
							return true;
						}
						t++;
						if (t == array.length)
							t = 0;
					}
				}
				return false;
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
				return arr;
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
				int end = theEnd;
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
				int end = theEnd;
				if (start == end || !theSubLock.check(stamp))
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
		public ReversibleSpliterator<E> spliterator(boolean forward) {
			// Can only remove, so no need to account for capacity dropping
			int init = forward ? theStart : theEnd;
			return new ArraySpliterator(theStart, theEnd, init, theSubLock.subLock(added -> theEnd += added));
		}

		@Override
		public boolean add(E e) {
			try (Transaction t = theSubLock.lock(true, null)) {
				CircularArrayList.this.add(theEnd, e);
				if (theAdvanced > 0) {
					if (theStart > 0)
						theStart--;
					else if (theEnd < theSize)
						theEnd++;
				} else
					theEnd++;
				theSubLock.indexChanged(1);
			}
			return true;
		}

		@Override
		public void add(int index, E element) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			try (Transaction t = theSubLock.lock(true, null)) {
				if (index > theEnd - theStart)
					throw new IndexOutOfBoundsException(index + " of " + (theEnd - theStart));
				internalAdd(theStart + index, element);
				if (theAdvanced > 0) {
					if (theStart > 0)
						theStart--;
					else if (theEnd < theSize)
						theEnd++;
				} else
					theEnd++;
				theSubLock.indexChanged(1);
			}
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			if (c.isEmpty())
				return false;
			try (Transaction t = theSubLock.lock(true, null)) {
				if (CircularArrayList.this.addAll(theEnd, c)) {
					int moveStartBack = theAdvanced;
					int moveEndFwd = theAdded - theAdvanced;
					if (moveStartBack > theStart) {
						moveEndFwd += moveStartBack - theStart;
						theStart = 0;
					} else
						theStart -= moveStartBack;
					if (theEnd + moveEndFwd > theSize)
						theEnd = theSize;
					else
						theEnd += moveEndFwd;
					theSubLock.indexChanged(theAdded - theAdvanced);
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
			try (Transaction t = theSubLock.lock(true, null)) {
				if (index > theEnd - theStart)
					throw new IndexOutOfBoundsException(index + " of " + (theEnd - theStart));
				int oldSize = theSize;
				CircularArrayList.this.addAll(theStart + index, c);
				if (theSize != oldSize) {
					adjustIndicesOnAdd(theAdvanced, theAdded);
					theSubLock.indexChanged(theAdded - theAdvanced);
					return true;
				} else
					return false;
			}
		}

		private void adjustIndicesOnAdd(int advanced, int added) {
			int moveStartBack = advanced;
			int moveEndFwd = added - advanced;
			if (moveStartBack > theStart) {
				moveEndFwd += moveStartBack - theStart;
				theStart = 0;
			} else
				theStart -= moveStartBack;
			if (theEnd + moveEndFwd > theSize)
				theEnd = theSize;
			else
				theEnd += moveEndFwd;
		}

		@Override
		public boolean remove(Object o) {
			try (Transaction t = theSubLock.lock(true, null)) {
				int index = theStart;
				int ti = translateToInternalIndex(index);
				for (; index < theEnd; index++) {
					if (Objects.equals(theArray[ti], o)) {
						internalRemove(index, ti);
						theEnd--;
						theSubLock.indexChanged(-1);
						return true;
					}
					ti++;
					if (ti == theArray.length)
						ti = 0;
				}
			}
			return false;
		}

		@Override
		public boolean removeLast(Object o) {
			try (Transaction t = theSubLock.lock(true, null)) {
				if (theStart == theEnd)
					return false;
				int index = theEnd - 1;
				int ti = translateToInternalIndex(index);
				for (; index >= theStart; index--) {
					if (Objects.equals(theArray[ti], o)) {
						internalRemove(index, ti);
						theEnd--;
						theSubLock.indexChanged(-1);
						return true;
					}
					ti--;
					if (ti < 0)
						ti = theArray.length - 1;
				}
			}
			return false;
		}

		@Override
		public E remove(int index) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			try (Transaction t = theSubLock.lock(true, null)) {
				if (index >= theEnd - theStart)
					throw new IndexOutOfBoundsException(index + " of " + (theEnd - theStart));
				E old = internalRemove(theStart + index, translateToInternalIndex(theStart + index));
				theEnd--;
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
			try (Transaction t = theSubLock.lock(true, null)) {
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
			try (Transaction t = theSubLock.lock(true, null)) {
				int cap = theArray.length;
				int inspect = translateToInternalIndex(theStart);
				int copyTo = inspect;
				int dest = translateToInternalIndex(theEnd);
				do {
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
				} while (inspect != dest);
				moveContents(dest, theSize - theEnd, -removed);
				copyTo += theSize - theEnd;
				if (copyTo > cap)
					copyTo -= cap;
				clearEntries(copyTo, removed);
				theSize -= removed;
				theEnd -= removed;
				theSubLock.indexChanged(-removed);
				theLocker.indexChanged(-removed);
			}
			return removed > 0;
		}

		@Override
		public void clear() {
			try (Transaction t = theSubLock.lock(true, null)) {
				int size = theEnd - theStart;
				if (size == 0)
					return;
				CircularArrayList.this.removeRange(theStart, theEnd);
				theEnd = theStart;
				theSubLock.indexChanged(-size);
			}
		}

		@Override
		public E set(int index, E element) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			try (Transaction t = theSubLock.lock(true, null)) {
				if (index >= theEnd - theStart)
					throw new IndexOutOfBoundsException(index + " of " + (theEnd - theStart));
				int ti = translateToInternalIndex(theStart + index);
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
			return new ListIter(new ArraySpliterator(start, end, start + index, theSubLock.subLock(added -> {
				if (added > 0)
					adjustIndicesOnAdd(theAdvanced, added);
				else
					theEnd += added;
			})));
		}

		@Override
		public SubList subList(int fromIndex, int toIndex) {
			if (fromIndex < 0)
				throw new IndexOutOfBoundsException("" + fromIndex);
			if (fromIndex > toIndex)
				throw new IndexOutOfBoundsException(fromIndex + ">" + toIndex);
			int start = theStart;
			int end = theEnd;
			theSubLock.check();
			if (toIndex > end - start)
				throw new IndexOutOfBoundsException(toIndex + " of " + (end - start));
			int from = start + fromIndex;
			int to = start + toIndex;
			return new SubList(from, to, theSubLock.subLock(added -> {
				if (added > 0)
					adjustIndicesOnAdd(theAdvanced, added);
				else
					theEnd += added;
			}));
		}

		@Override
		public int hashCode() {
			return theSubLock.doOptimistically(0, (h, stamp) -> {
				Object[] array = theArray;
				int offset = theOffset;
				int size = theSize;
				int start = theStart;
				int end = theEnd;
				if (!theSubLock.check(stamp))
					return 0;
				int hash = 0;
				int index = start;
				int t = translateToInternalIndex(array, offset, size, index);
				for (; index < end; index++) {
					Object v = array[t];
					if (!theSubLock.check(stamp))
						return 0;
					hash += v == null ? 0 : v.hashCode();
					t++;
					if (t == array.length)
						t = 0;
				}
				return hash;
			});
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Collection))
				return false;
			Collection<?> c = (Collection<?>) o;
			return theSubLock.doOptimistically(false, (b, stamp) -> {
				Object[] array = theArray;
				int offset = theOffset;
				int size = theSize;
				int start = theStart;
				int end = theEnd;
				if (!theSubLock.check(stamp))
					return false;
				if (c.size() != end - start)
					return false;
				int index = start;
				int t = translateToInternalIndex(array, offset, size, index);
				Iterator<?> iter = c.iterator();
				for (; index < end; index++) {
					Object v = array[t];
					if (!iter.hasNext())
						return false;
					Object co = iter.next();
					if (!theSubLock.check(stamp))
						return false;
					if (!Objects.equals(v, co))
						return false;
					t++;
					if (t == array.length)
						t = 0;
				}
				return true;
			});
		}

		@Override
		public String toString() {
			return theSubLock.doOptimistically(new StringBuilder(), (str, stamp) -> {
				Object[] array = theArray;
				int offset = theOffset;
				int size = theSize;
				int start = theStart;
				int end = theEnd;
				if (!theSubLock.check(stamp))
					return str;
				str.setLength(0);
				str.append('[');
				int index = start;
				int t = translateToInternalIndex(array, offset, size, index);
				for (; index < end; index++) {
					Object v = array[t];
					if (!theSubLock.check(stamp))
						return str;
					if (index > start)
						str.append(", ");
					str.append(v);
					t++;
					if (t == array.length)
						t = 0;
				}
				str.append(']');
				return str;
			}).toString();
		}
	}
}
