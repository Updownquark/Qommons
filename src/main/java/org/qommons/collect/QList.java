package org.qommons.collect;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.Transaction;
import org.qommons.value.Value;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * QList doesn't add much in the way of functionality to ReversibleQollection except to implement {@link List}
 * 
 * @param <E> The type of elements in this list
 */
public interface QList<E> extends ReversibleQollection<E>, TransactableList<E> {
	// De-conflicting declarations required by the compiler

	@Override
	default E[] toArray() {
		return ReversibleQollection.super.toArray();
	}

	@Override
	default <T> T[] toArray(T[] a) {
		return ReversibleQollection.super.toArray(a);
	}

	@Override
	default Iterator<E> iterator() {
		return ReversibleQollection.super.iterator();
	}

	@Override
	default int indexOf(Object o) {
		return ReversibleQollection.super.indexOf(o);
	}

	@Override
	default int lastIndexOf(Object o) {
		return ReversibleQollection.super.lastIndexOf(o);
	}

	@Override
	abstract ElementSpliterator<E> spliterator();

	@Override
	abstract E get(int index);

	// Default implementations of redundant List methods

	@Override
	default ListIterator<E> listIterator() {
		return listIterator(0);
	}

	@Override
	default ListIterator<E> listIterator(int index) {
		return new SimpleListIterator<>(this, index);
	}

	/**
	 * A sub-list of this list. The returned list is backed by this list and updated along with it. The index arguments may be any
	 * non-negative value. If this list's size is {@code <=fromIndex}, the list will be empty. If {@code toIndex>} this list's size, the
	 * returned list's size may be less than {@code toIndex-fromIndex}.
	 *
	 * @see java.util.List#subList(int, int)
	 */
	@Override
	default List<E> subList(int fromIndex, int toIndex) {
		return new SubListImpl<>(this, this, fromIndex, toIndex);
	}

	// Overridden methods to return a QList

	@Override
	default QList<E> reverse() {
		return new ReversedQList<>(this);
	}

	@Override
	default <T> QList<T> map(Function<? super E, T> map) {
		return (QList<T>) ReversibleQollection.super.map(map);
	}

	@Override
	default QList<E> filter(Function<? super E, String> filter) {
		return (QList<E>) ReversibleQollection.super.filter(filter);
	}

	@Override
	default <T> QList<T> filter(Class<T> type) {
		return (QList<T>) ReversibleQollection.super.filter(type);
	}

	@Override
	default <T> QList<T> filterMap(FilterMapDef<E, ?, T> filterMap) {
		return new FilterMappedQList<>(this, filterMap);
	}

	@Override
	default <T, V> QList<V> combine(Value<T> arg, BiFunction<? super E, ? super T, V> func) {
		return (QList<V>) ReversibleQollection.super.combine(arg, func);
	}

	@Override
	default <T, V> QList<V> combine(Value<T> arg, TypeToken<V> type, BiFunction<? super E, ? super T, V> func) {
		return (QList<V>) ReversibleQollection.super.combine(arg, type, func);
	}

	@Override
	default <T, V> QList<V> combine(Value<T> arg, TypeToken<V> type, BiFunction<? super E, ? super T, V> func,
		BiFunction<? super V, ? super T, E> reverse) {
		return new CombinedQList<>(this, arg, type, func, reverse);
	}

	@Override
	default QList<E> immutable(String modMsg) {
		return (QList<E>) ReversibleQollection.super.immutable(modMsg);
	}

	@Override
	default QList<E> filterRemove(Function<? super E, String> filter) {
		return (QList<E>) ReversibleQollection.super.filterRemove(filter);
	}

	@Override
	default QList<E> noRemove(String modMsg) {
		return (QList<E>) ReversibleQollection.super.noRemove(modMsg);
	}

	@Override
	default QList<E> filterAdd(Function<? super E, String> filter) {
		return (QList<E>) ReversibleQollection.super.filterAdd(filter);
	}

	@Override
	default QList<E> noAdd(String modMsg) {
		return (QList<E>) ReversibleQollection.super.noAdd(modMsg);
	}

	@Override
	default QList<E> filterModification(Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
		return new ModFilteredQList<>(this, removeFilter, addFilter);
	}

