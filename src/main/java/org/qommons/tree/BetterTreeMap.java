package org.qommons.tree;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.*;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A tree-based implementation of {@link BetterSortedMap}
 * 
 * @param <K> The type of keys in the map
 * @param <V> The type of values in the map
 */
public class BetterTreeMap<K, V> extends AbstractIdentifiable implements TreeBasedSortedMap<K, V> {
	private static final String DEFAULT_DESCRIPTION = "better-tree-map";

	/**
	 * Builds a {@link BetterTreeMap}
	 * 
	 * @param <K> The key type for the map
	 * @param <B> The sub-type of this builder
	 */
	public static class Builder<K, B extends Builder<K, ? extends B>> extends BetterTreeSet.Builder<K, BetterTreeSet<K>, B> {
		Builder(Comparator<? super K> compare) {
			super(compare);
			withDescription(DEFAULT_DESCRIPTION);
		}

		/**
		 * @param <V> The value type for the map
		 * @return The new map
		 */
		public <V> BetterTreeMap<K, V> buildMap() {
			return new BetterTreeMap<>(getLocker(), getDescription(), getCompare());
		}

		/**
		 * @param <V> The value type for the map
		 * @param values The initial values for the map
		 * @return The new map
		 */
		public <V> BetterTreeMap<K, V> buildMap(Map<? extends K, ? extends V> values) {
			return (BetterTreeMap<K, V>) buildMap().withAll(values);
		}
	}

	/**
	 * @param <K> The type of key for the map
	 * @param keyCompare The key comparator for the map
	 * @return A builder to build a tree map
	 */
	public static <K> Builder<K, ?> build(Comparator<? super K> keyCompare) {
		return new Builder<>(keyCompare);
	}

	/**
	 * @param <K> The type of key for the map
	 * @param <V> The type of value for the map
	 * @param keyCompare The key comparator for the map
	 * @return The new tree map
	 */
	public static <K, V> BetterTreeMap<K, V> create(Comparator<? super K> keyCompare) {
		return create(keyCompare, null);
	}

	/**
	 * @param <K> The type of key for the map
	 * @param <V> The type of value for the map
	 * @param keyCompare The key comparator for the map
	 * @param build Optional configuration for the new tree map
	 * @return The new tree map
	 */
	public static <K, V> BetterTreeMap<K, V> create(Comparator<? super K> keyCompare, Consumer<? super Builder<K, ?>> build) {
		Builder<K, ?> builder = new Builder<>(keyCompare);
		if (build != null)
			build.accept(builder);
		return builder.buildMap();
	}

	/** The key comparator for the map */
	protected final Comparator<? super K> theCompare;
	private final BetterTreeEntrySet<K, V> theEntries;
	private final KeySet theKeySet;
	private final EntrySet theEntrySet;

	BetterTreeMap(boolean threadSafe, Comparator<? super K> compare, ThreadConstraint threadConstraint) {
		this(v -> {
			if (threadSafe)
				return new StampedLockingStrategy(v, threadConstraint);
			else if (threadConstraint != ThreadConstraint.ANY && threadConstraint.supportsInvoke())
				return ThreadConstrainedLockingStrategy.get(threadConstraint);
			else
				return new FastFailLockingStrategy();
		}, DEFAULT_DESCRIPTION, compare);
	}

	BetterTreeMap(boolean threadSafe, SortedMap<K, ? extends V> map, ThreadConstraint threadConstraint) {
		this(v -> {
			if (threadSafe)
				return new StampedLockingStrategy(v, threadConstraint);
			else if (threadConstraint != ThreadConstraint.ANY && threadConstraint.supportsInvoke())
				return ThreadConstrainedLockingStrategy.get(threadConstraint);
			else
				return new FastFailLockingStrategy();
		}, map);
	}

	/**
	 * @param locker The locking strategy for the map
	 * @param map The initial values for the map
	 */
	public BetterTreeMap(Function<Object, CollectionLockingStrategy> locker, SortedMap<K, ? extends V> map) {
		theCompare = map.comparator();
		initIdentity(Identifiable.baseId(DEFAULT_DESCRIPTION, this));
		theEntries = new BetterTreeEntrySet<>(locker, getIdentity(), map, this::newEntry);
		theKeySet = new KeySet();
		theEntrySet = new EntrySet(this);
	}

