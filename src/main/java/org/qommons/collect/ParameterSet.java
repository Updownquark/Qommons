package org.qommons.collect;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.qommons.ArrayUtils;

/** A sorted set of strings that is optimized for indexOf operations on small sets */
public final class ParameterSet extends AbstractSet<String> implements Comparable<ParameterSet> {
	public static final ParameterSet EMPTY = new ParameterSet(new String[0]);
	private static final ParameterMap<?> EMPTY_MAP = new EmptyParameterMap<>();
	private static final int MAX_CACHED_MAPS = 100;

	public static ParameterSet of(String... keys) {
		if (keys.length == 0)
			return EMPTY;
		else
			return new ParameterSet(keys);
	}

	public static ParameterSet of(Collection<String> keys) {
		if (keys.isEmpty())
			return EMPTY;
		else
			return new ParameterSet(keys);
	}

	// Package private here means no synthetic accessors
	final String[] theKeys;
	private final boolean small;
	private boolean hashed;
	private int hashCode;
	private ConcurrentLinkedQueue<ParameterMapImpl<?>> theMapCache;

	public ParameterSet(String... keys) {
		theKeys = keys;
		Arrays.sort(theKeys, (s1, s2) -> {
			int lenDiff = s1.length() - s2.length();
			if (lenDiff != 0) {
				return lenDiff;
			}
			return s1.compareTo(s2);
		});
		small = keys.length <= 10;
	}

	public ParameterSet(Collection<String> keys) {
		this(keys.toArray(new String[keys.size()]));
	}
	
	@Override
	public int size() {
		return theKeys.length;
	}

	public String get(int index) {
		return theKeys[index];
	}

	@Override
	public Iterator<String> iterator() {
		return Arrays.asList(theKeys).iterator();
	}

	@Override
	public String[] toArray() {
		return theKeys.clone();
	}

	public int indexOf(String key) {
		if (small) {
			for (int i = 0; i < theKeys.length; i++) {
				if (theKeys[i] == key) {
					return i;
				}
			}
			return ArrayUtils.binarySearch(0, theKeys.length - 1, index -> {
				return compare(index, key);
			});
		}
		return ArrayUtils.binarySearch(0, theKeys.length - 1, index -> {
			if (theKeys[index] == key) {
				return 0;
			}
			return compare(index, key);
		});
	}

	int compare(int keyIndex, String test) {
		String key = theKeys[keyIndex];
		int testLen = test.length();
		int lenDiff = testLen - key.length();
		if (lenDiff != 0) {
			return lenDiff;
		}
		for (int c = 0; c < testLen; c++) {
			char c1 = test.charAt(c);
			char c2 = key.charAt(c);
			if (c1 != c2) {
				return c1 - c2;
			}
		}
		return 0;
	}

	@Override
	public boolean contains(Object o) {
		return o instanceof String && contains((String) o);
	}

	public boolean contains(String key) {
		return indexOf(key) >= 0;
	}

	public <V> ParameterMap<V> createMap() {
		if (theKeys.length == 0)
			return (ParameterMap<V>) EMPTY_MAP;
		if (theMapCache != null) {
			ParameterMapImpl<?> map = theMapCache.poll();
			if (map != null) {
				map.init(null);
				return (ParameterMap<V>) map;
			}
		}
		return new ParameterMapImpl<>(this, null);
	}

	public <V> ParameterMap<V> createMap(IntFunction<V> valueProducer) {
		if (theMapCache != null) {
			ParameterMapImpl<?> map = theMapCache.poll();
			if (map != null) {
				((ParameterMapImpl<V>) map).init(valueProducer);
				return (ParameterMap<V>) map;
			}
		}
		return new ParameterMapImpl<>(this, valueProducer);
	}

	public <V> ParameterMap<V> createDynamicMap(IntFunction<V> valueProducer) {
		return new DynamicParameterMapImpl<>(this, valueProducer);
	}

	private static volatile int RELEASE_COUNT;
	private static volatile boolean IS_CACHING_MAPS;

