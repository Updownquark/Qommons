package org.qommons.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A {@link BetterMultiMap} that allows control over attempted modifications and listening for such modifications
 * 
 * @param <K> The type of keys in the multi-map
 * @param <V> The type of values in the multi-map
 * @param <M> The sub type of this multi-map
 */
public class ModControlledMultiMap<K, V, M extends BetterMultiMap<K, V>> implements BetterMultiMap<K, V> {
	/**
	 * Interface determining which multi-map modifications are allowed
	 * 
	 * @param <K> The type of keys in the controlled multi-map
	 * @param <V> The type of values in the controlled multi-map
	 */
	public interface MultiMapModificationControl<K, V> {
		/**
		 * Implementation of {@link BetterMultiMap#getOrPutEntry(Object, Function, ElementId, ElementId, boolean, Runnable, Runnable)}
		 * 
		 * @param key The key to add
		 * @param values The values to add
		 * @param after The entry element to add the entry after
		 * @param before The entry element to add the entry before
		 * @return Whether the entry can be added
		 */
		String canAddEntry(K key, Iterable<? extends V> values, ElementId after, ElementId before);

		/**
		 * Implementation of {@link BetterCollection#canAdd(Object, ElementId, ElementId)} for the values in a multi-entry
		 * 
		 * @param entry The entry to add the value to
		 * @param value The value to add
		 * @param after The value element to add the value after
		 * @param before The value element to add the value before
		 * @return Whether the value can be added
		 */
		String canAddValue(MultiEntryHandle<K, V> entry, V value, ElementId after, ElementId before);

		/**
		 * Implementation of {@link MutableCollectionElement#canRemove()} for a single value entry
		 * 
		 * @param entry The entry to remove
		 * @return Whether the entry can be removed
		 */
		String canRemove(MultiEntryValueHandle<K, V> entry);

		/**
		 * Implementation of {@link MutableCollectionElement#isEnabled()} for the key set
		 * 
		 * @param entry The entry to change the key for
		 * @return Whether the entry's key is modifiable
		 */
		String isKeyModifiable(MultiEntryHandle<K, V> entry);

		/**
		 * Implementation of {@link MutableCollectionElement#isAcceptable(Object)} in the key set
		 * 
		 * @param entry The entry to change the key for
		 * @param newKey The new key for the entry
		 * @return Whether the key can be replaced for the entry
		 */
		String isKeyAcceptable(MultiEntryHandle<K, V> entry, K newKey);

		/**
		 * Implementation of {@link MutableCollectionElement#isEnabled()} for the values in a multi-entry
		 * 
		 * @param entry The entry to change the value for
		 * @return Whether the value can be changed for the entry
		 */
		String isModifiable(MultiEntryValueHandle<K, V> entry);

		/**
		 * Implementation of {@link MutableCollectionElement#isAcceptable(Object)} for the values in a multi-entry
		 * 
		 * @param entry The entry to change the value for
		 * @param newValue The new value for the entry
		 * @return Whether the value can be replaced for the entry
		 */
		String isAcceptable(MultiEntryValueHandle<K, V> entry, V newValue);

		/**
		 * Implementation of {@link BetterCollection#canMove(ElementId, ElementId, ElementId)} for the entry set
		 * 
		 * @param entry The entry to move
		 * @param after The entry to move the target entry after
		 * @param before The entry to move the target entry before
		 * @return Whether the entry can be moved
		 */
		String canMoveEntry(MultiEntryHandle<K, V> entry, ElementId after, ElementId before);

		/**
		 * Implementation of {@link BetterCollection#canMove(ElementId, ElementId, ElementId)} for the values in a multi-entry
		 * 
		 * @param keyEntry The entry to move the value in
		 * @param valueEntry The value entry to move
		 * @param after The value entry to move the target entry after
		 * @param before The value entry to move the target entry before
		 * @return Whether the entry can be moved
		 */
		String canMoveValue(MultiEntryHandle<K, V> keyEntry, MultiEntryValueHandle<K, V> valueEntry, ElementId after, ElementId before);
	}

	/**
	 * Interface reporting any modifications to a multi-map
	 * 
	 * @param <K> The type of keys in the controlled multi-map
	 * @param <V> the type of values in the controlled multi-map
	 */
	public interface MultiMapModificationListener<K, V> {
		/** @param entry The entry that was added */
		void entryAdded(MultiEntryHandle<K, V> entry);

