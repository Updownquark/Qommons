package org.qommons.tree;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.qommons.Ternian;
import org.qommons.Transaction;
import org.qommons.collect.MutableElementHandle;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.collect.ReversibleElementSpliterator;
import org.qommons.collect.ReversibleList;
import org.qommons.collect.TransactableCollection;
import org.qommons.collect.TransactableList;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

public abstract class AbstractRBTCollection<E, N extends RedBlackNode<E>> implements ReversibleList<E>, TransactableList<E> {
	private final Function<E, N> theNodeCreator;
	private final CollectionLockingStrategy theLocker;

	private N theRoot;

	/** @param nodeCreator The function to create nodes for the collection */
	public AbstractRBTCollection(Function<E, N> nodeCreator, CollectionLockingStrategy locker) {
		theNodeCreator = nodeCreator;
		theLocker = locker;
	}

	@Override
	public boolean isLockSupported() {
		return theLocker.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theLocker.lock(write, cause);
	}

	/** @return This collection's locking strategy */
	protected CollectionLockingStrategy getLocker() {
		return theLocker;
	}

	/** @return The root of this list's tree structure */
	protected N getRoot() {
		return theRoot;
	}

	/**
	 * @param value The value to create the node for
	 * @return The new node for the value
	 */
	protected N createNode(E value) {
		return theNodeCreator.apply(value);
	}

