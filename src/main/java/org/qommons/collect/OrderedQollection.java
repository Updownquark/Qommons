package org.qommons.collect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.qommons.Transaction;
import org.qommons.collect.Quiterator.CollectionElement;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

/**
 * A Qollection that has a further contract that all its elements are given in a constant order (barring modifications) by {@link Iterator
 * iteration} and {@link Spliterator spliteration}
 * 
 * @param <E> The type of elements in the collection
 */
public interface OrderedQollection<E> extends Qollection<E> {
	// Additional methods

	/**
	 * @param filter The filter function
	 * @return The first value in this collection passing the filter, or null if none of this collection's elements pass
	 */
	default Value<E> findFirst(Predicate<E> filter) {
		return new OrderedCollectionFinder<>(this, filter, true);
	}

	/**
	 * @param filter The filter function
	 * @return The first value in this collection passing the filter, or null if none of this collection's elements pass
	 */
	default Value<E> findLast(Predicate<E> filter) {
		return new OrderedCollectionFinder<>(this, filter, false);
	}

	/** @return The first value in this collection, or null if this collection is empty */
	default Value<E> getFirst() {
		return new OrderedCollectionFinder<>(this, value -> true, true);
	}

	/**
	 * Finds the last value in this list. The get() method of this observable may have linear time unless this is an instance of
	 * {@link RandomAccess}
	 *
	 * @return The last value in this collection, or null if this collection is empty
	 */
	default Value<E> getLast() {
		return new OrderedCollectionFinder<>(this, value -> true, false);
	}

	/** @return The last value in this collection, or null if the collection is empty */
	default E last() {
		Object[] returned = new Object[1];
		spliterator().forEachRemaining(v -> returned[0] = v);
		return (E) returned[0];
	}

	// Ordered collections need to know the indexes of their elements in a somewhat efficient way, so these index methods make sense here

	/**
	 * @param index The index of the element to get
	 * @return The element of this collection at the given index
	 */
	default E get(int index) {
		try (Transaction t = lock(false, null)) {
			if (index < 0 || index >= size())
				throw new IndexOutOfBoundsException(index + " of " + size());
			Iterator<E> iter = iterator();
			for (int i = 0; i < index; i++)
				iter.next();
			return iter.next();
		}
	}

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the first position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	default int indexOf(Object value) {
		try (Transaction t = lock(false, null)) {
			Iterator<E> iter = iterator();
			for (int i = 0; iter.hasNext(); i++) {
				if (Objects.equals(iter.next(), value))
					return i;
			}
			return -1;
		}
	}

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the last position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	default int lastIndexOf(Object value) {
		try (Transaction t = lock(false, null)) {
			int ret = -1;
			Iterator<E> iter = iterator();
			for (int i = 0; iter.hasNext(); i++) {
				if (Objects.equals(iter.next(), value))
					ret = i;
			}
			return ret;
		}
	}

	/**
	 * @param compare The comparator to use to sort this collection's elements
	 * @return A new collection containing all the same elements as this collection, but ordered according to the given comparator
	 */
	default OrderedQollection<E> sorted(Comparator<? super E> compare) {
		return new SortedObservableCollection<>(this, compare);
	}

	// Filter/mapping

	@Override
	default <T> OrderedQollection<T> filterMap(FilterMapDef<E, ?, T> filterMap) {
		return new FilterMappedOrderedQollection<>(this, filterMap);
	}

	@Override
	default OrderedQollection<E> filter(Function<? super E, String> filter) {
		return (OrderedQollection<E>) Qollection.super.filter(filter);
	}

	@Override
	default <T> OrderedQollection<T> filter(Class<T> type) {
		return (OrderedQollection<T>) Qollection.super.filter(type);
	}

	@Override
	default <T> OrderedQollection<T> map(Function<? super E, T> map) {
		return (OrderedQollection<T>) Qollection.super.map(map);
	}

	// Combination

	@Override
	default <T, V> OrderedQollection<V> combine(Value<T> arg, BiFunction<? super E, ? super T, V> func) {
		return (OrderedQollection<V>) Qollection.super.combine(arg, func);
	}

	@Override
	default <T, V> OrderedQollection<V> combine(Value<T> arg, TypeToken<V> type, BiFunction<? super E, ? super T, V> func) {
		return (OrderedQollection<V>) Qollection.super.combine(arg, type, func);
	}

