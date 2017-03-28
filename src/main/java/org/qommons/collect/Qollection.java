package org.qommons.collect;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.qommons.IterableUtils;
import org.qommons.Transaction;
import org.qommons.collect.MultiMap.MultiEntry;
import org.qommons.collect.Quiterator.CollectionElement;
import org.qommons.collect.Quiterator.WrappingElement;
import org.qommons.collect.Quiterator.WrappingQuiterator;
import org.qommons.value.Settable;
import org.qommons.value.Value;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * An enhanced collection.
 * 
 * The biggest differences between Qollection and Collection are:
 * <ul>
 * <li><b>Dynamic Transformation</b> The stream api allows transforming of the content of one collection into another, but the
 * transformation is done once for all, creating a new collection independent of the source. Sometimes it is desirable to make a transformed
 * collection that does its transformation dynamically, keeping the same data source, so that when the source is modified, the transformed
 * collection is also updated accordingly. #map(Function), #filter(Function), #groupBy(Function), and others allow this. In addition, the
 * syntax of creating these dynamic transformations is much simpler and cleaner: e.g.<br />
 * &nbsp;&nbsp;&nbsp;&nbsp; <code>coll.{@link #map(Function) map}(Function)</code><br />
 * instead of<br />
 * &nbsp;&nbsp;&nbsp;&nbsp;<code>coll.stream().map(Function).collect(Collectors.toList())</code>.</li>
 * <li><b>Modification Control</b> The {@link #filterAdd(Function)} and {@link #filterRemove(Function)} methods create collections that
 * forbid certain types of modifications to a collection. The {@link #immutable(String)} prevents any API modification at all.</li>
 * <li><b>Quiterator</b> Qollections must implement {@link #spliterator()}, which returns a {@link Quiterator}, which is an enhanced
 * {@link Spliterator}. This had potential for the improved performance associated with using {@link Spliterator} instead of
 * {@link Iterator} as well as the utility added by {@link Quiterator}.</li>
 * <li><b>Transactionality</b> Qollections support the {@link org.qommons.Transactable} interface, allowing callers to reserve a collection
 * for write or to ensure that the collection is not written to during an operation (for implementations that support this. See
 * {@link org.qommons.Transactable#isLockSupported() isLockSupported()}).</li>
 * <li><b>Run-time type safety</b> Qollections have a {@link #getType() type} associated with them, allowing them to enforce type-safety at
 * run time. How strictly this type-safety is enforced is implementation-dependent.</li>
 * </ul>
 * 
 * @param <E> The type of elements in this collection
 */
public interface Qollection<E> extends TransactableCollection<E> {
	/** Standard messages returned by this class */
	interface StdMsg {
		static String BAD_TYPE = "Object is the wrong type for this collection";
		static String UNSUPPORTED_OPERATION = "Unsupported Operation";
		static String NULL_DISALLOWED = "Null is not allowed";
		static String GROUP_EXISTS = "Group already exists";
		static String WRONG_GROUP = "Item does not belong to this group";
		static String NOT_FOUND = "No such item found";
	}

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
			Quiterator<E> iter = spliterator();
			boolean[] found = new boolean[1];
			while (!found[0] && iter.tryAdvance(v -> {
				if (Objects.equals(v, o))
					found[0] = true;
			})) {
			}
			return found[0];
		}
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		if (c.isEmpty())
			return true;
		ArrayList<Object> copy = new ArrayList<>(c);
		BitSet found = new BitSet(copy.size());
		try (Transaction t = lock(false, null)) {
			Quiterator<E> iter = spliterator();
			boolean[] foundOne = new boolean[1];
			while (iter.tryAdvance(next -> {
				int stop = found.previousClearBit(copy.size());
				for (int i = found.nextClearBit(0); i < stop; i = found.nextClearBit(i + 1))
					if (Objects.equals(next, copy.get(i))) {
						found.set(i);
						foundOne[0] = true;
					}
			})) {
				if (foundOne[0] && found.cardinality() == copy.size()) {
					break;
				}
				foundOne[0] = false;
			}
			return found.cardinality() == copy.size();
		}
	}

	@Override
	default E[] toArray() {
		ArrayList<E> ret;
		try (Transaction t = lock(false, null)) {
			ret = new ArrayList<>(size());
			spliterator().forEachRemaining(v -> ret.add(v));
		}

		return ret.toArray((E[]) java.lang.reflect.Array.newInstance(getType().wrap().getRawType(), ret.size()));
	}

	@Override
	default <T> T[] toArray(T[] a) {
		ArrayList<E> ret;
		try (Transaction t = lock(false, null)) {
			ret = new ArrayList<>();
			spliterator().forEachRemaining(v -> ret.add(v));
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
	 * Searches in this collection for an element.
	 *
	 * @param filter The filter function
	 * @return A value in this list passing the filter, or null if none of this collection's elements pass.
	 */
	default Value<E> find(Predicate<E> filter) {
		return new Value<E>() {
			private final TypeToken<E> type = Qollection.this.getType().wrap();

			@Override
			public TypeToken<E> getType() {
				return type;
			}

			@Override
			public E get() {
				for (E element : Qollection.this) {
					if (filter.test(element))
						return element;
				}
				return null;
			}

			@Override
			public String toString() {
				return "find in " + Qollection.this;
			}
		};
	}

	/**
	 * @param <T> The type of values to map to
	 * @param type The run-time type of values to map to
	 * @return A builder to customize the filter/mapped collection
	 */
	default <T> MappedQollectionBuilder<E, E, T> buildMap(TypeToken<T> type) {
		return new MappedQollectionBuilder<>(this, null, type);
	}

	/**
	 * Creates a collection using the results of a {@link MappedQollectionBuilder}
	 * 
	 * @param <T> The type of values to map to
	 * @param filterMap The definition for the filter/mapping
	 * @return The filter/mapped collection
	 */
	default <T> Qollection<T> filterMap(FilterMapDef<E, ?, T> filterMap) {
		return new FilterMappedQollection<>(this, filterMap);
	}

	/**
	 * @param <T> The type of the new collection
	 * @param map The mapping function
	 * @return An observable collection of a new type backed by this collection and the mapping function
	 */
	default <T> Qollection<T> map(Function<? super E, T> map) {
		return buildMap(MappedQollectionBuilder.returnType(map)).map(map, false).build();
	}

	/**
	 * @param filter The filter function
	 * @return A collection containing all non-null elements passing the given test
	 */
	default Qollection<E> filter(Function<? super E, String> filter) {
		return this.<E> buildMap(getType()).filter(filter, false).build();
	}

	/**
	 * @param <T> The type for the new collection
	 * @param type The type to filter this collection by
	 * @return A collection backed by this collection, consisting only of elements in this collection whose values are instances of the
	 *         given class
	 */
	default <T> Qollection<T> filter(Class<T> type) {
		return buildMap(TypeToken.of(type)).filter(value -> {
			if (type == null || type.isInstance(value))
				return null;
			else
				return StdMsg.BAD_TYPE;
		}, true).build();
	}

	/**
	 * Shorthand for {@link #flatten(Qollection) flatten}({@link #map(Function) map}(Function))
	 * 
	 * @param <T> The type of the values produced
	 * @param type The type of the values produced
	 * @param map The value producer
	 * @return A qollection whose values are the accumulation of all those produced by applying the given function to all of this
	 *         collection's values
	 */
	default <T> Qollection<T> flatMap(TypeToken<T> type, Function<? super E, ? extends Qollection<? extends T>> map) {
		TypeToken<Qollection<? extends T>> qollectionType;
		if (type == null) {
			qollectionType = (TypeToken<Qollection<? extends T>>) TypeToken.of(map.getClass())
				.resolveType(Function.class.getTypeParameters()[1]);
			if (!qollectionType.isAssignableFrom(new TypeToken<Qollection<T>>() {}))
				qollectionType = new TypeToken<Qollection<? extends T>>() {};
		} else
			qollectionType = new TypeToken<Qollection<? extends T>>() {}.where(new TypeParameter<T>() {}, type);
		return flatten(this.<Qollection<? extends T>> buildMap(qollectionType).map(map, false).build());
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable collection
	 * @param arg The value to combine with each of this collection's elements
	 * @param func The combination function to apply to this collection's elements and the given value
	 * @return An observable collection containing this collection's elements combined with the given argument
	 */
	default <T, V> Qollection<V> combine(Value<T> arg, BiFunction<? super E, ? super T, V> func) {
		return combine(arg, (TypeToken<V>) TypeToken.of(func.getClass()).resolveType(BiFunction.class.getTypeParameters()[2]), func);
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable collection
	 * @param arg The value to combine with each of this collection's elements
	 * @param type The type for the new collection
	 * @param func The combination function to apply to this collection's elements and the given value
	 * @return An observable collection containing this collection's elements combined with the given argument
	 */
	default <T, V> Qollection<V> combine(Value<T> arg, TypeToken<V> type, BiFunction<? super E, ? super T, V> func) {
		return combine(arg, type, func, null);
	}

	/**
	 * @param <T> The type of the argument value
	 * @param <V> The type of the new observable collection
	 * @param arg The value to combine with each of this collection's elements
	 * @param type The type for the new collection
	 * @param func The combination function to apply to this collection's elements and the given value
	 * @param reverse The reverse function if addition support is desired for the combined collection
	 * @return An observable collection containing this collection's elements combined with the given argument
	 */
	default <T, V> Qollection<V> combine(Value<T> arg, TypeToken<V> type, BiFunction<? super E, ? super T, V> func,
		BiFunction<? super V, ? super T, E> reverse) {
		return new CombinedQollection<>(this, type, arg, func, reverse);
	}

	/**
	 * Equivalent to {@link #reduce(Object, BiFunction, BiFunction)} with null for the remove function
	 *
	 * @param <T> The type of the reduced value
	 * @param init The seed value before the reduction
	 * @param reducer The reducer function to accumulate the values. Must be associative.
	 * @return The reduced value
	 */
	default <T> Value<T> reduce(T init, BiFunction<? super T, ? super E, T> reducer) {
		return reduce(init, reducer, null);
	}

	/**
	 * Equivalent to {@link #reduce(TypeToken, Object, BiFunction, BiFunction)} using the type derived from the reducer's return type
	 *
	 * @param <T> The type of the reduced value
	 * @param init The seed value before the reduction
	 * @param add The reducer function to accumulate the values. Must be associative.
	 * @param remove The de-reducer function to handle removal or replacement of values. This may be null, in which case removal or
	 *        replacement of values will result in the entire collection being iterated over for each subscription. Null here will have no
	 *        consequence if the result is never observed. Must be associative.
	 * @return The reduced value
	 */
	default <T> Value<T> reduce(T init, BiFunction<? super T, ? super E, T> add, BiFunction<? super T, ? super E, T> remove) {
		return reduce((TypeToken<T>) TypeToken.of(add.getClass()).resolveType(BiFunction.class.getTypeParameters()[2]), init, add, remove);
	}

	/**
	 * Reduces all values in this collection to a single value
	 *
	 * @param <T> The compile-time type of the reduced value
	 * @param type The run-time type of the reduced value
	 * @param init The seed value before the reduction
	 * @param add The reducer function to accumulate the values. Must be associative.
	 * @param remove The de-reducer function to handle removal or replacement of values. This may be null, in which case removal or
	 *        replacement of values will result in the entire collection being iterated over for each subscription. Null here will have no
	 *        consequence if the result is never observed. Must be associative.
	 * @return The reduced value
	 */
	default <T> Value<T> reduce(TypeToken<T> type, T init, BiFunction<? super T, ? super E, T> add,
		BiFunction<? super T, ? super E, T> remove) {
		return new Value<T>() {
			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public T get() {
				T ret = init;
				for (E element : Qollection.this)
					ret = add.apply(ret, element);
				return ret;
			}

			@Override
			public String toString() {
				return "reduce " + Qollection.this;
			}
		};
	}

	/**
	 * @param compare The comparator to use to compare this collection's values
	 * @return An observable value containing the minimum of the values, by the given comparator
	 */
	default Value<E> minBy(Comparator<? super E> compare) {
		return reduce(getType(), null, (v1, v2) -> {
			if (v1 == null)
				return v2;
			else if (v2 == null)
				return v1;
			else if (compare.compare(v1, v2) <= 0)
				return v1;
			else
				return v2;
		}, null);
	}

	/**
	 * @param compare The comparator to use to compare this collection's values
	 * @return An observable value containing the maximum of the values, by the given comparator
	 */
	default Value<E> maxBy(Comparator<? super E> compare) {
		return reduce(getType(), null, (v1, v2) -> {
			if (v1 == null)
				return v2;
			else if (v2 == null)
				return v1;
			else if (compare.compare(v1, v2) >= 0)
				return v1;
			else
				return v2;
		}, null);
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
	default <K> SortedMultiQMap<K, E> groupBy(Function<E, K> keyMap, Comparator<? super K> compare) {
		return groupBy(null, keyMap, compare);
	}

	/**
	 * @param compare The comparator to use to group the value
	 * @return A sorted multi-map containing each of this collection's elements, each in the collection of one value that it matches
	 *         according to the comparator
	 */
	default SortedMultiQMap<E, E> groupBy(Comparator<? super E> compare) {
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
	default <K> SortedMultiQMap<K, E> groupBy(TypeToken<K> keyType, Function<E, K> keyMap, Comparator<? super K> compare) {
		return new GroupedSortedMultiMap<>(this, keyMap, keyType, compare);
	}

	/**
	 * @param modMsg The message to return when modification is requested
	 * @return An observable collection that cannot be modified directly but reflects the value of this collection as it changes
	 */
	default Qollection<E> immutable(String modMsg) {
		return new ImmutableQollection<>(this, modMsg);
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
	 * @param removeMsg The message to return when removal is requested
	 * @return The removal-disabled collection
	 */
	default Qollection<E> noRemove(String removeMsg) {
		return filterModification(value -> removeMsg, null);
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
	 * @param addMsg The message to return when addition is requested
	 * @return The addition-disabled collection
	 */
	default Qollection<E> noAdd(String addMsg) {
		return filterModification(null, value -> addMsg);
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
	 * Builds a filtered and/or mapped collection
	 * 
	 * @param <E> The type of values in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of values in the mapped collection
	 */
	class MappedQollectionBuilder<E, I, T> {
		private final Qollection<E> theWrapped;
		private final MappedQollectionBuilder<E, ?, I> theParent;
		private final TypeToken<T> theType;
		private Function<? super I, String> theFilter;
		private boolean areNullsFiltered;
		private Function<? super I, ? extends T> theMap;
		private boolean areNullsMapped;
		private Function<? super T, ? extends I> theReverse;
		private boolean areNullsReversed;

		protected MappedQollectionBuilder(Qollection<E> wrapped, MappedQollectionBuilder<E, ?, I> parent, TypeToken<T> type) {
			theWrapped = wrapped;
			theParent = parent;
			theType = type;
		}

		protected Qollection<E> getQollection() {
			return theWrapped;
		}

		protected MappedQollectionBuilder<E, ?, I> getParent() {
			return theParent;
		}

		protected TypeToken<T> getType() {
			return theType;
		}

		protected Function<? super I, String> getFilter() {
			return theFilter;
		}

		protected boolean areNullsFiltered() {
			return areNullsFiltered;
		}

		protected Function<? super I, ? extends T> getMap() {
			return theMap;
		}

		protected boolean areNullsMapped() {
			return areNullsMapped;
		}

		protected Function<? super T, ? extends I> getReverse() {
			return theReverse;
		}

		protected boolean areNullsReversed() {
			return areNullsReversed;
		}

		static <T> TypeToken<T> returnType(Function<?, ? extends T> fn) {
			return (TypeToken<T>) TypeToken.of(fn.getClass()).resolveType(Function.class.getTypeParameters()[1]);
		}

		public MappedQollectionBuilder<E, I, T> filter(Function<? super I, String> filter, boolean filterNulls) {
			theFilter = filter;
			areNullsFiltered = filterNulls;
			return this;
		}

		public MappedQollectionBuilder<E, I, T> map(Function<? super I, ? extends T> map, boolean mapNulls) {
			theMap = map;
			areNullsMapped = mapNulls;
			return this;
		}

		public MappedQollectionBuilder<E, I, T> withReverse(Function<? super T, ? extends I> reverse, boolean reverseNulls) {
			theReverse = reverse;
			areNullsReversed = reverseNulls;
			return this;
		}

		public FilterMapDef<E, I, T> toDef() {
			FilterMapDef<E, ?, I> parent = theParent == null ? null : theParent.toDef();
			TypeToken<I> intermediate = parent == null ? (TypeToken<I>) theWrapped.getType() : parent.destType;
			return new FilterMapDef<>(theWrapped.getType(), intermediate, theType, parent, theFilter, areNullsFiltered, theMap,
				areNullsMapped, theReverse, areNullsReversed);
		}

		public Qollection<T> build() {
			if (theMap == null && !theWrapped.getType().equals(theType))
				throw new IllegalStateException("Building a type-mapped collection with no map defined");
			return theWrapped.filterMap(toDef());
		}

		public <X> MappedQollectionBuilder<E, T, X> andThen(TypeToken<X> nextType) {
			return new MappedQollectionBuilder<>(theWrapped, this, nextType);
		}
	}

	/**
	 * A definition for a filter/mapped collection
	 * 
	 * @param <E> The type of values in the source collection
	 * @param <I> Intermediate type, not exposed
	 * @param <T> The type of values for the mapped collection
	 */
	class FilterMapDef<E, I, T> {
		public final TypeToken<E> sourceType;
		private final TypeToken<I> intermediateType;
		public final TypeToken<T> destType;
		private final FilterMapDef<E, ?, I> parent;
		private final Function<? super I, String> filter;
		private final boolean filterNulls;
		private final Function<? super I, ? extends T> map;
		private final boolean mapNulls;
		private final Function<? super T, ? extends I> reverse;
		private final boolean reverseNulls;

		public FilterMapDef(TypeToken<E> sourceType, TypeToken<I> intermediateType, TypeToken<T> type, FilterMapDef<E, ?, I> parent,
			Function<? super I, String> filter, boolean filterNulls, Function<? super I, ? extends T> map, boolean mapNulls,
			Function<? super T, ? extends I> reverse, boolean reverseNulls) {
			this.sourceType = sourceType;
			this.intermediateType = intermediateType;
			this.destType = type;
			this.parent = parent;
			this.filter = filter;
			this.filterNulls = filterNulls;
			this.map = map;
			this.mapNulls = mapNulls;
			this.reverse = reverse;
			this.reverseNulls = reverseNulls;

			if (parent == null && !sourceType.equals(intermediateType))
				throw new IllegalArgumentException("A " + getClass().getName()
					+ " with no parent must have identical source and intermediate types: " + sourceType + ", " + intermediateType);
		}

		public boolean checkSourceType(Object value) {
			return value == null || sourceType.getRawType().isInstance(value);
		}

		private boolean checkIntermediateType(I value) {
			return value == null || intermediateType.getRawType().isInstance(value);
		}

		public boolean checkDestType(Object value) {
			return value == null || destType.getRawType().isInstance(value);
		}

		public FilterMapResult<E, ?> checkSourceValue(FilterMapResult<E, ?> result) {
			return internalCheckSourceValue((FilterMapResult<E, I>) result);
		}

		private FilterMapResult<E, I> internalCheckSourceValue(FilterMapResult<E, I> result) {
			result.error = null;

			// Get the starting point for this def
			I interm;
			if (parent != null) {
				interm = parent.map(result).result;
				result.result = null;
				if (result.error != null)
					return result;
				if (!checkIntermediateType(interm))
					throw new IllegalStateException(
						"Implementation error: intermediate value " + interm + " is not an instance of " + intermediateType);
			} else {
				interm = (I) result.source;
				if (!checkIntermediateType(interm))
					throw new IllegalStateException("Source value " + interm + " is not an instance of " + intermediateType);
			}
			if (result.error != null) {
				return result;
			}

			// Filter
			if (filter != null) {
				if (!filterNulls && interm == null)
					result.error = StdMsg.NULL_DISALLOWED;
				else
					result.error = filter.apply(interm);
			}

			return result;
		}

		public FilterMapResult<E, T> map(FilterMapResult<E, T> result) {
			internalCheckSourceValue((FilterMapResult<E, I>) result);
			I interm = ((FilterMapResult<E, I>) result).result;

			// Map
			if (map == null)
				result.result = (T) interm;
			else if (interm == null && !mapNulls)
				result.result = null;
			else
				result.result = map.apply(interm);

			if (result.result != null && !destType.getRawType().isInstance(result.result))
				throw new IllegalStateException("Result value " + result.result + " is not an instance of " + destType);

			return result;
		}

		public boolean isReversible() {
			return map == null || reverse != null;
		}

		public FilterMapResult<T, E> reverse(FilterMapResult<T, E> result) {
			if (!isReversible())
				throw new IllegalStateException("This filter map is not reversible");

			result.error = null;

			if (!checkDestType(result.source))
				throw new IllegalStateException("Value to reverse " + result.source + " is not an instance of " + destType);
			// reverse map
			I interm;
			if (map == null)
				interm = (I) result.source;
			else if (result.source != null || reverseNulls)
				interm = reverse.apply(result.source);
			else
				interm = null;
			if (!checkIntermediateType(interm))
				throw new IllegalStateException("Reversed value " + interm + " is not an instance of " + intermediateType);

			// Filter
			if (filter != null) {
				if (!filterNulls && interm == null)
					result.error = StdMsg.NULL_DISALLOWED;
				else
					result.error = filter.apply(interm);
			}
			if (result.error != null)
				return result;

			if (parent != null) {
				((FilterMapResult<I, E>) result).source = interm;
				parent.reverse((FilterMapResult<I, E>) result);
			} else
				result.result = (E) interm;
			return result;
		}
	}

	/**
	 * Used to query {@link Qollection.FilterMapDef}
	 * 
	 * @see Qollection.FilterMapDef#checkSourceValue(FilterMapResult)
	 * @see Qollection.FilterMapDef#map(FilterMapResult)
	 * @see Qollection.FilterMapDef#reverse(FilterMapResult)
	 * 
	 * @param <E> The source type
	 * @param <T> The destination type
	 */
	class FilterMapResult<E, T> {
		public E source;
		public T result;
		public String error;

		public FilterMapResult() {}

		public FilterMapResult(E src) {
			source = src;
		}
	}

	/**
	 * Implements {@link Qollection#buildMap(TypeToken)}
	 *
	 * @param <E> The type of the collection to filter/map
	 * @param <T> The type of the filter/mapped collection
	 */
	class FilterMappedQollection<E, T> implements PartialQollectionImpl<T> {
		private final Qollection<E> theWrapped;
		private final FilterMapDef<E, ?, T> theDef;

		FilterMappedQollection(Qollection<E> wrap, FilterMapDef<E, ?, T> filterMapDef) {
			theWrapped = wrap;
			theDef = filterMapDef;
		}

		protected Qollection<E> getWrapped() {
			return theWrapped;
		}

		protected FilterMapDef<E, ?, T> getDef() {
			return theDef;
		}

		@Override
		public TypeToken<T> getType() {
			return theDef.destType;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public int size() {
			if (theDef.filter == null)
				return theWrapped.size();

			int[] size = new int[1];
			FilterMapResult<E, T> result = new FilterMapResult<>();
			theWrapped.spliterator().forEachRemaining(v -> {
				result.source = v;
				theDef.checkSourceValue(result);
				if (result.error == null)
					size[0]++;
			});
			return size[0];
		}

		@Override
		public Quiterator<T> spliterator() {
			return map(theWrapped.spliterator());
		}

		@Override
		public boolean add(T e) {
			if (!theDef.isReversible() || !theDef.checkDestType(e))
				return false;
			FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>(e));
			if (reversed.error != null)
				return false;
			return theWrapped.add(reversed.result);
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			if (!theDef.isReversible())
				return false;
			FilterMapResult<T, E> reversed = new FilterMapResult<>();
			List<E> toAdd = c.stream().flatMap(v -> {
				if (!theDef.checkDestType(v))
					return Stream.empty();
				reversed.source = v;
				theDef.reverse(reversed);
				if (reversed.error == null)
					return Stream.of(reversed.result);
				else
					return Stream.empty();
			}).collect(Collectors.toList());
			if (toAdd.isEmpty())
				return false;
			return theWrapped.addAll(toAdd);
		}

		@Override
		public boolean remove(Object o) {
			if (o != null && !theDef.checkDestType(o))
				return false;
			boolean[] found = new boolean[1];
			if (theDef.isReversible()) {
				FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>((T) o));
				if (reversed.error != null)
					return false;
				try (Transaction t = lock(true, null)) {
					while (!found[0] && theWrapped.spliterator().tryAdvanceElement(el -> {
						if (Objects.equals(el.get(), reversed.result)) {
							found[0] = true;
							el.remove();
						}
					})) {
					}
				}
			} else {
				try (Transaction t = lock(true, null)) {
					FilterMapResult<E, T> result = new FilterMapResult<>();
					while (!found[0] && theWrapped.spliterator().tryAdvanceElement(el -> {
						result.source = el.get();
						theDef.map(result);
						if (result.error == null && Objects.equals(result.result, o)) {
							found[0] = true;
							el.remove();
						}
					})) {
					}
				}
			}
			return found[0];
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean[] removed = new boolean[1];
			try (Transaction t = lock(true, null)) {
				FilterMapResult<E, T> result = new FilterMapResult<>();
				theWrapped.spliterator().forEachElement(el -> {
					result.source = el.get();
					theDef.map(result);
					if (result.error == null && c.contains(result.result)) {
						el.remove();
						removed[0] = true;
					}
				});
			}
			return removed[0];
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean[] removed = new boolean[1];
			try (Transaction t = lock(true, null)) {
				FilterMapResult<E, T> result = new FilterMapResult<>();
				theWrapped.spliterator().forEachElement(el -> {
					result.source = el.get();
					theDef.map(result);
					if (result.error == null && !c.contains(result.result)) {
						el.remove();
						removed[0] = true;
					}
				});
			}
			return removed[0];
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				FilterMapResult<E, T> result = new FilterMapResult<>();
				theWrapped.spliterator().forEachElement(el -> {
					result.source = el.get();
					theDef.checkSourceValue(result);
					if (result.error == null)
						el.remove();
				});
			}
		}

		@Override
		public String canRemove(Object value) {
			if (!theDef.isReversible())
				return StdMsg.UNSUPPORTED_OPERATION;
			else if (!theDef.checkDestType(value))
				return StdMsg.BAD_TYPE;
			FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>((T) value));
			if (reversed.error != null)
				return reversed.error;
			return theWrapped.canRemove(reversed.result);
		}

		@Override
		public String canAdd(T value) {
			if (!theDef.isReversible())
				return StdMsg.UNSUPPORTED_OPERATION;
			else if (!theDef.checkDestType(value))
				return StdMsg.BAD_TYPE;
			FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>(value));
			if (reversed.error != null)
				return reversed.error;
			return theWrapped.canAdd(reversed.result);
		}

		protected Quiterator<T> map(Quiterator<E> iter) {
			return new WrappingQuiterator<>(iter, () -> {
				CollectionElement<? extends E>[] container = new CollectionElement[1];
				FilterMapResult<E, T> mapped = new FilterMapResult<>();
				WrappingElement<E, T> wrapperEl = new WrappingElement<E, T>(getType(), container) {
					@Override
					public T get() {
						return mapped.result;
					}

					@Override
					public <V extends T> String isAcceptable(V value) {
						if (!theDef.isReversible())
							return StdMsg.UNSUPPORTED_OPERATION;
						else if (!theDef.checkDestType(value))
							return StdMsg.BAD_TYPE;
						FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>(value));
						if (reversed.error != null)
							return reversed.error;
						return ((CollectionElement<E>) getWrapped()).isAcceptable(reversed.result);
					}

					@Override
					public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
						if (!theDef.isReversible())
							throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
						else if (!theDef.checkDestType(value))
							throw new IllegalArgumentException(StdMsg.BAD_TYPE);
						FilterMapResult<T, E> reversed = theDef.reverse(new FilterMapResult<>(value));
						if (reversed.error != null)
							throw new IllegalArgumentException(reversed.error);
						((CollectionElement<E>) getWrapped()).set(reversed.result, cause);
						T old = mapped.result;
						mapped.source = reversed.result;
						mapped.result = value;
						return old;
					}
				};
				return el -> {
					mapped.source = el.get();
					theDef.map(mapped);
					if (mapped.error != null)
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
	 * Implements {@link Qollection#combine(Value, BiFunction)}
	 *
	 * @param <E> The type of the collection to be combined
	 * @param <T> The type of the value to combine the collection elements with
	 * @param <V> The type of the combined collection
	 */
	class CombinedQollection<E, T, V> implements PartialQollectionImpl<V> {
		private final Qollection<E> theWrapped;

		private final TypeToken<V> theType;
		private final Value<T> theValue;
		private final BiFunction<? super E, ? super T, V> theMap;
		private final BiFunction<? super V, ? super T, E> theReverse;

		protected CombinedQollection(Qollection<E> wrap, TypeToken<V> type, Value<T> value, BiFunction<? super E, ? super T, V> map,
			BiFunction<? super V, ? super T, E> reverse) {
			theWrapped = wrap;
			theType = type;
			theValue = value;
			theMap = map;
			theReverse = reverse;
		}

		protected Qollection<E> getWrapped() {
			return theWrapped;
		}

		protected Value<T> getValue() {
			return theValue;
		}

		protected BiFunction<? super E, ? super T, V> getMap() {
			return theMap;
		}

		protected BiFunction<? super V, ? super T, E> getReverse() {
			return theReverse;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public TypeToken<V> getType() {
			return theType;
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean add(V e) {
			if (theReverse == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else
				return theWrapped.add(theReverse.apply(e, theValue.get()));
		}

		@Override
		public boolean addAll(Collection<? extends V> c) {
			if (theReverse == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			else {
				T combineValue = theValue.get();
				return theWrapped.addAll(c.stream().map(o -> theReverse.apply(o, combineValue)).collect(Collectors.toList()));
			}
		}

		@Override
		public boolean remove(Object o) {
			try (Transaction t = lock(true, null)) {
				T combineValue = theValue.get();
				Iterator<E> iter = theWrapped.iterator();
				while (iter.hasNext()) {
					E el = iter.next();
					if (Objects.equals(getMap().apply(el, combineValue), o)) {
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
				T combineValue = theValue.get();
				Iterator<E> iter = theWrapped.iterator();
				while (iter.hasNext()) {
					E el = iter.next();
					if (c.contains(getMap().apply(el, combineValue))) {
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
				T combineValue = theValue.get();
				Iterator<E> iter = theWrapped.iterator();
				while (iter.hasNext()) {
					E el = iter.next();
					if (!c.contains(getMap().apply(el, combineValue))) {
						iter.remove();
						ret = true;
					}
				}
			}
			return ret;
		}

		@Override
		public void clear() {
			getWrapped().clear();
		}

		@Override
		public String canRemove(Object value) {
			if (theReverse != null && (value == null || theType.getRawType().isInstance(value)))
				return theWrapped.canRemove(theReverse.apply((V) value, theValue.get()));
			else
				return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public String canAdd(V value) {
			if (theReverse != null)
				return theWrapped.canAdd(theReverse.apply(value, theValue.get()));
			else
				return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public Quiterator<V> spliterator() {
			Supplier<Function<CollectionElement<? extends E>, CollectionElement<V>>> elementMap = () -> {
				CollectionElement<? extends E>[] container = new CollectionElement[1];
				WrappingElement<E, V> wrapper = new WrappingElement<E, V>(getType(), container) {
					@Override
					public V get() {
						return theMap.apply(getWrapped().get(), theValue.get());
					}

					@Override
					public <V2 extends V> String isAcceptable(V2 value) {
						if (theReverse == null)
							return StdMsg.UNSUPPORTED_OPERATION;
						E reverse = theReverse.apply(value, theValue.get());
						return ((CollectionElement<E>) getWrapped()).isAcceptable(reverse);
					}

					@Override
					public <V2 extends V> V set(V2 value, Object cause) throws IllegalArgumentException {
						if (theReverse == null)
							throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
						E reverse = theReverse.apply(value, theValue.get());
						return theMap.apply(((CollectionElement<E>) getWrapped()).set(reverse, cause), theValue.get());
					}
				};
				return el -> {
					container[0] = el;
					return wrapper;
				};
			};
			return new WrappingQuiterator<>(theWrapped.spliterator(), elementMap);
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
			return theWrapped.filter(el -> Objects.equals(theKeyMap.apply(el), key) ? null : StdMsg.GROUP_EXISTS);
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
			theElements = wrap.filter(el -> Objects.equals(theKey, theKeyMap.apply(el)) ? null : StdMsg.WRONG_GROUP);
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
		public String canRemove(Object value) {
			return theElements.canRemove(value);
		}

		@Override
		public String canAdd(E value) {
			return theElements.canAdd(value);
		}

		@Override
		public Quiterator<E> spliterator() {
			return theElements.spliterator();
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
	class GroupedSortedMultiMap<K, E> implements SortedMultiQMap<K, E> {
		private final Qollection<E> theWrapped;
		private final Function<E, K> theKeyMap;
		private final TypeToken<K> theKeyType;
		private final Comparator<? super K> theCompare;

		private final SortedQSet<K> theKeySet;

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
			theKeySet = SortedQSet.unique(mapped, theCompare);
		}

		@Override
		public Comparator<? super K> comparator() {
			return theCompare;
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
		public SortedQSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public Qollection<E> get(Object key) {
			if (!theKeyType.getRawType().isInstance(key))
				return QList.constant(getValueType());
			return theWrapped.filter(el -> theCompare.compare(theKeyMap.apply(el), (K) key) == 0 ? null : StdMsg.WRONG_GROUP);
		}

		@Override
		public SortedQSet<? extends SortedMultiQEntry<K, E>> entrySet() {
			return SortedMultiQMap.defaultEntrySet(this);
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
		private final String theModificationMessage;

		/**
		 * @param wrap The collection to wrap
		 * @param modMsg The message to return when modifications are requested
		 */
		protected ImmutableQollection(Qollection<E> wrap, String modMsg) {
			theWrapped = wrap;
			theModificationMessage = modMsg;
		}

		protected Qollection<E> getWrapped() {
			return theWrapped;
		}

		protected String getModMessage() {
			return theModificationMessage;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			if (write)
				throw new IllegalArgumentException(theModificationMessage);
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
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						throw new IllegalArgumentException(theModificationMessage);
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						return theModificationMessage;
					}

					@Override
					public Value<String> isEnabled() {
						return Value.constant(theModificationMessage);
					}

					@Override
					public String canRemove() {
						return theModificationMessage;
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
		public int size() {
			return theWrapped.size();
		}

		@Override
		public String canRemove(Object value) {
			return theModificationMessage;
		}

		@Override
		public String canAdd(E value) {
			return theModificationMessage;
		}

		@Override
		public Qollection<E> immutable(String modMsg) {
			if (modMsg.equals(theModificationMessage))
				return this;
			else
				return theWrapped.immutable(modMsg);
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
					s = StdMsg.BAD_TYPE;
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
						throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						return StdMsg.UNSUPPORTED_OPERATION;
					}

					@Override
					public Value<String> isEnabled() {
						return Value.constant(StdMsg.UNSUPPORTED_OPERATION);
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
						return StdMsg.UNSUPPORTED_OPERATION;
					}

					@Override
					public void remove() {
						throw new IllegalArgumentException(canRemove());
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
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public String canAdd(E value) {
			return StdMsg.UNSUPPORTED_OPERATION;
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
			return Transaction.NONE;
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
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						Value<? extends E> element = getWrapped().get();
						if (!(element instanceof Settable))
							throw new IllegalArgumentException(StdMsg.UNSUPPORTED_OPERATION);
						if (value != null && !element.getType().getRawType().isInstance(value))
							throw new IllegalArgumentException(StdMsg.BAD_TYPE);
						return ((Settable<E>) element).set(value, cause);
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						Value<? extends E> element = getWrapped().get();
						if (!(element instanceof Settable))
							return StdMsg.UNSUPPORTED_OPERATION;
						if (value != null && !element.getType().getRawType().isInstance(value))
							return StdMsg.BAD_TYPE;
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
									return StdMsg.UNSUPPORTED_OPERATION;
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
				return StdMsg.NOT_FOUND;
			return msg[0];
		}

		@Override
		public String canAdd(E value) {
			return StdMsg.UNSUPPORTED_OPERATION;
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
				return StdMsg.UNSUPPORTED_OPERATION;
			return current.canRemove(value);
		}

		@Override
		public String canAdd(E value) {
			Qollection<E> current = theCollectionObservable.get();
			if (current == null)
				return StdMsg.UNSUPPORTED_OPERATION;
			return current.canAdd(value);
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Qollection<? extends E> coll = theCollectionObservable.get();
			return coll == null ? Transaction.NONE : coll.lock(write, cause);
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
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						if (!getWrapped().getType().getRawType().isInstance(value))
							throw new IllegalArgumentException(StdMsg.BAD_TYPE);
						return ((CollectionElement<E>) getWrapped()).set(value, cause);
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						if (!getWrapped().getType().getRawType().isInstance(value))
							return StdMsg.BAD_TYPE;
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

		protected Qollection<? extends Qollection<? extends E>> getWrapped() {
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
		public String canRemove(Object value) {
			if (theOuter.isEmpty())
				return StdMsg.UNSUPPORTED_OPERATION;
			String msg = null;
			for (Qollection<? extends E> sub : theOuter) {
				if (sub.contains(value)) {
					String subMsg = sub.canRemove(value);
					if (subMsg == null)
						return null;
					else if (msg == null)
						msg = subMsg;
				}
			}
			return msg;
		}

		@Override
		public boolean remove(Object o) {
			if (theOuter.isEmpty())
				return false;
			for (Qollection<? extends E> sub : theOuter) {
				if (sub.remove(o))
					return true;
			}
			return false;
		}

		@Override
		public String canAdd(E value) {
			if (theOuter.isEmpty())
				return StdMsg.UNSUPPORTED_OPERATION;
			String msg = null;
			for (Qollection<? extends E> sub : theOuter) {
				if (value == null || sub.getType().getRawType().isInstance(value)) {
					String subMsg = ((OrderedQollection<E>) sub).canAdd(value);
					if (subMsg == null)
						return null;
					else if (msg == null)
						msg = subMsg;
				}
			}
			return msg;
		}

		@Override
		public boolean add(E e) {
			if (theOuter.isEmpty())
				return false;
			for (Qollection<? extends E> sub : theOuter) {
				if (e == null || sub.getType().getRawType().isInstance(e)) {
					if (((Qollection<E>) sub).add(e))
						return true;
				}
			}
			return false;
		}

		@Override
		public void clear() {
			for (Qollection<? extends E> sub : theOuter)
				sub.clear();
		}

		@Override
		public Quiterator<E> spliterator() {
			return new Quiterator<E>() {
				private final Quiterator<? extends Qollection<? extends E>> theOuterator = theOuter.spliterator();
				private WrappingQuiterator<E, E> theInnerator;
				private Supplier<Function<CollectionElement<? extends E>, CollectionElement<E>>> theElementMap;
				private AtomicInteger counted = new AtomicInteger();

				{
					theElementMap = () -> {
						CollectionElement<? extends E>[] container = new CollectionElement[1];
						WrappingElement<E, E> wrapper = new WrappingElement<E, E>(getType(), container) {
							@Override
							public E get() {
								return getWrapped().get();
							}

							@Override
							public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
								if (!getWrapped().getType().getRawType().isInstance(value))
									throw new IllegalArgumentException(StdMsg.BAD_TYPE);
								return ((CollectionElement<E>) getWrapped()).set(value, cause);
							}

							@Override
							public <V extends E> String isAcceptable(V value) {
								if (!getWrapped().getType().getRawType().isInstance(value))
									return StdMsg.BAD_TYPE;
								return ((CollectionElement<E>) getWrapped()).isAcceptable(value);
							}
						};
						return el -> {
							counted.incrementAndGet();
							container[0] = el;
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
				public void forEachElement(Consumer<? super CollectionElement<E>> action) {
					theOuterator.forEachRemaining(coll -> {
						new WrappingQuiterator<>(coll.spliterator(), theElementMap).forEachElement(action);
					});
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
