package org.qommons.ex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Like {@link java.util.Iterator}, but returns an ExIterator, an {@link java.util.Iterator} that can throw checked exceptions
 *
 * @param <T> The type of values returned by the iterator
 * @param <E> The type of exception the iterator can throw
 */
public interface ExIterable<T, E extends Throwable> {
	/** @return An iterator over this sequence's values */
	ExIterator<T, E> iterator();

	/**
	 * @return A normal {@link Iterable} that iterates over the same sequence as this ExIterable and whose iterator throws
	 *         {@link CheckedExceptionWrapper} instances if the underlying ExIterator throws an exception
	 */
	default Iterable<T> unsafe() {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private ExIterator<T, E> backing = ExIterable.this.iterator();

					@Override
					public boolean hasNext() {
						try {
							return backing.hasNext();
						} catch(Throwable ex) {
							throw new CheckedExceptionWrapper(ex);
						}
					}

					@Override
					public T next() {
						try {
							return backing.next();
						} catch(Throwable ex) {
							throw new CheckedExceptionWrapper(ex);
						}
					}

					@Override
					public void remove() {
						backing.remove();
					}
				};
			}
		};
	}

	default <V> ExIterable<V, E> map(Function<? super T, V> map) {
		return mapEx(ExFunction.of(map));
	}

	default <V> ExIterable<V, E> mapEx(ExFunction<? super T, V, ? extends E> map) {
		ExIterable<T, E> outer = this;
		return new ExIterable<V, E>() {
			@Override
			public ExIterator<V, E> iterator() {
				return new ExIterator<V, E>() {
					private final ExIterator<T, E> backing = outer.iterator();

					@Override
					public boolean hasNext() throws E {
						return backing.hasNext();
					}

					@Override
					public V next() throws E {
						return map.apply(backing.next());
					}

					@Override
					public void remove() {
						backing.remove();
					}
				};
			}
		};
	}

	default ExIterable<T, E> filter(ExPredicate<? super T, ? extends E> filter) {
		ExIterable<T, E> outer = this;
		return new ExIterable<T, E>() {
			@Override
			public ExIterator<T, E> iterator() {
				return new ExIterator<T, E>() {
					private final ExIterator<T, E> backing = outer.iterator();
					private boolean found;
					private T next;

					@Override
					public boolean hasNext() throws E {
						while (backing.hasNext() && !found) {
							next = backing.next();
							if (filter.test(next))
								found = true;
							else
								next = null;
						}
						return found;
					}

					@Override
					public T next() throws E {
						if (!found && !hasNext())
							throw new java.util.NoSuchElementException();
						found = false;
						return next;
					}

					@Override
					public void remove() {
						backing.remove();
					}
				};
			}
		};
	}

	default ExIterable<T, E> append(ExIterable<? extends T, ? extends E>... others) {
		ArrayList<ExIterable<? extends T, ? extends E>> list = new ArrayList<>();
		list.add(this);
		for (ExIterable<? extends T, ? extends E> other : others)
			list.add(other);

		ExIterable<ExIterable<? extends T, ? extends E>, ? extends E> iterable;
		iterable = (ExIterable<ExIterable<? extends T, ? extends E>, ? extends E>) fromIterable(
			list);
		return flatten(iterable);
	}

	default ExIterable<T, E> beforeEach(Runnable action) {
		ExIterable<T, E> outer = this;
		return new ExIterable<T, E>() {
			@Override
			public ExIterator<T, E> iterator() {
				return new ExIterator<T, E>() {
					private final ExIterator<T, E> outerIter = outer.iterator();

					@Override
					public boolean hasNext() throws E {
						return outerIter.hasNext();
					}

					@Override
					public T next() throws E {
						action.run();
						return outerIter.next();
					}

					@Override
					public void remove() {
						outerIter.remove();
					}
				};
			}
		};
	}

	default ExIterable<T, E> onEach(Consumer<? super T> onValue) {
		ExIterable<T, E> outer = this;
		return new ExIterable<T, E>() {
			@Override
			public ExIterator<T, E> iterator() {
				return new ExIterator<T, E>() {
					private final ExIterator<T, E> outerIter = outer.iterator();

					@Override
					public boolean hasNext() throws E {
						return outerIter.hasNext();
					}

					@Override
					public T next() throws E {
						T value = outerIter.next();
						onValue.accept(value);
						return value;
					}

					@Override
					public void remove() {
						outerIter.remove();
					}
				};
			}
		};
	}

	default ExIterable<T, E> onStart(Runnable action){
		ExIterable<T, E> outer = this;
		return new ExIterable<T, E>() {
			@Override
			public ExIterator<T, E> iterator() {
				return new ExIterator<T, E>() {
					private final ExIterator<T, E> outerIter = outer.iterator();
					private boolean hasStarted = false;

					@Override
					public boolean hasNext() throws E {
						if(!hasStarted) {
							hasStarted=true;
							action.run();
						}
						return outerIter.hasNext();
					}

					@Override
					public T next() throws E {
						return outerIter.next();
					}

					@Override
					public void remove() {
						outerIter.remove();
					}
				};
			}
		};
	}

	default ExIterable<T, E> onFinish(Runnable action) {
		ExIterable<T, E> outer = this;
		return new ExIterable<T, E>() {
			@Override
			public ExIterator<T, E> iterator() {
				return new ExIterator<T, E>() {
					private final ExIterator<T, E> outerIter = outer.iterator();
					private boolean hasFinished = false;

					@Override
					public boolean hasNext() throws E {
						if (hasFinished)
							return false;
						else if (outerIter.hasNext())
							return true;
						else {
							hasFinished = true;
							action.run();
							return false;
						}
					}

					@Override
					public T next() throws E {
						return outerIter.next();
					}

					@Override
					public void remove() {
						outerIter.remove();
					}
				};
			}
		};
	}

	static <T, E extends Throwable> ExIterable<T, E> from(T... values) {
		return new ExIterable<T, E>() {
			@Override
			public ExIterator<T, E> iterator() {
				return new ExIterator<T, E>() {
					private int theIndex = 0;

					@Override
					public boolean hasNext() {
						return theIndex < values.length;
					}

					@Override
					public T next() {
						if(!hasNext())
							throw new java.util.NoSuchElementException();
						return values[theIndex++];
					}
				};
			}
		};
	}

	static <T> ExIterable<T, RuntimeException> fromIterable(Iterable<? extends T> values) {
		return new ExIterable<T, RuntimeException>() {
			@Override
			public ExIterator<T, RuntimeException> iterator() {
				return ExIterator.fromIterator(values.iterator());
			}
		};
	}

	static <T, E extends Throwable> ExIterable<T, E> forEx(ExIterable<T, RuntimeException> safe) {
		return (ExIterable<T, E>)(ExIterable<T, ?>) safe;
	}

	static <T, E extends Throwable> ExIterable<T, E> flatten(
			ExIterable<? extends ExIterable<? extends T, ? extends E>, ? extends E> container) {
		return new ExIterable<T, E>() {
			@Override
			public ExIterator<T, E> iterator() {
				return new ExIterator<T, E>() {
					private final ExIterator<? extends ExIterable<? extends T, ? extends E>, ? extends E> containerIterator = container
							.iterator();

					private ExIterator<? extends T, ? extends E> elementIterator = null;

					@Override
					public boolean hasNext() throws E {
						while((elementIterator == null || !elementIterator.hasNext()) && containerIterator.hasNext())
							elementIterator = containerIterator.next().iterator();
						return elementIterator != null && elementIterator.hasNext();
					}

					@Override
					public T next() throws E {
						if(!hasNext())
							throw new java.util.NoSuchElementException();
						return elementIterator.next();
					}

					@Override
					public void remove() {
						if(elementIterator == null)
							throw new java.util.NoSuchElementException();
						elementIterator.remove();
					}
				};
			}
		};
	}

	/**
	 * @param <T> The type of values to iterate
	 * @param <E> The super type of exceptions throwable by any of the given iterables
	 * @param elements The iterables for each element of the path
	 * @return An iterable for all paths. I.e. for each value output by the first iterator, the second iterator will be created. For each
	 *         element of that iterator, the third will be created, etc. Each path thus produced will be returned by this iterable. This
	 *         method reuses the list instance returned from the path iterator to save memory, so the consumer of the values must process or
	 *         copy the values it receives.
	 */
	public static <T, E extends Throwable> ExIterable<List<T>, E> combine(
		ExIterator<? extends ExIterable<? extends T, ? extends E>, ? extends E> elements) {
		return new ExIterable<List<T>, E>() {
			@Override
			public ExIterator<List<T>, E> iterator() {
				return new ExIterator<List<T>, E>() {
					private final LinkedList<T> values;
					private final List<T> exposedValues;
					private final List<ExIterable<? extends T, ? extends E>> iterables;
					private final LinkedList<ExIterator<? extends T, ? extends E>> iterators;
					private boolean isInitialized;
					private boolean readyForNext;

					{
						values = new LinkedList<>();
						exposedValues = Collections.unmodifiableList(values);
						iterables = new ArrayList<>();
						iterators = new LinkedList<>();
					}

					private void initialize() throws E {
						isInitialized = true;
						if (elements.hasNext()) {
							ExIterable<? extends T, ? extends E> iterable = elements.next();
							iterables.add(iterable);
							ExIterator<? extends T, ? extends E> iterator = iterable.iterator();
							iterators.add(iterator);
							values.add(null);
						}
					}

					@Override
					public boolean hasNext() throws E {
						if (!isInitialized)
							initialize();
						if (readyForNext)
							return !iterators.isEmpty();
						readyForNext = true;
						trimExhausted();
						return !iterators.isEmpty();
					}

					@Override
					public List<T> next() throws E {
						if (!readyForNext && !hasNext())
							throw new NoSuchElementException();
						advance();
						readyForNext = false;
						return exposedValues;
					}

					private void trimExhausted() throws E {
						// Back up to an iterator with more elements, if any
						while (!iterators.isEmpty() && !iterators.getLast().hasNext()) {
							iterators.removeLast();
							values.removeLast();
						}
					}

					private void advance() throws E {
						values.removeLast();
						T nextValue = iterators.getLast().next();
						values.add(nextValue);

						// Get iterators after the new element
						// Start with cached iterables
						boolean pathComplete = false;
						{
							ListIterator<ExIterable<? extends T, ? extends E>> iterableIter = iterables.listIterator(iterators.size());
							while (iterableIter.hasNext()) {
								ExIterator<? extends T, ? extends E> iterator = iterableIter.next().iterator();
								if (!iterator.hasNext()) {
									pathComplete = true;
									break;
								}
								iterators.add(iterator);
								nextValue = iterator.next();
								values.add(nextValue);
							}
						}

						// After cached iterables exhausted, get new iterables and cache them
						if (!pathComplete) {
							while (elements.hasNext()) {
								ExIterable<? extends T, ? extends E> iterable = elements.next();
								iterables.add(iterable);
								ExIterator<? extends T, ? extends E> iterator = iterable.iterator();
								if (!iterator.hasNext()) {
									pathComplete = true;
									break;
								}
								iterators.add(iterator);
								nextValue = iterator.next();
								values.add(nextValue);
							}
						}
					}
				};
			}
		};
	}
}
