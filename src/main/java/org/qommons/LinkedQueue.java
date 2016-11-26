package org.qommons;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class LinkedQueue<E> extends AbstractQueue<E> {
	private final AtomicReference<BiTuple<Node<E>, Node<E>>> theEnds;
	private final AtomicInteger theMods;

	public LinkedQueue() {
		theEnds = new AtomicReference<>(new BiTuple<>(null, null));
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
		if(c.isEmpty())
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
		theEnds.accumulateAndGet(new BiTuple<>(start, end), (current, added) -> {
			added.getValue1().thePrevious = current.getValue2();
			if (current.getValue1() != null)
				current.getValue2().theNext = added.getValue1();
			Node<E> s = current.getValue1() != null ? current.getValue1() : added.getValue1();
			return new BiTuple<>(s, added.getValue2());
		});
	}

	@Override
	public void clear() {
		theMods.incrementAndGet();
		Node<E> node = theEnds.getAndSet(new BiTuple<>(null, null)).getValue1();
		while (node != null) {
			Node<E> next = node.theNext;
			node.theNext = null;
			node = next;
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
		return new Itr();
	}

	class Itr implements Iterator<E> {
		private boolean needsAdvance = true;
		private Node<E> theCurrent;
		private boolean hasStarted;
		private boolean hasRemoved;

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
				theCurrent = theEnds.get().getValue1();
			} else
				theCurrent = theCurrent.theNext;
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
			if (needsAdvance)
				throw new IllegalStateException("remove() must be called immediately after next()");
			if(hasRemoved)
				throw new IllegalStateException("remove() cannot be called twice in succession");
			if (theCurrent == null)
				throw new IllegalStateException("No element to remove");
			hasRemoved=true;
			Node<E> prev=theCurrent.thePrevious;
			if(prev!=null){
				Node<E> next=theCurrent.theNext;
				if(next==null){
					boolean [] updated=new boolean[1];
					theEnds.updateAndGet(ends->{
						if(ends.getValue2()==theCurrent)
							return new BiTuple<>(ends.getValue1(), prev);
						else
							return ends;
					});
				}
				prev.theNext=next;
				theCurrent=prev;
			} else{
				hasStarted=false;
				if(theEn
				//TODO
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
