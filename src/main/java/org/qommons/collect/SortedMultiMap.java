package org.qommons.collect;

import java.util.NavigableSet;

public interface SortedMultiMap<K, V> extends MultiMap<K, V> {
	@Override
	NavigableSet<K> keySet();

	@Override
	NavigableSet<? extends MultiEntry<K, V>> entrySet();

	MultiEntry<K, V> lowerEntry(K key);
	MultiEntry<K, V> floorEntry(K key);
	MultiEntry<K, V> ceilingEntry(K key);
	MultiEntry<K, V> higherEntry(K key);
	MultiEntry<K, V> firstEntry();
	MultiEntry<K, V> lastEntry();

	SortedMultiMap<K, V> subMap(K low, boolean lowIncluded, K high, boolean highIncluded);
	default SortedMultiMap<K, V> subMap(K low, K high) {
		return subMap(low, true, high, false);
	}
	SortedMultiMap<K, V> headMap(K high, boolean highIncluded);
	default SortedMultiMap<K, V> headMap(K high) {
		return headMap(high, false);
	}
	SortedMultiMap<K, V> tailMap(K low, boolean lowIncluded);
	default SortedMultiMap<K, V> tailMap(K low) {
		return tailMap(low, true);
	}

	SortedMultiMap<K, V> reverse();
}
