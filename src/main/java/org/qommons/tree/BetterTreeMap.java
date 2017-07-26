package org.qommons.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transaction;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementHandle;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableElementHandle;
import org.qommons.collect.MutableElementHandle.StdMsg;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.collect.MutableMapEntryHandle;
import org.qommons.collect.SimpleMapEntry;
import org.qommons.collect.StampedLockingStrategy;

public class BetterTreeMap<K, V> implements BetterSortedMap<K, V> {
	protected final Comparator<? super K> theCompare;
	private final BetterTreeEntrySet<K, V> theEntries;

	public BetterTreeMap(boolean threadSafe, Comparator<? super K> compare) {
		this(threadSafe ? new StampedLockingStrategy() : new FastFailLockingStrategy(), compare);
	}

	public BetterTreeMap(CollectionLockingStrategy locking, Comparator<? super K> compare) {
		theCompare = compare;
		theEntries = new BetterTreeEntrySet<>(locking, compare);
	}

	@Override
	public BetterSortedSet<K> keySet() {
		return new KeySet();
	}

	@Override
	public BinaryTreeEntry<K, V> putEntry(K key, V value) {
		try (Transaction t = theEntries.lock(true, null)) {
			BinaryTreeNode<Map.Entry<K, V>>[] id = new BinaryTreeNode[1];
			if (theEntries.forMutableElement(entry -> theCompare.compare(key, entry.getKey()), entryEl -> {
				int compare = comparator().compare(key, entryEl.get().getKey());
				if (compare == 0) {
					entryEl.get().setValue(value);
					id[0] = (BinaryTreeNode<java.util.Map.Entry<K, V>>) entryEl.getElementId();
				} else
					id[0] = ((MutableBinaryTreeNode<Map.Entry<K, V>>) entryEl).add(newEntry(key, value), compare < 0);
			}, SortedSearchFilter.PreferLess))
				return new TreeEntry<>(id[0]);
			else
				return new TreeEntry<>(theEntries.addIfEmpty(newEntry(key, value)));
		}
	}

	protected Map.Entry<K, V> newEntry(K key, V value) {
		return new SimpleMapEntry<>(key, value, true);
	}

	protected MapEntryHandle<K, V> handleFor(ElementHandle<? extends Map.Entry<K, V>> entryHandle) {
		return new MapEntryHandle<K, V>() {
			@Override
			public ElementId getElementId() {
				return entryHandle.getElementId();
			}

			@Override
			public V get() {
				return entryHandle.get().getValue();
			}

			@Override
			public K getKey() {
				return entryHandle.get().getKey();
			}
		};
	}

