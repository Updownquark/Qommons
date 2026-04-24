package org.qommons;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.qommons.collect.BetterBitSet;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterableList;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.DequeList;
import org.qommons.ex.ExIterable;

/** Utilities dealing with {@link Iterable}s and {@link Iterator}s */
public class IterableUtils {
	/**
	 * Allows {@link IterableUtils#conditionalIterator(Iterator, Accepter, boolean)} to discriminately return iterated values
	 *
	 * @param <T> The type returned from the wrapped iterator
	 * @param <V> The type returned from the returned iterator
	 */
	public static interface Accepter<T, V> {
		/**
		 * @param value The value from the wrapped iterator to check for acceptance
		 * @return The value to return from the returned iterator, or null to not accept the value
		 */
		V accept(T value);
	}

	/**
	 * @param start The index to start at (inclusive)
	 * @param end The index to end at (exclusive)
	 * @return An iterable that iterates through integers starting at the given start and incrementing or decrementing until the given end
	 */
	public static BetterableList<Integer> indexList(int start, int end) {
		return new IndexCollection(start, end);
	}

	/**
	 * @param <T> The type of the value to iterate over
	 * @param value The supplier for the single value of the iterator
	 * @return An iterable that supplies iterators that return a single value, supplied by the given supplier
	 */
	public static <T> Betterable<T> single(Supplier<T> value) {
		class SingleIterator implements Iterator<T> {
			private boolean used;

			@Override
			public boolean hasNext() {
				return !used;
			}

			@Override
			public T next() {
				if (!used) {
					used = true;
					return value.get();
				}
				throw new NoSuchElementException();
			}
		}
		return new ToStringIterable<>(SingleIterator::new);
	}

