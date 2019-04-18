package org.qommons.collect;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.qommons.Transaction;

/**
 * A hash-based implementation of {@link BetterMap}
 * 
 * @param <K> The type of keys for the map
 * @param <V> The type of values for the map
 */
public class BetterHashMap<K, V> implements BetterMap<K, V> {
	/** Builds a {@link BetterHashMap} */
	public static class HashMapBuilder extends BetterHashSet.HashSetBuilder {
		@Override
		public HashMapBuilder unsafe() {
			return (HashMapBuilder) super.unsafe();
		}

		@Override
		public HashMapBuilder withLocking(CollectionLockingStrategy locker) {
			return (HashMapBuilder) super.withLocking(locker);
		}

		@Override
		public HashMapBuilder withEquivalence(ToIntFunction<Object> hasher, BiFunction<Object, Object, Boolean> equals) {
			return (HashMapBuilder) super.withEquivalence(//
				entry -> {
					if (entry instanceof Map.Entry)
						return hasher.applyAsInt(((Map.Entry<?, ?>) entry).getKey());
					else
						return hasher.applyAsInt(entry);
				}, (entry1, entry2) -> {
					if (entry1 instanceof Map.Entry && entry2 instanceof Map.Entry)
						return equals.apply(((Map.Entry<?, ?>) entry1).getKey(), ((Map.Entry<?, ?>) entry2).getKey());
					else
						return equals.apply(entry1, entry2);
				}
			);
		}

		@Override
		public HashMapBuilder identity() {
			return (HashMapBuilder) super.identity();
		}

		@Override
		public HashMapBuilder withLoadFactor(double loadFactor) {
			return (HashMapBuilder) super.withLoadFactor(loadFactor);
		}

		@Override
		public HashMapBuilder withInitialCapacity(int initExpectedSize) {
			return (HashMapBuilder) super.withInitialCapacity(initExpectedSize);
		}

		/**
		 * @param <K> The key type for the map
		 * @param <V> The value type for the map
		 * @return The new map
		 */
		public <K, V> BetterHashMap<K, V> buildMap() {
			return new BetterHashMap<>(buildSet());
		}

		/**
		 * @param <K> The key type for the map
		 * @param <V> The value type for the map
		 * @param values The initial key-value pairs to insert into the map
		 * @return A {@link BetterHashMap} built according to this builder's settings, with the given initial content
		 */
		public <K, V> BetterHashMap<K, V> buildMap(Map<? extends K, ? extends V> values) {
			BetterHashMap<K, V> map = new BetterHashMap<>(buildSet());
			map.putAll(values);
			return map;
		}
	}

	/** @return A builder to create a new {@link BetterHashMap} */
	public static HashMapBuilder build() {
		return new HashMapBuilder();
	}

	private final BetterHashSet<Map.Entry<K, V>> theEntries;
	private final KeySet theKeySet;

	private BetterHashMap(BetterHashSet<Map.Entry<K, V>> entries) {
		theEntries = entries;
		theKeySet = new KeySet();
	}

	/**
	 * @param capacity The minimum capacity for this map
	 * @return Whether the map was rebuilt
	 */
	public boolean ensureCapacity(int capacity) {
		return theEntries.ensureCapacity(capacity);
	}

	/**
	 * @return The efficiency of this hash map
	 * @see BetterHashSet#getEfficiency()
	 */
	public double getEfficiency() {
		return theEntries.getEfficiency();
	}

	@Override
	public BetterSet<K> keySet() {
		return theKeySet;
	}

	/**
	 * @param key The key for the entry
	 * @param value The initial value for the entry
	 * @return The map entry for the key to use in this map
	 */
	public Map.Entry<K, V> newEntry(K key, V value) {
		return new Entry(key, value);
	}

	@Override
	public MapEntryHandle<K, V> putEntry(K key, V value, boolean first) {
		try (Transaction t = theEntries.lock(true, null)) {
			Entry newEntry = (Entry) newEntry(key, value);
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getElement(newEntry, true);
			if (entryEl != null) {
				((Entry) entryEl.get()).mutable().setValue(value);
			} else {
				entryEl = theEntries.addElement(newEntry, first);
				((Entry) entryEl.get()).setId(entryEl.getElementId());
			}
			return handleFor(entryEl);
		}
	}

