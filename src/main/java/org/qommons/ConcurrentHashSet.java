package org.qommons;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentHashSet<E> extends AbstractSet<E> {
	private final ConcurrentHashMap<E, E> theBackingMap = new ConcurrentHashMap<>();

	@Override
	public Iterator<E> iterator() {
		return theBackingMap.keySet().iterator();
	}

	@Override
	public int size() {
		return theBackingMap.size();
	}

	@Override
	public boolean isEmpty() {
		return theBackingMap.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return theBackingMap.containsKey(o);
	}

	@Override
	public Object[] toArray() {
		return theBackingMap.keySet().toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return theBackingMap.keySet().toArray(a);
	}

	@Override
	public boolean add(E e) {
		return theBackingMap.put(e, e) == null;
	}

	@Override
	public boolean remove(Object o) {
		return theBackingMap.remove(o) != null;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return theBackingMap.keySet().containsAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return theBackingMap.keySet().retainAll(c);
	}

	@Override
	public void clear() {
		theBackingMap.clear();
	}
}
