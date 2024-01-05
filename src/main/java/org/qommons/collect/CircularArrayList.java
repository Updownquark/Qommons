package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;

import org.qommons.Ternian;

/**
 * A list/deque that uses an array that is indexed circularly. This allows performance improvements due to not having to move array contents
 * when items are removed from the beginning of the list.
 * 
 * This class also supports:
 * <ul>
 * <li>A {@link #setMaxCapacity(int) max capacity} option which will drop elements to maintain a maximum size.</li>
 * <li>Automatic capacity management with {@link #setMinOccupancy(double)}</li>
 * </ul>
 * 
 * @param <E> The type of elements in the list
 */
public class CircularArrayList<E> implements DequeList<E> {
	/**
	 * The maximum size of array to allocate. Some VMs reserve some header words in an array. Attempts to allocate larger arrays may result
	 * in OutOfMemoryError: Requested array size exceeds VM limit
	 */
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
	/** The relative amount by which the internal array will grow, by default, when the list's size needs exceed its capacity */
	private static final double DEFAULT_GROWTH_FACTOR = 0.5;

	/** Builds a {@link CircularArrayList} */
	public static class Builder implements Cloneable {
		private int theInitCapacity;
		private int theMinCapacity;
		private int theMaxCapacity;
		private double theMinOccupancy;
		private double theGrowthFactor;
		private boolean isFeatureLocked;

