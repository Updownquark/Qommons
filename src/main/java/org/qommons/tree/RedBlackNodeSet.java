package org.qommons.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transaction;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.MutableElementHandle;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ReversibleElementSpliterator;
import org.qommons.collect.ReversibleList;
import org.qommons.collect.ReversibleSpliterator;
import org.qommons.collect.TransactableList;
import org.qommons.collect.TransactableSortedSet;

import com.google.common.reflect.TypeToken;

public class RedBlackNodeSet<E> implements ReversibleList<E>, BetterSortedSet<E>, TransactableList<E>, TransactableSortedSet<E> {
	private final Function<E, RedBlackTreeNode<E>> theNodeCreator;
	private final CollectionLockingStrategy theLocker;

	private N theRoot;

	@Override
	public boolean forElement(E value, Consumer<? super MutableElementHandle<? extends E>> onElement, boolean first) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean belongs(Object o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int size() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object[] toArray() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T> T[] toArray(T[] a) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean add(E e) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub

	}

	@Override
	public ReversibleElementSpliterator<E> mutableSpliterator(boolean fromStart) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public E get(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E set(int index, E element) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void add(int index, E element) {
		// TODO Auto-generated method stub

	}

	@Override
	public E remove(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int indexOf(Object o) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int lastIndexOf(Object o) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Comparator<? super E> comparator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TypeToken<E> getType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int indexFor(Comparable<? super E> search) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public E relative(Comparable<? super E> search, boolean up) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean forValue(Comparable<? super E> search, boolean up, Consumer<? super E> onValue) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean forElement(Comparable<? super E> search, boolean up, Consumer<? super MutableElementHandle<? extends E>> onElement) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ReversibleElementSpliterator<E> mutableSpliterator(Comparable<? super E> search, boolean up) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ReversibleSpliterator<E> spliterator(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ReversibleElementSpliterator<E> mutableSpliterator(int index) {
		// TODO Auto-generated method stub
		return null;
	}

}
