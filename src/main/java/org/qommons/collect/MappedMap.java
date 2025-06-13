package org.qommons.collect;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * <p>
 * A map with the same key set as a different map, but whose values are mapped with a function.
 * </p>
 * <p>
 * Removal is supported (if supported by the source), but not {@link Map#put(Object, Object)}.
 * </p>
 * 
 * @param <K> The key-type of the map
 * @param <V1> The value-type of the source map
 * @param <V2> The value-type of the mapped map
 */
public class MappedMap<K, V1, V2> extends AbstractMap<K, V2> {
	private final Map<K, V1> theSourceMap;
	private final Function<? super V1, ? extends V2> theMapping;
	private final Set<Map.Entry<K, V2>> theEntrySet;

	/**
	 * @param source The source map
	 * @param mapping The value mapping function
	 */
	public MappedMap(Map<K, V1> source, Function<? super V1, ? extends V2> mapping) {
		theSourceMap = source;
		theMapping = mapping;
		theEntrySet = new MappedSet<>(source.entrySet(), MappedEntry::new,
			e -> e instanceof Map.Entry && theSourceMap.containsKey(((Map.Entry<?, ?>) e).getKey()));
	}

	@Override
	public int size() {
		return theSourceMap.size();
	}

	@Override
	public boolean isEmpty() {
		return theSourceMap.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return theSourceMap.containsKey(key);
	}

	@Override
	public V2 get(Object key) {
		V1 value = theSourceMap.get(key);
		return value == null ? null : theMapping.apply(value);
	}

	@Override
	public Set<K> keySet() {
		return theSourceMap.keySet();
	}

	@Override
	public Collection<V2> values() {
		return new MappedCollection<>(theSourceMap.values(), theMapping);
	}

	@Override
	public Set<Map.Entry<K, V2>> entrySet() {
		return theEntrySet;
	}

	class MappedEntry implements Map.Entry<K, V2> {
		private final Map.Entry<K, V1> theSourceEntry;

		MappedEntry(Entry<K, V1> sourceEntry) {
			theSourceEntry = sourceEntry;
		}

		@Override
		public K getKey() {
			return theSourceEntry.getKey();
		}

		@Override
		public V2 getValue() {
			return theMapping.apply(theSourceEntry.getValue());
		}

		@Override
		public V2 setValue(V2 value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int hashCode() {
			return getKey().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof Map.Entry))
				return false;
			else
				return getKey().equals(((Map.Entry<?, ?>) obj).getKey());
		}

		@Override
		public String toString() {
			return getKey() + "=" + getValue();
		}
	}
}
