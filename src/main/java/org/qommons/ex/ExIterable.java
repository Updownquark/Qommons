package org.qommons.ex;

import java.util.Iterator;

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

	default <V> ExIterable<V, E> map(ExFunction<T, V, E> map) {
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

	static <T, E extends Throwable> ExIterable<T, E> iterate(T... values) {
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

	static <T, E extends Throwable> ExIterable<T, E> iterate(Iterable<T> values) {
		return new ExIterable<T, E>() {
			@Override
			public ExIterator<T, E> iterator() {
				return new ExIterator<T, E>() {
					private final Iterator<T> backing = values.iterator();

					@Override
					public boolean hasNext() {
						return backing.hasNext();
					}

					@Override
					public T next() {
						return backing.next();
					}
				};
			}
		};
	}

	static <T, E extends Throwable> ExIterable<T, E> flatten(ExIterable<ExIterable<T, E>, E> container) {
		return new ExIterable<T, E>() {
			@Override
			public ExIterator<T, E> iterator() {
				return new ExIterator<T, E>() {
					private final ExIterator<ExIterable<T, E>, E> containerIterator = container.iterator();

					private ExIterator<T, E> elementIterator = null;

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
}
