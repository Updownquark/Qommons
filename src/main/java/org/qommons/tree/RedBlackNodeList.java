package org.qommons.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.*;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.ValueStoredCollection.RepairListener;

/**
 * An abstract implementation of a tree-backed collection. Since fast indexing is supported, BetterList is implemented.
 * 
 * @param <E> The type of values in the list
 */
public abstract class RedBlackNodeList<E> extends AbstractIdentifiable implements TreeBasedList<E> {
	/**
	 * @param <E> The type of elements for the list
	 * @param <L> The type of the list
	 * @param <B> The sub-type of this builder
	 */
	public static abstract class RBNLBuilder<E, L extends RedBlackNodeList<E>, B extends RBNLBuilder<E, L, ? extends B>>
		extends CollectionBuilder.Default<B> {

		/** @param initDescrip The initial (default) description for the list */
		protected RBNLBuilder(String initDescrip) {
			super(initDescrip);
		}

		/**
		 * Builds the list
		 * 
		 * @return The new list
		 */
		public abstract L build();

		/**
		 * Builds the list
		 * 
		 * @param values The initial values for the new list
		 * @return The new list
		 */
		public L build(Iterable<? extends E> values) {
			L built = build();
			built.initialize(values, v -> v, false);
			return built;
		}
	}

	private final RedBlackTree<E> theTree;
	private final CollectionLockingStrategy theLocker;

	/**
	 * Creates a list
	 * 
	 * @param locker The locking strategy to use
	 * @param description The description for this list
	 */
	protected RedBlackNodeList(Function<Object, CollectionLockingStrategy> locker, String description) {
		theLocker = locker.apply(this);
		theTree = new RedBlackTree<>();
		theTree.forBetterApi(theLocker, NodeId::new);
		initIdentity(Identifiable.baseId(description, this));
	}

	/**
	 * Creates a list
	 * 
	 * @param locker The locking strategy to use
	 * @param identity The identity for this list
	 */
	protected RedBlackNodeList(Function<Object, CollectionLockingStrategy> locker, Object identity) {
		theLocker = locker.apply(this);
		theTree = new RedBlackTree<>();
		theTree.forBetterApi(theLocker, NodeId::new);
		initIdentity(identity);
	}

	/**
	 * Initializes this tree with the contents of the given iterable. No calls are made to {@link #add(Object)} or any other method in this
	 * list, so no filtering is possible.
	 * 
	 * @param <E2> The type of values to initialize the collection with
	 * @param values The values to initialize this list with
	 * @param map The map to apply to the values before insertion
	 * @param lock Whether to lock this collection for the operation
	 * @return Whether the list now has values
	 */
	protected <E2 extends E> boolean initialize(Iterable<E2> values, Function<? super E2, ? extends E> map, boolean lock) {
		if (theTree.getRoot() != null)
			throw new IllegalStateException("Cannot initialize a non-empty list");
		try (Transaction t = lock ? Transactable.lock(values, false, null) : Transaction.NONE) {
			if (values instanceof RedBlackNodeList) {
				RedBlackNodeList<E2> rbnl = (RedBlackNodeList<E2>) values;
				if (rbnl.theTree.getRoot() == null)
					return false;
				theTree.setRoot(RedBlackNode.deepCopy(rbnl.theTree.getRoot(), theTree, map));
			} else if (values != null)
				RedBlackNode.build(theTree, values, map);
			if (lock && theTree.getRoot() != null)
				theLocker.modified();
			return theTree.getRoot() != null;
		}
	}

	/** @return This collection's locking strategy */
	protected CollectionLockingStrategy getLocker() {
		return theLocker;
	}

	@Override
	protected Object createIdentity() {
		throw new IllegalStateException("Should have been initialized");
	}

	@Override
	public BinaryTreeNode<E> getRoot() {
		return theTree.getRoot();
	}

