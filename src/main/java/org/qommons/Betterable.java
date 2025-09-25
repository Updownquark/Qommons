package org.qommons;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.qommons.collect.BetterList;

/**
 * An extension of {@link Iterable} that supports several simple chained operations
 * 
 * @param <T> The type of values iterated over
 */
public interface Betterable<T> extends Iterable<T> {
	/**
	 * @param <V> The type of the iterable to produce
	 * @param map The mapping function for iterable values
	 * @return An iterable whose values are those of this iterable, mapped via the given function
	 */
	default <V> Betterable<V> map(Function<? super T, ? extends V> map) {
		return IterableUtils.<T, V> map(this, map);
	}

	/**
	 * @param filter The function to filter items from this iterable
	 * @return The filtered iterable
	 */
	default Betterable<T> filter(Predicate<? super T> filter) {
		return IterableUtils.filter(this, filter);
	}

	/**
	 * @param <T2> The type of the values to iterate over
	 * @param flatten A function that produces iterables from each value in this iterable
	 * @return An Iterable that iterates through all elements in the given iterables, each mapped from a value in this iterable
	 */
	default <T2> Betterable<T2> flatten(Function<? super T, ? extends Iterable<? extends T2>> flatten) {
		return IterableUtils.flatten(map(flatten));
	}

	/** @return An iterable that skips values in this iterable which have already been encountered */
	default Betterable<T> distinct() {
		return distinct(HashSet::new);
	}

	/**
	 * @param distinct Produces a set for each iterator to keep track of encountered values
	 * @return An iterable that skips values in this iterable which have already been encountered
	 */
	default Betterable<T> distinct(Supplier<? extends Set<? super T>> distinct) {
		class DistinctIterator implements Iterator<T> {
			private final Iterator<T> iterator;
			private final Set<? super T> encountered;
			private boolean hasNext;
			private T theNext;

			DistinctIterator(Set<? super T> encountered) {
				iterator = iterator();
				this.encountered = encountered;
			}

			@Override
			public boolean hasNext() {
				while (!hasNext && iterator.hasNext()) {
					T next = iterator.next();
					if (encountered.add(next)) {
						theNext = next;
						hasNext = true;
					}
				}
				return hasNext;
			}

			@Override
			public T next() {
				if (!hasNext())
					throw new NoSuchElementException();
				hasNext = false;
				return theNext;
			}

			@Override
			public void remove() {
				iterator.remove();
				encountered.remove(theNext);
			}
		}
		return new IterableUtils.ToStringIterable<>(() -> new DistinctIterator(distinct.get()));
	}

	/** @return A materialized snapshot copy of this iterable's data as a {@link BetterList} */
	default BetterList<T> cache() {
		ArrayList<T> list;
		if (this instanceof Collection)
			list = new ArrayList<>(((Collection<T>) this).size());
		else
			list = new ArrayList<>();
		for (T value : this)
			list.add(value);
		return BetterList.of(list);
	}

	/** @return An unmodifiable iterable that returns the same information as <code>this</code> but disallows modification */
	default Betterable<T> unmodifiable() {
		return IterableUtils.unmodifiable(this);
	}

	/**
	 * @param test The finality test
	 * @return A Betterable that returns the same values as this one until (and including) the first value that passes the given test
	 */
	default Betterable<T> takeUntil(Predicate<? super T> test) {
		class TakeUntilIterator implements Iterator<T> {
			private final Iterator<T> iterator = iterator();
			private boolean isDone;

			@Override
			public boolean hasNext() {
				return !isDone && iterator.hasNext();
			}

			@Override
			public T next() {
				if (!hasNext())
					throw new NoSuchElementException();
				T next = iterator.next();
				isDone = test.test(next);
				return next;
			}

			@Override
			public void remove() {
				iterator.remove();
			}
		}
		return new IterableUtils.ToStringIterable<>(TakeUntilIterator::new);
	}

	/**
	 * @param <T> The type of the values to iterate over
	 * @param components The iterables to concatenate in a single iterable
	 * @return An Iterable that iterates through all elements in the given iterables
	 */
	static <T> Betterable<T> concat(Iterable<? extends T>... components) {
		return IterableUtils.concat(components);
	}

	/**
	 * @param start The index to start at (inclusive)
	 * @param end The index to end at (exclusive)
	 * @return An iterable that iterates through integers starting at the given start and incrementing or decrementing until the given end
	 */
	static Betterable<Integer> index(int start, int end) {
		return IterableUtils.indexList(start, end);
	}

	/**
	 * @param <T> The type of the value to iterate over
	 * @param value The supplier for the single value of the iterator
	 * @return An iterable that supplies iterators that return a single value, supplied by the given suppliers
	 */
	static <T> Betterable<T> single(Supplier<T> value) {
		return IterableUtils.single(value);
	}

	/**
	 * @param <T> The type of values to iterate over
	 * @param array The values to iterate over
	 * @param forward Whether to iterate forward or backward in the array
	 * @return The iterable over the given values
	 */
	static <T> Betterable<T> iterable(T[] array, boolean forward) {
		return IterableUtils.iterable(array, forward);
	}

	/**
	 * @param <T> The type of values to iterate over
	 * @param initial The first value in the sequence
	 * @param op The operator to produce further values in the sequence by operating on the previous returned value
	 * @param count The number of values for the sequence
	 * @return The iterable sequence of values
	 */
	static <T> Betterable<T> operate(T initial, UnaryOperator<T> op, int count) {
		return IterableUtils.createCount(initial, op, count);
	}
}