	// Static utility methods

	/**
	 * @param <E> The compile-time type of the list
	 * @param type The run-time type of the list
	 * @param values The values for the list
	 * @return An immutable list with the given values
	 */
	static <E> QList<E> constant(TypeToken<E> type, E... values) {
		return new ConstantQList<>(type, java.util.Arrays.asList(values));
	}

	/**
	 * @param <E> The compile-time type of the list
	 * @param type The run-time type of the list
	 * @param values The values for the list
	 * @return An immutable list with the given values
	 */
	static <E> QList<E> constant(TypeToken<E> type, List<E> values) {
		return new ConstantQList<>(type, values);
	}

	/**
	 * Turns a list of observable values into a list composed of those holders' values
	 *
	 * @param <E> The type of elements held in the values
	 * @param collection The collection to flatten
	 * @return The flattened collection
	 */
	public static <E> QList<E> flattenValues(QList<? extends Value<? extends E>> collection) {
		return new FlattenedValuesList<>(collection);
	}

	/**
	 * Turns an observable value containing an observable list into the contents of the value
	 * 
	 * @param <E> The type of elements in the list
	 * @param collectionObservable The observable value
	 * @return A list representing the contents of the value, or a zero-length list when null
	 */
	public static <E> QList<E> flattenValue(Value<QList<E>> collectionObservable) {
		return new FlattenedValueList<>(collectionObservable);
	}

	/**
	 * Flattens a collection of lists.
	 *
	 * @param <E> The super-type of all list in the wrapping list
	 * @param list The list to flatten
	 * @return A list containing all elements of all lists in the outer list
	 */
	public static <E> QList<E> flatten(QList<? extends QList<? extends E>> list) {
		return new FlattenedQList<>(list);
	}

	/**
	 * @param <T> The supertype of elements in the lists
	 * @param type The super type of all possible lists in the outer list
	 * @param lists The lists to flatten
	 * @return An observable list that contains all the values of the given lists
	 */
	public static <T> QList<T> flattenLists(TypeToken<T> type, QList<? extends T>... lists) {
		type = type.wrap();
		if (lists.length == 0)
			return constant(type);
		QList<QList<T>> wrapper = constant(new TypeToken<QList<T>>() {}.where(new TypeParameter<T>() {}, type), (QList<T>[]) lists);
		return flatten(wrapper);
	}

	/**
	 * Similar to {@link #flatten(QList)} except that the elements from the inner lists can be interspersed in the returned list via a
	 * discriminator function. The relative ordering of each inner list will be unchanged in the returned collection.
	 * 
	 * @param <E> The type of elements in the list
	 * @param coll The list of lists whose elements to intersperse
	 * @param discriminator A function that is given an element from each of the list in the outer list and decides which of those elements
	 *        will be the next element returned in the outer list
	 * @param reverse A function that does the opposite of <code>discriminator</code>: It returns the index of the element which should be
	 *        placed last in the list. The elements given to this function will be in reverse order--the first element in the list the
	 *        function receives will be from the last list in the outer list.
	 * @return A list containing all elements of each of the outer list contents, ordered by the discriminator
	 */
	public static <E> QList<E> intersperse(QList<? extends QList<? extends E>> coll, Function<? super List<E>, Integer> discriminator,
		Function<? super List<E>, Integer> reverse) {
		return new InterspersedQList<>(coll, discriminator, reverse);
	}

	/**
	 * @param <T> The type of the collection
	 * @param collection The collection to wrap as a list
	 * @return A list containing all elements in the collection, ordered and accessible by index
	 */
	public static <T> QList<T> asList(Qollection<T> collection) {
		if (collection instanceof QList)
			return (QList<T>) collection;
		return new CollectionWrappingList<>(collection);
	}

