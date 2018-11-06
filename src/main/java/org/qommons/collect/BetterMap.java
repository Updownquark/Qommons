package org.qommons.collect;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.ValueStoredCollection.RepairListener;

/**
 * A {@link Map} that provides access to its entries by ID.
 * 
 * See <a href="https://github.com/Updownquark/Qommons/wiki/BetterMap-API">the wiki</a> for more detail.
 * 
 * @param <K> The type of keys stored in the map
 * @param <V> The type of values stored in the map
 */
public interface BetterMap<K, V> extends TransactableMap<K, V> {
	@Override
	BetterSet<K> keySet();

	@Override
	default BetterSet<Map.Entry<K, V>> entrySet() {
		return new BetterEntrySet<>(this);
	}

	@Override
	default BetterCollection<V> values() {
		return new BetterMapValueCollection<>(this);
	}

	@Override
	default boolean isLockSupported() {
		return keySet().isLockSupported();
	}

	@Override
	default Transaction lock(boolean write, boolean structural, Object cause) {
		return keySet().lock(write, structural, cause);
	}

	@Override
	default Transaction tryLock(boolean write, boolean structural, Object cause) {
		return keySet().tryLock(write, structural, cause);
	}

	/**
	 * @param structuralOnly Whether to obtain the structural modification stamp, or to include updates
	 * @return The stamp of the given type
	 */
	default long getStamp(boolean structuralOnly) {
		return keySet().getStamp(structuralOnly);
	}

	/**
	 * @param key The key for the entry
	 * @param value The value for the entry
	 * @param first Whether to prefer inserting the key toward the beginning or end of the key set (if supported)
	 * @return The handle for the new or updated entry
	 */
	default MapEntryHandle<K, V> putEntry(K key, V value, boolean first) {
		return putEntry(key, value, null, null, first);
	}

