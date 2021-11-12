package org.qommons.collect;

import java.util.Map;

import org.qommons.Transactable;
import org.qommons.Transaction;

/**
 * A multi-map that is transactable
 * 
 * @param <K> The key type for the map
 * @param <V> The value type for the map
 */
public interface TransactableMultiMap<K, V> extends MultiMap<K, V>, Transactable {
	@Override
	default boolean isLockSupported() {
		return keySet().isLockSupported();
	}

	@Override
	TransactableSet<K> keySet();

	@Override
	TransactableCollection<V> get(K key);

	@Override
	default boolean putAll(Map<? extends K, ? extends V> values) {
		boolean changed = false;
		try (Transaction t = lock(true, null)) {
			for (Map.Entry<? extends K, ? extends V> entry : values.entrySet())
				changed |= add(entry.getKey(), entry.getValue());
		}
		return changed;
	}

	@Override
	default boolean putAll(MultiMap<? extends K, ? extends V> values) {
		boolean changed = false;
		try (Transaction t = lock(true, null)) {
			for (MultiEntry<? extends K, ? extends V> entry : values.entrySet())
				changed |= addAll(entry.getKey(), entry.getValues());
		}
		return changed;
	}

}