	void released(ParameterMapImpl<?> map) {
		int maxCached = MAX_CACHED_MAPS;
		if (maxCached == 0) {
			return;
		} else if (theMapCache == null) {
			theMapCache = new ConcurrentLinkedQueue<>();
		}
		int count = RELEASE_COUNT + 1;
		boolean caching;
		if (count % (maxCached / 2) == 0) {
			count = 0;
			IS_CACHING_MAPS = caching = theMapCache.size() < maxCached;
		} else {
			caching=IS_CACHING_MAPS;
		}
		RELEASE_COUNT = count;
		if (caching) {
			theMapCache.add(map);
		}
	}

	@Override
	public int compareTo(ParameterSet o) {
		int comp = theKeys.length - o.theKeys.length;
		if (comp != 0) {
			return comp;
		}
		for (int k = 0; k < theKeys.length; k++) {
			if (theKeys[k] == o.theKeys[k]) {
				continue;
			}
			comp = compare(k, o.theKeys[k]);
			if (comp != 0) {
				return comp;
			}
		}
		return 0;
	}

	@Override
	public int hashCode() {
		if (!hashed) {
			int hash = 0;
			for (String key : theKeys) {
				hash += key.hashCode();
			}
			hashCode = hash;
			hashed = true;
		}
		return hashCode;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		} else if (!(obj instanceof ParameterSet)) {
			return false;
		}
		ParameterSet other = (ParameterSet) obj;
		// Hash code is likely to be more expensive than equals
		// Don't compute the hash code if it's not already, but if we have it, use it
		if (hashed && other.hashed && hashCode != other.hashCode) {
			return false;
		} else if (theKeys.length != other.theKeys.length) {
			return false;
		}
		boolean allIdentical = true;
		for (int i = 0; i < theKeys.length; i++) {
			if (theKeys[i] == other.theKeys[i]) {
				continue;
			}
			allIdentical = false;
			if (theKeys[i].length() != other.theKeys[i].length()) {
				return false;
			}
		}
		if (allIdentical) {
			return true;
		}
		// We already checked the length
		for (int i = 0; i < theKeys.length; i++) {
			for (int c = 0; c < theKeys[i].length(); c++) {
				if (theKeys[i].charAt(c) != other.theKeys[i].charAt(c)) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public String toString() {
		return Arrays.toString(theKeys);
	}

	// This interface does not extend Map<String, V> because its functionality differs a bit
	// It throws exceptions when a value is requested for a key that does not exist in the key set
	public interface ParameterMap<V> {
		ParameterSet keySet();

		default int keyIndex(String key) {
			int index = keySet().indexOf(key);
			if (index < 0) {
				throw new IllegalArgumentException("Key is not present: " + key);
			}
			return index;
		}

		/** @return The number of non-null values in this map */
		int valueCount();

		V get(int index);

		V put(int index, V value);

		V computeIfAbsent(int index, Function<String, V> valueProducer);

		default V get(String key) {
			return get(//
					keyIndex(key));
		}

		default V getIfPresent(String key) {
			int keyIndex = keyIndex(key);
			if (keyIndex < 0)
				return null;
			return get(keyIndex);
		}

		default V put(String key, V value) {
			return put(keyIndex(key), value);
		}

		default V computeIfAbsent(String key, Function<String, V> valueProducer) {
			return computeIfAbsent(keyIndex(key), valueProducer);
		}

		default ParameterMap<V> withAll(Map<String, ? extends V> values) {
			for (Map.Entry<String, ? extends V> entry : values.entrySet()) {
				int idx = keySet().indexOf(entry.getKey());
				if (idx >= 0)
					put(idx, entry.getValue());
			}
			return this;
		}

		void clear();

		Iterable<V> allValues();

		Iterable<V> values();

		default Stream<V> stream() {
			return StreamSupport.stream(Spliterators.spliterator(values().iterator(), valueCount(), 0), false);
		}

		default Map.Entry<String, V>[] toEntryArray() {
			Map.Entry<String, V>[] array = new Map.Entry[keySet().size()];
			for (int i = 0; i < array.length; i++) {
				array[i] = new SimpleMapEntry<>(keySet().get(i), get(i));
			}
			return array;
		}

		default ParameterMap<V> copy() {
			if (keySet().isEmpty())
				return this;
			ParameterMap<V> copy = keySet().createMap();
			for (int i = 0; i < keySet().size(); i++) {
				copy.put(i, get(i));
			}
			return copy;
		}

		ParameterMap<V> unmodifiable();

		default Map<String, V> asJavaMap() {
			return new ParamMapAsMap<>(this);
		}

		/** If supported, allows this map to be released and re-used later */
		void release();

		static <V> ParameterMap<V> of(Map<String, V> values) {
			return ParameterSet.of(values.keySet()).<V> createMap().withAll(values);
		}
	}

	private static final class EmptyParameterMap<T> implements ParameterMap<T> {
		@Override
		public ParameterSet keySet() {
			return EMPTY;
		}

		@Override
		public int valueCount() {
			return 0;
		}

		@Override
		public T get(int index) {
			throw new IndexOutOfBoundsException(index + " of 0");
		}

		@Override
		public T put(int index, T value) {
			throw new IndexOutOfBoundsException(index + " of 0");
		}

		@Override
		public T computeIfAbsent(int index, Function<String, T> valueProducer) {
			throw new IndexOutOfBoundsException(index + " of 0");
		}

		@Override
		public void clear() {}

		@Override
		public Iterable<T> allValues() {
			return Collections.emptyList();
		}

		@Override
		public Iterable<T> values() {
			return Collections.emptyList();
		}

		@Override
		public ParameterMap<T> unmodifiable() {
			return this;
		}

		@Override
		public void release() {}
	}

	public static final class CustomOrderedParameterSet extends AbstractSet<String> {
		final ParameterSet theSet;
		final int theSize;
		final int[] theCustomOrder;
		final int[] theReverseCustomOrder;

		public CustomOrderedParameterSet(ParameterSet set, Set<String> order) {
			theSet = set;
			theCustomOrder = new int[set.size()];
			theReverseCustomOrder = new int[set.size()];
			Arrays.fill(theReverseCustomOrder, -1);
			int i = 0;
			for (String s : order) {
				int keyIndex = set.indexOf(s);
				theCustomOrder[i] = keyIndex;
				theReverseCustomOrder[keyIndex] = i;
				i++;
			}
			theSize = i;
		}

		public ParameterSet getStringSet() {
			return theSet;
		}

		@Override
		public int size() {
			return theSize;
		}

		@Override
		public boolean isEmpty() {
			return theSize == 0;
		}

		@Override
		public boolean contains(Object o) {
			if (!(o instanceof String)) {
				return false;
			}
			int keyIndex = theSet.indexOf((String) o);
			if (keyIndex < 0) {
				return false;
			}
			return theReverseCustomOrder[keyIndex] >= 0;
		}

		@Override
		public Iterator<String> iterator() {
			return new COSSSSIterator();
		}

		@Override
		public Object[] toArray() {
			String[] array = new String[theSize];
			for (int i = 0; i < theSize; i++) {
				array[i] = theSet.get(theCustomOrder[i]);
			}
			return array;
		}

		@Override
		public <T> T[] toArray(T[] a) {
			if (!(a instanceof String[]) && a.getClass() != Object[].class) {
				throw new ClassCastException("Strings cannot be cast to " + a.getClass().getComponentType().getName());
			}
			if (a.length < theSize) {
				if (a instanceof String[]) {
					a = (T[]) new String[theSize];
				} else {
					a = (T[]) new Object[theSize];
				}
			}
			for (int i = 0; i < theSize; i++) {
				a[i] = (T) theSet.get(theCustomOrder[i]);
			}
			return a;
		}

		private class COSSSSIterator implements Iterator<String> {
			private int index;

			@Override
			public boolean hasNext() {
				return index < theSize;
			}

			@Override
			public String next() {
				if (index == theSize) {
					throw new NoSuchElementException();
				}
				String s = theSet.get(theCustomOrder[index]);
				index++;
				return s;
			}
		}
	}

	public static final class CustomOrderedParameterMap<V> extends AbstractMap<String, V> {
		final ParameterMap<V> theMap;
		final CustomOrderedParameterSet theKeySet;

		public CustomOrderedParameterMap(ParameterMap<V> map, Set<String> order) {
			theMap = map;
			if (order instanceof CustomOrderedParameterSet) {
				theKeySet = (CustomOrderedParameterSet) order;
			} else {
				theKeySet = new CustomOrderedParameterSet(theMap.keySet(), order);
			}
		}

		public ParameterMap<V> getMap() {
			return theMap;
		}

		public CustomOrderedParameterSet getKeySet() {
			return theKeySet;
		}

		@Override
		public int size() {
			return theKeySet.theSize;
		}

		@Override
		public boolean isEmpty() {
			return theKeySet.isEmpty();
		}

		@Override
		public V get(Object key) {
			if (!(key instanceof String)) {
				return null;
			}
			int index = theMap.keySet().indexOf((String) key);
			if (index < 0 || theKeySet.theReverseCustomOrder[index] < 0) {
				return null;
			}
			return theMap.get(index);
		}

		@Override
		public V put(String key, V value) {
			int index = theMap.keySet().indexOf(key);
			if (index < 0 || theKeySet.theReverseCustomOrder[index] < 0) {
				return null;
			}
			return theMap.put(index, value);
		}

		@Override
		public V remove(Object key) {
			if (!(key instanceof String)) {
				return null;
			}
			return put((String) key, null);
		}

		@Override
		public void clear() {
			for (int i = 0; i < theKeySet.theSize; i++) {
				theMap.put(theKeySet.theCustomOrder[i], null);
			}
		}

		@Override
		public Set<String> keySet() {
			return theKeySet;
		}

		@Override
		public Set<Map.Entry<String, V>> entrySet() {
			return new EntrySet();
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Map)) {
				return false;
			}
			Map<?, ?> other = (Map<?, ?>) o;
			if (theKeySet.theSize != other.size()) {
				return false;
			}
			if (other instanceof CustomOrderedParameterMap) {
				CustomOrderedParameterMap<?> otherSSMap = (CustomOrderedParameterMap<?>) other;
				if (theMap.keySet().equals(otherSSMap.theMap.keySet())
						&& Arrays.equals(theKeySet.theCustomOrder, otherSSMap.theKeySet.theCustomOrder)) {
					if (theMap == otherSSMap.theMap) {
						return true;
					}
					// Can do this by index instead of looking strings up
					for (int i = 0; i < theKeySet.theSize; i++) {
						int keyIndex = theKeySet.theCustomOrder[i];
						if (!Objects.equals(theMap.get(keyIndex), otherSSMap.theMap.get(keyIndex))) {
							return false;
						}
					}
					return true;
				}
			}
			for (int i = 0; i < theKeySet.theSize; i++) {
				int keyIndex = theKeySet.theCustomOrder[i];
				if (!Objects.equals(theMap.get(keyIndex), other.get(theMap.keySet().get(keyIndex)))) {
					return false;
				}
			}
			return true;
		}

		private class EntrySet extends AbstractSet<Map.Entry<String, V>> {
			@Override
			public int size() {
				return theKeySet.theSize;
			}

			@Override
			public boolean isEmpty() {
				return theKeySet.isEmpty();
			}

			@Override
			public boolean contains(Object o) {
				return CustomOrderedParameterMap.this.containsKey(o);
			}

			@Override
			public Iterator<Map.Entry<String, V>> iterator() {
				return new EntryIterator();
			}
		}

		private class EntryIterator implements Iterator<Map.Entry<String, V>> {
			private int index;

			@Override
			public boolean hasNext() {
				return index < theKeySet.theSize;
			}

			@Override
			public Map.Entry<String, V> next() {
				if (index == theKeySet.theSize) {
					throw new NoSuchElementException();
				}
				int keyIndex = theKeySet.theCustomOrder[index];
				index++;
				return new Entry(keyIndex);
			}
		}

		private class Entry implements Map.Entry<String, V> {
			private final int index;

			Entry(int index) {
				this.index = index;
			}

			@Override
			public String getKey() {
				return theMap.keySet().get(index);
			}

			@Override
			public V getValue() {
				return theMap.get(index);
			}

			@Override
			public V setValue(V value) {
				return theMap.put(index, value);
			}
		}
	}