	/**
	 * @param index The index of the node to get
	 * @return The tree node in this list's backing tree structure at the given index
	 */
	public BinaryTreeNode<E> getNode(int index) {
		if (theTree.getRoot() == null)
			throw new IndexOutOfBoundsException(index + " of 0");
		return theLocker.doOptimistically(null, (init, ctx) -> theTree.getRoot().get(index, ctx));
	}

	/** For unit tests. Ensures the integrity of the collection. */
	public void checkValid() {
		if (theTree.getRoot() != null)
			theTree.getRoot().checkValid();
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theLocker.getThreadConstraint();
	}

	@Override
	public boolean isLockSupported() {
		return theLocker.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theLocker.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theLocker.tryLock(write, cause);
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return theLocker.getCurrentCauses();
	}

	@Override
	public CoreId getCoreId() {
		return theLocker.getCoreId();
	}

	@Override
	public long getStamp() {
		return theLocker.getStamp();
	}

	@Override
	public int size() {
		return theTree.size();
	}

	@Override
	public boolean isEmpty() {
		return theTree.getRoot() == null;
	}

	@Override
	public BinaryTreeNode<E> getTerminalElement(boolean first) {
		return theTree.getTerminal(first);
	}

	/**
	 * Same as {@link #getTerminalElement(boolean)}, but returns a mutable element
	 * 
	 * @param first Whether to get the first or the last element in this collection
	 * @return The first or the last element in this collection
	 */
	public MutableBinaryTreeNode<E> getMutableTerminal(boolean first) {
		return wrapMutable(theTree.getTerminal(first));
	}

	@Override
	public Object[] toArray() {
		return TreeBasedList.super.toArray();
	}

	// The tree's iterators are more performant than the default sequence-based iterators

	@Override
	public Iterator<E> iterator() {
		return theTree.iterator();
	}

	@Override
	public Iterator<E> iterator(boolean fromBeginning) {
		return theTree.iterator(fromBeginning);
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return TreeBasedList.super.toArray(a);
	}

	@Override
	public BinaryTreeNode<E> getElement(ElementId id) {
		return checkNode(id, true).theNode;
	}

	@Override
	public BinaryTreeNode<E> getElement(int index) {
		RedBlackNode<E> root = theTree.getRoot();
		if (root == null)
			throw new IndexOutOfBoundsException(index + " of 0");
		return theLocker.doOptimistically(null, (init, ctx) -> root.get(index, ctx));
	}

	@Override
	public MutableBinaryTreeNode<E> mutableElement(ElementId id) {
		return mutableNodeFor(id);
	}