		/** @param entry The value entry that was added */
		void valueAdded(MultiEntryValueHandle<K, V> entry);

		/**
		 * Called before an entry is removed. This method may do the removal or it may do nothing.
		 * 
		 * @param entry The entry to be removed
		 */
		void entryPreRemove(MultiEntryValueHandle<K, V> entry);

		/** @param entry The entry that was removed */
		void entryRemoved(MultiEntryValueHandle<K, V> entry);

		/**
		 * @param entry The entry whose key was changed
		 * @param previousKey The previous key in the entry
		 */
		void entryKeyChanged(MultiEntryHandle<K, V> entry, K previousKey);

		/**
		 * @param entry The entry whose value was changed
		 * @param previousValue The previous value in the entry
		 */
		void entryValueChanged(MultiEntryValueHandle<K, V> entry, V previousValue);

		/**
		 * @param entry The entry that will be moved
		 * @param after The entry that the target entry will be moved after
		 * @param before The entry that the target entry will be moved before
		 * @return Data to be passed to {@link #entryMoved(MultiEntryHandle, Object)} after the move operation
		 */
		Object entryPreMoved(MultiEntryHandle<K, V> entry, ElementId after, ElementId before);

		/**
		 * @param entry The entry that was moved
		 * @param moveData The data returned by {@link #entryPreMoved(MultiEntryHandle, ElementId, ElementId)} before the move
		 */
		void entryMoved(MultiEntryHandle<K, V> entry, Object moveData);

		/**
		 * @param keyEntry The multi-entry that the value will be moved in
		 * @param valueEntry The value entry to move
		 * @param after The value entry that the target entry will be moved after
		 * @param before The value entry that the target entry will be moved before
		 * @return Data to be passed to {@link #valueMoved(MultiEntryHandle, MultiEntryValueHandle, Object)} after the move operation
		 */
		Object valuePreMove(MultiEntryHandle<K, V> keyEntry, MultiEntryValueHandle<K, V> valueEntry, ElementId after, ElementId before);

		/**
		 * @param keyEntry The multi-entry that the value was moved in
		 * @param valueEntry The entry that was moved
		 * @param moveData The data returned by {@link #entryPreMoved(MultiEntryHandle, ElementId, ElementId)} before the move
		 */
		void valueMoved(MultiEntryHandle<K, V> keyEntry, MultiEntryValueHandle<K, V> valueEntry, Object moveData);
	}

	/**
	 * Creates a modification-controlled multi-map
	 * 
	 * @param <K> The type of keys in the multi-map
	 * @param <V> The type of values in the multi-map
	 * @param <M> The sub type of the multi-map
	 * @param map The multi-map to control
	 * @param control The optional modification controller
	 * @param listener The optional modification listener
	 * @return The modification-controlled multi-map
	 */
	public static <K, V, M extends BetterMultiMap<K, V>> M controlMultiMap(M map, MultiMapModificationControl<K, V> control,
		MultiMapModificationListener<K, V> listener) {
		if (map instanceof BetterSortedMultiMap)
			return (M) new ModControlledSortedMultiMap<>((BetterSortedMultiMap<K, V>) map, control, listener);
		else
			return (M) new ModControlledMultiMap<>(map, control, listener);
	}

	private final M theBacking;
	private final MultiMapModificationControl<K, V> theControl;
	private final MultiMapModificationListener<K, V> theListener;
	private BetterSet<K> theKeySet;

	/**
	 * @param backing The multi-map to control
	 * @param control The optional modification controller
	 * @param listener The optional modification listener
	 */
	public ModControlledMultiMap(M backing, MultiMapModificationControl<K, V> control, MultiMapModificationListener<K, V> listener) {
		theBacking = backing;
		theControl = control;
		theListener = listener;
	}

	/** @return The modifiable content of this multi-map */
	protected M getBacking() {
		return theBacking;
	}

	/** @return The modification controller for this multi-ap. May be null if this multi-map is not actually modification-restricted. */
	public MultiMapModificationControl<K, V> getControl() {
		return theControl;
	}

	/** @return The modification listener for this multi-map. May be null */
	protected MultiMapModificationListener<K, V> getListener() {
		return theListener;
	}

	@Override
	public Object getIdentity() {
		return theBacking.getIdentity();
	}

	@Override
	public BetterMultiMap<K, V> alias(String alias) {
		theBacking.alias(alias);
		return this;
	}

	@Override
	public Set<String> getAliases() {
		return theBacking.getAliases();
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return theBacking.getCurrentCauses();
	}

