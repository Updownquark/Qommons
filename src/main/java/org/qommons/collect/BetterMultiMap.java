package org.qommons.collect;

import java.util.Collection;

import org.qommons.Identifiable;
import org.qommons.StructuredStamped;
import org.qommons.Transaction;

/**
 * An {@link CollectionElement element}-accessible structure of many values per key
 * 
 * @param <K> The type of keys in the map
 * @param <V> The type of values in the map
 */
public interface BetterMultiMap<K, V> extends TransactableMultiMap<K, V>, StructuredStamped, Identifiable {
	@Override
	BetterSet<K> keySet();

	@Override
	BetterSet<? extends MultiEntryHandle<K, V>> entrySet();

	@Override
	BetterCollection<V> get(Object key);

	default MultiEntryHandle<K, V> getEntry(K key) {
		try (Transaction t = lock(false, null)) {
			CollectionElement<K> keyElement = keySet().getElement(key, true);
			return keyElement == null ? null : getEntry(keyElement.getElementId());
		}
	}

	MultiEntryHandle<K, V> getEntry(ElementId keyId);

	default MultiEntryValueHandle<K, V> getElement(K key, V value, boolean first) {
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

	default MultiEntryValueHandle<K, V> getElement(ElementId keyId, ElementId valueId) {
		MultiEntryHandle<K, V> keyElement = getEntry(keyId);
		CollectionElement<V> valueElement = keyElement.getValues().getElement(valueId);
		return new MultiEntryValueHandle<K, V>() {
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

	default MultiEntryValueHandle<K, V> putEntry(K key, V value, boolean first) {
		return putEntry(key, value, null, null, first);
	}

	MultiEntryValueHandle<K, V> putEntry(K key, V value, ElementId afterKey, ElementId beforeKey, boolean first);

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
		private Object theIdentity;

		public ReversedMultiMap(BetterMultiMap<K, V> source) {
			theSource = source;
		}

		protected BetterMultiMap<K, V> getSource() {
			return theSource;
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(theSource.getIdentity(), "reverse");
			return theIdentity;
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theSource.lock(write, structural, cause);
		}

		@Override
		public Transaction tryLock(boolean write, boolean structural, Object cause) {
			return theSource.tryLock(write, structural, cause);
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
		public BetterSet<? extends MultiEntryHandle<K, V>> entrySet() {
			return theSource.entrySet().reverse();
		}

		@Override
		public BetterCollection<V> get(Object key) {
			return theSource.get(key).reverse();
		}

		@Override
		public MultiEntryHandle<K, V> getEntry(ElementId keyId) {
			return MultiEntryHandle.reverse(theSource.getEntry(keyId.reverse()));
		}

		@Override
		public MultiEntryValueHandle<K, V> putEntry(K key, V value, ElementId afterKey, ElementId beforeKey, boolean first) {
			return MultiEntryValueHandle
				.reverse(theSource.putEntry(key, value, ElementId.reverse(beforeKey), ElementId.reverse(afterKey), !first));
		}

		@Override
		public BetterMultiMap<K, V> reverse() {
			return theSource;
		}
	}
}
