package org.qommons.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public interface BetterMap<K, V> extends TransactableMap<K, V> {
	@Override
	BetterSet<K> keySet();

	@Override
	default BetterSet<Map.Entry<K, V>> entrySet() {
		return new BetterEntrySet<>(this);
	}

	@Override
	default BetterCollection<V> values() {
		return new BetterMapValueCollection<>(this);
	}

	@Override
	default boolean isLockSupported() {
		return keySet().isLockSupported();
	}

	@Override
	default Transaction lock(boolean write, Object cause) {
		return keySet().lock(write, cause);
	}

	ElementId putEntry(K key, V value);

	boolean forEntry(K key, Consumer<? super MapEntryHandle<K, V>> onEntry);

	boolean forMutableEntry(K key, Consumer<? super MutableMapEntryHandle<K, V>> onEntry);

	<X> X ofEntry(ElementId entryId, Function<? super MapEntryHandle<K, V>, X> onEntry);

	<X> X ofMutableEntry(ElementId entryId, Function<? super MutableMapEntryHandle<K, V>, X> onEntry);

	default void forEntry(ElementId entryId, Consumer<? super MapEntryHandle<K, V>> onEntry) {
		ofEntry(entryId, entry -> {
			onEntry.accept(entry);
			return null;
		});
	}

	default void forMutableEntry(ElementId entryId, Consumer<? super MutableMapEntryHandle<K, V>> onEntry) {
		ofMutableEntry(entryId, entry -> {
			onEntry.accept(entry);
			return null;
		});
	}

	default BetterMap<K, V> reverse() {
		return new ReversedMap<>(this);
	}

	@Override
	default int size() {
		return keySet().size();
	}

	@Override
	default boolean isEmpty() {
		return keySet().isEmpty();
	}

	@Override
	default boolean containsKey(Object key) {
		return keySet().contains(key);
	}

	@Override
	default boolean containsValue(Object value) {
		return values().contains(value);
	}

	@Override
	default V get(Object key) {
		if (!keySet().belongs(key))
			return null;
		Object[] value = new Object[1];
		if (!forEntry((K) key, entry -> value[0] = entry.get()))
			return null;
		return (V) value[0];
	}

	@Override
	default V remove(Object key) {
		if (!keySet().belongs(key))
			return null;
		Object[] value = new Object[1];
		if (!forMutableEntry((K) key, entry -> {
			value[0] = entry.get();
			entry.remove();
		}))
			return null;
		return (V) value[0];
	}

	@Override
	default V put(K key, V value) {
		Object[] old = new Object[1];
		if (forMutableEntry(key, entry -> old[0] = entry.setValue(value)))
			return (V) old[0];
		else {
			putEntry(key, value);
			return null;
		}
	}

	@Override
	default void putAll(Map<? extends K, ? extends V> m) {
		try (Transaction t = lock(true, null); Transaction ct = Transactable.lock(m, false, null)) {
			for (Map.Entry<? extends K, ? extends V> entry : m.entrySet())
				put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	default void clear() {
		keySet().clear();
	}

	class ReversedMap<K, V> implements BetterMap<K, V> {
		private final BetterMap<K, V> theWrapped;

		public ReversedMap(BetterMap<K, V> wrapped) {
			theWrapped = wrapped;
		}

		protected BetterMap<K, V> getWrapped() {
			return theWrapped;
		}

		@Override
		public BetterSet<K> keySet() {
			return theWrapped.keySet().reverse();
		}

		@Override
		public ElementId putEntry(K key, V value) {
			return ElementId.reverse(theWrapped.putEntry(key, value));
		}

		@Override
		public boolean forEntry(K key, Consumer<? super MapEntryHandle<K, V>> onElement) {
			return theWrapped.forEntry(key, el -> onElement.accept(el.reverse()));
		}

		@Override
		public boolean forMutableEntry(K key, Consumer<? super MutableMapEntryHandle<K, V>> onElement) {
			return theWrapped.forMutableEntry(key, el -> onElement.accept(el.reverse()));
		}

		@Override
		public <X> X ofEntry(ElementId entryId, Function<? super MapEntryHandle<K, V>, X> onElement) {
			return theWrapped.ofEntry(entryId.reverse(), el -> onElement.apply(el.reverse()));
		}

		@Override
		public <X> X ofMutableEntry(ElementId entryId, Function<? super MutableMapEntryHandle<K, V>, X> onElement) {
			return theWrapped.ofMutableEntry(entryId.reverse(), el -> onElement.apply(el.reverse()));
		}
	}

	class BetterEntrySet<K, V> implements BetterSet<Map.Entry<K, V>> {
		private final BetterMap<K, V> theMap;

		public BetterEntrySet(BetterMap<K, V> map) {
			theMap = map;
		}

		protected BetterMap<K, V> getMap() {
			return theMap;
		}

		@Override
		public boolean belongs(Object o) {
			return o instanceof Map.Entry && theMap.keySet().belongs(((Map.Entry<?, ?>) o).getKey());
		}

		@Override
		public boolean isLockSupported() {
			return theMap.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theMap.lock(write, cause);
		}

		@Override
		public int size() {
			return theMap.size();
		}

		@Override
		public boolean isEmpty() {
			return theMap.isEmpty();
		}

		@Override
		public Object[] toArray() {
			return BetterSet.super.toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return BetterSet.super.toArray(a);
		}

		@Override
		public boolean forElement(Map.Entry<K, V> value, Consumer<? super CollectionElement<? extends Map.Entry<K, V>>> onElement,
			boolean first) {
			return theMap.forEntry(value.getKey(), entry -> onElement.accept(handleFor(entry)));
		}

		@Override
		public boolean forMutableElement(Map.Entry<K, V> value, Consumer<? super MutableCollectionElement<? extends Map.Entry<K, V>>> onElement,
			boolean first) {
			return theMap.forMutableEntry(value.getKey(), entry -> onElement.accept(mutableHandleFor(entry)));
		}

		@Override
		public <T> T ofElementAt(ElementId elementId, Function<? super CollectionElement<? extends Map.Entry<K, V>>, T> onElement) {
			return theMap.ofEntry(elementId, entry -> onElement.apply(handleFor(entry)));
		}

		@Override
		public <T> T ofMutableElementAt(ElementId elementId,
			Function<? super MutableCollectionElement<? extends Map.Entry<K, V>>, T> onElement) {
			return theMap.ofMutableEntry(elementId, entry -> onElement.apply(mutableHandleFor(entry)));
		}

		@Override
		public MutableElementSpliterator<Map.Entry<K, V>> mutableSpliterator(boolean fromStart) {
			return wrap(theMap.keySet().mutableSpliterator(fromStart));
		}

		protected CollectionElement<Map.Entry<K, V>> handleFor(MapEntryHandle<K, V> entry) {
			return new CollectionElement<Map.Entry<K, V>>() {
				@Override
				public ElementId getElementId() {
					return entry.getElementId();
				}

				@Override
				public Map.Entry<K, V> get() {
					return new Map.Entry<K, V>() {
						@Override
						public K getKey() {
							return entry.getKey();
						}

						@Override
						public V getValue() {
							return entry.get();
						}

						@Override
						public V setValue(V value) {
							throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
						}
					};
				}
			};
		}

		protected MutableCollectionElement<Map.Entry<K, V>> mutableHandleFor(MutableMapEntryHandle<K, V> entry) {
			return new MutableCollectionElement<Map.Entry<K, V>>() {
				@Override
				public ElementId getElementId() {
					return entry.getElementId();
				}

				@Override
				public Map.Entry<K, V> get() {
					return entry;
				}

				@Override
				public String isEnabled() {
					return entry.isEnabled();
				}

				@Override
				public String isAcceptable(Map.Entry<K, V> value) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public void set(Map.Entry<K, V> value) throws UnsupportedOperationException, IllegalArgumentException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
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
				public String canAdd(Map.Entry<K, V> value, boolean before) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public ElementId add(Map.Entry<K, V> value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}
			};
		}

		@Override
		public String canAdd(Map.Entry<K, V> value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ElementId addElement(Map.Entry<K, V> value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public boolean addAll(Collection<? extends java.util.Map.Entry<K, V>> c) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			theMap.clear();
		}

		protected MutableElementSpliterator<Map.Entry<K, V>> wrap(MutableElementSpliterator<K> keySpliter) {
			return new EntrySpliterator(keySpliter);
		}

		class EntrySpliterator implements MutableElementSpliterator<Map.Entry<K, V>> {
			private final MutableElementSpliterator<K> theKeySpliter;

			EntrySpliterator(MutableElementSpliterator<K> keySpliter) {
				theKeySpliter = keySpliter;
			}

			@Override
			public long estimateSize() {
				return theKeySpliter.estimateSize();
			}

			@Override
			public long getExactSizeIfKnown() {
				return theKeySpliter.getExactSizeIfKnown();
			}

			@Override
			public Comparator<? super Entry<K, V>> getComparator() {
				Comparator<? super K> keyCompare = theKeySpliter.getComparator();
				if (keyCompare == null)
					return null;
				return (entry1, entry2) -> keyCompare.compare(entry1.getKey(), entry2.getKey());
			}

			@Override
			public int characteristics() {
				return theKeySpliter.characteristics();
			}

			@Override
			public boolean tryAdvanceElement(Consumer<? super CollectionElement<Map.Entry<K, V>>> action) {
				return theKeySpliter.tryAdvance(key -> theMap.forEntry(key, entry -> action.accept(handleFor(entry))));
			}

			@Override
			public boolean tryReverseElement(Consumer<? super CollectionElement<Map.Entry<K, V>>> action) {
				return theKeySpliter.tryReverse(key -> theMap.forEntry(key, entry -> action.accept(handleFor(entry))));
			}

			@Override
			public boolean tryAdvanceElementM(Consumer<? super MutableCollectionElement<Map.Entry<K, V>>> action) {
				// Using the mutable version on the key spliterator to avoid problems upgrading a read to a write lock
				return theKeySpliter
					.tryAdvanceElementM(keyEl -> theMap.forMutableEntry(keyEl.get(), entry -> action.accept(mutableHandleFor(entry))));
			}

			@Override
			public boolean tryReverseElementM(Consumer<? super MutableCollectionElement<Map.Entry<K, V>>> action) {
				// Using the mutable version on the key spliterator to avoid problems upgrading a read to a write lock
				return theKeySpliter
					.tryReverseElementM(keyEl -> theMap.forMutableEntry(keyEl.get(), entry -> action.accept(mutableHandleFor(entry))));
			}

			@Override
			public MutableElementSpliterator<Map.Entry<K, V>> trySplit() {
				MutableElementSpliterator<K> keySplit = theKeySpliter.trySplit();
				return keySplit == null ? null : new EntrySpliterator(keySplit);
			}
		}
	}

	class BetterMapValueCollection<K, V> implements BetterCollection<V> {
		private final BetterMap<K, V> theMap;

		public BetterMapValueCollection(BetterMap<K, V> map) {
			theMap = map;
		}

		protected BetterMap<K, V> getMap() {
			return theMap;
		}

		@Override
		public boolean isLockSupported() {
			return theMap.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theMap.lock(write, cause);
		}

		@Override
		public boolean belongs(Object o) {
			return true; // I guess?
		}

		@Override
		public int size() {
			return theMap.size();
		}

		@Override
		public boolean isEmpty() {
			return theMap.isEmpty();
		}

		@Override
		public String canAdd(V value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ElementId addElement(V value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public boolean addAll(Collection<? extends V> c) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			theMap.clear();
		}

		@Override
		public boolean forElement(V value, Consumer<? super CollectionElement<? extends V>> onElement, boolean first) {
			try (Transaction t = lock(false, null)) {
				ElementSpliterator<V> spliter = first ? spliterator(first) : spliterator(first).reverse();
				boolean[] success = new boolean[1];
				while (success[0] && spliter.tryAdvanceElement(el -> {
					if (Objects.equals(el.get(), value)) {
						success[0] = true;
						onElement.accept(el);
					}
				})) {
				}
				return success[0];
			}
		}

		@Override
		public boolean forMutableElement(V value, Consumer<? super MutableCollectionElement<? extends V>> onElement, boolean first) {
			try (Transaction t = lock(false, null)) {
				MutableElementSpliterator<V> spliter = first ? mutableSpliterator(first) : mutableSpliterator(first).reverse();
				boolean[] success = new boolean[1];
				while (success[0] && spliter.tryAdvanceElementM(el -> {
					if (Objects.equals(el.get(), value)) {
						success[0] = true;
						onElement.accept(el);
					}
				})) {
				}
				return success[0];
			}
		}

		@Override
		public <T> T ofElementAt(ElementId elementId, Function<? super CollectionElement<? extends V>, T> onElement) {
			return theMap.ofEntry(elementId, entry -> onElement.apply(entry));
		}

		@Override
		public <T> T ofMutableElementAt(ElementId elementId, Function<? super MutableCollectionElement<? extends V>, T> onElement) {
			return theMap.ofMutableEntry(elementId, entry -> onElement.apply(entry));
		}

		@Override
		public MutableElementSpliterator<V> mutableSpliterator(boolean fromStart) {
			class ValueSpliterator implements MutableElementSpliterator<V> {
				private final MutableElementSpliterator<K> theKeySpliter;

				ValueSpliterator(MutableElementSpliterator<K> keySpliter) {
					theKeySpliter = keySpliter;
				}

				@Override
				public long estimateSize() {
					return theKeySpliter.estimateSize();
				}

				@Override
				public long getExactSizeIfKnown() {
					return theKeySpliter.getExactSizeIfKnown();
				}

				@Override
				public int characteristics() {
					return theKeySpliter.characteristics() & (~SORTED);
				}

				@Override
				public boolean tryAdvanceElement(Consumer<? super CollectionElement<V>> action) {
					return theKeySpliter.tryAdvanceElement(keyEl -> theMap.forEntry(keyEl.getElementId(), entry -> action.accept(entry)));
				}

				@Override
				public boolean tryReverseElement(Consumer<? super CollectionElement<V>> action) {
					return theKeySpliter.tryReverseElement(keyEl -> theMap.forEntry(keyEl.getElementId(), entry -> action.accept(entry)));
				}

				@Override
				public boolean tryAdvanceElementM(Consumer<? super MutableCollectionElement<V>> action) {
					return theKeySpliter
						.tryAdvanceElementM(keyEl -> theMap.forMutableEntry(keyEl.getElementId(), entry -> action.accept(entry)));
				}

				@Override
				public boolean tryReverseElementM(Consumer<? super MutableCollectionElement<V>> action) {
					return theKeySpliter
						.tryReverseElementM(keyEl -> theMap.forMutableEntry(keyEl.getElementId(), entry -> action.accept(entry)));
				}

				@Override
				public MutableElementSpliterator<V> trySplit() {
					MutableElementSpliterator<K> keySplit = theKeySpliter.trySplit();
					return keySplit == null ? null : new ValueSpliterator(keySplit);
				}
			}
			return new ValueSpliterator(theMap.keySet().mutableSpliterator(fromStart));
		}
	}
}
