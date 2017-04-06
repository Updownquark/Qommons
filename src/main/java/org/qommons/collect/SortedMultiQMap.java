package org.qommons.collect;

import java.util.Comparator;

import org.qommons.value.Value;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

public interface SortedMultiQMap<K, V> extends MultiQMap<K, V> {
	/**
	 * A multi-map entry that can be sorted
	 *
	 * @param <K> The type of the key
	 * @param <V> The type of the value
	 */
	interface SortedMultiQEntry<K, V> extends MultiQEntry<K, V>, Comparable<SortedMultiQEntry<? extends K, ?>> {}

	/** @return The comparator by which this map's keys are sorted */
	default Comparator<? super K> comparator() {
		return keySet().comparator();
	}

	@Override
	SortedQSet<K> keySet();

	/**
	 * <p>
	 * A default implementation of {@link #keySet()}.
	 * </p>
	 * <p>
	 * No {@link MultiQMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #entrySet()} methods. {@link #defaultEntrySet(SortedMultiQMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(SortedMultiQMap)} or {@link MultiQMap#defaultGet(MultiQMap, Object)}. Either {@link #entrySet()} or both
	 * {@link #keySet()} and {@link #get(Object)} must be custom. If an implementation supplies custom {@link #keySet()} and
	 * {@link #get(Object)} implementations, it may use {@link #defaultEntrySet(SortedMultiQMap)} for its {@link #entrySet()} . If an
	 * implementation supplies a custom {@link #entrySet()} implementation, it may use {@link #defaultKeySet(SortedMultiQMap)} and
	 * {@link MultiQMap#defaultGet(MultiQMap, Object)} for its {@link #keySet()} and {@link #get(Object)} implementations, respectively.
	 * Using default implementations for both will result in infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create a key set for
	 * @return A key set for the map
	 */
	public static <K, V> SortedQSet<K> defaultKeySet(SortedMultiQMap<K, V> map) {
		return map.entrySet().mapEquivalent(map.getKeyType(), MultiQEntry::getKey, map.comparator());
	}

	@Override
	SortedQSet<? extends SortedMultiQEntry<K, V>> entrySet();

	/**
	 * <p>
	 * A default implementation of {@link #entrySet()}.
	 * </p>
	 * <p>
	 * No {@link MultiQMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #entrySet()} methods. {@link #defaultEntrySet(SortedMultiQMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(SortedMultiQMap)} or {@link MultiQMap#defaultGet(MultiQMap, Object)}. Either {@link #entrySet()} or both
	 * {@link #keySet()} and {@link #get(Object)} must be custom. If an implementation supplies custom {@link #keySet()} and
	 * {@link #get(Object)} implementations, it may use {@link #defaultEntrySet(SortedMultiQMap)} for its {@link #entrySet()} . If an
	 * implementation supplies a custom {@link #entrySet()} implementation, it may use {@link #defaultKeySet(SortedMultiQMap)} and
	 * {@link MultiQMap#defaultGet(MultiQMap, Object)} for its {@link #keySet()} and {@link #get(Object)} implementations, respectively.
	 * Using default implementations for both will result in infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create an entry set for
	 * @return An entry set for the map
	 */
	public static <K, V> SortedQSet<? extends SortedMultiQEntry<K, V>> defaultEntrySet(SortedMultiQMap<K, V> map) {
		TypeToken<SortedMultiQEntry<K, V>> entryType = new TypeToken<SortedMultiQEntry<K, V>>() {}//
			.where(new TypeParameter<K>() {}, map.getKeyType())//
			.where(new TypeParameter<V>() {}, map.getValueType());
		return map.keySet().mapEquivalent(entryType, map::entryFor, null);
	}

	/**
	 * @param key The key to get the entry for
	 * @return A multi-entry that represents the given key's presence in this map. Never null.
	 */
	@Override
	default SortedMultiQEntry<K, V> entryFor(K key) {
		Qollection<V> values = get(key);
		if (values instanceof SortedMultiQEntry)
			return (SortedMultiQEntry<K, V>) values;
		else if (values instanceof QList)
			return new ObsSortedMultiEntryList<>(this, key, (QList<V>) values, comparator());
		else if (values instanceof SortedQSet)
			return new ObsSortedMultiEntrySortedSet<>(this, key, (SortedQSet<V>) values, comparator());
		else if (values instanceof OrderedQollection)
			return new ObsSortedMultiEntryOrdered<>(this, key, (OrderedQollection<V>) values, comparator());
		else if (values instanceof QSet)
			return new ObsSortedMultiEntrySet<>(this, key, (QSet<V>) values, comparator());
		else
			return new ObsSortedMultiEntryImpl<>(this, key, values, comparator());
	}

	@Override
	default MultiQEntry<K, V> entryFor(K key, Value<? extends MultiQEntry<K, V>> values) {
		if (values.getType().isAssignableFrom(QList.class)) {
			return new ObsSortedMultiEntryList<>(this, key, getValueType(), (Value<? extends QList<V>>) values, comparator());
		} else if (values.getType().isAssignableFrom(SortedQSet.class)) {
			return new ObsSortedMultiEntrySortedSet<>(this, key, getValueType(), (Value<? extends SortedQSet<V>>) values, comparator());
		} else if (values.getType().isAssignableFrom(OrderedQollection.class)) {
			return new ObsSortedMultiEntryOrdered<>(this, key, getValueType(), (Value<? extends OrderedQollection<V>>) values,
				comparator());
		} else if (values.getType().isAssignableFrom(QSet.class)) {
			return new ObsSortedMultiEntrySet<>(this, key, getValueType(), (Value<? extends QSet<V>>) values, comparator());
		} else {
			return new ObsSortedMultiEntryImpl<>(this, key, getValueType(), values, comparator());
		}
	}

	@Override
	default SortedQMap<K, V> unique() {
		// TODO Auto-generated method stub
	}

	@Override
	default SortedMultiQMap<K, V> immutable() {
		// TODO Auto-generated method stub
	}
}
