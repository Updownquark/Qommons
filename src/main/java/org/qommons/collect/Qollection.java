package org.qommons.collect;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.observe.Subscription;
import org.observe.collect.ObservableCollection.ImmutableObservableCollection;
import org.observe.collect.ObservableElement;
import org.qommons.Transaction;
import org.qommons.collect.Quiterator.CollectionElement;

import com.google.common.reflect.TypeToken;

public interface Qollection<E> extends Collection<E>, TransactableCollection<E> {
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
		return filterMap2(type, v -> new FilterMapResult<>(v == null && !filterNulls ? null : map.apply(v), true), reverse);
	}

	/**
	 * @param filter The filter function
	 * @return A collection containing all non-null elements passing the given test
	 */
	default Qollection<E> filter(Predicate<? super E> filter) {
		return filterMap2(getType(), v -> new FilterMapResult<>(v, filter.test(v)), null);
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
				return new FilterMapResult<>(type.cast(value), true);
			else
				return new FilterMapResult<>(null, false);
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
			return new FilterMapResult<>(mapped, mapped != null);
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

	/** @return An observable collection that cannot be modified directly but reflects the value of this collection as it changes */
	default Qollection<E> immutable() {
		return new ImmutableObservableCollection<>(this);
	}

	/**
	 * Creates a wrapper collection for which removals are filtered
	 *
	 * @param filter The filter to check removals with
	 * @return The removal-filtered collection
	 */
	default Qollection<E> filterRemove(Predicate<? super E> filter) {
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
	default Qollection<E> filterAdd(Predicate<? super E> filter) {
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
	default Qollection<E> filterModification(Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
		return new ModFilteredQollection<>(this, removeFilter, addFilter);
	}

	/**
	 * Tests the removability of an element from this collection. This method exposes a "best guess" on whether an element in the collection
	 * could be removed, but does not provide any guarantee. This method should return true for any object for which {@link #remove(Object)}
	 * is successful, but the fact that an object passes this test does not guarantee that it would be removed successfully. E.g. the
	 * position of the element in the collection may be a factor, but may not be tested for here.
	 *
	 * @param value The value to test removability for
	 * @return Whether the given value could possibly be removed from this collection
	 */
	boolean canRemove(Object value);

	/**
	 * Tests the compatibility of an object with this collection. This method exposes a "best guess" on whether an element could be added to
	 * the collection , but does not provide any guarantee. This method should return true for any object for which {@link #add(Object)} is
	 * successful, but the fact that an object passes this test does not guarantee that it would be removed successfully. E.g. the position
	 * of the element in the collection may be a factor, but is tested for here.
	 *
	 * @param value The value to test compatibility for
	 * @return Whether the given value could possibly be added to this collection
	 */
	boolean canAdd(E value);

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
	 * An extension of ObservableCollection that implements some of the redundant methods and throws UnsupportedOperationExceptions for
	 * modifications. Mostly copied from {@link java.util.AbstractCollection}.
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
		public final boolean passed;

		/**
		 * @param _mapped The mapped result
		 * @param _passed Whether the value passed the filter
		 */
		public FilterMapResult(T _mapped, boolean _passed) {
			mapped = _mapped;
			passed = _passed;
		}
	}

	/**
	 * Implements {@link Qollection#filterMap(TypeToken, Function, Function)}
	 *
	 * @param <E> The type of the collection to filter/map
	 * @param <T> The type of the filter/mapped collection
	 */
	abstract class FilterMappedQollection<E, T> implements PartialQollectionImpl<T> {
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
				if (theMap.apply(el).passed)
					ret++;
			return ret;
		}

		@Override
		public Iterator<T> iterator() {
			return filter(theWrapped.iterator());
		}

		@Override
		public boolean add(T e) {
			if (theReverse == null)
				return PartialQollectionImpl.super.add(e);
			else {
				E reversed = theReverse.apply(e);
				if (!theMap.apply(reversed).passed)
					throw new IllegalArgumentException("The value " + e + " is not acceptable in this mapped list");
				return theWrapped.add(reversed);
			}
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			if (theReverse == null)
				return PartialQollectionImpl.super.addAll(c);
			else {
				List<E> toAdd = c.stream().map(theReverse).collect(Collectors.toList());
				for (E value : toAdd)
					if (!theMap.apply(value).passed)
						throw new IllegalArgumentException("Value " + value + " is not acceptable in this mapped list");
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
					if (mapped.passed && Objects.equals(mapped.mapped, o)) {
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
					if (mapped.passed && c.contains(mapped.mapped)) {
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
					if (mapped.passed && !c.contains(mapped.mapped)) {
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
					if (mapped.passed) {
						iter.remove();
					}
				}
			}
		}

		@Override
		public boolean canRemove(Object value) {
			if (theReverse != null && (value == null || theType.getRawType().isInstance(value)))
				return theWrapped.canRemove(theReverse.apply((T) value));
			else
				return false;
		}

		@Override
		public boolean canAdd(T value) {
			if (theReverse != null)
				return theWrapped.canAdd(theReverse.apply(value));
			else
				return false;
		}

		protected Iterator<T> filter(Iterator<E> iter) {
			return new Iterator<T>() {
				private FilterMapResult<T> nextVal;

				@Override
				public boolean hasNext() {
					while ((nextVal == null || !nextVal.passed) && iter.hasNext()) {
						nextVal = theMap.apply(iter.next());
					}
					return nextVal != null && nextVal.passed;
				}

				@Override
				public T next() {
					if ((nextVal == null || !nextVal.passed) && !hasNext())
						throw new java.util.NoSuchElementException();
					T ret = nextVal.mapped;
					nextVal = null;
					return ret;
				}

				@Override
				public void remove() {
					iter.remove();
				}
			};
		}

		@Override
		public String toString() {
			return Qollection.toString(this);
		}
	}

	/**
	 * An observable collection that cannot be modified directly, but reflects the value of a wrapped collection as it changes
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
		public Subscription onElement(Consumer<? super ObservableElement<E>> observer) {
			return theWrapped.onElement(observer);
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
		public boolean canRemove(Object value) {
			return false;
		}

		@Override
		public boolean canAdd(E value) {
			return false;
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
	 * Implements {@link Qollection#filterModification(Predicate, Predicate)}
	 *
	 * @param <E> The type of the collection to control
	 */
	class ModFilteredCollection<E> implements PartialQollectionImpl<E> {
		private final Qollection<E> theWrapped;

		private final Predicate<? super E> theRemoveFilter;
		private final Predicate<? super E> theAddFilter;

		public ModFilteredCollection(Qollection<E> wrapped, Predicate<? super E> removeFilter, Predicate<? super E> addFilter) {
			theWrapped = wrapped;
			theRemoveFilter = removeFilter;
			theAddFilter = addFilter;
		}

		protected Qollection<E> getWrapped() {
			return theWrapped;
		}

		protected Predicate<? super E> getRemoveFilter() {
			return theRemoveFilter;
		}

		protected Predicate<? super E> getAddFilter() {
			return theAddFilter;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public Quiterator<E> spliterator() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<E>> onElement) {
			return theWrapped.onElement(onElement);
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
		public Iterator<E> iterator() {
			return new Iterator<E>() {
				private final Iterator<E> backing = theWrapped.iterator();

				private E theLast;

				@Override
				public boolean hasNext() {
					return backing.hasNext();
				}

				@Override
				public E next() {
					theLast = backing.next();
					return theLast;
				}

				@Override
				public void remove() {
					if (theRemoveFilter == null || theRemoveFilter.test(theLast))
						backing.remove();
				}
			};
		}

		@Override
		public boolean add(E value) {
			if (theAddFilter == null || theAddFilter.test(value))
				return theWrapped.add(value);
			else
				return false;
		}

		@Override
		public boolean addAll(Collection<? extends E> values) {
			if (theAddFilter != null)
				return theWrapped.addAll(values.stream().filter(theAddFilter).collect(Collectors.toList()));
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
					if (theRemoveFilter.test(next)) {
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
					if (theRemoveFilter.test(next))
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
					if (theRemoveFilter.test(next))
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
				if (theRemoveFilter.test(next))
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
		public boolean canRemove(Object value) {
			if (theRemoveFilter != null && (value == null || theWrapped.getType().getRawType().isInstance(value))
				&& !theRemoveFilter.test((E) value))
				return false;
			return theWrapped.canRemove(value);
		}

		@Override
		public boolean canAdd(E value) {
			if (theAddFilter != null && !theAddFilter.test(value))
				return false;
			return theWrapped.canAdd(value);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}
}