		private Builder() {
			theInitCapacity = 0;
			theMaxCapacity = MAX_ARRAY_SIZE;
			theGrowthFactor = DEFAULT_GROWTH_FACTOR;
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
		 * @param minCap The minimum capacity for the list
		 * @return This builder
		 */
		public Builder withMinCapacity(int minCap) {
			theInitCapacity = capConstraintsAdjusted(theInitCapacity, minCap, theMaxCapacity);
			theMinCapacity = minCap;
			return this;
		}

		/**
		 * @param maxCap The maximum capacity for the list
		 * @return This builder
		 * @see CircularArrayList#setMaxCapacity(int)
		 */
		public Builder withMaxCapacity(int maxCap) {
			theInitCapacity = capConstraintsAdjusted(theInitCapacity, theMinCapacity, maxCap);
			theMaxCapacity = maxCap;
			return this;
		}

		/**
		 * @param minOccupancy The minimum amount of the list's capacity that must be occupied. When occupancy drops below this threshold,
		 *        the array will be trimmed to the maximum of its size and minimum capacity.
		 * @return This builder
		 */
		public Builder withMinOccupancy(double minOccupancy) {
			if (Double.isNaN(minOccupancy) || Double.isInfinite(minOccupancy))
				throw new IllegalArgumentException("Min occupancy must a finite number");
			if (minOccupancy < 0 || minOccupancy > 1)
				throw new IllegalArgumentException("Min occupancy must be between 0 and 1");
			theMinOccupancy = minOccupancy;
			return this;
		}

		/**
		 * @param factor The growth factor for the array, i.e. the relative amount by which the internal array will grow, at minimum, when
		 *        the list's size needs exceed its capacity. A value of zero means the array will only grow to accommodate all values. A
		 *        value of 1 means the array will at least double in size when new space is needed.
		 * @return This builder
		 */
		public Builder withGrowthFactor(double factor) {
			if (factor < 0)
				throw new IllegalArgumentException("Growth factor must be at least zero");
			if (Double.isNaN(factor) || Double.isInfinite(factor) || factor >= 100)
				throw new IllegalArgumentException("Growth factor must be a finite number less than 100");
			theGrowthFactor = factor;
			return this;
		}

		/**
		 * Locks the list's feature set so that its capacity adjustment capabilities cannot be directly modified on the list
		 * 
		 * @return This builder
		 */
		public Builder lockFeatures() {
			isFeatureLocked = true;
			return this;
		}

		/**
		 * Unlocks the list's feature set so that its capacity adjustment capabilities can be directly modified on the list
		 * 
		 * @return This builder
		 */
		public Builder unlockFeatures() {
			isFeatureLocked = false;
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
		 * @return The minimum capacity for the list
		 * @see CircularArrayList#setMinCapacity(int)
		 */
		public int getMinCapacity() {
			return theMinCapacity;
		}

		/**
		 * @return The maximum capacity for the list
		 * @see CircularArrayList#setMaxCapacity(int)
		 */
		public int getMaxCapacity() {
			return theMaxCapacity;
		}

		/**
		 * @return The minimum occupancy for the list
		 * @see CircularArrayList#setMinOccupancy(double)
		 */
		public double getMinOccupancy() {
			return theMinOccupancy;
		}

		/**
		 * @return The relative amount by which the internal array will grow when the list's size needs exceed its capacity
		 * @see CircularArrayList#setGrowthFactor(double)
		 */
		public double getGrowthFactor() {
			return theGrowthFactor;
		}

		/**
		 * @param <E> The type of the list
		 * @return The built list
		 */
		public <E> CircularArrayList<E> build() {
			return new CircularArrayList<>(this, isFeatureLocked);
		}
	}

	/** @return A builder to build a customized {@link CircularArrayList} */
	public static Builder build() {
		return new Builder();
	}

	private static int capConstraintsAdjusted(int cap, int minCap, int maxCap) {
		if (maxCap <= 0)
			throw new IllegalArgumentException("Max capacity must be at least 1");
		if (maxCap > MAX_ARRAY_SIZE)
			throw new IllegalArgumentException("Maximum allowed capacity is " + MAX_ARRAY_SIZE);
		if (minCap > maxCap)
			throw new IllegalArgumentException("Min capacity " + minCap + " cannot be greater than max capacity " + maxCap);
		if (cap < minCap)
			return minCap;
		else if (cap > maxCap)
			return maxCap;
		return cap;
	}

	/** Static value to avoid needing to instantiate multiple zero-length arrays */
	private static final Object[] EMPTY_ARRAY = new Object[0];

	private Object[] theArray;
	private final boolean isFeatureLocked;
	private int theOffset;
	private int theSize;
	private int theMinCapacity;
	private int theMaxCapacity;
	private double theMinOccupancy;
	private double theGrowthFactor;
	private long theStamp;

	private int theAdvanced;

	/** Creates an empty list */
	public CircularArrayList() {
		this(build(), false);
	}

	private CircularArrayList(Builder builder, boolean featureLocked) {
		theArray = builder.getInitCapacity() == 0 ? EMPTY_ARRAY : new Object[builder.getInitCapacity()];
		theMinCapacity = builder.getMinCapacity();
		theMaxCapacity = builder.getMaxCapacity();
		theMinOccupancy = builder.getMinOccupancy();
		theGrowthFactor = builder.getGrowthFactor();
		isFeatureLocked = featureLocked;
	}

	/** For unit tests. Ensures the integrity of the list. */
	public void checkValid() {
		if (theSize > theArray.length)
			throw new IllegalStateException("Can't have more elements than the capacity");
		if (theOffset >= theArray.length)
			throw new IllegalStateException("The offset must be a valid index in the array");
		if (theArray.length < theMinCapacity || theArray.length > theMaxCapacity)
			throw new IllegalStateException("The array must be within the list's capacity settings: " + theMinCapacity + "<="
				+ theArray.length + "<=" + theMaxCapacity);
		if (theMinOccupancy > 0) {
			// Ensure the minimum occupancy requirement is met
			int occSize = Math.max((int) Math.ceil(theSize / theMinOccupancy), theMinCapacity);
			if (theArray.length > occSize)
				throw new IllegalStateException("Min occupancy is not met: " + occSize + "/" + theArray.length);
		}

		// Ensure only elements contained in the list are referenced by the list
		int t = theOffset + theSize;
		if (t >= theArray.length)
			t -= theArray.length;
		while (t != theOffset) {
			if (theArray[t] != null)
				throw new IllegalStateException("Array[" + t + "] is not null");
			t++;
			if (t == theArray.length)
				t = 0;
		}
	}

	/**
	 * Sets this list's minimum capacity, which will always be kept if this list's capacity is reduced, either manually via
	 * {@link #trimToSize()} or automatically via #setMinOccupancy(double).
	 * 
	 * @param minCap The minimum capacity for this list
	 * @return This list
	 */
	public CircularArrayList<E> setMinCapacity(int minCap) {
		if (isFeatureLocked)
			throw new IllegalStateException("This list's feature set is locked--its feature set cannot be changed directly");
		int cap = capConstraintsAdjusted(theArray.length, minCap, theMaxCapacity);
		if (theArray.length < cap) {
			Object[] newArray = new Object[cap];
			internalArrayCopy(newArray);
			theArray = newArray;
			theOffset = 0;
		}
		return this;
	}

	/**
	 * Sets this list's maximum capacity. When items are added to a list whose size equals its maximum capacity, items at the beginning (low
	 * index) of the list will be removed from the list to maintain the list's size at the maximum capacity.
	 * 
	 * @param maxCap The maximum capacity for this list
	 * @return This list
	 */
	public CircularArrayList<E> setMaxCapacity(int maxCap) {
		if (isFeatureLocked)
			throw new IllegalStateException("This list's feature set is locked--its feature set cannot be changed directly");
		int cap = capConstraintsAdjusted(theArray.length, theMinCapacity, maxCap);
		theMaxCapacity = maxCap;
		if (cap < theSize)
			removeRange(0, theSize - maxCap);
		if (theArray.length > cap) {
			Object[] newArray = new Object[cap];
			internalArrayCopy(newArray);
			theArray = newArray;
			theOffset = 0;
		}
		return this;
	}

	/**
	 * @param minOccupancy The minimum amount of the list's capacity that must be occupied. When occupancy drops below this threshold, the
	 *        array will be trimmed to the maximum of its size and minimum capacity.
	 * @return This list
	 */
	public CircularArrayList<E> setMinOccupancy(double minOccupancy) {
		if (Double.isNaN(minOccupancy) || Double.isInfinite(minOccupancy))
			throw new IllegalArgumentException("Min occupancy must a finite number");
		if (minOccupancy < 0 || minOccupancy > 1)
			throw new IllegalArgumentException("Min occupancy must be between 0 and 1");
		theMinOccupancy = minOccupancy;
		return this;
	}

	/**
	 * @param factor The growth factor for the array, i.e. the relative amount by which the internal array will grow, at minimum, when the
	 *        list's size needs exceed its capacity. A value of zero means the array will only grow to accommodate all values. A value of 1
	 *        means the array will at least double in size when new space is needed.
	 * @return This list
	 */
	public CircularArrayList<E> setGrowthFactor(double factor) {
		if (factor < 0)
			throw new IllegalArgumentException("Growth factor must be at least zero");
		if (Double.isNaN(factor) || Double.isInfinite(factor) || factor >= 100)
			throw new IllegalArgumentException("Growth factor must be a finite number less than 100");
		theGrowthFactor = factor;
		return this;
	}

	/**
	 * @return This list's minimum capacity
	 * @see #setMinCapacity(int)
	 */
	protected int getMinCapacity() {
		return theMinCapacity;
	}

	/**
	 * @return This list's maximum capacity
	 * @see #setMaxCapacity(int)
	 */
	public int getMaxCapacity() {
		return theMaxCapacity;
	}

	/**
	 * @return This list's minimum occupancy
	 * @see #setMinOccupancy(double)
	 */
	protected double getMinOccupancy() {
		return theMinOccupancy;
	}

	/**
	 * @return This list's growth factor
	 * @see #setGrowthFactor(double)
	 */
	protected double getGrowthFactor() {
		return theGrowthFactor;
	}

	@Override
	public long getStamp() {
		return theStamp;
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
	public boolean contains(Object o) {
		Object[] array = theArray;
		int offset = theOffset;
		int size = theSize;
		int t = offset;
		for (int i = 0; i < size; i++) {
			Object v = array[t];
			if (Objects.equals(v, o))
				return true;
			t++;
			if (t == array.length)
				t = 0;
		}
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		Object[] array = theArray;
		int offset = theOffset;
		int size = theSize;
		for (Object o : c) {
			boolean found = false;
			int t = offset;
			for (int i = 0; i < size; i++) {
				Object v = array[t];
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
	}

	@Override
	public boolean containsAny(Collection<?> c) {
		Object[] array = theArray;
		int offset = theOffset;
		int size = theSize;
		for (Object o : c) {
			int t = offset;
			for (int i = 0; i < size; i++) {
				Object v = array[t];
				if (Objects.equals(v, o)) {
					return true;
				}
				t++;
				if (t == array.length)
					t = 0;
			}
		}
		return false;
	}

	@Override
	public <T> T[] toArray(final T[] a) {
		Object[] array = theArray;
		int offset = theOffset;
		int size = theSize;
		T[] arr = a;
		if (arr.length < size)
			arr = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
		internalArrayCopy(array, offset, size, arr);
		return arr;
	}

	@Override
	public E get(int index) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		Object[] array = theArray;
		int offset = theOffset;
		int size = theSize;
		return (E) array[translateToInternalIndex(array, offset, size, index)];
	}

	@Override
	public E peekFirst() {
		Object[] array = theArray;
		int offset = theOffset;
		int size = theSize;
		if (size == 0)
			return null;
		return (E) array[offset];
	}

	@Override
	public E peekLast() {
		Object[] array = theArray;
		int offset = theOffset;
		int size = theSize;
		if (size == 0)
			return null;
		return (E) array[translateToInternalIndex(array, offset, size, size - 1)];
	}

	@Override
	public int indexOf(Object o) {
		Object[] array = theArray;
		int offset = theOffset;
		int size = theSize;
		int t = offset;
		for (int i = 0; i < size; i++) {
			Object v = array[t];
			if (Objects.equals(v, o))
				return i;
			t++;
			if (t == array.length)
				t = 0;
		}
		return -1;
	}

	@Override
	public int lastIndexOf(Object o) {
		Object[] array = theArray;
		int offset = theOffset;
		int size = theSize;
		if (size == 0)
			return -1;
		int index = size - 1;
		int t = translateToInternalIndex(array, offset, size, index);
		for (; index >= 0; index--) {
			Object v = array[t];
			if (Objects.equals(v, o))
				return index;
			t--;
			if (t < 0)
				t = array.length - 1;
		}
		return -1;
	}

	@Override
	public E set(int index, E element) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		int translated = translateToInternalIndex(index);
		E old = (E) theArray[translated];
		theArray[translated] = element;
		theStamp++;
		return old;
	}

	@Override
	public boolean add(E e) {
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
		theStamp++;
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		if (c.isEmpty())
			return false;
		int cSize = c.size();
		if (cSize + theSize > theMaxCapacity) {
			ensureCapacity(theMaxCapacity);
			if (cSize >= theMaxCapacity) {
				theOffset = 0;
				theAdvanced = theSize;
				theSize = theMaxCapacity;
				Iterator<? extends E> iter = c.iterator();
				for (int i = cSize; i > theMaxCapacity; i++)
					iter.next(); // Bleed off items that would be dropped due to capacity
				for (int i = 0; i < theMaxCapacity; i++)
					theArray[i] = iter.next();
			} else {
				theAdvanced = cSize + theSize - theMaxCapacity;
				int start = translateToInternalIndex(theSize % theArray.length);
				theOffset += theAdvanced;
				if (theOffset >= theArray.length)
					theOffset -= theArray.length;
				theSize = theMaxCapacity;
				for (E e : c)
					theArray[start++] = e;
			}
		} else {
			theAdvanced = 0;
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
		theStamp++;
		return true;
	}

	@Override
	public void add(int index, E element) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		if (index > theSize)
			throw new IndexOutOfBoundsException(index + " of " + theSize);
		internalAdd(index, element);
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		if (c.isEmpty())
			return false;
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
		theStamp++;
		return true;
	}

	/**
	 * @param values The values to add
	 * @return This list
	 */
	public CircularArrayList<E> with(E... values) {
		addAll(Arrays.asList(values));
		return this;
	}

	/**
	 * @param values The values to add
	 * @return This list
	 */
	public CircularArrayList<E> withAll(Collection<? extends E> values) {
		addAll(values);
		return this;
	}

	@Override
	public boolean offerFirst(E e) {
		if (theSize == theMaxCapacity)
			return false;
		add(0, e);
		return true;
	}

	@Override
	public boolean offerLast(E e) {
		if (theSize == theMaxCapacity)
			return false;
		return add(e);
	}

	@Override
	public boolean remove(Object o) {
		int index = indexOf(o);
		if (index >= 0) {
			remove(index);
			return true;
		} else
			return false;
	}

	@Override
	public boolean removeLastOccurrence(Object o) {
		int index = lastIndexOf(o);
		if (index >= 0) {
			remove(index);
			return true;
		} else
			return false;
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
		if (fromIndex < theSize - toIndex) {
			moveContents(theOffset, fromIndex, count);
			clearEntries(theOffset, count);
			theOffset = (theOffset + count) % theArray.length;
		} else {
			moveContents(theOffset + toIndex, theSize - toIndex, -count);
			clearEntries(translateToInternalIndex(theSize - count), count);
		}
		theSize -= count;
		if (count > 0) {
			trimIfNeeded();
			theStamp++;
		}
	}

	@Override
	public boolean removeIf(Predicate<? super E> filter) {
		if (theSize == 0)
			return false;
		int removed = 0;
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
		if (removed > 0) {
			trimIfNeeded();
			theStamp++;
		}
		return removed > 0;
	}

	@Override
	public void clear() {
		Arrays.fill(theArray, theOffset, Math.min(theOffset + theSize, theArray.length), null);
		if (theOffset + theSize > theArray.length)
			Arrays.fill(theArray, 0, theOffset + theSize - theArray.length, null);
		theSize = 0;
		theOffset = 0;
		trimIfNeeded();
		theStamp++;
	}

	@Override
	public E remove(int index) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		return internalRemove(index, translateToInternalIndex(index));
	}

	@Override
	public E pollFirst() {
		if (theSize == 0)
			return null;
		return internalRemove(0, translateToInternalIndex(0));
	}

	@Override
	public E pollLast() {
		if (theSize == 0)
			return null;
		return internalRemove(theSize - 1, translateToInternalIndex(theSize - 1));
	}

	@Override
	public ListIterator<E> iterator(int start, int end, int next, boolean forward) {
		return new CALIterator(null, start, end, next, forward);
	}

	@Override
	public SubList subList(int fromIndex, int toIndex) {
		return new SubList(null, fromIndex, toIndex);
	}

	@Override
	public int hashCode() {
		Object[] array = theArray;
		int offset = theOffset;
		int size = theSize;
		int hash = 0;
		int t = offset;
		for (int i = 0; i < size; i++) {
			E v = (E) array[t];
			hash += v == null ? 0 : v.hashCode();
			t++;
			if (t == array.length)
				t = 0;
		}
		return hash;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Collection))
			return false;
		Collection<?> c = (Collection<?>) o;
		Object[] array = theArray;
		int offset = theOffset;
		int size = theSize;
		if (c.size() != size)
			return false;
		int t = offset;
		Iterator<?> iter = c.iterator();
		for (int i = 0; i < size; i++) {
			if (!iter.hasNext())
				return false;
			Object co = iter.next();
			E v = (E) array[t];
			if (!Objects.equals(v, co))
				return false;
			t++;
			if (t == array.length)
				t = 0;
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		Object[] array = theArray;
		int offset = theOffset;
		int size = theSize;
		str.setLength(0);
		str.append('[');
		int t = offset;
		for (int i = 0; i < size; i++) {
			E v = (E) array[t];
			if (i > 0)
				str.append(", ");
			str.append(v);
			t++;
			if (t == array.length)
				t = 0;
		}
		str.append(']');
		return str.toString();
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
			int oldCapacity = theArray.length;
			if (capacity - MAX_ARRAY_SIZE > 0)
				throw new OutOfMemoryError("Cannot allocate an array of size " + capacity);
			int growCapacity = oldCapacity == 0 ? 10 : oldCapacity + (int) Math.round(theGrowthFactor * oldCapacity);
			if (growCapacity - MAX_ARRAY_SIZE > 0)
				growCapacity = MAX_ARRAY_SIZE;
			int newCapacity = growCapacity;
			if (newCapacity - capacity < 0)
				newCapacity = capacity;
			Object[] newArray = new Object[newCapacity];
			if (theSize > 0)
				internalArrayCopy(newArray);
			theOffset = 0;
			theArray = newArray;
			return true;
		} else
			return false;
	}

