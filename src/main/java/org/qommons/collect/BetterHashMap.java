package org.qommons.collect;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A hash-based implementation of {@link BetterMap}
 * 
 * @param <K> The type of keys for the map
 * @param <V> The type of values for the map
 */
public class BetterHashMap<K, V> extends AbstractIdentifiable implements BetterMap<K, V> {
	/**
	 * Builds a {@link BetterHashMap}
	 * 
	 * @param <B> The sub-type of this builder
	 */
	public static class HashMapBuilder<B extends HashMapBuilder<? extends B>> implements CollectionBuilder<B> {
		private final BetterHashSet.HashSetBuilder<?> theSetBuilder;

		HashMapBuilder() {
			theSetBuilder = BetterHashSet.build().withDescription("better-hash-map");
			withEquivalence(Objects::hashCode, Objects::equals);
		}

		/**
		 * Causes this builder to build a map whose hash and comparison are defined externally
		 * 
		 * @param hasher The hash function for values in the map
		 * @param equals The equivalence check for values in the map
		 * @return This builder
		 */
		public B withEquivalence(ToIntFunction<Object> hasher, BiPredicate<Object, Object> equals) {
			theSetBuilder.withEquivalence(//
				entry -> {
					if (entry instanceof Map.Entry)
						return hasher.applyAsInt(((Map.Entry<?, ?>) entry).getKey());
					else
						return hasher.applyAsInt(entry);
				}, (entry1, entry2) -> {
					if (entry1 instanceof Map.Entry) {
						if (entry2 instanceof Map.Entry)
							return equals.test(((Map.Entry<?, ?>) entry1).getKey(), ((Map.Entry<?, ?>) entry2).getKey());
						else
							return equals.test(((Map.Entry<?, ?>) entry1).getKey(), entry2);
					} else if (entry2 instanceof Map.Entry)
						return equals.test(entry1, ((Map.Entry<?, ?>) entry2).getKey());
					else
						return equals.test(entry1, entry2);
				});
			return (B) this;
		}

		/**
		 * Causes this builder to build a set whose values are stored by identity, instead of notional equivalence
		 * 
		 * @return This builder
		 */
		public B identity() {
			return withEquivalence(System::identityHashCode, (o1, o2) -> o1 == o2);
		}

		/**
		 * @param loadFactor The load factor for the set that this builder creates
		 * @return This builder
		 */
		public B withLoadFactor(double loadFactor) {
			theSetBuilder.withLoadFactor(loadFactor);
			return (B) this;
		}

		/**
		 * @param initExpectedSize The number of values that the set created by this builder should accommodate without re-hashing the table
		 * @return This builder
		 */
		public B withInitialCapacity(int initExpectedSize) {
			theSetBuilder.withInitialCapacity(initExpectedSize);
			return (B) this;
		}

		@Override
		public B withDescription(String descrip) {
			theSetBuilder.withDescription(descrip);
			return (B) this;
		}

		@Override
		public String getDescription() {
			return theSetBuilder.getDescription();
		}

		@Override
		public B withThreadConstraint(ThreadConstraint threadConstraint) {
			theSetBuilder.withThreadConstraint(threadConstraint);
			return (B) this;
		}

		@Override
		public B withCollectionLocking(Function<Object, CollectionLockingStrategy> locker) {
			theSetBuilder.withCollectionLocking(locker);
			return (B) this;
		}

		/**
		 * @param <K> The key type for the map
		 * @param <V> The value type for the map
		 * @return The new map
		 */
		public <K, V> BetterHashMap<K, V> build() {
			return build(null);
		}

		/**
		 * @param <K> The key type for the map
		 * @param <V> The value type for the map
		 * @param values The initial key-value pairs to insert into the map
		 * @return A {@link BetterHashMap} built according to this builder's settings, with the given initial content
		 */
		public <K, V> BetterHashMap<K, V> build(Map<? extends K, ? extends V> values) {
			return new BetterHashMap<>(theSetBuilder, values);
		}
	}

	/** @return A builder to create a new {@link BetterHashMap} */
	public static HashMapBuilder<?> build() {
		return new HashMapBuilder<>();
	}

	/**
	 * @param <K> The type of keys for the map
	 * @param <V> The type of values for the map
	 * @return The new hash map
	 */
	public static <K, V> BetterHashMap<K, V> create() {
		return BetterHashMap.build().build();
	}

	/**
	 * @param <K> The type of keys for the map
	 * @param <V> The type of values for the map
	 * @param build Optional configuration for the new hash map
	 * @return The new hash map
	 */
	public static <K, V> BetterHashMap<K, V> create(Consumer<? super HashMapBuilder<?>> build) {
		HashMapBuilder<?> builder = build();
		if (build != null)
			build.accept(builder);
		return builder.build();
	}

	private final BetterHashSet<Entry> theEntries;
	private final KeySet theKeySet;

