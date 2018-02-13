package org.qommons.collect;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import org.qommons.Transaction;

/**
 * A hash-based implementation of {@link BetterMap}
 * 
 * @param <K> The type of keys for the map
 * @param <V> The type of values for the map
 */
public class BetterHashMap<K, V> implements BetterMap<K, V> {
	/** Builds a {@link BetterHashMap} */
	public static class HashMapBuilder extends BetterHashSet.HashSetBuilder {
		@Override
		public HashMapBuilder unsafe() {
			return (HashMapBuilder) super.unsafe();
		}

		@Override
		public HashMapBuilder withEquivalence(ToIntFunction<Object> hasher, BiFunction<Object, Object, Boolean> equals) {
			return (HashMapBuilder) super.withEquivalence(//
				entry -> hasher.applyAsInt(((Map.Entry<?, ?>) entry).getKey()), //
				(entry1, entry2) -> equals.apply(((Map.Entry<?, ?>) entry1).getKey(), ((Map.Entry<?, ?>) entry2).getKey())//
			);
		}

		@Override
		public HashMapBuilder identity() {
			return (HashMapBuilder) super.identity();
		}

		@Override
		public HashMapBuilder withLoadFactor(double loadFactor) {
			return (HashMapBuilder) super.withLoadFactor(loadFactor);
		}

		@Override
		public HashMapBuilder withInitialCapacity(int initExpectedSize) {
			return (HashMapBuilder) super.withInitialCapacity(initExpectedSize);
		}

		/**
		 * @param <K> The key type for the map
		 * @param <V> The value type for the map
		 * @return The new map
		 */
		public <K, V> BetterHashMap<K, V> buildMap() {
			return new BetterHashMap<>(buildSet());
		}

		/**
		 * @param <K> The key type for the map
		 * @param <V> The value type for the map
		 * @param values The initial key-value pairs to insert into the map
		 * @return A {@link BetterHashMap} built according to this builder's settings, with the given initial content
		 */
		public <K, V> BetterHashMap<K, V> buildMap(Map<? extends K, ? extends V> values) {
			BetterHashMap<K, V> map = new BetterHashMap<>(buildSet());
			map.putAll(values);
			return map;
		}
	}

	/** @return A builder to create a new {@link BetterHashMap} */
	public static HashMapBuilder build() {
		return new HashMapBuilder();
	}

	private final BetterHashSet<Map.Entry<K, V>> theEntries;

	private BetterHashMap(BetterHashSet<Map.Entry<K, V>> entries) {
		theEntries = entries;
	}

	@Override
	public BetterSet<K> keySet() {
		return new KeySet();
	}

	/**
	 * @param key The key for the entry
	 * @param value The initial value for the entry
	 * @return The map entry for the key to use in this map
	 */
	public Map.Entry<K, V> newEntry(K key, V value) {
		return new SimpleMapEntry<>(key, value, true);
	}