	@Override
	public MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
		try (Transaction t = theEntries.lock(true, null)) {
			Entry newEntry = (Entry) newEntry(key, value);
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getElement(newEntry, true);
			if (entryEl != null) {
				((Entry) entryEl.get()).mutable().setValue(value);
			} else {
				entryEl = theEntries.addElement(newEntry, after, before, first);
				((Entry) entryEl.get()).setId(entryEl.getElementId());
			}
			return handleFor(entryEl);
		}
	}

	@Override
	public MapEntryHandle<K, V> getEntry(K key) {
		CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getElement(theEntries.getHasher().applyAsInt(key),
			entry -> theEntries.getEquals().apply(entry.getKey(), key));
		return entryEl == null ? null : handleFor(entryEl);
	}

	@Override
	public MapEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, boolean first, Runnable added) {
		CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getOrAdd(//
			theEntries.getHasher().applyAsInt(key), entry -> theEntries.getEquals().apply(entry.getKey(), key), //
			() -> {
				V newValue = value.apply(key);
				return newEntry(key, newValue);
			}, null, null, first, added);
		if (entryEl == null)
			return null;
		return handleFor(entryEl);
	}

	@Override
	public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
		return handleFor(theEntries.getElement(entryId));
	}

	@Override
	public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
		return mutableHandleFor(theEntries.mutableElement(entryId));
	}

	public boolean isValid(ElementId elementId) {
		return theEntries.isValid(elementId);
	}

	/**
	 * @param entry The element in the entry set
	 * @return The map handle for the entry
	 */
	protected MapEntryHandle<K, V> handleFor(CollectionElement<? extends Map.Entry<K, V>> entry) {
		if (entry == null)
			return null;
		Entry e = (Entry) entry.get();
		e.setId(entry.getElementId());
		return e;
	}

	/**
	 * @param entry The mutable element in the entry set
	 * @return The mutable map handle for the entry
	 */
	protected MutableMapEntryHandle<K, V> mutableHandleFor(MutableCollectionElement<? extends Map.Entry<K, V>> entry) {
		return entry == null ? null : ((Entry) entry.get()).mutable();
	}

	@Override
	public String toString() {
		return entrySet().toString();
	}

	class Entry extends BetterMapEntryImpl<K, V> {
		Entry(K key, V value) {
			super(key, value);
		}

		void setId(ElementId id) {
			theId = id;
		}

		MutableMapEntryHandle<K, V> mutable() {
			return super.mutable(theEntries, BetterHashMap.this::values);
		}

		@Override
		protected CollectionElement<K> keyHandle() {
			return super.keyHandle();
		}

		MutableCollectionElement<K> mutableKeyHandle() {
			return mutableKeyHandle(theEntries, BetterHashMap.this::keySet);
		}
	}

	class KeySet implements BetterSet<K> {
		@Override
		public boolean belongs(Object o) {
			return true;
		}

		@Override
		public boolean isLockSupported() {
			return theEntries.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theEntries.lock(write, structural, cause);
		}

		@Override
		public Transaction tryLock(boolean write, boolean structural, Object cause) {
			return theEntries.tryLock(write, structural, cause);
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theEntries.getStamp(structuralOnly);
		}

		@Override
		public int size() {
			return theEntries.size();
		}

		@Override
		public boolean isEmpty() {
			return theEntries.isEmpty();
		}

		@Override
		public Object[] toArray() {
			return BetterSet.super.toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return BetterSet.super.toArray(a);
		}

		protected CollectionElement<K> handleFor(CollectionElement<? extends Map.Entry<K, V>> entryEl) {
			return entryEl == null ? null : ((Entry) entryEl.get()).keyHandle();
		}

		protected MutableCollectionElement<K> mutableHandleFor(MutableCollectionElement<? extends Map.Entry<K, V>> entryEl) {
			return entryEl == null ? null : ((Entry) entryEl.get()).mutableKeyHandle();
		}

		@Override
		public CollectionElement<K> getTerminalElement(boolean first) {
			return handleFor(theEntries.getTerminalElement(first));
		}

		@Override
		public CollectionElement<K> getAdjacentElement(ElementId elementId, boolean next) {
			return handleFor(theEntries.getAdjacentElement(elementId, next));
		}

		@Override
		public CollectionElement<K> getElement(K value, boolean first) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getElement(new SimpleMapEntry<>(value, null), first);
			return entryEl == null ? null : handleFor(entryEl);
		}

		@Override
		public CollectionElement<K> getElement(ElementId id) {
			return handleFor(theEntries.getElement(id));
		}

		@Override
		public CollectionElement<K> getOrAdd(K value, boolean first, Runnable added) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getOrAdd(newEntry(value, null), first, added);
			return entryEl == null ? null : handleFor(entryEl);
		}

		@Override
		public MutableCollectionElement<K> mutableElement(ElementId id) {
			return mutableHandleFor(theEntries.mutableElement(id));
		}

		@Override
		public String canAdd(K value, ElementId after, ElementId before) {
			return theEntries.canAdd(newEntry(value, null), after, before);
		}

		@Override
		public CollectionElement<K> addElement(K value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return handleFor(theEntries.addElement(newEntry(value, null), after, before, first));
		}

		@Override
		public void clear() {
			theEntries.clear();
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return theEntries.isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			return theEntries.checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<K, X> listener) {
			RepairListener<Map.Entry<K, V>, X> entryListener = listener == null ? null : new EntryRepairListener<>(listener);
			return theEntries.repair(element, entryListener);
		}

		@Override
		public <X> boolean repair(RepairListener<K, X> listener) {
			RepairListener<Map.Entry<K, V>, X> entryListener = listener == null ? null : new EntryRepairListener<>(listener);
			return theEntries.repair(entryListener);
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterSet.toString(this);
		}

		private class EntryRepairListener<X> implements RepairListener<Map.Entry<K, V>, X> {
			private final RepairListener<K, X> theKeyListener;

			EntryRepairListener(org.qommons.collect.ValueStoredCollection.RepairListener<K, X> keyListener) {
				theKeyListener = keyListener;
			}

			@Override
			public X removed(CollectionElement<Map.Entry<K, V>> element) {
				return theKeyListener.removed(handleFor(element));
			}

			@Override
			public void disposed(Map.Entry<K, V> value, X data) {
				theKeyListener.disposed(value.getKey(), data);
			}

			@Override
			public void transferred(CollectionElement<Map.Entry<K, V>> element, X data) {
				theKeyListener.transferred(handleFor(element), data);
			}
		}
	}
}
