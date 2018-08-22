package org.qommons.collect;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Supplier;

public class BetterMapEntryImpl<K, V> implements MapEntryHandle<K, V> {
	K theKey;
	V theValue;
	protected ElementId theId; // Protected so implementations can set this

	private MutableMapEntryHandle<K, V> mutableHandle;
	private CollectionElement<K> keyHandle;
	private MutableCollectionElement<K> mutableKeyHandle;

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

	protected MutableMapEntryHandle<K, V> mutable(BetterSet<Map.Entry<K, V>> entrySet, Supplier<BetterCollection<V>> values) {
		if (mutableHandle == null) {
			mutableHandle = createMutableHandle(entrySet, values);
		}
		return mutableHandle;
	}

	protected CollectionElement<K> keyHandle() {
		if (keyHandle == null) {
			keyHandle = new CollectionElement<K>() {
				@Override
				public ElementId getElementId() {
					return theId;
				}

				@Override
				public K get() {
					return theKey;
				}
			};
		}
		return keyHandle;
	}

	protected MutableCollectionElement<K> mutableKeyHandle(BetterSet<Map.Entry<K, V>> entrySet, Supplier<BetterSet<K>> keySet) {
		if (mutableKeyHandle == null) {
			MutableCollectionElement<Map.Entry<K, V>> mutableEntryEl = entrySet.mutableElement(theId);
			mutableKeyHandle = new MutableCollectionElement<K>() {
				@Override
				public BetterCollection<K> getCollection() {
					return keySet.get();
				}

				@Override
				public ElementId getElementId() {
					return theId;
				}

				@Override
				public K get() {
					return theKey;
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
					theKey = value;
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
					return keyHandle();
				}
			};
		}
		return mutableKeyHandle;
	}

	protected MutableMapEntryHandle<K, V> createMutableHandle(BetterSet<Entry<K, V>> entrySet, Supplier<BetterCollection<V>> values) {
		return new BetterMapMutableEntryHandleImpl<>(this, entrySet, values);
	}

	public static class BetterMapMutableEntryHandleImpl<K, V> implements MutableMapEntryHandle<K, V> {
		private final BetterMapEntryImpl<K, V> theEntry;
		private final MutableCollectionElement<Map.Entry<K, V>> theMutableEntryEl;
		private final Supplier<BetterCollection<V>> theValues;

		public BetterMapMutableEntryHandleImpl(BetterMapEntryImpl<K, V> entry, BetterSet<Entry<K, V>> entrySet,
			Supplier<BetterCollection<V>> values) {
			theEntry = entry;
			theMutableEntryEl = entrySet.mutableElement(theEntry.theId);
			theValues = values;
		}

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
		public String canAdd(V value, boolean before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ElementId add(V value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
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
