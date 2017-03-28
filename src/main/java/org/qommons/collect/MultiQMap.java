package org.qommons.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transaction;
import org.qommons.value.Value;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

public interface MultiQMap<K, V> extends TransactableMultiMap<K, V> {
	interface MultiQEntry<K, V> extends MultiEntry<K, V>, Qollection<V> {}

	/**
	 * A {@link MultiQMap.MultiQEntry} whose values are sorted
	 *
	 * @param <K> The type of key this entry uses
	 * @param <V> The type of value this entry stores
	 */
	public interface ValueSortedMultiEntry<K, V> extends MultiQEntry<K, V>, SortedQSet<V> {}

	TypeToken<K> getKeyType();

	TypeToken<V> getValueType();

	@Override
	QSet<K> keySet();

	/**
	 * <p>
	 * A default implementation of {@link #keySet()}.
	 * </p>
	 * <p>
	 * No {@link MultiQMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #entrySet()} methods. {@link #defaultEntrySet(MultiQMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(MultiQMap)} or {@link #defaultGet(MultiQMap, Object)}. Either {@link #entrySet()} or both {@link #keySet()} and
	 * {@link #get(Object)} must be custom. If an implementation supplies custom {@link #keySet()} and {@link #get(Object)} implementations,
	 * it may use {@link #defaultEntrySet(MultiQMap)} for its {@link #entrySet()} . If an implementation supplies a custom
	 * {@link #entrySet()} implementation, it may use {@link #defaultKeySet(MultiQMap)} and {@link #defaultGet(MultiQMap, Object)} for its
	 * {@link #keySet()} and {@link #get(Object)} implementations, respectively. Using default implementations for both will result in
	 * infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create a key set for
	 * @return A key set for the map
	 */
	public static <K, V> QSet<K> defaultKeySet(MultiQMap<K, V> map) {
		return map.entrySet().mapEquivalent(map.getKeyType(), MultiQEntry::getKey, null);
	}

	@Override
	Qollection<V> get(Object key);

	/**
	 * <p>
	 * A default implementation of {@link #get(Object)}.
	 * </p>
	 * <p>
	 * No {@link MultiQMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #entrySet()} methods. {@link #defaultEntrySet(MultiQMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(MultiQMap)} or {@link #defaultGet(MultiQMap, Object)}. Either {@link #entrySet()} or both {@link #keySet()} and
	 * {@link #get(Object)} must be custom. If an implementation supplies custom {@link #keySet()} and {@link #get(Object)} implementations,
	 * it may use {@link #defaultEntrySet(MultiQMap)} for its {@link #entrySet()} . If an implementation supplies a custom
	 * {@link #entrySet()} implementation, it may use {@link #defaultKeySet(MultiQMap)} and {@link #defaultGet(MultiQMap, Object)} for its
	 * {@link #keySet()} and {@link #get(Object)} implementations, respectively. Using default implementations for both will result in
	 * infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create an entry set for
	 * @param key The key to get the value collection for
	 * @return A key set for the map
	 */
	public static <K, V> Qollection<V> defaultGet(MultiQMap<K, V> map, Object key) {
		if (key != null && !map.getKeyType().isAssignableFrom(key.getClass()))
			return QList.constant(map.getValueType());

		QSet<? extends MultiQEntry<K, V>> entries = map.entrySet();
		class KeyEntry extends Qollection.ConstantQollection<Object> implements MultiEntry<Object, Object> {
			KeyEntry() {
				super(TypeToken.of(Object.class), Collections.emptyList());
			}

			@Override
			public Object getKey() {
				return key;
			}

			@Override
			public int hashCode() {
				return Objects.hashCode(key);
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof MultiEntry && Objects.equals(((MultiEntry<?, ?>) obj).getKey(), key);
			}

			@Override
			public String toString() {
				return String.valueOf(key);
			}
		}
		KeyEntry keyEntry = new KeyEntry();

		org.qommons.value.Value<? extends MultiQEntry<K, V>> equiv = entries.equivalent(keyEntry);
		return map.entryFor((K) key, equiv);
	}

	@Override
	QSet<? extends MultiQEntry<K, V>> entrySet();

