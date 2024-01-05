package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.IntUnaryOperator;

import org.qommons.Identifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * This class is based on the idea that certain small optimizations can be made to searching within small, sorted arrays of constant size.
 * 
 * @param <E> The type of the set
 */
public interface HighPerformanceArraySet<E> extends BetterSortedSet<E> {
	/** The maximum size of a set that may be created with {@link #create(Comparator, Collection)} */
	public static final int MAX_SIZE = 7;

	/**
	 * @param <E> The type for the set
	 * @param sorting The sorting for the set
	 * @param values The values for the set
	 * @return A new {@link HighPerformanceArraySet} containing the given elements
	 * @throws IllegalArgumentException If:
	 *         <ul>
	 *         <li>The number of values is greater than {@link #MAX_SIZE}</li>
	 *         <li>If any of the values are null</li>
	 *         <li>If any of the values are equivalent according to the given comparator</li>
	 *         </ul>
	 */
	static <E> HighPerformanceArraySet<E> create(Comparator<? super E> sorting, E... values) throws IllegalArgumentException {
		return create(sorting, Arrays.asList(values));
	}

	/**
	 * @param <E> The type for the set
	 * @param sorting The sorting for the set
	 * @param values The values for the set
	 * @return A new {@link HighPerformanceArraySet} containing the given elements
	 * @throws IllegalArgumentException If:
	 *         <ul>
	 *         <li>The number of values is greater than {@link #MAX_SIZE}</li>
	 *         <li>If any of the values are null</li>
	 *         <li>If any of the values are equivalent according to the given comparator</li>
	 *         </ul>
	 */
	static <E> HighPerformanceArraySet<E> create(Comparator<? super E> sorting, Collection<? extends E> values)
		throws IllegalArgumentException {
		return Impl.create(sorting, values);
	}

	/**
	 * @param search The search to use
	 * @param filter The search filter to use
	 * @return The index of the element in this set matching the search, or <code>-index-1</code>, where <code>index</code> is the index
	 *         where an element matching the search would exist in this set if it were present.
	 */
	int findByIndex(IntUnaryOperator search, SortedSearchFilter filter);

	/**
	 * Overriding this method can allow better performance than going through the BetterSortedSet API because:
	 * <ul>
	 * <li>We can use indexing faster</li>
	 * <li>We avoid the use of elements</li>
	 * <li>We can make fewer comparisons than using sorting alone, because for the "leaves" we can compare with
	 * {@link Object#equals(Object)}, which may be faster</li>
	 * </ul>
	 * 
	 * @param search The search operation
	 * @return The index of the element in this set matching the search, or -index-1 where index is the location where an element matching
	 *         the search would be added if that were possible.
	 */
	int indexFor(IntUnaryOperator search);

	/**
	 * Overriding this method can allow better performance than going through the BetterSortedSet API because:
	 * <ul>
	 * <li>We can use indexing faster</li>
	 * <li>We avoid the use of elements</li>
	 * <li>We can make fewer comparisons than using sorting alone, because for the "leaves" we can compare with
	 * {@link Object#equals(Object)}, which may be faster</li>
	 * </ul>
	 * 
	 * @param value The value to search for in the set
	 * @return The index of the element in this set whose value {@link Object#equals(Object)} the give nvalue, or a number &lt;0 if no such
	 *         element exists in this set
	 */
	@Override
	int indexOf(Object value);

	@Override
	default CollectionElement<E> getElement(E value, boolean first) {
		int index = indexOf(value);
		return index < 0 ? null : getElement(index);
	}

