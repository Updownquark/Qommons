package org.qommons.collect;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.qommons.Transaction;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class ModControlledMap<K, V, M extends BetterMap<K, V>> implements BetterMap<K, V> {
	public interface MapModificationControl<K, V> {
		String canAdd(K key, V value, ElementId after, ElementId before);

		String canRemove(MapEntryHandle<K, V> entry);

		String isKeyModifiable(MapEntryHandle<K, V> entry);

		String isKeyAcceptable(MapEntryHandle<K, V> entry, K newKey);

		String isModifiable(MapEntryHandle<K, V> entry);

		String isAcceptable(MapEntryHandle<K, V> entry, V newValue);

		String canMove(MapEntryHandle<K, V> entry, ElementId after, ElementId before);
	}

	public interface MapModificationListener<K, V> {
		void entryAdded(MapEntryHandle<K, V> entry);

		void entryPreRemoved(MapEntryHandle<K, V> entry);

		void entryRemoved(MapEntryHandle<K, V> entry);

		void entryKeyChanged(MapEntryHandle<K, V> entry, K previousKey);

		void entryValueChanged(MapEntryHandle<K, V> entry, V previousValue);

		Object entryPreMoved(MapEntryHandle<K, V> entry, ElementId after, ElementId before);

		void entryMoved(MapEntryHandle<K, V> entry, Object moveData);
	}

	public static <K, V, M extends BetterMap<K, V>> M controlMap(M map, MapModificationControl<K, V> control,
		MapModificationListener<K, V> listener) {
		if (map instanceof BetterSortedMap)
			return (M) new ModControlledSortedMap<>((BetterSortedMap<K, V>) map, control, listener);
		else
			return (M) new ModControlledMap<>(map, control, listener);
	}

	private final M theBacking;
	private final MapModificationControl<K, V> theControl;
	private final MapModificationListener<K, V> theListener;
	private BetterSet<K> theKeySet;

	public ModControlledMap(M backing, MapModificationControl<K, V> control, MapModificationListener<K, V> listener) {
		theBacking = backing;
		theControl = control;
		theListener = listener;
	}

	protected M getBacking() {
		return theBacking;
	}

	public MapModificationControl<K, V> getControl() {
		return theControl;
	}

	protected MapModificationListener<K, V> getListener() {
		return theListener;
	}

	@Override
	public Object getIdentity() {
		return theBacking.getIdentity();
	}

	@Override
	public BetterMap<K, V> alias(String alias) {
		theBacking.alias(alias);
		return this;
	}

	@Override
	public Set<String> getAliases() {
		return theBacking.getAliases();
	}

	@Override
	public BetterSet<K> keySet() {
		if (theKeySet == null)
			theKeySet = createKeySet();
		return theKeySet;
	}

	protected BetterSet<K> createKeySet() {
		if (theControl == null && theListener == null)
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
	public MapEntryHandle<K, V> getEntry(K key) {
		return theBacking.getEntry(key);
	}

	@Override
	public MapEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
		boolean first, Runnable preAdd, Runnable postAdd) {
		if (theControl == null && theListener == null)
			return theBacking.getOrPutEntry(key, value, after, before, first, preAdd, postAdd);
		try (Transaction t = lock(true, null)) {
			MapEntryHandle<K, V> found = getEntry(key);
			if (found != null)
				return found;
			V v = value.apply(key);
			String msg = theControl == null ? null : theControl.canAdd(key, v, after, before);
			if (msg != null)
				throw new UnsupportedOperationException(msg);
			if (preAdd != null)
				preAdd.run();
			MapEntryHandle<K, V> added = putEntry(key, v, after, before, first);
			if (theListener != null)
				theListener.entryAdded(added);
			if (postAdd != null)
				postAdd.run();
			return added;
		}
	}

	@Override
	public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
		return theBacking.getEntryById(entryId);
	}

	@Override
	public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
		return wrapMutable(theBacking.mutableEntry(entryId));
	}

	@Override
	public String canPut(K key, V value) {
		String msg = theControl == null ? null : theControl.canAdd(key, value, null, null);
		if (msg == null)
			msg = theBacking.canPut(key, value);
		return msg;
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

	protected MutableMapEntryHandle<K, V> wrapMutable(MutableMapEntryHandle<K, V> backingEntry) {
		return new MutableEntryWrapper(backingEntry);
	}

	public class MutableEntryWrapper implements MutableMapEntryHandle<K, V> {
		private final MutableMapEntryHandle<K, V> theBackingEntry;

		protected MutableEntryWrapper(MutableMapEntryHandle<K, V> backingEntry) {
			theBackingEntry = backingEntry;
		}

		protected MutableMapEntryHandle<K, V> getBackingEntry() {
			return theBackingEntry;
		}

		@Override
		public K getKey() {
			return theBackingEntry.getKey();
		}

		@Override
		public String isEnabled() {
			String msg = theControl == null ? null : theControl.isModifiable(theBackingEntry);
			if (msg == null)
				msg = theBackingEntry.isEnabled();
			return msg;
		}

		@Override
		public String isAcceptable(V value) {
			String msg = theControl == null ? null : theControl.isAcceptable(theBackingEntry, value);
			if (msg == null)
				msg = theBackingEntry.isAcceptable(value);
			return msg;
		}

		@Override
		public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
			if (theControl == null && theListener == null) {
				theBackingEntry.set(value);
				return;
			}
			String msg = theControl == null ? null : theControl.isAcceptable(theBackingEntry, value);
			if (msg != null)
				throw new UnsupportedOperationException(msg);
			V prev = theBackingEntry.get();
			theBackingEntry.set(value);
			if (theListener != null)
				theListener.entryValueChanged(theBackingEntry, prev);
		}

		@Override
		public String canRemove() {
			String msg = theControl == null ? null : theControl.isModifiable(theBackingEntry);
			if (msg == null)
				msg = theBackingEntry.canRemove();
			return msg;
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			if (theControl == null && theListener != null) {
				theBackingEntry.remove();
				return;
			}
			String msg = theControl == null ? null : theControl.canRemove(theBackingEntry);
			if (msg != null)
				throw new UnsupportedOperationException(msg);
			if (theListener != null)
				theListener.entryPreRemoved(theBackingEntry);
			if (theBackingEntry.getElementId().isPresent())
				theBackingEntry.remove();
			if (theListener != null)
				theListener.entryRemoved(theBackingEntry);
		}

		@Override
		public ElementId getElementId() {
			return theBackingEntry.getElementId();
		}

		@Override
		public V get() {
			return theBackingEntry.get();
		}

		@Override
		public MutableMapEntryHandle<K, V> getAdjacent(boolean next) {
			MutableMapEntryHandle<K, V> adj = theBackingEntry.getAdjacent(next);
			return adj == null ? null : wrapMutable(adj);
		}

		@Override
		public String toString() {
			return theBackingEntry.toString();
		}
	}

	public static class KeySetControl<K, V> implements ModControlledCollection.CollectionModificationControl<K> {
		private final BetterMap<K, V> theBacking;
		private final MapModificationControl<K, V> theMapControl;

		public KeySetControl(BetterMap<K, V> backing, MapModificationControl<K, V> mapControl) {
			theBacking = backing;
			theMapControl = mapControl;
		}

		@Override
		public String canAdd(K value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public String canRemove(CollectionElement<K> element) {
			return theMapControl.canRemove(theBacking.getEntryById(element.getElementId()));
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
			return theMapControl.canMove(theBacking.getEntryById(element.getElementId()), after, before);
		}
	}

	public static class KeySetModListener<K, V> implements ModControlledCollection.CollectionModificationListener<K> {
		private final BetterMap<K, V> theBacking;
		private final MapModificationListener<K, V> theMapListener;

		public KeySetModListener(BetterMap<K, V> backing, MapModificationListener<K, V> mapListener) {
			theBacking = backing;
			theMapListener = mapListener;
		}

		@Override
		public void elementAdded(CollectionElement<K> element) {
			theMapListener.entryAdded(theBacking.getEntryById(element.getElementId()));
		}

		@Override
		public void elementPreRemove(MutableCollectionElement<K> element) {
			MutableMapEntryHandle<K, V> entry = theBacking.mutableEntry(element.getElementId());
			theMapListener.entryPreRemoved(entry);
			entry.remove();
			theMapListener.entryRemoved(entry);
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

	public static class ModControlledSortedMap<K, V, M extends BetterSortedMap<K, V>> extends ModControlledMap<K, V, M>
		implements BetterSortedMap<K, V> {
		public ModControlledSortedMap(M backing, MapModificationControl<K, V> control, MapModificationListener<K, V> listener) {
			super(backing, control, listener);
		}

		@Override
		protected MutableOrderedMapEntry<K, V> wrapMutable(MutableMapEntryHandle<K, V> backingEntry) {
			return new MutableOrderedEntryWrapper((MutableOrderedMapEntry<K, V>) backingEntry);
		}

		@Override
		public OrderedMapEntry<K, V> getEntry(K key) {
			return getBacking().getEntry(key);
		}

		@Override
		public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
			return getBacking().getEntryById(entryId);
		}

		@Override
		public OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
			return (OrderedMapEntry<K, V>) super.getOrPutEntry(key, value, after, before, first, preAdd, postAdd);
		}

		@Override
		public BetterSortedSet<K> keySet() {
			return (BetterSortedSet<K>) super.keySet();
		}

		@Override
		public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
			return wrapMutable(getBacking().mutableEntry(entryId));
		}

		@Override
		public OrderedMapEntry<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, SortedSearchFilter filter) {
			return getBacking().searchEntries(search, filter);
		}

		public class MutableOrderedEntryWrapper extends MutableEntryWrapper implements MutableOrderedMapEntry<K, V> {
			protected MutableOrderedEntryWrapper(MutableOrderedMapEntry<K, V> backingEntry) {
				super(backingEntry);
			}

			@Override
			protected MutableOrderedMapEntry<K, V> getBackingEntry() {
				return (MutableOrderedMapEntry<K, V>) super.getBackingEntry();
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
			public MutableOrderedMapEntry<K, V> getAdjacent(boolean next) {
				MutableOrderedMapEntry<K, V> adj = getBackingEntry().getAdjacent(next);
				return adj == null ? null : wrapMutable(adj);
			}
		}
	}
}
