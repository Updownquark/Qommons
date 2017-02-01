package org.qommons.collect;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.qommons.IterableUtils;
import org.qommons.Transaction;
import org.qommons.collect.MultiMap.MultiEntry;
import org.qommons.collect.Quiterator.CollectionElement;
import org.qommons.collect.Quiterator.WrappingElement;
import org.qommons.collect.Quiterator.WrappingQuiterator;
import org.qommons.value.Settable;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

import javafx.collections.ObservableList;

public interface Qollection<E> extends TransactableCollection<E> {
	/** @return The run-time type of elements in this collection */
	TypeToken<E> getType();

	@Override
	abstract Quiterator<E> spliterator();

	@Override
	default Iterator<E> iterator() {
		return new QuiteratorIterator<>(spliterator());
	}

	@Override
	default boolean isEmpty() {
		return size() == 0;
	}

	@Override
	default boolean contains(Object o) {
		try (Transaction t = lock(false, null)) {
			for (Object value : this)
				if (Objects.equals(value, o))
					return true;
			return false;
		}
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		if (c.isEmpty())
			return true;
		ArrayList<Object> copy = new ArrayList<>(c);
		BitSet found = new BitSet(copy.size());
		try (Transaction t = lock(false, null)) {
			Iterator<E> iter = iterator();
			while (iter.hasNext()) {
				E next = iter.next();
				int stop = found.previousClearBit(copy.size());
				for (int i = found.nextClearBit(0); i < stop; i = found.nextClearBit(i + 1))
					if (Objects.equals(next, copy.get(i)))
						found.set(i);
			}
			return found.cardinality() == copy.size();
		}
	}

	@Override
	default E[] toArray() {
		ArrayList<E> ret = new ArrayList<>();
		try (Transaction t = lock(false, null)) {
			for (E value : this)
				ret.add(value);
		}

		return ret.toArray((E[]) java.lang.reflect.Array.newInstance(getType().wrap().getRawType(), ret.size()));
	}

	@Override
	default <T> T[] toArray(T[] a) {
		ArrayList<E> ret = new ArrayList<>();
		try (Transaction t = lock(false, null)) {
			for (E value : this)
				ret.add(value);
		}
		return ret.toArray(a);
	}

	/**
	 * @param values The values to add to the collection
	 * @return This collection
	 */
	public default Qollection<E> addValues(E... values) {
		try (Transaction t = lock(true, null)) {
			for (E value : values)
				add(value);
		}
		return this;
	}

	/** @return A value for the size of this collection */
	default Value<Integer> sizeValue() {
		return new Value<Integer>() {
			private final TypeToken<Integer> intType = TypeToken.of(Integer.TYPE);

			@Override
			public TypeToken<Integer> getType() {
				return intType;
			}

			@Override
			public Integer get() {
				return size();
			}

			@Override
			public String toString() {
				return Qollection.this + ".size()";
			}
		};
	}

