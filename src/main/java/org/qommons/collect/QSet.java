package org.qommons.collect;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.IterableUtils;
import org.qommons.Transaction;
import org.qommons.collect.Quiterator.CollectionElement;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

/**
 * A Qollection of unique elements
 * 
 * @param <E> The type of elements in the set
 */
public interface QSet<E> extends Qollection<E>, Set<E> {
	@Override
	default Iterator<E> iterator() {
		return Qollection.super.iterator();
	}

	@Override
	abstract Quiterator<E> spliterator();

	@Override
	default boolean isEmpty() {
		return Qollection.super.isEmpty();
	}

	@Override
	default boolean contains(Object o) {
		return Qollection.super.contains(o);
	}

	@Override
	default boolean containsAll(java.util.Collection<?> coll) {
		return Qollection.super.containsAll(coll);
	}

	/**
	 * @param o The object to get the equivalent of
	 * @return The object in this set whose value is equivalent to the given value
	 */
	default Value<E> equivalent(Object o) {
		return new QSetEquivalentFinder<>(this, o);
	}

	@Override
	default E[] toArray() {
		return Qollection.super.toArray();
	}

	@Override
	default <T> T[] toArray(T[] a) {
		return Qollection.super.toArray(a);
	}

	@Override
	default <T> MappedSetOrQollectionBuilder<E, T> buildMap(TypeToken<T> type) {
		return new MappedSetOrQollectionBuilder<>(this, type);
	}

	@Override
	default <T> Qollection<T> filterMap(FilterMapDef<E, T> filterMap) {
		// TODO Auto-generated method stub
		return Qollection.super.filterMap(filterMap);
	}

	/**
	 * @param filter The filter function
	 * @return A collection containing all non-null elements passing the given test
	 */
	@Override
	default QSet<E> filter(Function<? super E, String> filter) {
		return (QSet<E>) Qollection.super.filter(filter);
	}

	/**
	 * @param <T> The type for the new collection
	 * @param type The type to filter this collection by
	 * @return A collection backed by this collection, consisting only of elements in this collection whose values are instances of the
	 *         given class
	 */
	@Override
	default <T> QSet<T> filter(Class<T> type) {
		return new FilteredSet<>(this, TypeToken.of(type), value -> {
			if (type.isInstance(value))
				return null;
			else
				return value.getClass().getName() + " is not an instance of " + type.getName();
		});
	}

	/**
	 * Creates a mapped set, relying on the caller to guarantee that values returned by the function are equivalent to the argument
	 *
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type for the mapped collection (may be null)
	 * @param map The mapping function to map the elements of this collection
	 * @param reverse The reverse function if addition support is desired for the mapped collection
	 * @return The mapped collection
	 */
	default <T> QSet<T> mapEquivalent(Function<? super E, T> map, Function<? super T, E> reverse) {
		return mapEquivalent((TypeToken<T>) TypeToken.of(map.getClass()).resolveType(Function.class.getTypeParameters()[1]), map, reverse);
	}

	/**
	 * Creates a mapped set, relying on the caller to guarantee that values returned by the function are equivalent to the argument
	 *
	 * @param <T> The type of the mapped collection
	 * @param type The run-time type for the mapped collection (may be null)
	 * @param map The mapping function to map the elements of this collection
	 * @param reverse The reverse function if addition support is desired for the mapped collection
	 * @return The mapped collection
	 */
	default <T> QSet<T> mapEquivalent(TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse) {
		return new MappedQSet<>(this, type, map, reverse);
	}

	@Override
	default QSet<E> immutable() {
		return new ImmutableQSet<>(this);
	}

	@Override
	default QSet<E> filterRemove(Function<? super E, String> filter) {
		return (QSet<E>) Qollection.super.filterRemove(filter);
	}

	@Override
	default QSet<E> noRemove() {
		return (QSet<E>) Qollection.super.noRemove();
	}

	@Override
	default QSet<E> filterAdd(Function<? super E, String> filter) {
		return (QSet<E>) Qollection.super.filterAdd(filter);
	}

	@Override
	default QSet<E> noAdd() {
		return (QSet<E>) Qollection.super.noAdd();
	}

