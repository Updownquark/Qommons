package org.qommons.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.SortedSet;

import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.OptimisticContext;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.collect.ValueStoredCollection;

public class SortedTreeList<E> extends RedBlackNodeList<E> implements ValueStoredCollection<E> {
	private final Comparator<? super E> theCompare;
	private final boolean isDistinct;

	public SortedTreeList(boolean safe, Comparator<? super E> compare) {
		this(safe ? new StampedLockingStrategy() : new FastFailLockingStrategy(), compare);
	}

	public SortedTreeList(CollectionLockingStrategy locker, Comparator<? super E> compare) {
		super(locker);
		theCompare = compare;
		isDistinct = this instanceof NavigableSet;
	}

	public SortedTreeList(boolean safe, SortedSet<E> values) {
		this(safe ? new StampedLockingStrategy() : new FastFailLockingStrategy(), values);
	}

	public SortedTreeList(CollectionLockingStrategy locker, SortedSet<E> values) {
		super(locker);
		theCompare = values.comparator();
		isDistinct = this instanceof NavigableSet;
		initialize(values, v -> v);
	}

	public SortedTreeList(boolean safe, SortedTreeList<E> values) {
		this(safe ? new StampedLockingStrategy() : new FastFailLockingStrategy(), values);
	}

	public SortedTreeList(CollectionLockingStrategy locker, SortedTreeList<E> values) {
		super(locker);
		theCompare = values.comparator();
		isDistinct = this instanceof NavigableSet;
		initialize(values, v -> v);
	}

	public Comparator<? super E> comparator() {
		return theCompare;
	}

	@Override
	public boolean isContentControlled() {
		return true;
	}

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

	public BinaryTreeNode<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
		if (isEmpty())
			return null;
		return getLocker().doOptimistically(null, //
			(init, ctx) -> {
				BinaryTreeNode<E> root = getRoot();
				if (root == null)
					return null;
				BinaryTreeNode<E> node = root.findClosest(//
					n -> search.compareTo(n.get()), filter.less.withDefault(true), filter.strict, ctx);
				if (node != null && filter == SortedSearchFilter.OnlyMatch && search.compareTo(node.get()) != 0)
					node = null;
				return node;
			}, true);
	}

	@Override
	public BinaryTreeNode<E> getElement(E value, boolean first) {
		return search(searchFor(value, 0), SortedSearchFilter.OnlyMatch);
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
		if (search(searchFor(value, 0), SortedSearchFilter.OnlyMatch) != null)
			return StdMsg.ELEMENT_EXISTS;
		return super.canAdd(value, after, before);
	}

	public Comparable<? super E> searchFor(E value, int onExact) {
		class ValueSearch<V> implements Comparable<V> {
			private final Comparator<? super V> theCompare;
			private final V theValue;
			private final int theOnExact;

			ValueSearch(Comparator<? super V> compare, V val, int _onExact) {
				theCompare = compare;
				theValue = val;
				theOnExact = _onExact;
			}

			@Override
			public int compareTo(V v) {
				int compare = theCompare.compare(theValue, v);
				if (compare == 0)
					compare = theOnExact;
				return compare;
			}

			@Override
			public boolean equals(Object o) {
				if (!(o instanceof ValueSearch))
					return false;
				ValueSearch<?> other = (ValueSearch<?>) o;
				return theCompare.equals(other.theCompare) && Objects.equals(theValue, other.theValue) && theOnExact == other.theOnExact;
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
			BinaryTreeNode<E> result = search(searchFor(value, 0), SortedSearchFilter.PreferLess);
			if (result == null)
				return super.addElement(value, after, before, first);
			int compare = theCompare.compare(result.get(), value);
			if (isDistinct && compare == 0)
				return null;
			else if (compare < 0)
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
	public CollectionElement<E> searchWithConsistencyDetection(E value, boolean[] invalid) {
		if (isEmpty())
			return null;
		return getLocker().doOptimistically(null, //
			(init, ctx) -> {
				BinaryTreeNode<E> root = getRoot();
				if (root == null)
					return null;
				invalid[0] = false;
				Object[] bounds = new Object[2];
				boolean[] hasBounds = new boolean[2];
				BinaryTreeNode<E> node = root.findClosest(n -> {
					if (hasBounds[0])
						invalid[0] = theCompare.compare(n.get(), (E) bounds[0]) > 0;
					if (!invalid[0] && hasBounds[1])
						invalid[0] = theCompare.compare(n.get(), (E) bounds[1]) < 0;
					int comp = theCompare.compare(value, n.get());
					if (comp < 0) {
						hasBounds[1] = true;
						bounds[1] = n.get();
					} else if (comp > 0) {
						hasBounds[0] = true;
						bounds[0] = n.get();
					}
					return comp;
				}, true, true, OptimisticContext.and(ctx, () -> invalid[0]));
				if (node != null && theCompare.compare(value, node.get()) != 0)
					node = null;
				return node;
			}, true);
	}

	@Override
	public boolean checkConsistency() {
		try (Transaction t = lock(false, null)) {
			E previous = null;
			boolean hasPrevious = false;
			for (E value : this) {
				if (hasPrevious && theCompare.compare(value, previous) <= 0)
					return true;
			}
			return false;
		}
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
