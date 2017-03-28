package org.qommons.collect;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
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
	default <T> MappedSetOrQollectionBuilder<E, E, T> buildMap(TypeToken<T> type) {
		return new MappedSetOrQollectionBuilder<>(this, null, type);
	}

	/**
	 * Similar to {@link #filterMap(FilterMapDef)}, but produces a set, as {@link EquivalentFilterMapDef} instances can only be produced
	 * with the assertion that any map operations preserve the Set's uniqueness contract.
	 * 
	 * @param filterMap The filter-map definition
	 * @return A set, filtered and mapped with the given definition
	 */
	default <T> QSet<T> filterMap(EquivalentFilterMapDef<E, ?, T> filterMap) {
		return new FilterMappedSet<>(this, filterMap);
	}

	/**
	 * @param filter The filter function
	 * @return A collection containing all non-null elements passing the given test
	 */
	@Override
	default QSet<E> filter(Function<? super E, String> filter) {
		return (QSet<E>) Qollection.super.filter(filter);
	}

	@Override
	default <T> QSet<T> filter(Class<T> type) {
		return (QSet<T>) Qollection.super.filter(type);
	}

	@Override
	default QSet<E> immutable(String modMsg) {
		return new ImmutableQSet<>(this, modMsg);
	}

	@Override
	default QSet<E> filterRemove(Function<? super E, String> filter) {
		return (QSet<E>) Qollection.super.filterRemove(filter);
	}

	@Override
	default QSet<E> noRemove(String modMsg) {
		return (QSet<E>) Qollection.super.noRemove(modMsg);
	}

	@Override
	default QSet<E> filterAdd(Function<? super E, String> filter) {
		return (QSet<E>) Qollection.super.filterAdd(filter);
	}

	@Override
	default QSet<E> noAdd(String modMsg) {
		return (QSet<E>) Qollection.super.noAdd(modMsg);
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

			@Override
			public String toString() {
				return QSet.toString(this);
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

	/**
	 * A filter-map builder that may produce either a plain {@link Qollection} or a {@link QSet}. It will produce a QSet unless {#link
	 * #map(Function, boolean)} is called, producing a plain {@link Qollection.MappedQollectionBuilder} that will produce a Qollection as
	 * normal. {@link #mapEquiv(Function, boolean)} may be used alternatively to preserve the uniqueness contract and produce a mapped QSet.
	 * 
	 * @param <E> The type of values in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of values in the mapped collection
	 */
	class MappedSetOrQollectionBuilder<E, I, T> extends MappedQollectionBuilder<E, I, T> {
		public MappedSetOrQollectionBuilder(QSet<E> wrapped, MappedSetOrQollectionBuilder<E, ?, I> parent, TypeToken<T> type) {
			super(wrapped, parent, type);
		}

		@Override
		public QSet<E> getQollection() {
			return (QSet<E>) super.getQollection();
		}

		@Override
		protected MappedSetOrQollectionBuilder<E, ?, I> getParent() {
			return (MappedSetOrQollectionBuilder<E, ?, I>) super.getParent();
		}

		/**
		 * This method differs from its super method slightly in that it does not return this builder. Since no assumption can be made that
		 * a set mapped with the given function would retain its unique contract, this method returns a different builder that produces a
		 * plain {@link Qollection} instead of a {@link QSet}. If it is known that the given function preserves the uniqueness quality
		 * required of {@link Set} implementations and a {@link QSet} is desired for the result, use {@link #mapEquiv(Function, boolean)}.
		 * 
		 * @param map The mapping function
		 * @param mapNulls Whether to apply the function to null values or simply pass them through to the mapped set as null values
		 * @return A plain {@link Qollection} builder with the same properties as this builder, plus the given map
		 */
		@Override
		public MappedQollectionBuilder<E, I, T> map(Function<? super I, ? extends T> map, boolean mapNulls) {
			MappedQollectionBuilder<E, I, T> nonEquivBuilder = new MappedQollectionBuilder<>(getQollection(), getParent(), getType());
			if (getFilter() != null)
				nonEquivBuilder.filter(getFilter(), areNullsFiltered());
			if (getReverse() != null)
				nonEquivBuilder.withReverse(getReverse(), areNullsReversed());
			return nonEquivBuilder.map(map, mapNulls);
		}

		/**
		 * Similar to {@link #map(Function, boolean)}, but with the additional (unenforced) assertion that the given function applied to
		 * this set will produce a set of similarly unique values. Although this assertion is not enforced here and no exceptions will be
		 * thrown for violation of it, uniqueness is part of the contract of a {@link Set} that may be relied on by other code that may fail
		 * if that contract is not met.
		 * 
		 * @param map The mapping function
		 * @param mapNulls Whether to apply the function to null values or simply pass them through to the mapped set as null values
		 * @return This builder
		 */
		public MappedSetOrQollectionBuilder<E, I, T> mapEquiv(Function<? super I, ? extends T> map, boolean mapNulls) {
			return (MappedSetOrQollectionBuilder<E, I, T>) super.map(map, mapNulls);
		}

		@Override
		public <X> MappedSetOrQollectionBuilder<E, T, X> andThen(TypeToken<X> nextType) {
			return new MappedSetOrQollectionBuilder<>(getQollection(), this, nextType);
		}

		@Override
		public EquivalentFilterMapDef<E, I, T> toDef() {
			EquivalentFilterMapDef<E, ?, I> parent = getParent() == null ? null : getParent().toDef();
			TypeToken<I> intermediate = parent == null ? (TypeToken<I>) getQollection().getType() : parent.destType;
			return new EquivalentFilterMapDef<>(getQollection().getType(), intermediate, getType(), parent, getFilter(), areNullsFiltered(),
				getMap(), areNullsMapped(), getReverse(), areNullsReversed());
		}

		@Override
		public QSet<T> build() {
			return (QSet<T>) super.build();
		}
	}

	/**
	 * The type of {@link Qollection.FilterMapDef} produced by {@link QSet.MappedSetOrQollectionBuilder}s when the uniqueness contract is
	 * preserved.
	 * 
	 * @param <E> The type of values in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of values in the mapped collection
	 */
	class EquivalentFilterMapDef<E, I, T> extends FilterMapDef<E, I, T> {
		public EquivalentFilterMapDef(TypeToken<E> sourceType, TypeToken<I> intermediateType, TypeToken<T> type,
			EquivalentFilterMapDef<E, ?, I> parent, Function<? super I, String> filter, boolean filterNulls,
			Function<? super I, ? extends T> map, boolean mapNulls, Function<? super T, ? extends I> reverse, boolean reverseNulls) {
			super(sourceType, intermediateType, type, parent, filter, filterNulls, map, mapNulls, reverse, reverseNulls);
		}
	}

	/**
	 * A set that is a result of a filter-map operation applied to another set
	 * 
	 * @param <E> The type of values in the source set
	 * @param <T> The type of values in this set
	 */
	class FilterMappedSet<E, T> extends FilterMappedQollection<E, T> implements PartialSetImpl<T> {
		public FilterMappedSet(QSet<E> wrap, EquivalentFilterMapDef<E, ?, T> filterMapDef) {
			super(wrap, filterMapDef);
		}

		@Override
		protected QSet<E> getWrapped() {
			return (QSet<E>) super.getWrapped();
		}

		@Override
		protected EquivalentFilterMapDef<E, ?, T> getDef() {
			return (EquivalentFilterMapDef<E, ?, T>) super.getDef();
		}
	}

	/**
	 * An observable set that cannot be modified directly, but reflects the value of a wrapped set as it changes
	 *
	 * @param <E> The type of elements in the set
	 */
	class ImmutableQSet<E> extends ImmutableQollection<E> implements PartialSetImpl<E> {
		protected ImmutableQSet(QSet<E> wrap, String modMsg) {
			super(wrap, modMsg);
		}

		@Override
		protected QSet<E> getWrapped() {
			return (QSet<E>) super.getWrapped();
		}

		@Override
		public ImmutableQSet<E> immutable(String modMsg) {
			return (ImmutableQSet<E>) super.immutable(modMsg);
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
