package org.qommons.collect;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.qommons.Equalizer;
import org.qommons.Equalizer.EqualizerNode;
import org.qommons.Hasher;
import org.qommons.IterableUtils;
import org.qommons.Transaction;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

/**
 * A Qollection of unique elements
 * 
 * @param <E> The type of elements in the set
 */
public interface QSet<E> extends Qollection<E>, TransactableSet<E> {
	// Additional methods

	/**
	 * @return The equalizer that enforces this set's uniqueness. The equalizer is only guaranteed to return valid results when given
	 *         objects that this set understands. If either value passed to {@link Equalizer#equals(Object, Object)} is not in this set's
	 *         domain, false will be returned.
	 */
	Equalizer equalizer();

	/**
	 * @param o The object to get the equivalent of
	 * @return The object in this set whose value is equivalent to the given value, if present
	 */
	Optional<E> equivalent(Object o);

	// Overrides needed by the compiler

	@Override
	default Iterator<E> iterator() {
		return Qollection.super.iterator();
	}

	@Override
	abstract ElementSpliterator<E> spliterator();

	@Override
	default E[] toArray() {
		return Qollection.super.toArray();
	}

	@Override
	default <T> T[] toArray(T[] a) {
		return Qollection.super.toArray(a);
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
	 * @param <T> The type to map to
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

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param equalizer The equalizer to test for uniqueness in the set
	 * @param hasher The hasher to provide the hash code for the values
	 * @param coll The collection with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> QSet<T> constant(TypeToken<T> type, Equalizer equalizer, Hasher<? super T> hasher, java.util.Collection<T> coll) {
		LinkedHashMap<Equalizer.EqualizerNode<T>, CollectionElement<T>> modSet = new LinkedHashMap<>(coll.size());
		for (T value : coll) {
			if (!modSet.containsKey(value)) {
				modSet.put(equalizer.nodeFor(value, hasher.hash(value)), new CollectionElement<T>() {
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
		class ConstantQSet implements QSet<T> {
			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public boolean isLockSupported() {
				return false;
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return () -> {
				};
			}

			@Override
			public Equalizer equalizer() {
				return equalizer;
			}

			@Override
			public int size() {
				return modSet.size();
			}

			@Override
			public boolean isEmpty() {
				return modSet.isEmpty();
			}

			protected EqualizerNode<T> nodeFor(T value) {
				return equalizer.nodeFor(value, hasher.hash(value));
			}

			@Override
			public Optional<T> equivalent(Object o) {
				if (!type.getRawType().isInstance(o))
					return null;
				CollectionElement<T> element = modSet.get(nodeFor((T) o));
				return element == null ? Optional.empty() : Optional.of(element.get());
			}

			@Override
			public boolean contains(Object o) {
				if (o != null && !type.getRawType().isInstance(o))
					return false;
				return modSet.containsKey(equalizer.nodeFor(o, hasher.hash((T) o)));
			}

			@Override
			public boolean containsAll(Collection<?> coll2) {
				for (Object o : coll2)
					if (o != null && !type.getRawType().isInstance(o))
						return false;
				if (coll2.size() > size() * 2)
					return DefaultQollectionMethods.containsAll(this, coll2); // Maybe better than making nodes for them all
				return modSet.keySet()
					.containsAll(coll2.stream().map(o -> equalizer.nodeFor(o, hasher.hash((T) o))).collect(Collectors.toList()));
			}

			@Override
			public boolean containsAny(Collection<?> coll2) {
				for (Object o : coll2) {
					if (o != null && !type.getRawType().isInstance(o))
						continue;
					if (modSet.containsKey(equalizer.nodeFor(o, hasher.hash((T) o))))
						return true;
				}
				return false;
			}

			@Override
			public Iterator<T> iterator() {
				return IterableUtils.immutableIterator(values.iterator());
			}

			@Override
			public String canAdd(T value) {
				return "This collection is immutable";
			}

			@Override
			public boolean add(T e) {
				return false;
			}

			@Override
			public boolean addAll(Collection<? extends T> c) {
				return false;
			}

			@Override
			public Qollection<T> addValues(T... values2) {
				return this;
			}

			@Override
			public String canRemove(Object value) {
				return "This collection is immutable";
			}

			@Override
			public boolean remove(Object o) {
				return false;
			}

			@Override
			public boolean removeAll(Collection<?> c) {
				return false;
			}

			@Override
			public boolean retainAll(Collection<?> c) {
				return false;
			}

			@Override
			public void clear() {
			}

			@Override
			public ElementSpliterator<T> spliterator() {
				Supplier<Function<Equalizer.EqualizerNode<T>, CollectionElement<T>>> elementSupplier = () -> {
					return value -> modSet.get(value);
				};
				return new ElementSpliterator.SimpleSpliterator<Equalizer.EqualizerNode<T>, T>(modSet.keySet().spliterator(), type,
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
	 * @param hasher The hasher to provide the hash code for the values
	 * @param values The array with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> QSet<T> constant(TypeToken<T> type, Equalizer equalizer, Hasher<? super T> hasher, T... values) {
		return constant(type, equalizer, hasher, java.util.Arrays.asList(values));
	}

	/**
	 * @param <T> The type of the collection
	 * @param coll The collection to turn into a set
	 * @param equalizer The Equalizer to determine uniqueness for the set
	 * @param hasher The hasher to provide the hash code for the values in the collection
	 * @return A set containing all unique elements of the given collection
	 */
	public static <T> QSet<T> unique(Qollection<T> coll, Equalizer equalizer, Hasher<? super T> hasher) {
		return new CollectionWrappingSet<>(coll, equalizer, hasher);
	}

	// Implementation member classes

	/**
	 * A filter-map builder that may produce either a plain {@link Qollection} or a {@link QSet}. It will produce a QSet unless {#link
	 * #map(Function, boolean)} is called, producing a plain {@link Qollection.MappedQollectionBuilder} that will produce a Qollection as
	 * normal. {@link #mapEquiv(Function, boolean, Function, boolean)} may be used alternatively to preserve the uniqueness contract and
	 * produce a mapped QSet.
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
		 * required of {@link Set} implementations and a {@link QSet} is desired for the result, use
		 * {@link #mapEquiv(Function, boolean, Function, boolean)}.
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
		 * @param reverse The reverse function to map from the results of a map operation back to objects that the wrapped set can
		 *        understand
		 * @param reverseNulls Whether to apply the reverse function to null values or simply pass them through to the wrapped set as null
		 *        values
		 * @return This builder
		 */
		public MappedSetOrQollectionBuilder<E, I, T> mapEquiv(Function<? super I, ? extends T> map, boolean mapNulls,
			Function<? super T, ? extends I> reverse, boolean reverseNulls) {
			Objects.requireNonNull(map);
			Objects.requireNonNull(reverse);
			return (MappedSetOrQollectionBuilder<E, I, T>) super.map(map, mapNulls).withReverse(reverse, reverseNulls);
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
	class FilterMappedSet<E, T> extends FilterMappedQollection<E, T> implements QSet<T> {
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

		@Override
		public Equalizer equalizer() {
			return (t1, t2) -> {
				if (!getType().getRawType().isInstance(t1) || !getType().getRawType().isInstance(t2))
					return false;
				FilterMapResult<T, E> reversed1 = getDef().reverse(new FilterMapResult<>((T) t1));
				FilterMapResult<T, E> reversed2 = getDef().reverse(new FilterMapResult<>((T) t2));
				if (reversed1.error != null || reversed2.error != null)
					return false;
				return getWrapped().equalizer().equals(reversed1.result, reversed2.result);
			};
		}

		@Override
		public Optional<T> equivalent(Object o) {
			if (!getType().getRawType().isInstance(o))
				return Optional.empty();
			FilterMapResult<T, E> reversed = getDef().reverse(new FilterMapResult<>((T) o));
			if (reversed.error != null)
				return Optional.empty();
			Optional<E> wrappedEquiv = getWrapped().equivalent(reversed.result);
			return wrappedEquiv.flatMap(e -> {
				FilterMapResult<E, T> res = getDef().map(new FilterMapResult<>(e));
				return res.error != null ? Optional.empty() : Optional.of(res.result);
			});
		}
	}

	/**
	 * Implements {@link QSet#filterModification(Function, Function)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class ModFilteredSet<E> extends ModFilteredQollection<E> implements QSet<E> {
		public ModFilteredSet(QSet<E> wrapped, Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected QSet<E> getWrapped() {
			return (QSet<E>) super.getWrapped();
		}

		@Override
		public Equalizer equalizer() {
			return getWrapped().equalizer();
		}

		@Override
		public Optional<E> equivalent(Object o) {
			return getWrapped().equivalent(o);
		}
	}

	/**
	 * Implements {@link QSet#flattenValue(Value)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class FlattenedValueSet<E> extends FlattenedValueQollection<E> implements QSet<E> {
		public FlattenedValueSet(Value<? extends QSet<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected Value<? extends QSet<E>> getWrapped() {
			return (Value<? extends QSet<E>>) super.getWrapped();
		}

		@Override
		public Equalizer equalizer() {
			QSet<E> current = getWrapped().get();
			return current == null ? Objects::equals : current.equalizer();
		}

		@Override
		public Optional<E> equivalent(Object o) {
			QSet<E> current = getWrapped().get();
			return current == null ? Optional.empty() : current.equivalent(o);
		}
	}

	/**
	 * Implements {@link QSet#unique(Qollection, Equalizer, Hasher)}
	 *
	 * @param <E> The type of elements in the set
	 */
	class CollectionWrappingSet<E> implements QSet<E> {
		private final Qollection<E> theCollection;
		private final Equalizer theEqualizer;
		private final Hasher<? super E> theHasher;

		public CollectionWrappingSet(Qollection<E> collection, Equalizer equalizer, Hasher<? super E> hasher) {
			theCollection = collection;
			theEqualizer = equalizer;
			theHasher = hasher;
		}

		protected Qollection<E> getWrapped() {
			return theCollection;
		}

		@Override
		public Equalizer equalizer() {
			return theEqualizer;
		}

		protected Hasher<? super E> getHasher() {
			return theHasher;
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
			return getNodeSet().size();
		}

		@Override
		public boolean isEmpty() {
			return theCollection.isEmpty();
		}

		protected HashSet<EqualizerNode<E>> getNodeSet() {
			try (Transaction t = theCollection.lock(false, null)) {
				HashSet<EqualizerNode<E>> set = new HashSet<>();
				for (E v : theCollection)
					set.add(nodeFor(v));
				return set;
			}
		}

		protected EqualizerNode<E> nodeFor(E value) {
			return theEqualizer.nodeFor(value, theHasher.hash(value));
		}

		@Override
		public Optional<E> equivalent(Object o) {
			if(!getType().getRawType().isInstance(o))
				return null;
			try(Transaction t=theCollection.lock(false, null)){
				HashMap<EqualizerNode<E>, E> map=new HashMap<>();
				for (E v : theCollection)
					map.put(nodeFor(v), v);
				EqualizerNode<E> node = nodeFor((E) o);
				if (map.containsKey(node))
					return Optional.of(map.get(node));
				else
					return Optional.empty();
			}
		}

		@Override
		public boolean contains(Object o) {
			if (o != null && !getType().getRawType().isInstance(o))
				return false;
			return getNodeSet().contains(nodeFor((E) o));
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			HashSet<EqualizerNode<E>> copy = new HashSet<>(c.size() * 4 / 3);
			for (Object o : c) {
				if (o != null && !getType().getRawType().isInstance(o))
					return false;
				copy.add(nodeFor((E) o));
			}
			return getNodeSet().containsAll(copy);
		}

		@Override
		public boolean containsAny(Collection<?> c) {
			for (Object o : c) {
				if (o != null && !getType().getRawType().isInstance(o))
					continue;
				if (getNodeSet().contains(nodeFor((E) o)))
					return true;
			}
			return false;
		}

		@Override
		public ElementSpliterator<E> spliterator() {
			return unique(theCollection.spliterator());
		}

		@Override
		public String canAdd(E value) {
			String canAdd = theCollection.canAdd(value);
			if (canAdd != null)
				return canAdd;
			HashSet<EqualizerNode<E>> set = getNodeSet();
			return set.contains(nodeFor(value)) ? "This value is already present in the set" : null;
		}
		
		@Override
		public boolean add(E value) {
			String canAdd = theCollection.canAdd(value);
			if (canAdd != null)
				return false;
			HashSet<EqualizerNode<E>> set = getNodeSet();
			if (set.contains(nodeFor(value)))
				return false;
			return theCollection.add(value);
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			HashSet<EqualizerNode<E>> set = getNodeSet();
			ArrayList<E> toAdd = new ArrayList<>(c.size());
			for (E value : c) {
				if (!set.add(nodeFor(value)))
					toAdd.add(value);
			}
			return theCollection.addAll(toAdd);
		}

		@Override
		public String canRemove(Object value) {
			return theCollection.canRemove(value);
		}

		@Override
		public boolean remove(Object o) {
			boolean mod = false;
			while (theCollection.remove(o))
				mod = true;
			return mod;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theCollection.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theCollection.retainAll(c);
		}

		@Override
		public void clear() {
			theCollection.clear();
		}

		protected ElementSpliterator<E> unique(ElementSpliterator<E> backing) {
			final HashSet<Equalizer.EqualizerNode<E>> set = new HashSet<>();
			Supplier<Function<CollectionElement<? extends E>, CollectionElement<E>>> elementMap = () -> {
				return el -> {
					EqualizerNode<E> node = nodeFor(el.get());
					if (set.add(node))
						return (CollectionElement<E>) el;
					else
						return null;
				};
			};
			return new ElementSpliterator.WrappingSplterator<>(null, theCollection.getType(), elementMap);
		}

		@Override
		public String toString() {
			return QSet.toString(this);
		}
	}
}
