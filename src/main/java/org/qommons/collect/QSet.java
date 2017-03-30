package org.qommons.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
	// Overrides needed by the compiler

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

	@Override
	default E[] toArray() {
		return Qollection.super.toArray();
	}

	@Override
	default <T> T[] toArray(T[] a) {
		return Qollection.super.toArray(a);
	}

	// Additional methods

	/**
	 * @param o The object to get the equivalent of
	 * @return The object in this set whose value is equivalent to the given value
	 */
	default Value<E> equivalent(Object o) {
		return new QSetEquivalentFinder<>(this, o);
	}

	// Filter/mapping

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

	// Modification controls

	@Override
	default QSet<E> immutable(String modMsg) {
		return (QSet<E>) Qollection.super.immutable(modMsg);
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

	// Static utility methods

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

	/** An interface allowing sets to be defined with their uniqueness defined other than by {@link Object#equals(Object)} */
	interface Equalizer {
		/**
		 * @param o1 The first object to test
		 * @param o2 The second object to test
		 * @return Whether the two objects are equivalent in this context
		 */
		boolean equals(Object o1, Object o2);

		/**
		 * @param o The object to hash
		 * @return A hash code such that hashCode(o1)==hashCode(o2) for all items for which {@link #equals(Object, Object) equals(o1, o2)}
		 *         is true
		 */
		int hashCode(Object o);

		/**
		 * @param <V> The type of the value
		 * @param value The value to make a node for
		 * @return An equalizer node for the given value using this equalizer
		 */
		public default <V> EqualizerNode<V> nodeFor(V value) {
			return new EqualizerNode<>(this, value);
		}

		/**
		 * A node that encapsulates a value and uses an {@link Equalizer} for its {@link #equals(Object)} and {@link #hashCode()} methods
		 * 
		 * @param <V> The type of value stored in the node
		 */
		public static class EqualizerNode<V> {
			private final Equalizer theEqualizer;
			private final V theValue;

			/**
			 * @param equalizer The equalizer for equals testing
			 * @param value The value to test
			 */
			public EqualizerNode(Equalizer equalizer, V value) {
				theEqualizer = equalizer;
				theValue = value;
			}

			/** @return The value in this node */
			public V get() {
				return theValue;
			}

			@Override
			public boolean equals(Object o) {
				if (o instanceof EqualizerNode)
					return theEqualizer.equals(theValue, ((EqualizerNode<?>) o).get());
				else
					return theEqualizer.equals(theValue, o);
			}

			@Override
			public int hashCode() {
				return theEqualizer.hashCode(theValue);
			}
		}

		/** An equalizer that uses default equality ({@link Objects#equals(Object)} and {@link Objects#hashCode(Object)}) */
		static Equalizer def = new Equalizer() {
			@Override
			public boolean equals(Object o1, Object o2) {
				return Objects.equals(o1, o2);
			}

			@Override
			public int hashCode(Object o) {
				return Objects.hashCode(o);
			}
		};

		/** An equalizer that uses identity equality (== and {@link System#identityHashCode(Object)}) */
		static Equalizer id = new Equalizer() {
			@Override
			public boolean equals(Object o1, Object o2) {
				return o1 == o2;
			}

			@Override
			public int hashCode(Object o) {
				return System.identityHashCode(o);
			}
		};
	}

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param equalizer The equalizer to test for uniqueness in the set
	 * @param coll The collection with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> QSet<T> constant(TypeToken<T> type, Equalizer equalizer, java.util.Collection<T> coll) {
		LinkedHashMap<Equalizer.EqualizerNode<T>, CollectionElement<T>> modSet = new LinkedHashMap<>(coll.size());
		for (T value : coll) {
			if (!modSet.containsKey(value)) {
				modSet.put(equalizer.nodeFor(value), new CollectionElement<T>() {
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
		List<T> values = new ArrayList<>(modSet.size());
		values.addAll(modSet.keySet().stream().map(node -> node.get()).collect(Collectors.toList()));
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
				return IterableUtils.immutableIterator(values.iterator());
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
				Supplier<Function<Equalizer.EqualizerNode<T>, CollectionElement<T>>> elementSupplier = () -> {
					return value -> modSet.get(value);
				};
				return new Quiterator.SimpleQuiterator<Equalizer.EqualizerNode<T>, T>(modSet.keySet().spliterator(), type,
					elementSupplier) {
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
	 * @param equalizer The equalizer to test for uniqueness in the set
	 * @param values The array with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> QSet<T> constant(TypeToken<T> type, Equalizer equalizer, T... values) {
		return constant(type, equalizer, java.util.Arrays.asList(values));
	}

	/**
	 * @param <T> The type of the collection
	 * @param coll The collection to turn into a set
	 * @param equalizer The Equalizer to determine uniqueness for the set
	 * @return A set containing all unique elements of the given collection
	 */
	public static <T> QSet<T> unique(Qollection<T> coll, Equalizer equalizer) {
		return new CollectionWrappingSet<>(coll, equalizer);
	}

	// Implementation member classes

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
		 * @param mapNulls Whether to apply the mapping function to null values or simply pass them through to the mapped set as null values
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
	 * Implements {@link QSet#unique(Qollection, Equalizer)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class CollectionWrappingSet<E> implements PartialSetImpl<E> {
		private final Qollection<E> theCollection;
		private final Equalizer theEqualizer;

		public CollectionWrappingSet(Qollection<E> collection, Equalizer equalizer) {
			theCollection = collection;
			theEqualizer = equalizer;
		}

		protected Qollection<E> getWrapped() {
			return theCollection;
		}

		@Override
		public TypeToken<E> getType() {
			return theCollection.getType();
		}

		@Override
		public boolean isLockSupported() {
			return theCollection.isLockSupported();
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
		public boolean isEmpty() {
			return theCollection.isEmpty();
		}

		@Override
		public Quiterator<E> spliterator() {
			return unique(theCollection.spliterator());
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

		@Override
		public String canRemove(Object value) {
			return theCollection.canRemove(value);
		}

		protected Quiterator<E> unique(Quiterator<E> backing) {
			final HashSet<Equalizer.EqualizerNode<E>> set = new HashSet<>();
			Supplier<Function<CollectionElement<? extends E>, CollectionElement<E>>> elementMap = () -> {
				return el -> {
					QSet.Equalizer.EqualizerNode<E> node = theEqualizer.nodeFor((E) el.get());
					if (set.add(node))
						return (CollectionElement<E>) el;
					else
						return null;
				};
			};
			return new Quiterator.WrappingQuiterator<>(null, theCollection.getType(), elementMap);
		}

		@Override
		public String toString() {
			return QSet.toString(this);
		}
	}
}