	/**
	 * <p>
	 * A default implementation of {@link #entrySet()}.
	 * </p>
	 * <p>
	 * No {@link MultiQMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #entrySet()} methods. {@link #defaultEntrySet(MultiQMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(MultiQMap)} or {@link #defaultGet(MultiQMap, Object)}. Either {@link #entrySet()} or both {@link #keySet()} and
	 * {@link #get(Object)} must be custom. If an implementation supplies custom {@link #keySet()} and {@link #get(Object)} implementations,
	 * it may use {@link #defaultEntrySet(MultiQMap)} for its {@link #entrySet()} . If an implementation supplies a custom
	 * {@link #entrySet()} implementation, it may use {@link #defaultKeySet(MultiQMap)} and {@link #defaultGet(MultiQMap, Object)} for its
	 * {@link #keySet()} and {@link #get(Object)} implementations, respectively. Using default implementations for both will result in
	 * infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create an entry set for
	 * @return An entry set for the map
	 */
	public static <K, V> QSet<? extends MultiQEntry<K, V>> defaultEntrySet(MultiQMap<K, V> map) {
		TypeToken<MultiQEntry<K, V>> entryType = new TypeToken<MultiQEntry<K, V>>() {}//
			.where(new TypeParameter<K>() {}, map.getKeyType())//
			.where(new TypeParameter<V>() {}, map.getValueType());
		return map.keySet().mapEquivalent(entryType, map::entryFor, null);
	}

	@Override
	default Qollection<V> values() {
		return Qollection.flatten(entrySet());
	}

	@Override
	default boolean add(K key, V value) {
		return get(key).add(value);
	}

	/**
	 * @param key The key to store the value by
	 * @param values The values to store
	 * @return Whether the map was changed as a result
	 */
	@Override
	default boolean addAll(K key, Collection<? extends V> values) {
		return get(key).addAll(values);
	}

	@Override
	default boolean remove(K key, Object value) {
		return get(key).remove(value);
	}

	@Override
	default boolean removeAll(K key) {
		Qollection<V> values = get(key);
		boolean ret = !values.isEmpty();
		values.clear();
		return ret;
	}

	/**
	 * @param key The key to get the entry for
	 * @return A multi-entry that represents the given key's presence in this map. Never null.
	 */
	default MultiQEntry<K, V> entryFor(K key) {
		Qollection<V> values = get(key);
		if (values instanceof MultiQEntry)
			return (MultiQEntry<K, V>) values;
		else if (values instanceof QList)
			return new ObsMultiEntryList<>(this, key, (QList<V>) values);
		else if (values instanceof SortedQSet)
			return new ObsMultiEntrySortedSet<>(this, key, (SortedQSet<V>) values);
		else if (values instanceof OrderedQollection)
			return new ObsMultiEntryOrdered<>(this, key, (OrderedQollection<V>) values);
		else if (values instanceof QSet)
			return new ObsMultiEntrySet<>(this, key, (QSet<V>) values);
		else
			return new ObsMultiEntryImpl<>(this, key, values);
	}

	/**
	 * @param key The key to get the entry for
	 * @param values The values to represent in the entry
	 * @return A multi-entry that represents the given key and values
	 */
	default MultiQEntry<K, V> entryFor(K key, Value<? extends MultiQEntry<K, V>> values) {
		if (TypeToken.of(ValueSortedMultiEntry.class).isAssignableFrom(values.getType()))
			return new ObsMultiEntrySortedSet<>(this, key, getValueType(), (Value<? extends ValueSortedMultiEntry<K, V>>) values);
		else if (TypeToken.of(QList.class).isAssignableFrom(values.getType())) {
			return new ObsMultiEntryList<>(this, key, getValueType(), (Value<? extends QList<V>>) values);
		} else if (TypeToken.of(SortedQSet.class).isAssignableFrom(values.getType())) {
			return new ObsMultiEntrySortedSet<>(this, key, getValueType(), (Value<? extends SortedQSet<V>>) values);
		} else if (TypeToken.of(OrderedQollection.class).isAssignableFrom(values.getType())) {
			return new ObsMultiEntryOrdered<>(this, key, getValueType(), (Value<? extends OrderedQollection<V>>) values);
		} else if (TypeToken.of(QSet.class).isAssignableFrom(values.getType())) {
			return new ObsMultiEntrySet<>(this, key, getValueType(), (Value<? extends QSet<V>>) values);
		} else {
			return new ObsMultiEntryImpl<>(this, key, getValueType(), values);
		}
	}