	static final class ParamMapAsMap<V> extends AbstractMap<String, V> {
		private final ParameterMap<V> theMap;

		ParamMapAsMap(ParameterMap<V> map) {
			theMap = map;
		}

		@Override
		public int size() {
			return theMap.keySet().size();
		}

		@Override
		public boolean isEmpty() {
			return theMap.keySet().isEmpty();
		}

		@Override
		public boolean containsKey(Object key) {
			return theMap.keySet().contains(key);
		}

		@Override
		public boolean containsValue(Object value) {
			for (int i = 0; i < theMap.keySet().size(); i++) {
				if (Objects.equals(theMap.get(i), value)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public V get(Object key) {
			if (!(key instanceof String)) {
				return null;
			}
			int index = theMap.keyIndex((String) key);
			if (index < 0) {
				return null;
			}
			return theMap.get(index);
		}

		@Override
		public V put(String key, V value) {
			return theMap.put(key, value);
		}

		@Override
		public V remove(Object key) {
			if (!(key instanceof String)) {
				return null;
			}
			int index = theMap.keyIndex((String) key);
			if (index < 0) {
				return null;
			}
			return theMap.put(index, null);
		}

		@Override
		public void clear() {
			theMap.clear();
		}

		@Override
		public Set<String> keySet() {
			return theMap.keySet();
		}

		@Override
		public Set<Map.Entry<String, V>> entrySet() {
			return new EntrySet();
		}

		private class EntrySet extends AbstractSet<Map.Entry<String, V>> {
			@Override
			public Iterator<Map.Entry<String, V>> iterator() {
				return new SSSEntryIterator();
			}

			@Override
			public int size() {
				return theMap.keySet().size();
			}
		}

		private class SSSEntryIterator implements Iterator<Map.Entry<String, V>> {
			private int index;

			@Override
			public boolean hasNext() {
				return index < theMap.keySet().size();
			}

			@Override
			public Map.Entry<String, V> next() {
				SSSEntry entry = new SSSEntry(index);
				index++;
				return entry;
			}
		}

		private class SSSEntry implements Map.Entry<String, V> {
			private final int index;

			SSSEntry(int index) {
				this.index = index;
			}

			@Override
			public String getKey() {
				return theMap.keySet().get(index);
			}

			@Override
			public V getValue() {
				return theMap.get(index);
			}

			@Override
			public V setValue(V value) {
				return theMap.put(index, value);
			}
		}
	}

	private static final class ParamMapValueIterable<V> implements Iterable<V> {
		private final Object[] theValues;
		private final IntFunction<V> theValueFunction;
		private final boolean allValues;

		ParamMapValueIterable(Object[] values, IntFunction<V> valueFunction, boolean allValues) {
			theValues = values;
			theValueFunction = valueFunction;
			this.allValues = allValues;
		}

		@Override
		public Iterator<V> iterator() {
			return new ParamMapValueIterator<>(theValues, theValueFunction, allValues);
		}
	}

	private static final class ParamMapValueIterator<V> implements Iterator<V> {
		private final Object[] theValues;
		private final IntFunction<V> theValueFunction;
		private final boolean allValues;
		private int index;
		private boolean hasChecked;

		ParamMapValueIterator(Object[] values, IntFunction<V> valueFunction, boolean allValues) {
			theValues = values;
			theValueFunction = valueFunction;
			this.allValues = allValues;

			if (!allValues) {
				while (index < theValues.length && get() == null) {
					hasChecked = false;
					index++;
				}
			}
		}

		private V get() {
			if (theValueFunction != null && !hasChecked && theValues[index] == null) {
				hasChecked = true;
				theValues[index] = theValueFunction.apply(index);
			}
			return (V) theValues[index];
		}

		@Override
		public boolean hasNext() {
			return index < theValues.length;
		}

		@Override
		public V next() {
			V value = get();
			do {
				hasChecked = false;
				index++;
			} while (!allValues && index < theValues.length && get() == null);
			return value;
		}
	}

	static final class ParameterMapImpl<V> implements ParameterMap<V> {
		private final ParameterSet theKeys;
		private Object[] theValues;
		private IntFunction<V> theValueFunction;
		private UnmodifiableParameterMap<V> unmodifiable;

		private int hashCode;

		ParameterMapImpl(ParameterSet keys, IntFunction<V> valueProducer) {
			theKeys = keys;
			theValueFunction = valueProducer;

			hashCode = -1;
		}

		void init(IntFunction<V> valueProducer) {
			theValueFunction = valueProducer;
			if (theValues != null)
				Arrays.fill(theValues, null);
		}

		@Override
		public ParameterSet keySet() {
			return theKeys;
		}

		@Override
		public int valueCount() {
			if (theValues == null)
				return 0;
			int sz = 0;
			for (int i = 0; i < theValues.length; i++) {
				if (theValues[i] != null) {
					sz++;
				}
			}
			return sz;
		}

		@Override
		public V get(int index) {
			if (theValues == null) {
				if (theValueFunction == null) {
					theKeys.get(index); // Let the key set throw an out of bounds exception if needed
					return null;
				} else
					theValues = new Object[theKeys.size()];
			}
			V value = (V) theValues[index];
			if (value == null && theValueFunction != null) {
				theValues[index] = value = theValueFunction.apply(index);
				if (value != null) {
					hashCode = -1;
				}
			}
			return value;
		}

		@Override
		public V put(int index, V value) {
			if (theValues == null)
				theValues = new Object[theKeys.size()];
			V old = (V) theValues[index];
			theValues[index] = value;
			hashCode = -1;
			return old;
		}

		@Override
		public V computeIfAbsent(int index, Function<String, V> valueProducer) {
			if (theValues == null)
				theValues = new Object[theKeys.size()];
			V value = (V) theValues[index];
			if (value == null) {
				theValues[index] = value = valueProducer.apply(theKeys.get(index));
				if (value != null) {
					hashCode = -1;
				}
			}
			return value;
		}

		@Override
		public void clear() {
			hashCode = -1;
			if (theValues != null)
				Arrays.fill(theValues, null);
		}

		@Override
		public Iterable<V> allValues() {
			if (theValues == null)
				theValues = new Object[theKeys.size()];
			return new ParamMapValueIterable<>(theValues, theValueFunction, true);
		}

		@Override
		public Iterable<V> values() {
			if (theValues == null)
				return Collections.emptyList();
			return new ParamMapValueIterable<>(theValues, theValueFunction, false);
		}

		@Override
		public ParameterMap<V> unmodifiable() {
			if (unmodifiable == null) {
				unmodifiable = new UnmodifiableParameterMap<>(this);
			}
			return unmodifiable;
		}

		@Override
		public void release() {
			theKeys.released(this);
		}

		@Override
		public int hashCode() {
			if (hashCode == -1) {
				int hc = theKeys.hashCode();
				if (theValues != null) {
					for (int i = 0; i < theValues.length; i++) {
						hc = hc * 31 + Objects.hashCode(get(i));
					}
				}
				hashCode = hc;
			}
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if(this==obj) {
				return true;
			} else if(!(obj instanceof ParameterMap)) {
				return false;
			}
			ParameterMap<?> other=(ParameterMap<?>) obj;
			if (!theKeys.equals(other.keySet())) {
				return false;
			}
			if (theValues == null)
				theValues = new Object[theKeys.size()];
			for (int i = 0; i < theValues.length; i++) {
				if (!Objects.equals(get(i), other.get(i))) {
					return false;
				}
			}
			return true;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append('{');
			boolean first = true;
			if (theValues == null)
				theValues = new Object[theKeys.size()];
			for (int i = 0; i < theValues.length; i++) {
				if (!first) {
					str.append(", ");
				}
				first = false;
				str.append(theKeys.get(i)).append('=');
				if (theValues[i] == null && theValueFunction != null) {
					str.append('?');
				} else {
					str.append(theValues[i]);
				}
			}
			str.append('}');
			return str.toString();
		}
	}

	static final class DynamicParameterMapImpl<V> implements ParameterMap<V> {
		private final ParameterSet theKeys;
		private final IntFunction<? extends V> theValues;
		private UnmodifiableParameterMap<V> unmodifiable;

		DynamicParameterMapImpl(ParameterSet keySet, IntFunction<? extends V> values) {
			theKeys = keySet;
			theValues = values;
		}

		@Override
		public ParameterSet keySet() {
			return theKeys;
		}

		@Override
		public int valueCount() {
			int vc = 0;
			for (int i = 0; i < theKeys.size(); i++)
				if (theValues.apply(i) != null)
					vc++;
			return vc;
		}

		@Override
		public V get(int index) {
			if (index < 0 || index >= theKeys.size())
				throw new IndexOutOfBoundsException(index + " of " + theKeys.size());
			return theValues.apply(index);
		}

		@Override
		public V put(int index, V value) {
			throw new UnsupportedOperationException("This view is not modifiable");
		}

		@Override
		public V computeIfAbsent(int index, Function<String, V> valueProducer) {
			return get(index);
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException("This view is not modifiable");
		}

		@Override
		public Iterable<V> allValues() {
			return new Iterable<V>() {
				@Override
				public Iterator<V> iterator() {
					return new Iterator<V>() {
						private int theIndex;

						@Override
						public boolean hasNext() {
							return theIndex < theKeys.size();
						}

						@Override
						public V next() {
							if (theIndex == theKeys.size())
								throw new NoSuchElementException();
							return theValues.apply(theIndex++);
						}
					};
				}
			};
		}

		@Override
		public Iterable<V> values() {
			return new Iterable<V>() {
				@Override
				public Iterator<V> iterator() {
					return new Iterator<V>() {
						private int theIndex;
						private boolean hasNext;
						private V theNext;

						@Override
						public boolean hasNext() {
							while (!hasNext && theIndex < theKeys.size()) {
								theNext = theValues.apply(theIndex++);
								hasNext = theNext != null;
							}
							return hasNext;
						}

						@Override
						public V next() {
							if (!hasNext())
								throw new NoSuchElementException();
							hasNext = false;
							V value = theNext;
							theNext = null;
							return value;
						}
					};
				}
			};
		}

		@Override
		public ParameterMap<V> unmodifiable() {
			if (unmodifiable == null) {
				unmodifiable = new UnmodifiableParameterMap<>(this);
			}
			return unmodifiable;
		}

		@Override
		public void release() {}

		@Override
		public int hashCode() {
			int hc = theKeys.hashCode();
			if (theValues != null) {
				for (int i = 0; i < theKeys.size(); i++) {
					hc = hc * 31 + Objects.hashCode(get(i));
				}
			}
			return hc;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			} else if (!(obj instanceof ParameterMap)) {
				return false;
			}
			ParameterMap<?> other = (ParameterMap<?>) obj;
			if (!theKeys.equals(other.keySet())) {
				return false;
			}
			for (int i = 0; i < theKeys.size(); i++) {
				if (!Objects.equals(get(i), other.get(i))) {
					return false;
				}
			}
			return true;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append('{');
			boolean first = true;
			for (int i = 0; i < theKeys.size(); i++) {
				if (!first) {
					str.append(", ");
				}
				first = false;
				str.append(theKeys.get(i)).append('=');
				str.append(get(i));
			}
			str.append('}');
			return str.toString();
		}
	}

	static final class UnmodifiableParameterMap<V> implements ParameterMap<V> {
		private final ParameterMap<V> theWrapped;

		UnmodifiableParameterMap(ParameterMap<V> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public ParameterSet keySet() {
			return theWrapped.keySet();
		}

		@Override
		public int valueCount() {
			return theWrapped.valueCount();
		}

		@Override
		public V get(int index) {
			return theWrapped.get(index);
		}

		@Override
		public V put(int index, V value) {
			throw new UnsupportedOperationException("This map supplies its own values");
		}

		@Override
		public V computeIfAbsent(int index, Function<String, V> valueProducer) {
			throw new UnsupportedOperationException("This map supplies its own values");
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException("This map supplies its own values");
		}

		@Override
		public Iterable<V> allValues() {
			return theWrapped.allValues();
		}

		@Override
		public Iterable<V> values() {
			return theWrapped.values();
		}

		// Non-modifying default methods overridden for performance

		@Override
		public int keyIndex(String key) {
			return theWrapped.keyIndex(key);
		}

		@Override
		public V get(String key) {
			return theWrapped.get(key);
		}

		@Override
		public ParameterMap<V> unmodifiable() {
			return this;
		}

		@Override
		public void release() {
			return; // Can't release the wrapped map through an unmodifiable view
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}
}
