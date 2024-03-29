package org.qommons.tree;

import java.util.Map;
import java.util.function.Function;

import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.ElementId;

/**
 * A {@link BetterSortedMap} whose entries implement {@link BinaryTreeEntry}
 * 
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public interface TreeBasedSortedMap<K, V> extends BetterSortedMap<K, V> {
	@Override
	default BinaryTreeEntry<K, V> putEntry(K key, V value, boolean first) {
		return (BinaryTreeEntry<K, V>) BetterSortedMap.super.putEntry(key, value, first);
	}

	@Override
	BinaryTreeEntry<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first);

	@Override
	BinaryTreeEntry<K, V> getEntry(K key);

	@Override
	BinaryTreeEntry<K, V> getEntryById(ElementId entryId);

	@Override
	MutableBinaryTreeEntry<K, V> mutableEntry(ElementId entryId);

	@Override
	TreeBasedSet<K> keySet();

	@Override
	TreeBasedSet<Map.Entry<K, V>> entrySet();

	@Override
	default BinaryTreeEntry<K, V> search(Comparable<? super K> search, BetterSortedList.SortedSearchFilter filter) {
		return (BinaryTreeEntry<K, V>) BetterSortedMap.super.search(search, filter);
	}

	@Override
	BinaryTreeEntry<K, V> searchEntries(Comparable<? super java.util.Map.Entry<K, V>> search, BetterSortedList.SortedSearchFilter filter);

	@Override
	default BinaryTreeEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId afterKey, ElementId beforeKey,
		boolean first, Runnable preAdd, Runnable postAdd) {
		return (BinaryTreeEntry<K, V>) BetterSortedMap.super.getOrPutEntry(key, value, afterKey, beforeKey, first, preAdd, postAdd);
	}

	@Override
	default TreeBasedSet<K> navigableKeySet() {
		return (TreeBasedSet<K>) BetterSortedMap.super.navigableKeySet();
	}
}