	/**
	 * Shrinks the size of this list's internal array to be exactly large enough to hold all the list's current content
	 * 
	 * @return This list
	 */
	public CircularArrayList<E> trimToSize() {
		_trimToSize();
		return this;
	}

	private void _trimToSize() {
		int newCap = Math.max(theSize, theMinCapacity);
		if (newCap != theArray.length) {
			Object[] newArray = newCap == 0 ? EMPTY_ARRAY : new Object[newCap];
			internalArrayCopy(newArray);
			theOffset = 0;
			theArray = newArray;
		}
	}

	private void trimIfNeeded() {
		if (theArray.length > theMinCapacity && theMinOccupancy > 0) {
			int dropSize = (int) Math.round(theMinOccupancy * theArray.length);
			if (theSize < dropSize)
				_trimToSize();
		}
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
			theAdvanced = 0;
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
		theStamp++;
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
		trimIfNeeded();
		theStamp++;
		return removed;
	}

	class CALIterator extends SubView<E> implements ListIterator<E> {
		private int theNext;
		private final boolean isForward;
		private Ternian movedForward;

		CALIterator(SubList parent, int start, int end, int next, boolean forward) {
			super(CircularArrayList.this, parent, start, end);
			theNext = next;
			isForward = forward;
			movedForward = Ternian.NONE;
		}