	/**
	 * @param locker The locking strategy for the map
	 * @param description A description for the map
	 * @param compare The key sorting for the map
	 */
	protected BetterTreeMap(Function<Object, CollectionLockingStrategy> locker, String description, Comparator<? super K> compare) {
		theCompare = compare;
		initIdentity(Identifiable.baseId(description, this));
		theEntries = new BetterTreeEntrySet<>(locker, description, compare);
		theKeySet = new KeySet();
		theEntrySet = new EntrySet(this);
	}

	@Override
	protected Object createIdentity() {
		throw new IllegalStateException("Should have been initialized");
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
		return wrap(theEntries.getOrAdd(newEntry(key, value), after, before, first, null, null));
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
		try {
			return wrap(theEntries.search(//
				e -> theCompare.compare(key, e.getKey()), BetterSortedList.SortedSearchFilter.OnlyMatch));
		} catch (NullPointerException e) {
			/* A very common use case is to make a sorted map with a lambda comparator (e.g. Comparable::compareTo).
			 * In such cases, any query with a null key will result in a NullPointerException.
			 * Ideally, either every comparator would be able to handle null (which I think it is unreasonable to expect of a developer),
			 * or we would be able to detect whether the comparator handles null and deal with it,
			 * but the Comparator API does not allow this.
			 * 
			 * In many cases (as here), the intended result of query with a null key into a map that does not handle null keys is clear.
			 * So we can either propagate (or not handle) the exception when the comparator throws it, or we can handle it as it should
			 * be handled with a slight performance hit for allowing the NPE to be thrown.
			 */
			if (key == null) // The comparator must not handle nulls
				return null;
			// else That can't be the problem and the dev needs to know there's some other issue
			throw e;
		}
	}

	@Override
	public BinaryTreeEntry<K, V> getEntryById(ElementId entryId) {
		return wrap(theEntries.getElement(entryId));
	}

