package org.qommons.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Simple double-linked list implementation of {@link SequencedDeque}. Very light-weight and basically bullet-proof when used concurrently,
 * as long as only one thread is modifying it at a time. It is not thread-safe when multiple threads are attempting to modify it. Obviously,
 * the data as viewed by read-only threads is somewhat undeterminable if viewed during a modification by another thread, but this class will
 * not throw exceptions.
 * 
 * @param <E> The type of values in the queue
 */
public class SimpleDeque<E> implements SequencedDeque<E>, ListenerQueue<E> {
	private Node theFirst;
	private Node theLast;
	private int theSize;
	private long theStamp;
	private int isFiring;

	@Override
	public long getStamp() {
		return theStamp;
	}

	@Override
	public long incrementStamp() {
		return ++theStamp;
	}

	@Override
	public boolean offerFirst(E e) {
		theFirst = new Node(null, theFirst, e);
		if (theLast == null)
			theLast = theFirst;
		theSize++;
		theStamp++;
		return true;
	}

	@Override
	public boolean offer(E e) {
		theLast = new Node(theLast, null, e);
		if (theFirst == null)
			theFirst = theLast;
		theSize++;
		theStamp++;
		return true;
	}

	@Override
	public Element<E> addNew(E value) {
		Node newNode = new Node(theLast, null, value);
		theLast = newNode;
		if (theFirst == null)
			theFirst = newNode;
		theSize++;
		theStamp++;
		return newNode;
	}

	@Override
	public E pollFirst() {
		Node node = theFirst;
		if (node == null)
			return null;
		node.remove();
		return node.value;
	}

	@Override
	public E pollLast() {
		Node node = theLast;
		if (node == null)
			return null;
		node.remove();
		return node.value;
	}

	@Override
	public E peekFirst() {
		Node first = theFirst;
		return first == null ? null : first.value;
	}

	@Override
	public E peekLast() {
		Node last = theLast;
		return last == null ? null : last.value;
	}

	@Override
	public boolean removeLastOccurrence(Object o) {
		Node node = theLast;
		while (node != null) {
			if (Objects.equals(node.value, o)) {
				node.remove();
				return true;
			}
			node = node.previous;
		}
		return false;
	}