	/**
	 * @param <T> The type of the value to iterate over
	 * @param value The single value of the iterator
	 * @return An iterable that supplies iterators that returns the given single value
	 */
	public static <T> Betterable<T> single(T value) {
		class SingleIterator implements Iterator<T> {
			private boolean used;

			@Override
			public boolean hasNext() {
				return !used;
			}

			@Override
			public T next() {
				if (!used) {
					used = true;
					return value;
				}
				throw new NoSuchElementException();
			}
		}
		return new ToStringIterable<>(SingleIterator::new);
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param array The array to iterate over
	 * @param forward Whether to iterate forward through the array or backward
	 * @return An iterable that returns an iterator to iterate over each element in the array
	 */
	public static <T> Betterable<T> iterable(final T[] array, final boolean forward) {
		return new ToStringIterable<>(() -> IterableUtils.iterator(array, forward));
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param array The array to iterate over
	 * @param forward Whether to iterate forward through the array or backward
	 * @return An iterator to iterate over each element in the array
	 */
	public static <T> Iterator<T> iterator(final T[] array, final boolean forward) {
		class ArrayIterator implements Iterator<T> {
			private int theIndex = forward ? 0 : array.length - 1;

			@Override
			public boolean hasNext() {
				return forward ? theIndex < array.length : theIndex >= 0;
			}

			@Override
			public T next() {
				T ret = array[theIndex];
				if (forward)
					theIndex++;
				else
					theIndex--;
				return ret;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		}
		return new ArrayIterator();
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param array The array to iterate over
	 * @return An iterator to iterate over each element in the array
	 */
	@Deprecated
	public static <T> Betterable<T> iterable(final T[] array) {
		return iterable(array, true);
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param components The iterables to concatenate
	 * @return An Iterable that iterates through all elements in the given iterables
	 */
	public static <T> Betterable<T> concat(final Iterable<? extends T>... components) {
		return IterableUtils.flatten(Arrays.asList(components));
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param components The collections to concatenate in a single iterable
	 * @return A collection containing the values of all the given collections
	 */
	public static <T> Collection<T> concat(final Collection<? extends T>... components) {
		Betterable<T> iterable = IterableUtils.flatten(Arrays.asList(components));
		class ConcatenatedCollection extends AbstractCollection<T> implements Betterable<T> {
			@Override
			public Iterator<T> iterator() {
				return iterable.iterator();
			}

			@Override
			public int size() {
				int size = 0;
				for (Collection<? extends T> component : components)
					size += component.size();
				return size;
			}
		}
		return new ConcatenatedCollection();
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param compound The iterables to compound in a single iterable
	 * @return An Iterable that iterates through all elements in the given iterables
	 */
	public static <T> Betterable<T> flatten(final Iterable<? extends Iterable<? extends T>> compound) {
		class FlattenedIterator implements Iterator<T> {
			private final Iterator<? extends Iterable<? extends T>> theCompoundIter = compound.iterator();
			private Iterator<? extends T> theLastValueIter;

			private Iterator<? extends T> theCurrentIter;

			private boolean calledHasNext;

			@Override
			public boolean hasNext() {
				calledHasNext = true;

				boolean currentIterHasValue = theCurrentIter != null && theCurrentIter.hasNext();
				while (!currentIterHasValue && theCompoundIter.hasNext()) {
					theCurrentIter = theCompoundIter.next().iterator();
					currentIterHasValue = theCurrentIter != null && theCurrentIter.hasNext();
				}
				return currentIterHasValue;
			}

			@Override
			public T next() {
				if (!calledHasNext && !hasNext())
					throw new NoSuchElementException();
				if (theCurrentIter == null)
					throw new NoSuchElementException();
				calledHasNext = false;
				T next = theCurrentIter.next();
				theLastValueIter = theCurrentIter;
				return next;
			}

			@Override
			public void remove() {
				if (theLastValueIter == null)
					throw new IllegalStateException("remove() must be called after next()");
				else
					theLastValueIter.remove();
			}
		}
		return new ToStringIterable<>(FlattenedIterator::new);
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param compound The iterators to compound in a single iterator
	 * @return An iterator that iterates through all elements in the given iterators
	 */
	public static <T> Iterator<T> iterator(final Iterator<? extends T>... compound) {
		class CompoundIterator implements Iterator<T> {
			private Iterator<? extends T> theLastValueIter;

			private Iterator<? extends T> theCurrentIter;

			private int theNextIndex;

			private boolean calledHasNext;

			private boolean currentIterHasValue;

			@Override
			public boolean hasNext() {
				calledHasNext = true;
				if (theCurrentIter != null && !theCurrentIter.hasNext()) {
					if (currentIterHasValue)
						theLastValueIter = theCurrentIter;
					theCurrentIter = null;
				}
				while (theCurrentIter == null && theNextIndex < compound.length) {
					currentIterHasValue = false;
					theCurrentIter = compound[theNextIndex++];
					if (!theCurrentIter.hasNext())
						theCurrentIter = null;
				}
				return theCurrentIter != null;
			}

			@Override
			public T next() {
				if (!calledHasNext && !hasNext())
					throw new java.util.NoSuchElementException();
				return theCurrentIter.next();
			}

			@Override
			public void remove() {
				if (!currentIterHasValue && theLastValueIter == null)
					throw new IllegalStateException("remove() must be called after next()");
				if (currentIterHasValue)
					theCurrentIter.remove();
				else
					theLastValueIter.remove();
			}
		}
		return new CompoundIterator();
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param iterable The iterable to wrap
	 * @return An immutable iterable that returns the same information as <code>iterable</code> but disallows modification
	 * @deprecated Use {@link #unmodifiable(Iterable)}
	 */
	@Deprecated
	public static <T> Betterable<T> immutableIterable(final Iterable<? extends T> iterable) {
		return unmodifiable(iterable);
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param iterable The iterable to wrap
	 * @return An unmodifiable iterable that returns the same information as <code>iterable</code> but disallows modification
	 */
	public static <T> Betterable<T> unmodifiable(final Iterable<? extends T> iterable) {
		if (iterable == null)
			throw new NullPointerException();
		return new ToStringIterable<>(() -> IterableUtils.unmodifiable(iterable.iterator()));
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param iterator The iterator to wrap
	 * @return An immutable iterator that returns the same information as <code>iterator</code> but disallows modification
	 * @deprecated Use {@link #unmodifiable(Iterator)}
	 */
	@Deprecated
	public static <T> Iterator<T> immutableIterator(final Iterator<? extends T> iterator) {
		return unmodifiable(iterator);
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param iterator The iterator to wrap
	 * @return An unmodifiable iterator that returns the same information as <code>iterator</code> but disallows modification
	 */
	public static <T> Iterator<T> unmodifiable(final Iterator<? extends T> iterator) {
		if (iterator == null)
			throw new NullPointerException();
		class UnmodifiableIterator implements Iterator<T> {
			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public T next() {
				return iterator.next();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		}
		return new UnmodifiableIterator();
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param iterator The list iterator to wrap
	 * @return An unmodifiable list iterator that returns the same information as <code>iterator</code> but disallows modification
	 */
	public static <T> ListIterator<T> unmodifiable(final ListIterator<? extends T> iterator) {
		if (iterator == null)
			throw new NullPointerException();
		class UnmodifiableListIterator implements ListIterator<T> {
			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public boolean hasPrevious() {
				return iterator.hasPrevious();
			}

			@Override
			public T next() {
				return iterator.next();
			}

			@Override
			public T previous() {
				return iterator.previous();
			}

			@Override
			public int nextIndex() {
				return iterator.nextIndex();
			}

			@Override
			public int previousIndex() {
				return iterator.previousIndex();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

			@Override
			public void set(T e) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void add(T e) {
				throw new UnsupportedOperationException();
			}
		}
		return new UnmodifiableListIterator();
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param iterator An iterator to cache the values of
	 * @return A iterable that returns a lazily-loaded cache so the iterables return once from the given iterator may be reused any number
	 *         of times
	 */
	public static <T> Betterable<T> cachingIterable(Iterator<T> iterator) {
		if (iterator == null)
			throw new NullPointerException();
		class CachingIterable implements Betterable<T> {
			private Iterator<T> backing;

			private final java.util.ArrayList<T> theCache = new java.util.ArrayList<>();

			private Object theLock = new Object();

			@Override
			public Iterator<T> iterator() {
				return new CachingIterator();
			}

			class CachingIterator implements Iterator<T> {
				private int theIndex;

				@Override
				public boolean hasNext() {
					Object lock = theLock;
					if (lock != null && theIndex == theCache.size()) {
						synchronized (lock) {
							if (backing == null)
								return false;
							if (theIndex == theCache.size()) {
								if (backing.hasNext())
									return true;
								else {
									theLock = null;
									backing = null;
									return false;
								}
							}
						}
					}
					return theIndex < theCache.size();
				}

				@Override
				public T next() {
					Object lock = theLock;
					if (lock != null && theIndex == theCache.size()) {
						synchronized (lock) {
							if (theIndex == theCache.size()) {
								T ret = backing.next();
								theCache.add(ret);
								theIndex++;
								return ret;
							}
						}
					}
					return theCache.get(theIndex++);
				}
			}
		}
		return new CachingIterable();
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param <V> The type of values to return from the wrapping iterator
	 * @param wrap The iterator to wrap
	 * @param accepter The accepter to discriminate which values to return
	 * @param removable Whether the returned iterator's {@link Iterator#remove()} method should be active
	 * @return The iterators
	 */
	public static <T, V> Iterator<V> conditionalIterator(final Iterator<T> wrap, final Accepter<? super T, ? extends V> accepter,
		final boolean removable) {
		if (wrap == null)
			throw new NullPointerException();
		class ConditionalIterator implements Iterator<V> {
			private V theNextReturn;

			private boolean calledHasNext;

			@Override
			public boolean hasNext() {
				calledHasNext = true;
				while (theNextReturn == null && wrap.hasNext())
					theNextReturn = accepter.accept(wrap.next());
				return theNextReturn != null;
			}

			@Override
			public V next() {
				if (!calledHasNext && !hasNext()) {
					wrap.next(); // Let the wrapped iterator throw the exception
					throw new java.util.NoSuchElementException();
				}
				V ret = theNextReturn;
				theNextReturn = null;
				return ret;
			}

			@Override
			public void remove() {
				if (removable)
					wrap.remove();
				else
					throw new UnsupportedOperationException();
			}
		}
		return new ConditionalIterator();
	}

	/**
	 * Performs a depth-first iteration of nodes in a hierarchy structure
	 *
	 * @param <T> The type of nodes to iterate over
	 * @param value The value at the root level
	 * @param childGetter Gets children of each node
	 * @param filter An optional filter that, if it fails for a value, will prevent the value's children (but not the value itself) from
	 *        being iterated through
	 * @return An iterable that can iterate depth-first through the hierarchy
	 */
	public static <T> Betterable<T> depthFirst(T value, Function<? super T, ? extends Iterable<? extends T>> childGetter,
		Predicate<? super T> filter) {
		return depthFirstMulti(Collections.singleton(value), childGetter, filter);
	}

	/**
	 * Performs a depth-first iteration of nodes in a hierarchy structure
	 *
	 * @param <T> The type of nodes to iterate over
	 * @param values The values at the top level to iterate through
	 * @param childGetter Gets children of each node
	 * @param filter An optional filter that, if it fails for a value, will prevent the value's children (but not the value itself) from
	 *        being iterated through
	 * @return An iterable that can iterate depth-first through the hierarchy
	 */
	public static <T> Betterable<T> depthFirstMulti(Iterable<? extends T> values,
		Function<? super T, ? extends Iterable<? extends T>> childGetter,
		Predicate<? super T> filter) {
		if (values == null)
			throw new NullPointerException();
		class StackLevel {
			final T value;
			final Iterator<? extends T> children;

			StackLevel(T value, Iterator<? extends T> children) {
				this.value = value;
				this.children = children;
			}
		}
		class DepthFirstIterator implements Iterator<T> {
			private final Iterator<? extends T> theRootIterator = values.iterator();
			private final DequeList<StackLevel> theStack = new CircularArrayList<>();

			@Override
			public boolean hasNext() {
				return !theStack.isEmpty() || theRootIterator.hasNext();
			}

			@Override
			public T next() {
				T next;
				if (theStack.isEmpty())
					next = theRootIterator.next();
				else if (theStack.getLast().children.hasNext())
					next = theStack.getLast().children.next();
				else
					return theStack.removeLast().value;
				Iterable<? extends T> children = childGetter.apply(next);
				Iterator<? extends T> childIter = children == null ? Collections.emptyIterator() : children.iterator();
				while (childIter.hasNext()) {
					theStack.add(new StackLevel(next, childIter));
					next = childIter.next();
					children = childGetter.apply(next);
					childIter = children == null ? Collections.emptyIterator() : children.iterator();
				}
				return next;
			}
		}
		return new ToStringIterable<>(DepthFirstIterator::new);
	}

	/**
	 * Performs a breadth-first iteration of nodes in a hierarchy structure
	 *
	 * @param <T> The type of nodes to iterate over
	 * @param value The value at the root level
	 * @param childGetter Gets children of each node
	 * @param filter An optional filter that, if it fails for a value, will prevent the value's children (but not the value itself) from
	 *        being iterated through
	 * @return An iterable that can iterate depth-first through the hierarchy
	 */
	public static <T> Betterable<T> breadthFirst(T value, Function<? super T, ? extends Iterable<? extends T>> childGetter,
		Predicate<? super T> filter) {
		return breadthFirstMulti(Collections.singleton(value), childGetter, filter);
	}

	/**
	 * Performs a breadth-first iteration of nodes in a hierarchy structure
	 *
	 * @param <T> The type of nodes to iterate over
	 * @param values The value at the top level to iterate through
	 * @param childGetter Gets children of each node
	 * @param filter An optional filter that, if it fails for a value, will prevent the value's children (but not the value itself) from
	 *        being iterated through
	 * @return An iterable that can iterate depth-first through the hierarchy
	 */
	public static <T> Betterable<T> breadthFirstMulti(Iterable<? extends T> values,
		Function<? super T, ? extends Iterable<? extends T>> childGetter, Predicate<? super T> filter) {
		if (values == null)
			throw new NullPointerException();
		class BreadthFirstIterator implements Iterator<T> {
			private Queue<T> queue;
			private Iterator<? extends T> currentIterator = values.iterator();
			private T currentValue;
			private boolean hasValue;

			@Override
			public boolean hasNext() {
				while (true) {
					if (currentIterator != null && currentIterator.hasNext())
						return true;
					if (hasValue) {
						if (filter == null || filter.test(currentValue)) {
							if (queue == null)
								queue = new ArrayDeque<>();
							queue.add(currentValue);
						}
						hasValue = false;
						currentValue = null;
					}
					if (queue == null || queue.isEmpty())
						return false;
					currentIterator = childGetter.apply(queue.poll()).iterator();
				}
			}

			@Override
			public T next() {
				if (!hasNext())
					throw new NoSuchElementException();
				currentValue = currentIterator.next();
				hasValue = true;
				return currentValue;
			}
		}
		return new ToStringIterable<>(BreadthFirstIterator::new);
	}

	/**
	 * @param <T> The type of the iterable to map
	 * @param <V> The type of the iterable to produce
	 * @param iterable The iterable to map the values of
	 * @param map The mapping function for iterable values
	 * @return An iterable whose values are those of the given iterable, mapped via the given function
	 */
	public static <T, V> Betterable<V> map(Iterable<T> iterable, Function<? super T, ? extends V> map) {
		if (iterable == null)
			throw new NullPointerException();
		return new ToStringIterable<>(() -> new MappedIterator<>(iterable.iterator(), map));
	}

	/**
	 * @param <T> The type of the iterator to map
	 * @param <V> The type of the iterator to produce
	 * @param iterator The iterator to map the values of
	 * @param map The mapping function for iterated values
	 * @return An iterator whose values are those of the given iterator, mapped via the given function
	 */
	public static <T, V> Iterator<V> map(Iterator<T> iterator, Function<? super T, ? extends V> map) {
		if (iterator == null)
			throw new NullPointerException();
		return new MappedIterator<>(iterator, map);
	}

	static class MappedIterator<T, V> implements Iterator<V> {
		private final Iterator<T> theBacking;
		private final Function<? super T, ? extends V> theMap;

		MappedIterator(Iterator<T> backing, Function<? super T, ? extends V> map) {
			theBacking = backing;
			theMap = map;
		}

		@Override
		public boolean hasNext() {
			return theBacking.hasNext();
		}

		@Override
		public V next() {
			return theMap.apply(theBacking.next());
		}

		@Override
		public void remove() {
			theBacking.remove();
		}
	}
	/**
	 * @param <T> The type of the collection to map
	 * @param <V> The type of the collection to produce
	 * @param collection The collection to map the values of
	 * @param map The mapping function for collection values
	 * @return A collection whose values are those of the given collection, mapped via the given function
	 */
	public static <T, V> Collection<V> map(Collection<T> collection, Function<? super T, ? extends V> map) {
		Betterable<V> iterable = IterableUtils.map((Iterable<T>) collection, map);
		class ConcatenatedCollection extends AbstractCollection<V> implements Betterable<V> {
			@Override
			public Iterator<V> iterator() {
				return iterable.iterator();
			}

			@Override
			public int size() {
				return collection.size();
			}
		}
		return new ConcatenatedCollection();
	}

	/**
	 * @param <T> The type of the iterable to filter
	 * @param iterable The iterable to filter
	 * @param filter The function to filter items from the iterable
	 * @return The filtered iterable
	 */
	public static <T> Betterable<T> filter(Iterable<T> iterable, Predicate<? super T> filter) {
		if (iterable == null)
			throw new NullPointerException();
		class FilteredIterator implements Iterator<T> {
			private final Iterator<T> backing = iterable.iterator();
			private T theNext;
			boolean hasNext;

			@Override
			public boolean hasNext() {
				while (!hasNext && backing.hasNext()) {
					theNext = backing.next();
					hasNext = filter.test(theNext);
				}

				return hasNext;
			}

			@Override
			public T next() {
				if (!hasNext && !hasNext())
					throw new NoSuchElementException();
				T next = theNext;
				theNext = null;
				hasNext = false;
				return next;
			}

			@Override
			public void remove() {
				backing.remove();
			}
		}
		return new ToStringIterable<>(FilteredIterator::new);
	}

	/**
	 * @param <T> The type of values to iterate over
	 * @param iterable The iterable to wrap
	 * @return An iterable that skips values in the source which have already been encountered
	 */
	public static <T> Betterable<T> distinct(Iterable<T> iterable) {
		class DistinctIterator implements Iterator<T> {
			private final Iterator<T> theIterator = iterable.iterator();
			private final Set<T> theDistinctValues = new HashSet<>();
			private T theNextValue;

			@Override
			public boolean hasNext() {
				while (theNextValue == null && theIterator.hasNext()) {
					theNextValue = theIterator.next();
					if (!theDistinctValues.add(theNextValue))
						theNextValue = null;
				}
				return theNextValue != null;
			}

			@Override
			public T next() {
				if (hasNext()) {
					T value = theNextValue;
					theNextValue = null;
					return value;
				}
				throw new NoSuchElementException();
			}
		}
		return new ToStringIterable<>(DistinctIterator::new);
	}

	/**
	 * Used by {@link IterableUtils#compare(Iterable, Iterable, SortedAdjuster)}
	 * 
	 * @param <T> The type of the first collection
	 * @param <X> The type of the second collection
	 */
	public interface SortedAdjuster<T, X> {
		/**
		 * Compares objects from the 2 collections
		 * 
		 * @param v1 The object from the first collection
		 * @param v2 The object from the second collection
		 * @return Like {@link Comparator#compare(Object, Object)}, but for heterogeneous types
		 */
		int compare(T v1, X v2);

		/**
		 * @param newValue The value from the second collection that was not present in the first
		 * @param after The value in the first collection that the new value should be placed after (or null if the new value should be
		 *        first object in the first collection)
		 * @param before The value in the first collection that the new value should be placed before (or null if the new value should be
		 *        last object in the first collection)
		 */
		void added(X newValue, T after, T before);

		/**
		 * @param oldValue The value from the first collection that is not present in the second
		 * @param after The value in the second collection that the value should be placed after (or null if the value should be first
		 *        object in the second collection)
		 * @param before The value in the second collection that the value should be placed before (or null if the value should be last
		 *        object in the second collection)
		 * @return Whether to remove the value from the second collection (via {@link Iterator#remove()}}
		 */
		boolean removed(T oldValue, X after, X before);

		/**
		 * Called when a value is found in both collections (i.e. when {@link #compare(Object, Object)} returns 0)
		 * 
		 * @param v1 The value in the first collection
		 * @param v2 The value in the second collection
		 */
		void found(T v1, X v2);
	}

	/**
	 * @param <T> The type of values to iterate
	 * @param elements The iterables for each element of the path
	 * @return An iterable for all paths. I.e. for each value output by the first iterator, the second iterator will be created. For each
	 *         element of that iterator, the third will be created, etc. Each path thus produced will be returned by this iterable. This
	 *         method reuses the list instance returned from the path iterator to save memory, so the consumer of the values must process or
	 *         copy the values it receives.
	 */
	public static <T> Iterable<List<T>> combine(Iterable<? extends Iterable<? extends T>> elements) {
		if (elements == null)
			throw new NullPointerException();
		ExIterable<? extends ExIterable<? extends T, RuntimeException>, RuntimeException> exArg = ExIterable.fromIterable(elements)
			.map(elIter -> ExIterable.fromIterable(elIter));
		ExIterable<List<T>, RuntimeException> exRes = ExIterable.combine(exArg);
		return exRes.unsafe();
	}

	/**
	 * Compares two related sorted sequences
	 * 
	 * @param <T> The type of the first sequence
	 * @param <X> The type of the second sequence
	 * @param v1 The first sequence
	 * @param v2 The second sequence
	 * @param adjuster The adjuster to compare the iterator values and act upon differences
	 */
	public static <T, X> void compare(Iterable<? extends T> v1, Iterable<? extends X> v2, SortedAdjuster<T, X> adjuster) {
		compare(v1.iterator(), v2.iterator(), adjuster);
	}

	/**
	 * Compares two related sorted sequences
	 * 
	 * @param <T> The type of the first iterator
	 * @param <X> The type of the second iterator
	 * @param iter1 The first iterator
	 * @param iter2 The second iterator
	 * @param adjuster The adjuster to compare the iterator values and act upon differences
	 */
	public static <T, X> void compare(Iterator<? extends T> iter1, Iterator<? extends X> iter2, SortedAdjuster<T, X> adjuster) {
		class LookAheadIterator<V> {
			final Iterator<? extends V> iterator;
			V previous;
			V current;

			boolean hasCurrent;

			LookAheadIterator(Iterator<? extends V> iter) {
				iterator = iter;
				hasCurrent = iter.hasNext();
				if (hasCurrent) {
					current = iter.next();
				}
			}

			void proceed() {
				previous = current;
				hasCurrent = hasCurrent && iterator.hasNext();
				current = hasCurrent ? iterator.next() : null;
			}
		}

		LookAheadIterator<T> laIter1 = new LookAheadIterator<>(iter1);
		LookAheadIterator<X> laIter2 = new LookAheadIterator<>(iter2);
		while (laIter1.hasCurrent && laIter2.hasCurrent) {
			int comp = adjuster.compare(laIter1.current, laIter2.current);
			if (comp < 0) {
				// v1 was not found in iter2
				if (adjuster.removed(laIter1.current, laIter2.previous, laIter2.current))
					laIter1.iterator.remove();

				laIter1.proceed();
			} else if (comp > 0) {
				// v2 was not found in iter1
				adjuster.added(laIter2.current, laIter1.previous, laIter1.current);

				laIter2.proceed();
			} else {
				adjuster.found(laIter1.current, laIter2.current);

				laIter1.proceed();
				laIter2.proceed();
			}
		}
		while (laIter1.hasCurrent) {
			// v1 was not found in iter2
			if (adjuster.removed(laIter1.current, laIter2.previous, laIter2.current))
				laIter1.iterator.remove();

			laIter1.proceed();
		}
		while (laIter2.hasCurrent) {
			// v2 was not found in iter1
			adjuster.added(laIter2.current, laIter1.previous, null);

			laIter2.proceed();
		}
	}

	static class ToStringIterable<T> implements Betterable<T> {
		private final Supplier<Iterator<T>> theIterator;

		public ToStringIterable(Supplier<Iterator<T>> iterator) {
			theIterator = iterator;
		}

		@Override
		public Iterator<T> iterator() {
			return theIterator.get();
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder("[");
			boolean first = true;
			for (T value : this) {
				if (first)
					first = false;
				else
					str.append(", ");
				str.append(value);
			}
			return str.append(']').toString();
		}
	}

	/**
	 * @param <T> The type of the value to iterate over
	 * @param initial The first value in the sequence
	 * @param op The operator to generate subsequent values
	 * @param count The number of values for the sequence
	 * @return The iterable sequence
	 */
	public static <T> Betterable<T> createCount(T initial, UnaryOperator<T> op, int count) {
		class OperatorIterator implements Iterator<T> {
			private T theNext;
			private int theReturned;

			@Override
			public boolean hasNext() {
				return theReturned < count;
			}

			@Override
			public T next() {
				if (!hasNext())
					throw new NoSuchElementException();
				theReturned++;
				T value = theNext;
				theNext = op.apply(value);
				return value;
			}
		}
		return new ToStringIterable<>(OperatorIterator::new);
	}

	/**
	 * @param <T> The type of the comparable value to iterate over
	 * @param initial The first value in the sequence
	 * @param op The operator to generate subsequent values
	 * @param until The value to stop before
	 * @return The iterable sequence
	 */
	public static <T extends Comparable<T>> Betterable<T> createUntil(T initial, UnaryOperator<T> op, T until) {
		class UntilIterator implements Iterator<T> {
			private T theNext;

			@Override
			public boolean hasNext() {
				return theNext.compareTo(until) < 0;
			}

			@Override
			public T next() {
				if (!hasNext())
					throw new NoSuchElementException();
				T value = theNext;
				theNext = op.apply(value);
				return value;
			}
		}
		return new ToStringIterable<>(UntilIterator::new);
	}

	/**
	 * @param <T> The type of values to permutate
	 * @param source The values to permutate
	 * @return A {@link PermutationIterable} with {@link PermutationIterable#getMinSize() minSize}=1,
	 *         {@link PermutationIterable#getMaxSize() maxSize}={@link Integer#MAX_VALUE}, and {@link PermutationIterable#isDistinct()
	 *         distinct}=false
	 */
	public static <T> PermutationIterable<T> fullPermutation(Iterable<T> source) {
		return new PermutationIterable<>(source);
	}

	static class IndexCollection extends AbstractList<Integer> implements BetterableList<Integer> {
		private final int theStart;
		private final int theEnd;
		private final boolean isIncrement;

		public IndexCollection(int start, int end) {
			theStart = start;
			theEnd = end;
			isIncrement = start <= end;
		}

		@Override
		public Integer get(int index) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			if (isIncrement) {
				int v = theStart + index;
				if (v < theEnd)
					return v;
				else
					throw new IndexOutOfBoundsException(index + " of " + size());
			} else {
				int v = theStart - index;
				if (v > theEnd)
					return v;
				else
					throw new IndexOutOfBoundsException(index + " of " + size());
			}
		}

		@Override
		public int size() {
			return Math.abs(theEnd - theStart);
		}

		@Override
		public Iterator<Integer> iterator() {
			return new IndexIterator(theStart, theEnd);
		}

		@Override
		public boolean isEmpty() {
			return theStart == theEnd;
		}

		@Override
		public boolean contains(Object o) {
			if (!(o instanceof Integer))
				return false;
			int v = ((Integer) o).intValue();
			if (isIncrement) {
				return v >= theStart && v < theEnd;
			} else
				return v <= theStart && v > theEnd;
		}
	}

	static class IndexIterator implements Iterator<Integer> {
		private final int theEnd;
		private final boolean isIncrement;
		private int theNextValue;

		IndexIterator(int start, int end) {
			theEnd = end;
			isIncrement = end >= start;
			theNextValue = start;
		}

		@Override
		public boolean hasNext() {
			if (isIncrement)
				return theNextValue < theEnd;
			else
				return theNextValue > theEnd;
		}

		@Override
		public Integer next() {
			if (!hasNext())
				throw new NoSuchElementException("Index iteration reached " + theEnd);
			int value = theNextValue;
			if (isIncrement)
				theNextValue++;
			else
				theNextValue--;
			return value;
		}
	}

	/**
	 * <p>
	 * An iterable of all possible permutations of the given source values with the given constraints.
	 * </p>
	 * <p>
	 * E.g. If {0, 1, 2, 3} is given for the source with {@link #setMinSize(int) minSize}=1, {@link #setMaxSize(int) max
	 * size}={@link Integer#MAX_VALUE}, {@link #setDistinct(boolean, boolean) distinct/ascending}=true/true, the result will be an iterator
	 * that iterates over:
	 * <ol>
	 * <li>[0]</li>
	 * <li>[1]</li>
	 * <li>[2]</li>
	 * <li>[3]</li>
	 * <li>[0,1]</li>
	 * <li>[0,2]</li>
	 * <li>[0,3]</li>
	 * <li>[1,2]</li>
	 * <li>[1,3]</li>
	 * <li>[2,3]</li>
	 * <li>[0,1,2]</li>
	 * <li>[0,1,3]</li>
	 * <li>[0,2,3]</li>
	 * <li>[0,1,2,3]</li>
	 * </ol>
	 * </p>
	 * <p>
	 * For efficiency, the list returned from {@link Iterator#next()} will be the same instance each time--an unmodifiable wrapper around a
	 * list that is updated internally for each iteration.
	 * </p>
	 * 
	 * @param <T> The type of values to permutate
	 */
	public static class PermutationIterable<T> implements Betterable<BetterList<T>> {
		private final Iterable<T> theSource;
		private int theMinSize;
		private int theMaxSize;
		private boolean isDistinct;
		private boolean isAscending;

		PermutationIterable(Iterable<T> source) {
			theSource = source;
			theMinSize = 1;
			theMaxSize = Integer.MAX_VALUE;
		}

		/** @return The minimum size of the lists to iterate over */
		public int getMinSize() {
			return theMinSize;
		}

		/**
		 * @param minSize The minimum number of values in each permutation
		 * @return This iterable
		 */
		public PermutationIterable<T> setMinSize(int minSize) {
			if (minSize < 0)
				throw new IllegalArgumentException("Min size must not be negative");
			else if (minSize > theMaxSize)
				theMaxSize = minSize;
			theMinSize = minSize;
			return this;
		}

		/** @return The maximum number of values in each permutation */
		public int getMaxSize() {
			return theMaxSize;
		}

		/**
		 * @param maxSize The maximum number of values in each permutation
		 * @return This iterable
		 */
		public PermutationIterable<T> setMaxSize(int maxSize) {
			if (maxSize < 0)
				throw new IllegalArgumentException("Max size must not be negative");
			else if (maxSize < theMinSize)
				theMinSize = maxSize;
			theMaxSize = maxSize;
			return this;
		}

		/**
		 * Sets {@link #getMinSize() minSize} and {@link #getMaxSize() maxSize} at once
		 * 
		 * @param size The number of values in each permutation
		 * @return This iterable
		 */
		public PermutationIterable<T> setSize(int size) {
			if (size < 0)
				throw new IllegalArgumentException("Size must not be negative");
			theMinSize = theMaxSize = size;
			return this;
		}

		/**
		 * @return Whether this iterable only uses distinct values from its source iterable. This distinct-ness is not based on
		 *         {@link Object#equals(Object)} or identity, but rather on the assumption that the source iterable provides distinct values
		 *         in a consistent order.
		 */
		public boolean isDistinct() {
			return isDistinct;
		}

		/**
		 * @param distinct Whether to provide only combinations with {@link #isDistinct() distinct} values
		 * @param strictAscending If both <code>distinct</code> and <code>strictAscending</code> are true, this iterable will only return
		 *        permutations of the source whose elements are in ascending order, according to the iteration order of the source. So,
		 *        e.g., if the source is [0, 1, 2], a distinct ascending iterator would not return a combination of [0, 2, 1].
		 * @return This iterable
		 */
		public PermutationIterable<T> setDistinct(boolean distinct, boolean strictAscending) {
			this.isDistinct = distinct;
			isAscending = strictAscending;
			return this;
		}

		@Override
		public Iterator<BetterList<T>> iterator() {
			return new PermutationIterator<>(theSource, theMinSize, theMaxSize, isDistinct, isAscending);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			boolean first = true;
			for (List<T> value : this) {
				if (first)
					first = false;
				else
					str.append('\n');
				str.append(value);
			}
			return str.toString();
		}

		static class PermutationIterator<T> implements Iterator<BetterList<T>> {
			private final int myMinSize;
			private final int myMaxSize;

			private final Iterable<T> theSource;
			private final CircularArrayList<Iterator<T>> theIterators = new CircularArrayList<>();
			private int theCurrentDim;
			private final CircularArrayList<T> theValues;
			private final BetterList<T> theExposedValues;
			private final BetterBitSet theUsedValues;
			private final boolean isAscending;
			private boolean knownHasNext;

			PermutationIterator(Iterable<T> source, int minSize, int maxSize, boolean distinct, boolean ascending) {
				theSource = source;
				myMinSize = minSize;
				myMaxSize = maxSize;

				theValues = CircularArrayList.build().build();
				theExposedValues = new BetterList.ConstantList<T>(theValues) {
					@Override
					protected Object createIdentity() {
						return Identifiable.wrap(theSource, "permutate", myMinSize, myMaxSize, theUsedValues != null);
					}
				};
				theUsedValues = distinct ? new BetterBitSet() : null;
				isAscending = ascending;
				knownHasNext = myMinSize == 0;
			}

			private Iterator<T> createIterator() {
				Iterator<T> source = theSource.iterator();
				if (theUsedValues != null)
					return new DistinctIterator<>(source, theUsedValues, isAscending);
				else
					return source;
			}

			@Override
			public boolean hasNext() {
				if (knownHasNext)
					return true;
				if (theCurrentDim < myMinSize) {
					do {
						theCurrentDim++;
						Iterator<T> iter = createIterator();
						theIterators.add(iter);
						if (!iter.hasNext())
							return false;
						theValues.add(iter.next());
					} while (theCurrentDim < myMinSize);
					return (knownHasNext = true);
				}

				do {
					// Remove spent iterators
					while (!theIterators.isEmpty()) {
						if (theValues.size() == theIterators.size())
							theValues.removeLast();
						if (theIterators.getLast().hasNext()) {
							break;
						} else {
							theIterators.removeLast();
						}
					}

					if (theIterators.isEmpty()) {// No more permutations in the current dimension. Next dimension.
						if (theCurrentDim == myMaxSize)
							return false;
						theCurrentDim++;
					}

					// Fill out the iterators up to the current dimension
					while (theIterators.size() < theCurrentDim) {
						Iterator<T> iter = createIterator();
						theIterators.add(iter);
					}
					if (theIterators.size() == theCurrentDim)
						knownHasNext = fillValues();
				} while (!knownHasNext);
				return knownHasNext;
			}

			private boolean fillValues() {
				while (theValues.size() < theIterators.size()) {
					Iterator<T> iter = theIterators.get(theValues.size());
					if (!iter.hasNext())
						return false;
					theValues.add(iter.next());
				}
				return true;
			}

			@Override
			public BetterList<T> next() {
				if (!knownHasNext) {
					if (!hasNext())
						throw new NoSuchElementException();
				}
				knownHasNext = false;
				return theExposedValues;
			}
		}

		static class DistinctIterator<T> implements Iterator<T> {
			private final Iterator<T> iterator;
			private final BetterBitSet theUsedValues;
			private final boolean isAscending;
			private int index;
			private boolean knownHasNext;
			private boolean hasNext;

			DistinctIterator(Iterator<T> iterator, BetterBitSet usedValues, boolean ascending) {
				this.iterator = iterator;
				theUsedValues = usedValues;
				isAscending = ascending;
			}

			@Override
			public boolean hasNext() {
				if (!knownHasNext) {
					hasNext = checkHasNext();
					knownHasNext = true;
				}
				return hasNext;
			}

			private boolean checkHasNext() {
				theUsedValues.clear(index);
				while (iterator.hasNext()) {
					index++;
					boolean allowed;
					if (isAscending)
						allowed = theUsedValues.nextSetBit(index) < 0;
					else
						allowed = !theUsedValues.get(index);
					if (allowed)
						return true;
					else
						iterator.next(); // Illegal value. Skip it.
				}
				return false;
			}

			@Override
			public T next() {
				if (!hasNext())
					throw new NoSuchElementException();
				knownHasNext = false;
				theUsedValues.set(index);
				return iterator.next();
			}
		}
	}
}
