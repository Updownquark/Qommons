package org.qommons.tree;

import java.util.Collection;
import java.util.Deque;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

import org.qommons.Ternian;
import org.qommons.Transaction;
import org.qommons.collect.Betterator;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.ReversibleList;
import org.qommons.collect.ReversibleElementSpliterator;
import org.qommons.collect.TransactableList;

/**
 * A list backed by a binary red/black tree structure. Tree lists are O(log(n)) for get(index) and all modifications; constant time-from a
 * {@link #listIterator() list iterator}.
 *
 * @param <N> The sub-type of {@link CountedRedBlackNode} used to store the data
 * @param <E> The type of values in the list
 */
public class RedBlackTreeList<N extends CountedRedBlackNode<E>, E> extends AbstractRBTCollection<E, N>
	implements ReversibleList<E>, TransactableList<E>, Deque<E> {
	/** @param nodeCreator The function to create nodes for the list */
	public RedBlackTreeList(Function<E, N> nodeCreator) {
		this(nodeCreator, new FastFailLockingStrategy());
	}

	/**
	 * @param nodeCreator The function to create nodes for the list
	 * @param locker The locking strategy for the list
	 */
	public RedBlackTreeList(Function<E, N> nodeCreator, CollectionLockingStrategy locker) {
		super(nodeCreator, locker);
	}

	/** @return The root of this list's tree structure */
	@Override
	public N getRoot() {
		return super.getRoot();
	}

	/**
	 * @param index The index to get the node at
	 * @return The node at the given index
	 */
	@Override
	public N getNodeAt(int index) {
		return super.getNodeAt(index);
	}

	@Override
	public E get(int index) {
		N node = getNodeAt(index);
		return node.getValue();
	}

	@Override
	public int indexOf(Object o) {
		return getLocker().doOptimistically(0, (idx, stamp) -> {
			N found = findNode(v -> Objects.equals(v, o), Ternian.TRUE);
			return found == null ? -1 : found.getIndex();
		});
	}

	@Override
	public int lastIndexOf(Object o) {
		return getLocker().doOptimistically(0, (idx, stamp) -> {
			N found = findNode(v -> Objects.equals(v, o), Ternian.FALSE);
			return found == null ? -1 : found.getIndex();
		});
	}

	@Override
	public Betterator<E> iterator() {
		return ReversibleList.super.iterator();
	}

	@Override
	public Betterator<E> descendingIterator() {
		return super.descendingIterator();
	}

	@Override
	public boolean add(E e) {
		try (Transaction t = lock(true, null)) {
			N node = getEndNode(false);
			addAfter(e, node);
			return true;
		}
	}

	@Override
	public void add(int index, E element) {
		try (Transaction t = lock(true, null)) {
			if (isEmpty() && index == 0)
				addAfter(element, null);
			else {
				N node = getNodeAt(index);
				addBefore(element, node);
			}
		}
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		try (Transaction t = lock(true, null)) {
			N node = getEndNode(false);
			for (E o : c)
				node = addAfter(o, node);
			return !c.isEmpty();
		}
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		try (Transaction t = lock(true, null)) {
			if (index == size())
				return addAll(c);
			N node = getNodeAt(index);
			boolean first = true;
			for (E o : c) {
				if (first) {
					node = addBefore(o, node);
					first = false;
				} else
					node = addAfter(o, node);
			}
			return !c.isEmpty();
		}
	}

	/**
	 * @param element the element to add
	 * @param before The node to add the element before
	 * @return The new node
	 */
	@Override
	public N addBefore(E element, N before) {
		return super.addBefore(element, before);
	}

	/**
	 * @param element the element to add
	 * @param after The node to add the element after
	 * @return The new node
	 */
	@Override
	public N addAfter(E element, N after) {
		return super.addAfter(element, after);
	}

	@Override
	public E remove(int index) {
		try (Transaction t = lock(true, null)) {
			N node = getNodeAt(index);
			delete(node);
			return node.getValue();
		}
	}

	@Override
	public E set(int index, E element) {
		try (Transaction t = lock(true, null)) {
			N node = getNodeAt(index);
			E old = node.getValue();
			replace(node, createNode(element));
			return old;
		}
	}

	@Override
	public void addFirst(E e) {
		offerFirst(e);
	}

	@Override
	public void addLast(E e) {
		add(e);
	}

	@Override
	public boolean offerFirst(E e) {
		try (Transaction t = lock(true, null)) {
			N first = getEndNode(true);
			if (first == null)
				addAfter(e, null);
			else
				addBefore(e, first);
			return true;
		}
	}

	@Override
	public boolean offerLast(E e) {
		return add(e);
	}

	@Override
	public E removeFirst() {
		try (Transaction t = lock(true, null)) {
			N first = getEndNode(true);
			if (first == null)
				throw new NoSuchElementException();
			delete(first);
			return first.getValue();
		}
	}

	@Override
	public E removeLast() {
		try (Transaction t = lock(true, null)) {
			N first = getEndNode(false);
			if (first == null)
				throw new NoSuchElementException();
			delete(first);
			return first.getValue();
		}
	}

	@Override
	public E pollFirst() {
		try (Transaction t = lock(true, null)) {
			N first = getEndNode(true);
			if (first == null)
				return null;
			delete(first);
			return first.getValue();
		}
	}

	@Override
	public E pollLast() {
		try (Transaction t = lock(true, null)) {
			N first = getEndNode(true);
			if (first == null)
				return null;
			delete(first);
			return first.getValue();
		}
	}

	@Override
	public E getFirst() {
		return getLocker().doOptimistically(null, (v, stamp) -> {
			N node = getEndNode(true);
			if (node == null)
				throw new NoSuchElementException();
			return node.getValue();
		});
	}

	@Override
	public E getLast() {
		return getLocker().doOptimistically(null, (v, stamp) -> {
			N node = getEndNode(false);
			if (node == null)
				throw new NoSuchElementException();
			return node.getValue();
		});
	}

	@Override
	public E peekFirst() {
		return getLocker().doOptimistically(null, (v, stamp) -> {
			N node = getEndNode(true);
			if (node == null)
				return null;
			return node.getValue();
		});
	}

	@Override
	public E peekLast() {
		return getLocker().doOptimistically(null, (v, stamp) -> {
			N node = getEndNode(false);
			if (node == null)
				return null;
			return node.getValue();
		});
	}

	@Override
	public boolean removeFirstOccurrence(Object o) {
		return remove(o);
	}

	@Override
	public boolean removeLastOccurrence(Object o) {
		return removeLast(o);
	}

	@Override
	public boolean offer(E e) {
		return add(e);
	}

	@Override
	public E remove() {
		return removeFirst();
	}

	@Override
	public E poll() {
		return pollFirst();
	}

	@Override
	public E element() {
		return getFirst();
	}

	@Override
	public E peek() {
		return peekFirst();
	}

	@Override
	public void push(E e) {
		add(0, e);
	}

	@Override
	public E pop() {
		return removeFirst();
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		N prev, next;
		if (index == size()) {
			prev = getEndNode(false);
			next = null;
		} else {
			next = getNodeAt(index);
			prev = (N) next.getClosest(true);
		}

		return new ListIter(nodeSpliterator(prev, next));
	}

	@Override
	public ReversibleList<E> subList(int fromIndex, int toIndex) {
	}

	private class ListIter extends ReversibleElementSpliterator.PartialListIterator<E> {
		private final NodeSpliterator theNodes;

		ListIter(NodeSpliterator nodes) {
			super(values(nodes));
			theNodes = nodes;
		}

		@Override
		public int nextIndex() {
			N nextNode = theNodes.getNextNode();
			return nextNode == null ? size() : nextNode.getIndex();
		}

		@Override
		public int previousIndex() {
			N prevNode = theNodes.getPreviousNode();
			return prevNode == null ? 0 : prevNode.getIndex();
		}

		@Override
		public void add(E e) {
			int todo = todo;// TOOD
		}
	}
}
