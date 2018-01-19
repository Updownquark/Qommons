package org.qommons.collect;

import java.util.Collection;

import org.qommons.Transaction;

public interface BetterMultiMap<K, V> extends TransactableMultiMap<K, V> {
	@Override
	BetterSet<K> keySet();

	@Override
	BetterSet<? extends MultiEntry<K, V>> entrySet();

	@Override
	BetterCollection<V> get(Object key);

	@Override
	default boolean isLockSupported() {
		return keySet().isLockSupported();
	}

	@Override
	default Transaction lock(boolean write, Object cause) {
		return keySet().lock(write, cause);
	}

	@Override
	Transaction lock(boolean write, boolean structural, Object cause);

	long getStamp(boolean structuralOnly);

	default MultiEntryHandle<K, V> getEntry(K key) {
		try (Transaction t = lock(false, null)) {
			CollectionElement<K> keyElement = keySet().getElement(key, true);
			return keyElement == null ? null : getEntry(keyElement.getElementId());
		}
	}

	MultiEntryHandle<K, V> getEntry(ElementId keyId);

	default MultiMapEntryHandle<K, V> getElement(K key, V value, boolean first) {
		try (Transaction t = lock(false, null)) {
			MultiEntryHandle<K, V> keyElement = getEntry(key);
			if (keyElement == null)
				return null;
			CollectionElement<V> valueElement = keyElement.getValues().getElement(value, first);
			if (valueElement == null)
				return null;
			return getElement(keyElement.getElementId(), valueElement.getElementId());
		}
	}

	default MultiMapEntryHandle<K, V> getElement(ElementId keyId, ElementId valueId) {
		MultiEntryHandle<K, V> keyElement = getEntry(keyId);
		CollectionElement<V> valueElement = keyElement.getValues().getElement(valueId);
		return new MultiMapEntryHandle<K, V>() {
			@Override
			public ElementId getKeyId() {
				return keyElement.getElementId();
			}

			@Override
			public K getKey() {
				return keyElement.getKey();
			}

			@Override
			public ElementId getElementId() {
				return valueElement.getElementId();
			}

			@Override
			public V get() {
				return valueElement.get();
			}
		};
	}

	default MutableMultiMapHandle<K, V> mutableElement(ElementId keyId, ElementId valueId) {
		MultiEntryHandle<K, V> keyElement = getEntry(keyId);
		MutableCollectionElement<V> valueElement = keyElement.getValues().mutableElement(valueId);
		return new MutableMultiMapHandle<K, V>() {
			@Override
			public ElementId getKeyId() {
				return keyElement.getElementId();
			}

			@Override
			public K getKey() {
				return keyElement.getKey();
			}

			@Override
			public BetterCollection<V> getCollection() {
				return keyElement.getValues();
			}

			@Override
			public ElementId getElementId() {
				return valueElement.getElementId();
			}

			@Override
			public V get() {
				return valueElement.get();
			}

			@Override
			public String isEnabled() {
				return valueElement.isEnabled();
			}

			@Override
			public String isAcceptable(V value) {
				return valueElement.isAcceptable(value);
			}

			@Override
			public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
				valueElement.set(value);
			}

			@Override
			public String canRemove() {
				return valueElement.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				valueElement.remove();
			}

			@Override
			public String canAdd(V value, boolean before) {
				return valueElement.canAdd(value, before);
			}

			@Override
			public ElementId add(V value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				return valueElement.add(value, before);
			}
		};
	}

	default MultiMapEntryHandle<K, V> putEntry(K key, V value, boolean first) {
		return putEntry(key, value, null, null, first);
	}

	MultiMapEntryHandle<K, V> putEntry(K key, V value, ElementId afterKey, ElementId beforeKey, boolean first);

	@Override
	default boolean add(K key, V value) {
		return get(key).add(value);
	}

	@Override
	default boolean addAll(K key, Collection<? extends V> values) {
		return get(key).addAll(values);
	}

	@Override
	default boolean remove(K key, Object value) {
		return get(key).remove(value);
	}

	@Override
	default boolean removeAll(K key) {
		try (Transaction t = lock(true, true, null)) {
			Collection<V> values = get(key);
			if (values.isEmpty())
				return false;
			values.clear();
			return true;
		}
	}

	default BetterMultiMap<K, V> reverse() {
		return new ReversedMultiMap<>(this);
	}

	class ReversedMultiMap<K, V> implements BetterMultiMap<K, V> {
		private final BetterMultiMap<K, V> theSource;

		public ReversedMultiMap(BetterMultiMap<K, V> source) {
			theSource = source;
		}

		protected BetterMultiMap<K, V> getSource() {
			return theSource;
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theSource.lock(write, structural, cause);
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theSource.getStamp(structuralOnly);
		}

		@Override
		public BetterSet<K> keySet() {
			return theSource.keySet().reverse();
		}

		@Override
		public BetterSet<? extends MultiEntry<K, V>> entrySet() {
			return theSource.entrySet().reverse();
		}

		@Override
		public BetterCollection<V> get(Object key) {
			return theSource.get(key).reverse();
		}

		@Override
		public MultiEntryHandle<K, V> getEntry(ElementId keyId) {
			return theSource.getEntry(keyId.reverse()).reverse();
		}

		@Override
		public MultiMapEntryHandle<K, V> putEntry(K key, V value, ElementId afterKey, ElementId beforeKey, boolean first) {
			return MultiMapEntryHandle
				.reverse(theSource.putEntry(key, value, ElementId.reverse(beforeKey), ElementId.reverse(afterKey), !first));
		}

		@Override
		public BetterMultiMap<K, V> reverse() {
			return theSource;
		}
	}
}