		@Override
		protected void changed(int added) {
			super.changed(added);
			if (added > 0 && theAdvanced > 0)
				setStart(Math.max(0, getStart() - theAdvanced));
		}

		private boolean has(boolean next) {
			check(-1);
			if (next)
				return theNext < getEnd();
			else
				return theNext > getStart();
		}

		private E advance(boolean next) {
			check(-1);
			E value;
			if (next) {
				if (theNext >= getEnd())
					throw new NoSuchElementException();
				value = getRoot().get(theNext);
				theNext++;
			} else {
				if (theNext <= getStart())
					throw new NoSuchElementException();
				theNext--;
				value = getRoot().get(theNext);
			}
			movedForward = Ternian.of(next);
			return value;
		}

		private int index(boolean next) {
			check(-1);
			if (next)
				return theNext - getStart();
			else
				return theNext - getStart() - 1;
		}

		@Override
		public boolean hasNext() {
			return has(isForward);
		}

		@Override
		public E next() {
			return advance(isForward);
		}

		@Override
		public boolean hasPrevious() {
			return has(!isForward);
		}

		@Override
		public E previous() {
			return advance(!isForward);
		}

		@Override
		public int nextIndex() {
			return index(isForward);
		}

		@Override
		public int previousIndex() {
			return index(!isForward);
		}

