package org.qommons.collect;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.junit.Assert;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.CollectionLockingStrategy.OptimisticContext;

/**
 * A list/deque that uses an array that is indexed circularly. This allows performance improvements due to not having to move array contents
 * when items are removed from the beginning of the list.
 * 
 * This class also supports:
 * <ul>
 * <li>A {@link #setMaxCapacity(int) max capacity} option which will drop elements to maintain a maximum size.</li>
 * <li>Thread-safety</li>
 * <li>Automatic capacity management with {@link #setMinOccupancy(double)}</li>
 * <li>{@link BetterList Reversibility}</li>
 * </ul>
 * 
 * @param <E> The type of elements in the list
 */
public class CircularArrayList<E> implements BetterList<E> {
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
		private boolean isThreadSafe;

		private Builder() {
			theInitCapacity = 0;
			theMaxCapacity = MAX_ARRAY_SIZE;
			theGrowthFactor = DEFAULT_GROWTH_FACTOR;
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
	private static final CircularArrayList<?>.ArrayElement[] EMPTY_ARRAY = new CircularArrayList.ArrayElement[0];

	private ArrayElement[] theArray;
	private final CollectionLockingStrategy theLocker;
	private final boolean isFeatureLocked;
	private int theOffset;
	private int theSize;
	private int theMinCapacity;
	private int theMaxCapacity;
	private double theMinOccupancy;
	private double theGrowthFactor;

	private int theAdvanced;

	/** Creates an empty list */
	public CircularArrayList() {
		this(build(), false);
	}

	private CircularArrayList(Builder builder, boolean featureLocked) {
		theArray = builder.getInitCapacity() == 0 ? (ArrayElement[]) EMPTY_ARRAY
			: new CircularArrayList.ArrayElement[builder.getInitCapacity()];
		theMinCapacity = builder.getMinCapacity();
		theMaxCapacity = builder.getMaxCapacity();
		theMinOccupancy = builder.getMinOccupancy();
		theGrowthFactor = builder.getGrowthFactor();
		theLocker = builder.makeLockingStrategy();
		isFeatureLocked = featureLocked;
	}

	/** For unit tests. Ensures the integrity of the list. */
	public void checkValid() {
		// Can't have more elements than the capacity
		Assert.assertTrue(theSize <= theArray.length);
		// The offset must be a valid index in the array
		Assert.assertTrue(theOffset < theArray.length);
		// The array must be within the list's capacity settings
		Assert.assertTrue(theArray.length <= theMaxCapacity);
		Assert.assertTrue(theArray.length >= theMinCapacity);
		if (theMinOccupancy > 0) {
			// Ensure the minimum occupancy requirement is met
			int occSize = Math.max((int) Math.ceil(theSize / theMinOccupancy), theMinCapacity);
			Assert.assertTrue(theArray.length <= occSize);
		}
		
		// Ensure only elements contained in the list are referenced by the list
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
	 * Sets this list's minimum capacity, which will always be kept if this list's capacity is reduced, either manually via
	 * {@link #trimToSize()} or automatically via #setMinOccupancy(double).
	 * 
	 * @param minCap The minimum capacity for this list
	 * @return This list
	 */
	public CircularArrayList<E> setMinCapacity(int minCap) {
		if (isFeatureLocked)
			throw new IllegalStateException("This list's feature set is locked--its feature set cannot be changed directly");
		try (Transaction t = lock(true, null)) {
			int cap = capConstraintsAdjusted(theArray.length, minCap, theMaxCapacity);
			if (theArray.length < cap) {
				ArrayElement[] newArray = new CircularArrayList.ArrayElement[cap];
				internalArrayCopy(newArray);
				theArray = newArray;
				theOffset = 0;
			}
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
		try (Transaction t = lock(true, null)) {
			int cap = capConstraintsAdjusted(theArray.length, theMinCapacity, maxCap);
			theMaxCapacity = maxCap;
			if (cap < theSize)
				removeRange(0, theSize - maxCap);
			if (theArray.length > cap) {
				ArrayElement[] newArray = new CircularArrayList.ArrayElement[cap];
				internalArrayCopy(newArray);
				theArray = newArray;
				theOffset = 0;
			}
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
	public boolean isLockSupported() {
		return true;
	}

	@Override
	public Transaction lock(boolean write, boolean structural, Object cause) {
		return theLocker.lock(write, structural, cause);
	}

	@Override
	public long getStamp(boolean structuralOnly) {
		return theLocker.getStatus(structuralOnly);
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
	public boolean belongs(Object o) {
		return true;
	}

	private ArrayElement getElementOptimistic(Object value, boolean first) {
		return theLocker.doOptimistically(null, (element, stamp) -> {
			return findElementOptimistic(value, first, theArray, theSize, theOffset, stamp);
		}, true);
	}

	private ArrayElement findElementOptimistic(Object value, boolean first, ArrayElement[] array, int size, int offset,
		OptimisticContext ctx) {
		int t = first ? offset : (offset + size - 1) % array.length;
		for (int i = 0; i < size; i++) {
			ArrayElement el = array[t];
			if (!ctx.check())
				return null;
			if (Objects.equals(el.get(), value))
				return el;
			if (first) {
				t++;
				if (t == array.length)
					t = 0;
			} else {
				t--;
				if (t < 0)
					t = array.length - 1;
			}
		}
		return null;
	}

	@Override
	public CollectionElement<E> getElement(E value, boolean first) {
		return getElementOptimistic(value, first);
	}

	@Override
	public CollectionElement<E> getElement(ElementId id) {
		return ((ArrayElementId) id).element.check().immutable();
	}

	@Override
	public MutableCollectionElement<E> mutableElement(ElementId id) {
		return ((ArrayElementId) id).element.check();
	}

	@Override
	public MutableElementSpliterator<E> spliterator(boolean forward) {
		return new ArraySpliterator(0, theSize, forward ? 0 : theSize, theLocker.getStatus(true));
	}

	@Override
	public MutableElementSpliterator<E> spliterator(ElementId element, boolean asNext) {
		try (Transaction t = lock(false, null)) {
			return new ArraySpliterator(0, size(), ((ArrayElementId) element).element.check().getIndex(), theLocker.getStatus(true));
		}
	}

	@Override
	public CollectionElement<E> getElement(int index) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		return theArray[translateToInternalIndex(index)].immutable();
	}

	@Override
	public int getElementsBefore(ElementId id) {
		int index = ((ArrayElementId) id).element.check().getIndex();
		if (index < 0)
			throw new IllegalArgumentException("Element has been removed");
		return index;
	}

	@Override
	public int getElementsAfter(ElementId id) {
		return theLocker.doOptimistically(-1, (value, ctx) -> {
			int index = ((ArrayElementId) id).element.check().getIndex();
			if (index < 0)
				throw new IllegalArgumentException("Element has been removed");
			return theSize - index - 1;
		}, true);
	}

	@Override
	public boolean contains(Object o) {
		return getElementOptimistic(o, true) != null;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return theLocker.doOptimistically(false, (bool, ctx) -> {
			ArrayElement[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			for (Object o : c)
				if (findElementOptimistic(o, true, array, size, offset, ctx) == null)
					return false;
			return true;
		}, false);
	}

	@Override
	public boolean containsAny(Collection<?> c) {
		return theLocker.doOptimistically(false, (bool, ctx) -> {
			ArrayElement[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			for (Object o : c) {
				if (findElementOptimistic(o, true, array, size, offset, ctx) != null)
					return true;
			}
			return false;
		}, false);
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return BetterList.super.toArray(a);
	}

	@Override
	public Object[] toArray() {
		return BetterList.super.toArray();
	}

	@Override
	public E get(int index) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		return theLocker.doOptimistically(null, (v, ctx) -> {
			ArrayElement[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!ctx.check())
				return null;
			ArrayElement el = array[translateToInternalIndex(array, offset, size, index)];
			if (el == null)
				return null; // May have been removed by another thread
			return el.get();
		}, true);
	}

	@Override
	public E getFirst() {
		return theLocker.doOptimistically(null, (v, ctx) -> {
			ArrayElement[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!ctx.check())
				return null;
			if (size == 0)
				throw new NoSuchElementException();
			ArrayElement el = array[offset];
			if (el == null)
				return null; // May have been removed by another thread
			return el.get();
		}, true);
	}

	@Override
	public E getLast() {
		return theLocker.doOptimistically(null, (v, ctx) -> {
			ArrayElement[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!ctx.check())
				return null;
			if (size == 0)
				throw new NoSuchElementException();
			ArrayElement el = array[translateToInternalIndex(array, offset, size, size - 1)];
			if (el == null)
				return null; // May have been removed by another thread
			return el.get();
		}, true);
	}

	@Override
	public E peekFirst() {
		return theLocker.doOptimistically(null, (v, ctx) -> {
			ArrayElement[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (size == 0 || !ctx.check())
				return null;
			ArrayElement el = array[offset];
			if (el == null)
				return null; // May have been removed by another thread
			return el.get();
		}, true);
	}

	@Override
	public E peekLast() {
		return theLocker.doOptimistically(null, (v, ctx) -> {
			ArrayElement[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (size == 0 || !ctx.check())
				return null;
			ArrayElement el = array[translateToInternalIndex(array, offset, size, size - 1)];
			if (el == null)
				return null; // May have been removed by another thread
			return el.get();
		}, true);
	}

	@Override
	public int indexOf(Object o) {
		return theLocker.doOptimistically(-1, (idx, ctx) -> {
			ArrayElement el = getElementOptimistic(o, true);
			return el == null ? -1 : el.getIndex();
		}, true);
	}

	@Override
	public int lastIndexOf(Object o) {
		return theLocker.doOptimistically(-1, (idx, ctx) -> {
			ArrayElement el = getElementOptimistic(o, false);
			return el == null ? -1 : el.getIndex();
		}, true);
	}

	@Override
	public String canAdd(E value) {
		return null;
	}

	@Override
	public CollectionElement<E> addElement(E value, boolean first) {
		try (Transaction t = lock(true, null)) {
			return internalAdd(first ? 0 : theSize, value).immutable();
		}
	}

	@Override
	public CollectionElement<E> addElement(int index, E value) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		try (Transaction t = lock(true, null)) {
			if (index > theSize)
				throw new IndexOutOfBoundsException(index + " of " + theSize);
			return internalAdd(index, value).immutable();
		}
	}

	@Override
	public void add(int index, E element) {
		addElement(index, element);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		if (c.isEmpty())
			return false;
		try (Transaction t = lock(true, null); Transaction ct = Transactable.lock(c, false, null)) {
			return internalAddAll(theSize, c);
		}
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		if (c.isEmpty())
			return false;
		try (Transaction t = lock(true, null); Transaction ct = Transactable.lock(c, false, null)) {
			return internalAddAll(index, c);
		}
	}

	private boolean internalAddAll(int index, Collection<? extends E> c) {
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
			theArray[ti] = new ArrayElement(iter.next(), ti);
			ti++;
			if (ti == theArray.length)
				ti = 0;
		}
		theOffset += theAdvanced;
		if (theOffset >= theArray.length)
			theOffset -= theArray.length;
		theLocker.changed(true); // This value should not matter for the root locker
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
		// We'll interpret this method to mean that this method should not cause any items to be dropped due to capacity
		try (Transaction t = lock(true, null)) {
			if (theSize < theMaxCapacity) {
				add(0, e);
				return true;
			}
			return false;
		}
	}

	@Override
	public boolean offerLast(E e) {
		// We'll interpret this method to mean that this method should not cause any items to be dropped due to capacity
		try (Transaction t = lock(true, null)) {
			if (theSize < theMaxCapacity) {
				add(e);
				return true;
			}
			return false;
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
				updateIndexes(theOffset, fromIndex);
			} else {
				moveContents(theOffset + toIndex, theSize - toIndex, -count);
				clearEntries(translateToInternalIndex(theSize - count), count);
				updateIndexes(theOffset + fromIndex, theSize - toIndex);
			}
			theSize -= count;
			theLocker.changed(true);
			if (count > 0)
				trimIfNeeded();
		}
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
				ArrayElement value = theArray[inspect];
				if (filter.test(value.get())) {
					removed++;
				} else {
					if (removed > 0) {
						theArray[copyTo] = value;
						theArray[copyTo].setIndex(copyTo);
					}
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
				theLocker.changed(true);
			}
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
			trimIfNeeded();
			theLocker.changed(true);
		}
	}

	@Override
	public E remove(int index) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		try (Transaction t = lock(true, null)) {
			return internalRemove(index, translateToInternalIndex(index)).get();
		}
	}

	@Override
	public int hashCode() {
		return theLocker.doOptimistically(0, (h, ctx) -> {
			ArrayElement[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!ctx.check())
				return -1;
			int hash = 0;
			int t = offset;
			for (int i = 0; i < size; i++) {
				ArrayElement el = array[t];
				if (!ctx.check())
					return -1;
				hash += el.get() == null ? 0 : el.get().hashCode();
				t++;
				if (t == array.length)
					t = 0;
			}
			return hash;
		}, false);
	}

	@Override
	public boolean equals(Object o){
		if(!(o instanceof Collection))
			return false;
		Collection<?> c=(Collection<?>) o;
		return theLocker.doOptimistically(false, (b, ctx) -> {
			ArrayElement[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!ctx.check())
				return false;
			if (c.size() != size)
				return false;
			int t = offset;
			Iterator<?> iter = c.iterator();
			for (int i = 0; i < size; i++) {
				if (!iter.hasNext())
					return false;
				Object co = iter.next();
				ArrayElement el = array[t];
				if (!ctx.check())
					return false;
				if (!Objects.equals(el.get(), co))
					return false;
				t++;
				if (t == array.length)
					t = 0;
			}
			return true;
		}, false);
	}

	@Override
	public String toString() {
		return theLocker.doOptimistically(new StringBuilder(), (str, ctx) -> {
			ArrayElement[] array = theArray;
			int offset = theOffset;
			int size = theSize;
			if (!ctx.check())
				return str;
			str.setLength(0);
			str.append('[');
			int t = offset;
			for (int i = 0; i < size; i++) {
				ArrayElement el = array[t];
				if (!ctx.check())
					return str;
				if (i > 0)
					str.append(", ");
				str.append(el.get());
				t++;
				if (t == array.length)
					t = 0;
			}
			str.append(']');
			return str;
		}, true).toString();
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
				if (capacity - MAX_ARRAY_SIZE > 0)
					throw new OutOfMemoryError("Cannot allocate an array of size " + capacity);
				int growCapacity = oldCapacity == 0 ? 10 : oldCapacity + (int) Math.round(theGrowthFactor * oldCapacity);
				if (growCapacity - MAX_ARRAY_SIZE > 0)
					growCapacity = MAX_ARRAY_SIZE;
				int newCapacity = growCapacity;
				if (newCapacity - capacity < 0)
					newCapacity = capacity;
				ArrayElement[] newArray = new CircularArrayList.ArrayElement[newCapacity];
				if (theSize > 0)
					internalArrayCopy(newArray);
				theOffset = 0;
				theArray = newArray;
			}
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
		try (Transaction t = theLocker.lock(true, null)) {
			_trimToSize();
		}
		return this;
	}

	private void _trimToSize() {
		int newCap = Math.max(theSize, theMinCapacity);
		if (newCap != theArray.length) {
			ArrayElement[] newArray = newCap == 0 ? (ArrayElement[]) EMPTY_ARRAY : new CircularArrayList.ArrayElement[newCap];
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

	final int translateToCollectionIndex(int arrayIndex) {
		int ci = arrayIndex - theOffset;
		if (ci < 0)
			ci += theArray.length;
		return ci;
	}

	private static final int translateToInternalIndex(CircularArrayList<?>.ArrayElement[] array, int offset, int size, int index) {
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
				// TODO Pretty sure this is wrong. Need to update offset for the less than half case, right?
				if (index <= theSize / 2) {
					moveContents(theOffset + theAdvanced, index, -spaces);
				} else {
					moveContents(theOffset + index, theSize - index, spaces);
				}
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
		if (sourceStart >= cap)
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
	
	void updateIndexes(int from, int count){
		int ti=from;
		while(ti!=count){
			if(ti==theArray.length)
				ti=0;
			theArray[ti].setIndex(ti);
			ti++;
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
	protected final void internalArrayCopy(ArrayElement[] a) {
		internalArrayCopy(theArray, theOffset, theSize, a);
	}

	private static final void internalArrayCopy(CircularArrayList<?>.ArrayElement[] src, int offset, int size,
		CircularArrayList<?>.ArrayElement[] dest) {
		System.arraycopy(src, offset, dest, 0, Math.min(size, src.length - offset));
		if (offset + size > src.length)
			System.arraycopy(src, 0, dest, src.length - offset, size - src.length + offset);
	}

	private final ArrayElement internalAdd(int index, E value) {
		makeRoom(index, 1);
		ArrayElement element;
		int ti = translateToInternalIndex(index);
		theArray[ti] = element = new ArrayElement(value, ti);
		theOffset += theAdvanced;
		if (theOffset >= theArray.length)
			theOffset -= theArray.length;
		theLocker.changed(true);
		return element;
	}

	private final ArrayElement internalRemove(int listIndex, int translatedIndex) {
		ArrayElement removed = theArray[translatedIndex];
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
			updateIndexes(theOffset, listIndex);
			theSize--;
		} else {
			moveContents(translatedIndex + 1, theSize - listIndex - 1, -1);
			theArray[translateToInternalIndex(theSize - 1)] = null; // Remove reference
			updateIndexes(translatedIndex, theSize - listIndex - 1);
			theSize--;
		}
		removed.removed();
		trimIfNeeded();
		theLocker.changed(true);
		return removed;
	}

	private class ArrayElementId implements ElementId {
		final ArrayElement element;

		ArrayElementId(ArrayElement element) {
			this.element = element;
		}

		@Override
		public boolean isPresent() {
			return element.getIndex() >= 0;
		}

		@Override
		public int compareTo(ElementId o) {
			int diff = element.getIndex() - ((ArrayElementId) o).element.getIndex();
			if (isPresent()) {
				if (o.isPresent())
					return diff;
				else
					return diff + 1;
			} else {
				if (o.isPresent())
					return diff - 1;
				else
					return diff;
			}
		}

		@Override
		public int hashCode() {
			return element.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return element == ((ArrayElementId) obj).element;
		}
	}

	private class ArrayElement implements MutableCollectionElement<E> {
		private int index;
		private final ArrayElementId theElementId;
		private E theValue;

		ArrayElement(E value, int index) {
			this.index = index;
			theElementId = new ArrayElementId(this);
			theValue = value;
		}

		ArrayElement check() {
			if (!isPresent())
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return this;
		}

		private boolean isPresent() {
			return index >= 0;
		}

		public int getIndex() {
			int i = index;
			if (i < 0)
				i = -i - 1;
			return translateToCollectionIndex(i);
		}

		private void setIndex(int index) {
			this.index = index;
		}

		@Override
		public ElementId getElementId() {
			return theElementId;
		}

		@Override
		public E get() {
			return theValue;
		}

		@Override
		public String isEnabled() {
			if (index < 0)
				throw new IllegalStateException("This element has been removed");
			return null;
		}

		@Override
		public String isAcceptable(E value) {
			if (index < 0)
				throw new IllegalStateException("This element has been removed");
			if (!belongs(value))
				return StdMsg.ILLEGAL_ELEMENT;
			return null;
		}

		@Override
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			if (index < 0)
				throw new IllegalStateException("This element has been removed");
			String msg = isAcceptable(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			theValue = value;
		}

		@Override
		public String canRemove() {
			return null;
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			try (Transaction t = lock(false, null)) {
				if (index < 0)
					throw new IllegalStateException("This element has been removed");
				internalRemove(getIndex(), index);
			}
		}

		void removed() {
			index = -(index + 1);
		}

		@Override
		public String canAdd(E value, boolean before) {
			if (index < 0)
				throw new IllegalStateException("This element has been removed");
			return isAcceptable(value);
		}

		@Override
		public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
			if (index < 0)
				throw new IllegalStateException("This element has been removed");
			String msg = canAdd(value, before);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			int cIndex = getIndex();
			if (!before)
				cIndex++;
			return CircularArrayList.this.addElement(cIndex, value).getElementId();
		}

		@Override
		public String toString() {
			return new StringBuilder().append('[').append(getIndex()).append("]=").append(theValue).toString();
		}
	}

	private class ArraySpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<E> {
		private int theStart;
		private int theEnd;
		private int theCursor; // The index of the element that would be given to the consumer for tryAdvance()
		private int theCurrentIndex; // The index of the element last returned by this iterator from tryAdvance or tryReverse()
		private boolean elementExists;
		private long theStructureStamp;

		ArraySpliterator(int start, int end, int initIndex, long structStamp) {
			super(CircularArrayList.this);
			int size = theSize;
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
			theStructureStamp = structStamp;
			check();
		}

		private void check() {
			if (theStructureStamp != CircularArrayList.this.theLocker.getStatus(true))
				throw new ConcurrentModificationException(BACKING_COLLECTION_CHANGED);
		}

		@Override
		public long estimateSize() {
			return theEnd - theStart;
		}

		@Override
		public long getExactSizeIfKnown() {
			return theEnd - theStart;
		}

		@Override
		public int characteristics() {
			return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
		}

		@Override
		protected boolean internalForElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
			int tIndex = tryElement(forward);
			if (tIndex < 0)
				return false;
			action.accept(theArray[tIndex].immutable());
			return true;
		}

		@Override
		protected boolean internalForElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
			int tIndex = tryElement(forward);
			if (tIndex < 0)
				return false;
			action.accept(new SpliterWrappingEl(theArray[tIndex], forward));
			return true;
		}

		private int tryElement(boolean advance) {
			check();
			if (advance) {
				if (theCursor >= theEnd)
					return -1;
				theCurrentIndex = theCursor;
				theCursor++;
			} else {
				if (theCursor <= theStart)
					return -1;
				theCursor--;
				theCurrentIndex = theCursor;
			}
			int translatedIndex = translateToInternalIndex(theCurrentIndex);
			elementExists = true;
			return translatedIndex;
		}

		@Override
		public MutableElementSpliterator<E> trySplit() {
			if (theEnd - theStart <= 1)
				return null;
			int mid = (theStart + theEnd) / 2;
			ArraySpliterator split;
			if (theCursor <= mid) {
				split = new ArraySpliterator(mid, theEnd, mid, theStructureStamp);
				theEnd = mid;
			} else {
				split = new ArraySpliterator(theStart, mid, mid, theStructureStamp);
				theStart = mid;
			}
			return split;
		}

		class SpliterWrappingEl implements MutableCollectionElement<E> {
			private final MutableCollectionElement<E> el;
			private final boolean isForward;

			SpliterWrappingEl(MutableCollectionElement<E> el, boolean forward) {
				this.el = el;
				isForward = forward;
			}

			@Override
			public ElementId getElementId() {
				return el.getElementId();
			}

			@Override
			public E get() {
				return el.get();
			}

			@Override
			public String isEnabled() {
				return el.isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				return el.isAcceptable(value);
			}

			@Override
			public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
				try (Transaction t = CircularArrayList.this.theLocker.lock(true, false, null)) {
					el.set(value);
					CircularArrayList.this.theLocker.changed(false);
				}
			}

			@Override
			public String canRemove() {
				return el.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				try (Transaction t = CircularArrayList.this.theLocker.lock(true, true, null)) {
					check();
					el.remove();
					CircularArrayList.this.theLocker.changed(true);
					theStructureStamp = CircularArrayList.this.theLocker.getStatus(true);
					theEnd--;
					if (isForward)
						theCursor--;
				}
			}

			@Override
			public String canAdd(E value, boolean before) {
				return el.canAdd(value, before);
			}

			@Override
			public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				try (Transaction t = CircularArrayList.this.theLocker.lock(true, true, null)) {
					check();
					ElementId newId = el.add(value, before);
					if (newId == null)
						return null; // Can't happen currently, but meh
					CircularArrayList.this.theLocker.changed(true);
					theStructureStamp = CircularArrayList.this.theLocker.getStatus(true);
					theEnd++;
					if (before)
						theCursor++;
					return newId;
				}
			}
		}

		@Override
		public String toString() {
			return CircularArrayList.this.theLocker.doOptimistically(new StringBuilder(), (str, ctx) -> {
				ArrayElement[] array = theArray;
				int offset = theOffset;
				int size = theSize;
				int start = theStart;
				int end = theEnd;
				int cursor = theCurrentIndex;
				boolean ee = elementExists;
				if (!ctx.check())
					return str;
				str.setLength(0);
				str.append('<');
				int index = start;
				int t = translateToInternalIndex(array, offset, size, index);
				boolean first = true;
				for (; index < end; index++) {
					ArrayElement el = array[t];
					if (!ctx.check())
						return str;
					if (!first)
						str.append(", ");
					first = false;
					if (index == cursor) {
						str.append('*');
						if (!ee)
							str.append('*');
					}
					str.append(el.get());
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
			}, true).toString();
		}
	}
}
