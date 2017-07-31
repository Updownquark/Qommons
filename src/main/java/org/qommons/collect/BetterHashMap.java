package org.qommons.collect;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class BetterHashMap<K, V> implements BetterMap<K, V> {
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

		public <K, V> BetterHashMap<K, V> buildMap() {
			return new BetterHashMap<>(buildSet());
		}
	}

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

	@Override
	public MapEntryHandle<K, V> putEntry(K key, V value) {
		try (Transaction t = theEntries.lock(true, null)) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getElement(new SimpleMapEntry<>(key, value), true);
			if (entryEl != null) {
				entryEl.get().setValue(value);
			} else
				entryEl = theEntries.addElement(new SimpleMapEntry<>(key, value, true));
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
	public MapEntryHandle<K, V> getEntry(ElementId entryId) {
		return handleFor(theEntries.getElement(entryId));
	}

	@Override
	public <X> X ofMutableEntry(ElementId entryId, Function<? super MutableMapEntryHandle<K, V>, X> onEntry) {
		return theEntries.ofMutableElement(entryId, entryEl -> onEntry.apply(mutableHandleFor(entryEl)));
	}

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
		};
	}

	protected MutableMapEntryHandle<K, V> mutableHandleFor(MutableCollectionElement<? extends Map.Entry<K, V>> entry) {
		return new MutableMapEntryHandle<K, V>() {
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
		};
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
		public Transaction lock(boolean write, Object cause) {
			return theEntries.lock(write, cause);
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

				@Override
				public String canAdd(K value, boolean before) {
					return ((MutableCollectionElement<Map.Entry<K, V>>) entryEl).canAdd(new SimpleMapEntry<>(value, null), before);
				}

				@Override
				public ElementId add(K value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
					return ((MutableCollectionElement<Map.Entry<K, V>>) entryEl).add(new SimpleMapEntry<>(value, entryEl.get().getValue()),
						before);
				}
			};
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
		public <X> X ofMutableElement(ElementId element, Function<? super MutableCollectionElement<K>, X> onElement) {
			return theEntries.ofMutableElement(element, entryEl -> onElement.apply(mutableHandleFor(entryEl)));
		}

		@Override
		public MutableElementSpliterator<K> mutableSpliterator(ElementId element, boolean asNext) {
			return new KeySpliterator(theEntries.mutableSpliterator(element, asNext));
		}

		@Override
		public boolean forElement(K value, Consumer<? super CollectionElement<? extends K>> onElement, boolean first) {
			return theEntries.forElement(new SimpleMapEntry<>(value, null), entryEl -> onElement.accept(handleFor(entryEl)), first);
		}

		@Override
		public boolean forMutableElement(K value, Consumer<? super MutableCollectionElement<? extends K>> onElement, boolean first) {
			return theEntries.forMutableElement(new SimpleMapEntry<>(value, null), entryEl -> onElement.accept(mutableHandleFor(entryEl)),
				first);
		}

		@Override
		public MutableElementSpliterator<K> mutableSpliterator(boolean fromStart) {
			return new KeySpliterator(theEntries.mutableSpliterator(fromStart));
		}

		@Override
		public String canAdd(K value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<K> addElement(K value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public boolean addAll(Collection<? extends K> c) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			theEntries.clear();
		}

		private class KeySpliterator implements MutableElementSpliterator<K> {
			private final MutableElementSpliterator<Map.Entry<K, V>> theEntrySpliter;

			KeySpliterator(MutableElementSpliterator<Map.Entry<K, V>> entrySpliter) {
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
			public boolean tryAdvanceElement(Consumer<? super CollectionElement<K>> action) {
				return theEntrySpliter.tryAdvanceElement(el -> action.accept(handleFor(el)));
			}

			@Override
			public boolean tryReverseElement(Consumer<? super CollectionElement<K>> action) {
				return theEntrySpliter.tryReverseElement(el -> action.accept(handleFor(el)));
			}

			@Override
			public boolean tryAdvanceElementM(Consumer<? super MutableCollectionElement<K>> action) {
				return theEntrySpliter.tryAdvanceElementM(el -> action.accept(mutableHandleFor(el)));
			}

			@Override
			public boolean tryReverseElementM(Consumer<? super MutableCollectionElement<K>> action) {
				return theEntrySpliter.tryReverseElementM(el -> action.accept(mutableHandleFor(el)));
			}

			@Override
			public MutableElementSpliterator<K> trySplit() {
				MutableElementSpliterator<Map.Entry<K, V>> entrySplit = theEntrySpliter.trySplit();
				return entrySplit == null ? null : new KeySpliterator(entrySplit);
			}
		}

	}
}
