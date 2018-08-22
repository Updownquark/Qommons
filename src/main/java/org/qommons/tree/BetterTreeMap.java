package org.qommons.tree;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterMapEntryImpl;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.collect.MutableMapEntryHandle;
import org.qommons.collect.OptimisticContext;
import org.qommons.collect.SimpleMapEntry;
import org.qommons.collect.StampedLockingStrategy;

/**
 * A tree-based implementation of {@link BetterSortedMap}
 * 
 * @param <K> The type of keys in the map
 * @param <V> The type of values in the map
 */
public class BetterTreeMap<K, V> implements BetterSortedMap<K, V> {
	/** The key comparator for the map */
	protected final Comparator<? super K> theCompare;
	private final BetterTreeEntrySet<K, V> theEntries;
	private final KeySet theKeySet;

	/**
	 * @param threadSafe Whether to secure this collection for thread-safety
	 * @param compare The comparator to use to sort the keys
	 */
	public BetterTreeMap(boolean threadSafe, Comparator<? super K> compare) {
		this(threadSafe ? new StampedLockingStrategy() : new FastFailLockingStrategy(), compare);
	}

	/**
	 * @param locker The locking strategy for the collection
	 * @param compare The comparator to use to sort the keys
	 */
	public BetterTreeMap(CollectionLockingStrategy locker, Comparator<? super K> compare) {
		theCompare = compare;
		theEntries = new BetterTreeEntrySet<>(locker, compare);
		theKeySet = new KeySet();
	}

	/** Checks this map's structure for errors */
	protected void checkValid() {
		theEntries.checkValid();
	}

	@Override
	public BetterSortedSet<K> keySet() {
		return theKeySet;
	}

	@Override
	public BinaryTreeEntry<K, V> putEntry(K key, V value, boolean first) {
		return (BinaryTreeEntry<K, V>) BetterSortedMap.super.putEntry(key, value, first);
	}

