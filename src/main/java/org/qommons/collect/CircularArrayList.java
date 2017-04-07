package org.qommons.collect;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.qommons.ReversibleCollection;
import org.qommons.Transaction;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

public class CircularArrayList<E> implements BetterCollection<E>, ReversibleCollection<E>, TransactableList<E>, Deque<E> {
	/**
	 * The maximum size of array to allocate. Some VMs reserve some header words in an array. Attempts to allocate larger arrays may result
	 * in OutOfMemoryError: Requested array size exceeds VM limit
	 */
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	private Object[] theArray;
	private int theOffset;
	private int theSize;

	private final ReentrantReadWriteLock theLock;

	public CircularArrayList() {
		this(true);
	}

	public CircularArrayList(boolean locking) {
		this(locking ? new ReentrantReadWriteLock() : null, 10);
	}

	public CircularArrayList(ReentrantReadWriteLock lock, int initCap) {
		theLock = lock;
		theArray = new Object[initCap];
	}

	@Override
	public boolean isLockSupported() {
		return theLock != null;
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		if (theLock == null)
			return Transaction.NONE;
		Lock lock = write ? theLock.writeLock() : theLock.readLock();
		lock.lock();
		return () -> lock.unlock();
	}

	@Override
	public int size() {
		return theSize;
	}

	@Override
	public boolean isEmpty() {
		return theSize == 0;
	}

	@Override
	public ElementSpliterator<E> spliterator() {
		class ArraySpliterator implements ElementSpliterator<E> {
			private int theStart;
			private int theEnd;
			private int theTranslatedIndex;
			private final CollectionElement<E> element;
			private boolean isRemoved;

			ArraySpliterator(int start, int end) {
				theStart = start;
				theEnd = end;
				theTranslatedIndex = translateToInternalIndex(theStart, false);
				element = new CollectionElement<E>() {
					@Override
					public TypeToken<E> getType() {
						return ArraySpliterator.this.getType();
					}

					@Override
					public Value<String> isEnabled() {
						return Value.constant(TypeToken.of(String.class), null);
					}

					@Override
					public <V extends E> String isAcceptable(V value) {
						return null;
					}

					@Override
					public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
						E old = (E) theArray[theTranslatedIndex];
						theArray[theTranslatedIndex] = value;
						return old;
					}

					@Override
					public E get() {
						return (E) theArray[theTranslatedIndex];
					}

					@Override
					public String canRemove() {
						if (isRemoved)
							return "Element is already removed";
						return null;
					}

					@Override
					public void remove() throws IllegalArgumentException {
						if (isRemoved)
							throw new IllegalArgumentException("Element is already removed");
						isRemoved = true;
						internalRemove(theStart);
					}
				};
			}

			@Override
			public TypeToken<E> getType() {
				return (TypeToken<E>) TypeToken.of(Object.class);
			}

			@Override
			public long estimateSize() {
				return theEnd - theStart;
			}

			@Override
			public int characteristics() {
				return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
			}

			@Override
			public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
				if (theStart == theEnd)
					return false;
				action.accept(element);
				theStart++;
				theTranslatedIndex++;
				if (theTranslatedIndex == theArray.length)
					theTranslatedIndex = 0;
				return true;
			}