	@Override
	public boolean remove(Object o) {
		Node node = theFirst;
		while (node != null) {
			if (Objects.equals(node.value, o)) {
				node.remove();
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
		Node node = theFirst;
		int found = 0;
		while (node != null) {
			if (Objects.equals(node.value, o)) {
				found++;
				node.remove();
			}
			node = node.next;
		}
		return found;
	}

	@Override
	public boolean contains(Object o) {
		for (Node node = theFirst; node != null; node = node.next) {
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
		for (Node node = theFirst; node != null && index < array.length; node = node.next, index++)
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
		for (Node node = theFirst; node != null && index < array.length; node = node.next, index++)
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
	public boolean containsAny(Collection<?> c) {
		for (Object o : c) {
			if (contains(o))
				return true;
		}
		return false;
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
		for (Node node = theFirst; node != null; node = node.next) {
			if (!c.contains(node.value)) {
				node.remove();
				changed = true;
			}
		}
		return changed;
	}

	@Override
	public void forEach(Consumer<? super E> action) {
		isFiring++;
		try {
			Node node = theFirst;
			while (node != null) {
				try {
					action.accept(node.value);
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
				node = node.next;
			}
		} finally {
			isFiring--;
		}
	}

	@Override
	public boolean isFiring() {
		return isFiring != 0;
	}

	@Override
	public void clear() {
		Node node = theFirst;
		if (node == null)
			return;
		theFirst = theLast = null;
		theSize = 0;
		theStamp++;
		// Need to tell all the elements that they're removed
		while (node != null) {
			node.previous = node; // isPresent() just checks whether previous.next==this
			node = node.next;
		}
	}
	
	@Override
	public int dumpAndClear(Consumer<E> consumer) {
		Node node = theFirst;
		int size = theSize;
		clear();
		while (node != null) {
			consumer.accept(node.value);
			node = node.next;
		}
		return size;
	}

	/** Inspects the internal structure of this deque and throws an {@link AssertionError} if it is internally inconsistent */
	public void checkValid() {
		int size=0;
		if(theFirst==null){
			if(theLast!=null)
				throw new AssertionError("First is null, but last is not");
		} else {
			if(theLast==null)
				throw new AssertionError("First is not null, but last is");
			if(theFirst.previous!=null)
				throw new AssertionError("First node thinks it has a previous node");
			for (Node node = theFirst; node != null; node = node.next) {
				size++;
				if(node.next==null){
					if(theLast!=node)
						throw new AssertionError("Last node by iteration is not the deque's last");
				} else if(node.next.previous!=node)
					throw new AssertionError("Inconsistent node: "+node.value+".next.previous="+node.next.previous.value);
			}
		}
		if(size!=theSize)
			throw new AssertionError("Size is inconsistent: "+size+" vs "+theSize);
	}

	@Override
	public int hashCode() {
		int hash = 0;
		for (Node node = theFirst; node != null; node = node.next)
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
		for (Node node = theFirst;; node = node.next) {
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
		for (Node node = theFirst; node != null; node = node.next) {
			if (first)
				first = false;
			else
				str.append(", ");
			str.append(node.value);
		}
		return str.append(']').toString();
	}

	void insert(E newValue, Node atNode, boolean before) {
		Node newNode = new Node(before ? atNode.previous : atNode, before ? atNode : atNode.next, newValue);
		if (newNode.previous == null)
			theFirst = newNode;
		if (newNode.next == null)
			theLast = newNode;
		theSize++;
		theStamp++;
	}

	class Node implements Element<E> {
		Node previous;
		Node next;
		E value;

		Node(Node previous, Node next, E value) {
			this.previous = previous;
			this.next = next;
			this.value = value;
			if (previous != null)
				previous.next = this;
			if (next != null)
				next.previous = this;
		}

		@Override
		public E get() {
			return value;
		}

		@Override
		public void set(E value) {
			this.value = value;
		}

		@Override
		public boolean isPresent() {
			if (previous == null)
				return theFirst == this;
			else
				return previous.next == this;
		}

		@Override
		public boolean remove() {
			if (previous != null) {
				if (previous.next != this)
					return false; // Likely already removed, otherwise it's an internal error
				previous.next = next;
			} else {
				if (theFirst != this)
					return false; // Likely already removed, otherwise it's an internal error
				theFirst = next;
			}
			// We've established that the node is in the sequence, so no need to check again for next
			if (next != null)
				next.previous = previous;
			else
				theLast = previous;
			theSize--;
			theStamp++;
			return true;
		}

		@Override
		public void unsubscribe() {
			Node prev = previous, nxt = next;
			if (prev != null) {
				if (prev.next != this)
					return; // Likely already removed, otherwise it's an internal error
				previous.next = nxt;
			} else {
				if (theFirst != this)
					return; // Likely already removed, otherwise it's an internal error
				theFirst = nxt;
			}
			// We've established that the node is in the sequence, so no need to check again for next
			if (nxt != null)
				nxt.previous = prev;
			else
				theLast = previous;
			theSize--;
			theStamp++;
		}

		Node getAdjacent(boolean forward) {
			return forward ? next : previous;
		}

		@Override
		public String toString() {
			return String.valueOf(value);
		}
	}

	class SimpleDequeSequence implements Sequence<E> {
		private final boolean isStartAtFirst;
		private Node theNode;

		SimpleDequeSequence(boolean first) {
			isStartAtFirst = first;
		}

		@Override
		public boolean advance(boolean forward) {
			if (theNode != null) {
				Node next = theNode.getAdjacent(forward);
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
			Node node = theNode;
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
			theNode.remove();
			Node next = theNode.getAdjacent(true);
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
