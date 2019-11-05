package org.qommons.collect;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

import org.qommons.Identifiable;
import org.qommons.QommonsUtils;
import org.qommons.StructuredStamped;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

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
	default BetterSet<? extends MultiEntryHandle<K, V>> entrySet() {
		return new BetterMultiMapEntrySet<>(this);
	}

	@Override
	default BetterCollection<? extends MultiEntryValueHandle<K, V>> singleEntries() {
		return new BetterMapSingleEntryCollection<>(this);
	}

	/**
	 * @param keyId The key ID of the entry
	 * @return The entry in this map with the given ID
	 */
	MultiEntryHandle<K, V> getEntryById(ElementId keyId);

	/**
	 * @param keyId The key ID of the entry
	 * @param valueId The value ID of the entry
	 * @return The value entry in this map with the given IDs
	 */
	default MultiEntryValueHandle<K, V> getEntryById(ElementId keyId, ElementId valueId) {
		return new DefaultValueHandle<>(getEntryById(keyId), valueId);
	}

	/**
	 * @param first Whether to get the first or last entry in the map
	 * @return The first or last entry in this map, or null if the map is empty
	 */
	default MultiEntryHandle<K, V> getTerminalEntry(boolean first) {
		try (Transaction t = lock(false, null)) {
			CollectionElement<K> keyEl = keySet().getTerminalElement(first);
			return keyEl == null ? null : getEntryById(keyEl.getElementId());
		}
	}

	/**
	 * @param entryId The entry to get the adjacent entry for
	 * @param next Whether to get the next or previous entry
	 * @return The adjacent entry, or null if the given entry is terminal in the given direction
	 */
	default MultiEntryHandle<K, V> getAdjacentEntry(ElementId entryId, boolean next) {
		try (Transaction t = lock(false, null)) {
			CollectionElement<K> keyEl = keySet().getAdjacentElement(entryId, next);
			return keyEl == null ? null : getEntryById(keyEl.getElementId());
		}
	}

	@Override
	BetterCollection<V> get(Object key);

	/**
	 * @param key The key of the entry
	 * @return The entry in this map with the given key, or null if no such entry exists
	 */
	default MultiEntryHandle<K, V> getEntry(K key) {
		try (Transaction t = lock(false, null)) {
			CollectionElement<K> keyElement = keySet().getElement(key, true);
			return keyElement == null ? null : getEntryById(keyElement.getElementId());
		}
	}

	/**
	 * @param key The key of the entry
	 * @param value The value of the entry
	 * @param first Whether to search for the first or last value in its key entry
	 * @return The value entry in this map with the given key and value, or null if no such entry exists
	 */
	default MultiEntryValueHandle<K, V> getEntry(K key, V value, boolean first) {
		try (Transaction t = lock(false, null)) {
			MultiEntryHandle<K, V> keyElement = getEntry(key);
			if (keyElement == null)
				return null;
			CollectionElement<V> valueElement = keyElement.getValues().getElement(value, first);
			if (valueElement == null)
				return null;
			return getEntryById(keyElement.getElementId(), valueElement.getElementId());
		}
	}

	/**
	 * @param keyId The key ID of the entry
	 * @param valueId The value ID of the entry
	 * @return The mutable value entry in this map with the given IDs
	 */
	default MutableMultiMapHandle<K, V> mutableElement(ElementId keyId, ElementId valueId) {
		MultiEntryHandle<K, V> keyElement = getEntryById(keyId);
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

	/**
	 * @param key The key for the entry
	 * @param value The value to insert
	 * @param first Whether to prefer inserting the value earlier or later in the key entry
	 * @return The entry for the key into which the value may have been inserted (normal {@link Collection#add(Object)} rules may apply)
	 */
	default MultiEntryValueHandle<K, V> putEntry(K key, V value, boolean first) {
		return putEntry(key, value, null, null, first);
	}

	/**
	 * @param key The key for the entry
	 * @param value The value to insert
	 * @param afterKey The key element after which to insert the entry (if it must be created, may be null)
	 * @param beforeKey The key element before which to insert the entry (if it must be created, may be null)
	 * @param first Whether to prefer inserting the key entry (and the value element) earlier or later in the map
	 * @return The entry for the key into which the value may have been inserted (normal {@link Collection#add(Object)} rules may apply)
	 */
	default MultiEntryValueHandle<K, V> putEntry(K key, V value, ElementId afterKey, ElementId beforeKey, boolean first) {
		boolean[] added = new boolean[1];
		MultiEntryHandle<K, V> entry = getOrPutEntry(key, k -> BetterList.of(value), afterKey, beforeKey, first, () -> added[0] = true);
		if (entry == null)
			return null;
		CollectionElement<V> valueEl;
		if (added[0])
			valueEl = entry.getValues().getTerminalElement(true);
		else
			valueEl = entry.getValues().addElement(value, first);
		if (valueEl == null)
			return null;
		return getEntryById(entry.getElementId(), valueEl.getElementId());
	}

	/**
	 * Retrieves or adds an entry for the given key and returns the entry of the affected element
	 * 
	 * @param key The key to retrieve or insert the value for
	 * @param value The function to produce the initial values for the added entry, if not present
	 * @param afterKey The key element after which to insert the entry (if it must be added, null allowed)
	 * @param beforeKey The key element before which to insert the entry (if it must be added, null allowed)
	 * @param first Whether to prefer adding the new entry early or late in the key/entry set
	 * @param added The runnable that will be invoked if the entry is added
	 * @return The entry of the element if retrieved or added; may be null if key/value pair is not permitted in the map
	 */
	MultiEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
		ElementId beforeKey, boolean first, Runnable added);

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

	/**
	 * @param firstValue Whether to use the first value in the map for each key, or the last value
	 * @return A BetterMap with the same key set as this, but only a single value per key
	 */
	default BetterMap<K, V> singleMap(boolean firstValue) {
		return new SingleMap<>(this, firstValue);
	}

	/** @return A map with this map's content but whose key and value collections are reversed from this */
	default BetterMultiMap<K, V> reverse() {
		return new ReversedMultiMap<>(this);
	}

	/**
	 * Implements {@link BetterMultiMap#entrySet()}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class BetterMultiMapEntrySet<K, V> extends AbstractIdentifiable implements BetterSet<MultiEntryHandle<K, V>> {
		private final BetterMultiMap<K, V> theMap;

		public BetterMultiMapEntrySet(BetterMultiMap<K, V> map) {
			theMap = map;
		}

		protected BetterMultiMap<K, V> getMap() {
			return theMap;
		}

		protected CollectionElement<MultiEntryHandle<K, V>> entryFor(MultiEntryHandle<K, V> entry) {
			return entry == null ? null : new EntrySetElement(entry);
		}

		protected MutableCollectionElement<MultiEntryHandle<K, V>> mutableEntryFor(MultiEntryHandle<K, V> entry) {
			return entry == null ? null : new MutableEntrySetElement(entry);
		}

		@Override
		public boolean belongs(Object o) {
			return o instanceof BetterMultiMapEntrySet.EntrySetElement && ((EntrySetElement) o).getCollection() == this;
		}

		@Override
		public CollectionElement<MultiEntryHandle<K, V>> getElement(MultiEntryHandle<K, V> value, boolean first) {
			return entryFor(getMap().getEntryById(value.getElementId()));
		}

		@Override
		public CollectionElement<MultiEntryHandle<K, V>> getElement(ElementId id) {
			return entryFor(getMap().getEntryById(id));
		}

		@Override
		public CollectionElement<MultiEntryHandle<K, V>> getTerminalElement(boolean first) {
			try (Transaction t = lock(false, true, null)) {
				CollectionElement<K> keyEl = getMap().keySet().getTerminalElement(first);
				return keyEl == null ? null : entryFor(getMap().getEntryById(keyEl.getElementId()));
			}
		}

		@Override
		public CollectionElement<MultiEntryHandle<K, V>> getAdjacentElement(ElementId elementId, boolean next) {
			try (Transaction t = lock(false, true, null)) {
				CollectionElement<K> keyEl = getMap().keySet().getAdjacentElement(elementId, next);
				return keyEl == null ? null : entryFor(getMap().getEntryById(keyEl.getElementId()));
			}
		}

		@Override
		public MutableCollectionElement<MultiEntryHandle<K, V>> mutableElement(ElementId id) {
			return mutableEntryFor(getMap().getEntryById(id));
		}

		@Override
		public BetterList<CollectionElement<MultiEntryHandle<K, V>>> getElementsBySource(ElementId sourceEl) {
			return QommonsUtils.map2(getMap().keySet().getElementsBySource(sourceEl), //
				keyEl -> entryFor(getMap().getEntryById(keyEl.getElementId())));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(localElement);
			return getMap().keySet().getSourceElements(localElement, sourceCollection);
		}

		@Override
		public String canAdd(MultiEntryHandle<K, V> value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<MultiEntryHandle<K, V>> addElement(MultiEntryHandle<K, V> value, ElementId after, ElementId before,
			boolean first) throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			getMap().clear();
		}

		@Override
		public Object createIdentity() {
			return Identifiable.wrap(getMap().getIdentity(), "entrySet");
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return getMap().getStamp(structuralOnly);
		}

		@Override
		public boolean isLockSupported() {
			return getMap().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return getMap().lock(write, structural, cause);
		}

		@Override
		public Transaction tryLock(boolean write, boolean structural, Object cause) {
			return getMap().tryLock(write, structural, cause);
		}

		@Override
		public int size() {
			return getMap().keySet().size();
		}

		@Override
		public boolean isEmpty() {
			return getMap().keySet().isEmpty();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return BetterSet.super.toArray(a);
		}

		@Override
		public MultiEntryHandle<K, V>[] toArray() {
			try (Transaction t = lock(false, null)) {
				return toArray(new MultiEntryHandle[size()]);
			}
		}

		@Override
		public CollectionElement<MultiEntryHandle<K, V>> getOrAdd(MultiEntryHandle<K, V> value, ElementId after, ElementId before,
			boolean first, Runnable added) {
			return entryFor(getMap().getOrPutEntry(value.getKey(), k -> value.getValues(), after, before, first, added));
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return getMap().keySet().isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			return getMap().keySet().checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<MultiEntryHandle<K, V>, X> listener) {
			return getMap().keySet().repair(element, //
				listener == null ? null : new KeyRepairListener<>(listener));
		}

		@Override
		public <X> boolean repair(RepairListener<MultiEntryHandle<K, V>, X> listener) {
			return getMap().keySet().repair(listener == null ? null : new KeyRepairListener<>(listener));
		}

		private class EntrySetElement implements CollectionElement<MultiEntryHandle<K, V>> {
			final MultiEntryHandle<K, V> theEntry;

			EntrySetElement(MultiEntryHandle<K, V> entry) {
				theEntry = entry;
			}

			@Override
			public ElementId getElementId() {
				return theEntry.getElementId();
			}

			@Override
			public MultiEntryHandle<K, V> get() {
				return theEntry;
			}

			public BetterCollection<MultiEntryHandle<K, V>> getCollection() {
				return BetterMultiMapEntrySet.this;
			}
		}

		private class MutableEntrySetElement extends EntrySetElement implements MutableCollectionElement<MultiEntryHandle<K, V>> {
			MutableEntrySetElement(MultiEntryHandle<K, V> entry) {
				super(entry);
			}

			@Override
			public String isEnabled() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public String isAcceptable(MultiEntryHandle<K, V> value) {
				return isEnabled();
			}

			@Override
			public void set(MultiEntryHandle<K, V> value) throws UnsupportedOperationException, IllegalArgumentException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String canRemove() {
				return getMap().keySet().mutableElement(theEntry.getElementId()).canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				getMap().keySet().mutableElement(theEntry.getElementId()).remove();
			}
		}

		private class KeyRepairListener<X> implements RepairListener<K, EntryRemovedData<K, V, X>> {
			private final RepairListener<MultiEntryHandle<K, V>, X> theEntryRepairListener;

			public KeyRepairListener(
				org.qommons.collect.ValueStoredCollection.RepairListener<MultiEntryHandle<K, V>, X> entryRepairListener) {
				theEntryRepairListener = entryRepairListener;
			}

			@Override
			public EntryRemovedData<K, V, X> removed(CollectionElement<K> element) {
				CollectionElement<MultiEntryHandle<K, V>> entry = entryFor(getMap().getEntryById(element.getElementId()));
				return new EntryRemovedData<>(theEntryRepairListener.removed(entry), //
					entry.get());
			}

			@Override
			public void disposed(K value, EntryRemovedData<K, V, X> data) {
				theEntryRepairListener.disposed(data.entry, data.listenerData);
			}

			@Override
			public void transferred(CollectionElement<K> element, EntryRemovedData<K, V, X> data) {
				theEntryRepairListener.transferred(entryFor(getMap().getEntryById(element.getElementId())), data.listenerData);
			}
		}

		static class EntryRemovedData<K, V, X> {
			private final X listenerData;
			private final MultiEntryHandle<K, V> entry;

			EntryRemovedData(X listenerData, MultiEntryHandle<K, V> entry) {
				this.listenerData = listenerData;
				this.entry = entry;
			}
		}
	}

	/**
	 * Implements {@link BetterMultiMap#singleEntries()}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class BetterMapSingleEntryCollection<K, V> extends AbstractIdentifiable implements BetterCollection<MultiEntryValueHandle<K, V>> {
		private final BetterMultiMap<K, V> theMap;

		public BetterMapSingleEntryCollection(BetterMultiMap<K, V> map) {
			theMap = map;
		}

		protected BetterMultiMap<K, V> getMap() {
			return theMap;
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return getMap().getStamp(structuralOnly);
		}

		@Override
		public boolean isEmpty() {
			return getMap().keySet().isEmpty();
		}

		@Override
		public boolean isLockSupported() {
			return getMap().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return getMap().lock(write, structural, cause);
		}

		@Override
		public Transaction tryLock(boolean write, boolean structural, Object cause) {
			return getMap().tryLock(write, structural, cause);
		}

		@Override
		public Object createIdentity() {
			return Identifiable.wrap(getMap().getIdentity(), "entryValues");
		}

		@Override
		public int size() {
			return getMap().valueSize();
		}

		protected boolean keysEqual(K key1, K key2) {
			return Objects.equals(key1, key2);
		}

		protected CollectionElement<MultiEntryValueHandle<K, V>> entryFor(MultiEntryValueHandle<K, V> entry) {
			return entry == null ? null : new ValueHandleElement(entry);
		}

		protected MutableCollectionElement<MultiEntryValueHandle<K, V>> mutableEntryFor(MultiEntryValueHandle<K, V> entry) {
			return entry == null ? null : new MutableValueHandleElement(entry);
		}

		@Override
		public boolean belongs(Object o) {
			return o instanceof MultiEntryValueHandle && getMap().keySet().belongs(((MultiEntryValueHandle<?, ?>) o).getKey());
		}

		@Override
		public CollectionElement<MultiEntryValueHandle<K, V>> getElement(MultiEntryValueHandle<K, V> value, boolean first) {
			return entryFor(getMap().getEntryById(value.getKeyId(), value.getElementId()));
		}

		@Override
		public CollectionElement<MultiEntryValueHandle<K, V>> getElement(ElementId id) {
			if (!(id instanceof KeyValueElementId))
				throw new NoSuchElementException();
			KeyValueElementId kvId = (KeyValueElementId) id;
			return entryFor(getMap().getEntryById(kvId.keyId, kvId.valueId));
		}

		@Override
		public CollectionElement<MultiEntryValueHandle<K, V>> getTerminalElement(boolean first) {
			try (Transaction t = lock(false, null)) {
				CollectionElement<K> keyEl = getMap().keySet().getTerminalElement(first);
				if (keyEl == null)
					return null;
				MultiEntryHandle<K, V> entry = getMap().getEntryById(keyEl.getElementId());
				CollectionElement<V> valueEl = entry.getValues().getTerminalElement(first);
				return valueEl == null ? null : entryFor(getMap().getEntryById(keyEl.getElementId(), valueEl.getElementId()));
			}
		}

		@Override
		public CollectionElement<MultiEntryValueHandle<K, V>> getAdjacentElement(ElementId elementId, boolean next) {
			if (!(elementId instanceof KeyValueElementId))
				throw new NoSuchElementException();
			KeyValueElementId kvId = (KeyValueElementId) elementId;
			ElementId keyId = kvId.keyId;
			MultiEntryHandle<K, V> entry = getMap().getEntryById(keyId);
			CollectionElement<V> value = entry.getValues().getAdjacentElement(kvId.valueId, next);
			while (value == null) {
				keyId = CollectionElement.getElementId(getMap().keySet().getAdjacentElement(entry.getElementId(), next));
				if (keyId == null)
					break;
				entry = getMap().getEntryById(keyId);
				value = entry.getValues().getTerminalElement(next);
			}
			return value == null ? null : entryFor(getMap().getEntryById(keyId, value.getElementId()));
		}

		@Override
		public MutableCollectionElement<MultiEntryValueHandle<K, V>> mutableElement(ElementId id) {
			if (!(id instanceof KeyValueElementId))
				throw new NoSuchElementException();
			KeyValueElementId kvId = (KeyValueElementId) id;
			return mutableEntryFor(getMap().getEntryById(kvId.keyId, kvId.valueId));
		}

		@Override
		public BetterList<CollectionElement<MultiEntryValueHandle<K, V>>> getElementsBySource(ElementId sourceEl) {
			BetterList<? extends CollectionElement<?>> els;
			if (sourceEl instanceof KeyValueElementId) {
				KeyValueElementId kvId = (KeyValueElementId) sourceEl;
				els = getMap().keySet().getElementsBySource(kvId.keyId);
				if (!els.isEmpty()) {
					ElementId keyId = els.getFirst().getElementId();
					return QommonsUtils.map2(getMap().getEntryById(keyId).getValues().getElementsBySource(kvId.valueId), //
						valueEl -> entryFor(getMap().getEntryById(keyId, valueEl.getElementId())));
				}
			}
			els = getMap().keySet().getElementsBySource(sourceEl);
			if (!els.isEmpty()) {
				return BetterList
					.of(els.stream().flatMap(keyEl -> getMap().getEntryById(keyEl.getElementId()).getValues().elements().stream()//
						.map(valueEl -> entryFor(getMap().getEntryById(keyEl.getElementId(), valueEl.getElementId())))));
			}
			// No choice but to go key-by-key
			try (Transaction t = lock(false, null)) {
				CollectionElement<K> keyEl = getMap().keySet().getTerminalElement(true);
				while (keyEl != null) {
					MultiEntryHandle<K, V> entry = getMap().getEntryById(keyEl.getElementId());
					els = entry.getValues().getElementsBySource(sourceEl);
					if (!els.isEmpty())
						return BetterList
							.of(els.stream().map(valueEl -> entryFor(getMap().getEntryById(entry.getElementId(), valueEl.getElementId()))));

					keyEl = getMap().keySet().getAdjacentElement(keyEl.getElementId(), true);
				}
			}
			return BetterList.empty();
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (!(localElement instanceof KeyValueElementId))
				throw new NoSuchElementException();
			KeyValueElementId kvId = (KeyValueElementId) localElement;

			BetterList<ElementId> els = getMap().keySet().getSourceElements(kvId.keyId, sourceCollection);
			if (!els.isEmpty())
				return els;
			MultiEntryHandle<K, V> entry = getMap().getEntryById(kvId.keyId);
			return entry.getValues().getSourceElements(localElement, sourceCollection);
		}

		@Override
		public String canAdd(MultiEntryValueHandle<K, V> value, ElementId after, ElementId before) {
			if (after != null && !(after instanceof KeyValueElementId))
				throw new NoSuchElementException();
			KeyValueElementId kvAfter = (KeyValueElementId) after;
			if (before != null && !(before instanceof KeyValueElementId))
				throw new NoSuchElementException();
			KeyValueElementId kvBefore = (KeyValueElementId) before;
			if (kvAfter != null && kvBefore != null && kvAfter.compareTo(kvBefore) >= 0)
				throw new IllegalArgumentException("after is after before");

			try (Transaction t = lock(false, null)) {
				if (kvAfter != null) {
					MultiEntryHandle<K, V> afterEntry = getMap().getEntryById(kvAfter.keyId);
					if (keysEqual(afterEntry.getKey(), value.getKey())) {
						ElementId beforeValueId;
						if (kvBefore == null)
							beforeValueId = null;
						else if (keysEqual(getMap().keySet().getElement(kvBefore.keyId).get(), value.getKey()))
							beforeValueId = kvBefore.valueId;
						else
							beforeValueId = null;
						return afterEntry.getValues().canAdd(value.getValue(), kvAfter.valueId, beforeValueId);
					}
				}
				if (kvBefore != null) {
					if (kvAfter != null && kvAfter.keyId.equals(kvBefore.keyId))
						return StdMsg.ILLEGAL_ELEMENT_POSITION;
					MultiEntryHandle<K, V> beforeEntry = getMap().getEntryById(kvBefore.keyId);
					if (keysEqual(beforeEntry.getKey(), value.getKey()))
						return beforeEntry.getValues().canAdd(value.getValue(), null, kvBefore.valueId);
				}
				MultiEntryHandle<K, V> entry = getMap().getEntry(value.getKey());
				if (entry != null) {
					if ((kvAfter != null && entry.getElementId().compareTo(kvAfter.keyId) < 0)//
						|| (kvBefore != null && entry.getElementId().compareTo(kvBefore.keyId) > 0))
						return StdMsg.ILLEGAL_ELEMENT_POSITION;
					else
						return entry.getValues().canAdd(value.getValue(), null, null);
				} else
					return getMap().keySet().canAdd(value.getKey(), kvAfter == null ? null : kvAfter.keyId,
						kvBefore == null ? null : kvBefore.keyId);
			}
		}

		@Override
		public CollectionElement<MultiEntryValueHandle<K, V>> addElement(MultiEntryValueHandle<K, V> value, ElementId after,
			ElementId before, boolean first) throws UnsupportedOperationException, IllegalArgumentException {
			if (after != null && !(after instanceof KeyValueElementId))
				throw new NoSuchElementException();
			KeyValueElementId kvAfter = (KeyValueElementId) after;
			if (before != null && !(before instanceof KeyValueElementId))
				throw new NoSuchElementException();
			KeyValueElementId kvBefore = (KeyValueElementId) before;
			if (kvAfter != null && kvBefore != null && kvAfter.compareTo(kvBefore) >= 0)
				throw new IllegalArgumentException("after is after before");

			try (Transaction t = lock(true, null)) {
				if (kvAfter != null) {
					MultiEntryHandle<K, V> afterEntry = getMap().getEntryById(kvAfter.keyId);
					if (keysEqual(afterEntry.getKey(), value.getKey())) {
						ElementId beforeValueId;
						if (kvBefore == null)
							beforeValueId = null;
						else if (keysEqual(getMap().keySet().getElement(kvBefore.keyId).get(), value.getKey()))
							beforeValueId = kvBefore.valueId;
						else
							beforeValueId = null;
						CollectionElement<V> valueEl = afterEntry.getValues().addElement(value.getValue(), kvAfter.valueId, beforeValueId,
							first);
						return valueEl == null ? null : entryFor(getMap().getEntryById(afterEntry.getElementId(), valueEl.getElementId()));
					}
				}
				if (kvBefore != null) {
					if (kvAfter != null && kvAfter.keyId.equals(kvBefore.keyId))
						throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
					MultiEntryHandle<K, V> beforeEntry = getMap().getEntryById(kvBefore.keyId);
					if (keysEqual(beforeEntry.getKey(), value.getKey())) {
						CollectionElement<V> valueEl = beforeEntry.getValues().addElement(value.getValue(), null, kvBefore.valueId, first);
						return valueEl == null ? null : entryFor(getMap().getEntryById(beforeEntry.getElementId(), valueEl.getElementId()));
					}
				}
				MultiEntryHandle<K, V> entry = getMap().getEntry(value.getKey());
				if (entry != null) {
					if ((kvAfter != null && entry.getElementId().compareTo(kvAfter.keyId) < 0)//
						|| (kvBefore != null && entry.getElementId().compareTo(kvBefore.keyId) > 0))
						throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
					else {
						CollectionElement<V> valueEl = entry.getValues().addElement(value.getValue(), null, null, first);
						return valueEl == null ? null : entryFor(getMap().getEntryById(entry.getElementId(), valueEl.getElementId()));
					}
				} else {
					return entryFor(getMap().putEntry(value.getKey(), value.getValue(), kvAfter == null ? null : kvAfter.keyId, //
						kvBefore == null ? null : kvBefore.keyId, first));
				}
			}
		}

		@Override
		public void clear() {
			getMap().clear();
		}

		static class KeyValueElementId implements ElementId {
			final ElementId keyId;
			final ElementId valueId;

			KeyValueElementId(ElementId keyId, ElementId valueId) {
				this.keyId = keyId;
				this.valueId = valueId;
			}

			@Override
			public int compareTo(ElementId o) {
				if (!(o instanceof KeyValueElementId))
					throw new IllegalArgumentException("ElementIds can only be compared within the same collection");
				KeyValueElementId other = (KeyValueElementId) o;
				int comp = keyId.compareTo(other.keyId);
				if (comp == 0)
					comp = valueId.compareTo(other.valueId);
				return comp;
			}

			@Override
			public boolean isPresent() {
				return keyId.isPresent() && valueId.isPresent();
			}

			@Override
			public boolean isDerivedFrom(ElementId other) {
				return keyId.isDerivedFrom(other) || valueId.isDerivedFrom(other);
			}
		}

		class ValueHandleElement implements CollectionElement<MultiEntryValueHandle<K, V>> {
			final MultiEntryValueHandle<K, V> theEntry;
			KeyValueElementId theId;

			ValueHandleElement(MultiEntryValueHandle<K, V> entry) {
				theEntry = entry;
			}

			@Override
			public ElementId getElementId() {
				if (theId == null)
					theId = new KeyValueElementId(theEntry.getKeyId(), theEntry.getElementId());
				return theId;
			}

			@Override
			public MultiEntryValueHandle<K, V> get() {
				return theEntry;
			}
		}

		class MutableValueHandleElement extends ValueHandleElement implements MutableCollectionElement<MultiEntryValueHandle<K, V>> {
			final MutableCollectionElement<V> theValueEl;

			MutableValueHandleElement(MultiEntryValueHandle<K, V> entry) {
				super(entry);
				theValueEl = getMap().getEntryById(entry.getKeyId()).getValues().mutableElement(entry.getElementId());
			}

			@Override
			public BetterCollection<MultiEntryValueHandle<K, V>> getCollection() {
				return BetterMapSingleEntryCollection.this;
			}

			@Override
			public String isEnabled() {
				return theValueEl.isEnabled();
			}

			@Override
			public String isAcceptable(MultiEntryValueHandle<K, V> value) {
				if (!keysEqual(theEntry.getKey(), value.getKey()))
					return StdMsg.ILLEGAL_ELEMENT;
				return theValueEl.isAcceptable(value.getValue());
			}

			@Override
			public void set(MultiEntryValueHandle<K, V> value) throws UnsupportedOperationException, IllegalArgumentException {
				if (!keysEqual(theEntry.getKey(), value.getKey()))
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				theValueEl.set(value.getValue());
			}

			@Override
			public String canRemove() {
				return theValueEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theValueEl.remove();
			}
		}
	}

	/**
	 * A default implementation of {@link BetterMultiMap#getEntryById(ElementId, ElementId)}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class DefaultValueHandle<K, V> implements MultiEntryValueHandle<K, V> {
		private final MultiEntryHandle<K, V> theMultiEntry;
		private final ElementId theValueId;

		public DefaultValueHandle(MultiEntryHandle<K, V> multiEntry, ElementId valueId) {
			theMultiEntry = multiEntry;
			theValueId = valueId;
		}

		@Override
		public K getKey() {
			return theMultiEntry.getKey();
		}

		@Override
		public ElementId getElementId() {
			return theValueId;
		}

		@Override
		public V get() {
			return theMultiEntry.getValues().getElement(theValueId).get();
		}

		@Override
		public ElementId getKeyId() {
			return theMultiEntry.getElementId();
		}
	}

	/**
	 * Implements {@link BetterMultiMap#singleMap(boolean)}
	 *
	 * @param <K> The key-type of the map
	 * @param <V> The value-type of the map
	 */
	class SingleMap<K, V> implements BetterMap<K, V> {
		private final BetterMultiMap<K, V> theSource;
		private final boolean isFirstValue;
		private Object theIdentity;

		public SingleMap(BetterMultiMap<K, V> outer, boolean firstValue) {
			theSource = outer;
			isFirstValue = firstValue;
		}

		protected BetterMultiMap<K, V> getSource() {
			return theSource;
		}

		/** @return Whether this map is using the first or last value for each key in the multi map */
		public boolean isFirstValue() {
			return isFirstValue;
		}

		@Override
		public Object getIdentity() {
			if (theIdentity == null)
				theIdentity = Identifiable.wrap(theSource.getIdentity(), "single");
			return theIdentity;
		}

		@Override
		public boolean isLockSupported() {
			return theSource.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theSource.lock(write, structural, cause);
		}

		@Override
		public BetterSet<K> keySet() {
			return theSource.keySet();
		}

		@Override
		public V get(Object key) {
			BetterCollection<V> values = theSource.get(key);
			return isFirstValue ? values.peekFirst() : values.peekLast();
		}

		@Override
		public MapEntryHandle<K, V> getEntry(K key) {
			MultiEntryHandle<K, V> outerHandle = theSource.getEntry(key);
			return outerHandle == null ? null : entryFor(outerHandle);
		}

		@Override
		public MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
			try (Transaction t = lock(true, null)) {
				boolean[] added = new boolean[1];
				MultiEntryHandle<K, V> entry = theSource.getOrPutEntry(key, k -> BetterList.of(value), after, before, first,
					() -> added[0] = true);
				if (entry == null)
					return null;
				if (!added[0])
					entry.getValues().mutableElement(entry.getValues().getTerminalElement(isFirstValue).getElementId()).set(value);
				return entryFor(entry);
			}
		}

		@Override
		public MapEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId afterKey, ElementId beforeKey,
			boolean first, Runnable added) {
			return entryFor(theSource.getOrPutEntry(key, k -> BetterList.of(value.apply(k)), afterKey, beforeKey, first, added));
		}

		/**
		 * @param outerHandle The multi-entry to wrap
		 * @return The map entry to expose as an entry of this map, backed by the multi-entry from the source
		 */
		protected MapEntryHandle<K, V> entryFor(MultiEntryHandle<K, V> outerHandle) {
			return outerHandle == null ? null : new MapEntryHandle<K, V>() {
				@Override
				public ElementId getElementId() {
					return outerHandle.getElementId();
				}

				@Override
				public V get() {
					return outerHandle.getValues().peekFirst();
				}

				@Override
				public K getKey() {
					return outerHandle.getKey();
				}
			};
		}

		@Override
		public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
			return entryFor(theSource.getEntryById(entryId));
		}

		@Override
		public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
			return mutableEntryFor(theSource.getEntryById(entryId));
		}

		private MutableMapEntryHandle<K, V> mutableEntryFor(MultiEntryHandle<K, V> outerHandle) {
			return outerHandle == null ? null : new MutableMapEntryHandle<K, V>() {
				@Override
				public K getKey() {
					return outerHandle.getKey();
				}

				@Override
				public BetterCollection<V> getCollection() {
					return outerHandle.getValues();
				}

				@Override
				public ElementId getElementId() {
					return outerHandle.getElementId();
				}

				@Override
				public V get() {
					return outerHandle.getValues().peekFirst();
				}

				@Override
				public String isEnabled() {
					return outerHandle.getValues().mutableElement(outerHandle.getValues().getTerminalElement(true).getElementId())
						.isEnabled();
				}

				@Override
				public String isAcceptable(V value) {
					return outerHandle.getValues().mutableElement(outerHandle.getValues().getTerminalElement(true).getElementId())
						.isAcceptable(value);
				}

				@Override
				public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
					outerHandle.getValues().mutableElement(outerHandle.getValues().getTerminalElement(true).getElementId()).set(value);
				}

				@Override
				public String canRemove() {
					for (CollectionElement<V> valueEl : outerHandle.getValues().elements()) {
						String msg = outerHandle.getValues().mutableElement(valueEl.getElementId()).canRemove();
						if (msg != null)
							return msg;
					}
					return null;
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					for (CollectionElement<V> valueEl : outerHandle.getValues().elements())
						outerHandle.getValues().mutableElement(valueEl.getElementId()).remove();
				}
			};
		}

		@Override
		public V put(K key, V value) {
			try (Transaction t = theSource.lock(true, null)) {
				BetterCollection<V> values = theSource.get(key);
				CollectionElement<V> terminal = values.getTerminalElement(isFirstValue);
				if (terminal == null) {
					values.add(value);
					return null;
				} else {
					V old = terminal.get();
					values.mutableElement(terminal.getElementId()).set(value);
					return old;
				}
			}
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}

	/**
	 * Implements {@link BetterMultiMap#reverse()}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
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
		public int valueSize() {
			return theSource.valueSize();
		}

		@Override
		public MultiEntryHandle<K, V> getEntryById(ElementId keyId) {
			return MultiEntryHandle.reverse(theSource.getEntryById(keyId.reverse()));
		}

		@Override
		public MultiEntryValueHandle<K, V> getEntryById(ElementId keyId, ElementId valueId) {
			return MultiEntryValueHandle.reverse(theSource.getEntryById(keyId.reverse(), valueId.reverse()));
		}

		@Override
		public MultiEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
			ElementId beforeKey, boolean first, Runnable added) {
			return MultiEntryHandle
				.reverse(theSource.getOrPutEntry(key, value, ElementId.reverse(beforeKey), ElementId.reverse(afterKey), !first, added));
		}

		@Override
		public boolean clear() {
			return theSource.clear();
		}

		@Override
		public BetterMultiMap<K, V> reverse() {
			return theSource;
		}
	}
}
