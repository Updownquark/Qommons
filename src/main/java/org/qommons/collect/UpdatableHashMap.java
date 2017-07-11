package org.qommons.collect;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.qommons.collect.UpdatableHashSet.HashWrapper;
import org.qommons.collect.UpdatableSet.ElementUpdateResult;

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
		} else {
			HashWrapper<K> keyWrapper = theWrappersById.remove(key);
			if (keyWrapper != null) {
				return theMap.remove(keyWrapper).value;
			}
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
		Set<Map.Entry<HashWrapper<K>, MapValue<K, V>>> entries = theMap.entrySet();
		class EntrySet extends AbstractSet<Map.Entry<K, V>> {
			@Override
			public Iterator<Map.Entry<K, V>> iterator() {
				Iterator<Map.Entry<HashWrapper<K>, MapValue<K, V>>> iter = entries.iterator();
				return new Iterator<Map.Entry<K, V>>() {
					@Override
					public boolean hasNext() {
						return iter.hasNext();
					}

					@Override
					public Map.Entry<K, V> next() {
						Map.Entry<HashWrapper<K>, MapValue<K, V>> entry = iter.next();
						return new Map.Entry<K, V>() {
							@Override
							public K getKey() {
								return entry.getKey().value;
							}

							@Override
							public V getValue() {
								return entry.getValue().value;
							}

							@Override
							public V setValue(V value) {
								V old = entry.getValue().value;
								entry.getValue().value = value;
								return old;
							}

							@Override
							public int hashCode() {
								return entry.getKey().hashCode();
							}

							@Override
							public boolean equals(Object obj) {
								if (!(obj instanceof Map.Entry))
									return false;
								return Objects.equals(entry.getKey(), ((Map.Entry<?, ?>) obj).getKey());
							}

							@Override
							public String toString() {
								return getKey() + "=" + getValue();
							}
						};
					}

					@Override
					public void remove() {
						iter.remove();
					}
				};
			}

			@Override
			public int size() {
				return entries.size();
			}

			@Override
			public boolean isEmpty() {
				return entries.isEmpty();
			}

			@Override
			public boolean contains(Object o) {
				if (!(o instanceof Map.Entry))
					return false;
				return UpdatableHashMap.this.containsKey(((Map.Entry<?, ?>) o).getKey());
			}

			@Override
			public void clear() {
				entries.clear();
			}
		}
		return new EntrySet();
	}

	@Override
	public ElementUpdateResult update(K key) {
		HashWrapper<K> wrapper = theWrappersById.get(key);
		if (wrapper != null) {
			int hashCode = Objects.hashCode(wrapper.value);
			if (hashCode == wrapper.hashCode())
				return ElementUpdateResult.NotChanged;
			MapValue<K, V> valueHolder = theMap.remove(wrapper);
			wrapper.setHashCode(hashCode);
			if (theMap.computeIfAbsent(wrapper, w -> valueHolder) == valueHolder)
				return ElementUpdateResult.ReStored;
			else
				return ElementUpdateResult.Removed;
		} else
			return ElementUpdateResult.NotFound;
	}

	@Override
	public UpdatableSet<K> keySet() {
		Set<HashWrapper<K>> keySet = theMap.keySet();
		class UpdatableKeySet extends AbstractSet<K> implements UpdatableSet<K> {
			@Override
			public ElementUpdateResult update(K value) {
				return UpdatableHashMap.this.update(value);
			}

			@Override
			public Iterator<K> iterator() {
				Iterator<HashWrapper<K>> iter = keySet.iterator();
				return new Iterator<K>() {
					@Override
					public boolean hasNext() {
						return iter.hasNext();
					}

					@Override
					public K next() {
						return iter.next().value;
					}

					@Override
					public void remove() {
						iter.remove();
					}
				};
			}

			@Override
			public int size() {
				return keySet.size();
			}

			@Override
			public boolean isEmpty() {
				return keySet.isEmpty();
			}

			@Override
			public boolean contains(Object o) {
				return keySet.contains(new HashWrapper<>(o));
			}

			@Override
			public boolean remove(Object o) {
				if (keySet.remove(new HashWrapper<>(o))) {
					return true;
				} else {
					HashWrapper<K> wrapper = theWrappersById.remove(o);
					if (wrapper != null) {
						keySet.remove(wrapper);
						return true;
					} else
						return false;
				}
			}

			@Override
			public void clear() {
				keySet.clear();
			}
		}
		return new UpdatableKeySet();
	}
}