	@Override
	public Transaction lock(boolean tryOnly) {
		return theBacking.lock(tryOnly);
	}

	@Override
	public Transaction lockWrite(boolean tryOnly, Object cause) {
		return theBacking.lockWrite(tryOnly, cause);
	}

	@Override
	public CoreId getCoreId() {
		return theBacking.getCoreId();
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theBacking.getThreadConstraint();
	}

	@Override
	public long getStamp() {
		return theBacking.getStamp();
	}

	@Override
	public BetterSet<K> keySet() {
		if (theKeySet == null)
			theKeySet = createKeySet();
		return theKeySet;
	}

	/** @return The key set for this modification-controlled multi-map */
	protected BetterSet<K> createKeySet() {
		if (theControl == null)
			return theBacking.keySet();
		ModControlledCollection.CollectionModificationControl<K> keyControl = createKeyControl();
		ModControlledCollection.CollectionModificationListener<K> keyListener = createKeyModListener();
		if (keyControl == null && keyListener == null)
			return theBacking.keySet();
		return ModControlledCollection.controlCollection(theBacking.keySet(), keyControl, keyListener);
	}

	/** @return The modification controller for this multi-map's key set */
	protected ModControlledCollection.CollectionModificationControl<K> createKeyControl() {
		return theControl == null ? null : new KeySetControl<>(theBacking, theControl);
	}

	/** @return The modification listener for this multi-map's key set */
	protected ModControlledCollection.CollectionModificationListener<K> createKeyModListener() {
		return theListener == null ? null : new KeySetModListener<>(theBacking, theListener);
	}

	@Override
	public int valueSize() {
		return theBacking.valueSize();
	}

	@Override
	public boolean clear() {
		return theBacking.clear();
	}

	@Override
	public MultiEntryHandle<K, V> getEntryById(ElementId keyId) {
		return wrapEntry(theBacking.getEntryById(keyId));
	}

	@Override
	public BetterCollection<V> get(K key) {
		return wrapValues(key, theBacking.get(key));
	}