	private BetterHashMap(BetterHashSet.HashSetBuilder<?> entryBuilder, Map<? extends K, ? extends V> values) {
		theEntries = entryBuilder.build(values == null ? null : values.entrySet().stream()//
			.<Entry> map(entry -> newEntry(entry.getKey(), entry.getValue())).collect(Collectors.toSet()));
		if (values != null) {
			for (CollectionElement<Entry> entry : theEntries.elements())
				entry.get().setElement(entry);
		}
		theKeySet = new KeySet();
	}

	/**
	 * @param capacity The minimum capacity for this map
	 * @return Whether the map was rebuilt
	 */
	public boolean ensureCapacity(int capacity) {
		return theEntries.ensureCapacity(capacity);
	}

	/**
	 * @return The efficiency of this hash map
	 * @see BetterHashSet#getEfficiency()
	 */
	public double getEfficiency() {
		return theEntries.getEfficiency();
	}

	@Override
	protected Object createIdentity() {
		return theEntries.getIdentity();
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theEntries.getThreadConstraint();
	}

	@Override
	public BetterSet<K> keySet() {
		return theKeySet;
	}

	/**
	 * @param key The key for the entry
	 * @param value The initial value for the entry
	 * @return The map entry for the key to use in this map
	 */
	protected Entry newEntry(K key, V value) {
		return new Entry(key, value);
	}

	@Override
	public MapEntryHandle<K, V> putEntry(K key, V value, boolean first) {
		try (Transaction t = theEntries.lockWrite(false, null)) {
			Entry newEntry = newEntry(key, value);
			CollectionElement<Entry> entryEl = theEntries.getElement(newEntry, true);
			if (entryEl != null) {
				entryEl.get().mutable().setValue(value);
			} else {
				entryEl = theEntries.addElement(newEntry, first);
				entryEl.get().setElement(entryEl);
			}
			return handleFor(entryEl);
		}
	}

