package org.qommons.collect;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.MutableElementHandle.StdMsg;

public interface BetterMap<K, V> extends TransactableMap<K, V> {
	@Override
	abstract boolean isLockSupported();

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

	ElementId putElement(K key, V value);

	boolean forEntry(K key, Consumer<? super MapEntryHandle<K, V>> onElement);

	boolean forMutableEntry(K key, Consumer<? super MutableMapEntryHandle<K, V>> onElement);

	<X> X ofEntry(ElementId entryId, Function<? super MapEntryHandle<K, V>, X> onElement);

	<X> X ofMutableEntry(ElementId entryId, Function<? super MutableMapEntryHandle<K, V>, X> onElement);

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
			putElement(key, value);
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

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public BetterSet<K> keySet() {
			return theWrapped.keySet().reverse();
		}

		@Override
		public ElementId putElement(K key, V value) {
			return ElementId.reverse(theWrapped.putElement(key, value));
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
		public boolean forElement(Map.Entry<K, V> value, Consumer<? super ElementHandle<? extends Map.Entry<K, V>>> onElement,
			boolean first) {
			return theMap.forEntry(value.getKey(), entry -> onElement.accept(handleFor(entry)));
		}

		@Override
		public boolean forMutableElement(Map.Entry<K, V> value, Consumer<? super MutableElementHandle<? extends Map.Entry<K, V>>> onElement,
			boolean first) {
			return theMap.forMutableEntry(value.getKey(), entry -> onElement.accept(mutableHandleFor(entry)));
		}

		@Override
		public <T> T ofElementAt(ElementId elementId, Function<? super ElementHandle<? extends Map.Entry<K, V>>, T> onElement) {
			return theMap.ofEntry(elementId, entry -> onElement.apply(handleFor(entry)));
		}

		@Override
		public <T> T ofMutableElementAt(ElementId elementId,
			Function<? super MutableElementHandle<? extends Map.Entry<K, V>>, T> onElement) {
			return theMap.ofMutableEntry(elementId, entry -> onElement.apply(mutableHandleFor(entry)));
		}

		@Override
		public MutableElementSpliterator<Map.Entry<K, V>> mutableSpliterator(boolean fromStart) {
			return new EntrySpliterator(theMap.keySet().mutableSpliterator(fromStart));
		}

		protected ElementHandle<Map.Entry<K, V>> handleFor(MapEntryHandle<K, V> entry) {}

		protected MutableElementHandle<Map.Entry<K, V>> mutableHandleFor(MutableMapEntryHandle<K, V> entry) {
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
			public boolean tryAdvanceElement(Consumer<? super ElementHandle<Map.Entry<K, V>>> action) {
				return theKeySpliter.tryAdvance(key -> theMap.forEntry(key, entry -> action.accept(handleFor(entry))));
			}

			@Override
			public boolean tryReverseElement(Consumer<? super ElementHandle<Map.Entry<K, V>>> action) {
				return theKeySpliter.tryReverse(key -> theMap.forEntry(key, entry -> action.accept(handleFor(entry))));
			}

			@Override
			public boolean tryAdvanceElementM(Consumer<? super MutableElementHandle<Map.Entry<K, V>>> action) {
				// Using the mutable version on the key spliterator to avoid problems upgrading a read to a write lock
				return theKeySpliter
					.tryAdvanceElementM(keyEl -> theMap.forMutableEntry(keyEl.get(), entry -> action.accept(mutableHandleFor(entry))));
			}

			@Override
			public boolean tryReverseElementM(Consumer<? super MutableElementHandle<Map.Entry<K, V>>> action) {
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
		public boolean forElement(V value, Consumer<? super ElementHandle<? extends V>> onElement, boolean first) {
			// TODO Auto-generated method stub
		}

		@Override
		public boolean forMutableElement(V value, Consumer<? super MutableElementHandle<? extends V>> onElement, boolean first) {
			// TODO Auto-generated method stub
		}

		@Override
		public <T> T ofElementAt(ElementId elementId, Function<? super ElementHandle<? extends V>, T> onElement) {
			// TODO Auto-generated method stub
		}

		@Override
		public <T> T ofMutableElementAt(ElementId elementId, Function<? super MutableElementHandle<? extends V>, T> onElement) {
			// TODO Auto-generated method stub
		}

		@Override
		public MutableElementSpliterator<V> mutableSpliterator(boolean fromStart) {
			// TODO Auto-generated method stub
		}
	}
}
