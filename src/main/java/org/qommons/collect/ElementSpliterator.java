package org.qommons.collect;

import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.qommons.value.Settable;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

/**
 * A {@link Spliterator} that allows the option of providing its values wrapped in a {@link CollectionElement}, which allows elements in the
 * source collection to be replaced (using {@link Settable#set(Object, Object)}) or {@link CollectionElement#remove() removed} during
 * iteration.
 * 
 * ElementSpliterators are {@link #trySplit() splittable} just as Spliterators are, though the added functionality (particularly
 * {@link CollectionElement#remove()}) may be disabled for split spliterators.
 * 
 * @param <T> The type of values that this ElementSpliterator provides
 */
public interface ElementSpliterator<T> extends Spliterator<T> {
	/** Message returned for attempting to put an element into a space that is not compatible with its type */
	static String BAD_TYPE = "Object is the wrong type for this collection";

	/** @return The type of elements returned by this ElementSpliterator */
	TypeToken<T> getType();

	/**
	 * Iterates through each element covered by this ElementSpliterator
	 * 
	 * @param action Accepts each element in sequence. Unless a sub-type of ElementSpliterator or a specific supplier of a ElementSpliterator advertises
	 *        otherwise, the element object may only be treated as valid until the next element is returned and also should not be kept
	 *        longer than the reference to the ElementSpliterator.
	 * @return false if no remaining elements existed upon entry to this method, else true.
	 */
	boolean tryAdvanceElement(Consumer<? super CollectionElement<T>> action);

	/**
	 * Operates on each element remaining in this ElementSpliterator
	 * 
	 * @param action The action to perform on each element
	 */
	default void forEachElement(Consumer<? super CollectionElement<T>> action) {
		while (tryAdvanceElement(action)) {
		}
	}

	@Override
	default boolean tryAdvance(Consumer<? super T> action) {
		return tryAdvanceElement(v -> {
			action.accept(v.get());
		});
	}

	@Override
	default void forEachRemaining(Consumer<? super T> action) {
		while (tryAdvance(v -> action.accept(v))) {
		}
	}

	/**
	 * @param <V> The type to map to
	 * @param map The mapping function
	 * @return A new ElementSpliterator whose values are the given map applied to this ElementSpliterator's values
	 */
	default <V> ElementSpliterator<V> map(Function<? super T, V> map) {
		return map((TypeToken<V>) TypeToken.of(map.getClass()).resolveType(Function.class.getTypeParameters()[1]), map, null);
	}

	/**
	 * @param <V> The compile-time type to map to
	 * @param type The run-time type of the map result
	 * @param map The mapping function
	 * @param reverse The reverse-function for allowing {@link CollectionElement#set(Object, Object) replacement} of values
	 * @return A new ElementSpliterator whose values are the given map applied to this ElementSpliterator's values
	 */
	default <V> ElementSpliterator<V> map(TypeToken<V> type, Function<? super T, V> map, Function<? super V, ? extends T> reverse) {
		return new MappedQuiterator<>(type, this, map, reverse);
	}

	/**
	 * @param filter The filter
	 * @return A new ElementSpliterator whose values are the this ElementSpliterator's values that pass the given filter
	 */
	default ElementSpliterator<T> filter(Predicate<? super T> filter) {
		return new FilteredSpliterator<>(this, filter);
	}

	@Override
	ElementSpliterator<T> trySplit();

