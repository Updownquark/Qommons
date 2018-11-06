package org.qommons.collect;

import java.util.Map;

import org.qommons.StructuredTransactable;

/**
 * A transactable map
 * 
 * @param <K> The key type for the map
 * @param <V> The value type for the map
 */
public interface TransactableMap<K, V> extends Map<K, V>, StructuredTransactable {
	@Override
	TransactableSet<K> keySet();

	@Override
	TransactableSet<Map.Entry<K, V>> entrySet();

	@Override
	TransactableCollection<V> values();
}
