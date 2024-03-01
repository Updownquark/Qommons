package org.qommons.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/** A static utility class for {@link BetterCollection}s and related structures */
public class BetterCollections {
	private static boolean SIMPLIFY_DUPLICATE_OPERATIONS = true;

	private static boolean TESTING = false;

	/**
	 * This flag allows some BetterCollection implementations to simplify duplicate operations like collection.reverse().reverse().
	 * 
	 * It is disableable for testing.
	 * 
	 * @return Whether BetterCollections should simplify duplicate operations
	 */
	public static boolean simplifyDuplicateOperations() {
		return SIMPLIFY_DUPLICATE_OPERATIONS;
	}

	/**
	 * @param simplify Whether BetterCollections should simplify duplicate operations
	 * @see #simplifyDuplicateOperations()
	 */
	public static void setSimplifyDuplicateOperations(boolean simplify) {
		SIMPLIFY_DUPLICATE_OPERATIONS = simplify;
	}

	/** @return Whether collections should perform additional integrity checks */
	public static boolean isTesting() {
		return TESTING;
	}

	/** @param testing Whether collections should perform additional integrity checks */
	public static void setTesting(boolean testing) {
		TESTING = testing;
	}

	private BetterCollections() {}

	/**
	 * @param <E> The type of the collection
	 * @param collection The collection to get an unmodifiable view of
	 * @return A BetterCollection backed by the given collection but though which the source collection cannot be modified in any way
	 */
	public static <E> BetterCollection<E> unmodifiableCollection(BetterCollection<? extends E> collection) {
		if (collection instanceof BetterSet)
			return unmodifiableSet((BetterSet<? extends E>) collection);
		else if (collection instanceof BetterList)
			return unmodifiableList((BetterList<? extends E>) collection);
		else
			return new UnmodifiableBetterCollection<>(collection);
	}

	/**
	 * @param <E> The type of the set
	 * @param set The set to get an unmodifiable view of
	 * @return A BetterSet backed by the given set but though which the source set cannot be modified in any way
	 */
	public static <E> BetterSet<E> unmodifiableSet(BetterSet<? extends E> set) {
		if (set instanceof BetterSortedSet)
			return unmodifiableSortedSet((BetterSortedSet<? extends E>) set);
		else
			return new UnmodifiableBetterSet<>(set);
	}

	/**
	 * @param <E> The type of the list
	 * @param list The list to get an unmodifiable view of
	 * @return A BetterList backed by the given list but though which the source list cannot be modified in any way
	 */
	public static <E> BetterList<E> unmodifiableList(BetterList<? extends E> list) {
		if (list instanceof BetterSortedSet)
			return unmodifiableSortedSet((BetterSortedSet<? extends E>) list);
		else
			return new UnmodifiableBetterList<>(list);
	}

