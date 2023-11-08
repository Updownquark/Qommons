package org.qommons.collect;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A {@link MapEntryHandle} implementation for {@link BetterMap} implementations that caches some things for performance
 * 
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public class BetterMapEntryImpl<K, V> implements MapEntryHandle<K, V> {
	K theKey;
	V theValue;
	/** The element ID of this entry */
	protected ElementId theId; // Protected so implementations can set this

	private MutableMapEntryHandle<K, V> mutableHandle;
	private CollectionElement<K> keyHandle;
	private MutableCollectionElement<K> mutableKeyHandle;

	/**
	 * @param key The key of this entry
	 * @param value The value of this entry
	 */
	public BetterMapEntryImpl(K key, V value) {
		theKey = key;
		theValue = value;
	}

	@Override
	public ElementId getElementId() {
		return theId;
	}

	@Override
	public K getKey() {
		return theKey;
	}

	@Override
	public V get() {
		return theValue;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(theKey);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Map.Entry && Objects.equals(((Map.Entry<?, ?>) o).getKey(), theKey);
	}

	@Override
	public String toString() {
		return new StringBuilder().append(theKey).append('=').append(theValue).toString();
	}

	/**
	 * @param entrySet The entry set for the map
	 * @param values Supplies the {@link BetterMap#values() values} collection for the map
	 * @return The mutable handle for this map entry
	 */
	protected MutableMapEntryHandle<K, V> mutable(BetterSet<Map.Entry<K, V>> entrySet, Supplier<BetterCollection<V>> values) {
		if (mutableHandle == null) {
			mutableHandle = createMutableHandle(entrySet, values);
		}
		return mutableHandle;
	}

	/**
	 * Creates the mutable entry
	 * 
	 * @param entrySet The entry set for the map
	 * @param values Supplies the {@link BetterMap#values() values} collection for the map
	 * @return The mutable handle for this map entry
	 */
	protected MutableMapEntryHandle<K, V> createMutableHandle(BetterSet<Entry<K, V>> entrySet, Supplier<BetterCollection<V>> values) {
		return new BetterMapMutableEntryHandleImpl<>(this, entrySet, values);
	}

	/** @return The collection element for this entry in the {@link BetterMap#keySet() key set} */
	protected CollectionElement<K> keyHandle() {
		if (keyHandle == null) {
			keyHandle = makeKeyHandle();
		}
		return keyHandle;
	}

	/**
	 * Creates the key set element for this entry
	 * 
	 * @return The collection element for this entry in the {@link BetterMap#keySet() key set}
	 */
	protected CollectionElement<K> makeKeyHandle() {
		return new BetterMapEntryKeyHandle<>(this);
	}

	/**
	 * @param entrySet The entry set for the map
	 * @param keySet Supplies the {@link BetterMap#keySet() key set}
	 * @return The mutable element for this entry in the {@link BetterMap#keySet() key set}
	 */
	protected MutableCollectionElement<K> mutableKeyHandle(BetterSet<Map.Entry<K, V>> entrySet, Supplier<BetterSet<K>> keySet) {
		if (mutableKeyHandle == null) {
			mutableKeyHandle = createMutableKeyHandle(entrySet, keySet);
		}
		return mutableKeyHandle;
	}

	/**
	 * Creates the mutable key handle
	 * 
	 * @param entrySet The entry set for the map
	 * @param keySet Supplies the {@link BetterMap#keySet() key set}
	 * @return The mutable element for this entry in the {@link BetterMap#keySet() key set}
	 */
	protected MutableCollectionElement<K> createMutableKeyHandle(BetterSet<Entry<K, V>> entrySet, Supplier<BetterSet<K>> keySet) {
		MutableCollectionElement<Map.Entry<K, V>> mutableEntryEl = entrySet.mutableElement(theId);
		return new BetterMapEntryMutableKeyHandle<>(this, mutableEntryEl, keySet);
	}

	/**
	 * Implements {@link BetterMapEntryImpl#keyHandle()}
	 * 
	 * @param <K> The key type of the map
	 */
	public static class BetterMapEntryKeyHandle<K> implements CollectionElement<K> {
		private final BetterMapEntryImpl<K, ?> theEntry;

		/** @param entry The entry that this key handle is for */
		public BetterMapEntryKeyHandle(BetterMapEntryImpl<K, ?> entry) {
			theEntry = entry;
		}

		/** @return The entry that this key handle is for */
		protected BetterMapEntryImpl<K, ?> getEntry() {
			return theEntry;
		}

		@Override
		public ElementId getElementId() {
			return theEntry.theId;
		}

		@Override
		public K get() {
			return theEntry.theKey;
		}
	}

	/**
	 * Implements {@link BetterMapEntryImpl#mutableKeyHandle(BetterSet, Supplier)}
	 * 
	 * @param <K> The key type of the map
	 */
	public static class BetterMapEntryMutableKeyHandle<K> extends BetterMapEntryKeyHandle<K> implements MutableCollectionElement<K> {
		private final MutableCollectionElement<Entry<K, ?>> mutableEntryEl;
		private final Supplier<BetterSet<K>> keySet;

		/**
		 * @param entry The entry that this key handle is for
		 * @param mutableEntryEl The mutable entry for this entry in the {@link BetterMap#entrySet() entry set}
		 * @param keySet Supplies the {@link BetterMap#keySet() key set}
		 */
		public BetterMapEntryMutableKeyHandle(BetterMapEntryImpl<K, ?> entry,
			MutableCollectionElement<? extends Map.Entry<K, ?>> mutableEntryEl, Supplier<BetterSet<K>> keySet) {
			super(entry);
			this.mutableEntryEl = (MutableCollectionElement<Entry<K, ?>>) mutableEntryEl;
			this.keySet = keySet;
		}

		@Override
		public BetterCollection<K> getCollection() {
			return keySet.get();
		}

		@Override
		public String isEnabled() {
			return mutableEntryEl.isEnabled();
		}

		@Override
		public String isAcceptable(K value) {
			return mutableEntryEl.isAcceptable(new SimpleMapEntry<>(value, null));
		}

		@Override
		public void set(K value) throws UnsupportedOperationException, IllegalArgumentException {
			K preKey = getEntry().theKey;
			getEntry().theKey = value;
			try {
				mutableEntryEl.set(getEntry());
			} catch (UnsupportedOperationException | IllegalArgumentException e) {
				getEntry().theKey = preKey;
				throw e;
			}
		}

		@Override
		public String canRemove() {
			return mutableEntryEl.canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			mutableEntryEl.remove();
		}

		@Override
		public CollectionElement<K> immutable() {
			return getEntry().keyHandle();
		}
	}

	/**
	 * Implements {@link BetterMapEntryImpl#mutable(BetterSet, Supplier)}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static class BetterMapMutableEntryHandleImpl<K, V> implements MutableMapEntryHandle<K, V> {
		private final BetterMapEntryImpl<K, V> theEntry;
		private final MutableCollectionElement<Map.Entry<K, V>> theMutableEntryEl;
		private final Supplier<BetterCollection<V>> theValues;

		/**
		 * @param entry The entry that this mutable entry is for
		 * @param entrySet The {@link BetterMap#entrySet() entry set} for the map
		 * @param values Supplies the {@link BetterMap#values() values} collection for the map
		 */
		public BetterMapMutableEntryHandleImpl(BetterMapEntryImpl<K, V> entry, BetterSet<Entry<K, V>> entrySet,
			Supplier<BetterCollection<V>> values) {
			theEntry = entry;
			theMutableEntryEl = entrySet.mutableElement(theEntry.theId);
			theValues = values;
		}

		/** @return The entry that this mutable entry is for */
		protected BetterMapEntryImpl<K, V> getEntry() {
			return theEntry;
		}

		@Override
		public BetterCollection<V> getCollection() {
			return theValues.get();
		}

		@Override
		public ElementId getElementId() {
			return theEntry.theId;
		}

		@Override
		public K getKey() {
			return theEntry.theKey;
		}

		@Override
		public V get() {
			return theEntry.theValue;
		}

		@Override
		public String isEnabled() {
			return null;
		}

		@Override
		public String isAcceptable(V value) {
			return null; // check with the values collection?
		}

		@Override
		public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
			theEntry.theValue = value;
			theMutableEntryEl.set(theMutableEntryEl.get());
		}

		@Override
		public String canRemove() {
			return theMutableEntryEl.canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			theMutableEntryEl.remove();
		}

		@Override
		public MapEntryHandle<K, V> immutable() {
			return theEntry;
		}

		@Override
		public int hashCode() {
			return theEntry.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theEntry.equals(obj);
		}

		@Override
		public String toString() {
			return theEntry.toString();
		}
	}
}
