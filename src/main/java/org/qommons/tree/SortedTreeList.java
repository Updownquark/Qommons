<<<<<<< HEAD
package org.qommons.tree;

import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Objects;

import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.*;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A {@link org.qommons.collect.BetterList} backed by a tree structure that sorts its values, with duplicates allowed
 * 
 * @param <E> The type of value in the list
 */
public class SortedTreeList<E> extends RedBlackNodeList<E> {
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
		super(locker);
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
		BinaryTreeNode<E> root = getRoot();
		return root == null ? -1 : root.indexFor(node -> search.compareTo(node.get()));
	}

	/**
	 * Searches this sorted list for an element
	 *
	 * @param search The search to navigate through this list for the target value. The search must follow this set's {@link #comparator()
	 *        order}.
	 * @param filter The filter on the result
	 * @return The element that is the best found result of the search, or null if this list is empty or does not contain any element
	 *         matching the given filter
	 * @see SortedSearchFilter
	 */
	public BinaryTreeNode<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
		try (Transaction t = lock(false, null)) {
			if (isEmpty())
				return null;
			BinaryTreeNode<E> node = getRoot().findClosest(//
				n -> search.compareTo(n.get()), filter.less.withDefault(true), filter.strict);
			if (node == null)
				return null;
			if (filter == SortedSearchFilter.OnlyMatch && search.compareTo(node.get()) != 0)
				return null;
			return node;
		}
	}

	@Override
	public CollectionElement<E> getElement(E value, boolean first) {
		try (Transaction t = lock(false, null)) {
			ValueHolder<CollectionElement<E>> element = new ValueHolder<>();
			ElementSpliterator<E> spliter = first ? spliterator(first) : spliterator(first).reverse();
			while (!element.isPresent() && spliter.forElement(el -> {
				if (Objects.equals(el.get(), value))
					element.accept(first ? el : el.reverse());
			}, true)) {}
			return element.get();
		}
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
		public MutableBinaryTreeNode<E> get(int index) {
			return mutableNodeFor(theWrapped.get(index));
		}

		@Override
		public MutableBinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean strictly) {
			return mutableNodeFor(theWrapped.findClosest(finder, lesser, strictly));
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
				int compare = comparator().compare(previous.get(), value);
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
=======
package org.qommons.tree;

import java.util.Comparator;
import java.util.NavigableSet;
import java.util.Objects;

import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.StampedLockingStrategy;

public class SortedTreeList<E> extends RedBlackNodeList<E> {
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

	public Comparator<? super E> comparator() {
		return theCompare;
	}

	@Override
	public boolean isContentControlled() {
		return true;
	}

	public int indexFor(Comparable<? super E> search) {
		BinaryTreeNode<E> root = getRoot();
		return root == null ? -1 : root.indexFor(node -> search.compareTo(node.get()));
	}

	public BinaryTreeNode<E> search(Comparable<? super E> search, SortedSearchFilter filter) {
		try (Transaction t = lock(false, null)) {
			if (isEmpty())
				return null;
			BinaryTreeNode<E> node = getRoot().findClosest(//
				n -> search.compareTo(n.get()), filter.less.withDefault(true), filter.strict);
			if (node == null)
				return null;
			if (filter == SortedSearchFilter.OnlyMatch && search.compareTo(node.get()) != 0)
				return null;
			return node;
		}
	}

	@Override
	public CollectionElement<E> getElement(E value, boolean first) {
		try (Transaction t = lock(false, null)) {
			ValueHolder<CollectionElement<E>> element = new ValueHolder<>();
			ElementSpliterator<E> spliter = first ? spliterator(first) : spliterator(first).reverse();
			while (!element.isPresent() && spliter.forElement(el -> {
				if (Objects.equals(el.get(), value))
					element.accept(first ? el : el.reverse());
			}, true)) {}
			return element.get();
		}
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
		public MutableBinaryTreeNode<E> get(int index) {
			return mutableNodeFor(theWrapped.get(index));
		}

		@Override
		public MutableBinaryTreeNode<E> findClosest(Comparable<BinaryTreeNode<E>> finder, boolean lesser, boolean strictly) {
			return mutableNodeFor(theWrapped.findClosest(finder, lesser, strictly));
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
>>>>>>> refs/remotes/origin/work
