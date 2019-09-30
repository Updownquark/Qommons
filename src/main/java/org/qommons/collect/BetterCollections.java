package org.qommons.collect;

import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;

import org.qommons.Transaction;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class BetterCollections {
	private BetterCollections() {}

	public static <E> BetterCollection<E> unmodifiableCollection(BetterCollection<? extends E> collection) {
		return new UnmodifiableBetterCollection<>(collection);
	}

	public static <E> BetterSet<E> unmodifiableSet(BetterSet<? extends E> set) {
		return new UnmodifiableBetterSet<>(set);
	}

	public static <E> BetterList<E> unmodifiableList(BetterList<? extends E> list) {
		return new UnmodifiableBetterList<>(list);
	}

	public static <E> BetterSortedSet<E> unmodifiableSortedSet(BetterSortedSet<? extends E> sortedSet) {
		return new UnmodifiableBetterSortedSet<>(sortedSet);
	}

	public static <K, V> BetterMap<K, V> unmodifiableMap(BetterMap<? extends K, ? extends V> map) {
		return new UnmodifiableBetterMap<>(map);
	}

	public static <K, V> BetterSortedMap<K, V> unmodifiableSortedMap(BetterSortedMap<? extends K, ? extends V> map) {
		return new UnmodifiableBetterSortedMap<>(map);
	}

	protected static <K, V> MapEntryHandle<K, V> unmodifiableEntry(MapEntryHandle<? extends K, ? extends V> entry) {
		return entry == null ? null : new UnmodifiableEntry<>(entry);
	}

	protected static <K, V> MutableMapEntryHandle<K, V> unmodifiableMutableEntry(BetterCollection<? extends V> values,
		MapEntryHandle<? extends K, ? extends V> entry) {
		return entry == null ? null : new UnmodifiableMutableEntry<>(values, entry);
	}

	public static class UnmodifiableBetterCollection<E> implements BetterCollection<E> {
		private final BetterCollection<? extends E> theWrapped;

		protected UnmodifiableBetterCollection(BetterCollection<? extends E> wrapped) {
			theWrapped = wrapped;
		}

		protected BetterCollection<? extends E> getWrapped() {
			return theWrapped;
		}

		@Override
		public Object getIdentity() {
			return theWrapped.getIdentity();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theWrapped.lock(false, !write || structural, cause);
		}

		@Override
		public Transaction tryLock(boolean write, boolean structural, Object cause) {
			return theWrapped.tryLock(false, !write || structural, cause);
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public boolean belongs(Object o) {
			return theWrapped.belongs(o);
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theWrapped.getStamp(structuralOnly);
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			if (!theWrapped.belongs(value))
				return null;
			return ((BetterCollection<E>) theWrapped).getElement(value, first);
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			return (CollectionElement<E>) theWrapped.getElement(id);
		}

		@Override
		public CollectionElement<E> getTerminalElement(boolean first) {
			return (CollectionElement<E>) theWrapped.getTerminalElement(first);
		}

		@Override
		public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
			return (CollectionElement<E>) theWrapped.getAdjacentElement(elementId, next);
		}

		@Override
		public MutableCollectionElement<E> mutableElement(ElementId id) {
			return new UnmodifiableElementWrapper<>(this, theWrapped.getElement(id));
		}

		@Override
		public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl) {
			return (BetterList<CollectionElement<E>>) (BetterList<?>) theWrapped.getElementsBySource(sourceEl);
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return theWrapped.getSourceElements(localElement, theWrapped); // For element validation
			return theWrapped.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public String canAdd(E value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			if (theWrapped.isEmpty())
				return;
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	public static class UnmodifiableElementWrapper<E> implements MutableCollectionElement<E> {
		private final UnmodifiableBetterCollection<E> theCollection;
		private final CollectionElement<? extends E> theWrapped;

		protected UnmodifiableElementWrapper(UnmodifiableBetterCollection<E> collection, CollectionElement<? extends E> wrapped) {
			theCollection = collection;
			theWrapped = wrapped;
		}

		@Override
		public ElementId getElementId() {
			return theWrapped.getElementId();
		}

		@Override
		public E get() {
			return theWrapped.get();
		}

		@Override
		public BetterCollection<E> getCollection() {
			return theCollection;
		}

		@Override
		public String isEnabled() {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public String isAcceptable(E value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canRemove() {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	public static class UnmodifiableBetterSet<E> extends UnmodifiableBetterCollection<E> implements BetterSet<E> {
		protected UnmodifiableBetterSet(BetterSet<? extends E> wrapped) {
			super(wrapped);
		}

		@Override
		protected BetterSet<? extends E> getWrapped() {
			return (BetterSet<? extends E>) super.getWrapped();
		}

		@Override
		public CollectionElement<E> getOrAdd(E value, boolean first, Runnable added) {
			if (!getWrapped().belongs(value))
				return null;
			return getElement(value, first);
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return getWrapped().isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			return getWrapped().checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			// Kinda weird here since this involves modification, but the caller itself isn't doing the modification
			return ((BetterSet<E>) getWrapped()).repair(element, listener);
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			// Kinda weird here since this involves modification, but the caller itself isn't doing the modification
			return ((BetterSet<E>) getWrapped()).repair(listener);
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return super.toArray(a);
		}
	}

	public static class UnmodifiableBetterList<E> extends UnmodifiableBetterCollection<E> implements BetterList<E> {
		protected UnmodifiableBetterList(BetterList<? extends E> wrapped) {
			super(wrapped);
		}

		@Override
		protected BetterList<? extends E> getWrapped() {
			return (BetterList<? extends E>) super.getWrapped();
		}

		@Override
		public CollectionElement<E> getElement(int index) {
			return (CollectionElement<E>) getWrapped().getElement(index);
		}

		@Override
		public boolean isContentControlled() {
			return getWrapped().isContentControlled();
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return getWrapped().getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return getWrapped().getElementsAfter(id);
		}
	}

	public static class UnmodifiableBetterSortedSet<E> extends UnmodifiableBetterList<E> implements BetterSortedSet<E> {
		protected UnmodifiableBetterSortedSet(BetterSortedSet<? extends E> wrapped) {
			super(wrapped);
		}

		@Override
		protected BetterSortedSet<? extends E> getWrapped() {
			return (BetterSortedSet<? extends E>) super.getWrapped();
		}

		@Override
		public Comparator<? super E> comparator() {
			return (Comparator<? super E>) getWrapped().comparator();
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
			return (CollectionElement<E>) getWrapped().search(search, filter);
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			return getWrapped().indexFor(search);
		}

		@Override
		public CollectionElement<E> getOrAdd(E value, boolean first, Runnable added) {
			return getElement(value, first);
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return getWrapped().isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			return getWrapped().checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			// Kinda weird here since this involves modification, but the caller itself isn't doing the modification
			return ((BetterSet<E>) getWrapped()).repair(element, listener);
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			// Kinda weird here since this involves modification, but the caller itself isn't doing the modification
			return ((BetterSet<E>) getWrapped()).repair(listener);
		}
	}

	public static class UnmodifiableBetterMap<K, V> implements BetterMap<K, V> {
		private final BetterMap<? extends K, ? extends V> theWrapped;

		protected UnmodifiableBetterMap(BetterMap<? extends K, ? extends V> wrapped) {
			theWrapped = wrapped;
		}

		protected BetterMap<? extends K, ? extends V> getWrapped() {
			return theWrapped;
		}

		@Override
		public Object getIdentity() {
			return theWrapped.getIdentity();
		}

		@Override
		public BetterSet<K> keySet() {
			return unmodifiableSet(theWrapped.keySet());
		}

		@Override
		public MapEntryHandle<K, V> getEntry(K key) {
			if (!theWrapped.keySet().belongs(key))
				return null;
			return unmodifiableEntry(((BetterMap<K, V>) theWrapped).getEntry(key));
		}

		@Override
		public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
			return unmodifiableEntry(theWrapped.getEntryById(entryId));
		}

		@Override
		public MapEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, boolean first, Runnable added) {
			return getEntry(key);
		}

		@Override
		public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
			return unmodifiableMutableEntry(theWrapped.values(), theWrapped.getEntryById(entryId));
		}

		@Override
		public MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}
	}

	public static class UnmodifiableBetterSortedMap<K, V> extends UnmodifiableBetterMap<K, V> implements BetterSortedMap<K, V> {
		protected UnmodifiableBetterSortedMap(BetterSortedMap<? extends K, ? extends V> wrapped) {
			super(wrapped);
		}

		@Override
		protected BetterSortedMap<? extends K, ? extends V> getWrapped() {
			return (BetterSortedMap<? extends K, ? extends V>) super.getWrapped();
		}

		@Override
		public BetterSortedSet<K> keySet() {
			return unmodifiableSortedSet(getWrapped().keySet());
		}

		@Override
		public MapEntryHandle<K, V> searchEntries(Comparable<? super Entry<K, V>> search, SortedSearchFilter filter) {
			return unmodifiableEntry(getWrapped().searchEntries(entry -> search.compareTo((Map.Entry<K, V>) entry), filter));
		}
	}

	public static class UnmodifiableEntry<K, V> implements MapEntryHandle<K, V> {
		private final MapEntryHandle<? extends K, ? extends V> theWrapped;

		protected UnmodifiableEntry(MapEntryHandle<? extends K, ? extends V> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public ElementId getElementId() {
			return theWrapped.getElementId();
		}

		@Override
		public V get() {
			return theWrapped.get();
		}

		@Override
		public K getKey() {
			return theWrapped.getKey();
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	public static class UnmodifiableMutableEntry<K, V> extends UnmodifiableEntry<K, V> implements MutableMapEntryHandle<K, V> {
		private final BetterCollection<? extends V> theValues;

		protected UnmodifiableMutableEntry(BetterCollection<? extends V> values, MapEntryHandle<? extends K, ? extends V> wrapped) {
			super(wrapped);
			theValues = values;
		}

		@Override
		public BetterCollection<V> getCollection() {
			return unmodifiableCollection(theValues);
		}

		@Override
		public String isEnabled() {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public String isAcceptable(V value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canRemove() {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}
	}
}
