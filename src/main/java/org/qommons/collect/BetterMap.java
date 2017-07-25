package org.qommons.collect;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transactable;
import org.qommons.Transaction;

public interface BetterMap<K, V> extends TransactableMap<K, V> {
	@Override
	BetterSet<K> keySet();

	@Override
	BetterSet<Map.Entry<K, V>> entrySet();

	@Override
	BetterCollection<V> values();

	ElementId putElement(K key, V value);

	boolean forEntry(K key, Consumer<? super MapEntryHandle<K, V>> onElement);

	boolean forMutableEntry(K key, Consumer<? super MutableMapEntryHandle<K, V>> onElement);

	<X> X ofEntry(ElementId entryId, Function<? super MapEntryHandle<K, V>, X> onElement);

	<X> X ofMutableEntry(ElementId entryId, Function<? super MutableMapEntryHandle<K, V>, X> onElement);

	default BetterMap<K, V> reverse() {}

	@Override
	default int size() {
		return keySet().size();
	}

	@Override
	default boolean isEmpty() {
		return keySet().isEmpty();
	}

	@Override
	default boolean containsKey(Object key) {
		return keySet().contains(key);
	}

	@Override
	default boolean containsValue(Object value) {
		return values().contains(value);
	}

	@Override
	default V get(Object key) {
		if (!keySet().belongs(key))
			return null;
		Object[] value = new Object[1];
		if (!forEntry((K) key, entry -> value[0] = entry.get()))
			return null;
		return (V) value[0];
	}

	@Override
	default V remove(Object key) {
		if (!keySet().belongs(key))
			return null;
		Object[] value = new Object[1];
		if (!forMutableEntry((K) key, entry -> {
			value[0] = entry.get();
			entry.remove();
		}))
			return null;
		return (V) value[0];
	}

	@Override
	default void putAll(Map<? extends K, ? extends V> m) {
		try (Transaction t = lock(true, null); Transaction ct = Transactable.lock(m, false, null)) {
			for (Map.Entry<? extends K, ? extends V> entry : m.entrySet())
				put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	default void clear() {
		keySet().clear();
	}
}