	@Override
	default CollectionElement<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
		int found = findByIndex(index -> search.compareTo(get(index)), filter);
		return found < 0 ? null : getElement(found);
	}

	@Override
	default int indexFor(Comparable<? super E> search) {
		return findByIndex(index -> search.compareTo(get(index)), SortedSearchFilter.OnlyMatch);
	}

	@Override
	default boolean isConsistent(ElementId element) {
		return true;
	}

	@Override
	default boolean checkConsistency() {
		return false;
	}

	@Override
	default <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
		return false;
	}

	@Override
	default <X> boolean repair(RepairListener<E, X> listener) {
		return false;
	}

	@Override
	default boolean belongs(Object o) {
		return true;
	}

	@Override
	default BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return BetterList.of(getElement(sourceEl));
		return BetterList.empty();
	}

	@Override
	default BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return BetterList.of(localElement);
		return BetterList.empty();
	}

	@Override
	default String canAdd(E value, ElementId after, ElementId before) {
		return StdMsg.UNSUPPORTED_OPERATION;
	}

	@Override
	default CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	default void clear() {}

	@Override
	default Transaction lock(boolean write, Object cause) {
		return Transaction.NONE;
	}

	@Override
	default Transaction tryLock(boolean write, Object cause) {
		return Transaction.NONE;
	}

	@Override
	default Collection<Cause> getCurrentCauses() {
		return Collections.emptyList();
	}

	@Override
	default CoreId getCoreId() {
		return CoreId.EMPTY;
	}

	@Override
	default ThreadConstraint getThreadConstraint() {
		return ThreadConstraint.ANY;
	}

	@Override
	default long getStamp() {
		return 0;
	}

	/**
	 * @param <V> The value type for the map
	 * @return A map with this set as the key set and all-null values
	 */
	<V> HPAMap<E, V> createMap();

	/**
	 * A map created by {@link HighPerformanceArraySet#createMap()}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public interface HPAMap<K, V> extends BetterSortedMap<K, V> {
		@Override
		HighPerformanceArraySet<K> keySet();

		/**
		 * @param index The index of the entry to get
		 * @return The entry in this map at the given index
		 */
		Map.Entry<K, V> getEntry(int index);

		/**
		 * @param index The index of the value to get
		 * @return The value in this map at the given index
		 */
		V get(int index);

		@Override
		default BetterSortedSet<Entry<K, V>> entrySet() {
			return new EntrySet<>(this);
		}

		/** @return A map backed by this map but which is unmodifiable */
		HPAMap<K, V> unmodifiable();

		/**
		 * Default implementation of {@link HPAMap#entrySet()}
		 * 
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 */
		static class EntrySet<K, V> extends BetterSortedMap.BetterSortedEntrySet<K, V> {
			EntrySet(HPAMap<K, V> map) {
				super(map);
			}

			@Override
			protected HPAMap<K, V> getMap() {
				return (HPAMap<K, V>) super.getMap();
			}

			@Override
			public Iterator<Map.Entry<K, V>> iterator() {
				return new Iterator<Map.Entry<K, V>>() {
					private int theIndex;

					@Override
					public boolean hasNext() {
						return theIndex < size();
					}

					@Override
					public Map.Entry<K, V> next() {
						return getMap().getEntry(theIndex++);
					}
				};
			}
		}
	}

	/** Implementations for this class */
	class Impl {
		private static final Object[] EMPTY_VALUES = new Object[0];

		static <E> HighPerformanceArraySet<E> create(Comparator<? super E> sorting, Collection<? extends E> values) {
			if (values.size() > MAX_SIZE)
				throw new IllegalArgumentException("Max size of a " + HighPerformanceArraySet.class.getSimpleName() + " is " + MAX_SIZE);
			if (values.isEmpty())
				return new Empty<>(sorting);
			else if (values.size() == 1) {
				E value = values.iterator().next();
				if (value == null)
					throw new IllegalArgumentException(
						"Null values are not allowed in " + HighPerformanceArraySet.class.getSimpleName() + "s");
				return new Singleton<>(sorting, value);
			}

			Object[] valuesA = values.toArray();
			Arrays.sort(valuesA, (Comparator<Object>) sorting);
			for (int i = 1; i < valuesA.length; i++) {
				if (valuesA[i] == null)
					throw new IllegalArgumentException(
						"Null values are not allowed in " + HighPerformanceArraySet.class.getSimpleName() + "s");
				else if (sorting.compare((E) valuesA[i - 1], (E) valuesA[i]) == 0)
					throw new IllegalArgumentException("Values for a " + HighPerformanceArraySet.class.getSimpleName()
						+ " must all be distinct: " + valuesA[i - 1] + " and " + valuesA[i]);
			}
			switch (valuesA.length) {
			case 2:
				return new Double<>(sorting, (E) valuesA[0], (E) valuesA[1]);
			case 3:
				return new Triple<>(sorting, valuesA);
			case 4:
				return new Quadruple<>(sorting, valuesA);
			case 5:
				return new Quintuple<>(sorting, valuesA);
			case 6:
				return new Hextuple<>(sorting, valuesA);
			default:
				return new Heptuple<>(sorting, valuesA);
			}
		}

		static class Empty<E> extends BetterSortedSet.EmptySortedSet<E> implements HighPerformanceArraySet<E> {
			Empty(Comparator<? super E> compare) {
				super(compare);
			}

			@Override
			public int indexFor(IntUnaryOperator search) {
				return -1;
			}

			@Override
			public int findByIndex(IntUnaryOperator search, SortedSearchFilter filter) {
				return -1;
			}

			@Override
			public int indexOf(Object value) {
				return -1;
			}

			@Override
			public <V> HPAMap<E, V> createMap() {
				return new HighPerformanceArrayMap<>(this);
			}
		}

		static abstract class AbstractHPAS<E> implements HighPerformanceArraySet<E> {
			protected final Comparator<? super E> theSorting;
			private Object theIdentity;

			protected AbstractHPAS(Comparator<? super E> sorting) {
				theSorting = sorting;
			}

			@Override
			public Comparator<? super E> comparator() {
				return theSorting;
			}

			@Override
			public abstract E get(int index);

			@Override
			public abstract CollectionElement<E> getElement(int index) throws IndexOutOfBoundsException;

			protected abstract MutableCollectionElement<E> getMutableElement(int index) throws IndexOutOfBoundsException;

			@Override
			public int getElementsBefore(ElementId id) {
				if (id instanceof IndexedElementId && ((IndexedElementId) id).set == this)
					return ((IndexedElementId) id).index;
				throw new NoSuchElementException();
			}

			@Override
			public int getElementsAfter(ElementId id) {
				return size() - getElementsBefore(id) - 1;
			}

			@Override
			public CollectionElement<E> getElement(ElementId id) {
				if (id instanceof IndexedElementId && ((IndexedElementId) id).set == this)
					return getElement(((IndexedElementId) id).index);
				throw new NoSuchElementException();
			}

			@Override
			public CollectionElement<E> getTerminalElement(boolean first) {
				return getElement(first ? 0 : size() - 1);
			}

			@Override
			public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
				if (elementId instanceof IndexedElementId && ((IndexedElementId) elementId).set == this) {
					int index = ((IndexedElementId) elementId).index;
					if (next) {
						if (index == size() - 1)
							return null;
						return getElement(index + 1);
					} else {
						if (index == 0)
							return null;
						return getElement(index - 1);
					}
				}
				throw new NoSuchElementException();
			}

			@Override
			public MutableCollectionElement<E> mutableElement(ElementId id) {
				if (id instanceof IndexedElementId && ((IndexedElementId) id).set == this)
					return getMutableElement(((IndexedElementId) id).index);
				throw new NoSuchElementException();
			}

			@Override
			public ElementId getEquivalentElement(ElementId equivalentEl) {
				if (equivalentEl instanceof IndexedElementId && ((IndexedElementId) equivalentEl).set == this)
					return equivalentEl;
				return null;
			}

			@Override
			public boolean isEmpty() {
				return false;
			}

			@Override
			public Object getIdentity() {
				if (theIdentity == null)
					theIdentity = Identifiable.baseId("HPAS", this);
				return theIdentity;
			}

			@Override
			public <V> HPAMap<E, V> createMap() {
				return new HighPerformanceArrayMap<>(this);
			}

			@Override
			public int hashCode() {
				return BetterSet.hashCode(this);
			}

			@Override
			public boolean equals(Object obj) {
				return BetterSet.equals(this, obj);
			}

			@Override
			public String toString() {
				return BetterSet.toString(this);
			}

			CollectionElement<E> createElement(int index) {
				return new Element(index);
			}

			MutableCollectionElement<E> createMutableElement(int index) {
				return new MutableElement(index);
			}

			class Element implements CollectionElement<E> {
				final IndexedElementId theId;

				Element(int index) {
					theId = new IndexedElementId(AbstractHPAS.this, index);
				}

				@Override
				public ElementId getElementId() {
					return theId;
				}

				@Override
				public E get() {
					return AbstractHPAS.this.get(theId.index);
				}

				@Override
				public int hashCode() {
					return theId.hashCode();
				}

				@Override
				public boolean equals(Object obj) {
					return obj == this;
				}

				@Override
				public String toString() {
					return "[" + theId.index + "]=" + get();
				}
			}

			class MutableElement extends Element implements MutableCollectionElement<E> {
				MutableElement(int index) {
					super(index);
				}

				@Override
				public BetterCollection<E> getCollection() {
					return AbstractHPAS.this;
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
			}
		}

		static class IndexedElementId implements ElementId {
			final HighPerformanceArraySet<?> set;
			final int index;

			IndexedElementId(HighPerformanceArraySet<?> set, int index) {
				this.set = set;
				this.index = index;
			}

			@Override
			public boolean isPresent() {
				return true;
			}

			@Override
			public int compareTo(ElementId o) {
				return Integer.compare(index, ((IndexedElementId) o).index);
			}
		}

		static class Singleton<E> extends AbstractHPAS<E> {
			private final E theValue;
			private final CollectionElement<E> theElement;
			private MutableCollectionElement<E> theMutableElement;

			Singleton(Comparator<? super E> sorting, E value) {
				super(sorting);
				theValue = value;
				theElement = createElement(0);
			}

			@Override
			public E get(int index) {
				if (index == 0)
					return theValue;
				throw new IndexOutOfBoundsException(index + " of 1");
			}

			@Override
			public CollectionElement<E> getElement(int index) throws IndexOutOfBoundsException {
				if (index == 0)
					return theElement;
				throw new IndexOutOfBoundsException(index + " of 1");
			}

			@Override
			protected MutableCollectionElement<E> getMutableElement(int index) throws IndexOutOfBoundsException {
				if (index == 0) {
					if (theMutableElement == null)
						theMutableElement = createMutableElement(0);
					return theMutableElement;
				}
				throw new IndexOutOfBoundsException(index + " of 1");
			}

			@Override
			public int size() {
				return 1;
			}

			@Override
			public int indexFor(IntUnaryOperator search) {
				int comp = search.applyAsInt(0);
				if (comp == 0)
					return 0;
				else if (comp < 0)
					return -1;
				else
					return -2;
			}

			@Override
			public int findByIndex(IntUnaryOperator search, SortedSearchFilter filter) {
				switch (filter) {
				case PreferGreater:
				case PreferLess:
					return 0;
				default:
					break;
				}
				int comp = search.applyAsInt(0);
				switch (filter) {
				case Less:
					if (comp >= 0)
						return 0;
					else
						return -1;
				case Greater:
					if (comp <= 0)
						return 0;
					else
						return -1;
					// case OnlyMatch:
				default:
					if (comp == 0)
						return 0;
					else
						return -1;
				}
			}

			@Override
			public int indexOf(Object value) {
				if (theValue.equals(value))
					return 0;
				else
					return -1;
			}

			@Override
			public <T> T[] toArray(T[] a) {
				if (a.length == 0)
					a = (T[]) Array.newInstance(a.getClass().getComponentType(), 1);
				a[0] = (T) theValue;
				return a;
			}
		}

		static class Double<E> extends AbstractHPAS<E> {
			private final E theFirst;
			private final E theSecond;
			private final CollectionElement<E> theFirstElement;
			private final CollectionElement<E> theSecondElement;
			private MutableCollectionElement<E> theFirstMutableElement;
			private MutableCollectionElement<E> theSecondMutableElement;

			Double(Comparator<? super E> sorting, E first, E second) {
				super(sorting);
				theFirst = first;
				theSecond = second;
				theFirstElement = createElement(0);
				theSecondElement = createElement(1);
			}

			@Override
			public E get(int index) {
				switch (index) {
				case 0:
					return theFirst;
				case 1:
					return theSecond;
				default:
					throw new IndexOutOfBoundsException(index + " of 2");
				}
			}

			@Override
			public CollectionElement<E> getElement(int index) throws IndexOutOfBoundsException {
				switch (index) {
				case 0:
					return theFirstElement;
				case 1:
					return theSecondElement;
				default:
					throw new IndexOutOfBoundsException(index + " of 2");
				}
			}

			@Override
			protected MutableCollectionElement<E> getMutableElement(int index) throws IndexOutOfBoundsException {
				switch (index) {
				case 0:
					if (theFirstMutableElement == null)
						theFirstMutableElement = createMutableElement(0);
					return theFirstMutableElement;
				case 1:
					if (theSecondMutableElement == null)
						theSecondMutableElement = createMutableElement(1);
					return theSecondMutableElement;
				default:
					throw new IndexOutOfBoundsException(index + " of 2");
				}
			}

			@Override
			public int size() {
				return 2;
			}

			@Override
			public int indexFor(IntUnaryOperator search) {
				int comp = search.applyAsInt(0);
				if (comp == 0)
					return 0;
				if (comp < 0)
					return -1;
				comp = search.applyAsInt(1);
				if (comp == 0)
					return 1;
				else if (comp < 0)
					return -2;
				else
					return -3;
			}

			@Override
			public int findByIndex(IntUnaryOperator search, SortedSearchFilter filter) {
				int comp = search.applyAsInt(0);
				if (comp == 0)
					return 0;
				if (comp < 0) {
					switch (filter) {
					case OnlyMatch:
						return -1;
					case Less:
					case PreferGreater:
						return 0;
					default:
						break;
					}
				}
				comp = search.applyAsInt(1);
				if (comp == 0)
					return 1;
				else if (comp > 0) { // An element matching the search would go between the 2 existing elements
					switch (filter) {
					case OnlyMatch:
						return -2;
					case Less:
					case PreferGreater:
						return 1;
					case Greater:
					case PreferLess:
					default:
						return 0;
					}
				} else { // An element matching the search would go after the second element
					switch (filter) {
					case OnlyMatch:
						return -3;
					case Greater:
					case PreferGreater:
						return 1;
					case PreferLess:
					case Less:
					default:
						return 0;
					}
				}
			}

			@Override
			public int indexOf(Object value) {
				int comp = theSorting.compare((E) value, theFirst);
				if (comp == 0)
					return 0;
				else if (comp < 0)
					return -1;
				comp = theSorting.compare((E) value, theSecond);
				if (comp == 0)
					return 1;
				else if (comp < 0)
					return -2;
				else
					return -3;
			}

			@Override
			public <T> T[] toArray(T[] a) {
				if (a.length < 2)
					a = (T[]) Array.newInstance(a.getClass().getComponentType(), 2);
				a[0] = (T) theFirst;
				a[1] = (T) theSecond;
				return a;
			}
		}

		static abstract class ArrayBackedHPAS<E> extends AbstractHPAS<E> {
			protected final Object[] theValues;
			private final CollectionElement<E>[] theElements;
			private MutableCollectionElement<E>[] theMutableElements;

			ArrayBackedHPAS(Comparator<? super E> sorting, Object[] values) {
				super(sorting);
				theValues = values;
				theElements = new CollectionElement[values.length];
				for (int i = 0; i < values.length; i++)
					theElements[i] = createElement(i);
			}

			@Override
			public int size() {
				return theValues.length;
			}

			@Override
			public E get(int index) {
				return (E) theValues[index];
			}

			@Override
			public int findByIndex(IntUnaryOperator search, SortedSearchFilter filter) {
				int index = indexFor(search);
				if (index >= 0 || filter == SortedSearchFilter.OnlyMatch)
					return index;
				int insertIndex = -index - 1;
				switch (filter) {
				case Greater:
					if (insertIndex == size())
						return index;
					else if (insertIndex == size())
						return index;
					else
						return insertIndex;
				case Less:
					if (insertIndex == 0)
						return -1;
					else
						return insertIndex - 1;
				case PreferGreater:
					if (insertIndex == size())
						return insertIndex - 1;
					else if (insertIndex == size())
						return index;
					else
						return insertIndex;
				case PreferLess:
					return insertIndex;
				default:
					throw new IllegalStateException("Unrecognized filter: " + filter);
				}
			}

			@Override
			public CollectionElement<E> getElement(int index) throws IndexOutOfBoundsException {
				return theElements[index];
			}

			@Override
			protected MutableCollectionElement<E> getMutableElement(int index) throws IndexOutOfBoundsException {
				if (theMutableElements == null)
					theMutableElements = new MutableCollectionElement[theValues.length];
				if (theMutableElements[index] == null)
					theMutableElements[index] = createMutableElement(index);
				return theMutableElements[index];
			}

			@Override
			public <T> T[] toArray(T[] a) {
				if (a.length < theValues.length)
					a = (T[]) Array.newInstance(a.getClass().getComponentType(), theValues.length);
				System.arraycopy(theValues, 0, a, 0, theValues.length);
				return a;
			}
		}

		static class Triple<E> extends ArrayBackedHPAS<E> {
			Triple(Comparator<? super E> sorting, Object[] values) {
				super(sorting, values);
			}

			@Override
			public int indexFor(IntUnaryOperator search) {
				int offsetPlus1 = 1;
				int comp = search.applyAsInt(1);
				if (comp == 0)
					return offsetPlus1;
				int index;
				if (comp < 0)
					index = 0;
				else
					index = 1;

				comp = search.applyAsInt(index);
				if (comp == 0)
					return index;
				else if (comp < 0) {
					return -index - 1;
				} else {
					return -index - 2;
				}
			}

			@Override
			public int indexOf(Object value) {
				int comp = theSorting.compare((E) value, (E) theValues[1]);
				if (comp == 0)
					return 1;
				else if (comp < 0) {
					if (theValues[0].equals(value))
						return 0;
					else
						return -1;
				} else {
					if (theValues[2].equals(value))
						return 2;
					else
						return -1;
				}
			}
		}

		static int searchSinglet(IntUnaryOperator search, int offset) {
			int comp = search.applyAsInt(offset);
			if (comp == 0)
				return offset;
			else if (comp < 0)
				return -offset - 1;
			else
				return -offset - 2;
		}

		static int searchInDoublet(IntUnaryOperator search, int offset) {
			int comp = search.applyAsInt(offset);
			if (comp == 0)
				return offset;
			if (comp < 0)
				return -offset - 1;
			int offsetPlus1 = offset + 1;
			comp = search.applyAsInt(offsetPlus1);
			if (comp == 0)
				return offsetPlus1;
			else if (comp < 0)
				return -offset - 2;
			else
				return -offset - 3;
		}

		static int searchInTriplet(IntUnaryOperator search, int offset) {
			int offsetPlus1 = offset + 1;
			int comp = search.applyAsInt(offset + 1);
			if (comp == 0)
				return offsetPlus1;
			int index;
			if (comp > 0)
				index = offset + 2;
			else
				index = offset;

			comp = search.applyAsInt(index);
			if (comp == 0)
				return index;
			else if (comp > 0) {
				return -index - 2;
			} else {
				return -index - 1;
			}
		}

		static class Quadruple<E> extends ArrayBackedHPAS<E> {
			Quadruple(Comparator<? super E> sorting, Object[] values) {
				super(sorting, values);
			}

			@Override
			public int indexFor(IntUnaryOperator search) {
				int comp = search.applyAsInt(1);
				if (comp == 0)
					return 1;
				else if (comp < 0)
					return searchSinglet(search, 0);
				else
					return searchInDoublet(search, 2);
			}

			@Override
			public int indexOf(Object value) {
				int comp = theSorting.compare((E) value, (E) theValues[1]);
				if (comp == 0)
					return 1;
				else if (comp < 0) {
					if (theSorting.compare((E) value, (E) theValues[0]) == 0)
						return 0;
					else
						return -1;
				}
				comp = theSorting.compare((E) value, (E) theValues[2]);
				if (comp == 0)
					return 2;
				else if (comp < 0)
					return -1;
				else if (theSorting.compare((E) value, (E) theValues[3]) == 0)
					return 3;
				else
					return -1;
			}
		}

		static class Quintuple<E> extends ArrayBackedHPAS<E> {
			Quintuple(Comparator<? super E> sorting, Object[] values) {
				super(sorting, values);
			}

			@Override
			public int indexFor(IntUnaryOperator search) {
				int comp = search.applyAsInt(2);
				if (comp == 0)
					return 2;
				else if (comp < 0)
					return searchInDoublet(search, 0);
				else
					return searchInDoublet(search, 3);
			}

			@Override
			public int indexOf(Object value) {
				int comp = theSorting.compare((E) value, (E) theValues[2]);
				if (comp == 0)
					return 2;
				else if (comp < 0) {
					comp = theSorting.compare((E) value, (E) theValues[1]);
					if (comp == 0)
						return 1;
					else if (comp > 0)
						return -1;
					else if (theValues[0].equals(value))
						return 0;
					else
						return -1;
				} else {
					comp = theSorting.compare((E) value, (E) theValues[3]);
					if (comp == 0)
						return 3;
					else if (comp < 0)
						return -1;
					else if (theValues[4].equals(value))
						return 4;
					else
						return -1;
				}
			}
		}

		static class Hextuple<E> extends ArrayBackedHPAS<E> {
			Hextuple(Comparator<? super E> sorting, Object[] values) {
				super(sorting, values);
			}

			@Override
			public int indexFor(IntUnaryOperator search) {
				int comp = search.applyAsInt(2);
				if (comp == 0)
					return 2;
				else if (comp < 0)
					return searchInDoublet(search, 0);
				else
					return searchInTriplet(search, 3);
			}

			@Override
			public int indexOf(Object value) {
				int comp = theSorting.compare((E) value, (E) theValues[2]);
				if (comp == 0)
					return 2;
				else if (comp < 0) {
					comp = theSorting.compare((E) value, (E) theValues[1]);
					if (comp == 0)
						return 1;
					else if (comp > 0)
						return -1;
					else if (theValues[0].equals(value))
						return 0;
					else
						return -1;
				} else { // It's one of elements 3, 4, or 5
					comp = theSorting.compare((E) value, (E) theValues[4]);
					if (comp == 0)
						return 4;
					else if (comp < 0) {
						if (theValues[3].equals(value))
							return 3;
						else
							return -1;
					} else if (theValues[5].equals(value))
						return 5;
					else
						return -1;
				}
			}
		}

		static class Heptuple<E> extends ArrayBackedHPAS<E> {
			Heptuple(Comparator<? super E> sorting, Object[] values) {
				super(sorting, values);
			}

			@Override
			public int indexFor(IntUnaryOperator search) {
				int comp = search.applyAsInt(3);
				if (comp == 0)
					return 3;
				else if (comp < 0)
					return searchInTriplet(search, 0);
				else
					return searchInTriplet(search, 4);
			}

			@Override
			public int indexOf(Object value) {
				int comp = theSorting.compare((E) value, (E) theValues[3]);
				if (comp == 0)
					return 3;
				else if (comp < 0) { // It's one of elements 0, 1, or 2
					comp = theSorting.compare((E) value, (E) theValues[1]);
					if (comp == 0)
						return 1;
					else if (comp < 0) {
						if (theValues[0].equals(value))
							return 0;
						else
							return -1;
					} else if (theValues[2].equals(value))
						return 2;
					else
						return -1;
				} else { // It's one of elements 4, 5, or 6
					comp = theSorting.compare((E) value, (E) theValues[5]);
					if (comp == 0)
						return 5;
					else if (comp < 0) {
						if (theValues[4].equals(value))
							return 4;
						else
							return -1;
					} else if (theValues[6].equals(value))
						return 6;
					else
						return -1;
				}
			}
		}

		static class HighPerformanceArrayMap<K, V> implements HPAMap<K, V> {
			private final HighPerformanceArraySet<K> theKeySet;
			private final Object[] theValues;
			private final Entry[] theEntries;
			private MutableMapEntryHandle<K, V>[] theMutableEntries;
			private Object theIdentity;

			HighPerformanceArrayMap(HighPerformanceArraySet<K> keySet) {
				theKeySet = keySet;
				theValues = keySet.isEmpty() ? EMPTY_VALUES : new Object[keySet.size()];
				theEntries = new HighPerformanceArrayMap.Entry[theValues.length];
			}

			@Override
			public Object getIdentity() {
				if (theIdentity == null)
					theIdentity = Identifiable.wrap(theKeySet.getIdentity(), "map", this);
				return theIdentity;
			}

			@Override
			public HighPerformanceArraySet<K> keySet() {
				return theKeySet;
			}

			@Override
			public int size() {
				return theValues.length;
			}

			@Override
			public V get(int index) {
				return (V) theValues[index];
			}

			@Override
			public V get(Object key) {
				int index = theKeySet.indexOf(key);
				return index < 0 ? null : (V) theValues[index];
			}

			@Override
			public V put(K key, V value) {
				int index = theKeySet.indexOf(key);
				if (index < 0)
					throw new IllegalArgumentException("Unrecognized key: " + key);
				V old = (V) theValues[index];
				theValues[index] = value;
				return old;
			}

			@Override
			public MapEntryHandle<K, V> getEntry(int index) {
				if (theEntries[index] == null)
					theEntries[index] = new Entry(index);
				return theEntries[index];
			}

			@Override
			public MapEntryHandle<K, V> getEntry(K key) {
				int index = theKeySet.indexOf(key);
				if (index < 0)
					return null;
				return getEntry(index);
			}

			@Override
			public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
				int index = theKeySet.getElementsBefore(entryId);
				if (index < 0)
					return null;
				return getEntry(index);
			}

			@Override
			public MapEntryHandle<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, SortedSearchFilter filter) {
				int found = theKeySet.findByIndex(index -> search.compareTo(getEntry(index)), filter);
				if (found < 0)
					return null;
				return getEntry(found);
			}

			@Override
			public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
				int index = theKeySet.getElementsBefore(entryId);
				if (theMutableEntries == null)
					theMutableEntries = new MutableMapEntryHandle[theValues.length];
				if (theMutableEntries[index] == null)
					theMutableEntries[index] = new MutableEntry(index);
				return theMutableEntries[index];
			}

			@Override
			public String canPut(K key, V value) {
				int index = theKeySet.indexOf(key);
				if (index < 0)
					return StdMsg.UNSUPPORTED_OPERATION;
				return null;
			}

			@Override
			public MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
				int index = theKeySet.indexOf(key);
				if (index < 0)
					return null;
				theValues[index] = value;
				return getEntry(index);
			}

			@Override
			public BetterSortedSet<Map.Entry<K, V>> entrySet() {
				return new EntrySet<>(this);
			}

			@Override
			public HPAMap<K, V> unmodifiable() {
				return new UnmodifiableHPAMap<>(this);
			}

			@Override
			public int hashCode() {
				return BetterMap.hashCode(this);
			}

			@Override
			public boolean equals(Object obj) {
				return BetterMap.equals(this, obj);
			}

			@Override
			public String toString() {
				return entrySet().toString();
			}

			class Entry implements MapEntryHandle<K, V> {
				K key;
				final int theIndex;

				Entry(int index) {
					theIndex = index;
				}

				@Override
				public ElementId getElementId() {
					return theKeySet.getElement(theIndex).getElementId();
				}

				@Override
				public K getKey() {
					if (key == null)
						key = theKeySet.get(theIndex);
					return key;
				}

				@Override
				public V get() {
					return (V) theValues[theIndex];
				}

				@Override
				public int hashCode() {
					return theIndex;
				}

				@Override
				public boolean equals(Object obj) {
					return this == obj;
				}

				@Override
				public String toString() {
					return getKey() + "=" + get();
				}
			}

			class MutableEntry extends Entry implements MutableMapEntryHandle<K, V> {
				MutableEntry(int index) {
					super(index);
				}

				@Override
				public BetterCollection<V> getCollection() {
					return values();
				}

				@Override
				public String isEnabled() {
					return null;
				}

				@Override
				public String isAcceptable(V value) {
					return null;
				}

				@Override
				public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
					theValues[theIndex] = value;
				}

				@Override
				public String canRemove() {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}
			}
		}

		static class UnmodifiableHPAMap<K, V> implements HPAMap<K, V> {
			private final HPAMap<K, V> theWrapped;

			UnmodifiableHPAMap(HPAMap<K, V> wrapped) {
				theWrapped = wrapped;
			}

			@Override
			public MapEntryHandle<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, SortedSearchFilter filter) {
				return theWrapped.searchEntries(search, filter);
			}

			@Override
			public String canPut(K key, V value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public MapEntryHandle<K, V> getEntry(K key) {
				return theWrapped.getEntry(key);
			}

			@Override
			public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
				return theWrapped.getEntryById(entryId);
			}

			@Override
			public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
				return new UnmodifiableMutableElement(getEntryById(entryId));
			}

			@Override
			public Object getIdentity() {
				return theWrapped.getIdentity();
			}

			@Override
			public HighPerformanceArraySet<K> keySet() {
				return theWrapped.keySet(); // High-performance array sets are immutable
			}

			@Override
			public Entry<K, V> getEntry(int index) {
				return theWrapped.getEntry(index);
			}

			@Override
			public V get(int index) {
				return theWrapped.get(index);
			}

			@Override
			public HPAMap<K, V> unmodifiable() {
				return this;
			}

			@Override
			public int hashCode() {
				return BetterMap.hashCode(this);
			}

			@Override
			public boolean equals(Object obj) {
				return BetterMap.equals(this, obj);
			}

			@Override
			public String toString() {
				return entrySet().toString();
			}

			class UnmodifiableMutableElement implements MutableMapEntryHandle<K, V> {
				private final MapEntryHandle<K, V> theWrappedElement;

				UnmodifiableMutableElement(MapEntryHandle<K, V> wrapped) {
					theWrappedElement = wrapped;
				}

				@Override
				public K getKey() {
					return theWrappedElement.getKey();
				}

				@Override
				public ElementId getElementId() {
					return theWrappedElement.getElementId();
				}

				@Override
				public V get() {
					return theWrappedElement.get();
				}

				@Override
				public BetterCollection<V> getCollection() {
					return UnmodifiableHPAMap.this.values();
				}

				@Override
				public String isEnabled() {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public String isAcceptable(V value) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
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
					return theWrappedElement.toString();
				}
			}
		}
	}
}