	@Override
	public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return BetterList.of(getElement(sourceEl));
		return BetterList.empty();
	}

	@Override
	public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this) {
			if (!(localElement instanceof RedBlackNodeList.NodeId) || ((NodeId) localElement).getList() != this)
				throw new NoSuchElementException(localElement + " does not belong to this collection");
			return BetterList.of(localElement);
		}
		return BetterList.empty();
	}

	@Override
	public ElementId getEquivalentElement(ElementId equivalentEl) {
		if (!(equivalentEl instanceof RedBlackNodeList.NodeId) || ((NodeId) equivalentEl).getList() != this)
			return null;
		return equivalentEl;
	}

	@Override
	public String canAdd(E value, ElementId after, ElementId before) {
		return null;
	}

	@Override
	public BinaryTreeNode<E> addElement(E value, boolean first) {
		return addElement(value, null, null, first);
	}

	@Override
	public BinaryTreeNode<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		return addNode(value, after, before, first);
	}

	/**
	 * Same as {@link #addElement(Object, boolean)}, but returns a mutable element
	 * 
	 * @param value The value to add
	 * @param first Whether to prefer a lower position over a higher one
	 * @return The element at which the value was added, or null if the value was not added due to a non-erroring condition
	 */
	public MutableBinaryTreeNode<E> addElement2(E value, boolean first) {
		return addElement2(value, null, null, first);
	}

	/**
	 * Same as {@link #addElement(Object, ElementId, ElementId, boolean)}, but returns a mutable element
	 * 
	 * @param value The value to add
	 * @param after The element currently occupying the position before which (exclusive) the value's insertion is desirable, or null if the
	 *        element may be added at the end of the collection
	 * @param before The element currently occupying the position before which (exclusive) the value's insertion is desirable, or null if
	 *        the element may be added at the end of the collection
	 * @param first Whether to prefer a lower position over a higher one
	 * @return The element at which the value was added, or null if the value was not added due to a non-erroring condition
	 * @throws IllegalArgumentException If either <code>after</code> or <code>before</code> is not null and not an element in this
	 *         collection
	 */
	public MutableBinaryTreeNode<E> addElement2(E value, ElementId after, ElementId before, boolean first)
		throws IllegalArgumentException {
		return wrapMutable(addNode(value, after, before, first));
	}

	private RedBlackNode<E> addNode(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		RedBlackNode<E> newNode = new RedBlackNode<>(theTree, value);
		try (Transaction t = theLocker.lock(true, null)) {
			if (first && after != null) {
				if (!((NodeId) after).theNode.isPresent())
					throw new IllegalArgumentException("Unrecognized element");
				((NodeId) after).theNode.add(newNode, false);
			} else if (!first && before != null) {
				if (!((NodeId) before).theNode.isPresent())
					throw new IllegalArgumentException("Unrecognized element");
				((NodeId) before).theNode.add(newNode, true);
			} else if (theTree.getRoot() == null)
				theTree.setRoot(newNode);
			else
				theTree.getTerminal(first).add(newNode, first);
			theLocker.modified();
		}
		return newNode;
	}

	@Override
	public BinaryTreeNode<E> splitBetween(ElementId element1, ElementId element2) {
		return theLocker.doOptimistically(null,
			(init, ctx) -> RedBlackNode.splitBetween(((NodeId) element1).theNode, ((NodeId) element2).theNode, ctx));
	}

	/**
	 * <p>
	 * Searches in this list for a value, using the stored order of the elements to optimize the search.
	 * </p>
	 * 
	 * <p>
	 * If this list's element order is not enforced (as in a {@link BetterSortedSet}) or it has become corrupt (i.e. in need of
	 * {@link ValueStoredCollection#repair(RepairListener) repair}, this operation may fail; i.e. it may return null or a non-matching node
	 * when the value exists in the list or a closer node exists.
	 * </p>
	 * 
	 * @param search The search to use to search this list
	 * @param filter The filter on the kind of node to return
	 * @return The (or a) node in this list for which <code>search{@link Comparable#compareTo(Object) compareTo()}</code> returns zero, or
	 *         the node in this list closest to such a hypothetical node matching the given filter.
	 */
	public BinaryTreeNode<E> search(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter) {
		if (isEmpty())
			return null;
		return getLocker().doOptimistically(null, //
			(init, ctx) -> {
				BinaryTreeNode<E> end = getTerminalElement(false);
				if (end == null)
					return null;
				int comp = search.compareTo(end.get());
				if (comp == 0)
					return end;
				else if (comp > 0) {
					switch (filter) {
					case Less:
					case PreferLess:
					case PreferGreater:
						return end;
					case Greater:
					case OnlyMatch:
						return null;
					}
				}
				BinaryTreeNode<E> begin = getTerminalElement(true);
				if (!begin.equals(end))
					comp = search.compareTo(begin.get());
				if (comp == 0)
					return begin;
				else if (comp < 0) {
					switch (filter) {
					case Greater:
					case PreferGreater:
					case PreferLess:
						return begin;
					case Less:
					case OnlyMatch:
						return null;
					}
				}
				BinaryTreeNode<E> root = getRoot();
				BinaryTreeNode<E> node = root.findClosest(//
					n -> search.compareTo(n.get()), filter.less.withDefault(true), filter.strict, ctx);
				if (node != null) {
					if (search.compareTo(node.get()) == 0) {
						if (filter.strict) {
							// Interpret this to mean that the caller is interested in the first or last node matching the search
							BinaryTreeNode<E> adj = node.getAdjacent(filter == BetterSortedList.SortedSearchFilter.Greater);
							while (adj != null && search.compareTo(adj.get()) == 0) {
								node = adj;
								adj = node.getAdjacent(filter == BetterSortedList.SortedSearchFilter.Greater);
							}
						}
					} else if (filter == BetterSortedList.SortedSearchFilter.OnlyMatch)
						node = null;
				}
				return node;
			});
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		try (Transaction t = lock(true, null)) {
			if (theTree.getRoot() == null && !isContentControlled()) {
				return initialize(c, e -> e, true); // Already locked, but apply the stamp
			} else
				return TreeBasedList.super.addAll(c);
		}
	}

	@Override
	public BetterList<E> withAll(Collection<? extends E> values) {
		addAll(values);
		return this;
	}

	@Override
	public void clear() {
		try (Transaction t = lock(true, null)) {
			if (theTree.getRoot() != null)
				theLocker.modified();
			theTree.setRoot(null);
		}
	}

	/**
	 * Checks the collection's storage structure for consistency at the given element
	 * 
	 * @param element The element to check the structure's consistency at
	 * @param compare The comparator by which this collection is ordered
	 * @param distinct Whether this collection prevents duplicates
	 * @return Whether the collection's storage appears to be consistent at the given element
	 * @see ValueStoredCollection#isConsistent(ElementId)
	 */
	protected boolean isConsistent(ElementId element, Comparator<? super E> compare, boolean distinct) {
		CollectionElement<E> el = getElement(element);
		CollectionElement<E> adj = el.getAdjacent(false);
		if (adj != null) {
			int comp = compare.compare(adj.get(), el.get());
			if (comp > 0 || (distinct && comp == 0))
				return false;
		}
		adj = el.getAdjacent(true);
		if (adj != null) {
			int comp = compare.compare(adj.get(), el.get());
			if (comp < 0 || (distinct && comp == 0))
				return false;
		}
		return true;
	}

	/**
	 * Searches for and fixes any inconsistencies in the collection's storage structure at the given element.
	 * 
	 * @param <X> The type of the data transferred for the listener
	 * @param element The element at which to check and repair the collection
	 * @param compare The comparator by which this collection is ordered
	 * @param distinct Whether this collection prevents duplicates
	 * @param listener The listener to monitor repairs. May be null.
	 * @return Whether any inconsistencies were found
	 * @see ValueStoredCollection#repair(org.qommons.collect.ValueStoredCollection.RepairListener)
	 */
	protected <X> boolean repair(ElementId element, Comparator<? super E> compare, boolean distinct,
		ValueStoredCollection.RepairListener<E, X> listener) {
		try (Transaction t = lock(true, null)) {
			boolean repaired = theTree.repair(//
				checkNode(element, true).theNode, compare, distinct, new TreeRepairListener<>(listener));
			if (repaired)
				theLocker.modified();
			return repaired;
		}
	}

	/**
	 * Searches for any inconsistencies in the entire collection's storage structure. This typically takes linear time.
	 * 
	 * @param compare The comparator by which this collection is ordered
	 * @param distinct Whether this collection prevents duplicates
	 * @return Whether any inconsistency was found in the collection
	 * @see ValueStoredCollection#checkConsistency()
	 */
	protected boolean checkConsistency(Comparator<? super E> compare, boolean distinct) {
		try (Transaction t = lock(false, null)) {
			E previous = null;
			boolean hasPrevious = false;
			for (E value : this) {
				if (hasPrevious) {
					int comp = compare.compare(value, previous);
					if (comp < 0 || (distinct && comp == 0))
						return true;
				}
			}
			return false;
		}
	}

	/**
	 * Searches for and fixes any inconsistencies in the collection's storage structure.
	 * 
	 * @param <X> The type of the data transferred for the listener
	 * @param compare The comparator by which this collection is ordered
	 * @param distinct Whether this collection prevents duplicates
	 * @param listener The listener to monitor repairs. May be null.
	 * @return Whether any inconsistencies were found
	 * @see ValueStoredCollection#repair(org.qommons.collect.ValueStoredCollection.RepairListener)
	 */
	protected <X> boolean repair(Comparator<? super E> compare, boolean distinct, ValueStoredCollection.RepairListener<E, X> listener) {
		try (Transaction t = lock(true, null)) {
			boolean repaired = theTree.repair(compare, distinct, new TreeRepairListener<>(listener));
			if (repaired)
				theLocker.modified();
			return repaired;
		}
	}

	private class TreeRepairListener<X> implements RedBlackTree.RepairListener<E, X> {
		private final ValueStoredCollection.RepairListener<E, X> theWrapped;

		TreeRepairListener(RepairListener<E, X> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public X removed(RedBlackNode<E> node) {
			return theWrapped == null ? null : theWrapped.removed(node);
		}

		@Override
		public void disposed(E value, X data) {
			if (theWrapped != null)
				theWrapped.disposed(value, data);
		}

		@Override
		public void transferred(RedBlackNode<E> node, X data) {
			if (theWrapped != null)
				theWrapped.transferred(node, data);
		}
	}

	@Override
	public int hashCode() {
		return BetterCollection.hashCode(this);
	}

	@Override
	public boolean equals(Object o) {
		return BetterCollection.equals(this, o);
	}

	@Override
	public String toString() {
		return BetterCollection.toString(this);
	}

	/**
	 * @param element The element ID supplied by this collection
	 * @return A {@link MutableBinaryTreeNode} for the element with the given ID
	 */
	protected MutableBinaryTreeNode<E> mutableNodeFor(ElementId element) {
		return wrapMutable(checkNode(element, true).theNode);
	}

	/**
	 * @param node The node representing an element supplied by this collection
	 * @return A {@link MutableBinaryTreeNode} for the element with the given ID
	 */
	protected MutableBinaryTreeNode<E> mutableNodeFor(BinaryTreeNode<E> node) {
		return wrapMutable(checkNode(node.getElementId(), true).theNode);
	}

	private NodeId checkNode(ElementId id, boolean requirePresent) {
		try {
			NodeId nodeId = (NodeId) id;
			if (nodeId.theNode.getTree() != theTree)
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			if (requirePresent && !nodeId.isPresent())
				throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
			return nodeId;
		} catch (ClassCastException e) {
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		}
	}

	private MutableNodeWrapper wrapMutable(RedBlackNode<E> node) {
		return node == null ? null : new MutableNodeWrapper(node);
	}

	class NodeId implements ElementId {
		final RedBlackNode<E> theNode;

		NodeId(RedBlackNode<E> node) {
			theNode = node;
		}

		RedBlackNodeList<E> getList() {
			return RedBlackNodeList.this;
		}

		@Override
		public boolean isPresent() {
			return theNode.isPresent();
		}

		@Override
		public int compareTo(ElementId id) {
			NodeId nodeId = (NodeId) id;
			if (theTree != nodeId.theNode.getTree())
				throw new IllegalArgumentException("Cannot compare nodes from different trees");
			return theLocker.doOptimistically(0, (init, ctx) -> {
				if (theNode == nodeId.theNode)
					return 0;
				else if (isPresent()) {
					if (id.isPresent()) {
						return RedBlackNode.compare(theNode, nodeId.theNode, ctx);
					} else {
						// A couple little optimizations for common uses
						if (nodeId.theNode.getAdjacent(false) == theNode)
							return -1;
						else if (nodeId.theNode.getAdjacent(true) == theNode)
							return 1;
						int compare = theNode.getElementsBefore(ctx) - nodeId.theNode.getElementsBefore(ctx);
						compare = compare + 1;
						if (compare == 0)
							compare = -1;
						return compare;
					}
				} else {
					// A couple little optimizations for common uses
					if (theNode.getAdjacent(false) == nodeId.theNode)
						return 1;
					else if (theNode.getAdjacent(true) == nodeId.theNode)
						return -1;
					int compare = theNode.getElementsBefore(ctx) - nodeId.theNode.getElementsBefore(ctx);
					// We can assume the other ID is present, because tree nodes cannot be compared
					// if the tree has been changed since the node was removed.
					// So if one node has been removed and then another one has been removed, this call is invalid
					// and one of the getNodesBefore calls above should have thrown an exception
					compare = compare - 1;
					if (compare == 0)
						compare = 1;
					return compare;
				}
			});
		}

		@Override
		public int hashCode() {
			return theNode.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof RedBlackNodeList.NodeId && theNode == ((NodeId) o).theNode;
		}

		@Override
		public String toString() {
			String index = theLocker.doOptimistically("", (init, ctx) -> {
				if (theNode.isPresent())
					return "" + theNode.getElementsBefore(ctx);
				else
					return "removed";
			});
			return new StringBuilder().append('[').append(index).append("]: ").append(theNode.get()).toString();
		}
	}

	class MutableNodeWrapper implements MutableBinaryTreeNode<E> {
		private final RedBlackNode<E> theNode;

		MutableNodeWrapper(RedBlackNode<E> node) {
			theNode = node;
		}

		@Override
		public int size() {
			return theNode.size();
		}

		@Override
		public boolean getSide() {
			return theNode.getSide();
		}

		@Override
		public int getElementsBefore() {
			return theLocker.doOptimistically(0, (__, ctx) -> theNode.getElementsBefore(ctx));
		}

		@Override
		public int getElementsAfter() {
			return theLocker.doOptimistically(0, (__, ctx) -> theNode.getElementsAfter(ctx));
		}

		@Override
		public ElementId getElementId() {
			return theNode.getElementId();
		}

		@Override
		public E get() {
			return theNode.get();
		}

		@Override
		public MutableBinaryTreeNode<E> getParent() {
			return wrapMutable(theNode.getParent());
		}

		@Override
		public MutableBinaryTreeNode<E> getLeft() {
			return wrapMutable(theNode.getLeft());
		}

		@Override
		public MutableBinaryTreeNode<E> getRight() {
			return wrapMutable(theNode.getRight());
		}

		@Override
		public MutableBinaryTreeNode<E> getAdjacent(boolean next) {
			return wrapMutable(theNode.getAdjacent(next));
		}

		@Override
		public MutableBinaryTreeNode<E> getRoot() {
			return wrapMutable(theNode.getRoot());
		}

		@Override
		public MutableBinaryTreeNode<E> getSibling() {
			return wrapMutable(theNode.getSibling());
		}

		@Override
		public MutableBinaryTreeNode<E> get(int index, OptimisticContext ctx) {
			return theLocker.doOptimistically(null, (init, ctx2) -> wrapMutable(theNode.get(index, OptimisticContext.and(ctx, ctx2))));
		}

		@Override
		public MutableBinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean strictly,
			OptimisticContext ctx) {
			return theLocker.doOptimistically(null,
				(init, ctx2) -> mutableNodeFor(theNode.findClosest(finder, lesser, strictly, OptimisticContext.and(ctx, ctx2))));
		}

		@Override
		public String isEnabled() {
			return null;
		}

		@Override
		public String isAcceptable(E value) {
			return null;
		}

		private boolean isPresent() {
			return theNode.isPresent();
		}

		@Override
		public void set(E value) {
			try (Transaction t = lock(true, null)) {
				if (!isPresent())
					throw new IllegalStateException("This element has been removed");
				theNode.setValue(value);
				theLocker.modified();
			}
		}

		@Override
		public String canRemove() {
			return null;
		}

		@Override
		public void remove() {
			try (Transaction t = lock(true, null)) {
				if (!isPresent())
					throw new IllegalStateException("This element has been removed");
				theNode.delete();
				theLocker.modified();
			}
		}
	}
}
