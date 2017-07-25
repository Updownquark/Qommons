package org.qommons.collect;

import java.util.Map;
import java.util.NavigableMap;
import java.util.function.Consumer;

public interface BetterSortedMap<K, V> extends BetterMap<K, V>, NavigableMap<K, V> {
	@Override
	BetterSortedSet<K> keySet();

	@Override
	BetterSortedSet<Map.Entry<K, V>> entrySet();

	/**
	 * Returns a value at or adjacent to another value
	 *
	 * @param value The relative value
	 * @param up Whether to get the closest value greater or less than the given value
	 * @return The result of the search, or null if no such value was found
	 */
	Map.Entry<K, V> relativeEntry(Comparable<? super K> search, boolean up);

	/**
	 * @param value The value to search for
	 * @param onElement The action to perform on the element containing the given value, if found
	 * @return Whether such a value was found
	 */
	@Override
	default boolean forEntry(K key, Consumer<? super MapEntryHandle<K, V>> onElement) {
		boolean[] success = new boolean[1];
		forEntry(v -> comparator().compare(key, v), el -> {
			if (comparator().compare(key, el.getKey()) == 0) {
				onElement.accept(el);
				success[0] = true;
			}
		}, true);
		return success[0];
	}

	/**
	 * @param value The value to search for
	 * @param onElement The action to perform on the element containing the given value, if found
	 * @return Whether such a value was found
	 */
	@Override
	default boolean forMutableEntry(K key, Consumer<? super MutableMapEntryHandle<K, V>> onElement) {
		boolean[] success = new boolean[1];
		forMutableEntry(v -> comparator().compare(key, v), el -> {
			if (comparator().compare(key, el.getKey()) == 0) {
				onElement.accept(el);
				success[0] = true;
			}
		}, true);
		return success[0];
	}

	/**
	 * Searches for an entry in this sorted map
	 * 
	 * @param search The search to use. Must follow this sorted map's key ordering.
	 * @param onElement The action to perform on the closest found element in the sorted map
	 * @param up Whether to prefer a greater or lesser element. This parameter will be ignored if such an element is not found. In other
	 *        words, this method will always provide an element unless this set is empty.
	 * @return True unless this set was empty
	 */
	boolean forEntry(Comparable<? super K> search, Consumer<? super MapEntryHandle<K, V>> onElement, boolean up);

	/**
	 * Like {@link #forEntry(Comparable, Consumer, boolean)}, but provides a mutable entry
	 * 
	 * @param search The search to use. Must follow this sorted set's ordering.
	 * @param onElement The action to perform on the closest found element in the sorted set
	 * @param up Whether to prefer a greater or lesser element. This parameter will be ignored if such an element is not found. In other
	 *        words, this method will always provide an element unless this set is empty.
	 * @return True unless this set was empty
	 */
	boolean forMutableEntry(Comparable<? super K> search, Consumer<? super MutableMapEntryHandle<K, V>> onElement, boolean up);

	@Override
	default K firstKey() {
		return keySet().first();
	}

	@Override
	default K lastKey() {
		return keySet().last();
	}

	@Override
	default java.util.Map.Entry<K, V> lowerEntry(K key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	default K lowerKey(K key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	default java.util.Map.Entry<K, V> floorEntry(K key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	default K floorKey(K key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	default java.util.Map.Entry<K, V> ceilingEntry(K key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	default K ceilingKey(K key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	default java.util.Map.Entry<K, V> higherEntry(K key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	default K higherKey(K key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	default java.util.Map.Entry<K, V> firstEntry() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	default java.util.Map.Entry<K, V> lastEntry() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	default java.util.Map.Entry<K, V> pollFirstEntry() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	default java.util.Map.Entry<K, V> pollLastEntry() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	default BetterSortedMap<K, V> reverse() {}

	@Override
	default BetterSortedMap<K, V> descendingMap() {
		return reverse();
	}

	@Override
	default BetterSortedSet<K> navigableKeySet() {
		return keySet();
	}

	@Override
	default BetterSortedSet<K> descendingKeySet() {
		return keySet().reverse();
	}

	@Override
	default BetterSortedMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		return subMap(k -> {
			int compare = comparator().compare(fromKey, k);
			if (!fromInclusive && compare == 0)
				compare = 1;
			return compare;
		}, k -> {
			int compare = comparator().compare(toKey, k);
			if (!toInclusive && compare == 0)
				compare = -1;
			return compare;
		});
	}

	default BetterSortedMap<K, V> subMap(Comparable<? super K> from, Comparable<? super K> to) {
		return new BetterSubMap<>(this, from, to);
	}

	@Override
	default BetterSortedMap<K, V> headMap(K toKey, boolean inclusive) {
		return subMap(null, k -> {
			int compare = comparator().compare(toKey, k);
			if (!inclusive && compare == 0)
				compare = -1;
			return compare;
		});
	}

	@Override
	default BetterSortedMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return subMap(k -> {
			int compare = comparator().compare(fromKey, k);
			if (!inclusive && compare == 0)
				compare = 1;
			return compare;
		}, null);
	}

	@Override
	default BetterSortedSet<E> subSet(E fromElement, E toElement) {
		return subSet(fromElement, true, toElement, false);
	}

	@Override
	default BetterSortedSet<E> headSet(E toElement) {
		return headSet(toElement, false);
	}

	@Override
	default BetterSortedSet<E> tailSet(E fromElement) {
		return tailSet(fromElement, true);
	}
}
