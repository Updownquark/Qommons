package org.qommons.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.SortedSet;

import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.collect.OptimisticContext;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.collect.ValueStoredCollection;

/**
 * A {@link org.qommons.collect.BetterList} backed by a tree structure that sorts its values, with duplicates allowed
 * 
 * @param <E> The type of value in the list
 */
public class SortedTreeList<E> extends RedBlackNodeList<E> implements ValueStoredCollection<E> {
	private static final String DEFAULT_DESCRIP = "sorted-tree-list";

	/**
	 * @param <E> The type of elements in the list
	 * @param <L> The sub-type of the list
	 */
	public static class Builder<E, L extends SortedTreeList<E>> extends RBNLBuilder<E, L> {
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
		public Builder<E, L> withDescription(String descrip) {
			super.withDescription(descrip);
			return this;
		}

		@Override
		public Builder<E, L> safe(boolean safe) {
			super.safe(safe);
			return this;
		}

		@Override
		public Builder<E, L> withLocker(CollectionLockingStrategy locker) {
			super.withLocker(locker);
			return this;
		}

		@Override
		public L build() {
			return (L) new SortedTreeList<>(getLocker(), getDescription(), theCompare);
		}
	}

	/**
	 * @param <E> The type of elements for the list
	 * @param compare The comparator for the list's ordering
	 * @return The builder for the list
	 */
	public static <E> Builder<E, SortedTreeList<E>> buildTreeList(Comparator<? super E> compare) {
		return new Builder<>(compare);
	}

	private final Comparator<? super E> theCompare;
	private final boolean isDistinct;

	/**
	 * @param safe Whether the list should be thread-safe or fail-fast
	 * @param compare The comparator to order the values
	 */
	public SortedTreeList(boolean safe, Comparator<? super E> compare) {
		this(safe ? new StampedLockingStrategy() : new FastFailLockingStrategy(), compare);
	}

	/**
	 * @param locker The locking strategy for the list
	 * @param compare The comparator to order the values
	 */
	public SortedTreeList(CollectionLockingStrategy locker, Comparator<? super E> compare) {
		this(locker, DEFAULT_DESCRIP, compare);
	}

	public SortedTreeList(boolean safe, SortedSet<E> values) {
		this(safe ? new StampedLockingStrategy() : new FastFailLockingStrategy(), values);
	}

	public SortedTreeList(CollectionLockingStrategy locker, SortedSet<E> values) {
		this(locker, DEFAULT_DESCRIP, values.comparator());
		initialize(values, v -> v);
	}

	public SortedTreeList(boolean safe, SortedTreeList<E> values) {
		this(safe ? new StampedLockingStrategy() : new FastFailLockingStrategy(), values);
	}

	public SortedTreeList(CollectionLockingStrategy locker, SortedTreeList<E> values) {
		this(locker, DEFAULT_DESCRIP, values.comparator());
		initialize(values, v -> v);
	}

	/**
	 * @param locker The locker for this list
	 * @param descrip The description for this list
	 * @param compare The comparator for this list's ordering
	 */
	protected SortedTreeList(CollectionLockingStrategy locker, String descrip, Comparator<? super E> compare) {
		super(locker, descrip);
		theCompare = compare;
		isDistinct = this instanceof NavigableSet;
	}

	/**
	 * @param locker The locker for this list
	 * @param identity The identity for this list
	 * @param compare The comparator for this list's ordering
	 */
	protected SortedTreeList(CollectionLockingStrategy locker, Object identity, Comparator<? super E> compare) {
		super(locker, identity);
		theCompare = compare;
		isDistinct = this instanceof NavigableSet;
	}

	/** @return The comparator that orders this list's values */
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
	 *         <li>or <code>-(i-1)</code> where <code>i</code> is the index at which such a value would be inserted in this list</li>
	 *         </ul>
	 */
	public int indexFor(Comparable<? super E> search) {
		if (isEmpty())
			return -1;
		return getLocker().doOptimistically(-1, //
			(init, ctx) -> {
		BinaryTreeNode<E> root = getRoot();
				if (root == null)
					return -1;
				return root.indexFor(node -> search.compareTo(node.get()), ctx);
			}, true);
	}

