package org.qommons.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.function.Function;

import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.OptimisticContext;
import org.qommons.collect.SplitSpliterable;
import org.qommons.collect.StampedLockingStrategy;

/**
 * A {@link org.qommons.collect.BetterList} backed by a tree structure that sorts its values, with duplicates allowed
 * 
 * @param <E> The type of value in the list
 */
public class SortedTreeList<E> extends RedBlackNodeList<E> implements TreeBasedSortedList<E>, BetterSortedList<E> {
	private static final String DEFAULT_DESCRIP = "sorted-tree-list";

	/**
	 * @param <E> The type of elements in the list
	 * @param <L> The sub-type of the list
	 * @param <B> The sub-type of this builder
	 */
	public static class Builder<E, L extends SortedTreeList<E>, B extends Builder<E, L, ? extends B>> extends RBNLBuilder<E, L, B> {
		private final Comparator<? super E> theCompare;

		/** @param compare The comparator for the list's ordering */
		protected Builder(Comparator<? super E> compare) {
			super(DEFAULT_DESCRIP);
			theCompare = compare;
		}

		/** @return The comparator for the new list */
		protected Comparator<? super E> getCompare() {
			return theCompare;
		}

		@Override
		public L build() {
			return (L) new SortedTreeList<>(getLocker(), getDescription(), theCompare);
		}

		/**
		 * @param values The initial values for the list
		 * @return The new list
		 */
		public L build(SortedSet<? extends E> values) {
			SortedTreeList<E> list = build();
			list.initialize(values, v -> v);
			return (L) list;
		}

		/**
		 * @param values The initial values for the list
		 * @return The new list
		 */
		public L build(BetterSortedList<? extends E> values) {
			SortedTreeList<E> list = build();
			list.initialize(values, v -> v);
			return (L) list;
		}
	}

	/**
	 * @param <E> The type of elements for the list
	 * @param compare The comparator for the list's ordering
	 * @return The builder for the list
	 */
	public static <E> Builder<E, SortedTreeList<E>, ?> buildTreeList(Comparator<? super E> compare) {
		return new Builder<>(compare);
	}

	private final Comparator<? super E> theCompare;
	private final boolean isDistinct;

	SortedTreeList(boolean safe, Comparator<? super E> compare, ThreadConstraint threadConstraint) {
		this(v -> safe ? new StampedLockingStrategy(v, threadConstraint) : new FastFailLockingStrategy(threadConstraint), DEFAULT_DESCRIP,
			compare);
	}

	/**
	 * @param locker The locking strategy for the list
	 * @param compare The comparator to order the values
	 */
	public SortedTreeList(Function<Object, CollectionLockingStrategy> locker, Comparator<? super E> compare) {
		this(locker, DEFAULT_DESCRIP, compare);
	}

	/**
	 * @param locker The locker for this list
	 * @param descrip The description for this list
	 * @param compare The comparator for this list's ordering
	 */
	protected SortedTreeList(Function<Object, CollectionLockingStrategy> locker, String descrip, Comparator<? super E> compare) {
		super(locker, descrip);
		if (compare == null)
			throw new NullPointerException();
		theCompare = compare;
		isDistinct = this instanceof NavigableSet;
	}

	/**
	 * @param locker The locker for this list
	 * @param identity The identity for this list
	 * @param compare The comparator for this list's ordering
	 */
	protected SortedTreeList(Function<Object, CollectionLockingStrategy> locker, Object identity, Comparator<? super E> compare) {
		super(locker, identity);
		if (compare == null)
			throw new NullPointerException();
		theCompare = compare;
		isDistinct = this instanceof NavigableSet;
	}

	/** @return The comparator that orders this list's values */
	@Override
	public Comparator<? super E> comparator() {
		return theCompare;
	}

	@Override
	public boolean isContentControlled() {
		return true;
	}