	@Override
	public BinaryTreeEntry<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
		return wrap(theEntries.addElement(newEntry(key, value), after, before, first));
	}

	/**
	 * @param key The key for the entry
	 * @param value The initial value for the entry
	 * @return The map entry for the key to use in this map
	 */
	protected Map.Entry<K, V> newEntry(K key, V value) {
		return new TreeEntry(key, value);
	}

	@Override
	public BinaryTreeEntry<K, V> getEntry(K key) {
		return wrap(theEntries.search(//
			e -> theCompare.compare(key, e.getKey()), SortedSearchFilter.OnlyMatch));
	}

	@Override
	public BinaryTreeEntry<K, V> getEntryById(ElementId entryId) {
		return wrap(theEntries.getElement(entryId));
	}

	@Override
	public BinaryTreeEntry<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, SortedSearchFilter filter) {
		return wrap(theEntries.search(search, filter));
	}

	@Override
	public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
		return wrapMutable((TreeEntry) theEntries.getElement(entryId).get());
	}

	@Override
	public int hashCode() {
		int h = 0;
		for (Map.Entry<K, V> entry : entrySet())
			h = h * 7 + entry.getKey().hashCode() * 3 + entry.getValue().hashCode();
		return h;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (!(obj instanceof Map))
			return false;
		Iterator<Map.Entry<K, V>> iter = entrySet().iterator();
		Iterator<? extends Map.Entry<?, ?>> otherIter = ((Map<?, ?>) obj).entrySet().iterator();
		while (iter.hasNext()) {
			if (!otherIter.hasNext())
				return false;
			if (!iter.next().equals(otherIter.next()))
				return false;
		}
		if (otherIter.hasNext())
			return false;
		return true;
	}

	@Override
	public String toString() {
		return theEntries.toString();
	}

	TreeEntry wrap(BinaryTreeNode<Map.Entry<K, V>> entryNode) {
		if (entryNode == null)
			return null;
		TreeEntry entry = (TreeEntry) entryNode.get();
		if (entry.theEntryNode == null)
			entry.setNode(entryNode);
		return entry;
	}

	class TreeEntry extends BetterMapEntryImpl<K, V> implements BinaryTreeEntry<K, V> {
		BinaryTreeNode<Map.Entry<K, V>> theEntryNode;

		TreeEntry(K key, V value) {
			super(key, value);
		}

		void setNode(BinaryTreeNode<Map.Entry<K, V>> node) {
			theEntryNode = node;
			theId = node.getElementId();
		}

		protected BinaryTreeNode<Map.Entry<K, V>> getEntryNode() {
			return theEntryNode;
		}

		@Override
		public int size() {
			return theEntryNode.size();
		}

		@Override
		public TreeEntry getParent() {
			return wrap(theEntryNode.getParent());
		}

		@Override
		public TreeEntry getLeft() {
			return wrap(theEntryNode.getLeft());
		}

		@Override
		public TreeEntry getRight() {
			return wrap(theEntryNode.getRight());
		}

		@Override
		public TreeEntry getClosest(boolean left) {
			return wrap(theEntryNode.getClosest(left));
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
		public TreeEntry getRoot() {
			return wrap(theEntryNode.getRoot());
		}

		@Override
		public TreeEntry getSibling() {
			return wrap(theEntryNode.getSibling());
		}

		@Override
		public TreeEntry get(int index, OptimisticContext ctx) {
			return theEntries.getLocker().doOptimistically(null, //
				(init, ctx2) -> wrap(theEntryNode.get(index, OptimisticContext.and(ctx, ctx2))), true);
		}

		@Override
		public TreeEntry findClosest(Comparable<BinaryTreeNode<V>> finder, boolean lesser, boolean strictly, OptimisticContext ctx) {
			return theEntries.getLocker()
				.doOptimistically(null, //
					(init, ctx2) -> wrap(
						theEntryNode.findClosest(n -> finder.compareTo(wrap(n)), lesser, strictly, OptimisticContext.and(ctx, ctx2))),
					true);
		}

		MutableTreeEntry mutable() {
			return (BetterTreeMap<K, V>.MutableTreeEntry) mutable(theEntries, BetterTreeMap.this::values);
		}

		@Override
		protected MutableMapEntryHandle<K, V> createMutableHandle(BetterSet<Entry<K, V>> entrySet, Supplier<BetterCollection<V>> values) {
			return new MutableTreeEntry(this);
		}

		@Override
		protected CollectionElement<K> keyHandle() {
			return super.keyHandle();
		}

		MutableCollectionElement<K> mutableKeyHandle() {
			return mutableKeyHandle(theEntries, BetterTreeMap.this::keySet);
		}
	}

	static class BetterTreeEntrySet<K, V> extends BetterTreeSet<Map.Entry<K, V>> {
		BetterTreeEntrySet(CollectionLockingStrategy locker, Comparator<? super K> compare) {
			super(locker, (e1, e2) -> compare.compare(e1.getKey(), e2.getKey()));
		}
	}

	MutableTreeEntry wrapMutable(TreeEntry entry) {
		return entry == null ? null : entry.mutable();
	}

	class MutableTreeEntry extends BetterMapEntryImpl.BetterMapMutableEntryHandleImpl<K, V> implements MutableBinaryTreeEntry<K, V> {
		public MutableTreeEntry(TreeEntry entry) {
			super(entry, theEntries, BetterTreeMap.this::values);
		}

		@Override
		protected TreeEntry getEntry() {
			return (BetterTreeMap<K, V>.TreeEntry) super.getEntry();
		}

		@Override
		public int size() {
			return getEntry().size();
		}

		@Override
		public boolean getSide() {
			return getEntry().getSide();
		}

		@Override
		public int getNodesBefore() {
			return getEntry().getNodesBefore();
		}

		@Override
		public int getNodesAfter() {
			return getEntry().getNodesAfter();
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getParent() {
			return wrapMutable(getEntry().getParent());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getLeft() {
			return wrapMutable(getEntry().getLeft());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getRight() {
			return wrapMutable(getEntry().getRight());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getClosest(boolean left) {
			return wrapMutable(getEntry().getClosest(left));
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getRoot() {
			return wrapMutable(getEntry().getRoot());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> getSibling() {
			return wrapMutable(getEntry().getSibling());
		}

		@Override
		public MutableBinaryTreeEntry<K, V> get(int index, OptimisticContext ctx) {
			return theEntries.getLocker().doOptimistically(null, //
				(init, ctx2) -> wrapMutable(getEntry().get(index, OptimisticContext.and(ctx, ctx2))), true);
		}

		@Override
		public MutableBinaryTreeEntry<K, V> findClosest(Comparable<BinaryTreeNode<V>> finder, boolean lesser, boolean strictly,
			OptimisticContext ctx) {
			return theEntries.getLocker().doOptimistically(null, //
				(init, ctx2) -> wrapMutable(
					getEntry().findClosest(n -> finder.compareTo(n), lesser, strictly, OptimisticContext.and(ctx, ctx2))),
				true);
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			// TODO Auto-generated method stub
			super.remove();
		}

		@Override
		public TreeEntry immutable() {
			return getEntry();
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
		public CollectionElement<K> getTerminalElement(boolean first) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getTerminalElement(first);
			return entryEl == null ? null : handleFor(entryEl);
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
			return entryHandle == null ? null : ((TreeEntry) entryHandle.get()).keyHandle();
		}

		protected MutableCollectionElement<K> mutableHandleFor(MutableCollectionElement<? extends Map.Entry<K, V>> entryHandle) {
			return entryHandle == null ? null : ((TreeEntry) entryHandle.get()).mutableKeyHandle();
		}

		@Override
		public MutableElementSpliterator<K> spliterator(boolean fromStart) {
			return new KeySpliterator(theEntries.spliterator(fromStart));
		}

		@Override
		public String canAdd(K value, ElementId after, ElementId before) {
			return theEntries.canAdd(new SimpleMapEntry<>(value, null), after, before);
		}

		@Override
		public CollectionElement<K> addElement(K value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			CollectionElement<Map.Entry<K, V>> entry = theEntries.addElement(newEntry(value, null), after, before, first);
			return entry == null ? null : handleFor(entry);
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
			return BetterCollection.toString(this);
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

			protected MutableCollectionElement<K> mutableHandleFor(MutableCollectionElement<? extends Map.Entry<K, V>> entryHandle) {
				return new MutableCollectionElement<K>() {
					@Override
					public BetterCollection<K> getCollection() {
						return KeySet.this;
					}

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
						return null;
					}

					@Override
					public String isAcceptable(K value) {
						return ((MutableCollectionElement<SimpleMapEntry<K, V>>) entryHandle).isAcceptable(new SimpleMapEntry<>(value, null));
					}

					@Override
					public void set(K value) throws UnsupportedOperationException, IllegalArgumentException {
						((MutableCollectionElement<Map.Entry<K, V>>) entryHandle).set(newEntry(value, entryHandle.get().getValue()));
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
						return ((MutableCollectionElement<SimpleMapEntry<K, V>>) entryHandle).canAdd(new SimpleMapEntry<>(value, null), before);
					}

					@Override
					public ElementId add(K value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
						return ((MutableCollectionElement<Map.Entry<K, V>>) entryHandle).add(newEntry(value, null),
							before);
					}
				};
			}
		}
	}
}