	@Override
	public MultiEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
		ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd) {
		if (theControl == null && theListener == null) {
			MultiEntryHandle<K, V> entry = theBacking.getOrPutEntry(key, value, afterKey, beforeKey, first, preAdd, postAdd);
			return entry == null ? null : wrapEntry(entry);
		}
		try (Transaction t = lockWrite(false, null)) {
			MultiEntryHandle<K, V> found = getEntry(key);
			if (found != null)
				return found;
			Iterable<? extends V> values = value.apply(key);
			String msg = theControl == null ? null : theControl.canAddEntry(key, values, afterKey, beforeKey);
			if (msg != null)
				throw new UnsupportedOperationException(msg);
			if (preAdd != null)
				preAdd.run();

			MultiEntryHandle<K, V> added = null;
			for (V v : values) {
				MultiEntryValueHandle<K, V> valueEntry;
				if (added == null) {
					valueEntry = putEntry(key, v, afterKey, beforeKey, first);
					added = theBacking.getEntryById(valueEntry.getKeyId());
					if (theListener != null)
						theListener.entryAdded(added);
				} else {
					CollectionElement<V> addedValue = added.getValues().addElement(v, first);
					valueEntry = valueHandle(addedValue, added.getElementId(), theBacking);
				}
				if (theListener != null)
					theListener.valueAdded(valueEntry);
			}
			return added;
		}
	}

	@Override
	public int hashCode() {
		return theBacking.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return theBacking.equals(obj);
	}

	@Override
	public String toString() {
		return theBacking.toString();
	}

	/**
	 * @param backingEntry The modifiable multi-map entry to wrap
	 * @return The modification-controlled modifiable multi-map entry
	 */
	protected MultiEntryHandle<K, V> wrapEntry(MultiEntryHandle<K, V> backingEntry) {
		if (backingEntry == null)
			return null;
		else if (backingEntry instanceof OrderedMultiEntry)
			return new OrderedEntryWrapper((OrderedMultiEntry<K, V>) backingEntry);
		else
			return new EntryWrapper(backingEntry);
	}

	/**
	 * @param key The key for the values to wrap
	 * @param values The modifiable collection of values for the key
	 * @return The modification-controlled value collection for the key
	 */
	protected BetterCollection<V> wrapValues(K key, BetterCollection<V> values) {
		if (theControl == null && theListener == null)
			return values;

		ModControlledCollection.CollectionModificationControl<V> valuesControl = createValueControl(key);
		ModControlledCollection.CollectionModificationListener<V> valuesListener = createValueListener(key);
		if (valuesControl == null && valuesListener == null)
			return values;
		return ModControlledCollection.controlCollection(values, valuesControl, valuesListener);
	}

	/**
	 * @param key The key to control the values for
	 * @return The values controller for values in this map for the given key
	 */
	protected ModControlledCollection.CollectionModificationControl<V> createValueControl(K key) {
		return theControl == null ? null : new ValuesControl(key);
	}

	/**
	 * @param key The key to listen to the values for
	 * @return The values listener for values in this map for the given key
	 */
	protected ModControlledCollection.CollectionModificationListener<V> createValueListener(K key) {
		return theListener == null ? null : new ValuesListener(key);
	}

	/** Default implementation of {@link ModControlledMultiMap#wrapEntry(MultiEntryHandle)} */
	public class EntryWrapper implements MultiEntryHandle<K, V> {
		private final MultiEntryHandle<K, V> theBackingEntry;
		private final BetterCollection<V> theValues;

		/** @param backingEntry The modifiable multi-map entry to wrap */
		protected EntryWrapper(MultiEntryHandle<K, V> backingEntry) {
			theBackingEntry = backingEntry;
			theValues = wrapValues(backingEntry.getKey(), backingEntry.getValues());
		}

		/** @return The wrapped modifiable multi-map entry */
		protected MultiEntryHandle<K, V> getBackingEntry() {
			return theBackingEntry;
		}

		@Override
		public K getKey() {
			return theBackingEntry.getKey();
		}

		@Override
		public ElementId getElementId() {
			return theBackingEntry.getElementId();
		}

		@Override
		public BetterCollection<V> getValues() {
			return theValues;
		}

		@Override
		public MultiEntryHandle<K, V> getAdjacent(boolean next) {
			return wrapEntry(theBackingEntry.getAdjacent(next));
		}
	}

	/** Default ordered implementation of {@link ModControlledMultiMap#wrapEntry(MultiEntryHandle)} */
	public class OrderedEntryWrapper extends EntryWrapper implements OrderedMultiEntry<K, V> {
		/** @param backingEntry The modifiable multi-map entry to wrap */
		protected OrderedEntryWrapper(OrderedMultiEntry<K, V> backingEntry) {
			super(backingEntry);
		}

		@Override
		protected OrderedMultiEntry<K, V> getBackingEntry() {
			return (OrderedMultiEntry<K, V>) super.getBackingEntry();
		}

		@Override
		public int getElementsBefore() {
			return getBackingEntry().getElementsBefore();
		}

		@Override
		public int getElementsAfter() {
			return getBackingEntry().getElementsAfter();
		}

		@Override
		public OrderedMultiEntry<K, V> getAdjacent(boolean next) {
			return (OrderedMultiEntry<K, V>) super.getAdjacent(next);
		}
	}

	/**
	 * Default implementation for {@link ModControlledMultiMap#createKeyControl()}
	 * 
	 * @param <K> The type of keys in the multi-map
	 * @param <V> The type of values in the multi-map
	 */
	public static class KeySetControl<K, V> implements ModControlledCollection.CollectionModificationControl<K> {
		private final BetterMultiMap<K, V> theBacking;
		private final MultiMapModificationControl<K, V> theMapControl;

		/**
		 * @param backing The wrapped multi-map
		 * @param mapControl The multi-map modification controller
		 */
		public KeySetControl(BetterMultiMap<K, V> backing, MultiMapModificationControl<K, V> mapControl) {
			theBacking = backing;
			theMapControl = mapControl;
		}

		@Override
		public String canAdd(K value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public String canRemove(CollectionElement<K> element) {
			MultiEntryHandle<K, V> entry = theBacking.getEntryById(element.getElementId());
			String msg = null;
			for (CollectionElement<V> value = entry.getValues().getTerminalElement(true); //
				value != null && msg == null; //
				value = value.getAdjacent(true)) {
				MultiEntryValueHandle<K, V> valueEntry;
				if (value instanceof MultiEntryValueHandle)
					valueEntry = (MultiEntryValueHandle<K, V>) value;
				else
					valueEntry = theBacking.getEntryById(entry.getElementId(), value.getElementId());
				msg = theMapControl.canRemove(valueEntry);
			}
			return msg;
		}

		@Override
		public String isModifiable(CollectionElement<K> element) {
			return theMapControl.isKeyModifiable(theBacking.getEntryById(element.getElementId()));
		}

		@Override
		public String isAcceptable(CollectionElement<K> element, K newValue) {
			return theMapControl.isKeyAcceptable(theBacking.getEntryById(element.getElementId()), newValue);
		}

		@Override
		public String canMove(CollectionElement<K> element, ElementId after, ElementId before) {
			return theMapControl.canMoveEntry(theBacking.getEntryById(element.getElementId()), after, before);
		}
	}

	/**
	 * Default implementation for {@link ModControlledMultiMap#createKeyModListener()}
	 * 
	 * @param <K> The type of keys in the multi-map
	 * @param <V> The type of values in the multi-map
	 */
	public static class KeySetModListener<K, V> implements ModControlledCollection.CollectionModificationListener<K> {
		private final BetterMultiMap<K, V> theBacking;
		private final MultiMapModificationListener<K, V> theMapListener;

		/**
		 * @param backing The wrapped multi-map
		 * @param mapListener The multi-map modification listener
		 */
		public KeySetModListener(BetterMultiMap<K, V> backing, MultiMapModificationListener<K, V> mapListener) {
			theBacking = backing;
			theMapListener = mapListener;
		}

		@Override
		public void elementAdded(CollectionElement<K> element) {
			theMapListener.entryAdded(theBacking.getEntryById(element.getElementId()));
		}

		@Override
		public void elementPreRemove(MutableCollectionElement<K> element) {
			MultiEntryHandle<K, V> entry = theBacking.getEntryById(element.getElementId());
			for (CollectionElement<V> value = entry.getValues().getTerminalElement(true); value != null; value = value.getAdjacent(true)) {
				MultiEntryValueHandle<K, V> valueEntry = valueHandle(value, entry.getElementId(), theBacking);
				theMapListener.entryPreRemove(valueEntry);
				entry.getValues().mutableElement(value.getElementId()).remove();
				theMapListener.entryRemoved(valueEntry);
			}
		}

		@Override
		public void elementRemoved(CollectionElement<K> element) { // Handled in pre-remove
		}

		@Override
		public void elementReplaced(CollectionElement<K> element, K previousValue) {
			theMapListener.entryKeyChanged(theBacking.getEntryById(element.getElementId()), previousValue);
		}

		@Override
		public Object elementPreMove(CollectionElement<K> element, ElementId after, ElementId before) {
			return theMapListener.entryPreMoved(theBacking.getEntryById(element.getElementId()), after, before);
		}

		@Override
		public void elementMoved(CollectionElement<K> element, Object moveData) {
			theMapListener.entryMoved(theBacking.getEntryById(element.getElementId()), moveData);
		}
	}

	private static <K, V> MultiEntryValueHandle<K, V> valueHandle(CollectionElement<V> valueElement, ElementId keyId,
		BetterMultiMap<K, V> map) {
		if (valueElement instanceof MultiEntryValueHandle)
			return (MultiEntryValueHandle<K, V>) valueElement;
		else
			return map.getEntryById(keyId, valueElement.getElementId());
	}

	/** Default implementation for {@link ModControlledMultiMap#createValueControl(Object)} */
	public class ValuesControl implements ModControlledCollection.CollectionModificationControl<V> {
		private final K theKey;
		private MultiEntryHandle<K, V> theCachedEntry;

		/** @param key The key to control the values for */
		public ValuesControl(K key) {
			theKey = key;
		}

		/** @return The current entry in the map for this controller's key */
		protected MultiEntryHandle<K, V> getCurrentEntry() {
			if (theCachedEntry == null || !theCachedEntry.getElementId().isPresent())
				theCachedEntry = getBacking().getEntry(theKey);
			return theCachedEntry;
		}

		@Override
		public String canAdd(V value, ElementId after, ElementId before) {
			MultiEntryHandle<K, V> entry = getCurrentEntry();
			if (entry == null)
				return getControl().canAddEntry(theKey, Collections.singletonList(value), null, null);
			else
				return getControl().canAddValue(entry, value, after, before);
		}

		@Override
		public String canRemove(CollectionElement<V> element) {
			return getControl().canRemove(valueHandle(element, getCurrentEntry().getElementId(), getBacking()));
		}

		@Override
		public String isModifiable(CollectionElement<V> element) {
			return getControl().isModifiable(valueHandle(element, getCurrentEntry().getElementId(), getBacking()));
		}

		@Override
		public String isAcceptable(CollectionElement<V> element, V newValue) {
			return getControl().isAcceptable(valueHandle(element, getCurrentEntry().getElementId(), getBacking()), newValue);
		}

		@Override
		public String canMove(CollectionElement<V> element, ElementId after, ElementId before) {
			MultiEntryHandle<K, V> entry = getCurrentEntry();
			return getControl().canMoveValue(entry, valueHandle(element, entry.getElementId(), getBacking()), after, before);
		}
	}

	/** Default implementation for {@link ModControlledMultiMap#createValueListener(Object)} */
	public class ValuesListener implements ModControlledCollection.CollectionModificationListener<V> {
		private final K theKey;
		private MultiEntryHandle<K, V> theCachedEntry;

		/** @param key The key to listen to the values for */
		public ValuesListener(K key) {
			theKey = key;
		}

		/** @return The current entry in the map for this listener's key */
		protected MultiEntryHandle<K, V> getCurrentEntry() {
			if (theCachedEntry == null || !theCachedEntry.getElementId().isPresent())
				theCachedEntry = getBacking().getEntry(theKey);
			return theCachedEntry;
		}

		@Override
		public void elementAdded(CollectionElement<V> element) {
			MultiEntryHandle<K, V> entry = getCurrentEntry();
			if (element.getAdjacent(false) == null && element.getAdjacent(true) == null)
				getListener().entryAdded(entry);
			getListener().valueAdded(valueHandle(element, entry.getElementId(), getBacking()));
		}

		@Override
		public void elementPreRemove(MutableCollectionElement<V> element) {
			// The handle might not be available after removal
			MultiEntryValueHandle<K, V> handle = valueHandle(element, getCurrentEntry().getElementId(), getBacking());
			getListener().entryPreRemove(handle);
			element.remove();
			getListener().entryRemoved(handle);
		}

		@Override
		public void elementRemoved(CollectionElement<V> element) { // Handled in pre-remove
		}

		@Override
		public void elementReplaced(CollectionElement<V> element, V previousValue) {
			getListener().entryValueChanged(valueHandle(element, getCurrentEntry().getElementId(), getBacking()), previousValue);
		}

		@Override
		public Object elementPreMove(CollectionElement<V> element, ElementId after, ElementId before) {
			MultiEntryHandle<K, V> entry = getCurrentEntry();
			return getListener().valuePreMove(entry, valueHandle(element, entry.getElementId(), getBacking()), after, before);
		}

		@Override
		public void elementMoved(CollectionElement<V> element, Object moveData) {
			MultiEntryHandle<K, V> entry = getCurrentEntry();
			getListener().valueMoved(entry, valueHandle(element, entry.getElementId(), getBacking()), moveData);
		}
	}

	/**
	 * SortedMultiMap implementation for {@link ModControlledMultiMap}
	 * 
	 * @param <K> The type of keys in the multi-map
	 * @param <V> The type of values in the multi-map
	 * @param <M> The sub type of this sorted multi-map
	 */
	public static class ModControlledSortedMultiMap<K, V, M extends BetterSortedMultiMap<K, V>> extends ModControlledMultiMap<K, V, M>
		implements BetterSortedMultiMap<K, V> {
		/**
		 * @param backing The multi-map to control
		 * @param control The optional modification controller
		 * @param listener The optional modification listener
		 */
		public ModControlledSortedMultiMap(M backing, MultiMapModificationControl<K, V> control,
			MultiMapModificationListener<K, V> listener) {
			super(backing, control, listener);
		}

		@Override
		public BetterSortedSet<K> keySet() {
			return (BetterSortedSet<K>) super.keySet();
		}

		@Override
		protected BetterSortedSet<K> createKeySet() {
			return (BetterSortedSet<K>) super.createKeySet();
		}

		@Override
		public OrderedMultiEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
			ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd) {
			return (OrderedMultiEntry<K, V>) super.getOrPutEntry(key, value, afterKey, beforeKey, first, preAdd, postAdd);
		}

		@Override
		public OrderedMultiEntry<K, V> getEntryById(ElementId keyId) {
			return (OrderedMultiEntry<K, V>) super.getEntryById(keyId);
		}

		@Override
		protected OrderedMultiEntry<K, V> wrapEntry(MultiEntryHandle<K, V> backingEntry) {
			return backingEntry == null ? null : new OrderedEntryWrapper((OrderedMultiEntry<K, V>) backingEntry);
		}
	}
}