	/**
	 * @param search The comparable to use to search this list's values
	 * @return Either:
	 *         <ul>
	 *         <li>The index of some value <code>v</code> in this list for which <code>search.compareTo(v)==0</code> if such a value
	 *         exists</li>
	 *         <li>or <code>-i-1</code> where <code>i</code> is the index at which such a value would be inserted in this list</li>
	 *         </ul>
	 */
	@Override
	public int indexFor(Comparable<? super E> search) {
		if (isEmpty())
			return -1;
		return getLocker().doOptimistically(-1, //
			(init, ctx) -> {
				BinaryTreeNode<E> end = getTerminalElement(false);
				if (end == null)
					return -1;
				int comp = search.compareTo(end.get());
				if (comp == 0)
					return size() - 1;
				else if (comp > 0)
					return -size() - 1;
				BinaryTreeNode<E> begin = getTerminalElement(true);
				if (begin.equals(end))
					return -1;
				comp = search.compareTo(begin.get());
				if (comp == 0)
					return 0;
				else if (comp < 0)
					return -1;
				BinaryTreeNode<E> root = getRoot();
				return root.indexFor(node -> search.compareTo(node.get()), ctx);
			});
	}

	@Override
	public BinaryTreeNode<E> getElement(E value, boolean first) {
		BinaryTreeNode<E> found = search(searchFor(value, 0), BetterSortedList.SortedSearchFilter.OnlyMatch);
		if (found == null)
			return null;
		else if (isDistinct)
			return found;
		if (first) {
			for (BinaryTreeNode<E> left = found.getClosest(true); left != null
				&& theCompare.compare(left.get(), value) == 0; left = left.getClosest(true)) {
				found = left;
			}
		} else {
			for (BinaryTreeNode<E> right = found.getClosest(false); right != null
				&& theCompare.compare(right.get(), value) == 0; right = right.getClosest(false)) {
				found = right;
			}
		}
		return found;
	}

	@Override
	protected MutableBinaryTreeNode<E> mutableNodeFor(BinaryTreeNode<E> node) {
		return node == null ? null : new SortedMutableTreeNode(super.mutableNodeFor(node));
	}

	@Override
	protected MutableBinaryTreeNode<E> mutableNodeFor(ElementId node) {
		return new SortedMutableTreeNode(super.mutableNodeFor(node));
	}

	@Override
	public String canAdd(E value, ElementId after, ElementId before) {
		if (after != null) {
			int compare = theCompare.compare(getElement(after).get(), value);
			if (isDistinct && compare == 0)
				return StdMsg.ELEMENT_EXISTS;
			else if (compare > 0)
				return StdMsg.ILLEGAL_ELEMENT_POSITION;
		}
		if (before != null) {
			int compare = theCompare.compare(getElement(before).get(), value);
			if (isDistinct && compare == 0)
				return StdMsg.ELEMENT_EXISTS;
			else if (compare < 0)
				return StdMsg.ILLEGAL_ELEMENT_POSITION;
		}
		if (isDistinct && search(searchFor(value, 0), BetterSortedList.SortedSearchFilter.OnlyMatch) != null)
			return StdMsg.ELEMENT_EXISTS;
		return super.canAdd(value, after, before);
	}

	@Override
	public BinaryTreeNode<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		try (Transaction t = lock(true, null)) {
			boolean useAfter = false, useBefore = false;
			if (after != null) {
				int compare = theCompare.compare(getElement(after).get(), value);
				if (isDistinct && compare == 0)
					return null;
				else if (compare > 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
				if (first) {
					CollectionElement<E> adj = getAdjacentElement(after, true);
					if (adj == null || theCompare.compare(adj.get(), value) >= 0)
						useAfter = true;
				}
			}
			if (before != null) {
				int compare = theCompare.compare(getElement(before).get(), value);
				if (isDistinct && compare == 0)
					return null;
				else if (compare < 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
				if (!first) {
					CollectionElement<E> adj = getAdjacentElement(before, false);
					if (adj == null || theCompare.compare(adj.get(), value) <= 0)
						useBefore = true;
				}
			}
			if (!isDistinct) {
				// If this list is not distinct, and therefore the positioning may be flexible,
				// try to put the value in the appropriate position relative to the after or before elements
				// Also more efficient this way if the caller has gone to the trouble of figuring out where to put the element
				if (useAfter || useBefore)
					return super.addElement(value, after, before, first);
			}
			BinaryTreeNode<E> result = search(searchFor(value, 0), BetterSortedList.SortedSearchFilter.of(first, false));
			if (result == null) // Empty list
				return super.addElement(value, after, before, first);
			int compare = theCompare.compare(result.get(), value);
			if (isDistinct) {
				if (compare == 0)
					return null;
			} else {
				while (compare == 0) {
					BinaryTreeNode<E> adj = getAdjacentElement(result.getElementId(), !first);
					if (adj != null) {
						result = adj;
						compare = theCompare.compare(result.get(), value);
					} else
						break;
				}
			}
			if (compare < 0 || (compare == 0 && !first))
				return super.addElement(value, result.getElementId(), null, true);
			else
				return super.addElement(value, null, result.getElementId(), false);
		}
	}

	@Override
	public String canMove(ElementId valueEl, ElementId after, ElementId before) {
		if (after != null && before != null && after.compareTo(before) > 0)
			throw new IllegalArgumentException("after (" + after + ") is after before (" + before + ")");
		E value = getElement(valueEl).get();
		if (after != null && theCompare.compare(value, getElement(after).get()) < 0)
			return StdMsg.ILLEGAL_ELEMENT_POSITION;
		if (before != null && theCompare.compare(value, getElement(before).get()) > 0)
			return StdMsg.ILLEGAL_ELEMENT_POSITION;
		return null;
	}

	@Override
	public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		if (after != null && before != null && after.compareTo(before) > 0)
			throw new IllegalArgumentException("after (" + after + ") is after before (" + before + ")");
		if ((after == null || valueEl.compareTo(after) >= 0) && (before == null || valueEl.compareTo(before) <= 0))
			return getElement(valueEl);
		MutableCollectionElement<E> el = mutableElement(valueEl);
		E value = el.get();
		if (after != null && theCompare.compare(value, getElement(after).get()) < 0)
			throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
		if (before != null && theCompare.compare(value, getElement(before).get()) > 0)
			throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
		el.remove();
		afterRemove.run();
		return addElement(value, after, before, first);
	}

