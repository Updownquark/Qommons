package org.qommons.collect;

import com.google.common.reflect.TypeToken;

public interface MultiQMap<K, V> extends MultiMap<K, V> {
	interface MultiQEntry<K, V> extends MultiEntry<K, V>, Qollection<V> {}

	TypeToken<K> getKeyType();

	TypeToken<V> getValueType();

	@Override
	QSet<K> keySet();

	@Override
	Qollection<V> get(Object key);

	@Override
	Qollection<V> values();

	@Override
	QSet<? extends MultiQEntry<K, V>> entrySet();
}
