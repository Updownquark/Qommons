package org.qommons.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Simple double-linked list implementation of {@link SequencedDeque}. Very light-weight and basically bullet-proof when used concurrently,
 * as long as only one thread is modifying it at a time. It is not thread-safe when multiple threads are attempting to modify it. Obviously,
 * the data as viewed by read-only threads is somewhat undeterminable if viewes during a modification by another thread, but this class will
 * not throw exceptions.
 * 
 * @param <E> The type of values in the queue
 */
public class SimpleDeque<E> implements SequencedDeque<E> {
	private Node<E> theFirst;
	private Node<E> theLast;
	private int theSize;
	private long theStamp;

	@Override
	public boolean offerFirst(E e) {
		theFirst = new Node<>(null, theFirst, e);
		if (theLast == null)
			theLast = theFirst;
		theSize++;
		theStamp++;
		return true;
	}

	@Override
	public boolean offerLast(E e) {
		theLast = new Node<>(theLast, null, e);
		if (theFirst == null)
			theFirst = theLast;
		theSize++;
		theStamp++;
		return true;
	}

	@Override
	public E pollFirst() {
		return remove(theFirst);
	}

	@Override
	public E pollLast() {
		return remove(theLast);
	}

	@Override
	public E peekFirst() {
		Node<E> first = theFirst;
		return first == null ? null : first.value;
	}

	@Override
	public E peekLast() {
		Node<E> last = theLast;
		return last == null ? null : last.value;
	}

	@Override
	public boolean removeLastOccurrence(Object o) {
		Node<E> node = theLast;
		while (node != null) {
			if (Objects.equals(node.value, o)) {
				remove(node);
				return true;
			}
			node = node.previous;
		}
		return false;
	}

	@Override
	public boolean remove(Object o) {
		Node<E> node = theFirst;
		while (node != null) {
			if (Objects.equals(node.value, o)) {
				remove(node);
				return true;
			}
			node = node.next;
		}
		return false;
	}

	/**
	 * @param o The value to remove
	 * @return The number of occurrences of the value that were found and removed from this deque
	 */
	public int removeAllOccurrences(Object o) {
		Node<E> node = theFirst;
		int found = 0;
		while (node != null) {
			if (Objects.equals(node.value, o)) {
				found++;
				remove(node);
			}
			node = node.next;
		}
		return found;
	}

	@Override
	public boolean contains(Object o) {
		for (Node<E> node = theFirst; node != null; node = node.next) {
			if (Objects.equals(node.value, o))
				return true;
		}
		return false;
	}

	@Override
	public int size() {
		return theSize;
	}

	@Override
	public Sequence<E> sequence(boolean fromBeginning) {
		return new SimpleDequeSequence(fromBeginning);
	}

	@Override
	public boolean isEmpty() {
		return theFirst == null;
	}

	@Override
	public Object[] toArray() {
		Object[] array = new Object[theSize];
		int index = 0;
		for (Node<E> node = theFirst; node != null && index < array.length; node = node.next, index++)
			array[index] = node.value;
		if (index < array.length)
			array = Arrays.copyOf(array, index);
		return array;
	}

	@Override
	public <T> T[] toArray(T[] array) {
		if (array.length < theSize)
			array = Arrays.copyOf(array, theSize);
		int index = 0;
		for (Node<E> node = theFirst; node != null && index < array.length; node = node.next, index++)
			array[index] = (T) node.value;
		if (index < array.length)
			array = Arrays.copyOf(array, index);
		return array;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		for (Object obj : c) {
			if (!contains(obj))
				return false;
		}
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		if (c.isEmpty())
			return false;
		for (E value : c)
			add(value);
		return true;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean changed = false;
		for (Object o : c) {
			if (removeAllOccurrences(o) > 0)
				changed = true;
		}
		return changed;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		boolean changed = false;
		for (Object o : c) {
			if (removeAllOccurrences(o) > 0)
				changed = true;
		}
		return changed;
	}

	@Override
	public void clear() {
		if (theFirst == null)
			return;
		theFirst = theLast = null;
		theSize = 0;
		theStamp++;
	}

	@Override
	public long getStamp() {
		return theStamp;
	}

	@Override
	public boolean containsAny(Collection<?> c) {
		for (Object o : c) {
			if (contains(o))
				return true;
		}
		return false;
	}