	@Override
	default <T, V> OrderedQollection<V> combine(Value<T> arg, TypeToken<V> type, BiFunction<? super E, ? super T, V> func,
		BiFunction<? super V, ? super T, E> reverse) {
		return new CombinedOrderedQollection<>(this, arg, type, func, reverse);
	}

	// Grouping

	@Override
	default <K> MultiQMap<K, E> groupBy(TypeToken<K> keyType, Function<E, K> keyMap) {
		return new GroupedOrderedMultiMap<>(this, keyMap, keyType);
	}

	// Modification control

	@Override
	default OrderedQollection<E> immutable(String modMsg) {
		return (OrderedQollection<E>) Qollection.super.immutable(modMsg);
	}

	@Override
	default OrderedQollection<E> filterRemove(Function<? super E, String> filter) {
		return (OrderedQollection<E>) Qollection.super.filterRemove(filter);
	}

	@Override
	default OrderedQollection<E> noRemove(String modMsg) {
		return (OrderedQollection<E>) Qollection.super.noRemove(modMsg);
	}

	@Override
	default OrderedQollection<E> filterAdd(Function<? super E, String> filter) {
		return (OrderedQollection<E>) Qollection.super.filterAdd(filter);
	}

	@Override
	default OrderedQollection<E> noAdd(String modMsg) {
		return (OrderedQollection<E>) Qollection.super.noAdd(modMsg);
	}

	@Override
	default OrderedQollection<E> filterModification(Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
		return new ModFilteredOrderedQollection<>(this, removeFilter, addFilter);
	}

	// Static utility methods