	@Override
	public MapEntryHandle<K, V> putEntry(K key, V value, boolean first) {
		try (Transaction t = theEntries.lock(true, null)) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getElement(new SimpleMapEntry<>(key, value), true);
			if (entryEl != null) {
				entryEl.get().setValue(value);
			} else
				entryEl = theEntries.addElement(newEntry(key, value), first);
			return handleFor(entryEl);
		}
	}

	@Override
	public MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
		try (Transaction t = theEntries.lock(true, null)) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getElement(new SimpleMapEntry<>(key, value), true);
			if (entryEl != null) {
				entryEl.get().setValue(value);
			} else
				entryEl = theEntries.addElement(newEntry(key, value), after, before, first);
			return handleFor(entryEl);
		}
	}

	@Override
	public MapEntryHandle<K, V> getEntry(K key) {
		try (Transaction t = theEntries.lock(false, null)) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getElement(new SimpleMapEntry<>(key, null), true);
			return entryEl == null ? null : handleFor(entryEl);
		}
	}

	@Override
	public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
		return handleFor(theEntries.getElement(entryId));
	}

	@Override
	public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
		return mutableHandleFor(theEntries.mutableElement(entryId));
	}

	/**
	 * @param entry The element in the entry set
	 * @return The map handle for the entry
	 */
	protected MapEntryHandle<K, V> handleFor(CollectionElement<? extends Map.Entry<K, V>> entry) {
		return new MapEntryHandle<K, V>() {
			@Override
			public ElementId getElementId() {
				return entry.getElementId();
			}

			@Override
			public K getKey() {
				return entry.get().getKey();
			}

			@Override
			public V get() {
				return entry.get().getValue();
			}

			@Override
			public int hashCode() {
				return entry.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return entry.equals(obj);
			}

			@Override
			public String toString() {
				return entry.toString();
			}
		};
	}

	/**
	 * @param entry The mutable element in the entry set
	 * @return The mutable map handle for the entry
	 */
	protected MutableMapEntryHandle<K, V> mutableHandleFor(MutableCollectionElement<? extends Map.Entry<K, V>> entry) {
		return new MutableMapEntryHandle<K, V>() {
			@Override
			public BetterCollection<V> getCollection() {
				return values();
			}

			@Override
			public ElementId getElementId() {
				return entry.getElementId();
			}

			@Override
			public K getKey() {
				return entry.get().getKey();
			}

			@Override
			public V get() {
				return entry.get().getValue();
			}

			@Override
			public String isEnabled() {
				return null;
			}

			@Override
			public String isAcceptable(V value) {
				return null;
			}

			@Override
			public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
				((Map.Entry<K, V>) entry.get()).setValue(value);
			}

			@Override
			public String canRemove() {
				return entry.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				entry.remove();
			}

			@Override
			public String canAdd(V value, boolean before) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public ElementId add(V value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public int hashCode() {
				return entry.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return entry.equals(obj);
			}

			@Override
			public String toString() {
				return entry.toString();
			}
		};
	}

	@Override
	public String toString() {
		return entrySet().toString();
	}

	class KeySet implements BetterSet<K> {
		@Override
		public boolean belongs(Object o) {
			return true;
		}

		@Override
		public boolean isLockSupported() {
			return theEntries.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theEntries.lock(write, structural, cause);
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theEntries.getStamp(structuralOnly);
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
			if (entryEl == null)
				return null;
			return new CollectionElement<K>() {
				@Override
				public ElementId getElementId() {
					return entryEl.getElementId();
				}

				@Override
				public K get() {
					return entryEl.get().getKey();
				}
			};
		}

		protected MutableCollectionElement<K> mutableHandleFor(MutableCollectionElement<? extends Map.Entry<K, V>> entryEl) {
			return new MutableCollectionElement<K>() {
				@Override
				public BetterCollection<K> getCollection() {
					return KeySet.this;
				}

				@Override
				public ElementId getElementId() {
					return entryEl.getElementId();
				}

				@Override
				public K get() {
					return entryEl.get().getKey();
				}

				@Override
				public String isEnabled() {
					return entryEl.isEnabled();
				}

				@Override
				public String isAcceptable(K value) {
					return ((MutableCollectionElement<Map.Entry<K, V>>) entryEl).isAcceptable(new SimpleMapEntry<>(value, null));
				}

				@Override
				public void set(K value) throws UnsupportedOperationException, IllegalArgumentException {
					((MutableCollectionElement<Map.Entry<K, V>>) entryEl).set(new SimpleMapEntry<>(value, entryEl.get().getValue()));
				}

				@Override
				public String canRemove() {
					return entryEl.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					entryEl.remove();
				}
			};
		}

		@Override
		public CollectionElement<K> getTerminalElement(boolean first) {
			return handleFor(theEntries.getTerminalElement(first));
		}

		@Override
		public CollectionElement<K> getAdjacentElement(ElementId elementId, boolean next) {
			return handleFor(theEntries.getAdjacentElement(elementId, next));
		}

		@Override
		public CollectionElement<K> getElement(K value, boolean first) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getElement(new SimpleMapEntry<>(value, null), first);
			return entryEl == null ? null : handleFor(entryEl);
		}

		@Override
		public CollectionElement<K> getElement(ElementId id) {
			return handleFor(theEntries.getElement(id));
		}

		@Override
		public MutableCollectionElement<K> mutableElement(ElementId id) {
			return mutableHandleFor(theEntries.mutableElement(id));
		}

		@Override
		public MutableElementSpliterator<K> spliterator(ElementId element, boolean asNext) {
			return new KeySpliterator(theEntries.spliterator(element, asNext));
		}

		@Override
		public boolean forElement(K value, Consumer<? super CollectionElement<K>> onElement, boolean first) {
			return theEntries.forElement(new SimpleMapEntry<>(value, null), entryEl -> onElement.accept(handleFor(entryEl)), first);
		}

		@Override
		public boolean forMutableElement(K value, Consumer<? super MutableCollectionElement<K>> onElement, boolean first) {
			return theEntries.forMutableElement(new SimpleMapEntry<>(value, null), entryEl -> onElement.accept(mutableHandleFor(entryEl)),
				first);
		}

		@Override
		public MutableElementSpliterator<K> spliterator(boolean fromStart) {
			return new KeySpliterator(theEntries.spliterator(fromStart));
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
		public void clear() {
			theEntries.clear();
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

		private class KeySpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<K> {
			private final MutableElementSpliterator<Map.Entry<K, V>> theEntrySpliter;

			KeySpliterator(MutableElementSpliterator<Map.Entry<K, V>> entrySpliter) {
				super(KeySet.this);
				theEntrySpliter = entrySpliter;
			}

			@Override
			public long estimateSize() {
				return theEntrySpliter.estimateSize();
			}

			@Override
			public long getExactSizeIfKnown() {
				return theEntrySpliter.getExactSizeIfKnown();
			}

			@Override
			public int characteristics() {
				return theEntrySpliter.characteristics();
			}

			@Override
			protected boolean internalForElement(Consumer<? super CollectionElement<K>> action, boolean forward) {
				return theEntrySpliter.forElement(el -> action.accept(handleFor(el)), forward);
			}

			@Override
			protected boolean internalForElementM(Consumer<? super MutableCollectionElement<K>> action, boolean forward) {
				return theEntrySpliter.forElementM(el -> action.accept(mutableHandleFor(el)), forward);
			}

			@Override
			public MutableElementSpliterator<K> trySplit() {
				MutableElementSpliterator<Map.Entry<K, V>> entrySplit = theEntrySpliter.trySplit();
				return entrySplit == null ? null : new KeySpliterator(entrySplit);
			}
		}
	}
}