	@Override
	public int hashCode() {
		int hash = 0;
		for (Node<E> node = theFirst; node != null; node = node.next)
			hash = 31 * hash + (node.value == null ? 0 : node.value.hashCode());
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof Collection))
			return false;
		Collection<?> other = (Collection<?>) obj;
		if (theSize != other.size())
			return false;
		Iterator<?> otherIter = other.iterator();
		for (Node<E> node = theFirst;; node = node.next) {
			if (node == null)
				return !otherIter.hasNext();
			else if (!otherIter.hasNext())
				return false;
			else if (!Objects.equals(node.value, otherIter.next()))
				return false;
		}
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder().append('[');
		boolean first = true;
		for (Node<E> node = theFirst; node != null; node = node.next) {
			if (first)
				first = false;
			else
				str.append(", ");
			str.append(node.value);
		}
		return str.append(']').toString();
	}

	E remove(Node<E> node) {
		if (node == null || node.removed)
			return null;
		node.removed = true;
		if (node.previous != null)
			node.previous.next = node.next;
		else
			theFirst = node.next;
		if (node.next != null)
			node.next.previous = node.previous;
		else
			theLast = node.previous;
		theSize--;
		theStamp++;
		return node.value;
	}

	void insert(E newValue, Node<E> atNode, boolean before) {
		Node<E> newNode = new Node<>(before ? atNode.previous : atNode, before ? atNode : atNode.next, newValue);
		if (newNode.previous == null)
			theFirst = newNode;
		if (newNode.next == null)
			theLast = newNode;
		theSize++;
		theStamp++;
	}

	static class Node<E> {
		Node<E> previous;
		Node<E> next;
		E value;
		boolean removed;

		Node(Node<E> previous, Node<E> next, E value) {
			this.previous = previous;
			this.next = next;
			this.value = value;
			if (previous != null)
				previous.next = this;
			if (next != null)
				next.previous = this;
		}

		Node<E> getAdjacent(boolean forward) {
			Node<E> adj = forward ? next : previous;
			while (adj != null && adj.removed)
				adj = forward ? adj.next : adj.previous;
			return adj;
		}

		@Override
		public String toString() {
			return String.valueOf(value);
		}
	}

	class SimpleDequeSequence implements Sequence<E> {
		private final boolean isStartAtFirst;
		private Node<E> theNode;

		SimpleDequeSequence(boolean first) {
			isStartAtFirst = first;
		}

		@Override
		public boolean advance(boolean forward) {
			if (theNode != null) {
				Node<E> next = theNode.getAdjacent(forward);
				if (next == null)
					return false;
				theNode = next;
				return true;
			} else {
				theNode = isStartAtFirst ? theFirst : theLast;
				return theNode != null;
			}
		}

		@Override
		public boolean has(boolean next) {
			if (theNode != null)
				return theNode.getAdjacent(next) != null;
			else
				return theFirst != null;
		}

		@Override
		public boolean exists() {
			return theNode != null;
		}

		@Override
		public E get() throws NoSuchElementException {
			Node<E> node = theNode;
			if (node != null)
				return node.value;
			else
				throw new NoSuchElementException();
		}

		@Override
		public String canRemove() {
			if (theNode == null)
				return NO_ELEMENT_AT_POSTION;
			return null;
		}

		@Override
		public void remove() throws IllegalStateException {
			if (theNode == null)
				throw new IllegalStateException(NO_ELEMENT_AT_POSTION);
			SimpleDeque.this.remove(theNode);
			Node<E> next = theNode.getAdjacent(true);
			if (next == null)
				next = theNode.getAdjacent(false);
			theNode = next;
		}

		@Override
		public String isSettable() {
			if (theNode == null)
				return NO_ELEMENT_AT_POSTION;
			return null;
		}

		@Override
		public String isAcceptable(E newValue) {
			if (theNode == null)
				return NO_ELEMENT_AT_POSTION;
			return null;
		}

		@Override
		public void set(E newValue) throws IllegalStateException {
			if (theNode == null)
				throw new IllegalStateException(NO_ELEMENT_AT_POSTION);
			theNode.value = newValue;
		}

		@Override
		public String canAdd(E value, boolean before) {
			return null;
		}

		@Override
		public void add(E newValue, boolean before) {
			if (theNode != null)
				insert(newValue, theNode, before);
			else {
				if (isStartAtFirst)
					offerFirst(newValue);
				else
					offerLast(newValue);
				if (before == isStartAtFirst)
					theNode = isStartAtFirst ? theFirst : theLast;
			}
		}
	}
}
