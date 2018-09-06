package org.qommons.tree;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterMapEntryImpl;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.collect.OptimisticContext;
import org.qommons.collect.SimpleMapEntry;
import org.qommons.collect.StampedLockingStrategy;

/**
 * A tree-based implementation of {@link BetterSortedMap}
 * 
 * @param <K> The type of keys in the map
 * @param <V> The type of values in the map
 */
public class BetterTreeMap<K, V> implements TreeBasedSortedMap<K, V> {
	/** The key comparator for the map */
	protected final Comparator<? super K> theCompare;
	private final BetterTreeEntrySet<K, V> theEntries;
	private final KeySet theKeySet;
	private final EntrySet theEntrySet;

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
		theEntrySet = new EntrySet(this);
	}

	public BetterTreeMap(boolean threadSafe, SortedMap<K, ? extends V> map) {
		this(threadSafe ? new StampedLockingStrategy() : new FastFailLockingStrategy(), map);
	}

	public BetterTreeMap(CollectionLockingStrategy locker, SortedMap<K, ? extends V> map) {
		theCompare = map.comparator();
		theEntries = new BetterTreeEntrySet<>(locker, map, this::newEntry);
		theKeySet = new KeySet();
		theEntrySet = new EntrySet(this);
	}

	/** Checks this map's structure for errors */
	protected void checkValid() {
		theEntries.checkValid();
	}

	@Override
	public TreeBasedSet<K> keySet() {
		return theKeySet;
	}

	@Override
	public TreeBasedSet<Entry<K, V>> entrySet() {
		return theEntrySet;
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
	public MutableBinaryTreeEntry<K, V> mutableEntry(ElementId entryId) {
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
		protected MutableBinaryTreeEntry<K, V> createMutableHandle(BetterSet<Entry<K, V>> entrySet, Supplier<BetterCollection<V>> values) {
			return new MutableTreeEntry(this);
		}

		@Override
		protected BinaryTreeNode<K> keyHandle() {
			return (BinaryTreeNode<K>) super.keyHandle();
		}

		@Override
		protected BinaryTreeNode<K> makeKeyHandle() {
			return new BinaryTreeKeyHandle<>(this);
		}

		MutableBinaryTreeNode<K> mutableKeyHandle() {
			return mutableKeyHandle(theEntries, BetterTreeMap.this::keySet);
		}

		@Override
		protected MutableBinaryTreeNode<K> mutableKeyHandle(BetterSet<Map.Entry<K, V>> entrySet, Supplier<BetterSet<K>> keySet) {
			return (MutableBinaryTreeNode<K>) super.mutableKeyHandle(entrySet, keySet);
		}

		@Override
		protected MutableCollectionElement<K> createMutableKeyHandle(BetterSet<Map.Entry<K, V>> entrySet,
			Supplier<BetterSet<K>> keySet) {
			MutableCollectionElement<Map.Entry<K, V>> mutableEntryEl = entrySet.mutableElement(theId);
			return new MutableBinaryTreeKeyHandle<>(this, mutableEntryEl, keySet);
		}
	}

	class BinaryTreeKeyHandle<K> extends BetterMapEntryImpl.BetterMapEntryKeyHandle<K> implements BinaryTreeNode<K> {
		BinaryTreeKeyHandle(BetterTreeMap<K, ?>.TreeEntry entry) {
			super(entry);
		}

		@Override
		protected BetterTreeMap<K, ?>.TreeEntry getEntry() {
			return (BetterTreeMap<K, ?>.TreeEntry) super.getEntry();
		}

		@Override
		public BinaryTreeNode<K> getParent() {
			return key(getEntry().getParent());
		}

		@Override
		public BinaryTreeNode<K> getLeft() {
			return key(getEntry().getLeft());
		}

		@Override
		public BinaryTreeNode<K> getRight() {
			return key(getEntry().getRight());
		}

		@Override
		public BinaryTreeNode<K> getClosest(boolean left) {
			return key(getEntry().getClosest(left));
		}

		@Override
		public int size() {
			return getEntry().size();
		}

		@Override
		public BinaryTreeNode<K> getRoot() {
			return getEntry().getRoot().keyHandle();
		}

		@Override
		public boolean getSide() {
			return getEntry().getSide();
		}

		@Override
		public BinaryTreeNode<K> getSibling() {
			return key(getEntry().getSibling());
		}

		@Override
		public BinaryTreeNode<K> get(int index, OptimisticContext ctx) {
			return key(getEntry().get(index, ctx));
		}

		@Override
		public int getNodesBefore() {
			return getEntry().getNodesBefore();
		}

		@Override
		public int getNodesAfter() {
			return getEntry().getNodesAfter();
		}

		private BinaryTreeNode<K> key(BetterTreeMap<K, ?>.TreeEntry entry) {
			return entry == null ? null : entry.keyHandle();
		}
	}

	class MutableBinaryTreeKeyHandle<K> extends BetterMapEntryImpl.BetterMapEntryMutableKeyHandle<K> implements MutableBinaryTreeNode<K> {
		MutableBinaryTreeKeyHandle(BetterTreeMap<K, ?>.TreeEntry entry, MutableCollectionElement<? extends Map.Entry<K, ?>> mutableEntryEl,
			Supplier<BetterSet<K>> keySet) {
			super(entry, mutableEntryEl, keySet);
		}

		@Override
		protected BetterTreeMap<K, ?>.TreeEntry getEntry() {
			return (BetterTreeMap<K, ?>.TreeEntry) super.getEntry();
		}

		@Override
		public MutableBinaryTreeNode<K> getParent() {
			return key(getEntry().getParent());
		}

		@Override
		public MutableBinaryTreeNode<K> getLeft() {
			return key(getEntry().getLeft());
		}

		@Override
		public MutableBinaryTreeNode<K> getRight() {
			return key(getEntry().getRight());
		}

		@Override
		public MutableBinaryTreeNode<K> getClosest(boolean left) {
			return key(getEntry().getClosest(left));
		}

		@Override
		public MutableBinaryTreeNode<K> findClosest(Comparable<BinaryTreeNode<K>> finder, boolean lesser, boolean strictly,
			OptimisticContext ctx) {
			return key(getEntry().findClosest(entry -> finder.compareTo(((BetterTreeMap<K, ?>.TreeEntry) entry).keyHandle()), lesser,
				strictly, ctx));
		}

		@Override
		public int size() {
			return getEntry().size();
		}

		@Override
		public MutableBinaryTreeNode<K> getRoot() {
			return getEntry().getRoot().mutableKeyHandle();
		}

		@Override
		public boolean getSide() {
			return getEntry().getSide();
		}

		@Override
		public MutableBinaryTreeNode<K> getSibling() {
			return key(getEntry().getSibling());
		}

		@Override
		public MutableBinaryTreeNode<K> get(int index, OptimisticContext ctx) {
			return key(getEntry().get(index, ctx));
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
		public BinaryTreeNode<K> immutable() {
			return getEntry().keyHandle();
		}

		private MutableBinaryTreeNode<K> key(BetterTreeMap<K, ?>.TreeEntry entry) {
			return entry == null ? null : entry.mutableKeyHandle();
		}
	}

	static class BetterTreeEntrySet<K, V> extends BetterTreeSet<Map.Entry<K, V>> {
		BetterTreeEntrySet(CollectionLockingStrategy locker, Comparator<? super K> compare) {
			super(locker, (e1, e2) -> compare.compare(e1.getKey(), e2.getKey()));
		}

		BetterTreeEntrySet(CollectionLockingStrategy locker, SortedMap<K, ? extends V> map, BiFunction<K, V, Map.Entry<K, V>> creator) {
			this(locker, map.comparator());
			initialize((Set<? extends Map.Entry<K, V>>) map.entrySet(), entry -> creator.apply(entry.getKey(), entry.getValue()));
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
		public TreeEntry immutable() {
			return getEntry();
		}
	}

	class KeySet implements TreeBasedSet<K> {
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
		public Transaction tryLock(boolean write, boolean structural, Object cause) {
			return theEntries.tryLock(write, structural, cause);
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
			return TreeBasedSet.super.toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return TreeBasedSet.super.toArray(a);
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
		public BinaryTreeNode<K> getElement(int index) {
			return handleFor(theEntries.getElement(index));
		}

		@Override
		public BinaryTreeNode<K> getElement(ElementId id) {
			return handleFor(theEntries.getElement(id));
		}

		@Override
		public BinaryTreeNode<K> getTerminalElement(boolean first) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getTerminalElement(first);
			return entryEl == null ? null : handleFor(entryEl);
		}

		@Override
		public BinaryTreeNode<K> getAdjacentElement(ElementId elementId, boolean next) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getAdjacentElement(elementId, next);
			return entryEl == null ? null : handleFor(entryEl);
		}

		@Override
		public MutableBinaryTreeNode<K> mutableElement(ElementId id) {
			return mutableHandleFor(theEntries.mutableElement(id));
		}

		@Override
		public MutableElementSpliterator<K> spliterator(ElementId element, boolean asNext) {
			return new KeySpliterator(theEntries.spliterator(element, asNext));
		}

		@Override
		public BinaryTreeNode<K> search(Comparable<? super K> search, SortedSearchFilter filter) {
			return handleFor(theEntries.search(e -> search.compareTo(e.getKey()), filter));
		}

		@Override
		public BinaryTreeNode<K> splitBetween(ElementId element1, ElementId element2) {
			return handleFor(theEntries.splitBetween(element1, element2));
		}

		protected BinaryTreeNode<K> handleFor(CollectionElement<? extends Map.Entry<K, V>> entryHandle) {
			return entryHandle == null ? null : ((TreeEntry) entryHandle.get()).keyHandle();
		}

		protected MutableBinaryTreeNode<K> mutableHandleFor(MutableCollectionElement<? extends Map.Entry<K, V>> entryHandle) {
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
		public BinaryTreeNode<K> addElement(K value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			CollectionElement<Map.Entry<K, V>> entry = theEntries.addElement(newEntry(value, null), after, before, first);
			return entry == null ? null : handleFor(entry);
		}

		@Override
		public BinaryTreeNode<K> getOrAdd(K value, boolean first, Runnable added) {
			TreeEntry entry = (BetterTreeMap<K, V>.TreeEntry) BetterTreeMap.this.getOrPutEntry(value, null, first, added);
			return entry == null ? null : entry.keyHandle();
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

			KeySpliterator(MutableElementSpliterator<Map.Entry<K, V>> entrySpliter) {
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

	class EntrySet extends BetterSortedMap.BetterSortedEntrySet<K, V> implements TreeBasedSet<Map.Entry<K, V>> {
		EntrySet(BetterSortedMap<K, V> map) {
			super(map);
		}

		@Override
		public BinaryTreeNode<Map.Entry<K, V>> getElement(int index) {
			return (BinaryTreeNode<Map.Entry<K, V>>) super.getElement(index);
		}

		@Override
		public BinaryTreeNode<Entry<K, V>> getTerminalElement(boolean first) {
			return (BinaryTreeNode<Map.Entry<K, V>>) super.getTerminalElement(first);
		}

		@Override
		public BinaryTreeNode<Map.Entry<K, V>> getAdjacentElement(ElementId elementId, boolean next) {
			return (BinaryTreeNode<Map.Entry<K, V>>) super.getAdjacentElement(elementId, next);
		}

		@Override
		public BinaryTreeNode<Map.Entry<K, V>> search(Comparable<? super Map.Entry<K, V>> search, SortedSearchFilter filter) {
			return (BinaryTreeNode<Map.Entry<K, V>>) super.search(search, filter);
		}

		@Override
		public BinaryTreeNode<Map.Entry<K, V>> getElement(Map.Entry<K, V> value, boolean first) {
			return (BinaryTreeNode<Map.Entry<K, V>>) super.getElement(value, first);
		}

		@Override
		public BinaryTreeNode<Map.Entry<K, V>> getElement(ElementId id) {
			return new EntryElement(id);
		}

		@Override
		public BinaryTreeNode<Map.Entry<K, V>> getOrAdd(Map.Entry<K, V> value, boolean first, Runnable added) {
			return (BinaryTreeNode<Map.Entry<K, V>>) super.getOrAdd(value, first, added);
		}

		@Override
		public BinaryTreeNode<Map.Entry<K, V>> splitBetween(ElementId element1, ElementId element2) {
			BinaryTreeNode<?> found = theEntries.splitBetween(element1, element2);
			return found == null ? null : getElement(found.getElementId());
		}

		@Override
		public MutableBinaryTreeNode<Map.Entry<K, V>> mutableElement(ElementId id) {
			return new MutableEntryElement(id);
		}

		@Override
		public BinaryTreeNode<Map.Entry<K, V>> addElement(Map.Entry<K, V> value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (BinaryTreeNode<Map.Entry<K, V>>) super.addElement(value, after, before, first);
		}

		class EntryElement implements BinaryTreeNode<Map.Entry<K, V>> {
			final ElementId theId;

			EntryElement(ElementId id) {
				theId = id;
			}

			@Override
			public ElementId getElementId() {
				return theId;
			}

			@Override
			public Map.Entry<K, V> get() {
				return getEntryById(theId);
			}

			@Override
			public BinaryTreeNode<Map.Entry<K, V>> getParent() {
				return element(getEntryById(theId).getParent());
			}

			@Override
			public BinaryTreeNode<Map.Entry<K, V>> getLeft() {
				return element(getEntryById(theId).getLeft());
			}

			@Override
			public BinaryTreeNode<Map.Entry<K, V>> getRight() {
				return element(getEntryById(theId).getRight());
			}

			@Override
			public BinaryTreeNode<Map.Entry<K, V>> getClosest(boolean left) {
				return element(getEntryById(theId).getClosest(left));
			}

			@Override
			public int size() {
				return getEntryById(theId).size();
			}

			@Override
			public BinaryTreeNode<Map.Entry<K, V>> getRoot() {
				return element(getEntryById(theId).getRoot());
			}

			@Override
			public boolean getSide() {
				return getEntryById(theId).getSide();
			}

			@Override
			public BinaryTreeNode<Map.Entry<K, V>> getSibling() {
				return element(getEntryById(theId).getSibling());
			}

			@Override
			public BinaryTreeNode<Map.Entry<K, V>> get(int index, OptimisticContext ctx) {
				return element(getEntryById(theId).get(index, ctx));
			}

			@Override
			public int getNodesBefore() {
				return getEntryById(theId).getNodesBefore();
			}

			@Override
			public int getNodesAfter() {
				return getEntryById(theId).getNodesAfter();
			}

			BinaryTreeNode<Map.Entry<K, V>> element(CollectionElement<?> el) {
				return el == null ? null : new EntryElement(el.getElementId());
			}
		}

		class MutableEntryElement extends EntryElement implements MutableBinaryTreeNode<Map.Entry<K, V>> {
			MutableEntryElement(ElementId id) {
				super(id);
			}

			@Override
			public BetterCollection<Map.Entry<K, V>> getCollection() {
				return EntrySet.this;
			}

			@Override
			public String isEnabled() {
				return mutableElement(theId).isEnabled();
			}

			@Override
			public String isAcceptable(Map.Entry<K, V> value) {
				if (value == null)
					return StdMsg.ILLEGAL_ELEMENT;
				String msg = theKeySet.mutableElement(theId).isAcceptable(value.getKey());
				if (msg != null)
					return msg;
				return mutableEntry(theId).isAcceptable(value.getValue());
			}

			@Override
			public void set(Map.Entry<K, V> value) throws UnsupportedOperationException, IllegalArgumentException {
				if (value == null)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				theKeySet.mutableElement(theId).set(value.getKey());
				mutableEntry(theId).set(value.getValue());
			}

			@Override
			public String canRemove() {
				return mutableEntry(theId).canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				mutableEntry(theId).remove();
			}

			@Override
			public MutableBinaryTreeNode<Map.Entry<K, V>> findClosest(Comparable<BinaryTreeNode<Map.Entry<K, V>>> finder, boolean lesser,
				boolean strictly, OptimisticContext ctx) {
				return (MutableBinaryTreeNode<java.util.Map.Entry<K, V>>) super.findClosest(finder, lesser, strictly, ctx);
			}

			@Override
			public MutableBinaryTreeNode<Map.Entry<K, V>> getParent() {
				return (MutableBinaryTreeNode<java.util.Map.Entry<K, V>>) super.getParent();
			}

			@Override
			public MutableBinaryTreeNode<Map.Entry<K, V>> getLeft() {
				return (MutableBinaryTreeNode<java.util.Map.Entry<K, V>>) super.getLeft();
			}

			@Override
			public MutableBinaryTreeNode<Map.Entry<K, V>> getRight() {
				return (MutableBinaryTreeNode<java.util.Map.Entry<K, V>>) super.getRight();
			}

			@Override
			public MutableBinaryTreeNode<Map.Entry<K, V>> getClosest(boolean left) {
				return (MutableBinaryTreeNode<java.util.Map.Entry<K, V>>) super.getClosest(left);
			}

			@Override
			public MutableBinaryTreeNode<Map.Entry<K, V>> getRoot() {
				return (MutableBinaryTreeNode<java.util.Map.Entry<K, V>>) super.getRoot();
			}

			@Override
			public MutableBinaryTreeNode<Map.Entry<K, V>> getSibling() {
				return (MutableBinaryTreeNode<java.util.Map.Entry<K, V>>) super.getSibling();
			}

			@Override
			public MutableBinaryTreeNode<Map.Entry<K, V>> get(int index, OptimisticContext ctx) {
				return (MutableBinaryTreeNode<java.util.Map.Entry<K, V>>) super.get(index, ctx);
			}

			@Override
			MutableBinaryTreeNode<Map.Entry<K, V>> element(CollectionElement<?> el) {
				return el == null ? null : new MutableEntryElement(el.getElementId());
			}
		}
	}
}