	/**
	 * @param index The index to get the node at
	 * @return The node at the given index
	 */
	protected N getNodeAt(int index) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		return theLocker.doOptimistically(null, (node, stamp) -> {
			node = theRoot;
			int passed = 0;
			while (true) {
				if (node == null)
					throw new IndexOutOfBoundsException(index + " of " + passed);
				int leftCount = CountedRedBlackNode.size(node.getLeft());
				int nodeIndex = passed + leftCount;
				if (!theLocker.check(stamp))
					return null;
				if (index < nodeIndex)
					node = (N) node.getLeft();
				else if (index == nodeIndex)
					return node;
				else {
					passed = nodeIndex + 1;
					node = (N) node.getRight();
				}
				if (!theLocker.check(stamp))
					return null;
			}
		});
	}

	/**
	 * Gets the terminal node in this collection's tree structure
	 * @param first Whether to get the first node or the last node
	 * @return The first or last node in this collection's tree structure
	 */
	protected N getEndNode(boolean first) {
		return theLocker.doOptimistically(null, (node, stamp) -> {
			node = theRoot;
			while (theLocker.check(stamp) && node != null && node.getChild(first) != null)
				node = (N) node.getChild(first);
			return node;
		});
	}

	@Override
	public int size() {
		return CountedRedBlackNode.size(theRoot);
	}

	@Override
	public boolean isEmpty() {
		return theRoot == null;
	}

	@Override
	public ReversibleElementSpliterator<E> spliterator(boolean fromStart) {
		N begin=getEndNode(fromStart);
		return values(nodeSpliterator((N) begin.getClosest(true), begin));
	}

	protected NodeSpliterator nodeSpliterator(N previous, N next) {
		return new NodeSpliterator(previous, next, theLocker.subLock());
	}

	protected ReversibleElementSpliterator<E> values(ReversibleElementSpliterator<N> nodeSpliterator) {
		TypeToken<E> type = (TypeToken<E>) TypeToken.of(Object.class);
		return new ReversibleElementSpliterator.WrappingReversibleSpliterator<>(nodeSpliterator, type, () -> {
			MutableElementHandle<N>[] container = new MutableElementHandle[1];
			MutableElementSpliterator.WrappingElement<N, E> wrapperEl = new MutableElementSpliterator.WrappingElement<N, E>(type, container) {
				@Override
				public E get() {
					return getWrapped().get().getValue();
				}

				@Override
				public <V extends E> String isAcceptable(V value) {
					return ((MutableElementHandle<N>) getWrapped()).isAcceptable(createNode(value));
				}

				@Override
				public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
					return ((MutableElementHandle<N>) getWrapped()).set(createNode(value), cause).getValue();
				}

				@Override
				public String canRemove() {
					return getWrapped().canRemove();
				}

				@Override
				public void remove() {
					getWrapped().remove();
				}
			};
			return el -> {
				container[0] = (MutableElementHandle<N>) el;
				return wrapperEl;
			};
		});
	}

	protected N findNode(Predicate<? super N> test) {
		return findNode(test, Ternian.NONE);
	}

	protected N findNode(Predicate<? super N> test, Ternian firstOnly) {
		return theLocker.doOptimistically(null, (node, stamp) -> {
			N root = theRoot;
			if (root == null)
				return null;
			AtomicInteger index = new AtomicInteger(-1);
			class FindTask extends RecursiveTask<N> {
				private final N theNode;

				FindTask(N taskNode) {
					theNode = taskNode;
				}

				@Override
				protected N compute() {
					if (!theLocker.check(stamp))
						return null;
					int foundIndex = index.get();
					if (foundIndex >= 0 && firstOnly.value == null)
						return null; // Already found a solution somewhere else and we don't care which one. Abort.

					int nodeIndex = theNode.getIndex();
					if (firstOnly.value != null) {
						// Test the child first, since if there's a match on the better side,
						// we don't care to actually run the test on this node
						N child = (N) theNode.getChild(firstOnly.value);
						if (child != null) {
							FindTask subTask = new FindTask(node);
							invokeAll(subTask);
							N result = subTask.join();
							if (result != null)
								return result;
							foundIndex = index.get();
							if (!isBetterIndex(nodeIndex, foundIndex) || !theLocker.check(stamp))
								return null; // Somebody else found a better solution first
						}
					} // else If we don't care which match we get, then test this node before we do the work of descending

					// If this node might be the best solution, do the test
					if (isBetterIndex(nodeIndex, foundIndex) && test.test(theNode)) {
						if (!theLocker.check(stamp))
							return null;
						boolean done = true;
						while (!index.compareAndSet(foundIndex, nodeIndex)) {
							foundIndex = index.get();
							if (!isBetterIndex(nodeIndex, foundIndex)) {
								done = false;
								break;
							}
						}
						if (done) {
							// We're the best solution in this node's sub-tree
							return theNode;
						}
					}

					// Test the first child if we haven't already
					if (firstOnly.value == null) {
						N child = (N) theNode.getChild(true);
						if (child != null) {
							FindTask subTask = new FindTask(node);
							invokeAll(subTask);
							N result = subTask.join();
							if (result != null)
								return result;
							foundIndex = index.get();
							if (foundIndex >= 0 || !theLocker.check(stamp))
								return null; // Somebody else found a solution first
						}
					}
					// Test the other child
					N child = (N) theNode.getChild(firstOnly.withDefault(false));
					if (child != null) {
						FindTask subTask = new FindTask(node);
						invokeAll(subTask);
						N result = subTask.join();
						if (result != null)
							return result;
					}
					return null;
				}

				private boolean isBetterIndex(int nodeIndex, int foundIndex) {
					if (foundIndex < 0)
						return true;
					if (firstOnly.value == null)
						return false;
					if (firstOnly.value)
						return nodeIndex < foundIndex;
					else
						return nodeIndex > foundIndex;
				}
			}
			return ForkJoinPool.commonPool().invoke(new FindTask(root));
		});
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		forEachNode(n -> action.accept(n.getValue()));
	}

	protected void forEachNode(Consumer<? super N> action) {
		if (theRoot == null)
			return;
		try (Transaction t = lock(false, null)) {
			if (theRoot == null)
				return;
			class NodeAction extends RecursiveAction {
				private final N theNode;

				NodeAction(N taskNode) {
					theNode = taskNode;
				}

				@Override
				protected void compute() {
					action.accept(theNode);
					N left = (N) theNode.getLeft();
					N right = (N) theNode.getRight();
					if (left != null && right != null)
						invokeAll(new NodeAction(left), new NodeAction(right));
					else if (left != null)
						invokeAll(new NodeAction(left));
					else if (right != null)
						invokeAll(new NodeAction(right));
				}
			}
			ForkJoinPool.commonPool().execute(new NodeAction(theRoot));
		}
	}

	@Override
	public boolean contains(Object o) {
		return findNode(n -> Objects.equals(n.getValue(), o)) != null;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		if (c.isEmpty())
			return true;
		Object NULL = new Object();
		ConcurrentHashMap<Object, Object> set = new ConcurrentHashMap<>(c.size() * 4 / 3, .75f, ForkJoinPool.getCommonPoolParallelism());
		for (Object o : c) {
			if (o == null)
				o = NULL;
			set.put(o, o);
		}
		forEach(v -> set.remove(v == null ? NULL : v));
		return set.isEmpty();
	}

	@Override
	public boolean containsAny(Collection<?> c) {
		if (c.isEmpty())
			return false;
		Transaction t;
		if (c instanceof TransactableCollection)
			t = ((TransactableCollection<?>) c).lock(false, null);
		else
			t = Transaction.NONE;
		try {
			return findNode(n -> c.contains(n.getValue())) != null;
		} finally {
			t.close();
		}
	}

	@Override
	public Object[] toArray() {
		return theLocker.doOptimistically((Object[]) null, (a, stamp) -> {
			int size = CountedRedBlackNode.size(theRoot);
			if (a == null || a.length != size) {
				a = new Object[size];
			}
			Object[] array = a;
			MutableElementSpliterator<E> spliter = spliterator();
			int[] i = new int[1];
			while (theLocker.check(stamp) && spliter.tryAdvance(v -> array[i[0]] = v)) {
				i[0]++;
			}
			return array;
		});
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return theLocker.doOptimistically(a, (arr, stamp) -> {
			int size = CountedRedBlackNode.size(theRoot);
			if (arr == null || a.length != size) {
				arr = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
			}
			T[] array = arr;
			MutableElementSpliterator<E> spliter = spliterator();
			int[] i = new int[1];
			while (theLocker.check(stamp) && spliter.tryAdvance(v -> array[i[0]] = (T) v)) {
				i[0]++;
			}
			return array;
		});
	}

	@Override
	public boolean remove(Object o) {
		try (Transaction t = lock(true, null)) {
			N node = findNode(v -> Objects.equals(v, o), Ternian.TRUE);
			if (node != null)
				delete(node);
			return node != null;
		}
	}

	@Override
	public boolean removeLast(Object o) {
		try (Transaction t = lock(true, null)) {
			N node = findNode(v -> Objects.equals(v, o), Ternian.FALSE);
			if (node != null)
				delete(node);
			return node != null;
		}
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		Transaction t;
		if (c instanceof TransactableCollection)
			t = ((TransactableCollection<?>) c).lock(false, null);
		else
			t = Transaction.NONE;
		try {
			return removeIf(c::contains);
		} finally {
			t.close();
		}
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		Transaction t;
		if (c instanceof TransactableCollection)
			t = ((TransactableCollection<?>) c).lock(false, null);
		else
			t = Transaction.NONE;
		try {
			return removeIf(v -> !c.contains(v));
		} finally {
			t.close();
		}
	}

	@Override
	public boolean removeIf(Predicate<? super E> test) {
		try (Transaction t = lock(true, null)) {
			ConcurrentLinkedQueue<N> toRemove = new ConcurrentLinkedQueue<>();
			forEachNode(n -> {
				if (test.test(n.getValue()))
					toRemove.add(n);
			});
			for (N node : toRemove)
				delete(node);
			theLocker.indexChanged(-toRemove.size());
			return !toRemove.isEmpty();
		}
	}

	@Override
	public void clear() {
		try (Transaction t = lock(true, null)) {
			theRoot = null;
			theLocker.indexChanged(-1);
		}
	}

	public E findAndReplace(Predicate<? super E> test, Function<? super E, ? extends E> map) {
		try (Transaction t = lock(true, null)) {
			N node = findNode(n -> test.test(n.getValue()), Ternian.NONE);
			if (node != null) {
				E newValue = map.apply(node.getValue());
				if (newValue != node.getValue())
					replace(node, createNode(newValue));
			}
			return node == null ? null : node.getValue();
		}
	}

	public void delete(N toDelete) {
		theRoot = (N) toDelete.delete();
	}

	public void replace(N toReplace, N replacement) {
		toReplace.replace(replacement);
		if (theRoot == toReplace)
			theRoot = replacement;
	}

	/**
	 * @param element the element to add
	 * @param before The node to add the element before
	 * @return The new node
	 */
	protected N addBefore(E element, N before) {
		N newNode = createNode(element);
		N left = (N) before.getLeft();
		RedBlackNode.TreeOpResult result;
		if (left == null)
			result = before.addOnSide(newNode, true, true);
		else {
			while (left.getRight() != null)
				left = (N) left.getRight();
			result = left.addOnSide(newNode, false, true);
		}
		theRoot = (N) result.getNewRoot();
		return newNode;
	}

	/**
	 * @param element the element to add
	 * @param after The node to add the element after
	 * @return The new node
	 */
	protected N addAfter(E element, N after) {
		N newNode = createNode(element);
		if (after == null) {
			if (theRoot == null) {
				theRoot = newNode;
				newNode.setRed(false);
			} else {
				N farLeft = theRoot;
				while (farLeft.getChild(true) != null)
					farLeft = (N) farLeft.getChild(true);
				RedBlackNode.TreeOpResult result = farLeft.addOnSide(newNode, true, true);
				theRoot = (N) result.getNewRoot();
			}
		} else {
			N right = (N) after.getRight();
			RedBlackNode.TreeOpResult result;
			if (right == null)
				result = after.addOnSide(newNode, false, true);
			else {
				while (right.getLeft() != null)
					right = (N) right.getLeft();
				result = right.addOnSide(newNode, true, true);
			}
			theRoot = (N) result.getNewRoot();
		}
		return newNode;
	}
	
	protected class NodeSpliterator implements ReversibleElementSpliterator<N> {
		private N theBegin;
		private boolean isBeginInclusive;
		private N theEnd;
		private boolean isEndInclusive;
		private N theCurrentNode;
		private N theNextNode;
		private N thePreviousNode;
		private final MutableElementHandle<N> element;
		private final TypeToken<N> theType;
		private final CollectionLockingStrategy.SubLockingStrategy theSubLock;
		
		protected NodeSpliterator(N previous, N next, CollectionLockingStrategy.SubLockingStrategy subLock) {
			thePreviousNode = previous;
			theNextNode = next;
			theSubLock = subLock;
			theType = new TypeToken<N>() {};
			element = new MutableElementHandle<N>() {
				@Override
				public TypeToken<N> getType() {
					return theType;
				}

				@Override
				public Value<String> isEnabled() {
					return Value.constant(TypeToken.of(String.class),
						theCurrentNode == null ? "Element has been removed" : null);
				}

				@Override
				public <V extends N> String isAcceptable(V value) {
					if (theCurrentNode == null)
						return "Element has been removed";
					int todo = todo; // TODO Check if the node is valid for this collection
					return null;
				}

				@Override
				public <V extends N> N set(V value, Object cause) throws IllegalArgumentException {
					if (theCurrentNode == null)
						throw new IllegalArgumentException("Element has been removed");
					N old;
					try (Transaction t = theSubLock.lock(true, null)) {
						if (theCurrentNode == null)
							throw new IllegalArgumentException("Element is already removed");
						old = theCurrentNode;
						int todo = todo; // TODO Check if the node is valid for this collection
						replace(theCurrentNode, value);
						theCurrentNode = value;
						// Could mess up other spliterators or views with references to the previously current node
						theSubLock.indexChanged(0);
					}
					return old;
				}

				@Override
				public N get() {
					if (theCurrentNode == null)
						throw new IllegalStateException("Element has been removed");
					return theSubLock.doOptimistically(null, (v, stamp) -> {
						return theCurrentNode;
					});
				}

				@Override
				public String canRemove() {
					if (theCurrentNode == null)
						return "Element is already removed";
					return null;
				}

				@Override
				public void remove() throws IllegalArgumentException {
					if (theCurrentNode == null)
						throw new IllegalArgumentException("Element is already removed");
					try (Transaction t = theSubLock.lock(true, null)) {
						if (theCurrentNode == null)
							throw new IllegalArgumentException("Element is already removed");
						if (thePreviousNode == theCurrentNode) {
							thePreviousNode = (N) theCurrentNode.getClosest(true);
						} else
							theNextNode = (N) theCurrentNode.getClosest(false);
						delete(theCurrentNode);
						theCurrentNode = null;
						theSubLock.indexChanged(-1);
					}
				}

				@Override
				public String toString() {
					if (theCurrentNode == null)
						return "(removed)";
					else
						return String.valueOf(get());
				}
			};
		}

		/**
		 * Sets the bounds of this spliterator such that it will not iterate through the entire collection
		 * 
		 * @param begin The beginning of this spliterator's subdomain
		 * @param beginInclusive Whether this spliterator's subdomain should be including, or only up to, the given begin
		 * @param end The end of this spliterator's subdomain
		 * @param endInclusive Whether this spliterator's subdomain should be including, or only up to, the given end
		 * @return This spliterator
		 */
		public NodeSpliterator withBounds(N begin, boolean beginInclusive, N end, boolean endInclusive) {
			theBegin = begin;
			isBeginInclusive = beginInclusive;
			theEnd = end;
			isEndInclusive = endInclusive;
			return this;
		}

		public N getPreviousNode() {
			return thePreviousNode;
		}

		public N getNextNode() {
			return theNextNode;
		}

		@Override
		public TypeToken<N> getType() {
			return new TypeToken<N>() {};
		}

		@Override
		public long estimateSize() {
			return theCurrentNode.getSize();
		}

		@Override
		public int characteristics() {
			return Spliterator.NONNULL | Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
		}

		@Override
		public boolean tryAdvanceElement(Consumer<? super MutableElementHandle<N>> action) {
			if (isEndInclusive) {
				if (thePreviousNode == theEnd)
					return false;
			} else {
				if (theNextNode == theEnd)
					return false;
			}
			theSubLock.check();
			theCurrentNode = theNextNode;
			thePreviousNode = theNextNode;
			theNextNode = (N) theNextNode.getClosest(false);
			action.accept(element);
			return true;
		}

		@Override
		public boolean tryReverseElement(Consumer<? super MutableElementHandle<N>> action) {
			if (isBeginInclusive) {
				if (theNextNode == theBegin)
					return false;
			} else {
				if (thePreviousNode == theBegin)
					return false;
			}
			theSubLock.check();
			theCurrentNode = thePreviousNode;
			theNextNode = thePreviousNode;
			thePreviousNode = (N) thePreviousNode.getClosest(true);
			action.accept(element);
			return true;
		}

		@Override
		public ReversibleElementSpliterator<N> trySplit() {
			int startIdx = theBegin == null ? 0 : theBegin.getIndex();
			int endIdx = theEnd == null ? size() : theEnd.getIndex();
			theSubLock.check();
			if (endIdx - startIdx <= 1)
				return null;
			int mid = (startIdx + endIdx) / 2;
			N midNode = getNodeAt(mid);
			N midPlusOne = (N) midNode.getClosest(false);
			int nextIdx = theNextNode == null ? endIdx : theNextNode.getIndex();
			NodeSpliterator split;
			if (nextIdx > mid) {
				split = new NodeSpliterator(midNode, midPlusOne, theSubLock.siblingLock())//
					.withBounds(theBegin, isBeginInclusive, midNode, true);
				theBegin = midNode;
				isBeginInclusive = false;
			} else {
				split = new NodeSpliterator(midNode, midPlusOne, theSubLock.siblingLock())//
					.withBounds(midNode, false, theEnd, isEndInclusive);
				theEnd = midNode;
				isEndInclusive = true;
			}
			return split;
		}
	}
}