		@Override
		public void remove() {
			check(-1);
			switch (movedForward) {
			case FALSE:
				getRoot().remove(theNext);
				break;
			case TRUE:
				getRoot().remove(theNext - 1);
				theNext--;
				break;
			default:
				throw new IllegalStateException("Cannot operate on the last element in this state");
			}
			changed(-1);
		}

		@Override
		public void set(E e) {
			check(-1);
			switch (movedForward) {
			case FALSE:
				getRoot().set(theNext, e);
				break;
			case TRUE:
				getRoot().set(theNext - 1, e);
				break;
			default:
				throw new IllegalStateException("Cannot operate on the last element in this state");
			}
			changed(0);
		}

		@Override
		public void add(E e) {
			check(-1);
			getRoot().add(theNext, e);
			theNext++;
			changed(1);
		}
	}

	/**
	 * The type of list returned from {@link CircularArrayList#subList(int, int)}. This list's {@link #subList(int, int)} also returns a
	 * list of this type.
	 */
	public class SubList extends AbstractSubDequeList<E> {
		SubList(SubList parent, int start, int end) {
			super(CircularArrayList.this, parent, start, end);
		}

		@Override
		protected void changed(int added) {
			super.changed(added);
			if (added > 0 && theAdvanced > 0)
				setStart(Math.max(0, getStart() - theAdvanced));
		}

