package org.qommons.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class ModControlledMultiMap<K, V, M extends BetterMultiMap<K, V>> implements BetterMultiMap<K, V> {
	public interface MultiMapModificationControl<K, V> {
		String canAddEntry(K key, Iterable<? extends V> values, ElementId after, ElementId before);

		String canAddValue(MultiEntryHandle<K, V> entry, V value, ElementId after, ElementId before);

		String canRemove(MultiEntryValueHandle<K, V> entry);

		String isKeyModifiable(MultiEntryHandle<K, V> entry);

		String isKeyAcceptable(MultiEntryHandle<K, V> entry, K newKey);

		String isModifiable(MultiEntryValueHandle<K, V> entry);

		String isAcceptable(MultiEntryValueHandle<K, V> entry, V newValue);

		String canMoveEntry(MultiEntryHandle<K, V> entry, ElementId after, ElementId before);

		String canMoveValue(MultiEntryHandle<K, V> keyEntry, MultiEntryValueHandle<K, V> valueEntry, ElementId after, ElementId before);
	}

	public interface MultiMapModificationListener<K, V> {
		void entryAdded(MultiEntryHandle<K, V> entry);

		void valueAdded(MultiEntryValueHandle<K, V> entry);

		void entryPreRemove(MultiEntryValueHandle<K, V> entry);

		void entryRemoved(MultiEntryValueHandle<K, V> entry);

		void entryKeyChanged(MultiEntryHandle<K, V> entry, K previousKey);

		void entryValueChanged(MultiEntryValueHandle<K, V> entry, V previousValue);

		Object entryPreMoved(MultiEntryHandle<K, V> entry, ElementId after, ElementId before);

		void entryMoved(MultiEntryHandle<K, V> entry, Object moveData);

		Object valuePreMove(MultiEntryHandle<K, V> keyEntry, MultiEntryValueHandle<K, V> valueEntry, ElementId after, ElementId before);

		void valueMoved(MultiEntryHandle<K, V> keyEntry, MultiEntryValueHandle<K, V> valueEntry, Object moveData);
	}

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

	public ModControlledMultiMap(M backing, MultiMapModificationControl<K, V> control, MultiMapModificationListener<K, V> listener) {
		theBacking = backing;
		theControl = control;
		theListener = listener;
	}

	protected M getBacking() {
		return theBacking;
	}

	public MultiMapModificationControl<K, V> getControl() {
		return theControl;
	}

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
	public Transaction lock(boolean write, Object cause) {
		return theBacking.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theBacking.tryLock(write, cause);
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

	protected BetterSet<K> createKeySet() {
		if (theControl == null)
			return theBacking.keySet();
		ModControlledCollection.CollectionModificationControl<K> keyControl = createKeyControl();
		ModControlledCollection.CollectionModificationListener<K> keyListener = createKeyModListener();
		if (keyControl == null && keyListener == null)
			return theBacking.keySet();
		return ModControlledCollection.controlCollection(theBacking.keySet(), keyControl, keyListener);
	}

	protected ModControlledCollection.CollectionModificationControl<K> createKeyControl() {
		return theControl == null ? null : new KeySetControl<>(theBacking, theControl);
	}

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
		try (Transaction t = lock(true, null)) {
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

	protected MultiEntryHandle<K, V> wrapEntry(MultiEntryHandle<K, V> backingEntry) {
		return backingEntry == null ? null : new EntryWrapper(backingEntry);
	}

	protected BetterCollection<V> wrapValues(K key, BetterCollection<V> values) {
		if (theControl == null && theListener == null)
			return values;

		ModControlledCollection.CollectionModificationControl<V> valuesControl = createValueControl(key);
		ModControlledCollection.CollectionModificationListener<V> valuesListener = createValueListener(key);
		if (valuesControl == null && valuesListener == null)
			return values;
		return ModControlledCollection.controlCollection(values, valuesControl, valuesListener);
	}

	protected ModControlledCollection.CollectionModificationControl<V> createValueControl(K key) {
		return theControl == null ? null : new ValuesControl(key);
	}

	protected ModControlledCollection.CollectionModificationListener<V> createValueListener(K key) {
		return theListener == null ? null : new ValuesListener(key);
	}

	public class EntryWrapper implements MultiEntryHandle<K, V> {
		private final MultiEntryHandle<K, V> theBackingEntry;
		private final BetterCollection<V> theValues;

		protected EntryWrapper(MultiEntryHandle<K, V> backingEntry) {
			theBackingEntry = backingEntry;
			theValues = wrapValues(backingEntry.getKey(), backingEntry.getValues());
		}

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

	public static class KeySetControl<K, V> implements ModControlledCollection.CollectionModificationControl<K> {
		private final BetterMultiMap<K, V> theBacking;
		private final MultiMapModificationControl<K, V> theMapControl;

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

	public static class KeySetModListener<K, V> implements ModControlledCollection.CollectionModificationListener<K> {
		private final BetterMultiMap<K, V> theBacking;
		private final MultiMapModificationListener<K, V> theMapListener;

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

	public class ValuesControl implements ModControlledCollection.CollectionModificationControl<V> {
		private final K theKey;
		private MultiEntryHandle<K, V> theCachedEntry;

		public ValuesControl(K key) {
			theKey = key;
		}

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

	public class ValuesListener implements ModControlledCollection.CollectionModificationListener<V> {
		private final K theKey;
		private MultiEntryHandle<K, V> theCachedEntry;

		public ValuesListener(K key) {
			theKey = key;
		}

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

	public static class ModControlledSortedMultiMap<K, V, M extends BetterSortedMultiMap<K, V>> extends ModControlledMultiMap<K, V, M>
		implements BetterSortedMultiMap<K, V> {
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

		public class OrderedEntryWrapper extends EntryWrapper implements OrderedMultiEntry<K, V> {
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
	}
}
