package org.qommons;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class LinkedQueue<E> extends AbstractQueue<E> {
	private volatile Node<E> theStart;
	private volatile Node<E> theEnd;
	private final ReentrantLock theLock;
	private final AtomicInteger theMods;

	public LinkedQueue() {
		theLock = new ReentrantLock();
		theMods = new AtomicInteger();
	}

	@Override
	public int size() {
		Iterator<E> iter = iterator();
		int size = 0;
		while (iter.hasNext()) {
			size++;
			iter.next();
		}
		return size;
	}

	@Override
	public boolean isEmpty() {
		return iterator().hasNext();
	}

	@Override
	public boolean offer(E e) {
		Node<E> node = new Node<>(e, theMods.getAndIncrement());
		insert(node, node);
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		if (c.isEmpty())
			return false;
		Node<E> addedStart = null;
		Node<E> addedEnd = null;
		int mod = theMods.getAndIncrement();
		for (E value : c) {
			addedEnd = new Node<>(value, mod);
			if (addedStart == null)
				addedStart = addedEnd;
			else
				addedStart.theNext = addedEnd;
		}
		if (addedStart != null)
			insert(addedStart, addedEnd);
		return addedStart != null;
	}

	private void insert(Node<E> start, Node<E> end) {
		theLock.lock();
		try {
			if (theEnd != null) {
				theEnd.theNext = start;
				start.thePrevious = theEnd;
			} else
				theStart = start;
			theEnd = end;
		} finally {
			theLock.unlock();
		}
	}

	@Override
	public void clear() {
		theMods.incrementAndGet();
		theLock.lock();
		try {
			Node<E> node = theStart;
			theStart = theEnd = null;
			while (node != null) {
				Node<E> next = node.theNext;
				node.theNext = null;
				node = next;
			}
		} finally {
			theLock.unlock();
		}
	}

	@Override
	public E remove() {
		Iterator<E> iter = iterator();
		if (iter.hasNext()) {
			E next = iter.next();
			iter.remove();
			return next;
		}
		throw new NoSuchElementException();
	}

	@Override
	public E poll() {
		Iterator<E> iter = iterator();
		if (iter.hasNext()) {
			E next = iter.next();
			iter.remove();
			return next;
		}
		return null;
	}

	@Override
	public E element() {
		Iterator<E> iter = iterator();
		if (iter.hasNext())
			return iter.next();
		throw new NoSuchElementException();
	}

	@Override
	public E peek() {
		Iterator<E> iter = iterator();
		if (iter.hasNext())
			return iter.next();
		return null;
	}

	@Override
	public Iterator<E> iterator() {
		return new Itr(true);
	}

	class Itr implements Iterator<E> {
		private final boolean withLater;
		private final int theItrMods;
		private boolean needsAdvance = true;
		private Node<E> theCurrent;
		private boolean hasStarted;
		private boolean hasRemoved;

		Itr(boolean withLater) {
			this.withLater = withLater;
			theItrMods = withLater ? 0 : theMods.get();
		}

		@Override
		public boolean hasNext() {
			if (needsAdvance)
				advance();
			return theCurrent != null;
		}

		private void advance() {
			needsAdvance = false;
			if (!hasStarted) {
				hasStarted = true;
				theCurrent = theStart;
			} else
				theCurrent = theCurrent.theNext;
			if (!withLater && theCurrent != null && theCurrent.theMods > theItrMods)
				theCurrent = null;
		}

		@Override
		public E next() {
			if (needsAdvance)
				advance();
			if (theCurrent == null)
				throw new NoSuchElementException();
			hasRemoved = false;
			needsAdvance = true;
			return theCurrent.theValue;
		}

		@Override
		public void remove() {
			if (!needsAdvance)
				throw new IllegalStateException("remove() must be called immediately after next()");
			if (hasRemoved)
				throw new IllegalStateException("remove() cannot be called twice in succession");
			if (theCurrent == null)
				throw new IllegalStateException("No element to remove");

			hasRemoved = true;
			theLock.lock();
			try {
				Node<E> prev = theCurrent.thePrevious;
				Node<E> next = theCurrent.theNext;
				if (prev != null)
					prev.theNext = next;
				else {
					hasStarted = false;
					theStart = next;
				}
				if (next != null)
					next.thePrevious = prev;
				else
					theEnd = prev;
				theCurrent = prev;
			} finally {
				theLock.unlock();
			}
		}
	}

	static class Node<E> {
		final E theValue;
		final int theMods;
		volatile Node<E> thePrevious;
		volatile Node<E> theNext;

		Node(E value, int mods) {
			theValue = value;
			theMods = mods;
		}
	}
}