	/**
	 * @param <E> The compile-time type for the spliterator
	 * @param type The type for the ElementSpliterator
	 * @return An empty ElementSpliterator of the given type
	 */
	static <E> ElementSpliterator<E> empty(TypeToken<E> type) {
		return new ElementSpliterator<E>() {
			@Override
			public long estimateSize() {
				return 0;
			}

			@Override
			public int characteristics() {
				return Spliterator.IMMUTABLE | Spliterator.SIZED;
			}

			@Override
			public TypeToken<E> getType() {
				return type;
			}

			@Override
			public boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
				return false;
			}

			@Override
			public ElementSpliterator<E> trySplit() {
				return null;
			}
		};
	}

	/**
	 * Implements {@link ElementSpliterator#map(TypeToken, Function, Function)}
	 * 
	 * @param <T> The type of values returned by the wrapped ElementSpliterator
	 * @param <V> The type of values returned by this ElementSpliterator
	 */
	class MappedQuiterator<T, V> extends WrappingSpliterator<T, V> {
		public MappedQuiterator(TypeToken<V> type, ElementSpliterator<T> wrap, Function<? super T, V> map,
			Function<? super V, ? extends T> reverse) {
			super(wrap, type, () -> {
				CollectionElement<? extends T>[] container = new CollectionElement[1];
				WrappingElement<T, V> wrapper = new WrappingElement<T, V>(type, container) {
					@Override
					public V get() {
						return map.apply(getWrapped().get());
					}

					@Override
					public <V2 extends V> String isAcceptable(V2 value) {
						if (reverse == null)
							return "Replacement is not enabled for this collection";
						T reversed = reverse.apply(value);
						return ((CollectionElement<T>) getWrapped()).isAcceptable(reversed);
					}

					@Override
					public <V2 extends V> V set(V2 value, Object cause) throws IllegalArgumentException {
						if (reverse == null)
							throw new IllegalArgumentException("Replacement is not enabled for this collection");
						T reversed = reverse.apply(value);
						return map.apply(((CollectionElement<T>) getWrapped()).set(reversed, cause));
					}
				};
				return el -> {
					container[0] = el;
					return wrapper;
				};
			});
		}
	}

	/**
	 * Implements {@link ElementSpliterator#filter(Predicate)}
	 * 
	 * @param <T> The type of values returned by this ElementSpliterator
	 */
	class FilteredSpliterator<T> implements ElementSpliterator<T> {
		private final ElementSpliterator<T> theWrapped;
		private final Predicate<? super T> theFilter;

		public FilteredSpliterator(ElementSpliterator<T> wrap, Predicate<? super T> filter) {
			theWrapped = wrap;
			theFilter = filter;
		}

		@Override
		public TypeToken<T> getType() {
			return theWrapped.getType();
		}

		@Override
		public boolean tryAdvanceElement(Consumer<? super CollectionElement<T>> action) {
			boolean[] found = new boolean[1];
			while (!found[0] && theWrapped.tryAdvanceElement(el -> {
				if (theFilter.test(el.get())) {
					found[0] = true;
					action.accept(el);
				}
			})) {
			}
			return found[0];
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<T>> action) {
			theWrapped.forEachElement(el -> {
				if (theFilter.test(el.get()))
					action.accept(el);
			});
		}

		@Override
		public long estimateSize() {
			return theWrapped.estimateSize(); // May not be right, but it's at least an upper bound
		}

		@Override
		public int characteristics() {
			return theWrapped.characteristics() & (~Spliterator.SIZED);
		}

		@Override
		public ElementSpliterator<T> trySplit() {
			return theWrapped.trySplit().filter(theFilter);
		}
	}

	/**
	 * A ElementSpliterator whose elements are the result of some filter-map operation on a vanilla {@link Spliterator}'s elements
	 * 
	 * @param <T> The type of elements in the wrapped Spliterator
	 * @param <V> The type of this ElementSpliterator's elements
	 */
	class SimpleSpliterator<T, V> implements ElementSpliterator<V> {
		private final Spliterator<T> theWrapped;
		private final TypeToken<V> theType;
		private final Supplier<? extends Function<? super T, ? extends CollectionElement<V>>> theMap;
		private final Function<? super T, ? extends CollectionElement<V>> theInstanceMap;

		public SimpleSpliterator(Spliterator<T> wrap, TypeToken<V> type,
			Supplier<? extends Function<? super T, ? extends CollectionElement<V>>> map) {
			theWrapped = wrap;
			theType = type;
			theMap = map;
			theInstanceMap = theMap.get();
		}

		@Override
		public TypeToken<V> getType() {
			return theType;
		}

		@Override
		public long estimateSize() {
			return theWrapped.estimateSize();
		}

		@Override
		public int characteristics() {
			return theWrapped.characteristics();
		}

		@Override
		public boolean tryAdvanceElement(Consumer<? super CollectionElement<V>> action) {
			boolean[] passed = new boolean[1];
			while (!passed[0] && theWrapped.tryAdvance(el -> {
				CollectionElement<V> mapped = theInstanceMap.apply(el);
				if (mapped != null) {
					passed[0] = true;
					action.accept(mapped);
				}
			})) {
			}
			return passed[0];
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<V>> action) {
			theWrapped.forEachRemaining(el -> {
				CollectionElement<V> mapped = theInstanceMap.apply(el);
				if (mapped != null)
					action.accept(mapped);
			});
		}

		@Override
		public ElementSpliterator<V> trySplit() {
			Spliterator<T> split = theWrapped.trySplit();
			if (split == null)
				return null;
			return new SimpleSpliterator<>(split, theType, theMap);
		}
	}

	/**
	 * A ElementSpliterator whose elements are the result of some filter-map operation on another ElementSpliterator's elements
	 * 
	 * @param <T> The type of elements in the wrapped ElementSpliterator
	 * @param <V> The type of this ElementSpliterator's elements
	 */
	class WrappingSpliterator<T, V> implements ElementSpliterator<V> {
		private final ElementSpliterator<? extends T> theWrapped;
		private final TypeToken<V> theType;
		private final Supplier<? extends Function<? super CollectionElement<? extends T>, ? extends CollectionElement<V>>> theMap;
		private final Function<? super CollectionElement<? extends T>, ? extends CollectionElement<V>> theInstanceMap;

		public WrappingSpliterator(ElementSpliterator<? extends T> wrap, TypeToken<V> type,
			Supplier<? extends Function<? super CollectionElement<? extends T>, ? extends CollectionElement<V>>> map) {
			theWrapped = wrap;
			theType = type;
			theMap = map;
			theInstanceMap = theMap.get();
		}

		@Override
		public TypeToken<V> getType() {
			return theType;
		}

		protected ElementSpliterator<? extends T> getWrapped() {
			return theWrapped;
		}

		protected Supplier<? extends Function<? super CollectionElement<? extends T>, ? extends CollectionElement<V>>> getMap() {
			return theMap;
		}

		protected Function<? super CollectionElement<? extends T>, ? extends CollectionElement<V>> getInstanceMap() {
			return theInstanceMap;
		}

		@Override
		public long estimateSize() {
			return theWrapped.estimateSize();
		}

		@Override
		public int characteristics() {
			return theWrapped.characteristics();
		}

		@Override
		public long getExactSizeIfKnown() {
			return theWrapped.getExactSizeIfKnown();
		}

		@Override
		public boolean tryAdvanceElement(Consumer<? super CollectionElement<V>> action) {
			boolean[] passed = new boolean[1];
			while (!passed[0] && theWrapped.tryAdvanceElement(el -> {
				CollectionElement<V> mapped = theInstanceMap.apply(el);
				if (mapped != null) {
					passed[0] = true;
					action.accept(mapped);
				}
			})) {
			}
			return passed[0];
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<V>> action) {
			theWrapped.forEachElement(el -> {
				CollectionElement<V> mapped = theInstanceMap.apply(el);
				if (mapped != null)
					action.accept(mapped);
			});
		}

		@Override
		public ElementSpliterator<V> trySplit() {
			ElementSpliterator<? extends T> wrapSplit = theWrapped.trySplit();
			if (wrapSplit == null)
				return null;
			return new WrappingSpliterator<>(wrapSplit, theType, theMap);
		}
	}

	/**
	 * An element returned from {@link ElementSpliterator.WrappingSpliterator}
	 * 
	 * @param <T> The type of value in the element wrapped by this element
	 * @param <V> The type of this element
	 */
	abstract class WrappingElement<T, V> implements CollectionElement<V> {
		private final TypeToken<V> theType;
		private final CollectionElement<? extends T>[] theWrapped;

		public WrappingElement(TypeToken<V> type, CollectionElement<? extends T>[] wrapped) {
			theType = type;
			theWrapped = wrapped;
		}

		protected CollectionElement<? extends T> getWrapped() {
			return theWrapped[0];
		}

		@Override
		public TypeToken<V> getType() {
			return theType;
		}

		@Override
		public Value<String> isEnabled() {
			return theWrapped[0].isEnabled();
		}

		@Override
		public String canRemove() {
			return theWrapped[0].canRemove();
		}

		@Override
		public void remove() {
			theWrapped[0].remove();
		}
	}

	/**
	 * An iterator backed by an {@link ElementSpliterator}
	 *
	 * @param <E> The type of elements to iterate over
	 */
	public static class SpliteratorBetterator<E> implements Betterator<E> {
		private final ElementSpliterator<E> theSpliterator;

		private boolean isNextCached;
		private boolean isDone;
		private CollectionElement<? extends E> cachedNext;

		/** @param spliterator The spliterator to back this iterator */
		public SpliteratorBetterator(ElementSpliterator<E> spliterator) {
			theSpliterator = spliterator;
		}

		@Override
		public boolean hasNext() {
			if (!isNextCached && !isDone) {
				cachedNext = null;
				if (theSpliterator.tryAdvanceElement(element -> {
					cachedNext = element;
				}))
					isNextCached = true;
				else
					isDone = true;
			}
			return isNextCached;
		}

		@Override
		public E next() {
			if (!hasNext())
				throw new java.util.NoSuchElementException();
			isNextCached = false;
			return cachedNext.get();
		}

		@Override
		public String canRemove() {
			if (cachedNext == null)
				throw new IllegalStateException(
					"First element has not been read, element has already been removed, or iterator has finished");
			if (isNextCached)
				throw new IllegalStateException("canRemove() must be called after next() and before the next call to hasNext()");
			return cachedNext.canRemove();
		}

		@Override
		public void remove() {
			if (cachedNext == null)
				throw new IllegalStateException(
					"First element has not been read, element has already been removed, or iterator has finished");
			if (isNextCached)
				throw new IllegalStateException("remove() must be called after next() and before the next call to hasNext()");
			cachedNext.remove();
			cachedNext = null;
		}

		@Override
		public String isAcceptable(E value) {
			if (cachedNext == null)
				throw new IllegalStateException(
					"First element has not been read, element has already been removed, or iterator has finished");
			if (isNextCached)
				throw new IllegalStateException("isAcceptable() must be called after next() and before the next call to hasNext()");
			if (!cachedNext.getType().getRawType().isInstance(value))
				return BAD_TYPE;
			return ((CollectionElement<E>) cachedNext).isAcceptable(value);
		}

		@Override
		public E set(E value, Object cause) {
			if (cachedNext == null)
				throw new IllegalStateException(
					"First element has not been read, element has already been removed, or iterator has finished");
			if (isNextCached)
				throw new IllegalStateException("set() must be called after next() and before the next call to hasNext()");
			if (!cachedNext.getType().getRawType().isInstance(value))
				throw new IllegalStateException(BAD_TYPE);
			return ((CollectionElement<E>) cachedNext).set(value, cause);
		}

		@Override
		public void forEachRemaining(Consumer<? super E> action) {
			if (isNextCached)
				action.accept(next());
			cachedNext = null;
			isDone = true;
			theSpliterator.forEachRemaining(action);
		}

		@Override
		public String toString() {
			return theSpliterator.toString();
		}
	}
}