			@Override
			public ElementSpliterator<E> trySplit() {
				if (theEnd - theStart <= 1)
					return null;
				int mid = (theStart + theEnd) / 2;
				theEnd = mid;
				return new ArraySpliterator(mid, theEnd);
			}
		}
	}

	@Override
	public boolean contains(Object o) {
		try (Transaction t = lock(false, null)) {
			for (E value : this)
				if (Objects.equals(value, o))
					return true;
		}
		return false;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object[] toArray() {
		try (Transaction t = lock(false, null)) {
			Object[] array = new Object[theSize];
			internalArrayCopy(array);
			return array;
		}
	}

	@Override
	public <T> T[] toArray(T[] a) {
		try (Transaction t = lock(false, null)) {
			if (a.length < theSize)
				a = (T[]) Array.newInstance(a.getClass().getComponentType(), theSize);
			internalArrayCopy(a);
			return a;
		}
	}

	private void internalArrayCopy(Object[] a) {
		System.arraycopy(theArray, theOffset, a, 0, Math.min(theSize, theArray.length - theOffset));
		if (theOffset + theSize > theArray.length)
			System.arraycopy(theArray, 0, a, theArray.length - theOffset, theSize - theArray.length + theOffset);
	}

	protected int translateToInternalIndex(int index, boolean adding) {
		if (index < 0)
			throw new ArrayIndexOutOfBoundsException(index);
		else if (index > theSize)
			throw new ArrayIndexOutOfBoundsException(index + " of " + theSize);
		else if (index == theSize) {
			if (!adding)
				throw new ArrayIndexOutOfBoundsException(index + " of " + theSize);
			else
				ensureCapacity(theSize + 1);
		}
		return (index + theOffset) % theArray.length;
	}

	public boolean ensureCapacity(int size) {
		if (theArray.length < size) {
			// overflow-conscious code
			int oldCapacity = theArray.length;
			int newCapacity = oldCapacity + (oldCapacity >> 1);
			if (newCapacity - size < 0)
				newCapacity = size;
			if (newCapacity - MAX_ARRAY_SIZE > 0)
				newCapacity = hugeCapacity(size);
			Object[] newArray = new Object[newCapacity];
			if (theSize > 0) {
				internalArrayCopy(newArray);
			}
			theOffset = 0;
			theArray = newArray;
		}
	}

	private static int hugeCapacity(int minCapacity) {
		if (minCapacity < 0) // overflow
			throw new OutOfMemoryError();
		return (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
	}

	@Override
	public E get(int index) {
		try (Transaction t = lock(false, null)) {
			return (E) theArray[translateToInternalIndex(index, false)];
		}
	}

	@Override
	public int indexOf(Object o) {
		try (Transaction t = lock(false, null)) {
			for (int i = 0; i < theSize; i++)
				if (Objects.equals(theArray[translateToInternalIndex(i, false)], o))
					return i;
		}
		return -1;
	}

	@Override
	public int lastIndexOf(Object o) {
		try (Transaction t = lock(false, null)) {
			for (int i = theSize - 1; i >= 0; i--)
				if (Objects.equals(theArray[translateToInternalIndex(i, false)], o))
					return i;
		}
		return -1;
	}

	@Override
	public E set(int index, E element) {
		// Although we're modifying, the modification is atomic--no need to lock for write
		try (Transaction t = lock(false, null)) {
			int translated = translateToInternalIndex(index, false);
			E old = (E) theArray[index];
			theArray[index] = element;
			return old;
		}
	}

	@Override
	public boolean add(E e) {
		try (Transaction t = lock(true, null)) {
			if (ensureCapacity(theSize + 1)) {
				theArray[theSize] = e;
				theSize++;
			}
		}
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		try (Transaction t = lock(true, null)) {
			ensureCapacity(theSize + c.size());
			if (theSize == 0) {
				c.toArray(theArray);
				theOffset = 0;
				theSize = c.size();
			} else {
				theSize += c.size();
				int idx = translateToInternalIndex(theSize, false);
				for (E e : c) {
					theArray[idx] = e;
					idx++;
					if (idx == theArray.length)
						idx = 0;
				}
			}
		}
		return true;
	}

	@Override
	public void add(int index, E element) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean remove(Object o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void clear() {
		try (Transaction t = lock(true, null)) {
			theSize = 0;
			theOffset = 0;
		}
	}

	@Override
	public E remove(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<E> descending() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addFirst(E e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void addLast(E e) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean offerFirst(E e) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean offerLast(E e) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public E removeFirst() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E removeLast() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E pollFirst() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E pollLast() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E getFirst() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E getLast() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E peekFirst() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E peekLast() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean removeFirstOccurrence(Object o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean removeLastOccurrence(Object o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean offer(E e) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public E remove() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E poll() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E element() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public E peek() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void push(E e) {
		// TODO Auto-generated method stub

	}

	@Override
	public E pop() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterator<E> descendingIterator() {
		// TODO Auto-generated method stub
		return null;
	}
}