	@Override
	public BinaryTreeEntry<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, BetterSortedList.SortedSearchFilter filter) {
		return wrap(theEntries.search(search, filter));
	}

	@Override
	public MutableBinaryTreeEntry<K, V> mutableEntry(ElementId entryId) {
		return wrapMutable((TreeEntry) theEntries.getElement(entryId).get());
	}

	@Override
	public String canPut(K key, V value) {
		if (containsKey(key))
			return StdMsg.ELEMENT_EXISTS;
		else
			return null;
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
			theElement = (CollectionElement<? extends BetterMapEntryImpl<K, V>>) node;
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
		public TreeEntry getAdjacent(boolean next) {
			return wrap(theEntryNode.getAdjacent(next));
		}

		@Override
		public int getElementsBefore() {
			return theEntryNode.getElementsBefore();
		}

		@Override
		public int getElementsAfter() {
			return theEntryNode.getElementsAfter();
		}

		@Override
		public boolean getSide() {
			return theEntryNode.getSide();
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
				(init, ctx2) -> wrap(theEntryNode.get(index, OptimisticContext.and(ctx, ctx2))));
		}

		@Override
		public TreeEntry findClosest(Comparable<BinaryTreeNode<V>> finder, boolean lesser, boolean strictly, OptimisticContext ctx) {
			return theEntries.getLocker()
				.doOptimistically(null, //
					(init, ctx2) -> wrap(
						theEntryNode.findClosest(n -> finder.compareTo(wrap(n)), lesser, strictly, OptimisticContext.and(ctx, ctx2))));
		}

		MutableTreeEntry mutable() {
			return (BetterTreeMap<K, V>.MutableTreeEntry) mutable(theEntries, BetterTreeMap.this::values);
		}

		@Override
		protected MutableBinaryTreeEntry<K, V> createMutableHandle(BetterSet<? extends Map.Entry<K, V>> entrySet,
			Supplier<BetterCollection<V>> values) {
			return new MutableTreeEntry(this);
		}

		@Override
		protected BinaryTreeNode<K> keyHandle() {
			return (BinaryTreeNode<K>) super.keyHandle();
		}

		@Override
		protected BinaryTreeNode<K> makeKeyHandle() {
			return new BinaryTreeKeyHandle(this);
		}

		MutableBinaryTreeNode<K> mutableKeyHandle() {
			return mutableKeyHandle(theEntries, BetterTreeMap.this::keySet);
		}

		@Override
		protected MutableBinaryTreeNode<K> mutableKeyHandle(BetterSet<? extends Map.Entry<K, V>> entrySet, Supplier<BetterSet<K>> keySet) {
			return (MutableBinaryTreeNode<K>) super.mutableKeyHandle(entrySet, keySet);
		}

		@Override
		protected MutableCollectionElement<K> createMutableKeyHandle(BetterSet<? extends Map.Entry<K, V>> entrySet,
			Supplier<BetterSet<K>> keySet) {
			MutableCollectionElement<? extends Map.Entry<K, V>> mutableEntryEl = entrySet.mutableElement(getElementId());
			return new MutableBinaryTreeKeyHandle(this, mutableEntryEl, keySet);
		}
	}

	class BinaryTreeKeyHandle extends BetterMapEntryImpl.BetterMapEntryKeyHandle<K> implements BinaryTreeNode<K> {
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
		public BinaryTreeNode<K> getAdjacent(boolean next) {
			return key(getEntry().getAdjacent(next));
		}

		@Override
		public int getElementsBefore() {
			return getEntry().getElementsBefore();
		}

		@Override
		public int getElementsAfter() {
			return getEntry().getElementsAfter();
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

		private BinaryTreeNode<K> key(BetterTreeMap<K, ?>.TreeEntry entry) {
			return entry == null ? null : entry.keyHandle();
		}
	}

	class MutableBinaryTreeKeyHandle extends BetterMapEntryImpl.BetterMapEntryMutableKeyHandle<K> implements MutableBinaryTreeNode<K> {
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
		public MutableBinaryTreeNode<K> getAdjacent(boolean next) {
			return key(getEntry().getAdjacent(next));
		}

		@Override
		public int getElementsBefore() {
			return getEntry().getElementsBefore();
		}

		@Override
		public int getElementsAfter() {
			return getEntry().getElementsAfter();
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
		public BinaryTreeNode<K> immutable() {
			return getEntry().keyHandle();
		}

		private MutableBinaryTreeNode<K> key(BetterTreeMap<K, ?>.TreeEntry entry) {
			return entry == null ? null : entry.mutableKeyHandle();
		}
	}

	static class BetterTreeEntrySet<K, V> extends BetterTreeSet<Map.Entry<K, V>> {
		BetterTreeEntrySet(Function<Object, CollectionLockingStrategy> locker, Object mapId, Comparator<? super K> compare) {
			super(locker, Identifiable.wrap(mapId, "entrySet"), (e1, e2) -> compare.compare(e1.getKey(), e2.getKey()));
		}

		BetterTreeEntrySet(Function<Object, CollectionLockingStrategy> locker, Object mapId, SortedMap<K, ? extends V> map,
			BiFunction<K, V, Map.Entry<K, V>> creator) {
			this(locker, Identifiable.wrap(mapId, "entrySet"), map.comparator());
			initialize((Set<? extends Map.Entry<K, V>>) map.entrySet(), entry -> creator.apply(entry.getKey(), entry.getValue()), false);
		}

		@Override
		public SortedSetSplitSpliterable<Map.Entry<K, V>> subList(int fromIndex, int toIndex) {
			return super.subList(fromIndex, toIndex);
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
		public int getElementsBefore() {
			return getEntry().getElementsBefore();
		}

		@Override
		public int getElementsAfter() {
			return getEntry().getElementsAfter();
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
		public MutableBinaryTreeEntry<K, V> getAdjacent(boolean next) {
			return wrapMutable(getEntry().getAdjacent(next));
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
				(init, ctx2) -> wrapMutable(getEntry().get(index, OptimisticContext.and(ctx, ctx2))));
		}

		@Override
		public MutableBinaryTreeEntry<K, V> findClosest(Comparable<BinaryTreeNode<V>> finder, boolean lesser, boolean strictly,
			OptimisticContext ctx) {
			return theEntries.getLocker().doOptimistically(null, //
				(init, ctx2) -> wrapMutable(
					getEntry().findClosest(n -> finder.compareTo(n), lesser, strictly, OptimisticContext.and(ctx, ctx2))));
		}

		@Override
		public TreeEntry immutable() {
			return getEntry();
		}
	}

	class KeySet extends AbstractIdentifiable implements TreeBasedSet<K> {
		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(BetterTreeMap.this.getIdentity(), "keySet");
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theEntries.getThreadConstraint();
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
		public Transaction tryLock(boolean write, Object cause) {
			return theEntries.tryLock(write, cause);
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
		public int indexFor(Comparable<? super K> search) {
			return theEntries.indexFor(e -> search.compareTo(e.getKey()));
		}

		@Override
		public BinaryTreeNode<K> getRoot() {
			return handleFor(theEntries.getRoot());
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
		public MutableBinaryTreeNode<K> mutableElement(ElementId id) {
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
		public BinaryTreeNode<K> search(Comparable<? super K> search, BetterSortedList.SortedSearchFilter filter) {
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
		public String canAdd(K value, ElementId after, ElementId before) {
			return theEntries.canAdd(new SimpleMapEntry<>(value, null), after, before);
		}

		@Override
		public BinaryTreeNode<K> addElement(K value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			BinaryTreeNode<Map.Entry<K, V>> entry = theEntries.addElement(newEntry(value, null), after, before, first);
			wrap(entry); // Initialize the element
			return entry == null ? null : handleFor(entry);
		}

		@Override
		public BinaryTreeNode<K> getOrAdd(K value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
			TreeEntry entry = (BetterTreeMap<K, V>.TreeEntry) BetterTreeMap.this.getOrPutEntry(value, null, after, before, first, preAdd,
				postAdd);
			return entry == null ? null : entry.keyHandle();
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
		public <X> boolean repair(ElementId element, RepairListener<K, X> listener) {
			return theEntries.repair(element, listener == null ? null : new EntryRepairListener<>(listener));
		}

		@Override
		public boolean checkConsistency() {
			return theEntries.checkConsistency();
		}

		@Override
		public <X> boolean repair(RepairListener<K, X> listener) {
			return theEntries.repair(listener == null ? null : new EntryRepairListener<>(listener));
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

		private class EntryRepairListener<X> implements RepairListener<Map.Entry<K, V>, X> {
			private final RepairListener<K, X> theKeyListener;

			EntryRepairListener(RepairListener<K, X> keyListener) {
				theKeyListener = keyListener;
			}

			@Override
			public X removed(CollectionElement<Map.Entry<K, V>> element) {
				return theKeyListener.removed(handleFor(element));
			}

			@Override
			public void disposed(Entry<K, V> value, X data) {
				theKeyListener.disposed(value.getKey(), data);
			}

			@Override
			public void transferred(CollectionElement<Entry<K, V>> element, X data) {
				theKeyListener.transferred(handleFor(element), data);
			}
		}
	}

	class EntrySet extends BetterSortedMap.BetterSortedEntrySet<K, V> implements TreeBasedSet<Map.Entry<K, V>> {
		EntrySet(BetterSortedMap<K, V> map) {
			super(map);
		}

		@Override
		public BinaryTreeNode<Entry<K, V>> getRoot() {
			BinaryTreeNode<?> root = theEntries.getRoot();
			return root == null ? null : getElement(root.getElementId());
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
		public BinaryTreeNode<Map.Entry<K, V>> search(Comparable<? super Map.Entry<K, V>> search,
			BetterSortedList.SortedSearchFilter filter) {
			return (BinaryTreeNode<Map.Entry<K, V>>) super.search(search, filter);
		}

		@Override
		public BinaryTreeNode<Map.Entry<K, V>> getElement(Map.Entry<K, V> value, boolean first) {
			return (BinaryTreeNode<Map.Entry<K, V>>) super.getElement(value, first);
		}

		@Override
		public BinaryTreeNode<Map.Entry<K, V>> getElement(ElementId id) {
			return new EntryElement(getEntryById(id));
		}

		@Override
		public BinaryTreeNode<Map.Entry<K, V>> getOrAdd(Map.Entry<K, V> value, ElementId after, ElementId before, boolean first,
			Runnable preAdd, Runnable postAdd) {
			return (BinaryTreeNode<Map.Entry<K, V>>) super.getOrAdd(value, after, before, first, preAdd, postAdd);
		}

		@Override
		public BinaryTreeNode<Map.Entry<K, V>> splitBetween(ElementId element1, ElementId element2) {
			BinaryTreeNode<?> found = theEntries.splitBetween(element1, element2);
			return found == null ? null : getElement(found.getElementId());
		}

		@Override
		public MutableBinaryTreeNode<Map.Entry<K, V>> mutableElement(ElementId id) {
			return new MutableEntryElement(mutableEntry(id));
		}

		@Override
		public BinaryTreeNode<Map.Entry<K, V>> addElement(Map.Entry<K, V> value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (BinaryTreeNode<Map.Entry<K, V>>) super.addElement(value, after, before, first);
		}

		@Override
		protected BinaryTreeNode<Map.Entry<K, V>> entryFor(MapEntryHandle<K, V> entry) {
			return entry == null ? null : new EntryElement((BinaryTreeEntry<K, V>) entry);
		}

		class EntryElement implements BinaryTreeNode<Map.Entry<K, V>> {
			private final BinaryTreeEntry<K, V> theEntry;

			EntryElement(BinaryTreeEntry<K, V> entry) {
				theEntry = entry;
			}

			protected BinaryTreeEntry<K, V> getEntry() {
				return theEntry;
			}

			@Override
			public ElementId getElementId() {
				return theEntry.getElementId();
			}

			@Override
			public Map.Entry<K, V> get() {
				return theEntry;
			}

			@Override
			public BinaryTreeNode<Map.Entry<K, V>> getParent() {
				return entryFor(theEntry.getParent());
			}

			@Override
			public BinaryTreeNode<Map.Entry<K, V>> getLeft() {
				return entryFor(theEntry.getLeft());
			}

			@Override
			public BinaryTreeNode<Map.Entry<K, V>> getRight() {
				return entryFor(theEntry.getRight());
			}

			@Override
			public BinaryTreeNode<Map.Entry<K, V>> getAdjacent(boolean next) {
				return entryFor(theEntry.getAdjacent(next));
			}

			@Override
			public int getElementsBefore() {
				return theEntry.getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return theEntry.getElementsAfter();
			}

			@Override
			public int size() {
				return theEntry.size();
			}

			@Override
			public BinaryTreeNode<Map.Entry<K, V>> getRoot() {
				return entryFor(theEntry.getRoot());
			}

			@Override
			public boolean getSide() {
				return theEntry.getSide();
			}

			@Override
			public BinaryTreeNode<Map.Entry<K, V>> getSibling() {
				return entryFor(theEntry.getSibling());
			}

			@Override
			public BinaryTreeNode<Map.Entry<K, V>> get(int index, OptimisticContext ctx) {
				return entryFor(theEntry.get(index, ctx));
			}

			protected BinaryTreeNode<Map.Entry<K, V>> element(BinaryTreeEntry<K, V> entry) {
				return entry == null ? null : new EntryElement(entry);
			}
		}

		class MutableEntryElement extends EntryElement implements MutableBinaryTreeNode<Map.Entry<K, V>> {
			MutableEntryElement(MutableBinaryTreeEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected MutableBinaryTreeEntry<K, V> getEntry() {
				return (MutableBinaryTreeEntry<K, V>) super.getEntry();
			}

			@Override
			public String isEnabled() {
				return getEntry().isEnabled();
			}

			@Override
			public String isAcceptable(Map.Entry<K, V> value) {
				if (value == null)
					return StdMsg.ILLEGAL_ELEMENT;
				String msg = theKeySet.mutableElement(getEntry().getElementId()).isAcceptable(value.getKey());
				if (msg != null)
					return msg;
				return getEntry().isAcceptable(value.getValue());
			}

			@Override
			public void set(Map.Entry<K, V> value) throws UnsupportedOperationException, IllegalArgumentException {
				if (value == null)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				theKeySet.mutableElement(getEntry().getElementId()).set(value.getKey());
				getEntry().set(value.getValue());
			}

			@Override
			public String canRemove() {
				return getEntry().canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				getEntry().remove();
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
			public MutableBinaryTreeNode<Map.Entry<K, V>> getAdjacent(boolean next) {
				return (MutableBinaryTreeNode<java.util.Map.Entry<K, V>>) super.getAdjacent(next);
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
			protected MutableBinaryTreeNode<Map.Entry<K, V>> element(BinaryTreeEntry<K, V> entry) {
				return entry == null ? null : new MutableEntryElement((MutableBinaryTreeEntry<K, V>) entry);
			}
		}
	}
}
