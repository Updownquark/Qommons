package org.qommons.collect;

public interface MultiQMap<K, V> extends MultiMap<K, V> {
	interface MultiQEntry<K, V> extends MultiEntry<K, V>, Qollection<V> {}

	@Override
	QSet<K> keySet();

	@Override
	Qollection<V> get(Object key);

	@Override
	Qollection<V> values();

	@Override
	QSet<? extends MultiQEntry<K, V>> entrySet();
}