		@Override
		public ListIterator<E> iterator(int start, int end, int next, boolean forward) {
			if (start < 0 || end > size() || start > end)
				throw new IndexOutOfBoundsException(start + " to " + end + " of " + size());
			return new CALIterator(this, getStart() + start, getStart() + end, getStart() + next, forward);
		}

		@Override
		public boolean offerFirst(E e) {
			if (theSize == theMaxCapacity)
				return false;
			add(0, e);
			return true;
		}

		@Override
		public boolean offerLast(E e) {
			if (theSize == theMaxCapacity)
				return false;
			add(size(), e);
			return true;
		}

		@Override
		public boolean add(E e) {
			add(size(), e);
			return true;
		}

		@Override
		public void add(int index, E element) {
			check(-1);
			if (index < 0 || index > size())
				throw new IndexOutOfBoundsException(index + " of " + size());
			getRoot().add(getStart() + index, element);
			changed(1);
		}

		@Override
		public <T> T[] toArray(final T[] a) {
			Object[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			int start = getStart();
			int end = getEnd();
			T[] arr = a;
			check(-1);
			if (arr.length < end - start)
				arr = (T[]) Array.newInstance(a.getClass().getComponentType(), end - start);
			internalArrayCopy(array, translateToInternalIndex(array, offset, size, start), end - start, arr);
			return arr;
		}

		@Override
		public boolean removeIf(Predicate<? super E> filter) {
			if (theSize == 0)
				return false;
			int removed = 0;
			Object[] array = theArray;
			int cap = array.length;
			int inspect = translateToInternalIndex(getStart());
			int copyTo = inspect;
			int dest = translateToInternalIndex(getEnd());
			int size = theSize;
			long stamp = check(-1);
			do {
				check(stamp);
				E value = (E) array[inspect];
				if (filter.test(value)) {
					removed++;
				} else {
					if (removed > 0)
						array[copyTo] = value;
					copyTo++;
					if (copyTo == cap)
						copyTo = 0;
				}
				inspect++;
				if (inspect == cap)
					inspect = 0;
			} while (inspect != dest);
			check(stamp);
			moveContents(dest, size - getEnd(), -removed);
			copyTo += size - getEnd();
			if (copyTo > cap)
				copyTo -= cap;
			clearEntries(copyTo, removed);
			theSize -= removed;
			changed(-removed);
			trimIfNeeded();
			return removed > 0;
		}

		@Override
		public SubList subList(int fromIndex, int toIndex) {
			check(-1);
			if (fromIndex < 0 || toIndex > size() || fromIndex > toIndex)
				throw new IndexOutOfBoundsException(fromIndex + " to " + toIndex + " of " + size());
			return new SubList(this, getStart() + fromIndex, getStart() + toIndex);
		}
	}
}
