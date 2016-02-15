package org.qommons;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.Predicate;

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
	 * @param <T> The type of the values to iterate over
	 * @param array The array to iterate over
	 * @param forward Whether to iterate forward through the array or backward
	 * @return An iterable that returns an iterator to iterate over each element in the array
	 */
	public static <T> Iterable<T> iterable(final T [] array, final boolean forward) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return IterableUtils.iterator(array, forward);
			}
		};
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param array The array to iterate over
	 * @param forward Whether to iterate forward through the array or backward
	 * @return An iterator to iterate over each element in the array
	 */
	public static <T> Iterator<T> iterator(final T [] array, final boolean forward) {
		return new Iterator<T>() {
			private int theIndex;
	
			{
				theIndex = forward ? 0 : array.length - 1;
			}
	
			@Override
			public boolean hasNext() {
				return forward ? theIndex < array.length : theIndex >= 0;
			}
	
			@Override
			public T next() {
				T ret = array[theIndex];
				if(forward)
					theIndex++;
				else
					theIndex--;
				return ret;
			}
	
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param compound The iterables to compound in a single iterable
	 * @return An Iterable that iterates through all elements in the given iterables
	 */
	public static <T> Iterable<T> iterable(final Iterable<? extends T>... compound) {
		return IterableUtils.flatten(Arrays.asList(compound));
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param compound The iterables to compound in a single iterable
	 * @return An Iterable that iterates through all elements in the given iterables
	 */
	public static <T> Iterable<T> flatten(final Iterable<? extends Iterable<? extends T>> compound) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private final Iterator<? extends Iterable<? extends T>> theCompoundIter = compound.iterator();
					private Iterator<? extends T> theLastValueIter;
	
					private Iterator<? extends T> theCurrentIter;
	
					private boolean calledHasNext;
					private boolean currentIterHasValue;
	
					@Override
					public boolean hasNext() {
						calledHasNext = true;
						if (currentIterHasValue)
							theLastValueIter = theCurrentIter;
	
						currentIterHasValue = theCurrentIter != null && theCurrentIter.hasNext();
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
				};
			}
		};
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param compound The iterators to compound in a single iterator
	 * @return An iterator that iterates through all elements in the given iterators
	 */
	public static <T> Iterator<T> iterator(final Iterator<? extends T>... compound) {
		return new Iterator<T>() {
			private Iterator<? extends T> theLastValueIter;
	
			private Iterator<? extends T> theCurrentIter;
	
			private int theNextIndex;
	
			private boolean calledHasNext;
	
			private boolean currentIterHasValue;
	
			@Override
			public boolean hasNext() {
				calledHasNext = true;
				if(theCurrentIter != null && !theCurrentIter.hasNext()) {
					if(currentIterHasValue)
						theLastValueIter = theCurrentIter;
					theCurrentIter = null;
				}
				while(theCurrentIter == null && theNextIndex < compound.length) {
					currentIterHasValue = false;
					theCurrentIter = compound[theNextIndex++];
					if(!theCurrentIter.hasNext())
						theCurrentIter = null;
				}
				return theCurrentIter != null;
			}
	
			@Override
			public T next() {
				if(!calledHasNext && !hasNext())
					throw new java.util.NoSuchElementException();
				return theCurrentIter.next();
			}
	
			@Override
			public void remove() {
				if(!currentIterHasValue && theLastValueIter == null)
					throw new IllegalStateException("remove() must be called after next()");
				if(currentIterHasValue)
					theCurrentIter.remove();
				else
					theLastValueIter.remove();
			}
		};
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param iterable The iterable to wrap
	 * @return An immutable iterable that returns the same information as <code>iterable</code> but disallows modification
	 */
	public static <T> Iterable<T> immutableIterable(final Iterable<T> iterable) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return IterableUtils.immutableIterator(iterable.iterator());
			}
		};
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param iterator The iterator to wrap
	 * @return An immutable iterator that returns the same information as <code>iterator</code> but disallows modification
	 */
	public static <T> Iterator<T> immutableIterator(final Iterator<T> iterator) {
		return new Iterator<T>() {
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
		};
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param iterator An iterator to cache the values of
	 * @return A iterable that returns a lazily-loaded cache so the iterables returne once from the given iterator may be reused any number
	 *         of times
	 */
	public static <T> Iterable<T> cachingIterable(Iterator<T> iterator) {
		final Iterator<T> [] backing = new Iterator[] {iterator};
		return new Iterable<T>() {
			private final java.util.ArrayList<T> theCache = new java.util.ArrayList<>();
	
			private Object theLock = new Object();
	
			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private int theIndex;
	
					@Override
					public boolean hasNext() {
						Object lock = theLock;
						if(lock != null && theIndex == theCache.size()) {
							synchronized(lock) {
								if(theIndex == theCache.size()) {
									if(backing[0].hasNext())
										return true;
									else {
										theLock = null;
										backing[0] = null;
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
						if(lock != null && theIndex == theCache.size()) {
							synchronized(lock) {
								if(theIndex == theCache.size()) {
									T ret = backing[0].next();
									theCache.add(ret);
									theIndex++;
									return ret;
								}
							}
						}
						return theCache.get(theIndex++);
					}
	
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
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
		return new Iterator<V>() {
			private V theNextReturn;
	
			private boolean calledHasNext;
	
			@Override
			public boolean hasNext() {
				calledHasNext = true;
				while(theNextReturn == null && wrap.hasNext())
					theNextReturn = accepter.accept(wrap.next());
				return theNextReturn != null;
			}
	
			@Override
			public V next() {
				if(!calledHasNext && !hasNext()) {
					wrap.next(); // Let the wrapped iterator throw the exception
					throw new java.util.NoSuchElementException();
				}
				V ret = theNextReturn;
				theNextReturn = null;
				return ret;
			}
	
			@Override
			public void remove() {
				if(removable)
					wrap.remove();
				else
					throw new UnsupportedOperationException();
			}
		};
	}

	/**
	 * Performs a depth-first iteration of nodes in a hierarchy structure
	 *
	 * @param <T> The type of nodes to iterate over
	 * @param value The value at the root level
	 * @param childGetter Gets children of each node
	 * @param filter An optional filter that, if it fails for a value, will prevent the value's children (but not the value itself) from
	 *            being iterated through
	 * @return An iterable that can iterate depth-first through the hierarchy
	 */
	public static <T> Iterable<T> depthFirst(T value, Function<? super T, Iterable<T>> childGetter, Predicate<? super T> filter) {
		return IterableUtils.depthFirstMulti(java.util.Arrays.asList(value), childGetter, filter);
	}

	/**
	 * Performs a depth-first iteration of nodes in a hierarchy structure
	 *
	 * @param <T> The type of nodes to iterate over
	 * @param values The values at the top level to iterate through
	 * @param childGetter Gets children of each node
	 * @param filter An optional filter that, if it fails for a value, will prevent the value's children (but not the value itself) from
	 *            being iterated through
	 * @return An iterable that can iterate depth-first through the hierarchy
	 */
	public static <T> Iterable<T> depthFirstMulti(Iterable<T> values, Function<? super T, Iterable<T>> childGetter,
		Predicate<? super T> filter) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private Iterator<T> theTopLevel = values.iterator();
	
					private Iterator<T> theChildren;
	
					@Override
					public boolean hasNext() {
						if(theChildren != null) {
							if(theChildren.hasNext())
								return true;
							theChildren = null;
						}
						return theTopLevel.hasNext();
					}
	
					@Override
					public T next() {
						if(theChildren != null && theChildren.hasNext())
							return theChildren.next();
						T ret = theTopLevel.next();
						if(filter == null || filter.test(ret)) {
							Iterable<T> childIter = () -> childGetter.apply(ret).iterator();
							theChildren = depthFirstMulti(childIter, childGetter, filter).iterator();
						}
						return ret;
					}
				};
			}
		};
	}

	/**
	 * Performs a breadth-first iteration of nodes in a hierarchy structure
	 *
	 * @param <T> The type of nodes to iterate over
	 * @param value The value at the root level
	 * @param childGetter Gets children of each node
	 * @param filter An optional filter that, if it fails for a value, will prevent the value's children (but not the value itself) from
	 *            being iterated through
	 * @return An iterable that can iterate depth-first through the hierarchy
	 */
	public static <T> Iterable<T> breadthFirst(T value, Function<? super T, Iterable<T>> childGetter, Predicate<? super T> filter) {
		return IterableUtils.breadthFirstMulti(java.util.Arrays.asList(value), childGetter, filter);
	}

	/**
	 * Performs a breadth-first iteration of nodes in a hierarchy structure
	 *
	 * @param <T> The type of nodes to iterate over
	 * @param values The value at the top level to iterate through
	 * @param childGetter Gets children of each node
	 * @param filter An optional filter that, if it fails for a value, will prevent the value's children (but not the value itself) from
	 *            being iterated through
	 * @return An iterable that can iterate depth-first through the hierarchy
	 */
	public static <T> Iterable<T> breadthFirstMulti(Iterable<T> values, Function<? super T, Iterable<T>> childGetter,
		Predicate<? super T> filter) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private Queue<T> queue = new ArrayDeque<>();
	
					{
						for(T value : values)
							queue.add(value);
					}
	
					@Override
					public boolean hasNext() {
						return !queue.isEmpty();
					}
	
					@Override
					public T next() {
						T ret = queue.poll();
						if(filter == null || filter.test(ret))
							for(T child : childGetter.apply(ret))
								queue.add(child);
						return ret;
					}
				};
			}
		};
	}

	/**
	 * @param <T> The type of the iterable to map
	 * @param <V> The type of the iterable to produce
	 * @param iterable The iterable to map the values of
	 * @param map The mapping function for iterable values
	 * @return An iterable whose values are those of the given iterable, mapped via the given function
	 */
	public static <T, V> Iterable<V> map(Iterable<T> iterable, Function<? super T, V> map) {
		return () -> new Iterator<V>() {
			private final Iterator<T> backing = iterable.iterator();

			@Override
			public boolean hasNext() {
				return backing.hasNext();
			}

			@Override
			public V next() {
				return map.apply(backing.next());
			}

			@Override
			public void remove() {
				backing.remove();
			}
		};
	}
}