	@Override
	public BinaryTreeNode<E> getElement(E value, boolean first) {
		BinaryTreeNode<E> found = search(searchFor(value, 0), SortedSearchFilter.OnlyMatch);
		if (found == null)
			return null;
		else if (isDistinct || Objects.equals(found.get(), value))
			return found;
		for (BinaryTreeNode<E> left = found.getClosest(true); left != null
			&& theCompare.compare(left.get(), value) == 0; left = left.getClosest(true)) {
			if (Objects.equals(left.get(), value))
				return left;
		}
		for (BinaryTreeNode<E> right = found.getClosest(false); right != null
			&& theCompare.compare(right.get(), value) == 0; right = right.getClosest(false)) {
			if (Objects.equals(right.get(), value))
				return right;
	}
		return null;
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
		if (isDistinct && search(searchFor(value, 0), SortedSearchFilter.OnlyMatch) != null)
			return StdMsg.ELEMENT_EXISTS;
		return super.canAdd(value, after, before);
	}

	/**
	 * Creates a {@link Comparable} to use in searching this sorted list from a value compatible with the list's comparator
	 * 
	 * @param value The comparable value
	 * @param onExact The value to return when the comparator matches. For example, to search for values strictly less than
	 *        <code>value</code>, an integer &lt;0 should be specified.
	 * @return The search to use with {@link #search(Comparable, SortedSearchFilter)}
	 */
	public Comparable<? super E> searchFor(E value, int onExact) {
		class ValueSearch<V> implements Comparable<V> {
			private final Comparator<? super V> theValueCompare;
			private final V theValue;
			private final int theOnExact;

			ValueSearch(Comparator<? super V> compare, V val, int _onExact) {
				theValueCompare = compare;
				theValue = val;
				theOnExact = _onExact;
			}

			@Override
			public int compareTo(V v) {
				int compare = theValueCompare.compare(theValue, v);
				if (compare == 0)
					compare = theOnExact;
				return compare;
			}

			@Override
			public boolean equals(Object o) {
				if (!(o instanceof ValueSearch))
					return false;
				ValueSearch<?> other = (ValueSearch<?>) o;
				return theValueCompare.equals(other.theValueCompare) && Objects.equals(theValue, other.theValue)
					&& theOnExact == other.theOnExact;
			}

			@Override
			public String toString() {
				if (theOnExact < 0)
					return "<" + theValue;
				else if (theOnExact == 0)
					return String.valueOf(theValue);
				else
					return ">" + theValue;
			}
		}
		return new ValueSearch<>(comparator(), value, onExact);
	}

	@Override
	public BinaryTreeNode<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		try (Transaction t = lock(true, true, null)) {
			if (after != null) {
				int compare = theCompare.compare(getElement(after).get(), value);
				if (isDistinct && compare == 0)
					return null;
				else if (compare > 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
			}
			if (before != null) {
				int compare = theCompare.compare(getElement(before).get(), value);
				if (isDistinct && compare == 0)
					return null;
				else if (compare < 0)
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
			}
			if (!isDistinct) {
				// If this list is not distinct, and therefore the positioning may be flexible,
				// try to put the value in the appropriate position relative to the after or before elements
				// Also more efficient this way if the caller has gone to the trouble of figuring out where to put the element
				if ((first && after != null) || (!first && before != null))
					return super.addElement(value, after, null, true);
			}
			BinaryTreeNode<E> result = search(searchFor(value, 0), SortedSearchFilter.of(first, false));
			if (result == null)
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
	public BinaryTreeNode<E> getOrAdd(E value, boolean first, Runnable added) {
		while (true) {
			BinaryTreeNode<E> found = search(searchFor(value, 0), SortedSearchFilter.PreferLess);
			if (found == null) {
				found = addElement(value, first);
				if (found != null && added != null)
					added.run();
				return found;
			}
			int compare = comparator().compare(value, found.get());
			if (compare == 0)
				return found;
			try (Transaction t = lock(true, true, null)) {
				MutableCollectionElement<E> mutableElement;
				try {
					mutableElement = mutableElement(found.getElementId());
				} catch (IllegalArgumentException e) {
					continue; // Possible it may have been removed already
				}
				ElementId addedId = mutableElement.add(value, compare < 0);
				if (added != null)
					added.run();
				return getElement(addedId);
			}
		}
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
	public MutableElementSpliterator<E> spliterator(boolean fromStart) {
		return new DefaultSplittableSpliterator<>(this, comparator(), 0, null, fromStart, null, null);
	}

	@Override
	public MutableElementSpliterator<E> spliterator(ElementId element, boolean asNext) {
		return new DefaultSplittableSpliterator<>(this, comparator(), 0, getElement(element), asNext, null, null);
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
				(init, ctx2) -> mutableNodeFor(theWrapped.get(index, OptimisticContext.and(ctx, ctx2))), true);
		}

		@Override
		public MutableBinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean strictly,
			OptimisticContext ctx) {
			return getLocker().doOptimistically(null, //
				(init, ctx2) -> mutableNodeFor(theWrapped.findClosest(finder, lesser, strictly, OptimisticContext.and(ctx, ctx2))), true);
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
