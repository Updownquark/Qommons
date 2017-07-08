package org.qommons.collect;

import java.util.*;

import org.qommons.collect.UpdatableHashSet.HashWrapper;

public class UpdatableHashMap<K, V> extends AbstractMap<K, V> implements UpdatableMap<K, V> {
	private static class MapValue<K, V> {
		final K key;
		V value;

		MapValue(K key, V value) {
			this.key = key;
			this.value = value;
		}
	}

	private final LinkedHashMap<HashWrapper<K>, MapValue<K, V>> theMap;
	private final IdentityHashMap<K, HashWrapper<K>> theWrappersById;

	public UpdatableHashMap() {
		theMap = new LinkedHashMap<>();
		theWrappersById = new IdentityHashMap<>();
	}

	@Override
	public int size() {
		return theMap.size();
	}

	@Override
	public boolean isEmpty() {
		return theMap.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return theMap.containsKey(new HashWrapper<>(key));
	}

	@Override
	public V get(Object key) {
		MapValue<K, V> valueHolder = theMap.get(new HashWrapper<>(key));
		return valueHolder == null ? null : valueHolder.value;
	}

	@Override
	public V put(K key, V value) {
		HashWrapper<K> wrapper = new HashWrapper<>(key);
		MapValue<K, V> valueHolder = theMap.get(wrapper);
		if (valueHolder != null) {
			V old = valueHolder.value;
			valueHolder.value = value;
			return old;
		}
		theMap.put(wrapper, new MapValue<>(key, value));
		theWrappersById.put(key, wrapper);
		return null;
	}

	@Override
	public V remove(Object key) {
		MapValue<K, V> valueHolder = theMap.remove(new HashWrapper<>(key));
		if (valueHolder != null) {
			theWrappersById.remove(valueHolder.key);
			return valueHolder.value;
		}
		return null;
	}

	@Override
	public void clear() {
		theMap.clear();
		theWrappersById.clear();
	}

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean update(K key) {
		HashWrapper<K> wrapper = theWrappersById.get(key);
		if (wrapper != null) {
			MapValue<K, V> valueHolder = theMap.remove(wrapper);
			wrapper.updateHashCode();
			theMap.put(wrapper, valueHolder);
			return true;
		}
		return false;
	}

	@Override
	public UpdatableSet<K> keySet() {
		Set<HashWrapper<K>> keySet = theMap.keySet();
		class UpdatableKeySet extends AbstractSet<K> implements UpdatableSet<K> {
			@Override
			public boolean update(K value) {
				return UpdatableHashMap.this.update(value);
			}

			@Override
			public Iterator<K> iterator() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public int size() {
				// TODO Auto-generated method stub
				return 0;
			}
		}
	}
}