	/**
	 * @param <E> The type of the values
	 * @param type The type of the collection
	 * @param collection The collection
	 * @return An immutable collection with the same values as those in the given collection
	 */
	public static <E> OrderedQollection<E> constant(TypeToken<E> type, List<E> collection) {
		return new ConstantOrderedQollection<>(type, collection);
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
	 * @param <E> The type of elements held in the values
	 * @param collection The collection to flatten
	 * @return The flattened collection
	 */
	public static <E> OrderedQollection<E> flattenValues(OrderedQollection<? extends Value<? extends E>> collection) {
		return new FlattenedOrderedValuesQollection<>(collection);
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 * 
	 * @param <E> The type of values in the flattened collection
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> OrderedQollection<E> flattenValue(Value<? extends OrderedQollection<E>> collectionObservable) {
		return new FlattenedOrderedValueQollection<>(collectionObservable);
	}

	/**
	 * Flattens a collection of ordered collections
	 *
	 * @param <E> The super-type of all collections in the wrapping collection
	 * @param list The collection to flatten
	 * @return A collection containing all elements of all collections in the outer collection
	 */
	public static <E> OrderedQollection<E> flatten(OrderedQollection<? extends OrderedQollection<E>> list) {
		return new FlattenedOrderedQollection<>(list);
	}

	/**
	 * Similar to {@link #flatten(OrderedQollection)} except that the elements from the inner collections can be interspersed in the
	 * returned collection via a discriminator function. The relative ordering of each inner collection will be unchanged in the returned
	 * collection.
	 * 
	 * @param <E> The type of elements in the collection
	 * @param coll The collection of collections whose elements to intersperse
	 * @param discriminator A function that is given an element from each of the collections in the outer collection and decides which of
	 *        those elements will be the next element returned in the outer collection
	 * @return A collection containing all elements of each of the outer collection's contents, ordered by the discriminator
	 */
	public static <E> OrderedQollection<E> intersperse(OrderedQollection<? extends OrderedQollection<? extends E>> coll,
		Function<? super List<E>, Integer> discriminator) {
		return new InterspersedQollection<>(coll, discriminator);
	}

	// Implementation member classes

	/**
	 * Finds something in an {@link OrderedQollection}
	 *
	 * @param <E> The type of value to find
	 */
	class OrderedCollectionFinder<E> implements Value<E> {
		private final OrderedQollection<E> theCollection;
		private final TypeToken<E> theType;
		private final Predicate<? super E> theFilter;
		private final boolean isForward;

		OrderedCollectionFinder(OrderedQollection<E> collection, Predicate<? super E> filter, boolean forward) {
			theCollection = collection;
			theType = theCollection.getType().wrap();
			theFilter = filter;
			isForward = forward;
		}

		/** @return The collection that this finder searches */
		public OrderedQollection<E> getCollection() {
			return theCollection;
		}

		/** @return The function to test elements with */
		public Predicate<? super E> getFilter() {
			return theFilter;
		}

		/** @return Whether this finder searches forward or backward in the collection */
		public boolean isForward() {
			return isForward;
		}

		@Override
		public TypeToken<E> getType() {
			return theType;
		}

		@Override
		public E get() {
			if (isForward) {
				for (E element : theCollection) {
					if (theFilter.test(element))
						return element;
				}
				return null;
			} else {
				E ret = null;
				for (E element : theCollection) {
					if (theFilter.test(element))
						ret = element;
				}
				return ret;
			}
		}
	}

	/**
	 * Implements {@link OrderedQollection#filterMap(FilterMapDef)}
	 *
	 * @param <E> The type of the collection to be filter-mapped
	 * @param <T> The type of the mapped collection
	 */
	class FilterMappedOrderedQollection<E, T> extends FilterMappedQollection<E, T> implements OrderedQollection<T> {
		FilterMappedOrderedQollection(OrderedQollection<E> wrap, FilterMapDef<E, ?, T> def) {
			super(wrap, def);
		}

		@Override
		protected OrderedQollection<E> getWrapped() {
			return (OrderedQollection<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link OrderedQollection#combine(Value, BiFunction)}
	 *
	 * @param <E> The type of the collection to be combined
	 * @param <T> The type of the value to combine the collection elements with
	 * @param <V> The type of the combined collection
	 */
	class CombinedOrderedQollection<E, T, V> extends CombinedQollection<E, T, V> implements OrderedQollection<V> {
		CombinedOrderedQollection(OrderedQollection<E> collection, Value<T> value, TypeToken<V> type,
			BiFunction<? super E, ? super T, V> map, BiFunction<? super V, ? super T, E> reverse) {
			super(collection, type, value, map, reverse);
		}

		@Override
		protected OrderedQollection<E> getWrapped() {
			return (OrderedQollection<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link OrderedQollection#groupBy(Function)}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class GroupedOrderedMultiMap<K, V> extends GroupedMultiMap<K, V> {
		public GroupedOrderedMultiMap(OrderedQollection<V> wrap, Function<V, K> keyMap, TypeToken<K> keyType) {
			super(wrap, keyMap, keyType);
		}

		@Override
		protected QSet<K> unique(Qollection<K> keyCollection) {
			return OrderedQSet.unique((OrderedQollection<K>) keyCollection);
		}
	}

	/**
	 * Implements {@link OrderedQollection#sorted(Comparator)}
	 *
	 * @param <E> The type of the elements in the collection
	 */
	class SortedObservableCollection<E> implements PartialQollectionImpl<E>, OrderedQollection<E> {
		private final OrderedQollection<E> theWrapped;
		private final Comparator<? super E> theCompare;

		public SortedObservableCollection(OrderedQollection<E> wrap, Comparator<? super E> compare) {
			theWrapped = wrap;
			theCompare = compare;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		/** @return The comparator sorting this collection's elements */
		public Comparator<? super E> comparator() {
			return theCompare;
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
		public String canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public String canAdd(E value) {
			return theWrapped.canAdd(value);
		}

		@Override
		public Quiterator<E> spliterator() {
			// TODO Any way to do this better?
			ArrayList<E> sorted;

			try (Transaction t = theWrapped.lock(true, null)) {
				sorted = new ArrayList<>(theWrapped.size());
				sorted.addAll(theWrapped);
			}
			Collections.sort(sorted, theCompare);
			Supplier<Function<E, CollectionElement<E>>> elementMap = () -> {
				Object[] value = new Object[1];
				CollectionElement<E> element = new CollectionElement<E>() {
					@Override
					public TypeToken<E> getType() {
						return theWrapped.getType();
					}

					@Override
					public E get() {
						return (E) value[0];
					}

					@Override
					public Value<String> isEnabled() {
						return Value.constant("Replacement is not enabled for this collection");
					}

					@Override
					public <V extends E> String isAcceptable(V value2) {
						// Logically this would be allowable if value2 is comparably equivalent to this value, but there's not replacement
						// mechanism
						return "Replacement is not enabled for this collection";
					}

					@Override
					public <V extends E> E set(V value2, Object cause) throws IllegalArgumentException {
						throw new IllegalArgumentException("Replacement is not enabled for this collection");
					}

					@Override
					public String canRemove() {
						if (theWrapped.indexOf(value[0]) == theWrapped.lastIndexOf(value[0]))
							return theWrapped.canRemove(value[0]);
						else
							// If there are more than one copy of this value, there's no way to tell if the collection would let us remove
							// them all
							return "More than one of this value is present in the collection";
					}

					@Override
					public void remove() throws IllegalArgumentException {
						do {
							theWrapped.remove(value[0]);
						} while (theWrapped.contains(value[0]));
					}
				};
				return v -> {
					value[0] = v;
					return element;
				};
			};
			return new Quiterator.SimpleQuiterator<E, E>(sorted.spliterator(), theWrapped.getType(), elementMap) {};
		}

		@Override
		public String toString() {
			return Qollection.toString(this);
		}
	}

	/**
	 * Implements {@link OrderedQollection#filterModification(Function, Function)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class ModFilteredOrderedQollection<E> extends ModFilteredQollection<E> implements OrderedQollection<E> {
		public ModFilteredOrderedQollection(OrderedQollection<E> wrapped, Function<? super E, String> removeFilter,
			Function<? super E, String> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected OrderedQollection<E> getWrapped() {
			return (OrderedQollection<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link OrderedQollection#constant(TypeToken, List)}
	 * 
	 * @param <E> The type of elements in the collection
	 */
	class ConstantOrderedQollection<E> extends ConstantQollection<E> implements OrderedQollection<E> {
		public ConstantOrderedQollection(TypeToken<E> type, List<E> collection) {
			super(type, collection);
		}
	}

	/**
	 * Implements {@link OrderedQollection#flattenValues(OrderedQollection)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedOrderedValuesQollection<E> extends FlattenedValuesQollection<E> implements OrderedQollection<E> {
		protected FlattenedOrderedValuesQollection(OrderedQollection<? extends Value<? extends E>> collection) {
			super(collection);
		}

		@Override
		protected OrderedQollection<? extends Value<? extends E>> getWrapped() {
			return (OrderedQollection<? extends Value<? extends E>>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link OrderedQollection#flattenValue(Value)}
	 *
	 * @param <E> The type of elements in the collection
	 */
	class FlattenedOrderedValueQollection<E> extends FlattenedValueQollection<E> implements OrderedQollection<E> {
		public FlattenedOrderedValueQollection(Value<? extends OrderedQollection<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected Value<? extends OrderedQollection<E>> getWrapped() {
			return (Value<? extends OrderedQollection<E>>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link OrderedQollection#flatten(OrderedQollection)}
	 *
	 * @param <E> The type of the collection
	 */
	class FlattenedOrderedQollection<E> extends FlattenedQollection<E> implements OrderedQollection<E> {
		protected FlattenedOrderedQollection(OrderedQollection<? extends OrderedQollection<? extends E>> outer) {
			super(outer);
		}

		@Override
		protected OrderedQollection<? extends OrderedQollection<? extends E>> getWrapped() {
			return (OrderedQollection<? extends OrderedQollection<? extends E>>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			int passed = 0;
			try (Transaction t = lock(false, null)) {
				for (OrderedQollection<? extends E> coll : getWrapped()) {
					int size = coll.size();
					if (passed + size > index)
						return coll.get(index - passed);
					passed += size;
				}
			}
			throw new IndexOutOfBoundsException(index + " of " + passed);
		}

		@Override
		public int indexOf(Object value) {
			int passed = 0;
			try (Transaction t = lock(false, null)) {
				for (OrderedQollection<? extends E> coll : getWrapped()) {
					int index = coll.indexOf(value);
					if (index >= 0)
						return passed + index;
					else
						passed += coll.size();
				}
			}
			return -1;
		}

		@Override
		public int lastIndexOf(Object value) {
			int passed = 0;
			int lastIndex = -1;
			try (Transaction t = lock(false, null)) {
				for (OrderedQollection<? extends E> coll : getWrapped()) {
					int index = coll.lastIndexOf(value);
					if (index >= 0)
						lastIndex = passed + index;
					passed += coll.size();
				}
			}
			return lastIndex;
		}
	}

	/**
	 * Implements {@link OrderedQollection#intersperse(OrderedQollection, Function)}
	 * 
	 * @param <E> The type of elements in the collection
	 */
	class InterspersedQollection<E> extends FlattenedQollection<E> implements OrderedQollection<E> {
		private final Function<? super List<E>, Integer> theDiscriminator;

		public InterspersedQollection(OrderedQollection<? extends OrderedQollection<? extends E>> coll,
			Function<? super List<E>, Integer> discriminator) {
			super(coll);
			theDiscriminator = discriminator;
		}

		@Override
		protected OrderedQollection<? extends OrderedQollection<? extends E>> getWrapped() {
			return (OrderedQollection<? extends OrderedQollection<? extends E>>) super.getWrapped();
		}

		protected Function<? super List<E>, Integer> getDiscriminator() {
			return theDiscriminator;
		}

		@Override
		public Quiterator<E> spliterator() {
			ArrayList<Quiterator<? extends E>> colls = new ArrayList<>();
			getWrapped().spliterator().forEachRemaining(coll -> colls.add(coll.spliterator()));
			colls.trimToSize();
			List<CollectionElement<? extends E>> elements = new ArrayList<>(colls.size());
			List<E> values = new ArrayList<>(colls.size());
			List<E> immutableValues = Collections.unmodifiableList(values);
			CollectionElement<? extends E>[] container = new CollectionElement[1];
			Quiterator.WrappingElement<E, E> wrapper = new Quiterator.WrappingElement<E, E>(getType(), container) {
				@Override
				public E get() {
					return getWrapped().get();
				}

				@Override
				public <V extends E> String isAcceptable(V value) {
					if (value != null && !getWrapped().getType().getRawType().isInstance(value))
						return StdMsg.BAD_TYPE;
					else
						return ((CollectionElement<E>) getWrapped()).isAcceptable(value);
				}

				@Override
				public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
					if (value != null && !getWrapped().getType().getRawType().isInstance(value))
						throw new IllegalArgumentException(StdMsg.BAD_TYPE);
					else
						return ((CollectionElement<E>) getWrapped()).set(value, cause);
				}
			};
			return new Quiterator<E>() {
				@Override
				public TypeToken<E> getType() {
					return InterspersedQollection.this.getType();
				}

				@Override
				public long estimateSize() {
					return size();
				}

				@Override
				public int characteristics() {
					return Spliterator.SIZED | Spliterator.ORDERED;
				}

				@Override
				public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
					if (colls.isEmpty())
						return false;
					if (elements.isEmpty()) {
						// Need to initialize
						Iterator<Quiterator<? extends E>> iter = colls.iterator();
						while (iter.hasNext()) {
							Quiterator<? extends E> q = iter.next();
							if (!q.tryAdvanceElement(el -> {
								elements.add(el);
								values.add(el.get());
							})) {
								iter.remove();
							}
						}
					}
					int nextIndex = theDiscriminator.apply(immutableValues);
					if (nextIndex < 0 || nextIndex >= values.size())
						throw new IndexOutOfBoundsException(nextIndex + " of " + values.size());
					container[0] = elements.get(nextIndex);
					if (!colls.get(nextIndex).tryAdvanceElement(el -> {
						elements.set(nextIndex, el);
						values.set(nextIndex, el.get());
					})) {
						colls.remove(nextIndex);
						elements.remove(nextIndex);
						values.remove(nextIndex);
					}
					action.accept(wrapper);
					return true;
				}

				@Override
				public void forEachElement(Consumer<? super CollectionElement<E>> action) {
					try (Transaction t = lock(true, null)) {
						Quiterator.super.forEachElement(action);
					}
				}

				@Override
				public void forEachRemaining(Consumer<? super E> action) {
					try (Transaction t = lock(false, null)) {
						Quiterator.super.forEachRemaining(action);
					}
				}

				@Override
				public Quiterator<E> trySplit() {
					return null;
				}
			};
		}
	}
}
