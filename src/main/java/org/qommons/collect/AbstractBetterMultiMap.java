package org.qommons.collect;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.function.Function;

import org.qommons.Identifiable;
import org.qommons.Stamped;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMultiMap;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.SortedTreeList;

/**
 * An abstract {@link BetterMultiMap} implementation based on a {@link BetterMap}
 * 
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public abstract class AbstractBetterMultiMap<K, V> implements BetterMultiMap<K, V> {
	/**
	 * Backs a {@link AbstractBetterMultiMap} by supplying value collections for each key
	 * 
	 * @param <K> The key super-type of the maps this supplier can support
	 * @param <V> The value super-type of the maps this supplier can support
	 */
	public interface ValueCollectionSupplier<K, V> {
		/**
		 * @param <V2> The sub-type of value collection to create
		 * @param key The key to create the value collection for
		 * @param locking The locking strategy for the collection to use
		 * @return The value collection to store values for the given key in the multi-map
		 */
		<V2 extends V> BetterCollection<V2> createValuesFor(K key, CollectionLockingStrategy locking);

		/**
		 * @param <V2> The sub-type of value collection to create
		 * @return An immutable, empty collection to use for the values of a key that cannot exist in the multi-map
		 */
		<V2 extends V> BetterCollection<V2> createEmptyValues();

		/**
		 * Creates a collection representing the values associated with a key in the multi-map. The collection will be exposed through the
		 * API and may have an unlimited lifetime. It will remain valid even as the key whose values it represents may be removed and added
		 * from the multi-map.
		 * 
		 * @param <V2> The sub-type of value collection to create
		 * @param backing The backing through which to poll the state and capabilities of the multi-map for the key
		 * @return The collection to represent the key's values in the multi-map
		 */
		<V2 extends V> BetterCollection<V2> createWrapperCollection(ValueCollectionBacking<V2> backing);

		/**
		 * Called when a collection created with {@link #createValuesFor(Object, CollectionLockingStrategy)} is no longer needed due to all
		 * its values being removed (an entry in a multi-map cannot exist with no values)
		 * 
		 * @param unused The unused collection
		 */
		default void dispose(BetterCollection<? extends V> unused) {}
	}

	/**
	 * An implementation in {@link AbstractBetterMultiMap} used by
	 * {@link AbstractBetterMultiMap.ValueCollectionSupplier#createWrapperCollection(ValueCollectionBacking)}. It represents the state of a
	 * particular key in the multi-map.
	 * 
	 * @param <V> The value type of the map
	 */
	public interface ValueCollectionBacking<V> extends Identifiable, Transactable, Stamped {
		/**
		 * @param addIfNotPresent Whether the key represented by this backing should be created if it is not present
		 * @return The current collection of values for the key, or null if the key is not currently present in the map
		 */
		BetterCollection<V> getBacking(boolean addIfNotPresent);

		/**
		 * @return null if the key can be added to the map (e.g. by adding a value to the collection of a key that is not present), or a
		 *         message saying why it can't be
		 */
		String canAdd();

		/** Removes the key from the map */
		void remove();
	}

	/** A simple {@link ValueCollectionSupplier} that just creates {@link BetterTreeList}s for each value */
	public static final ValueCollectionSupplier<Object, Object> LIST_SUPPLIER = new ValueCollectionSupplier<Object, Object>() {
		@Override
		public <V2> BetterCollection<V2> createValuesFor(Object key, CollectionLockingStrategy locking) {
			return BetterTreeList.<V2>build().withLocker(locking).build();
		}

		@Override
		public <V2> BetterCollection<V2> createEmptyValues() {
			return BetterList.empty();
		}

		@Override
		public <V2 extends Object> BetterCollection<V2> createWrapperCollection(ValueCollectionBacking<V2> backing) {
			return new WrappingBetterList<>(backing);
		}
	};

	/**
	 * @param <V> The super-type of value the supplier will support
	 * @param sorting The sorting for the values
	 * @param distinct Whether the values should be distinct within each key (i.e. the value collections will be {@link BetterSortedSet}s as
	 *        opposed to {@link BetterSortedList}s)
	 * @return A supplier that creates {@link BetterSortedList}s or {@link BetterSortedSet}s for each key
	 */
	public static <V> ValueCollectionSupplier<Object, V> sortedSupplier(Comparator<? super V> sorting, boolean distinct) {
		return new ValueCollectionSupplier<Object, V>() {
			private BetterList<V> EMPTY;

			@Override
			public <V2 extends V> BetterCollection<V2> createValuesFor(Object key, CollectionLockingStrategy locking) {
				return distinct ? BetterTreeSet.<V2> buildTreeSet(sorting).withLocker(locking).build()
					: SortedTreeList.<V2> buildTreeList(sorting).withLocker(locking).build();
			}

			@Override
			public <V2 extends V> BetterCollection<V2> createEmptyValues() {
				if (EMPTY == null) {
					if (distinct)
						EMPTY = BetterSortedSet.empty(sorting);
					else
						EMPTY = BetterSortedList.empty(sorting);
				}
				return (BetterCollection<V2>) EMPTY;
			}

			@Override
			public <V2 extends V> BetterCollection<V2> createWrapperCollection(ValueCollectionBacking<V2> backing) {
				if (distinct)
					return new WrappingSortedSet<>(backing, sorting);
				else
					return new WrappingSortedList<>(backing, sorting);
			}
		};
	}

	/**
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param keyCompare The key sorting for the map
	 * @return A builder to build a tree-based, {@link BetterSortedMultiMap}
	 */
	public static <K, V> BetterTreeMultiMap.Builder<K, V> buildSorted(Comparator<? super K> keyCompare) {
		return BetterTreeMultiMap.build(keyCompare);
	}

	/**
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @return A builder to build a hash-based {@link BetterMultiMap}
	 */
	public static <K, V> BetterHashMultiMap.Builder<K, V> buildHashed() {
		return BetterHashMultiMap.build();
	}

	/**
	 * A builder to build a {@link BetterMultiMap}
	 * 
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 */
	public static abstract class Builder<K, V> {
		private Function<Object, CollectionLockingStrategy> theLocking;
		private ValueCollectionSupplier<? super K, ? super V> theValues;
		private String theDescription;

		/** @param initDescription The initial description for the map */
		protected Builder(String initDescription) {
			theValues = LIST_SUPPLIER;
			theDescription = initDescription;
		}

		/**
		 * @param safe Whether the multi-map should be thread-safe
		 * @return This builder
		 */
		public Builder<K, V> safe(boolean safe) {
			return withLocking(v -> safe ? new StampedLockingStrategy(v) : new FastFailLockingStrategy());
		}

		/**
		 * @param locking The locking strategy for the multi-map
		 * @return This builder
		 * @see AbstractBetterMultiMap#LIST_SUPPLIER
		 */
		public Builder<K, V> withLocking(CollectionLockingStrategy locking) {
			return withLocking(__ -> locking);
		}

		/**
		 * @param locking The locking strategy for the multi-map
		 * @return This builder
		 * @see AbstractBetterMultiMap#LIST_SUPPLIER
		 */
		public Builder<K, V> withLocking(Function<Object, CollectionLockingStrategy> locking) {
			theLocking = locking;
			return this;
		}

		/**
		 * Specifies that the multi-map's value collections should be sorted
		 * 
		 * @param valueCompare The sorting for the values
		 * @param distinctValues Whether the values should be distinct
		 * @return This builder
		 * @see AbstractBetterMultiMap#sortedSupplier(Comparator, boolean)
		 */
		public Builder<K, V> withSortedValues(Comparator<? super V> valueCompare, boolean distinctValues) {
			theValues = sortedSupplier(valueCompare, distinctValues);
			return this;
		}

		/**
		 * @param values The value supplier for the multi-map
		 * @return This builder
		 */
		public Builder<K, V> withValues(ValueCollectionSupplier<? super K, ? super V> values) {
			theValues = values;
			return this;
		}

		/**
		 * @param description The description for the multi-map's {@link BetterMultiMap#getIdentity() identity}
		 * @return This builder
		 */
		public Builder<K, V> withDescription(String description) {
			theDescription = description;
			return this;
		}

		/**
		 * @param built The built multi map
		 * @return The locking for the multi-map
		 */
		protected CollectionLockingStrategy getLocking(Object built) {
			if (theLocking != null)
				return theLocking.apply(built);
			else
				return new StampedLockingStrategy(built);
		}

		/** @return The value supplier for the multi-map */
		protected ValueCollectionSupplier<? super K, ? super V> getValues() {
			return theValues;
		}

		/** @return The description for the multi-map */
		protected String getDescription() {
			return theDescription;
		}

		/** @return The new multi-map */
		public abstract BetterMultiMap<K, V> buildMultiMap();
	}

	private final Object theIdentity;
	private final CollectionLockingStrategy theLocking;
	private final BetterMap<K, BetterCollection<V>> theEntries;
	private final ValueCollectionSupplier<? super K, ? super V> theValues;
	private final BetterSet<K> theKeySet;

	private long theStamp;
	private long theLastCountStamp;
	private int theLastCountedValueSize;

	/**
	 * @param locking The locking for the map
	 * @param entries The backing {@link BetterMap} for the map
	 * @param values The value supplier for the map
	 * @param description The description for the map's {@link #getIdentity() identity}
	 */
	protected AbstractBetterMultiMap(Function<Object, CollectionLockingStrategy> locking, BetterMap<K, BetterCollection<V>> entries,
		ValueCollectionSupplier<? super K, ? super V> values, String description) {
		theIdentity = Identifiable.baseId(description, this);
		theLocking = locking.apply(this);
		theEntries = entries;
		theValues = values;
		theLastCountStamp = -1;

		theKeySet = createKeySet(theEntries.keySet());
	}

	/**
	 * @param backing The {@link BetterMap#keySet() key set} of the backing {@link BetterMap}
	 * @return The key set for this {@link BetterMultiMap}
	 */
	protected BetterSet<K> createKeySet(BetterSet<K> backing) {
		return new BetterMultiMapKeySet(backing);
	}

	@Override
	public Object getIdentity() {
		return theIdentity;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theLocking.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theLocking.tryLock(write, cause);
	}

	@Override
	public long getStamp() {
		return theStamp;
	}

	@Override
	public int valueSize() {
		if (theLastCountStamp == getStamp())
			return theLastCountedValueSize;
		try (Transaction t = lock(false, null)) {
			theLastCountStamp = getStamp();
			theLastCountedValueSize = 0;
			for (BetterCollection<V> values : theEntries.values())
				theLastCountedValueSize += values.size();
		}
		return theLastCountedValueSize;
	}

	@Override
	public boolean clear() {
		boolean removedAny = false;
		try (Transaction t = lock(true, null)) {
			CollectionElement<K> keyEl = theEntries.keySet().getTerminalElement(true);
			while (keyEl != null) {
				MutableMapEntryHandle<K, BetterCollection<V>> entry = theEntries.mutableEntry(keyEl.getElementId());
				if (entry.canRemove() == null) {
					BetterCollection<V> value = entry.getValue();
					entry.remove();
					theValues.dispose(value);
					removedAny = true;
				}
			}
		}
		return removedAny;
	}

	@Override
	public BetterSet<K> keySet() {
		return theKeySet;
	}

	@Override
	public MultiEntryHandle<K, V> getEntryById(ElementId keyId) {
		return entryFor(theEntries.getEntryById(keyId));
	}

	@Override
	public BetterCollection<V> get(Object key) {
		if (!theEntries.keySet().belongs(key))
			return theValues.createEmptyValues();
		return theValues.createWrapperCollection(new ValueBacking((K) key, null));
	}

	@Override
	public MultiEntryValueHandle<K, V> putEntry(K key, V value, ElementId afterKey, ElementId beforeKey, boolean first) {
		try (Transaction t = lock(true, null)) {
			MapEntryHandle<K, BetterCollection<V>> entry = theEntries.getOrPutEntry(key, k -> theValues.createValuesFor(k, theLocking),
				afterKey, beforeKey, first, null);
			if (entry == null)
				return null;
			CollectionElement<V> valueEl = entry.getValue().addElement(value, first);
			return valueEl == null ? null : getEntryById(entry.getElementId(), valueEl.getElementId());
		}
	}

	@Override
	public MultiEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
		ElementId beforeKey, boolean first, Runnable added) {
		return entryFor(theEntries.getOrPutEntry(key, k -> {
			BetterCollection<V> values = theValues.createValuesFor(k, theLocking);
			for (V v : value.apply(k))
				values.add(v);
			return values;
		}, afterKey, beforeKey, first, added));
	}

	private MultiEntryHandle<K, V> entryFor(MapEntryHandle<K, BetterCollection<V>> mapEntry) {
		return new MultiEntryHandle<K, V>() {
			@Override
			public K getKey() {
				return mapEntry.getKey();
			}

			@Override
			public ElementId getElementId() {
				return mapEntry.getElementId();
			}

			@Override
			public BetterCollection<V> getValues() {
				return theValues.createWrapperCollection(new ValueBacking(mapEntry.getKey(), mapEntry.getElementId()));
			}
		};
	}

	/** Implements {@link AbstractBetterMultiMap#keySet()} */
	protected class BetterMultiMapKeySet extends AbstractIdentifiable implements BetterSet<K> {
		private final BetterSet<K> theBacking;
		private long theKeyStamp;

		/** @param backing The key set of the backing multi-map's backing {@link BetterMap} */
		protected BetterMultiMapKeySet(BetterSet<K> backing) {
			theBacking = backing;
		}

		/** @return The key set of the backing multi-map's backing {@link BetterMap} */
		protected BetterSet<K> getBacking() {
			return theBacking;
		}

		@Override
		public boolean belongs(Object o) {
			return getBacking().belongs(o);
		}

		@Override
		public int size() {
			return getBacking().size();
		}

		@Override
		public boolean isEmpty() {
			return getBacking().isEmpty();
		}

		@Override
		public long getStamp() {
			return theKeyStamp;
		}

		@Override
		public Object createIdentity() {
			return Identifiable.wrap(AbstractBetterMultiMap.this, "keySet");
		}

		@Override
		public boolean isLockSupported() {
			return getBacking().isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return getBacking().lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return getBacking().tryLock(write, cause);
		}

		@Override
		public CollectionElement<K> getElement(K value, boolean first) {
			return getBacking().getElement(value, first);
		}

		@Override
		public CollectionElement<K> getElement(ElementId id) {
			return getBacking().getElement(id);
		}

		@Override
		public CollectionElement<K> getTerminalElement(boolean first) {
			return getBacking().getTerminalElement(first);
		}

		@Override
		public CollectionElement<K> getAdjacentElement(ElementId elementId, boolean next) {
			return getBacking().getAdjacentElement(elementId, next);
		}

		@Override
		public MutableCollectionElement<K> mutableElement(ElementId id) {
			MutableCollectionElement<K> keyEl = getBacking().mutableElement(id);
			return new MutableCollectionElement<K>() {
				@Override
				public ElementId getElementId() {
					return id;
				}

				@Override
				public K get() {
					return keyEl.get();
				}

				@Override
				public BetterCollection<K> getCollection() {
					return BetterMultiMapKeySet.this;
				}

				@Override
				public String isEnabled() {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public String isAcceptable(K value) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public void set(K value) throws UnsupportedOperationException, IllegalArgumentException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public String canRemove() {
					return keyEl.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					BetterCollection<V> values = theEntries.getEntryById(id).get();
					keyEl.remove();
					theValues.dispose(values);
				}
			};
		}

		@Override
		public BetterList<CollectionElement<K>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return getBacking().getElementsBySource(sourceEl, sourceCollection);
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(localElement);
			else
				return getBacking().getSourceElements(localElement, sourceCollection);
		}

		@Override
		public String canAdd(K value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<K> addElement(K value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return theBacking.canMove(valueEl, after, before);
		}

		@Override
		public CollectionElement<K> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			return getBacking().move(valueEl, after, before, first, afterRemove);
		}

		@Override
		public void clear() {
			try (Transaction t = AbstractBetterMultiMap.this.lock(true, null)) {
				if (isEmpty())
					return;
				theKeyStamp++;
				AbstractBetterMultiMap.this.clear();
			}
		}

		@Override
		public CollectionElement<K> getOrAdd(K value, ElementId after, ElementId before, boolean first, Runnable added) {
			return getBacking().getElement(value, first);
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return getBacking().isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			return getBacking().checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<K, X> listener) {
			return getBacking().repair(element, listener);
		}

		@Override
		public <X> boolean repair(RepairListener<K, X> listener) {
			return getBacking().repair(listener);
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return BetterSet.super.toArray(a);
		}

		@Override
		public int hashCode() {
			return BetterSet.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterSet.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterSet.toString(this);
		}
	}

	class ValueBacking extends AbstractIdentifiable implements ValueCollectionBacking<V> {
		private final K theKey;
		private ElementId theId;

		ValueBacking(K key, ElementId id) {
			theKey = key;
			theId = id;
		}

		@Override
		public Object createIdentity() {
			return Identifiable.wrap(AbstractBetterMultiMap.this.getIdentity(), "values", theKey);
		}

		@Override
		public BetterCollection<V> getBacking(boolean addIfNotPresent) {
			try (Transaction t = lock(false, null)) {
				MapEntryHandle<K, BetterCollection<V>> entry;
				if (theId != null && theId.isPresent())
					entry = theEntries.getEntryById(theId);
				else {
					entry = theEntries.getEntry(theKey);
					if (entry != null)
						theId = entry.getElementId();
				}
				if (entry != null)
					return entry.get();
				else if (!addIfNotPresent)
					return null;
			}
			try (Transaction t = lock(true, null)) {
				MapEntryHandle<K, BetterCollection<V>> entry = theEntries.getOrPutEntry(theKey,
					k -> theValues.createValuesFor(k, theLocking), //
					null, null, false, null);
				if (entry == null)
					throw new UnsupportedOperationException("Could not add map entry for " + theKey);
				return entry.getValue();
			}
		}

		@Override
		public boolean isLockSupported() {
			return AbstractBetterMultiMap.this.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return AbstractBetterMultiMap.this.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return AbstractBetterMultiMap.this.tryLock(write, cause);
		}

		@Override
		public long getStamp() {
			return AbstractBetterMultiMap.this.getStamp();
		}

		@Override
		public String canAdd() {
			String msg = theEntries.keySet().canAdd(theKey, null, null);
			if (msg != null && StdMsg.ELEMENT_EXISTS.equals(msg))
				return null;
			return msg;
		}

		@Override
		public void remove() {
			try (Transaction t = lock(true, null)) {
				if (theId != null && theId.isPresent())
					theEntries.mutableEntry(theId).remove();
				else {
					MapEntryHandle<K, BetterCollection<V>> entry = theEntries.getEntry(theKey);
					if (entry != null)
						theEntries.mutableEntry(entry.getElementId()).remove();
				}
			}
		}
	}

	/**
	 * Implements {@link AbstractBetterMultiMap.ValueCollectionSupplier#createWrapperCollection(ValueCollectionBacking)} for
	 * {@link AbstractBetterMultiMap#LIST_SUPPLIER}
	 * 
	 * @param <E> The type of values in the collection
	 */
	protected static class WrappingBetterList<E> implements BetterList<E> {
		private final ValueCollectionBacking<E> theWrapped;

		/** @param wrapped The backing from the map for the key */
		protected WrappingBetterList(ValueCollectionBacking<E> wrapped) {
			theWrapped = wrapped;
		}

		/** @return The backing from the map for the key */
		protected ValueCollectionBacking<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public int size() {
			BetterCollection<E> wrapped = theWrapped.getBacking(false);
			return wrapped == null ? 0 : wrapped.size();
		}

		@Override
		public boolean isEmpty() {
			BetterCollection<E> wrapped = theWrapped.getBacking(false);
			return wrapped == null ? true : wrapped.isEmpty();
		}

		@Override
		public Object getIdentity() {
			return theWrapped.getIdentity();
		}

		@Override
		public boolean belongs(Object o) {
			return true;
		}

		@Override
		public CollectionElement<E> getElement(E value, boolean first) {
			BetterCollection<E> wrapped = theWrapped.getBacking(false);
			return wrapped == null ? null : wrapped.getElement(value, first);
		}

		@Override
		public CollectionElement<E> getElement(ElementId id) {
			BetterCollection<E> wrapped = theWrapped.getBacking(false);
			if (wrapped == null)
				throw new NoSuchElementException();
			return wrapped.getElement(id);
		}

		@Override
		public CollectionElement<E> getTerminalElement(boolean first) {
			BetterCollection<E> wrapped = theWrapped.getBacking(false);
			return wrapped == null ? null : wrapped.getTerminalElement(first);
		}

		@Override
		public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
			BetterCollection<E> wrapped = theWrapped.getBacking(false);
			if (wrapped == null)
				throw new NoSuchElementException();
			return wrapped.getAdjacentElement(elementId, next);
		}

		@Override
		public MutableCollectionElement<E> mutableElement(ElementId id) {
			BetterCollection<E> wrapped = theWrapped.getBacking(false);
			if (wrapped == null)
				throw new NoSuchElementException();
			MutableCollectionElement<E> wrappedEl = wrapped.mutableElement(id);
			return new MutableCollectionElement<E>() {
				@Override
				public ElementId getElementId() {
					return id;
				}

				@Override
				public E get() {
					return wrappedEl.get();
				}

				@Override
				public BetterCollection<E> getCollection() {
					return WrappingBetterList.this;
				}

				@Override
				public String isEnabled() {
					BetterCollection<E> wrapped2 = theWrapped.getBacking(false);
					if (wrapped2 != wrapped)
						return StdMsg.ELEMENT_REMOVED;
					return wrappedEl.isEnabled();
				}

				@Override
				public String isAcceptable(E value) {
					BetterCollection<E> wrapped2 = theWrapped.getBacking(false);
					if (wrapped2 != wrapped)
						return StdMsg.ELEMENT_REMOVED;
					return wrappedEl.isAcceptable(value);
				}

				@Override
				public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
					BetterCollection<E> wrapped2 = theWrapped.getBacking(false);
					if (wrapped2 != wrapped)
						throw new IllegalStateException(StdMsg.ELEMENT_REMOVED);
					wrappedEl.set(value);
				}

				@Override
				public String canRemove() {
					BetterCollection<E> wrapped2 = theWrapped.getBacking(false);
					if (wrapped2 != wrapped)
						return StdMsg.ELEMENT_REMOVED;
					return wrappedEl.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					try (Transaction t = lock(true, null)) {
						BetterCollection<E> wrapped2 = theWrapped.getBacking(false);
						if (wrapped2 != wrapped)
							throw new IllegalStateException(StdMsg.ELEMENT_REMOVED);
						wrappedEl.remove();
						if (isEmpty())
							theWrapped.remove();
					}
				}
			};
		}

		@Override
		public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			BetterCollection<E> wrapped = theWrapped.getBacking(false);
			return wrapped == null ? BetterList.empty() : wrapped.getElementsBySource(sourceEl, sourceCollection);
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			BetterCollection<E> wrapped = theWrapped.getBacking(false);
			if (wrapped == null)
				throw new NoSuchElementException();
			if (sourceCollection == this)
				return wrapped.getSourceElements(localElement, wrapped);
			return wrapped.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public String canAdd(E value, ElementId after, ElementId before) {
			BetterCollection<E> wrapped = theWrapped.getBacking(false);
			if (wrapped == null) {
				if (after != null || before != null)
					throw new NoSuchElementException();
				return theWrapped.canAdd();
			}
			return wrapped.canAdd(value, after, before);
		}

		@Override
		public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			BetterCollection<E> wrapped = theWrapped.getBacking(true);
			return wrapped.addElement(value, after, before, first);
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			BetterCollection<E> wrapped = theWrapped.getBacking(true);
			return wrapped.canMove(valueEl, after, before);
		}

		@Override
		public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			BetterCollection<E> wrapped = theWrapped.getBacking(true);
			return wrapped.move(valueEl, after, before, first, afterRemove);
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theWrapped.tryLock(write, cause);
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		public CollectionElement<E> getElement(int index) throws IndexOutOfBoundsException {
			BetterCollection<E> wrapped = theWrapped.getBacking(false);
			if (wrapped == null)
				throw new NoSuchElementException();
			return ((BetterList<E>) wrapped).getElement(index);
		}

		@Override
		public boolean isContentControlled() {
			return false;
		}

		@Override
		public int getElementsBefore(ElementId id) {
			BetterCollection<E> wrapped = theWrapped.getBacking(false);
			if (wrapped == null)
				throw new NoSuchElementException();
			return ((BetterList<E>) wrapped).getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			BetterCollection<E> wrapped = theWrapped.getBacking(false);
			if (wrapped == null)
				throw new NoSuchElementException();
			return ((BetterList<E>) wrapped).getElementsAfter(id);
		}

		@Override
		public void clear() {
			try (Transaction t = lock(true, null)) {
				BetterCollection<E> wrapped = theWrapped.getBacking(false);
				if (wrapped == null)
					return;
				wrapped.clear();
				if (wrapped.isEmpty())
					theWrapped.remove();
			}
		}

		@Override
		public int hashCode() {
			return BetterCollection.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterCollection.equals(this, obj);
		}

		@Override
		public String toString() {
			return BetterCollection.toString(this);
		}
	}

	/**
	 * Implements {@link AbstractBetterMultiMap.ValueCollectionSupplier#createWrapperCollection(ValueCollectionBacking)} for
	 * {@link AbstractBetterMultiMap#sortedSupplier(Comparator, boolean) sortedSupplier(false)}
	 * 
	 * @param <E> The type of values in the collection
	 */
	protected static class WrappingSortedList<E> extends WrappingBetterList<E> implements BetterSortedList<E> {
		private final Comparator<? super E> theCompare;

		/**
		 * @param wrapped The backing from the map for the key
		 * @param compare The sorting for the list
		 */
		protected WrappingSortedList(ValueCollectionBacking<E> wrapped, Comparator<? super E> compare) {
			super(wrapped);
			theCompare = compare;
		}

		@Override
		public Comparator<? super E> comparator() {
			return theCompare;
		}

		@Override
		public boolean isConsistent(ElementId element) {
			BetterSortedList<E> wrapped = (BetterSortedList<E>) getWrapped().getBacking(false);
			if (wrapped == null)
				throw new NoSuchElementException();
			return wrapped.isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			BetterSortedList<E> wrapped = (BetterSortedList<E>) getWrapped().getBacking(false);
			return wrapped == null ? false : wrapped.checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
			BetterSortedList<E> wrapped = (BetterSortedList<E>) getWrapped().getBacking(false);
			if (wrapped == null)
				throw new NoSuchElementException();
			return wrapped.repair(element, listener);
		}

		@Override
		public <X> boolean repair(RepairListener<E, X> listener) {
			BetterSortedList<E> wrapped = (BetterSortedList<E>) getWrapped().getBacking(false);
			return wrapped == null ? false : wrapped.repair(listener);
		}

		@Override
		public CollectionElement<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
			BetterSortedList<E> wrapped = (BetterSortedList<E>) getWrapped().getBacking(false);
			return wrapped == null ? null : wrapped.search(search, filter);
		}

		@Override
		public int indexFor(Comparable<? super E> search) {
			BetterSortedList<E> wrapped = (BetterSortedList<E>) getWrapped().getBacking(false);
			return wrapped == null ? -1 : wrapped.indexFor(search);
		}
	}

	/**
	 * Implements {@link AbstractBetterMultiMap.ValueCollectionSupplier#createWrapperCollection(ValueCollectionBacking)} for
	 * {@link AbstractBetterMultiMap#sortedSupplier(Comparator, boolean) sortedSupplier(true)}
	 * 
	 * @param <E> The type of values in the collection
	 */
	protected static class WrappingSortedSet<E> extends WrappingSortedList<E> implements BetterSortedSet<E> {
		/**
		 * @param wrapped The backing from the map for the key
		 * @param compare The sorting for the set
		 */
		protected WrappingSortedSet(ValueCollectionBacking<E> wrapped, Comparator<? super E> compare) {
			super(wrapped, compare);
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return super.toArray(a);
		}
	}
}
