package org.qommons.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.qommons.Transaction;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
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

	protected void checkValid() {
		theEntries.checkValid();
	}

	@Override
	public BetterSortedSet<K> keySet() {
		return new KeySet();
	}

	@Override
	public BinaryTreeEntry<K, V> putEntry(K key, V value) {
		try (Transaction t = theEntries.lock(true, null)) {
			BinaryTreeNode<Map.Entry<K, V>> entry = theEntries.search(e -> theCompare.compare(key, e.getKey()),
				SortedSearchFilter.PreferLess);
			if (entry != null) {
				int compare = comparator().compare(key, entry.get().getKey());
				if (compare == 0) {
					entry.get().setValue(value);
				} else
					entry = theEntries
						.getElement(theEntries.ofMutableElement(entry.getElementId(), el -> el.add(newEntry(key, value), compare < 0)));
				return new TreeEntry<>(entry);
			} else
				return new TreeEntry<>(theEntries.addIfEmpty(newEntry(key, value)));
		}
	}

	protected Map.Entry<K, V> newEntry(K key, V value) {
		return new SimpleMapEntry<>(key, value, true);
	}

	@Override
	public BinaryTreeEntry<K, V> getEntry(K key) {
		BinaryTreeNode<Map.Entry<K, V>> entry = theEntries.search(e -> theCompare.compare(key, e.getKey()), SortedSearchFilter.OnlyMatch);
		return entry == null ? null : new TreeEntry<>(entry);
	}

	@Override
	public BinaryTreeEntry<K, V> getEntryById(ElementId entryId) {
		return new TreeEntry<>(theEntries.getElement(entryId));
	}

	@Override
	public BinaryTreeEntry<K, V> search(Comparable<? super K> search, SortedSearchFilter filter) {
		BinaryTreeNode<Map.Entry<K, V>> entry = theEntries.search(e -> search.compareTo(e.getKey()), filter);
		return entry == null ? null : new TreeEntry<>(entry);
	}

	@Override
	public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
		return new MutableTreeEntry<>(theEntries.mutableElement(entryId));
	}

	@Override
	public String toString() {
		return theEntries.toString();
	}

	static class BetterTreeEntrySet<K, V> extends BetterTreeSet<Map.Entry<K, V>> {
		BetterTreeEntrySet(CollectionLockingStrategy locker, Comparator<? super K> compare) {
			super(locker, (e1, e2) -> compare.compare(e1.getKey(), e2.getKey()));
		}
	}

	static class TreeEntry<K, V> implements BinaryTreeEntry<K, V> {
		private final BinaryTreeNode<Map.Entry<K, V>> theEntryNode;

		public TreeEntry(BinaryTreeNode<Map.Entry<K, V>> entryNode) {
			theEntryNode = entryNode;
		}

		protected BinaryTreeNode<Map.Entry<K, V>> getEntryNode() {
			return theEntryNode;
		}

		static <K, V> TreeEntry<K, V> wrap(BinaryTreeNode<Map.Entry<K, V>> entryNode) {
			return entryNode == null ? null : new TreeEntry<>(entryNode);
		}

		@Override
		public ElementId getElementId() {
			return theEntryNode.getElementId();
		}

		@Override
		public K getKey() {
			return theEntryNode.get().getKey();
		}

		@Override
		public V get() {
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
		public BinaryTreeEntry<K, V> getClosest(boolean left) {
			BinaryTreeNode<Map.Entry<K, V>> close = theEntryNode.getClosest(left);
			return close == null ? null : new TreeEntry<>(close);
		}

		@Override
		public boolean getSide() {
			return theEntryNode.getSide();
		}

		@Override
		public int getNodesBefore() {
			return theEntryNode.getNodesBefore();
		}

		@Override
		public int getNodesAfter() {
			return theEntryNode.getNodesAfter();
		}

		@Override
		public BinaryTreeEntry<K, V> getRoot() {
			return wrap(theEntryNode.getRoot());
		}

		@Override
		public BinaryTreeEntry<K, V> getSibling() {
			return wrap(theEntryNode.getSibling());
		}

		@Override
		public BinaryTreeEntry<K, V> get(int index) {
			return wrap(theEntryNode.get(index));
		}

		@Override
		public BinaryTreeEntry<K, V> findClosest(Comparable<BinaryTreeNode<V>> finder, boolean lesser, boolean strictly) {
			return wrap(theEntryNode.findClosest(n -> finder.compareTo(wrap(n)), lesser, strictly));
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theEntryNode.get().getKey());
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Map.Entry && Objects.equals(theEntryNode.get().getKey(), ((Map.Entry<?, ?>) obj).getKey());
		}

		@Override
		public String toString() {
			return theEntryNode.toString();
		}
	}

	static class MutableTreeEntry<K, V> extends TreeEntry<K, V> implements MutableBinaryTreeEntry<K, V> {
		public MutableTreeEntry(MutableBinaryTreeNode<Map.Entry<K, V>> entryNode) {
			super(entryNode);
		}

		private static <K, V> MutableTreeEntry<K, V> wrap(MutableBinaryTreeNode<Map.Entry<K, V>> entryNode) {
			return entryNode == null ? null : new MutableTreeEntry<>(entryNode);
		}

		@Override
		protected MutableBinaryTreeNode<Map.Entry<K, V>> getEntryNode() {
			return (MutableBinaryTreeNode<java.util.Map.Entry<K, V>>) super.getEntryNode();
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getParent() {
			return wrap(getEntryNode().getParent());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getLeft() {
			return wrap(getEntryNode().getLeft());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getRight() {
			return wrap(getEntryNode().getRight());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getClosest(boolean left) {
			return wrap(getEntryNode().getClosest(left));
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getRoot() {
			return wrap(getEntryNode().getRoot());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getSibling() {
			return wrap(getEntryNode().getSibling());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> get(int index) {
			return wrap(getEntryNode().get(index));
		}

		@Override
		public MutableBinaryTreeEntry<K, V> findClosest(Comparable<BinaryTreeNode<V>> finder, boolean lesser, boolean strictly) {
			return wrap(getEntryNode().findClosest(n -> finder.compareTo(TreeEntry.wrap(n)), lesser, strictly));
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
			getEntryNode().get().setValue(value);
		}

		@Override
		public String canRemove() {
			return getEntryNode().canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			getEntryNode().remove();
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
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theEntries.lock(write, structural, cause);
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theEntries.getStamp(structuralOnly);
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

		@Override
		public CollectionElement<K> getElement(int index) {
			return handleFor(theEntries.getElement(index));
		}

		@Override
		public CollectionElement<K> getElement(ElementId id) {
			return handleFor(theEntries.getElement(id));
		}

		@Override
		public CollectionElement<K> getAdjacentElement(ElementId elementId, boolean next) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getAdjacentElement(elementId, next);
			return entryEl == null ? null : handleFor(entryEl);
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
		public CollectionElement<K> search(Comparable<? super K> search, SortedSearchFilter filter) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.search(e -> search.compareTo(e.getKey()), filter);
			return entryEl == null ? null : handleFor(entryEl);
		}

		protected CollectionElement<K> handleFor(CollectionElement<? extends Map.Entry<K, V>> entryHandle) {
			return new CollectionElement<K>() {
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

		protected MutableCollectionElement<K> mutableHandleFor(MutableCollectionElement<? extends Map.Entry<K, V>> entryHandle) {
			return new MutableCollectionElement<K>() {
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
		public MutableElementSpliterator<K> spliterator(boolean fromStart) {
			return new KeySpliterator(theEntries.spliterator(fromStart));
		}

		@Override
		public boolean addAll(Collection<? extends K> c) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public CollectionElement<K> addIfEmpty(K value) throws IllegalStateException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			theEntries.clear();
		}

		private class KeySpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<K> {
			private final MutableElementSpliterator<Map.Entry<K, V>> theEntrySpliter;

			KeySpliterator(MutableElementSpliterator<java.util.Map.Entry<K, V>> entrySpliter) {
				super(theEntries);
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
			protected boolean internalForElementM(Consumer<? super MutableCollectionElement<K>> action, boolean forward) {
				return theEntrySpliter.forElementM(el -> action.accept(mutableHandleFor(el)), forward);
			}

			@Override
			protected boolean internalForElement(Consumer<? super CollectionElement<K>> action, boolean forward) {
				return theEntrySpliter.forElement(el -> action.accept(handleFor(el)), forward);
			}

			@Override
			public MutableElementSpliterator<K> trySplit() {
				MutableElementSpliterator<Map.Entry<K, V>> entrySplit = theEntrySpliter.trySplit();
				return entrySplit == null ? null : new KeySpliterator(entrySplit);
			}
		}
	}
}