	/**
	 * A default toString() method for list implementations to use
	 *
	 * @param list The list to print
	 * @return The string representation of the list
	 */
	public static String toString(QList<?> list) {
		StringBuilder ret = new StringBuilder("[");
		boolean first = true;
		try (Transaction t = list.lock(false, null)) {
			for (Object value : list) {
				if (!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
		}
		ret.append(']');
		return ret.toString();
	}

	// Implementation member classes

	/**
	 * Implements {@link QList#listIterator()}
	 *
	 * @param <E> The type of values to iterate
	 */
	class SimpleListIterator<E> implements java.util.ListIterator<E> {
		private final List<E> theList;

		/** Index of element to be returned by subsequent call to next. */
		int cursor = 0;

		/**
		 * Index of element returned by most recent call to next or previous. Reset to -1 if this element is deleted by a call to remove.
		 */
		int lastRet = -1;

		SimpleListIterator(List<E> list, int index) {
			theList = list;
			cursor = index;
		}

		@Override
		public boolean hasNext() {
			return cursor != theList.size();
		}

		@Override
		public E next() {
			try {
				int i = cursor;
				E next = theList.get(i);
				lastRet = i;
				cursor = i + 1;
				return next;
			} catch (IndexOutOfBoundsException e) {
				throw new NoSuchElementException();
			}
		}

		@Override
		public boolean hasPrevious() {
			return cursor != 0;
		}

		@Override
		public E previous() {
			try {
				int i = cursor - 1;
				E previous = theList.get(i);
				lastRet = cursor = i;
				return previous;
			} catch (IndexOutOfBoundsException e) {
				throw new NoSuchElementException();
			}
		}

		@Override
		public int nextIndex() {
			return cursor;
		}

		@Override
		public int previousIndex() {
			return cursor - 1;
		}

		@Override
		public void remove() {
			if (lastRet < 0)
				throw new IllegalStateException();

			try {
				theList.remove(lastRet);
				if (lastRet < cursor)
					cursor--;
				lastRet = -1;
			} catch (IndexOutOfBoundsException e) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public void add(E e) {
			try {
				int i = cursor;
				theList.add(i, e);
				lastRet = -1;
				cursor = i + 1;
			} catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public void set(E e) {
			if (lastRet < 0)
				throw new IllegalStateException();

			try {
				theList.set(lastRet, e);
			} catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}
	}

	/**
	 * Implements {@link QList#subList(int, int)}
	 *
	 * @param <E> The type of element in the list
	 */
	class SubListImpl<E> implements RRList<E> {
		private final QList<E> theRoot;
		private final RRList<E> theList;

		private final int theOffset;
		private int theSize;

		protected SubListImpl(QList<E> root, RRList<E> list, int fromIndex, int toIndex) {
			if (fromIndex < 0)
				throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
			if (fromIndex > toIndex)
				throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
			theRoot = root;
			theList = list;
			theOffset = fromIndex;
			theSize = toIndex - fromIndex;
		}

		@Override
		public E get(int index) {
			rangeCheck(index, false);
			return theList.get(index + theOffset);
		}

		@Override
		public int size() {
			int size = theList.size() - theOffset;
			if (theSize < size)
				size = theSize;
			return size;
		}

		@Override
		public boolean isEmpty() {
			return size() == 0;
		}

		@Override
		public boolean contains(Object o) {
			try (Transaction t = theRoot.lock(false, null)) {
				for (Object value : this)
					if (Objects.equals(value, o))
						return true;
				return false;
			}
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			if (c.isEmpty())
				return true;
			ArrayList<Object> copy = new ArrayList<>(c);
			BitSet found = new BitSet(copy.size());
			try (Transaction t = theRoot.lock(false, null)) {
				Iterator<E> iter = iterator();
				while (iter.hasNext()) {
					E next = iter.next();
					int stop = found.previousClearBit(copy.size());
					for (int i = found.nextClearBit(0); i < stop; i = found.nextClearBit(i + 1))
						if (Objects.equals(next, copy.get(i)))
							found.set(i);
				}
				return found.cardinality() == copy.size();
			}
		}

		@Override
		public E[] toArray() {
			ArrayList<E> ret = new ArrayList<>();
			try (Transaction t = theRoot.lock(false, null)) {
				for (E value : this)
					ret.add(value);
			}
			return ret.toArray((E[]) java.lang.reflect.Array.newInstance(theRoot.getType().wrap().getRawType(), ret.size()));
		}

		@Override
		public <T> T[] toArray(T[] a) {
			ArrayList<E> ret = new ArrayList<>();
			try (Transaction t = theRoot.lock(false, null)) {
				for (E value : this)
					ret.add(value);
			}
			return ret.toArray(a);
		}

		@Override
		public int indexOf(Object o) {
			try (Transaction t = theRoot.lock(false, null)) {
				ListIterator<E> it = listIterator();
				int i;
				for (i = 0; it.hasNext(); i++)
					if (Objects.equals(it.next(), o))
						return i;
				return -1;
			}
		}

		@Override
		public int lastIndexOf(Object o) {
			try (Transaction t = theRoot.lock(false, null)) {
				ListIterator<E> it = listIterator(size());
				int i;
				for (i = size() - 1; it.hasPrevious(); i--)
					if (Objects.equals(it.previous(), o))
						return i;
				return -1;
			}
		}

		@Override
		public boolean add(E value) {
			try (Transaction t = theRoot.lock(true, null)) {
				int preSize = theList.size();
				theList.add(theOffset + theSize, value);
				if (preSize < theList.size()) {
					theSize++;
					return true;
				}
				return false;
			}
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			return addAll(size(), c);
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			try (Transaction t = theRoot.lock(true, null)) {
				rangeCheck(index, true);
				int preSize = theList.size();
				theList.addAll(theOffset + index, c);
				int sizeDiff = theList.size() - preSize;
				if (sizeDiff > 0) {
					theSize += sizeDiff;
					return true;
				}
				return false;
			}
		}

		@Override
		public void add(int index, E value) {
			try (Transaction t = theRoot.lock(true, null)) {
				rangeCheck(index, true);
				int preSize = theList.size();
				theList.add(theOffset + index, value);
				if (preSize < theList.size()) {
					theSize++;
				}
			}
		}

		@Override
		public boolean remove(Object o) {
			try (Transaction t = theRoot.lock(true, null)) {
				Iterator<E> it = iterator();
				while (it.hasNext()) {
					if (Objects.equals(it.next(), o)) {
						it.remove();
						return true;
					}
				}
				return false;
			}
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			if (c.isEmpty())
				return false;
			try (Transaction t = theRoot.lock(true, null)) {
				boolean modified = false;
				Iterator<?> it = iterator();
				while (it.hasNext()) {
					if (c.contains(it.next())) {
						it.remove();
						modified = true;
					}
				}
				return modified;
			}
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			if (c.isEmpty()) {
				clear();
				return false;
			}
			try (Transaction t = theRoot.lock(true, null)) {
				boolean modified = false;
				Iterator<E> it = iterator();
				while (it.hasNext()) {
					if (!c.contains(it.next())) {
						it.remove();
						modified = true;
					}
				}
				return modified;
			}
		}

		@Override
		public E remove(int index) {
			try (Transaction t = theRoot.lock(true, null)) {
				rangeCheck(index, false);
				int preSize = theList.size();
				E ret = theList.remove(theOffset + index);
				if (theList.size() < preSize)
					theSize--;
				return ret;
			}
		}

		@Override
		public void clear() {
			if (!isEmpty())
				removeRange(0, size());
		}

		@Override
		public E set(int index, E value) {
			try (Transaction t = theRoot.lock(true, null)) {
				rangeCheck(index, false);
				return theList.set(theOffset + index, value);
			}
		}

		@Override
		public void removeRange(int fromIndex, int toIndex) {
			try (Transaction t = theRoot.lock(true, null)) {
				rangeCheck(fromIndex, false);
				rangeCheck(toIndex, true);
				int preSize = theList.size();
				theList.removeRange(fromIndex + theOffset, toIndex + theOffset);
				int sizeDiff = theList.size() - preSize;
				theSize += sizeDiff;
			}
		}

		@Override
		public List<E> subList(int fromIndex, int toIndex) {
			rangeCheck(fromIndex, false);
			if (toIndex < fromIndex)
				throw new IllegalArgumentException("" + toIndex);
			return new SubListImpl<>(theRoot, this, fromIndex, toIndex);
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			int size = size();
			if (index < 0 || index > size)
				throw new IndexOutOfBoundsException(index + " of " + size);
			return new ListIterator<E>() {
				private final ListIterator<E> backing = theList.listIterator(theOffset + index);

				private int theIndex = index;

				private boolean lastPrevious;

				@Override
				public boolean hasNext() {
					if (theIndex >= theSize)
						return false;
					return backing.hasNext();
				}

				@Override
				public E next() {
					if (theIndex >= theSize)
						throw new NoSuchElementException();
					theIndex++;
					lastPrevious = false;
					return backing.next();
				}

				@Override
				public boolean hasPrevious() {
					if (theIndex <= 0)
						return false;
					return backing.hasPrevious();
				}

				@Override
				public E previous() {
					if (theIndex <= 0)
						throw new NoSuchElementException();
					theIndex--;
					lastPrevious = true;
					return backing.previous();
				}

				@Override
				public int nextIndex() {
					return theIndex;
				}

				@Override
				public int previousIndex() {
					return theIndex - 1;
				}

				@Override
				public void remove() {
					try (Transaction t = theRoot.lock(true, null)) {
						int preSize = theList.size();
						backing.remove();
						if (theList.size() < preSize) {
							theSize--;
							if (!lastPrevious)
								theIndex--;
						}
					}
				}

				@Override
				public void set(E e) {
					backing.set(e);
				}

				@Override
				public void add(E e) {
					try (Transaction t = theRoot.lock(true, null)) {
						int preSize = theList.size();
						backing.add(e);
						if (theList.size() > preSize) {
							theSize++;
							theIndex++;
						}
					}
				}
			};
		}

		private void rangeCheck(int index, boolean withAdd) {
			if (index < 0 || (!withAdd && index >= theSize) || (withAdd && index > theSize))
				throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
		}

		private String outOfBoundsMsg(int index) {
			return "Index: " + index + ", Size: " + theSize;
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder("[");
			boolean first = true;
			try (Transaction t = theRoot.lock(false, null)) {
				for (E value : this) {
					if (!first) {
						ret.append(", ");
					} else
						first = false;
					ret.append(value);
				}
			}
			ret.append(']');
			return ret.toString();
		}
	}

	/**
	 * Implements {@link QList#reverse()}
	 * 
	 * @param <E> The type of elements in the list
	 */
	class ReversedQList<E> extends ReversedQollection<E> implements QList<E> {
		public ReversedQList(QList<E> wrap) {
			super(wrap);
		}

		@Override
		protected QList<E> getWrapped() {
			return (QList<E>) super.getWrapped();
		}

		@Override
		public QList<E> reverse() {
			return (QList<E>) super.reverse();
		}
	}

	/**
	 * Implements {@link QList#filterMap(Qollection.FilterMapDef)}
	 * 
	 * @param <E> The type of elements in the source list
	 * @param <T> The type of elements in this list
	 */
	class FilterMappedQList<E, T> extends FilterMappedReversibleQollection<E, T> implements QList<T> {
		public FilterMappedQList(QList<E> wrap, org.qommons.collect.Qollection.FilterMapDef<E, ?, T> def) {
			super(wrap, def);
		}

		@Override
		protected QList<E> getWrapped() {
			return (QList<E>) super.getWrapped();
		}

		@Override
		public T get(int index) {
			return super.get(index);
		}
	}

	/**
	 * Implements {@link QList#combine(Value, TypeToken, BiFunction, BiFunction)}
	 * 
	 * @param <E> The type of elements in the source list
	 * @param <T> The type of the value to combine with
	 * @param <V> The type of elements in this list
	 */
	class CombinedQList<E, T, V> extends CombinedReversibleQollection<E, T, V> implements QList<V> {
		public CombinedQList(QList<E> collection, Value<T> value, TypeToken<V> type, BiFunction<? super E, ? super T, V> map,
			BiFunction<? super V, ? super T, E> reverse) {
			super(collection, value, type, map, reverse);
		}

		@Override
		protected QList<E> getWrapped() {
			return (QList<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link QList#filterModification(Function, Function)}
	 * 
	 * @param <E> The type of elements in the list
	 */
	class ModFilteredQList<E> extends ModFilteredReversibleQollection<E> implements QList<E> {
		public ModFilteredQList(QList<E> wrapped, Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
			super(wrapped, removeFilter, addFilter);
		}

		@Override
		protected QList<E> getWrapped() {
			return (QList<E>) super.getWrapped();
		}
	}

	/**
	 * Implements {@link QList#constant(TypeToken, List)}
	 * 
	 * @param <E> The type of elements in the list
	 */
	class ConstantQList<E> extends ConstantOrderedQollection<E> implements QList<E> {
		public ConstantQList(TypeToken<E> type, List<E> collection) {
			super(type, collection);
		}

		@Override
		public ElementSpliterator<E> reverseSpliterator() {
			ListIterator<E> wrapped = getWrapped().listIterator(getWrapped().size());
			return new ElementSpliterator<E>() {
				@Override
				public long estimateSize() {
					return getWrapped().size();
				}

				@Override
				public int characteristics() {
					return Spliterator.IMMUTABLE | Spliterator.SIZED;
				}

				@Override
				public TypeToken<E> getType() {
					return ConstantQList.this.getType();
				}

				@Override
				public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
					if (!wrapped.hasPrevious())
						return false;
					E prev = wrapped.previous();
					action.accept(new CollectionElement<E>() {
						@Override
						public TypeToken<E> getType() {
							return ConstantQList.this.getType();
						}

						@Override
						public E get() {
							return prev;
						}

						@Override
						public Value<String> isEnabled() {
							return Value.constant("This collection may not be modified");
						}

						@Override
						public <V extends E> String isAcceptable(V value) {
							return "This collection may not be modified";
						}

						@Override
						public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
							throw new IllegalArgumentException("This collection may not be modified");
						}

						@Override
						public String canRemove() {
							return "This collection may not be modified";
						}

						@Override
						public void remove() throws IllegalArgumentException {
							throw new IllegalArgumentException("This collection may not be modified");
						}
					});
					return true;
				}

				@Override
				public boolean tryAdvance(Consumer<? super E> action) {
					if (!wrapped.hasPrevious())
						return false;
					E prev = wrapped.previous();
					action.accept(prev);
					return true;
				}

				@Override
				public ElementSpliterator<E> trySplit() {
					return null;
				}
			};
		}
	}

	/**
	 * Implements {@link QList#flattenValues(QList)}
	 * 
	 * @param <E> The type of elements in the list
	 */
	class FlattenedValuesList<E> extends FlattenedOrderedValuesQollection<E> implements QList<E> {
		public FlattenedValuesList(QList<? extends Value<? extends E>> collection) {
			super(collection);
		}

		@Override
		protected QList<? extends Value<? extends E>> getWrapped() {
			return (QList<? extends Value<? extends E>>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			return get(getWrapped().get(index));
		}

		@Override
		public ElementSpliterator<E> reverseSpliterator() {
			return wrap(getWrapped().reverseSpliterator());
		}
	}

	/**
	 * Implements {@link QList#flattenValue(Value)}
	 * 
	 * @param <E> The type of elements in the list
	 */
	class FlattenedValueList<E> extends FlattenedOrderedValueQollection<E> implements QList<E> {
		public FlattenedValueList(Value<? extends QList<E>> collectionObservable) {
			super(collectionObservable);
		}

		@Override
		protected Value<? extends QList<E>> getWrapped() {
			return (Value<? extends QList<E>>) super.getWrapped();
		}

		@Override
		public E get(int index) {
			QList<E> list = getWrapped().get();
			if (list == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			return list.get(index);
		}

		@Override
		public ElementSpliterator<E> reverseSpliterator() {
			QList<E> list = getWrapped().get();
			if (list == null)
				return ElementSpliterator.empty(FlattenedValueList.this.getType());
			return wrap(list.reverseSpliterator());
		}
	}

	/**
	 * Implements {@link QList#flatten(QList)}
	 * 
	 * @param <E> The type of elements in the list
	 */
	class FlattenedQList<E> extends FlattenedOrderedQollection<E> implements QList<E> {
		public FlattenedQList(QList<? extends QList<? extends E>> outer) {
			super(outer);
		}

		@Override
		protected QList<? extends QList<? extends E>> getWrapped() {
			return (QList<? extends QList<? extends E>>) super.getWrapped();
		}

		@Override
		public ElementSpliterator<E> reverseSpliterator() {
			return wrap(getWrapped().reverseSpliterator(), coll -> ((ReversibleQollection<E>) coll).reverseSpliterator());
		}
	}

	/**
	 * Implements {@link QList#intersperse(QList, Function, Function)}
	 * 
	 * @param <E> The type of elements in the list
	 */
	class InterspersedQList<E> extends InterspersedQollection<E> implements QList<E> {
		private final Function<? super List<E>, Integer> theReverse;

		public InterspersedQList(QList<? extends QList<? extends E>> coll, Function<? super List<E>, Integer> discriminator,
			Function<? super List<E>, Integer> reverse) {
			super(coll, discriminator);
			theReverse = reverse;
		}

		@Override
		protected QList<? extends QList<? extends E>> getWrapped() {
			return (QList<? extends QList<? extends E>>) super.getWrapped();
		}

		protected Function<? super List<E>, Integer> getReverse() {
			return theReverse;
		}

		@Override
		public E get(int index) {
			return super.get(index); // Don't think there's a better way to walk the list
		}

		@Override
		public ElementSpliterator<E> reverseSpliterator() {
			return intersperse(getWrapped().reverseSpliterator(), coll -> ((ReversibleQollection<? extends E>) coll).reverseSpliterator(),
				theReverse);
		}
	}

	/**
	 * Implements {@link QList#asList(Qollection)}
	 * 
	 * @param <E> The type of elements in the list
	 */
	class CollectionWrappingList<E> implements QList<E> {
		private final Qollection<E> theWrapped;

		public CollectionWrappingList(Qollection<E> wrap) {
			theWrapped = wrap;
		}

		protected Qollection<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public TypeToken<E> getType() {
			return theWrapped.getType();
		}

		@Override
		public int size() {
			return theWrapped.size();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theWrapped.lock(write, cause);
		}

		@Override
		public ElementSpliterator<E> spliterator() {
			return theWrapped.spliterator();
		}

		@Override
		public E get(int index) {
			if (theWrapped instanceof OrderedQollection)
				return ((OrderedQollection<E>) theWrapped).get(index);
			try (Transaction t = theWrapped.lock(false, null)) {
				if (index < 0 || index >= theWrapped.size())
					throw new IndexOutOfBoundsException(index + " of " + theWrapped.size());
				Iterator<E> iter = theWrapped.iterator();
				for (int i = 0; i < index; i++)
					iter.next();
				return iter.next();
			}
		}

		@Override
		public ElementSpliterator<E> reverseSpliterator() {
			if (theWrapped instanceof ReversibleQollection)
				return ((ReversibleQollection<E>) theWrapped).reverseSpliterator();
			ArrayList<E> reversed;
			try (Transaction t = theWrapped.lock(false, null)) {
				reversed = new ArrayList<>(theWrapped.size());
				reversed.addAll(theWrapped);
			}
			Collections.reverse(reversed);
			return new ElementSpliterator.SimpleSpliterator<>(reversed.spliterator(), getType(), () -> v -> new CollectionElement<E>() {
				@Override
				public TypeToken<E> getType() {
					return CollectionWrappingList.this.getType();
				}

				@Override
				public E get() {
					return v;
				}

				@Override
				public Value<String> isEnabled() {
					return Value.constant("This reversed iterator does not support modification");
				}

				@Override
				public <V extends E> String isAcceptable(V value) {
					return "This reversed iterator does not support modification";
				}

				@Override
				public <V extends E> E set(V value, Object cause) throws IllegalArgumentException {
					throw new IllegalArgumentException("This reversed iterator does not support modification");
				}

				@Override
				public String canRemove() {
					return "This reversed iterator does not support modification";
				}

				@Override
				public void remove() throws IllegalArgumentException {
					throw new IllegalArgumentException("This reversed iterator does not support modification");
				}
			});
		}

		@Override
		public String canRemove(Object value) {
			return theWrapped.canRemove(value);
		}

		@Override
		public String canAdd(E value) {
			return theWrapped.canAdd(value);
		}
	}
}