	/**
	 * @param key The key for the entry
	 * @param value The value for the entry
	 * @param after The ID of the key element to be the lower bound for the new entry's insertion in the key set
	 * @param before The ID of the key element to be the upper bound for the new entry's insertion in the key set
	 * @param first Whether to prefer inserting the key toward the beginning or end of the key set (if supported)
	 * @return The handle for the new or updated entry
	 */
	MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first);

	/**
	 * @param key The key to get the handle for
	 * @return The handle for the entry in this map with the given key, or null if the key does not exist in this map's key set
	 */
	MapEntryHandle<K, V> getEntry(K key);

	/**
	 * Same as {@link #computeIfAbsent(Object, Function)}, but returns the entry of the affected element
	 * 
	 * @param key The key to retrieve or insert the value for
	 * @param value The function to produce the value for the added entry, if not present
	 * @param first Whether to prefer adding the new entry early or late in the key/entry set
	 * @param added The runnable that will be invoked if the entry is added
	 * @return The entry of the element if retrieved or added; may be null if key/value pair is not permitted in the map
	 */
	MapEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, boolean first, Runnable added);

	/**
	 * @param entryId The element ID to get the handle for
	 * @return The handle for the entry in this map with the given ID
	 */
	MapEntryHandle<K, V> getEntryById(ElementId entryId);

	/**
	 * @param entryId The element ID to get the handle for
	 * @return The mutable handle for the entry in this map with the given ID
	 */
	MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId);

	/**
	 * @param entryId The ID of the entry to operate on
	 * @param onEntry The action to perform on the mutable entry with the given ID
	 */
	default void forMutableEntry(ElementId entryId, Consumer<? super MutableMapEntryHandle<K, V>> onEntry) {
		onEntry.accept(mutableEntry(entryId));
	}

	/** @return A {@link BetterMap} with this map's entries, reversed */
	default BetterMap<K, V> reverse() {
		return new ReversedMap<>(this);
	}

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
		MapEntryHandle<K, V> entry = getEntry((K) key);
		return entry == null ? null : entry.get();
	}

	@Override
	default V remove(Object key) {
		if (!keySet().belongs(key))
			return null;
		try (Transaction t = lock(true, null)) {
			MapEntryHandle<K, V> entry = getEntry((K) key);
			if (entry == null)
				return null;
			MutableMapEntryHandle<K, V> mutableEntry = mutableEntry(entry.getElementId());
			V old = mutableEntry.get();
			mutableEntry.remove();
			return old;
		}
	}

	@Override
	default V put(K key, V value) {
		while (true) {
			boolean[] added = new boolean[1];
			MapEntryHandle<K, V> entry = getOrPutEntry(key, k -> value, false, () -> added[0] = true);
			if (entry != null && !added[0]) {
				// Get the mutable entry in case the immutable one doesn't support Entry.setValue(Object)
				MutableMapEntryHandle<K, V> mutableEntry;
				try {
					mutableEntry = mutableEntry(entry.getElementId());
				} catch (IllegalArgumentException e) {
					continue;
				}
				V old = mutableEntry.get();
				mutableEntry.set(value);
				return old;
			} else
				return null;
			}
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

	/**
	 * @param key The key for the entry to add
	 * @param value The value to add for the key
	 * @return This map
	 */
	default BetterMap<K, V> with(K key, V value) {
		put(key, value);
		return this;
	}

	/**
	 * @param values The map whose entries to add to this map
	 * @return This map
	 */
	default BetterMap<K, V> withAll(Map<? extends K, ? extends V> values) {
		putAll(values);
		return this;
	}

	/**
	 * A search method that works to detect inconsistencies in the map's key storage structure during the search. See
	 * {@link BetterSet#searchWithConsistencyDetection(Object, boolean[])} for more information.
	 * 
	 * @param key The key to search for
	 * @param invalid The invalid flag to set to true if inconsistency is detected
	 * @return The entry element, or null if inconsistency is detected or no such element is found in the map
	 */
	default MapEntryHandle<K, V> searchWithConsistencyDetection(K key, boolean[] invalid) {
		CollectionElement<K> keyEl = keySet().searchWithConsistencyDetection(key, invalid);
		return keyEl == null ? null : getEntryById(keyEl.getElementId());
	}

	/**
	 * Searches for any inconsistencies in the map's key storage structure. This typically takes linear time. See
	 * {@link BetterSet#checkConsistency()} for more information.
	 * 
	 * @return Whether any inconsistency was found in the map
	 */
	default boolean checkConsistency() {
		return keySet().checkConsistency();
	}

	/**
	 * An interface to monitor #repair on a set.
	 * 
	 * @param <K> The type of keys in the map
	 * @param <V> The type of values in the set
	 * @param <X> The type of the custom data to keep track of transfer operations
	 */
	interface MapRepairListener<K, V, X> {
		/** @param element The element removed due to a collision */
		void removed(MapEntryHandle<K, V> element);

		/**
		 * @param element The element, having just been removed, which will immediately be transferred to another position in the set, with
		 *        a corresponding call to {@link #postTransfer(MapEntryHandle, Object)}
		 * @return A piece of data which will be given as the second argument to {@link #postTransfer(MapEntryHandle, Object)} as a means of
		 *         tracking
		 */
		X preTransfer(MapEntryHandle<K, V> element);

		/**
		 * @param element The element previously removed (with a corresponding call to {@link #preTransfer(MapEntryHandle)}) and now
		 *        re-added in a different position within the set
		 * @param data The data returned from the {@link #preTransfer(MapEntryHandle)} call
		 */
		void postTransfer(MapEntryHandle<K, V> element, X data);
	}

	/**
	 * Searches for and fixes any inconsistencies in the set's storage structure. See {@link BetterSet#repair(RepairListener)} for more
	 * information.
	 * 
	 * @param <X> The type of the data transferred for the listener
	 * @param listener The listener to monitor repairs. May be null.
	 * @return Whether any inconsistencies were found
	 */
	default <X> boolean repair(MapRepairListener<K, V, X> listener) {
		RepairListener<K, X> keyListener = listener == null ? null : new RepairListener<K, X>() {
			@Override
			public void removed(CollectionElement<K> element) {
				listener.removed(getEntryById(element.getElementId()));
			}

			@Override
			public X preTransfer(CollectionElement<K> element) {
				return listener.preTransfer(getEntryById(element.getElementId()));
			}

			@Override
			public void postTransfer(CollectionElement<K> element, X data) {
				listener.postTransfer(getEntryById(element.getElementId()), data);
			}
		};
		return keySet().repair(keyListener);
	}

	@Override
	default V getOrDefault(Object key, V defaultValue) {
		if (!keySet().belongs(key))
			return defaultValue;
		MapEntryHandle<K, V> handle = getEntry((K) key);
		return handle == null ? defaultValue : handle.get();
	}

	@Override
	default boolean remove(Object key, Object value) {
		if (!keySet().belongs(key))
			return false;
		MapEntryHandle<K, V> handle = getEntry((K) key);
		if (handle != null && Objects.equals(handle.get(), value)) {
			mutableEntry(handle.getElementId()).remove();
			return true;
		} else
			return false;
	}

	@Override
	default boolean replace(K key, V oldValue, V newValue) {
		MapEntryHandle<K, V> handle = getEntry(key);
		if (handle != null && Objects.equals(handle.get(), oldValue)) {
			mutableEntry(handle.getElementId()).set(newValue);
			return true;
		} else
			return false;
	}

	@Override
	default V replace(K key, V value) {
		MapEntryHandle<K, V> handle = getEntry(key);
		if (handle != null) {
			V oldValue = handle.getValue();
			mutableEntry(handle.getElementId()).set(value);
			return oldValue;
		} else
			return null;
	}

	@Override
	default V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
		MapEntryHandle<K, V> entry = getOrPutEntry(key, mappingFunction, false, null);
		return entry == null ? null : entry.getValue();
	}

	@Override
	default V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		while (true) {
		MapEntryHandle<K, V> handle = getEntry(key);
			if (handle != null) {
				MutableMapEntryHandle<K, V> mutableEntry;
				try {
					mutableEntry = mutableEntry(handle.getElementId());
				} catch (IllegalArgumentException e) {
					continue;
				}
				V oldValue = mutableEntry.get();
				V newValue = remappingFunction.apply(key, oldValue);
				if (newValue != null) {
					do {
						if (mutableEntry.compareAndSet(oldValue, newValue))
							return newValue;
					} while (mutableEntry.getElementId().isPresent());
				} else {
					try {
						mutableEntry.remove();
						return null;
					} catch (IllegalStateException e) {
						continue;
					}
				}
		} else
			return null;
	}
	}

	@Override
	default V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		while (true) {
			ValueHolder<V> value = new ValueHolder<>();
			MapEntryHandle<K, V> entry = getOrPutEntry(key, k -> {
				V newValue = remappingFunction.apply(k, null);
				value.accept(newValue);
				return newValue;
			}, false, null);
			if (value.isPresent())
				return value.get();// Added
			MutableMapEntryHandle<K, V> mutableEntry;
			try {
				mutableEntry = mutableEntry(entry.getElementId());
			} catch (IllegalArgumentException e) {
				continue;
			}
			V oldValue = mutableEntry.get();
			V newValue = remappingFunction.apply(key, oldValue);
			if (newValue != null) {
				do {
					if (mutableEntry.compareAndSet(oldValue, newValue))
						return newValue;
				} while (mutableEntry.getElementId().isPresent());
		} else {
				try {
					mutableEntry.remove();
					return null;
				} catch (IllegalStateException e) {
					continue;
		}
	}
		}
	}

	@Override
	default V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		return compute(key, (k, v) -> v == null ? value : remappingFunction.apply(v, value));
			}

	/**
	 * Implements {@link BetterMap#reverse()}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ReversedMap<K, V> implements BetterMap<K, V> {
		private final BetterMap<K, V> theWrapped;

		public ReversedMap(BetterMap<K, V> wrapped) {
			theWrapped = wrapped;
		}

		protected BetterMap<K, V> getWrapped() {
			return theWrapped;
		}

		@Override
		public BetterSet<K> keySet() {
			return theWrapped.keySet().reverse();
		}

		@Override
		public MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
			return MapEntryHandle.reverse(theWrapped.putEntry(key, value, ElementId.reverse(before), ElementId.reverse(after), !first));
		}

		@Override
		public MapEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, boolean first, Runnable added) {
			return MapEntryHandle.reverse(theWrapped.getOrPutEntry(key, value, !first, added));
		}

		@Override
		public MapEntryHandle<K, V> getEntry(K key) {
			return MapEntryHandle.reverse(theWrapped.getEntry(key));
		}

		@Override
		public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
			return theWrapped.getEntryById(entryId.reverse()).reverse();
		}

		@Override
		public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
			return theWrapped.mutableEntry(entryId.reverse()).reverse();
		}

		@Override
		public MapEntryHandle<K, V> searchWithConsistencyDetection(K key, boolean[] invalid) {
			return MapEntryHandle.reverse(getWrapped().searchWithConsistencyDetection(key, invalid));
		}

		@Override
		public boolean checkConsistency() {
			return getWrapped().checkConsistency();
		}

		@Override
		public <X> boolean repair(MapRepairListener<K, V, X> listener) {
			MapRepairListener<K, V, X> reversedListener = listener == null ? null : new MapRepairListener<K, V, X>() {
				@Override
				public void removed(MapEntryHandle<K, V> element) {
					listener.removed(element.reverse());
				}

				@Override
				public X preTransfer(MapEntryHandle<K, V> element) {
					return listener.preTransfer(element.reverse());
				}

				@Override
				public void postTransfer(MapEntryHandle<K, V> element, X data) {
					listener.postTransfer(element.reverse(), data);
				}
			};
			return getWrapped().repair(reversedListener);
		}
	}

	/**
	 * A default entry set for a {@link BetterMap}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class BetterEntrySet<K, V> implements BetterSet<Map.Entry<K, V>> {
		private final BetterMap<K, V> theMap;

		public BetterEntrySet(BetterMap<K, V> map) {
			theMap = map;
		}

		protected BetterMap<K, V> getMap() {
			return theMap;
		}

		@Override
		public boolean belongs(Object o) {
			return o instanceof Map.Entry && theMap.keySet().belongs(((Map.Entry<?, ?>) o).getKey());
		}

		@Override
		public boolean isLockSupported() {
			return theMap.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theMap.lock(write, structural, cause);
		}

		@Override
		public Transaction tryLock(boolean write, boolean structural, Object cause) {
			return theMap.tryLock(write, structural, cause);
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theMap.getStamp(structuralOnly);
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
		public Object[] toArray() {
			return BetterSet.super.toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return BetterSet.super.toArray(a);
		}

		@Override
		public CollectionElement<Entry<K, V>> getTerminalElement(boolean first) {
			CollectionElement<K> keyEl = theMap.keySet().getTerminalElement(first);
			return keyEl == null ? null : getElement(keyEl.getElementId());
		}

		@Override
		public CollectionElement<Entry<K, V>> getAdjacentElement(ElementId elementId, boolean next) {
			CollectionElement<K> keyEl = theMap.keySet().getAdjacentElement(elementId, next);
			return keyEl == null ? null : getElement(keyEl.getElementId());
		}

		@Override
		public CollectionElement<Entry<K, V>> getElement(Entry<K, V> value, boolean first) {
			if (value == null)
				return null;
			MapEntryHandle<K, V> entry = theMap.getEntry(value.getKey());
			return entry == null ? null : getElement(entry.getElementId());
		}

		@Override
		public CollectionElement<Entry<K, V>> getElement(ElementId id) {
			return new EntryElement(theMap.getEntryById(id));
		}

		@Override
		public CollectionElement<Entry<K, V>> getOrAdd(Entry<K, V> value, boolean first, Runnable added) {
			MapEntryHandle<K, V> entry = theMap.getOrPutEntry(value.getKey(), k -> value.getValue(), first, added);
			return entry == null ? null : getElement(entry.getElementId());
		}

		@Override
		public MutableCollectionElement<Entry<K, V>> mutableElement(ElementId id) {
			return new MutableEntryElement(theMap.mutableEntry(id));
		}

		@Override
		public MutableElementSpliterator<Entry<K, V>> spliterator(ElementId element, boolean asNext) {
			return new EntrySpliterator(theMap.keySet().spliterator(element, asNext));
		}

		@Override
		public MutableElementSpliterator<Map.Entry<K, V>> spliterator(boolean fromStart) {
			return wrap(theMap.keySet().spliterator(fromStart));
		}

		@Override
		public String canAdd(Entry<K, V> value, ElementId after, ElementId before) {
			return theMap.keySet().canAdd(value.getKey(), after, before);
		}

		@Override
		public CollectionElement<Entry<K, V>> addElement(Entry<K, V> value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			CollectionElement<K> keyEl = theMap.keySet().addElement(value.getKey(), after, before, first);
			if (keyEl == null)
				return null;
			MutableMapEntryHandle<K, V> mapEntry = theMap.mutableEntry(keyEl.getElementId());
			mapEntry.setValue(value.getValue());
			return new EntryElement(mapEntry);
		}

		@Override
		public void clear() {
			theMap.clear();
		}

		@Override
		public CollectionElement<Entry<K, V>> searchWithConsistencyDetection(Entry<K, V> value, boolean[] invalid) {
			MapEntryHandle<K, V> entryEl = theMap.searchWithConsistencyDetection(value.getKey(), invalid);
			return entryEl == null ? null : new EntryElement(entryEl);
		}

		@Override
		public boolean checkConsistency() {
			return theMap.checkConsistency();
		}

		@Override
		public <X> boolean repair(RepairListener<Entry<K, V>, X> listener) {
			MapRepairListener<K, V, X> mapListener = listener == null ? null : new MapRepairListener<K, V, X>() {
				@Override
				public void removed(MapEntryHandle<K, V> element) {
					listener.removed(new EntryElement(element));
				}

				@Override
				public X preTransfer(MapEntryHandle<K, V> element) {
					return listener.preTransfer(new EntryElement(element));
				}

				@Override
				public void postTransfer(MapEntryHandle<K, V> element, X data) {
					listener.postTransfer(new EntryElement(element), data);
				}
			};
			return theMap.repair(mapListener);
		}

		protected MutableElementSpliterator<Map.Entry<K, V>> wrap(MutableElementSpliterator<K> keySpliter) {
			return new EntrySpliterator(keySpliter);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}

		class EntryElement implements CollectionElement<Map.Entry<K, V>> {
			private final MapEntryHandle<K, V> theEntry;

			EntryElement(MapEntryHandle<K, V> entry) {
				theEntry = entry;
			}

			protected MapEntryHandle<K, V> getEntry() {
				return theEntry;
			}

			@Override
			public ElementId getElementId() {
				return theEntry.getElementId();
			}

			@Override
			public Map.Entry<K, V> get() {
				return new Map.Entry<K, V>() {
					@Override
					public K getKey() {
						return theEntry.getKey();
					}

					@Override
					public V getValue() {
						return theEntry.get();
					}

					@Override
					public V setValue(V value) {
						// Since the Map interface expects entries in the entrySet to support setValue, we'll allow this type of mutability
						try (Transaction t = lock(true, false, null)) {
							V current = theEntry.get();
							theMap.mutableEntry(getEntry().getElementId()).setValue(value);
							return current;
						}
					}

					@Override
					public int hashCode() {
						return EntryElement.this.hashCode();
					}

					@Override
					public boolean equals(Object obj) {
						return EntryElement.this.equals(obj);
					}

					@Override
					public String toString() {
						return theEntry.toString();
					}
				};
			}

			@Override
			public int hashCode() {
				return theEntry.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (obj instanceof BetterEntrySet.EntryElement)
					return theEntry.equals(((EntryElement) obj).theEntry);
				else if (obj instanceof Map.Entry)
					return Objects.equals(get().getKey(), ((Map.Entry<?, ?>) obj).getKey());
				else
					return false;
			}

			@Override
			public String toString() {
				return theEntry.toString();
			}
		}

		class MutableEntryElement extends EntryElement implements MutableCollectionElement<Map.Entry<K, V>> {
			MutableEntryElement(MutableMapEntryHandle<K, V> entry) {
				super(entry);
			}

			@Override
			protected MutableMapEntryHandle<K, V> getEntry() {
				return (MutableMapEntryHandle<K, V>) super.getEntry();
			}

			@Override
			public BetterCollection<Entry<K, V>> getCollection() {
				return BetterEntrySet.this;
			}

			@Override
			public String isEnabled() {
				return getEntry().isEnabled();
			}

			@Override
			public String isAcceptable(Map.Entry<K, V> value) {
				if (value == null)
					return StdMsg.ILLEGAL_ELEMENT;
				String msg = theMap.keySet().mutableElement(getEntry().getElementId()).isAcceptable(value.getKey());
				if (msg != null)
					return msg;
				return getEntry().isAcceptable(value.getValue());
			}

			@Override
			public void set(Map.Entry<K, V> value) throws UnsupportedOperationException, IllegalArgumentException {
				if (value == null)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				theMap.keySet().mutableElement(getEntry().getElementId()).set(value.getKey());
				getEntry().set(value.getValue());
			}

			@Override
			public String canRemove() {
				return getEntry().canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				getEntry().remove();
			}
		}

		class EntrySpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<Map.Entry<K, V>> {
			private final MutableElementSpliterator<K> theKeySpliter;

			EntrySpliterator(MutableElementSpliterator<K> keySpliter) {
				super(BetterEntrySet.this);
				theKeySpliter = keySpliter;
			}

			@Override
			public long estimateSize() {
				return theKeySpliter.estimateSize();
			}

			@Override
			public long getExactSizeIfKnown() {
				return theKeySpliter.getExactSizeIfKnown();
			}

			@Override
			public Comparator<? super Entry<K, V>> getComparator() {
				Comparator<? super K> keyCompare = theKeySpliter.getComparator();
				if (keyCompare == null)
					return null;
				return (entry1, entry2) -> keyCompare.compare(entry1.getKey(), entry2.getKey());
			}

			@Override
			public int characteristics() {
				return theKeySpliter.characteristics();
			}

			@Override
			protected boolean internalForElementM(Consumer<? super MutableCollectionElement<Entry<K, V>>> action, boolean forward) {
				return theKeySpliter.forElementM(
					keyEl -> theMap.forMutableEntry(keyEl.getElementId(), el -> action.accept(new MutableEntryElement(el))), forward);
			}

			@Override
			protected boolean internalForElement(Consumer<? super CollectionElement<Entry<K, V>>> action, boolean forward) {
				return theKeySpliter.forElement(keyEl -> action.accept(new EntryElement(theMap.getEntryById(keyEl.getElementId()))),
					forward);
			}

			@Override
			public MutableElementSpliterator<Map.Entry<K, V>> trySplit() {
				MutableElementSpliterator<K> keySplit = theKeySpliter.trySplit();
				return keySplit == null ? null : new EntrySpliterator(keySplit);
			}
		}
	}

	/**
	 * A default value collection implementation for a {@link BetterMap}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class BetterMapValueCollection<K, V> implements BetterCollection<V> {
		private final BetterMap<K, V> theMap;

		public BetterMapValueCollection(BetterMap<K, V> map) {
			theMap = map;
		}

		protected BetterMap<K, V> getMap() {
			return theMap;
		}

		@Override
		public boolean isLockSupported() {
			return theMap.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theMap.lock(write, structural, cause);
		}

		@Override
		public Transaction tryLock(boolean write, boolean structural, Object cause) {
			return theMap.tryLock(write, structural, cause);
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theMap.getStamp(structuralOnly);
		}

		@Override
		public boolean belongs(Object o) {
			return true; // I guess?
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
		public CollectionElement<V> getTerminalElement(boolean first) {
			CollectionElement<K> keyEl = theMap.keySet().getTerminalElement(first);
			return keyEl == null ? null : theMap.getEntryById(keyEl.getElementId());
		}

		@Override
		public CollectionElement<V> getAdjacentElement(ElementId elementId, boolean next) {
			CollectionElement<K> keyEl = theMap.keySet().getAdjacentElement(elementId, next);
			return keyEl == null ? null : theMap.getEntryById(keyEl.getElementId());
		}

		@Override
		public String canAdd(V value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<V> addElement(V value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			theMap.clear();
		}

		@Override
		public CollectionElement<V> getElement(V value, boolean first) {
			try (Transaction t = lock(false, null)) {
				CollectionElement<V>[] element = new CollectionElement[1];
				while (element[0] == null && spliterator(first).forElement(el -> {
					if (Objects.equals(el.get(), value))
						element[0] = el;
				}, first)) {}
				return element[0];
			}
		}

		@Override
		public CollectionElement<V> getElement(ElementId id) {
			return theMap.getEntryById(id);
		}

		@Override
		public MutableCollectionElement<V> mutableElement(ElementId id) {
			return theMap.mutableEntry(id);
		}

		@Override
		public MutableElementSpliterator<V> spliterator(ElementId element, boolean asNext) {
			return new ValueSpliterator(theMap.keySet().spliterator(element, asNext));
		}

		@Override
		public MutableElementSpliterator<V> spliterator(boolean fromStart) {
			return new ValueSpliterator(theMap.keySet().spliterator(fromStart));
		}

		class ValueSpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<V> {
			private final MutableElementSpliterator<K> theKeySpliter;

			ValueSpliterator(MutableElementSpliterator<K> keySpliter) {
				super(BetterMapValueCollection.this);
				theKeySpliter = keySpliter;
			}

			@Override
			public long estimateSize() {
				return theKeySpliter.estimateSize();
			}

			@Override
			public long getExactSizeIfKnown() {
				return theKeySpliter.getExactSizeIfKnown();
			}

			@Override
			public int characteristics() {
				return theKeySpliter.characteristics() & (~SORTED);
			}

			@Override
			protected boolean internalForElement(Consumer<? super CollectionElement<V>> action, boolean forward) {
				return theKeySpliter.forElement(keyEl -> action.accept(theMap.getEntryById(keyEl.getElementId())), forward);
			}

			@Override
			protected boolean internalForElementM(Consumer<? super MutableCollectionElement<V>> action, boolean forward) {
				return theKeySpliter.forElement(keyEl -> theMap.forMutableEntry(keyEl.getElementId(), action), forward);
			}

			@Override
			public MutableElementSpliterator<V> trySplit() {
				MutableElementSpliterator<K> keySplit = theKeySpliter.trySplit();
				return keySplit == null ? null : new ValueSpliterator(keySplit);
			}
		}
	}
}
