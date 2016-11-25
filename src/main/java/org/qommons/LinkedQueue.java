package org.qommons;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class LinkedQueue<E> extends AbstractQueue<E> {
	private static final long PURGE_TIME = 500;

	private volatile Node<E> theBegin;
	private volatile Node<E> theEnd;
	private volatile int theMods;

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
	public boolean addAll(Collection<? extends E> c) {
		if(c.isEmpty())
			return false;
		Node<E> addedStart=new Node<>(
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void clear() {
		Iterator<E> iter = iterator();
		while (iter.hasNext()) {
			iter.next();
			iter.remove();
		}
	}

	@Override
	public boolean offer(E e) {
		// TODO Auto-generated method stub
		return false;
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
		// TODO Auto-generated method stub
		return null;
	}

	static class Node<E> {
		E theValue;
		int theMods;
		long removeTime = -1;
		Node<E> theNext;

		Node(E value, int mods) {
			theValue = value;
			theMods = mods;
		}
	}
}