	@Override
	public MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
		try (Transaction t = theEntries.lockWrite(false, null)) {
			Entry newEntry = newEntry(key, value);
			CollectionElement<Entry> entryEl = theEntries.getElement(newEntry, true);
			if (entryEl != null) {
				entryEl.get().mutable().setValue(value);
			} else {
				entryEl = theEntries.addElement(newEntry, after, before, first);
				entryEl.get().setElement(entryEl);
			}
			return handleFor(entryEl);
		}
	}

	@Override
	public MapEntryHandle<K, V> getEntry(K key) {
		CollectionElement<Entry> entryEl = theEntries.getElement(theEntries.getHasher().applyAsInt(key),
			entry -> theEntries.getEquals().test(entry.getKey(), key));
		return entryEl == null ? null : handleFor(entryEl);
	}

	@Override
	public MapEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
		boolean first, Runnable preAdd, Runnable postAdd) {
		CollectionElement<Entry> entryEl = theEntries.getOrAdd(//
			theEntries.getHasher().applyAsInt(key), entry -> theEntries.getEquals().test(entry.getKey(), key), //
			() -> {
				V newValue = value.apply(key);
				return newEntry(key, newValue);
			}, after, before, first, preAdd, postAdd);
		if (entryEl == null)
			return null;
		return handleFor(entryEl);
	}

	@Override
	public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
		return handleFor(theEntries.getElement(entryId));
	}

	@Override
	public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
		return mutableHandleFor(theEntries.mutableElement(entryId));
	}

	@Override
	public String canPut(K key, V value) {
		if (containsKey(key))
			return StdMsg.ELEMENT_EXISTS;
		else
			return null;
	}

	/**
	 * Checks an element ID for validity as a key ID in this map
	 * 
	 * @param elementId The ID to check
	 * @return Whether the id is valid as a key ID in this map
	 */
	public boolean isValid(ElementId elementId) {
		return theEntries.isValid(elementId);
	}

	/**
	 * @param entry The element in the entry set
	 * @return The map handle for the entry
	 */
	protected MapEntryHandle<K, V> handleFor(CollectionElement<? extends Entry> entry) {
		if (entry == null)
			return null;
		Entry e = entry.get();
		e.setElement(entry);
		return e;
	}

	/**
	 * @param entry The mutable element in the entry set
	 * @return The mutable map handle for the entry
	 */
	protected MutableMapEntryHandle<K, V> mutableHandleFor(MutableCollectionElement<? extends Map.Entry<K, V>> entry) {
		return entry == null ? null : ((Entry) entry.get()).mutable();
	}

	@Override
	public String toString() {
		return entrySet().toString();
	}

	/** Default Map entry implementation for this class */
	protected class Entry extends BetterMapEntryImpl<K, V> {
		Entry(K key, V value) {
			super(key, value);
		}

		void setElement(CollectionElement<? extends Entry> element) {
			theElement = element;
		}

		MutableMapEntryHandle<K, V> mutable() {
			return super.mutable(theEntries, BetterHashMap.this::values);
		}

		@Override
		protected CollectionElement<K> keyHandle() {
			return super.keyHandle();
		}

		MutableCollectionElement<K> mutableKeyHandle() {
			return mutableKeyHandle(theEntries, BetterHashMap.this::keySet);
		}
	}

	class KeySet extends AbstractIdentifiable implements BetterSet<K> {
		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(BetterHashMap.this.getIdentity(), "keySet");
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return BetterHashMap.this.getThreadConstraint();
		}

		@Override
		public Transaction lock(boolean tryOnly) {
			return theEntries.lock(tryOnly);
		}

		@Override
		public Transaction lockWrite(boolean tryOnly, Object cause) {
			return theEntries.lockWrite(tryOnly, cause);
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return theEntries.getCurrentCauses();
		}

		@Override
		public CoreId getCoreId() {
			return theEntries.getCoreId();
		}

		@Override
		public long getStamp() {
			return theEntries.getStamp();
		}

		@Override
		public int size() {
			return theEntries.size();
		}

		@Override
		public boolean isEmpty() {
			return theEntries.isEmpty();
		}

		@Override
		public Object[] toArray() {
			return BetterSet.super.toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return BetterSet.super.toArray(a);
		}

		protected CollectionElement<K> handleFor(CollectionElement<? extends Map.Entry<K, V>> entryEl) {
			return entryEl == null ? null : ((Entry) entryEl.get()).keyHandle();
		}

		protected MutableCollectionElement<K> mutableHandleFor(MutableCollectionElement<? extends Map.Entry<K, V>> entryEl) {
			return entryEl == null ? null : ((Entry) entryEl.get()).mutableKeyHandle();
		}

		@Override
		public CollectionElement<K> getTerminalElement(boolean first) {
			return handleFor(theEntries.getTerminalElement(first));
		}

		@Override
		public CollectionElement<K> getElement(K value, boolean first) {
			CollectionElement<Entry> entryEl = theEntries.getElement(theEntries.getHasher().applyAsInt(value),
				entry -> theEntries.getEquals().test(entry, value));
			return entryEl == null ? null : handleFor(entryEl);
		}

		@Override
		public CollectionElement<K> getElement(ElementId id) {
			return handleFor(theEntries.getElement(id));
		}

		@Override
		public CollectionElement<K> getOrAdd(K value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
			CollectionElement<Entry> entryEl = theEntries.getOrAdd(newEntry(value, null), after, before, first, preAdd, postAdd);
			return entryEl == null ? null : handleFor(entryEl);
		}

		@Override
		public MutableCollectionElement<K> mutableElement(ElementId id) {
			return mutableHandleFor(theEntries.mutableElement(id));
		}

		@Override
		public BetterList<CollectionElement<K>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return QommonsUtils.map2(theEntries.getElementsBySource(sourceEl, sourceCollection), this::handleFor);
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return theEntries.getSourceElements(localElement, theEntries); // Validate element
			return theEntries.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			return theEntries.getEquivalentElement(equivalentEl);
		}

		@Override
		public String canAdd(K value, ElementId after, ElementId before) {
			return theEntries.canAdd(newEntry(value, null), after, before);
		}

		@Override
		public CollectionElement<K> addElement(K value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return handleFor(theEntries.addElement(newEntry(value, null), after, before, first));
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return theEntries.canMove(valueEl, after, before);
		}

		@Override
		public CollectionElement<K> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			CollectionElement<Entry> entry = theEntries.move(valueEl, after, before, first, afterRemove);
			if (entry.getElementId().equals(valueEl))
				return getElement(valueEl);
			Entry newEntry = newEntry(entry.get().getKey(), entry.get().getValue());
			newEntry.setElement(entry);
			theEntries.mutableElement(entry.getElementId()).set(newEntry);
			return newEntry.keyHandle();
		}

		@Override
		public void clear() {
			theEntries.clear();
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return theEntries.isConsistent(element);
		}

		@Override
		public boolean checkConsistency() {
			return theEntries.checkConsistency();
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<K, X> listener) {
			RepairListener<Entry, X> entryListener = listener == null ? null : new EntryRepairListener<>(listener);
			return theEntries.repair(element, entryListener);
		}

		@Override
		public <X> boolean repair(RepairListener<K, X> listener) {
			RepairListener<Entry, X> entryListener = listener == null ? null : new EntryRepairListener<>(listener);
			return theEntries.repair(entryListener);
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
			return BetterSet.toString(this);
		}

		private class EntryRepairListener<X> implements RepairListener<Entry, X> {
			private final RepairListener<K, X> theKeyListener;

			EntryRepairListener(org.qommons.collect.ValueStoredCollection.RepairListener<K, X> keyListener) {
				theKeyListener = keyListener;
			}

			@Override
			public X removed(CollectionElement<Entry> element) {
				return theKeyListener.removed(handleFor(element));
			}

			@Override
			public void disposed(Entry value, X data) {
				theKeyListener.disposed(value.getKey(), data);
			}

			@Override
			public void transferred(CollectionElement<Entry> element, X data) {
				theKeyListener.transferred(handleFor(element), data);
			}
		}
	}
}