	@Override
	default QSet<E> filterModification(Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
		return new ModFilteredSet<>(this, removeFilter, addFilter);
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 * 
	 * @param <E> The type of elements in the set
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> QSet<E> flattenValue(Value<? extends QSet<E>> collectionObservable) {
		return new FlattenedValueSet<>(collectionObservable);
	}

	/**
	 * A default toString() method for set implementations to use
	 *
	 * @param set The set to print
	 * @return The string representation of the set
	 */
	public static String toString(QSet<?> set) {
		StringBuilder ret = new StringBuilder("{");
		boolean first = true;
		try (Transaction t = set.lock(false, null)) {
			for (Object value : set) {
				if (!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
		}
		ret.append('}');
		return ret.toString();
	}

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param coll The collection with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> QSet<T> constant(TypeToken<T> type, java.util.Collection<T> coll) {
		LinkedHashMap<T, CollectionElement<T>> modSet = new LinkedHashMap<>(coll.size());
		for (T value : coll) {
			if (!modSet.containsKey(value)) {
				modSet.put(value, new CollectionElement<T>() {
					@Override
					public TypeToken<T> getType() {
						return type;
					}

					@Override
					public T get() {
						return value;
					}

					@Override
					public <V extends T> String isAcceptable(V value2) {
						return "This collection is immutable";
					}

					@Override
					public <V extends T> T set(V value2, Object cause) throws IllegalArgumentException {
						throw new IllegalArgumentException("This collection is immutable");
					}

					@Override
					public Value<String> isEnabled() {
						return Value.constant("This collection is immutable");
					}

					@Override
					public String canRemove() {
						return "This collection is immutable";
					}

					@Override
					public void remove() throws IllegalArgumentException {
						throw new IllegalArgumentException("This collection is immutable");
					}

					@Override
					public String canAdd(T toAdd) {
						return "This collection is immutable";
					}

					@Override
					public void add(T toAdd) throws IllegalArgumentException {
						throw new IllegalArgumentException("This collection is immutable");
					}
				});
			}
		}
		class ConstantQSet implements PartialSetImpl<T> {
			@Override
			public Transaction lock(boolean write, Object cause) {
				return () -> {
				};
			}

			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public int size() {
				return modSet.size();
			}

			@Override
			public Iterator<T> iterator() {
				return IterableUtils.immutableIterator(modSet.keySet().iterator());
			}

			@Override
			public String canRemove(Object value) {
				return "This collection is immutable";
			}

			@Override
			public String canAdd(T value) {
				return "This collection is immutable";
			}

			@Override
			public Quiterator<T> spliterator() {
				Supplier<Function<T, CollectionElement<T>>> elementSupplier = () -> {
					return value -> modSet.get(value);
				};
				return new Quiterator.SimpleQuiterator<T, T>(modSet.keySet().spliterator(), elementSupplier) {
					@Override
					public int characteristics() {
						return super.characteristics() | Spliterator.IMMUTABLE;
					}
				};
			}
		}
		return new ConstantQSet();
	}

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param values The array with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> QSet<T> constant(TypeToken<T> type, T... values) {
		return constant(type, java.util.Arrays.asList(values));
	}

	/**
	 * @param <T> The type of the collection
	 * @param coll The collection to turn into a set
	 * @return A set containing all unique elements of the given collection
	 */
	public static <T> QSet<T> unique(Qollection<T> coll) {
		return new CollectionWrappingSet<>(coll);
	}

	/**
	 * An extension of QSet that implements some of the redundant methods and throws UnsupportedOperationExceptions for modifications.
	 *
	 * @param <E> The type of element in the set
	 */
	interface PartialSetImpl<E> extends PartialQollectionImpl<E>, QSet<E> {
		@Override
		default boolean remove(Object o) {
			return PartialQollectionImpl.super.remove(o);
		}

		@Override
		default boolean removeAll(Collection<?> c) {
			return PartialQollectionImpl.super.removeAll(c);
		}

		@Override
		default boolean retainAll(Collection<?> c) {
			return PartialQollectionImpl.super.retainAll(c);
		}

		@Override
		default void clear() {
			PartialQollectionImpl.super.clear();
		}

		@Override
		default boolean add(E value) {
			return PartialQollectionImpl.super.add(value);
		}

		@Override
		default boolean addAll(Collection<? extends E> c) {
			return PartialQollectionImpl.super.addAll(c);
		}

		@Override
		abstract Quiterator<E> spliterator();
	}

	/**
	 * Implements {@link QSet#equivalent(Object)}
	 *
	 * @param <E> The type of the set to find the value in
	 */
	class QSetEquivalentFinder<E> implements Value<E> {
		private final QSet<E> theSet;

		private final Object theKey;

		protected QSetEquivalentFinder(QSet<E> set, Object key) {
			theSet = set;
			theKey = key;
		}

		@Override
		public TypeToken<E> getType() {
			return theSet.getType();
		}

		@Override
		public E get() {
			for (E value : theSet) {
				if (Objects.equals(value, theKey))
					return value;
			}
			return null;
		}
	}

	class MappedSetOrQollectionBuilder<E, T> extends MappedQollectionBuilder<E, T> {
		public MappedSetOrQollectionBuilder(QSet<E> wrapped, TypeToken<T> type) {
			super(wrapped, type);
		}
	}

	/**
	 * Implements {@link QSet#filter(Function)} and {@link QSet#filter(Class)}
	 *
	 * @param <E> The type of the set to filter
	 * @param <T> the type of the mapped set
	 */
	class FilteredSet<E, T> extends FilterMappedQollection<E, T> implements PartialSetImpl<T> {
		protected FilteredSet(QSet<E> wrap, TypeToken<T> type, Function<? super E, String> filter) {
			super(wrap, type, value -> {
				String pass = filter.apply(value);
				return new FilterMapResult<>(pass == null ? (T) value : null, pass);
			}, value -> (E) value);
		}

		@Override
		protected QSet<E> getWrapped() {
			return (QSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link QSet#mapEquivalent(TypeToken, Function, Function)}
	 *
	 * @param <E> The type of the set to map
	 * @param <T> The type of the mapped set
	 */
	class MappedQSet<E, T> extends FilterMappedQollection<E, T> implements QSet<T> {
		protected MappedQSet(QSet<E> wrap, TypeToken<T> type, Function<? super E, T> map, Function<? super T, E> reverse) {
			super(wrap, type, value -> new FilterMapResult<>(map.apply(value), null), reverse);
		}

		@Override
		protected QSet<E> getWrapped() {
			return (QSet<E>) super.getWrapped();
		}
	}

	/**
	 * An observable set that cannot be modified directly, but reflects the value of a wrapped set as it changes
	 *
	 * @param <E> The type of elements in the set
	 */
	class ImmutableQSet<E> extends ImmutableQollection<E> implements PartialSetImpl<E> {
		protected ImmutableQSet(QSet<E> wrap) {
			super(wrap);
		}

		@Override
		protected QSet<E> getWrapped() {
			return (QSet<E>) super.getWrapped();
		}

		@Override
		public ImmutableQSet<E> immutable() {
			return this;
		}
	}

	/**
	 * Implements {@link QSet#filterModification(Function, Function)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class ModFilteredSet<E> extends ModFilteredQollection<E> implements PartialSetImpl<E> {
		public ModFilteredSet(QSet<E> wrapped, Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected QSet<E> getWrapped() {
			return (QSet<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link QSet#flattenValue(Value)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class FlattenedValueSet<E> extends FlattenedValueQollection<E> implements PartialSetImpl<E> {
		public FlattenedValueSet(Value<? extends QSet<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected Value<? extends QSet<E>> getWrapped() {
			return (Value<? extends QSet<E>>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link QSet#unique(Qollection)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class CollectionWrappingSet<E> implements PartialSetImpl<E> {
		private final Qollection<E> theCollection;

		public CollectionWrappingSet(Qollection<E> collection) {
			theCollection = collection;
		}

		protected Qollection<E> getWrapped() {
			return theCollection;
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theCollection.lock(write, cause);
		}

		@Override
		public int size() {
			HashSet<E> set = new HashSet<>();
			for (E value : theCollection)
				set.add(value);
			return set.size();
		}

		@Override
		public Quiterator<E> spliterator() {
			return unique(theCollection.spliterator());
		}

		@Override
		public String canRemove(Object value) {
			return theCollection.canRemove(value);
		}

		@Override
		public String canAdd(E value) {
			String canAdd = theCollection.canAdd(value);
			if (canAdd != null)
				return canAdd;
			HashSet<E> set = new HashSet<>();
			for (E v : theCollection)
				set.add(v);
			return set.contains(value) ? "This value is already present in the set" : null;
		}

		protected Quiterator<E> unique(Quiterator<E> backing) {
			final HashSet<E> set = new HashSet<>();
			Supplier<Function<CollectionElement<? extends E>, CollectionElement<E>>> elementMap = () -> {
				return el -> {
					if (set.add(el.get()))
						return (CollectionElement<E>) el;
					else
						return null;
				};
			};
			return new Quiterator.WrappingQuiterator<>(null, elementMap);
		}

		@Override
		public String toString() {
			return QSet.toString(this);
		}
	}
}
