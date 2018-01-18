package org.qommons.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

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
	default Transaction lock(boolean write, Object cause) {
		return keySet().lock(write, cause);
	}

	@Override
	default Transaction lock(boolean write, boolean structural, Object cause) {
		return keySet().lock(write, structural, cause);
	}

	default long getStamp(boolean structuralOnly) {
		return keySet().getStamp(structuralOnly);
	}

	default MapEntryHandle<K, V> putEntry(K key, V value, boolean first){
		return putEntry(key, value, null, null, first);
	}

	MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first);

	MapEntryHandle<K, V> getEntry(K key);

	MapEntryHandle<K, V> getEntryById(ElementId entryId);

	MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId);

	default void forMutableEntry(ElementId entryId, Consumer<? super MutableMapEntryHandle<K, V>> onEntry) {
		onEntry.accept(mutableEntry(entryId));
	}

	default <X> X ofMutableEntry(ElementId entryId, Function<? super MutableMapEntryHandle<K, V>, X> onEntry) {
		return onEntry.apply(mutableEntry(entryId));
	}

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
		MapEntryHandle<K, V> entry = getEntry((K) key);
		if (entry == null)
			return null;
		return ofMutableEntry(entry.getElementId(), el -> {
			V old = el.get();
			el.remove();
			return old;
		});
	}

	@Override
	default V put(K key, V value) {
		try (Transaction t = lock(true, null)) {
			MapEntryHandle<K, V> entry = getEntry(key);
			if (entry != null)
				return ofMutableEntry(entry.getElementId(), el -> {
					V old = el.get();
					el.set(value);
					return old;
				});
			else {
				putEntry(key, value, false);
				return null;
			}
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
	}

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
		public CollectionElement<Entry<K, V>> getElement(Entry<K, V> value, boolean first) {
			if (value == null)
				return null;
			MapEntryHandle<K, V> entry = theMap.getEntry(value.getKey());
			return entry == null ? null : new EntryElement(entry);
		}

		@Override
		public CollectionElement<Entry<K, V>> getElement(ElementId id) {
			return new EntryElement(theMap.getEntryById(id));
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
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<Entry<K, V>> addElement(Entry<K, V> value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public boolean addAll(Collection<? extends java.util.Map.Entry<K, V>> c) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			theMap.clear();
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
							V current=theEntry.get();
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

			@Override
			public String canAdd(java.util.Map.Entry<K, V> value, boolean before) {
				return theMap.keySet().mutableElement(getEntry().getElementId()).canAdd(value.getKey(), before);
			}

			@Override
			public ElementId add(java.util.Map.Entry<K, V> value, boolean before)
				throws UnsupportedOperationException, IllegalArgumentException {
				ElementId id = theMap.keySet().mutableElement(getEntry().getElementId()).add(value.getKey(), before);
				theMap.mutableEntry(id).set(value.getValue());
				return id;
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
				return theKeySpliter.forElement(keyEl -> action.accept(new EntryElement(theMap.getEntryById(keyEl.getElementId()))), forward);
			}

			@Override
			public MutableElementSpliterator<Map.Entry<K, V>> trySplit() {
				MutableElementSpliterator<K> keySplit = theKeySpliter.trySplit();
				return keySplit == null ? null : new EntrySpliterator(keySplit);
			}
		}
	}

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
				}, first)) {
				}
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
