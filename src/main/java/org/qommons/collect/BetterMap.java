package org.qommons.collect;

import java.util.Map;
import java.util.function.Consumer;

public interface BetterMap<K, V> extends Map<K, V> {
	@Override
	BetterSet<K> keySet();

	@Override
	BetterSet<Map.Entry<K, V>> entrySet();

	@Override
	BetterCollection<V> values();

	ElementId putElement(K key, V value);

	boolean forEntry(K key, Consumer<? super ElementHandle<? extends Map.Entry<K, V>>> onElement);
}