	@Override
	public BinaryTreeNode<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable added) {
		return (BinaryTreeNode<E>) BetterSortedList.super.getOrAdd(value, after, before, first, added);
	}

	@Override
	public boolean isConsistent(ElementId element) {
		return super.isConsistent(element, theCompare, isDistinct);
	}

	@Override
	public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
		return super.repair(element, theCompare, isDistinct, listener);
	}

	@Override
	public boolean checkConsistency() {
		return super.checkConsistency(theCompare, isDistinct);
	}

	@Override
	public <X> boolean repair(RepairListener<E, X> listener) {
		return super.repair(theCompare, isDistinct, listener);
	}

	@Override
	public SortedTreeList<E> withAll(Collection<? extends E> values) {
		super.withAll(values);
		return this;
	}

	@Override
	public ReversedSortedTreeList<E> reverse() {
		return new ReversedSortedTreeList<>(this);
	}

	@Override
	public SplitSpliterable<E> subList(int fromIndex, int toIndex) {
		return TreeBasedSortedList.super.subList(fromIndex, toIndex);
	}

	/**
	 * Implements {@link SortedTreeList#reverse()}
	 * 
	 * @param <E> The type of values in the list
	 */
	public static class ReversedSortedTreeList<E> extends BetterSortedList.ReversedSortedList<E> implements TreeBasedSortedList<E> {
		/** @param wrap The {@link SortedTreeList} to reverse */
		public ReversedSortedTreeList(SortedTreeList<E> wrap) {
			super(wrap);
		}

		@Override
		protected SortedTreeList<E> getWrapped() {
			return (SortedTreeList<E>) super.getWrapped();
		}

		@Override
		public BinaryTreeNode<E> getRoot() {
			return BinaryTreeNode.reverse(getWrapped().getRoot());
		}

		@Override
		public BinaryTreeNode<E> splitBetween(ElementId element1, ElementId element2) {
			return BinaryTreeNode.reverse(getWrapped().splitBetween(ElementId.reverse(element1), ElementId.reverse(element2)));
		}

		@Override
		public BinaryTreeNode<E> getTerminalElement(boolean first) {
			return (BinaryTreeNode<E>) super.getTerminalElement(first);
		}

		@Override
		public BinaryTreeNode<E> getElement(int index) {
			return (BinaryTreeNode<E>) super.getElement(index);
		}

		@Override
		public BinaryTreeNode<E> getAdjacentElement(ElementId elementId, boolean next) {
			return (BinaryTreeNode<E>) super.getAdjacentElement(elementId, next);
		}

		@Override
		public BinaryTreeNode<E> getElement(E value, boolean first) {
			return (BinaryTreeNode<E>) super.getElement(value, first);
		}

		@Override
		public BinaryTreeNode<E> getElement(ElementId id) {
			return (BinaryTreeNode<E>) super.getElement(id);
		}

		@Override
		public BinaryTreeNode<E> addElement(E value, boolean first) throws UnsupportedOperationException, IllegalArgumentException {
			return TreeBasedSortedList.super.addElement(value, first);
		}

		@Override
		public BinaryTreeNode<E> addElement(int index, E element) {
			return TreeBasedSortedList.super.addElement(index, element);
		}

		@Override
		public BinaryTreeNode<E> addElement(E value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (BinaryTreeNode<E>) super.addElement(value, after, before, first);
		}

		@Override
		public MutableBinaryTreeNode<E> mutableElement(ElementId id) {
			return getWrapped().mutableElement(id.reverse()).reverse();
		}

		@Override
		public BinaryTreeNode<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
			return (BinaryTreeNode<E>) super.search(search, filter);
		}

		@Override
		public SortedTreeList<E> reverse() {
			return getWrapped();
		}

		@Override
		public SplitSpliterable<E> subList(int fromIndex, int toIndex) {
			return TreeBasedSortedList.super.subList(fromIndex, toIndex);
		}
	}


	private class SortedMutableTreeNode implements MutableBinaryTreeNode<E> {
		private final MutableBinaryTreeNode<E> theWrapped;

		SortedMutableTreeNode(MutableBinaryTreeNode<E> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public BetterCollection<E> getCollection() {
			return SortedTreeList.this;
		}

		@Override
		public ElementId getElementId() {
			return theWrapped.getElementId();
		}

		@Override
		public E get() {
			return theWrapped.get();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public MutableBinaryTreeNode<E> getParent() {
			return mutableNodeFor(theWrapped.getParent());
		}

		@Override
		public MutableBinaryTreeNode<E> getLeft() {
			return mutableNodeFor(theWrapped.getLeft());
		}

		@Override
		public MutableBinaryTreeNode<E> getRight() {
			return mutableNodeFor(theWrapped.getRight());
		}

		@Override
		public MutableBinaryTreeNode<E> getClosest(boolean left) {
			return mutableNodeFor(theWrapped.getClosest(left));
		}

		@Override
		public boolean getSide() {
			return theWrapped.getSide();
		}

		@Override
		public int getNodesBefore() {
			return theWrapped.getNodesBefore();
		}

		@Override
		public int getNodesAfter() {
			return theWrapped.getNodesAfter();
		}

		@Override
		public MutableBinaryTreeNode<E> getRoot() {
			return mutableNodeFor(theWrapped.getRoot());
		}

		@Override
		public MutableBinaryTreeNode<E> getSibling() {
			return mutableNodeFor(theWrapped.getSibling());
		}

		@Override
		public MutableBinaryTreeNode<E> get(int index, OptimisticContext ctx) {
			return getLocker().doOptimistically(null, //
				(init, ctx2) -> mutableNodeFor(theWrapped.get(index, OptimisticContext.and(ctx, ctx2))));
		}

		@Override
		public MutableBinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean strictly,
			OptimisticContext ctx) {
			return getLocker().doOptimistically(null, //
				(init, ctx2) -> mutableNodeFor(theWrapped.findClosest(finder, lesser, strictly, OptimisticContext.and(ctx, ctx2))));
		}

		@Override
		public String isEnabled() {
			return null;
		}

		@Override
		public String isAcceptable(E value) {
			if (value == get())
				return null;
			if (!belongs(value))
				return StdMsg.ILLEGAL_ELEMENT;
			BinaryTreeNode<E> previous = getClosest(true);
			BinaryTreeNode<E> next = getClosest(false);
			if (previous != null) {
				int compare = comparator().compare(value, previous.get());
				if (isDistinct && compare == 0)
					return StdMsg.ELEMENT_EXISTS;
				else if (compare < 0)
					return StdMsg.ILLEGAL_ELEMENT_POSITION;
			}
			if (next != null) {
				int compare = comparator().compare(value, next.get());
				if (isDistinct && compare == 0)
					return StdMsg.ELEMENT_EXISTS;
				else if (compare > 0)
					return StdMsg.ILLEGAL_ELEMENT_POSITION;
			}
			return null;
		}

		@Override
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			String msg = isAcceptable(value);
			if (msg != null)
				throw new IllegalArgumentException(msg);
			theWrapped.set(value);
		}

		@Override
		public String canRemove() {
			return theWrapped.canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			theWrapped.remove();
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof SortedTreeList.SortedMutableTreeNode && theWrapped.equals(((SortedMutableTreeNode) obj).theWrapped);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}
}