	/**
	 * @param <E> The type of the sorted set
	 * @param sortedSet The sorted set to get an unmodifiable view of
	 * @return A BetterSortedSet backed by the given sorted set but though which the source set cannot be modified in any way
	 */
	public static <E> BetterSortedSet<E> unmodifiableSortedSet(BetterSortedSet<? extends E> sortedSet) {
		return new UnmodifiableBetterSortedSet<>(sortedSet);
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to get an unmodifiable view of
	 * @return A BetterMap backed by the given map but though which the source map cannot be modified in any way
	 */
	public static <K, V> BetterMap<K, V> unmodifiableMap(BetterMap<? extends K, ? extends V> map) {
		if (map instanceof BetterSortedMap)
			return unmodifiableSortedMap((BetterSortedMap<? extends K, ? extends V>) map);
		else
			return new UnmodifiableBetterMap<>(map);
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The sorted map to get an unmodifiable view of
	 * @return A BetterSortedMap backed by the given map but though which the source map cannot be modified in any way
	 */
	public static <K, V> BetterSortedMap<K, V> unmodifiableSortedMap(BetterSortedMap<? extends K, ? extends V> map) {
		return new UnmodifiableBetterSortedMap<>(map);
	}

	/**
	 * This is needed because the standard map entry defines {@link java.util.Map.Entry#setValue(Object) setValue(Object)}, which may be
	 * supported without needed to retrieve the mutable entry.
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param entry The map entry to get an unmodifiable view of
	 * @return A MapEntryHandle backed by the given entry but though which the source entry cannot be modified in any way
	 */
	protected static <K, V> MapEntryHandle<K, V> unmodifiableEntry(MapEntryHandle<? extends K, ? extends V> entry) {
		return entry == null ? null : new UnmodifiableEntry<>(entry);
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param values The {@link BetterMap#values() values} of the map
	 * @param entry The map entry to get an unmodifiable view of
	 * @return A MutableMapEntryHandle backed by the given entry but through which the source entry cannot be modified in any way
	 */
	protected static <K, V> MutableMapEntryHandle<K, V> unmodifiableMutableEntry(BetterCollection<? extends V> values,
		MapEntryHandle<? extends K, ? extends V> entry) {
		return entry == null ? null : new UnmodifiableMutableEntry<>(values, entry);
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to get an unmodifiable view of
	 * @return A BetterMultiMap backed by the given map but though which the source map cannot be modified in any way
	 */
	public static <K, V> BetterMultiMap<K, V> unmodifiableMultiMap(BetterMultiMap<? extends K, ? extends V> map) {
		return new UnmodifiableBetterMultiMap<>(map);
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param entry The multi-map entry to get an unmodifiable view of
	 * @return A MultiEntryHandle backed by the given entry but through which the source entry cannot be modified in any way
	 */
	protected static <K, V> MultiEntryHandle<K, V> unmodifiableEntry(MultiEntryHandle<? extends K, ? extends V> entry) {
		return entry == null ? null : new UnmodifiableMultiEntry<>(entry);
	}

	private static class UnmodifiableIterator<E> implements Iterator<E> {
		private final Iterator<? extends E> theWrapped;

		UnmodifiableIterator(Iterator<? extends E> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public boolean hasNext() {
			return theWrapped.hasNext();
		}

		@Override
		public E next() {
			return theWrapped.next();
		}
	}

	/**
	 * Implements {@link BetterCollections#unmodifiableCollection(BetterCollection)}
	 * 
	 * @param <E> The type of the collection
	 */
	public static class UnmodifiableBetterCollection<E> implements BetterCollection<E> {
		private final BetterCollection<? extends E> theWrapped;

		/** @param wrapped The collection to wrap */
		protected UnmodifiableBetterCollection(BetterCollection<? extends E> wrapped) {
			if(wrapped==null)
				throw new NullPointerException();
			theWrapped = wrapped;
		}

		/** @return The wrapped collection */
		protected BetterCollection<? extends E> getWrapped() {
			return theWrapped;
		}

		@Override
		public Object getIdentity() {
			return theWrapped.getIdentity();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theWrapped.getThreadConstraint();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(false, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theWrapped.tryLock(false, cause);
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return theWrapped.getCurrentCauses();
		}

		@Override
		public CoreId getCoreId() {
			return theWrapped.getCoreId();
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
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		public Iterator<E> iterator() {
			return new UnmodifiableIterator<>(theWrapped.iterator());
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
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
		public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return (BetterList<CollectionElement<E>>) (BetterList<?>) theWrapped.getElementsBySource(sourceEl, sourceCollection);
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return theWrapped.getSourceElements(localElement, theWrapped); // For element validation
			return theWrapped.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			return theWrapped.getEquivalentElement(equivalentEl);
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
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
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
			if (obj == this)
				return true;
			if (obj instanceof UnmodifiableBetterCollection)
				obj = ((UnmodifiableBetterCollection<?>) obj).theWrapped;
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link MutableCollectionElement} in unmodifiable collection implementations
	 * 
	 * @param <E> The type of the element
	 */
	public static class UnmodifiableElementWrapper<E> implements MutableCollectionElement<E> {
		private final UnmodifiableBetterCollection<E> theCollection;
		private final CollectionElement<? extends E> theWrapped;

		/**
		 * @param collection The unmodifiable collection this element is for
		 * @param wrapped The element to wrap
		 */
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

	/**
	 * Implements {@link BetterCollections#unmodifiableSet(BetterSet)}
	 * 
	 * @param <E> The type of the set
	 */
	public static class UnmodifiableBetterSet<E> extends UnmodifiableBetterCollection<E> implements BetterSet<E> {
		/** @param wrapped The set to wrap */
		protected UnmodifiableBetterSet(BetterSet<? extends E> wrapped) {
			super(wrapped);
		}

		@Override
		protected BetterSet<? extends E> getWrapped() {
			return (BetterSet<? extends E>) super.getWrapped();
		}

		@Override
		public CollectionElement<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
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

	/**
	 * Implements {@link BetterCollections#unmodifiableList(BetterList)}
	 * 
	 * @param <E> The type of the list
	 */
	public static class UnmodifiableBetterList<E> extends UnmodifiableBetterCollection<E> implements BetterList<E> {
		/** @param wrapped The list to wrap */
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

	/**
	 * Implements {@link BetterCollections#unmodifiableSortedSet(BetterSortedSet)}
	 * 
	 * @param <E> The type of the set
	 */
	public static class UnmodifiableBetterSortedSet<E> extends UnmodifiableBetterList<E> implements BetterSortedSet<E> {
		/** @param wrapped The set to wrap */
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
		public CollectionElement<E> search(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter) {
			return (CollectionElement<E>) getWrapped().search(search, filter);
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			return getWrapped().indexFor(search);
		}

		@Override
		public CollectionElement<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
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
		public <T> T[] toArray(T[] array) {
			return BetterSortedSet.super.toArray(array);
		}

		@Override
		public Object[] toArray() {
			return BetterSortedSet.super.toArray();
		}
	}

	/**
	 * Implements {@link BetterCollections#unmodifiableMap(BetterMap)}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static class UnmodifiableBetterMap<K, V> implements BetterMap<K, V> {
		private final BetterMap<? extends K, ? extends V> theWrapped;

		/** @param wrapped The map to wrap */
		protected UnmodifiableBetterMap(BetterMap<? extends K, ? extends V> wrapped) {
			theWrapped = wrapped;
		}

		/** @return The wrapped map */
		protected BetterMap<? extends K, ? extends V> getWrapped() {
			return theWrapped;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theWrapped.getThreadConstraint();
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
			return unmodifiableEntry(((BetterMap<K, V>) theWrapped).getEntry(key));
		}

		@Override
		public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
			return unmodifiableEntry(theWrapped.getEntryById(entryId));
		}

		@Override
		public MapEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
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

		@Override
		public String canPut(K key, V value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public int hashCode() {
			return BetterMap.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterMap.equals(this, obj);
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}

	/**
	 * Implements {@link BetterCollections#unmodifiableSortedMap(BetterSortedMap)}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static class UnmodifiableBetterSortedMap<K, V> extends UnmodifiableBetterMap<K, V> implements BetterSortedMap<K, V> {
		/** @param wrapped The map to wrap */
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
		public MapEntryHandle<K, V> searchEntries(Comparable<? super Entry<K, V>> search, BetterSortedList.SortedSearchFilter filter) {
			return unmodifiableEntry(getWrapped().searchEntries(entry -> search.compareTo((Map.Entry<K, V>) entry), filter));
		}
	}

	/**
	 * Implements {@link MapEntryHandle} for unmodifiable maps
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static class UnmodifiableEntry<K, V> implements MapEntryHandle<K, V> {
		private final MapEntryHandle<? extends K, ? extends V> theWrapped;

		/** @param wrapped The map entry to wrap */
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

	/**
	 * Implements {@link MutableMapEntryHandle} for unmodifiable maps
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static class UnmodifiableMutableEntry<K, V> extends UnmodifiableEntry<K, V> implements MutableMapEntryHandle<K, V> {
		private final BetterCollection<? extends V> theValues;

		/**
		 * @param values The values collection
		 * @param wrapped The map entry to wrap
		 */
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

	/**
	 * Implements {@link BetterCollections#unmodifiableMultiMap(BetterMultiMap)}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static class UnmodifiableBetterMultiMap<K, V> implements BetterMultiMap<K, V> {
		private final BetterMultiMap<? extends K, ? extends V> theWrapped;
		private BetterSet<K> theKeySet;

		/** @param wrapped The multi-map to wrap */
		protected UnmodifiableBetterMultiMap(BetterMultiMap<? extends K, ? extends V> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public Object getIdentity() {
			return theWrapped.getIdentity();
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theWrapped.getThreadConstraint();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(false, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theWrapped.tryLock(false, cause);
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return theWrapped.getCurrentCauses();
		}

		@Override
		public CoreId getCoreId() {
			return theWrapped.getCoreId();
		}

		@Override
		public int valueSize() {
			return theWrapped.valueSize();
		}

		@Override
		public BetterSet<K> keySet() {
			if (theKeySet == null)
				theKeySet = unmodifiableSet(theWrapped.keySet());
			return theKeySet;
		}

		@Override
		public MultiEntryHandle<K, V> getEntryById(ElementId keyId) {
			return unmodifiableEntry(theWrapped.getEntryById(keyId));
		}

		@Override
		public BetterCollection<V> get(K key) {
			return unmodifiableCollection(((BetterMultiMap<K, ? extends V>) theWrapped).get(key));
		}

		@Override
		public MultiEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
			ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd) {
			MultiEntryHandle<? extends K, ? extends V> found = ((BetterMultiMap<K, V>) theWrapped).getEntry(key);
			if (found != null)
				return unmodifiableEntry(found);
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public boolean clear() {
			if (theWrapped.keySet().isEmpty())
				return false;
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			if (obj instanceof UnmodifiableBetterMultiMap)
				obj = ((UnmodifiableBetterMultiMap<?, ?>) obj).theWrapped;
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * Implements {@link MultiEntryHandle} for unmodifiable multi-maps
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static class UnmodifiableMultiEntry<K, V> implements MultiEntryHandle<K, V> {
		private final MultiEntryHandle<? extends K, ? extends V> theWrapped;
		private BetterCollection<V> theValues;

		/** @param wrapped The entry to wrap */
		protected UnmodifiableMultiEntry(MultiEntryHandle<? extends K, ? extends V> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public ElementId getElementId() {
			return theWrapped.getElementId();
		}

		@Override
		public K getKey() {
			return theWrapped.getKey();
		}

		@Override
		public BetterCollection<V> getValues() {
			if (theValues == null)
				theValues = unmodifiableCollection(theWrapped.getValues());
			return theValues;
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof MultiEntryHandle))
				return false;
			else
				return theWrapped.getElementId().equals(((MultiEntryHandle<?, ?>) obj).getElementId());
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}
}