	/**
	 * @param <T> The type of the new collection
	 * @param map The mapping function
	 * @return An observable collection of a new type backed by this collection and the mapping function
	 */
	default <T> Qollection<T> map(Function<? super E, T> map) {
		return map((TypeToken<T>) TypeToken.of(map.getClass()).resolveType(Function.class.getTypeParameters()[1]), map, null, false);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type for the mapped collection (may be null)
	 * @param map The mapping function to map the elements of this collection
	 * @return The mapped collection
	 */
	default <T> Qollection<T> map(TypeToken<T> type, Function<? super E, T> map) {
		return map(type, map, null, false);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type for the mapped collection (may be null)
	 * @param map The mapping function to map the elements of this collection
	 * @param reverse The reverse function if addition support is desired for the mapped collection
	 * @return The mapped collection
	 */
	default <T> Qollection<T> map(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse) {
		return map(type, map, reverse, false);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type for the mapped collection (may be null)
	 * @param map The mapping function to map the elements of this collection
	 * @param reverse The reverse function if addition support is desired for the mapped collection
	 * @param filterNulls Whether to apply the map to null values. If this is false and the value is null, a null element will be in the
	 *        resulting collection.
	 * @return The mapped collection
	 */
	default <T> Qollection<T> map(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse, boolean filterNulls) {
		return filterMap2(type, v -> new FilterMapResult<>(v == null && !filterNulls ? null : map.apply(v), null), reverse);
	}

	/**
	 * @param filter The filter function
	 * @return A collection containing all non-null elements passing the given test
	 */
	default Qollection<E> filter(Function<? super E, String> filter) {
		return filterMap2(getType(), v -> new FilterMapResult<>(v, filter.apply(v)), null);
	}

	/**
	 * @param <T> The type for the new collection
	 * @param type The type to filter this collection by
	 * @return A collection backed by this collection, consisting only of elements in this collection whose values are instances of the
	 *         given class
	 */
	default <T> Qollection<T> filter(Class<T> type) {
		return filterMap2(TypeToken.of(type), value -> {
			if (type.isInstance(value))
				return new FilterMapResult<>(type.cast(value), null);
			else
				return new FilterMapResult<>(null, value.getClass().getName() + " is not an instance of " + type.getName());
		}, value -> (E) value);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param filterMap The mapping function
	 * @return An observable collection of a new type backed by this collection and the mapping function
	 */
	default <T> Qollection<T> filterMap(Function<? super E, T> filterMap) {
		return filterMap((TypeToken<T>) TypeToken.of(filterMap.getClass()).resolveType(Function.class.getTypeParameters()[1]), filterMap,
			null);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type of the mapped collection
	 * @param map The mapping function
	 * @param reverse The reverse function if addition support is desired for the mapped collection
	 * @return A collection containing every element in this collection for which the mapping function returns a non-null value
	 */
	default <T> Qollection<T> filterMap(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse) {
		return filterMap2(type, value -> {
			T mapped = map.apply(value);
			return new FilterMapResult<>(mapped, mapped == null ? "Value " + mapped + " is not allowed in the collection" : null);
		}, reverse);
	}

	/**
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type of the mapped collection
	 * @param map The filter/mapping function
	 * @param reverse The reverse function if addition support is desired for the mapped collection
	 * @return A collection containing every element in this collection for which the mapping function returns a passing value
	 */
	default <T> Qollection<T> filterMap2(TypeToken<T> type, Function<? super E, FilterMapResult<T>> map, Function<? super T, E> reverse) {
		if (type == null)
			type = (TypeToken<T>) TypeToken.of(map.getClass()).resolveType(Function.class.getTypeParameters()[1]);
		return new FilterMappedQollection<>(this, type, map, reverse);
	}

	/**
	 * @param <K> The type of the key
	 * @param keyMap The mapping function to group this collection's values by
	 * @return A multi-map containing each of this collection's elements, each in the collection of the value mapped by the given function
	 *         applied to the element
	 */
	default <K> MultiQMap<K, E> groupBy(Function<E, K> keyMap) {
		return groupBy((TypeToken<K>) TypeToken.of(keyMap.getClass()).resolveType(Function.class.getTypeParameters()[1]), keyMap);
	}

	/**
	 * @param <K> The type of the key
	 * @param keyType The type of the key
	 * @param keyMap The mapping function to group this collection's values by
	 * @param equalizer The equalizer to use to group the keys
	 * @return A multi-map containing each of this collection's elements, each in the collection of the value mapped by the given function
	 *         applied to the element
	 */
	default <K> MultiQMap<K, E> groupBy(TypeToken<K> keyType, Function<E, K> keyMap) {
		return new GroupedMultiMap<>(this, keyMap, keyType);
	}

	/**
	 * @param <K> The type of the key
	 * @param keyMap The mapping function to group this collection's values by
	 * @param compare The comparator to use to sort the keys
	 * @return A sorted multi-map containing each of this collection's elements, each in the collection of the value mapped by the given
	 *         function applied to the element
	 */
	default <K> MultiSortedQMap<K, E> groupBy(Function<E, K> keyMap, Comparator<? super K> compare) {
		return groupBy(null, keyMap, compare);
	}

	/**
	 * @param compare The comparator to use to group the value
	 * @return A sorted multi-map containing each of this collection's elements, each in the collection of one value that it matches
	 *         according to the comparator
	 */
	default MultiSortedQMap<E, E> groupBy(Comparator<? super E> compare) {
		return groupBy(getType(), null, compare);
	}

	/**
	 * TODO TEST ME!
	 *
	 * @param <K> The type of the key
	 * @param keyType The type of the key
	 * @param keyMap The mapping function to group this collection's values by
	 * @param compare The comparator to use to sort the keys
	 * @return A sorted multi-map containing each of this collection's elements, each in the collection of the value mapped by the given
	 *         function applied to the element
	 */
	default <K> MultiSortedQMap<K, E> groupBy(TypeToken<K> keyType, Function<E, K> keyMap, Comparator<? super K> compare) {
		return new GroupedSortedMultiMap<>(this, keyMap, keyType, compare);
	}

	/** @return An observable collection that cannot be modified directly but reflects the value of this collection as it changes */
	default Qollection<E> immutable() {
		return new ImmutableQollection<>(this);
	}

	/**
	 * Creates a wrapper collection for which removals are filtered
	 *
	 * @param filter The filter to check removals with
	 * @return The removal-filtered collection
	 */
	default Qollection<E> filterRemove(Function<? super E, String> filter) {
		return filterModification(filter, null);
	}

	/**
	 * Creates a wrapper collection for which removals are rejected with an {@link IllegalStateException}
	 *
	 * @return The removal-disabled collection
	 */
	default Qollection<E> noRemove() {
		return filterModification(value -> {
			throw new IllegalStateException("This collection does not allow removal");
		}, null);
	}

	/**
	 * Creates a wrapper collection for which additions are filtered
	 *
	 * @param filter The filter to check additions with
	 * @return The addition-filtered collection
	 */
	default Qollection<E> filterAdd(Function<? super E, String> filter) {
		return filterModification(null, filter);
	}

	/**
	 * Creates a wrapper collection for which additions are rejected with an {@link IllegalStateException}
	 *
	 * @return The addition-disabled collection
	 */
	default Qollection<E> noAdd() {
		return filterModification(null, value -> {
			throw new IllegalStateException("This collection does not allow addition");
		});
	}

	/**
	 * Creates a wrapper around this collection that can filter items that are attempted to be added or removed from it. If the filter
	 * returns true, the addition/removal is allowed. If it returns false, the addition/removal is silently rejected. The filter is also
	 * allowed to throw an exception, in which case the operation as a whole will fail. In the case of batch operations like
	 * {@link #addAll(Collection) addAll} or {@link #removeAll(Collection) removeAll}, if the filter throws an exception on any item, the
	 * collection will not be changed. Note that for filters that can return false, silently failing to add or remove items may break the
	 * contract for the collection type.
	 *
	 * @param removeFilter The filter to test items being removed from the collection. If null, removals will not be filtered and will all
	 *        pass.
	 * @param addFilter The filter to test items being added to the collection. If null, additions will not be filtered and will all pass
	 * @return The controlled collection
	 */
	default Qollection<E> filterModification(Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
		return new ModFilteredQollection<>(this, removeFilter, addFilter);
	}

	/**
	 * Tests the removability of an element from this collection. This method exposes a "best guess" on whether an element in the collection
	 * could be removed, but does not provide any guarantee. This method should return true for any object for which {@link #remove(Object)}
	 * is successful, but the fact that an object passes this test does not guarantee that it would be removed successfully. E.g. the
	 * position of the element in the collection may be a factor, but may not be tested for here.
	 *
	 * @param value The value to test removability for
	 * @return Null if given value could possibly be removed from this collection, or a message why it can't
	 */
	String canRemove(Object value);

	/**
	 * Tests the compatibility of an object with this collection. This method exposes a "best guess" on whether an element could be added to
	 * the collection , but does not provide any guarantee. This method should return true for any object for which {@link #add(Object)} is
	 * successful, but the fact that an object passes this test does not guarantee that it would be removed successfully. E.g. the position
	 * of the element in the collection may be a factor, but is tested for here.
	 *
	 * @param value The value to test compatibility for
	 * @return Null if given value could possibly be added to this collection, or a message why it can't
	 */
	String canAdd(E value);

	/**
	 * @param <E> The type of the values
	 * @param type The type of the collection
	 * @param collection The collection
	 * @return An immutable collection with the same values as those in the given collection
	 */
	public static <E> Qollection<E> constant(TypeToken<E> type, Collection<E> collection) {
		return new ConstantQollection<>(type, collection);
	}

	/**
	 * @param <E> The type of the values
	 * @param type The type of the collection
	 * @param values The values for the new collection
	 * @return An immutable collection with the same values as those in the given collection
	 */
	public static <E> Qollection<E> constant(TypeToken<E> type, E... values) {
		return constant(type, java.util.Arrays.asList(values));
	}

	/**
	 * Turns a collection of observable values into a collection composed of those holders' values
	 *
	 * @param <T> The type of elements held in the values
	 * @param collection The collection to flatten
	 * @return The flattened collection
	 */
	public static <T> Qollection<T> flattenValues(Qollection<? extends Value<T>> collection) {
		return new FlattenedValuesQollection<>(collection);
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 * 
	 * @param <E> The type of values in the collection
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> Qollection<E> flattenValue(Value<? extends Qollection<E>> collectionObservable) {
		return new FlattenedValueQollection<>(collectionObservable);
	}

	/**
	 * @param <E> The super-type of elements in the inner collections
	 * @param coll The collection to flatten
	 * @return A collection containing all elements of all collections in the outer collection
	 */
	public static <E> Qollection<E> flatten(Qollection<? extends Qollection<? extends E>> coll) {
		return new FlattenedQollection<>(coll);
	}

	/**
	 * @param <T> An observable collection that contains all elements the given collections
	 * @param colls The collections to flatten
	 * @return A collection containing all elements of the given collections
	 */
	public static <T> Qollection<T> flattenCollections(Qollection<? extends T>... colls) {
		return flatten(constant(new TypeToken<Qollection<? extends T>>() {}, colls));
	}

	/**
	 * A simple toString implementation for collections
	 *
	 * @param coll The collection to print
	 * @return The string representation of the collection's contents
	 */
	public static String toString(TransactableCollection<?> coll) {
		StringBuilder ret = new StringBuilder("(");
		boolean first = true;
		try (Transaction t = coll.lock(false, null)) {
			for (Object value : coll) {
				if (!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
		}
		ret.append(')');
		return ret.toString();
	}

	/**
	 * An iterator backed by a Quiterator
	 * 
	 * @param <E> The type of elements to iterate over
	 */
	class QuiteratorIterator<E> implements Iterator<E> {
		private final Quiterator<E> theQuiterator;

		private boolean isNextCached;
		private boolean isDone;
		private CollectionElement<? extends E> cachedNext;

		public QuiteratorIterator(Quiterator<E> quiterator) {
			theQuiterator = quiterator;
		}

		@Override
		public boolean hasNext() {
			cachedNext = null;
			if (!isNextCached && !isDone) {
				if (theQuiterator.tryAdvanceElement(element -> {
					cachedNext = element;
				}))
					isNextCached = true;
				else
					isDone = true;
			}
			return isNextCached;
		}

		@Override
		public E next() {
			if (!hasNext())
				throw new java.util.NoSuchElementException();
			isNextCached = false;
			return cachedNext.get();
		}

		@Override
		public void remove() {
			if (cachedNext == null)
				throw new IllegalStateException(
					"First element has not been read, element has already been removed, or iterator has finished");
			if (isNextCached)
				throw new IllegalStateException("remove() must be called after next() and before the next call to hasNext()");
			cachedNext.remove();
			cachedNext = null;
		}

		@Override
		public void forEachRemaining(Consumer<? super E> action) {
			if (isNextCached)
				action.accept(next());
			cachedNext = null;
			isDone = true;
			theQuiterator.forEachRemaining(action);
		}
	}

	/**
	 * An extension of Qollection that implements some of the redundant methods and throws UnsupportedOperationExceptions for modifications.
	 * Mostly copied from {@link java.util.AbstractCollection}.
	 *
	 * @param <E> The type of element in the collection
	 */
	interface PartialQollectionImpl<E> extends Qollection<E> {
		@Override
		default boolean add(E e) {
			throw new UnsupportedOperationException(getClass().getName() + " does not implement add(value)");
		}

		@Override
		default boolean addAll(Collection<? extends E> c) {
			try (Transaction t = lock(true, null)) {
				boolean modified = false;
				for (E e : c)
					if (add(e))
						modified = true;
				return modified;
			}
		}

		@Override
		default boolean remove(Object o) {
			try (Transaction t = lock(true, null)) {
				Iterator<E> it = iterator();
				while (it.hasNext()) {
					if (Objects.equals(it.next(), o)) {
						it.remove();
						return true;
					}
				}
				return false;
			}
		}

		@Override
		default boolean removeAll(Collection<?> c) {
			if (c.isEmpty())
				return false;
			try (Transaction t = lock(true, null)) {
				boolean modified = false;
				Iterator<?> it = iterator();
				while (it.hasNext()) {
					if (c.contains(it.next())) {
						it.remove();
						modified = true;
					}
				}
				return modified;
			}
		}

		@Override
		default boolean retainAll(Collection<?> c) {
			if (c.isEmpty()) {
				clear();
				return false;
			}
			try (Transaction t = lock(true, null)) {
				boolean modified = false;
				Iterator<E> it = iterator();
				while (it.hasNext()) {
					if (!c.contains(it.next())) {
						it.remove();
						modified = true;
					}
				}
				return modified;
			}
		}

		@Override
		default void clear() {
			try (Transaction t = lock(true, null)) {
				Iterator<E> it = iterator();
				while (it.hasNext()) {
					it.next();
					it.remove();
				}
			}
		}
	}

	/**
	 * The result of a filter/map operation
	 *
	 * @param <T> The type of the mapped value
	 */
	class FilterMapResult<T> {
		/** The mapped result */
		public final T mapped;
		/** Whether the value passed the filter */
		public final String rejected;

		/**
		 * @param _mapped The mapped result
		 * @param _passed Null if the value passed the filter, or a message saying why it didn't
		 */
		public FilterMapResult(T _mapped, String _passed) {
			mapped = _mapped;
			rejected = _passed;
		}
	}

	/**
	 * Implements {@link Qollection#filterMap(TypeToken, Function, Function)}
	 *
	 * @param <E> The type of the collection to filter/map
	 * @param <T> The type of the filter/mapped collection
	 */
	class FilterMappedQollection<E, T> implements PartialQollectionImpl<T> {
		private final Qollection<E> theWrapped;
		private final TypeToken<T> theType;
		private final Function<? super E, FilterMapResult<T>> theMap;
		private final Function<? super T, E> theReverse;

		FilterMappedQollection(Qollection<E> wrap, TypeToken<T> type, Function<? super E, FilterMapResult<T>> map,
			Function<? super T, E> reverse) {
			theWrapped = wrap;
			theType = type != null ? type : (TypeToken<T>) TypeToken.of(map.getClass()).resolveType(Function.class.getTypeParameters()[1]);
			theMap = map;
			theReverse = reverse;
		}

		protected Qollection<E> getWrapped() {
			return theWrapped;
		}

		protected Function<? super E, FilterMapResult<T>> getMap() {
			return theMap;
		}

		protected Function<? super T, E> getReverse() {
			return theReverse;
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public int size() {
			int ret = 0;
			for (E el : theWrapped)
				if (theMap.apply(el).rejected == null)
					ret++;
			return ret;
		}

		@Override
		public Quiterator<T> spliterator() {
			return filter(theWrapped.spliterator());
		}

		@Override
		public boolean add(T e) {
			if (theReverse == null)
				return PartialQollectionImpl.super.add(e);
			else {
				E reversed = theReverse.apply(e);
				FilterMapResult<T> res = theMap.apply(reversed);
				if (res.rejected != null)
					throw new IllegalArgumentException(res.rejected);
				return theWrapped.add(reversed);
			}
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			if (theReverse == null)
				return PartialQollectionImpl.super.addAll(c);
			else {
				List<E> toAdd = c.stream().map(theReverse).collect(Collectors.toList());
				for (E value : toAdd) {
					String pass = theMap.apply(value).rejected;
					if (pass != null)
						throw new IllegalArgumentException(pass);
				}
				return theWrapped.addAll(toAdd);
			}
		}

		@Override
		public boolean remove(Object o) {
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while (iter.hasNext()) {
					E el = iter.next();
					FilterMapResult<T> mapped = getMap().apply(el);
					if (mapped.rejected == null && Objects.equals(mapped.mapped, o)) {
						iter.remove();
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean ret = false;
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while (iter.hasNext()) {
					E el = iter.next();
					FilterMapResult<T> mapped = getMap().apply(el);
					if (mapped.rejected == null && c.contains(mapped.mapped)) {
						iter.remove();
						ret = true;
					}
				}
			}
			return ret;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean ret = false;
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while (iter.hasNext()) {
					E el = iter.next();
					FilterMapResult<T> mapped = getMap().apply(el);
					if (mapped.rejected == null && !c.contains(mapped.mapped)) {
						iter.remove();
						ret = true;
					}
				}
			}
			return ret;
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = getWrapped().iterator();
				while (iter.hasNext()) {
					E el = iter.next();
					FilterMapResult<T> mapped = getMap().apply(el);
					if (mapped.rejected == null) {
						iter.remove();
					}
				}
			}
		}

		@Override
		public String canRemove(Object value) {
			if (theReverse == null)
				return "Removal is not enabled for this collection";
			else if (value == null || theType.getRawType().isInstance(value))
				return theWrapped.canRemove(theReverse.apply((T) value));
			else
				return "Value of type " + value.getClass().getName() + " cannot exist in a collection of type " + theType;
		}

		@Override
		public String canAdd(T value) {
			if (theReverse != null)
				return theWrapped.canAdd(theReverse.apply(value));
			else
				return "Addition is not enabled for this collection";
		}

		protected Quiterator<T> filter(Quiterator<E> iter) {
			return new WrappingQuiterator<>(iter, () -> {
				CollectionElement<? extends E>[] container = new CollectionElement[1];
				FilterMapResult<T> [] result=new FilterMapResult[1];
				Object [] wrapped=new Object[1];
				WrappingElement<E, T> wrapperEl = new WrappingElement<E, T>(getType(), container) {
					@Override
					public T get() {
						return result[0].mapped;
					}

					@Override
					public String canAdd(T toAdd) {
						if(theReverse==null)
							return "Addition is not enabled for this collection";
						E reverse = theReverse.apply(toAdd);
						FilterMapResult<T> res = theMap.apply(reverse);
						if (res.rejected != null)
							return res.rejected;
						if (!getWrapped().getType().getRawType().isInstance(reverse))
							return "The given value is not acceptable for this collection";
						return ((CollectionElement<E>) getWrapped()).canAdd(reverse);
					}

					@Override
					public void add(T toAdd) {
						if(theReverse==null)
							throw new IllegalArgumentException("Addition is not enabled for this collection");
						E reverse = theReverse.apply(toAdd);
						FilterMapResult<T> res = theMap.apply(reverse);
						if (res.rejected != null)
							throw new IllegalArgumentException(res.rejected);
						if (!getWrapped().getType().getRawType().isInstance(reverse))
							throw new IllegalArgumentException("The given value is not acceptable for this collection");
						((CollectionElement<E>) getWrapped()).add(reverse);
					}

					@Override
					public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
						if(theReverse==null)
							throw new IllegalArgumentException("Replacement is not enabled for this collection");
						E reverse=theReverse.apply(value);
						FilterMapResult<T> res=theMap.apply(reverse);
						if (res.rejected != null)
							throw new IllegalArgumentException(res.rejected);
						if (!getWrapped().getType().getRawType().isInstance(reverse))
							throw new IllegalArgumentException("The given value is not acceptable for this collection");
						((CollectionElement<E>) getWrapped()).set(reverse, cause);
						wrapped[0] = reverse;
						FilterMapResult<T> old = result[0];
						result[0] = res;
						return old.mapped;
					}

					@Override
					public <V extends T> String isAcceptable(V value) {
						if (theReverse == null)
							return "Replacement is not enabled for this collection";
						E reverse = theReverse.apply(value);
						FilterMapResult<T> res = theMap.apply(reverse);
						if (res.rejected != null)
							return res.rejected;
						if (!getWrapped().getType().getRawType().isInstance(reverse))
							return "The given value is not acceptable for this collection";
						return ((CollectionElement<E>) getWrapped()).isAcceptable(theReverse.apply(value));
					}
				};
				return el -> {
					wrapped[0]=el.get();
					result[0]=theMap.apply((E)wrapped[0]);
					if (result[0].rejected == null)
						return null;
					container[0] = el;
					return wrapperEl;
				};

			});
		}

		@Override
		public String toString() {
			return Qollection.toString(this);
		}
	}

	/**
	 * Implements {@link Qollection#groupBy(Function)}
	 *
	 * @param <K> The key type of the map
	 * @param <E> The value type of the map
	 */
	class GroupedMultiMap<K, E> implements MultiQMap<K, E> {
		private final Qollection<E> theWrapped;
		private final Function<E, K> theKeyMap;
		private final TypeToken<K> theKeyType;

		private final QSet<K> theKeySet;

		GroupedMultiMap(Qollection<E> wrap, Function<E, K> keyMap, TypeToken<K> keyType) {
			theWrapped = wrap;
			theKeyMap = keyMap;
			theKeyType = keyType != null ? keyType
				: (TypeToken<K>) TypeToken.of(keyMap.getClass()).resolveType(Function.class.getTypeParameters()[1]);

			Qollection<K> mapped;
			if (theKeyMap != null)
				mapped = theWrapped.map(theKeyMap);
			else
				mapped = (Qollection<K>) theWrapped;
			theKeySet = unique(mapped);
		}

		protected QSet<K> unique(Qollection<K> keyCollection) {
			return QSet.unique(keyCollection);
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theKeyType;
		}

		@Override
		public TypeToken<E> getValueType() {
			return theWrapped.getType();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public QSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public Qollection<E> get(Object key) {
			return theWrapped.filter(el -> Objects.equals(theKeyMap.apply(el), key) ? null : "");
		}

		@Override
		public QSet<? extends MultiQEntry<K, E>> entrySet() {
			return MultiQMap.defaultEntrySet(this);
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}

	/**
	 * An entry in a {@link Qollection.GroupedMultiMap}
	 *
	 * @param <K> The key type of the entry
	 * @param <E> The value type of the entry
	 */
	class GroupedMultiEntry<K, E> implements MultiQMap.MultiQEntry<K, E> {
		private final K theKey;

		private final Function<E, K> theKeyMap;

		private final Qollection<E> theElements;

		GroupedMultiEntry(K key, Qollection<E> wrap, Function<E, K> keyMap) {
			theKey = key;
			theKeyMap = keyMap;
			theElements = wrap.filter(el -> Objects.equals(theKey, theKeyMap.apply(el)));
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public TypeToken<E> getType() {
			return theElements.getType();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return theElements.onElement(onElement);
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theElements.lock(write, cause);
		}

		@Override
		public int size() {
			return theElements.size();
		}

		@Override
		public Iterator<E> iterator() {
			return theElements.iterator();
		}

		@Override
		public boolean add(E e) {
			return theElements.add(e);
		}

		@Override
		public boolean remove(Object o) {
			return theElements.remove(o);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return theElements.addAll(c);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theElements.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theElements.retainAll(c);
		}

		@Override
		public void clear() {
			theElements.clear();
		}

		@Override
		public boolean canRemove(Object value) {
			return theElements.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			return theElements.canAdd(value);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			return o instanceof MultiEntry && Objects.equals(theKey, ((MultiEntry<?, ?>) o).getKey());
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theKey);
		}

		@Override
		public String toString() {
			return getKey() + "=" + Qollection.toString(this);
		}
	}

	/**
	 * Implements {@link Qollection#groupBy(Function, Comparator)}
	 *
	 * @param <K> The key type of the map
	 * @param <E> The value type of the map
	 */
	class GroupedSortedMultiMap<K, E> implements ObservableSortedMultiMap<K, E> {
		private final Qollection<E> theWrapped;
		private final Function<E, K> theKeyMap;
		private final TypeToken<K> theKeyType;
		private final Comparator<? super K> theCompare;

		private final ObservableSortedSet<K> theKeySet;

		GroupedSortedMultiMap(Qollection<E> wrap, Function<E, K> keyMap, TypeToken<K> keyType, Comparator<? super K> compare) {
			theWrapped = wrap;
			theKeyMap = keyMap;
			theKeyType = keyType != null ? keyType
				: (TypeToken<K>) TypeToken.of(keyMap.getClass()).resolveType(Function.class.getTypeParameters()[1]);
			theCompare = compare;

			Qollection<K> mapped;
			if (theKeyMap != null)
				mapped = theWrapped.map(theKeyMap);
			else
				mapped = (Qollection<K>) theWrapped;
			theKeySet = unique(mapped);
		}

		@Override
		public Comparator<? super K> comparator() {
			return theCompare;
		}

		protected ObservableSortedSet<K> unique(Qollection<K> keyCollection) {
			return ObservableSortedSet.unique(keyCollection, theCompare);
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theKeyType;
		}

		@Override
		public TypeToken<E> getValueType() {
			return theWrapped.getType();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public Qollection<E> get(Object key) {
			if (!theKeyType.getRawType().isInstance(key))
				return ObservableList.constant(getValueType());
			return theWrapped.filter(el -> theCompare.compare(theKeyMap.apply(el), (K) key) == 0);
		}

		@Override
		public ObservableSortedSet<? extends ObservableSortedMultiEntry<K, E>> entrySet() {
			return ObservableSortedMultiMap.defaultEntrySet(this);
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}

	/**
	 * A collection that cannot be modified directly, but reflects the values in a wrapped collection
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ImmutableQollection<E> implements PartialQollectionImpl<E> {
		private final Qollection<E> theWrapped;

		/** @param wrap The collection to wrap */
		protected ImmutableQollection(Qollection<E> wrap) {
			theWrapped = wrap;
		}

		protected Qollection<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			if (write)
				throw new IllegalArgumentException("Immutable collections cannot be locked for writing");
			return theWrapped.lock(false, cause);
		}

		@Override
		public Quiterator<E> spliterator() {
			return new WrappingQuiterator<>(theWrapped.spliterator(), () -> {
				CollectionElement<E>[] container = new CollectionElement[1];
				WrappingElement<E, E> wrapperEl = new WrappingElement<E, E>(getType(), container) {
					@Override
					public E get() {
						return getWrapped().get();
					}

					@Override
					public String canAdd(E toAdd) {
						return "Addition is not enabled for this collection";
					}

					@Override
					public void add(E toAdd) {
						throw new IllegalArgumentException(canAdd(toAdd));
					}

					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						throw new IllegalArgumentException("Replacement is not enabled for this collection");
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						return "Replacement is not enabled for this collection";
					}

					@Override
					public Value<String> isEnabled() {
						return Value.constant("Replacement is not enabled for this collection");
					}

					@Override
					public String canRemove() {
						return "Removal is not enabled for this collection";
					}

					@Override
					public void remove() {
						throw new IllegalArgumentException(canRemove());
					}
				};
				return el -> {
					container[0] = (CollectionElement<E>) el;
					return wrapperEl;
				};
			});
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public Iterator<E> iterator() {
			return org.qommons.IterableUtils.immutableIterator(theWrapped.iterator());
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public String canRemove(Object value) {
			return "Removal is not enabled for this collection";
		}

		@Override
		public String canAdd(E value) {
			return "Addition is not enabled for this collection";
		}

		@Override
		public ImmutableQollection<E> immutable() {
			return this;
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link Qollection#filterModification(Function, Function)}
	 *
	 * @param <E> The type of the collection to control
	 */
	class ModFilteredQollection<E> implements PartialQollectionImpl<E> {
		private final Qollection<E> theWrapped;

		private final Function<? super E, String> theRemoveFilter;
		private final Function<? super E, String> theAddFilter;

		public ModFilteredQollection(Qollection<E> wrapped, Function<? super E, String> removeFilter,
			Function<? super E, String> addFilter) {
			theWrapped = wrapped;
			theRemoveFilter = removeFilter;
			theAddFilter = addFilter;
		}

		protected Qollection<E> getWrapped() {
			return theWrapped;
		}

		protected Function<? super E, String> getRemoveFilter() {
			return theRemoveFilter;
		}

		protected Function<? super E, String> getAddFilter() {
			return theAddFilter;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public Quiterator<E> spliterator() {
			return new WrappingQuiterator<>(theWrapped.spliterator(), () -> {
				CollectionElement<E>[] container = new CollectionElement[1];
				WrappingElement<E, E> wrapperEl = new WrappingElement<E, E>(getType(), container) {
					@Override
					public E get() {
						return getWrapped().get();
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						String s = null;
						if (theAddFilter != null)
							s = theAddFilter.apply(value);
						if (s == null)
							s = ((CollectionElement<E>) getWrapped()).isAcceptable(value);
						return s;
					}

					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						if (theAddFilter != null) {
							String s = theAddFilter.apply(value);
							if (s != null)
								throw new IllegalArgumentException(s);
						}
						return ((CollectionElement<E>) getWrapped()).set(value, cause);
					}

					@Override
					public String canRemove() {
						String s = null;
						if (theRemoveFilter != null)
							s = theRemoveFilter.apply(get());
						if (s == null)
							s = getWrapped().canRemove();
						return s;
					}

					@Override
					public void remove() {
						if (theRemoveFilter != null) {
							String s = theRemoveFilter.apply(get());
							if (s != null)
								throw new IllegalArgumentException(s);
						}
						getWrapped().remove();
					}

					@Override
					public String canAdd(E toAdd) {
						return isAcceptable(toAdd);
					}

					@Override
					public void add(E toAdd) {
						if (theAddFilter != null) {
							String s = theAddFilter.apply(toAdd);
							if (s != null)
								throw new IllegalArgumentException(s);
						}
						((CollectionElement<E>) getWrapped()).add(toAdd);
					}
				};
				return el -> {
					container[0] = (CollectionElement<E>) el;
					return wrapperEl;
				};
			});
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean add(E value) {
			if (theAddFilter == null || theAddFilter.apply(value) == null)
				return theWrapped.add(value);
			else
				return false;
		}

		@Override
		public boolean addAll(Collection<? extends E> values) {
			if (theAddFilter != null)
				return theWrapped.addAll(values.stream().filter(v -> theAddFilter.apply(v) == null).collect(Collectors.toList()));
			else
				return theWrapped.addAll(values);
		}

		@Override
		public boolean remove(Object value) {
			if (theRemoveFilter == null)
				return theWrapped.remove(value);

			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while (iter.hasNext()) {
					E next = iter.next();
					if (!Objects.equals(next, value))
						continue;
					if (theRemoveFilter.apply(next) == null) {
						iter.remove();
						return true;
					} else
						return false;
				}
			}
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> values) {
			if (theRemoveFilter == null)
				return theWrapped.removeAll(values);

			BitSet remove = new BitSet();
			int i = 0;
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while (iter.hasNext()) {
					E next = iter.next();
					if (!values.contains(next))
						continue;
					if (theRemoveFilter.apply(next) == null)
						remove.set(i);
					i++;
				}

				if (!remove.isEmpty()) {
					i = 0;
					iter = theWrapped.iterator();
					while (iter.hasNext()) {
						iter.next();
						if (remove.get(i))
							iter.remove();
						i++;
					}
				}
			}
			return !remove.isEmpty();
		}

		@Override
		public boolean retainAll(Collection<?> values) {
			if (theRemoveFilter == null)
				return theWrapped.retainAll(values);

			BitSet remove = new BitSet();
			int i = 0;
			try (Transaction t = lock(true, null)) {
				Iterator<E> iter = theWrapped.iterator();
				while (iter.hasNext()) {
					E next = iter.next();
					if (values.contains(next))
						continue;
					if (theRemoveFilter.apply(next) == null)
						remove.set(i);
					i++;
				}

				if (!remove.isEmpty()) {
					i = 0;
					iter = theWrapped.iterator();
					while (iter.hasNext()) {
						iter.next();
						if (remove.get(i))
							iter.remove();
						i++;
					}
				}
			}
			return !remove.isEmpty();
		}

		@Override
		public void clear() {
			if (theRemoveFilter == null) {
				theWrapped.clear();
				return;
			}

			BitSet remove = new BitSet();
			int i = 0;
			Iterator<E> iter = theWrapped.iterator();
			while (iter.hasNext()) {
				E next = iter.next();
				if (theRemoveFilter.apply(next) == null)
					remove.set(i);
				i++;
			}

			i = 0;
			iter = theWrapped.iterator();
			while (iter.hasNext()) {
				iter.next();
				if (remove.get(i))
					iter.remove();
				i++;
			}
		}

		@Override
		public String canRemove(Object value) {
			String s = null;
			if (theRemoveFilter != null) {
				if (value != null && !theWrapped.getType().getRawType().isInstance(value))
					s = "Value of type " + value.getClass().getName() + " cannot be removed from collection of type " + getType();
				if (s == null)
					s = theRemoveFilter.apply((E) value);
			}
			if (s == null)
				s = theWrapped.canRemove(value);
			return s;
		}

		@Override
		public String canAdd(E value) {
			String s = null;
			if (theAddFilter != null)
				s = theAddFilter.apply(value);
			if (s == null)
				s = theWrapped.canAdd(value);
			return s;
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link Qollection#constant(TypeToken, Collection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ConstantQollection<E> implements PartialQollectionImpl<E> {
		private final TypeToken<E> theType;
		private final Collection<E> theCollection;

		public ConstantQollection(TypeToken<E> type, Collection<E> collection) {
			theType = type;
			theCollection = collection;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public Quiterator<E> spliterator() {
			Supplier<? extends Function<? super E, ? extends CollectionElement<E>>> fn;
			fn = () -> {
				Object[] elementValue = new Object[1];
				CollectionElement<E> el = new CollectionElement<E>() {
					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						throw new IllegalArgumentException("This collection cannot be modified");
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						return "This collection cannot be modified";
					}

					@Override
					public Value<String> isEnabled() {
						return Value.constant("This collection cannot be modified");
					}

					@Override
					public TypeToken<E> getType() {
						return ConstantQollection.this.getType();
					}

					@Override
					public E get() {
						return (E) elementValue[0];
					}

					@Override
					public String canRemove() {
						return "This collection cannot be modified";
					}

					@Override
					public void remove() {
						throw new IllegalArgumentException(canRemove());
					}

					@Override
					public String canAdd(E toAdd) {
						return "This collection cannot be modified";
					}

					@Override
					public void add(E toAdd) {
						throw new IllegalArgumentException(canAdd(toAdd));
					}
				};
				return v -> {
					elementValue[0] = v;
					return el;
				};
			};
			return new Quiterator.SimpleQuiterator<>(theCollection.spliterator(), fn);
		}

		@Override
		public String canRemove(Object value) {
			return "Removal is not enabled for this collection";
		}

		@Override
		public String canAdd(E value) {
			return "Addition is not enabled for this collection";
		}

		@Override
		public int size() {
			return theCollection.size();
		}

		@Override
		public Iterator<E> iterator() {
			return IterableUtils.immutableIterator(theCollection.iterator());
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return () -> {
			};
		}
	}

	/**
	 * Implements {@link Qollection#flattenValues(Qollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedValuesQollection<E> implements PartialQollectionImpl<E> {
		private Qollection<? extends Value<? extends E>> theCollection;
		private final TypeToken<E> theType;

		protected FlattenedValuesQollection(Qollection<? extends Value<? extends E>> collection) {
			theCollection = collection;
			theType = (TypeToken<E>) theCollection.getType().resolveType(Value.class.getTypeParameters()[0]);
		}

		/** @return The collection of values that this collection flattens */
		protected Qollection<? extends Value<? extends E>> getWrapped() {
			return theCollection;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theCollection.lock(write, cause);
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public Quiterator<E> spliterator() {
			Supplier<Function<CollectionElement<? extends Value<? extends E>>, CollectionElement<E>>> fn;
			fn = () -> {
				CollectionElement<Value<? extends E>>[] container = new CollectionElement[1];
				WrappingElement<Value<? extends E>, E> wrapper = new WrappingElement<Value<? extends E>, E>(getType(), container) {
					@Override
					public String canAdd(E toAdd) {
						return "Addition is not enabled for this collection";
					}

					@Override
					public void add(E toAdd) {
						throw new IllegalArgumentException(canAdd(toAdd));
					}

					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						Value<? extends E> element = getWrapped().get();
						if (!(value instanceof Settable))
							throw new IllegalArgumentException("Replacement is not enabled for this element");
						if (value != null && !element.getType().getRawType().isInstance(value))
							throw new IllegalArgumentException("Value of type " + value.getClass().getName()
								+ " cannot be assigned to this element of type " + element.getType());
						return ((Settable<E>) element).set(value, cause);
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						Value<? extends E> element = getWrapped().get();
						if (!(value instanceof Settable))
							return "Replacement is not enabled for this element";
						if (value != null && !element.getType().getRawType().isInstance(value))
							return "Value of type " + value.getClass().getName() + " cannot be assigned to this element of type "
								+ element.getType();
						return ((Settable<E>) element).isAcceptable(value);
					}

					@Override
					public E get() {
						return getWrapped().get().get();
					}

					@Override
					public Value<String> isEnabled() {
						Value<? extends E> element = getWrapped().get();
						return new Value<String>() {
							@Override
							public TypeToken<String> getType() {
								return TypeToken.of(String.class);
							}

							@Override
							public String get() {
								if (!(element instanceof Settable))
									return "Replacement is not enabled for this element";
								return ((Settable<? extends E>) element).isEnabled().get();
							}
						};
					}
				};
				return el -> {
					container[0] = (CollectionElement<Value<? extends E>>) el;
					return wrapper;
				};
			};
			return new WrappingQuiterator<Value<? extends E>, E>(theCollection.spliterator(), fn);
		}

		@Override
		public String canRemove(Object value) {
			boolean[] found = new boolean[1];
			String[] msg = new String[1];
			Quiterator<? extends Value<? extends E>> iter = theCollection.spliterator();
			while (!found[0] && iter.tryAdvance(v -> {
				if (Objects.equals(v.get(), value)) {
					found[0] = true;
					msg[0] = ((Qollection<Value<? extends E>>) theCollection).canRemove(v);
				}
			})) {
			}
			if (!found[0])
				return "No such element in collection";
			return msg[0];
		}

		@Override
		public String canAdd(E value) {
			return "Addition is not enabled for this collection";
		}

		@Override
		public int size() {
			return theCollection.size();
		}

		@Override
		public String toString() {
			return Qollection.toString(this);
		}
	}

	/**
	 * Implements {@link Qollection#flattenValue(Value)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedValueQollection<E> implements PartialQollectionImpl<E> {
		private final Value<? extends Qollection<E>> theCollectionObservable;
		private final TypeToken<E> theType;

		protected FlattenedValueQollection(Value<? extends Qollection<E>> collectionObservable) {
			theCollectionObservable = collectionObservable;
			theType = (TypeToken<E>) theCollectionObservable.getType().resolveType(Qollection.class.getTypeParameters()[0]);
		}

		protected Value<? extends Qollection<E>> getWrapped() {
			return theCollectionObservable;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public int size() {
			Qollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? 0 : coll.size();
		}

		@Override
		public Iterator<E> iterator() {
			Qollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? Collections.EMPTY_LIST.iterator() : (Iterator<E>) coll.iterator();
		}

		@Override
		public String canRemove(Object value) {
			Qollection<E> current = theCollectionObservable.get();
			if (current == null)
				return "Removal is not currently enabled for this collection";
			return current.canRemove(value);
		}

		@Override
		public String canAdd(E value) {
			Qollection<E> current = theCollectionObservable.get();
			if (current == null)
				return "Addition is not currently enabled for this collection";
			return current.canAdd(value);
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Qollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? () -> {
			} : coll.lock(write, cause);
		}

		@Override
		public Quiterator<E> spliterator() {
			Qollection<? extends E> coll = theCollectionObservable.get();
			if (coll == null) {
				return new Quiterator<E>() {
					@Override
					public long estimateSize() {
						return 0;
					}

					@Override
					public int characteristics() {
						return Spliterator.IMMUTABLE | Spliterator.SIZED;
					}

					@Override
					public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
						return false;
					}

					@Override
					public Quiterator<E> trySplit() {
						return null;
					}
				};
			}
			Supplier<Function<CollectionElement<? extends E>, CollectionElement<E>>> fn;
			fn = () -> {
				CollectionElement<? extends E>[] container = new CollectionElement[1];
				WrappingElement<E, E> wrappingEl = new WrappingElement<E, E>(getType(), container) {
					@Override
					public String canAdd(E toAdd) {
						if (!getWrapped().getType().getRawType().isInstance(toAdd))
							return "Value's type is invalid for this collection";
						return ((CollectionElement<E>) getWrapped()).canAdd(toAdd);
					}

					@Override
					public void add(E toAdd) {
						if (!getWrapped().getType().getRawType().isInstance(toAdd))
							throw new IllegalArgumentException("Value's type is invalid for this collection");
						((CollectionElement<E>) getWrapped()).add(toAdd);
					}

					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						if (!getWrapped().getType().getRawType().isInstance(value))
							throw new IllegalArgumentException("Value's type is invalid for this collection");
						return ((CollectionElement<E>) getWrapped()).set(value, cause);
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						if (!getWrapped().getType().getRawType().isInstance(value))
							return "Value's type is invalid for this collection";
						return ((CollectionElement<E>) getWrapped()).isAcceptable(value);
					}

					@Override
					public E get() {
						return getWrapped().get();
					}
				};
				return el -> {
					container[0] = el;
					return wrappingEl;
				};
			};
			return new WrappingQuiterator<>(coll.spliterator(), fn);
		}

		@Override
		public String toString() {
			return Qollection.toString(this);
		}
	}

	/**
	 * Implements {@link Qollection#flatten(Qollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedQollection<E> implements PartialQollectionImpl<E> {
		private final Qollection<? extends Qollection<? extends E>> theOuter;
		private final TypeToken<E> theType;

		protected FlattenedQollection(Qollection<? extends Qollection<? extends E>> collection) {
			theOuter = collection;
			theType = (TypeToken<E>) theOuter.getType().resolveType(Qollection.class.getTypeParameters()[0]);
		}

		protected Qollection<? extends Qollection<? extends E>> getOuter() {
			return theOuter;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction outer = theOuter.lock(write, cause);
			Transaction[] inner = new Transaction[theOuter.size()];
			int[] i = new int[1];
			theOuter.spliterator().forEachRemaining(coll -> inner[i[0]++] = coll.lock(write, cause));
			return () -> {
				for (int j = 0; j < inner.length; j++)
					inner[j].close();
				outer.close();
			};
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public int size() {
			int ret = 0;
			for (Qollection<? extends E> subColl : theOuter)
				ret += subColl.size();
			return ret;
		}

		@Override
		public Iterator<E> iterator() {
			return (Iterator<E>) IterableUtils.flatten(theOuter).iterator();
		}

		@Override
		public String canRemove(Object value) {
			return "Removal is not enabled for this collection";
		}

		@Override
		public String canAdd(E value) {
			return "Addition is not enabled for this collection";
		}

		@Override
		public Quiterator<E> spliterator() {
			return new Quiterator<E>() {
				private final Quiterator<? extends Qollection<? extends E>> theOuterator = theOuter.spliterator();
				private WrappingQuiterator<E, E> theInnerator;
				private Supplier<Function<CollectionElement<? extends E>, CollectionElement<E>>> theElementMap;
				private AtomicInteger counted = new AtomicInteger();
				
				{
					theElementMap=()->{
						CollectionElement<? extends E> [] container=new CollectionElement[1];
						WrappingElement<E, E> wrapper = new WrappingElement<E, E>(getType(), container) {
							@Override
							public E get() {
								return getWrapped().get();
							}

							@Override
							public String canAdd(E toAdd) {
								if (!getWrapped().getType().getRawType().isInstance(toAdd))
									return "Value of type " + toAdd + " is not acceptable for this element";
								return ((CollectionElement<E>) getWrapped()).canAdd(toAdd);
							}

							@Override
							public void add(E toAdd) throws IllegalArgumentException {
								if (!getWrapped().getType().getRawType().isInstance(toAdd))
									throw new IllegalArgumentException("Value of type " + toAdd + " is not acceptable for this element");
								((CollectionElement<E>) getWrapped()).add(toAdd);
							}

							@Override
							public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
								if (!getWrapped().getType().getRawType().isInstance(value))
									throw new IllegalArgumentException("Value of type " + value + " is not acceptable for this element");
								return ((CollectionElement<E>) getWrapped()).set(value, cause);
							}

							@Override
							public <V extends E> String isAcceptable(V value) {
								if (!getWrapped().getType().getRawType().isInstance(value))
									return "Value of type " + value + " is not acceptable for this element";
								return ((CollectionElement<E>) getWrapped()).isAcceptable(value);
							}
						};
						return el->{
							counted.incrementAndGet();
							container[0]=el;
							return wrapper;
						};
					};
				}
				
				@Override
				public long estimateSize() {
					return size() - counted.get();
				}

				@Override
				public int characteristics() {
					return Spliterator.SIZED;
				}

				@Override
				public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
					if (theInnerator == null
						&& !theOuterator.tryAdvance(coll -> theInnerator = new WrappingQuiterator<>(coll.spliterator(), theElementMap)))
						return false;
					while (!theInnerator.tryAdvanceElement(action)) {
						if (!theOuterator.tryAdvance(coll -> theInnerator = new WrappingQuiterator<>(coll.spliterator(), theElementMap)))
							return false;
					}
					return true;
				}

				@Override
				public Quiterator<E> trySplit() {
					Quiterator<E>[] ret = new Quiterator[1];
					theOuterator.tryAdvance(coll -> {
						counted.addAndGet(coll.size());
						ret[0] = new WrappingQuiterator<>(coll.spliterator(), theElementMap);
					});
					return ret[0];
				}
			};
		}

		@Override
		public String toString() {
			return Qollection.toString(this);
		}
	}
}