	protected MutableMapEntryHandle<K, V> mutableHandleFor(MutableElementHandle<? extends Map.Entry<K, V>> entryHandle) {
		return new MutableMapEntryHandle<K, V>() {
			@Override
			public ElementId getElementId() {
				return entryHandle.getElementId();
			}

			@Override
			public K getKey() {
				return entryHandle.get().getKey();
			}

			@Override
			public V get() {
				return entryHandle.get().getValue();
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
				String msg = isAcceptable(value);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				entryHandle.get().setValue(value);
			}

			@Override
			public String canRemove() {
				return entryHandle.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				entryHandle.remove();
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

	@Override
	public <X> X ofEntry(ElementId entryId, Function<? super MapEntryHandle<K, V>, X> onEntry) {
		return theEntries.ofElementAt(strip(entryId), entryEl -> onEntry.apply(handleFor(entryEl)));
	}

	@Override
	public <X> X ofMutableEntry(ElementId entryId, Function<? super MutableMapEntryHandle<K, V>, X> onEntry) {
		return theEntries.ofMutableElementAt(strip(entryId), entryEl -> onEntry.apply(mutableHandleFor(entryEl)));
	}

	@Override
	public boolean forEntry(Comparable<? super K> search, Consumer<? super MapEntryHandle<K, V>> onEntry, SortedSearchFilter filter) {
		return theEntries.forElement(entry -> search.compareTo(entry.getKey()), entryEl -> onEntry.accept(handleFor(entryEl)), filter);
	}

	@Override
	public boolean forMutableEntry(Comparable<? super K> search, Consumer<? super MutableMapEntryHandle<K, V>> onEntry,
		SortedSearchFilter filter) {
		return theEntries.forMutableElement(entry -> search.compareTo(entry.getKey()), entryEl -> onEntry.accept(mutableHandleFor(entryEl)),
			filter);
	}

	protected ElementId strip(ElementId id) {
		if (id instanceof TreeEntry)
			id = ((TreeEntry<?, ?>) id).getEntryNode();
		return id;
	}

	static class BetterTreeEntrySet<K, V> extends BetterTreeSet<Map.Entry<K, V>> {
		BetterTreeEntrySet(CollectionLockingStrategy locker, Comparator<? super K> compare) {
			super(locker, (e1, e2) -> compare.compare(e1.getKey(), e2.getKey()));
		}
	}

	static class TreeEntry<K, V> implements BinaryTreeEntry<K, V> {
		private final BinaryTreeNode<Map.Entry<K, V>> theEntryNode;

		public TreeEntry(BinaryTreeNode<java.util.Map.Entry<K, V>> entryNode) {
			theEntryNode = entryNode;
		}

		protected BinaryTreeNode<Map.Entry<K, V>> getEntryNode() {
			return theEntryNode;
		}

		@Override
		public K get() {
			return theEntryNode.get().getKey();
		}

		@Override
		public V getValue() {
			return theEntryNode.get().getValue();
		}

		@Override
		public int size() {
			return theEntryNode.size();
		}

		@Override
		public BinaryTreeEntry<K, V> getParent() {
			BinaryTreeNode<Map.Entry<K, V>> parent = theEntryNode.getParent();
			return parent == null ? null : new TreeEntry<>(parent);
		}

		@Override
		public BinaryTreeEntry<K, V> getLeft() {
			BinaryTreeNode<Map.Entry<K, V>> left = theEntryNode.getLeft();
			return left == null ? null : new TreeEntry<>(left);
		}

		@Override
		public BinaryTreeEntry<K, V> getRight() {
			BinaryTreeNode<Map.Entry<K, V>> right = theEntryNode.getRight();
			return right == null ? null : new TreeEntry<>(right);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theEntryNode.get().getKey());
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof BinaryTreeEntry && Objects.equals(theEntryNode.get().getKey(), ((BinaryTreeEntry<?, ?>) obj).get());
		}

		@Override
		public String toString() {
			return theEntryNode.toString();
		}
	}

	class KeySet implements BetterSortedSet<K> {
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
		public Comparator<? super K> comparator() {
			return theCompare;
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
			return BetterSortedSet.super.toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return BetterSortedSet.super.toArray(a);
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return theEntries.getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return theEntries.getElementsAfter(id);
		}

		@Override
		public int indexFor(Comparable<? super K> search) {
			return theEntries.indexFor(e -> search.compareTo(e.getKey()));
		}

		protected ElementHandle<K> handleFor(ElementHandle<? extends Map.Entry<K, V>> entryHandle) {
			return new ElementHandle<K>() {
				@Override
				public ElementId getElementId() {
					return entryHandle.getElementId();
				}

				@Override
				public K get() {
					return entryHandle.get().getKey();
				}
			};
		}

		protected MutableElementHandle<K> mutableHandleFor(MutableElementHandle<? extends Map.Entry<K, V>> entryHandle) {
			return new MutableElementHandle<K>() {
				@Override
				public ElementId getElementId() {
					return entryHandle.getElementId();
				}

				@Override
				public K get() {
					return entryHandle.get().getKey();
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
					return entryHandle.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					entryHandle.remove();
				}

				@Override
				public String canAdd(K value, boolean before) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public ElementId add(K value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}
			};
		}

		@Override
		public <T> T ofElementAt(int index, Function<? super ElementHandle<? extends K>, T> onElement) {
			return theEntries.ofElementAt(index, el -> onElement.apply(handleFor(el)));
		}

		@Override
		public <T> T ofMutableElementAt(int index, Function<? super MutableElementHandle<? extends K>, T> onElement) {
			return theEntries.ofMutableElementAt(index, el -> onElement.apply(mutableHandleFor(el)));
		}

		@Override
		public <T> T ofElementAt(ElementId elementId, Function<? super ElementHandle<? extends K>, T> onElement) {
			return theEntries.ofElementAt(strip(elementId), el -> onElement.apply(handleFor(el)));
		}

		@Override
		public <T> T ofMutableElementAt(ElementId elementId, Function<? super MutableElementHandle<? extends K>, T> onElement) {
			return theEntries.ofMutableElementAt(strip(elementId), el -> onElement.apply(mutableHandleFor(el)));
		}

		@Override
		public boolean forElement(Comparable<? super K> search, Consumer<? super ElementHandle<? extends K>> onElement,
			SortedSearchFilter filter) {
			return theEntries.forElement(e -> search.compareTo(e.getKey()), el -> onElement.accept(handleFor(el)), filter);
		}

		@Override
		public boolean forMutableElement(Comparable<? super K> search, Consumer<? super MutableElementHandle<? extends K>> onElement,
			SortedSearchFilter filter) {
			return theEntries.forMutableElement(e -> search.compareTo(e.getKey()), el -> onElement.accept(mutableHandleFor(el)), filter);
		}

		@Override
		public MutableElementSpliterator<K> mutableSpliterator(boolean fromStart) {
			return new KeySpliterator(theEntries.mutableSpliterator(fromStart));
		}

		@Override
		public MutableElementSpliterator<K> mutableSpliterator(int index) {
			return new KeySpliterator(theEntries.mutableSpliterator(index));
		}

		@Override
		public MutableElementSpliterator<K> mutableSpliterator(Comparable<? super K> searchForStart, boolean higher) {
			return new KeySpliterator(theEntries.mutableSpliterator(e -> searchForStart.compareTo(e.getKey()), higher));
		}

		@Override
		public boolean addAll(Collection<? extends K> c) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public ElementId addIfEmpty(K value) throws IllegalStateException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			theEntries.clear();
		}

		private class KeySpliterator implements MutableElementSpliterator<K> {
			private final MutableElementSpliterator<Map.Entry<K, V>> theEntrySpliter;

			KeySpliterator(MutableElementSpliterator<java.util.Map.Entry<K, V>> entrySpliter) {
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
			public Comparator<? super K> getComparator() {
				return theCompare;
			}

			@Override
			public boolean tryAdvanceElement(Consumer<? super ElementHandle<K>> action) {
				return theEntrySpliter.tryAdvanceElement(el -> action.accept(handleFor(el)));
			}

			@Override
			public boolean tryReverseElement(Consumer<? super ElementHandle<K>> action) {
				return theEntrySpliter.tryReverseElement(el -> action.accept(handleFor(el)));
			}

			@Override
			public boolean tryAdvanceElementM(Consumer<? super MutableElementHandle<K>> action) {
				return theEntrySpliter.tryAdvanceElementM(el -> action.accept(mutableHandleFor(el)));
			}

			@Override
			public boolean tryReverseElementM(Consumer<? super MutableElementHandle<K>> action) {
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