	/** @return A collection of plain (non-observable) {@link java.util.Map.Entry entries}, one for each value in this map */
	default Qollection<Map.Entry<K, V>> singleEntries() {
		class DefaultMapEntry implements Map.Entry<K, V> {
			private final K theKey;

			private final V theValue;

			DefaultMapEntry(K key, V value) {
				theKey = key;
				theValue = value;
			}

			@Override
			public K getKey() {
				return theKey;
			}

			@Override
			public V getValue() {
				return theValue;
			}

			@Override
			public V setValue(V value) {
				throw new UnsupportedOperationException();
			}

			@Override
			public int hashCode() {
				return Objects.hashCode(theKey);
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof Map.Entry && Objects.equals(((Map.Entry<?, ?>) obj).getKey(), theKey);
			}

			@Override
			public String toString() {
				return theKey + "=" + theValue;
			}
		}
		return Qollection.flatten(entrySet().map(entry -> entry.map(value -> new DefaultMapEntry(entry.getKey(), value))));
	}

	/**
	 * @return An observable map with the same key set as this map and whose values are one of the elements in this multi-map for each key
	 */
	default QMap<K, V> unique() {
		MultiQMap<K, V> outer = this;
		class UniqueMap implements QMap<K, V> {
			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<V> getValueType() {
				return outer.getValueType();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public QSet<K> keySet() {
				return outer.keySet();
			}

			@Override
			public Value<V> valueOf(Object key) {
				return outer.get(key).find(value -> true);
			}

			@Override
			public QSet<? extends QMapEntry<K, V>> valueEntries() {
				return QMap.defaultValueEntries(this);
			}

			@Override
			public String toString() {
				return entrySet().toString();
			}
		}
		return new UniqueMap();
	}

	/**
	 * @param <T> The type of values to map to
	 * @param map The function to map values
	 * @return A map with the same key set, but with its values mapped according to the given mapping function
	 */
	default <T> MultiQMap<K, T> map(Function<? super V, T> map) {
		MultiQMap<K, V> outer = this;
		return new MultiQMap<K, T>() {
			private TypeToken<T> theValueType = (TypeToken<T>) TypeToken.of(map.getClass())
				.resolveType(Function.class.getTypeParameters()[1]);

			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<T> getValueType() {
				return theValueType;
			}

			@Override
			public QSet<K> keySet() {
				return outer.keySet();
			}

			@Override
			public Qollection<T> get(Object key) {
				return outer.get(key).map(map);
			}

			@Override
			public QSet<? extends MultiQEntry<K, T>> entrySet() {
				return MultiQMap.defaultEntrySet(this);
			}

			@Override
			public String toString() {
				return entrySet().toString();
			}
		};
	}

	/** @return An immutable copy of this map */
	default MultiQMap<K, V> immutable() {
		MultiQMap<K, V> outer = this;
		return new MultiQMap<K, V>() {
			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<V> getValueType() {
				return outer.getValueType();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public QSet<K> keySet() {
				return outer.keySet().immutable();
			}

			@Override
			public Qollection<V> get(Object key) {
				return outer.get(key).immutable();
			}

			@Override
			public QSet<? extends MultiQEntry<K, V>> entrySet() {
				return outer.entrySet().immutable();
			}

			@Override
			public String toString() {
				return entrySet().toString();
			}
		};
	}

	/**
	 * Simple multi-entry implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsMultiEntryImpl<K, V> implements MultiQEntry<K, V> {
		private final MultiQMap<K, V> theMap;
		private final K theKey;

		private final TypeToken<V> theValueType;

		private final Value<? extends Qollection<V>> theValues;

		ObsMultiEntryImpl(MultiQMap<K, V> map, K key, Qollection<V> values) {
			this(map, key, values.getType(),
				Value.constant(new TypeToken<Qollection<V>>() {}.where(new TypeParameter<V>() {}, values.getType()), values));
		}

		ObsMultiEntryImpl(MultiQMap<K, V> map, K key, TypeToken<V> valueType, Value<? extends Qollection<V>> values) {
			theMap = map;
			theKey = key;
			theValueType = valueType;
			theValues = values;
		}

		protected MultiQMap<K, V> getMap() {
			return theMap;
		}

		protected Qollection<V> getWrapped() {
			return theValues.get();
		}

		protected Value<? extends Qollection<V>> getWrappedObservable() {
			return theValues;
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public TypeToken<V> getType() {
			return theValueType;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theMap.lock(write, cause);
		}

		@Override
		public int size() {
			Qollection<V> current = getWrapped();
			return current != null ? current.size() : 0;
		}

		// TODO Replace with spliterator()
		@Override
		public Iterator<V> iterator() {
			Qollection<V> current = getWrapped();
			return current != null ? current.iterator() : new Iterator<V>() {
				@Override
				public boolean hasNext() {
					return false;
				}

				@Override
				public V next() {
					throw new java.util.NoSuchElementException();
				}
			};
		}

		@Override
		public Quiterator<V> spliterator() {
			// TODO Auto-generated method stub
		}

		@Override
		public boolean add(V e) {
			return theMap.add(theKey, e);
		}

		@Override
		public boolean addAll(Collection<? extends V> c) {
			return theMap.addAll(theKey, c);
		}

		@Override
		public boolean remove(Object o) {
			Qollection<V> current = getWrapped();
			if (current == null)
				return false;
			return current.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			Qollection<V> current = getWrapped();
			if (current == null)
				return false;
			return current.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			Qollection<V> current = getWrapped();
			if (current == null)
				return false;
			return current.retainAll(c);
		}

		@Override
		public void clear() {
			Qollection<V> current = getWrapped();
			if (current == null)
				return;
			current.clear();
		}

		@Override
		public String canRemove(Object value) {
			Qollection<V> current = getWrapped();
			if (current == null)
				return "Removal is not currently enabled for this collection";
			return current.canRemove(value);
		}

		@Override
		public String canAdd(V value) {
			Qollection<V> current = getWrapped();
			if (current == null)
				return "Addition is not currently enabled for this collection";
			return current.canAdd(value);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theKey);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			return obj instanceof MultiEntry && Objects.equals(theKey, ((MultiEntry<?, ?>) obj).getKey());
		}

		@Override
		public String toString() {
			return theKey + "=" + theValues.get();
		}
	}

	/**
	 * Simple ordered multi-entry implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsMultiEntryOrdered<K, V> extends ObsMultiEntryImpl<K, V> implements OrderedQollection<V> {
		public ObsMultiEntryOrdered(MultiQMap<K, V> map, K key, OrderedQollection<V> values) {
			super(map, key, values);
		}

		public ObsMultiEntryOrdered(MultiQMap<K, V> map, K key, TypeToken<V> valueType, Value<? extends OrderedQollection<V>> values) {
			super(map, key, valueType, values);
		}

		@Override
		protected OrderedQollection<V> getWrapped() {
			return (OrderedQollection<V>) super.getWrapped();
		}

		@Override
		protected Value<? extends OrderedQollection<V>> getWrappedObservable() {
			return (Value<? extends OrderedQollection<V>>) super.getWrappedObservable();
		}

		@Override
		public Quiterator<V> spliterator() {
			// TODO Auto-generated method stub
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<V>> onElement) {
			return OrderedQollection.flattenValue(getWrappedObservable()).onOrderedElement(onElement);
		}
	}

	/**
	 * Simple multi-entry sorted set implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsMultiEntrySortedSet<K, V> extends ObsMultiEntryOrdered<K, V> implements ValueSortedMultiEntry<K, V> {
		public ObsMultiEntrySortedSet(MultiQMap<K, V> map, K key, SortedQSet<V> values) {
			super(map, key, values);
		}

		public ObsMultiEntrySortedSet(MultiQMap<K, V> map, K key, TypeToken<V> valueType, Value<? extends SortedQSet<V>> values) {
			super(map, key, valueType, values);
		}

		@Override
		protected SortedQSet<V> getWrapped() {
			return (SortedQSet<V>) super.getWrapped();
		}

		@Override
		protected Value<? extends SortedQSet<V>> getWrappedObservable() {
			return (Value<? extends SortedQSet<V>>) super.getWrappedObservable();
		}

		@Override
		public Iterable<V> descending() {
			return () -> {
				return descendingIterator();
			};
		}

		@Override
		public V pollFirst() {
			SortedQSet<V> current = getWrapped();
			return current != null ? current.pollFirst() : null;
		}

		@Override
		public V pollLast() {
			SortedQSet<V> current = getWrapped();
			return current != null ? current.pollLast() : null;
		}

		@Override
		public Iterator<V> descendingIterator() {
			SortedQSet<V> current = getWrapped();
			return current != null ? current.descendingIterator() : new Iterator<V>() {
				@Override
				public boolean hasNext() {
					return false;
				}

				@Override
				public V next() {
					throw new java.util.NoSuchElementException();
				}
			};
		}

		@Override
		public Comparator<? super V> comparator() {
			SortedQSet<V> current = getWrapped();
			return current != null ? current.comparator() : null;
		}

		@Override
		public V first() {
			SortedQSet<V> current = getWrapped();
			if (current != null)
				return current.first();
			throw new java.util.NoSuchElementException();
		}

		@Override
		public V last() {
			SortedQSet<V> current = getWrapped();
			if (current != null)
				return current.last();
			throw new java.util.NoSuchElementException();
		}

		@Override
		public Iterable<V> iterateFrom(V element, boolean included, boolean reversed) {
			SortedQSet<V> current = getWrapped();
			return current == null ? Collections.EMPTY_LIST : current.iterateFrom(element, included, reversed);
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<V>> onElement) {
			return ObservableReversibleCollection.flattenValue(getWrappedObservable()).onElementReverse(onElement);
		}
	}

	/**
	 * Simple multi-entry list implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsMultiEntryList<K, V> extends ObsMultiEntryOrdered<K, V> implements QList<V> {
		public ObsMultiEntryList(MultiQMap<K, V> map, K key, QList<V> values) {
			super(map, key, values);
		}

		public ObsMultiEntryList(MultiQMap<K, V> map, K key, TypeToken<V> valueType, Value<? extends QList<V>> values) {
			super(map, key, valueType, values);
		}

		@Override
		protected Value<? extends QList<V>> getWrappedObservable() {
			return (Value<? extends QList<V>>) super.getWrappedObservable();
		}

		@Override
		protected QList<V> getWrapped() {
			return (QList<V>) super.getWrapped();
		}

		@Override
		public boolean addAll(int index, Collection<? extends V> c) {
			QList<V> current = getWrapped();
			if (current == null) {
				if (index == 0)
					return getMap().addAll(getKey(), c);
				else
					throw new IndexOutOfBoundsException(index + " of 0");
			} else
				return current.addAll(index, c);
		}

		@Override
		public V get(int index) {
			QList<V> current = getWrapped();
			if (current == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			else
				return current.get(index);
		}

		@Override
		public V set(int index, V element) {
			QList<V> current = getWrapped();
			if (current == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			else
				return current.set(index, element);
		}

		@Override
		public void add(int index, V element) {
			QList<V> current = getWrapped();
			if (current == null) {
				if (index == 0)
					getMap().add(getKey(), element);
				else
					throw new IndexOutOfBoundsException(index + " of 0");
			} else
				current.add(index, element);
		}

		@Override
		public V remove(int index) {
			QList<V> current = getWrapped();
			if (current == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			return current.remove(index);
		}

		@Override
		public int indexOf(Object o) {
			QList<V> current = getWrapped();
			return current != null ? current.indexOf(o) : -1;
		}

		@Override
		public int lastIndexOf(Object o) {
			QList<V> current = getWrapped();
			return current != null ? current.lastIndexOf(o) : -1;
		}

		@Override
		public Subscription onElementReverse(Consumer<? super ObservableOrderedElement<V>> onElement) {
			return ObservableReversibleCollection.flattenValue(getWrappedObservable()).onElementReverse(onElement);
		}
	}

	/**
	 * Simple multi-entry set implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsMultiEntrySet<K, V> extends ObsMultiEntryImpl<K, V> implements QSet<V> {
		public ObsMultiEntrySet(MultiQMap<K, V> map, K key, QSet<V> values) {
			super(map, key, values);
		}

		public ObsMultiEntrySet(MultiQMap<K, V> map, K key, TypeToken<V> valueType, Value<? extends QSet<V>> values) {
			super(map, key, valueType, values);
		}

		@Override
		protected QSet<V> getWrapped() {
			return (QSet<V>) super.getWrapped();
		}
	}
}
