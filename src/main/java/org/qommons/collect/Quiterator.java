package org.qommons.collect;

import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.qommons.value.Settable;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

public interface Quiterator<T> extends Spliterator<T> {
	interface CollectionElement<T> extends Settable<T> {
		String canRemove();
		void remove() throws IllegalArgumentException;
	}

	/**
	 * Iterates through each element covered by this Quiterator
	 * 
	 * @param action Accepts each element in sequence. Unless a sub-type of Quiterator or a specific supplier of a Quiterator advertises
	 *        otherwise, the element object may only be treated as valid until the next element is returned and should not be kept longer
	 *        than the reference to the Quiterator.
	 * @return false if no remaining elements existed upon entry to this method, else true.
	 */
	boolean tryAdvanceElement(Consumer<? super CollectionElement<T>> action);

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
		while (tryAdvanceElement(el -> action.accept(el.get())))
			;
	}

	default <V> Quiterator<V> map(Function<? super T, V> map) {
		return map((TypeToken<V>) TypeToken.of(map.getClass()).resolveType(Function.class.getTypeParameters()[1]), map, null);
	}

	default <V> Quiterator<V> map(TypeToken<V> type, Function<? super T, V> map, Function<? super V, ? extends T> reverse) {
		return new MappedQuiterator<>(type, this, map, reverse);
	}

	default Quiterator<T> filter(Predicate<? super T> filter) {
		return new FilteredQuiterator<>(this, filter);
	}

	@Override
	Quiterator<T> trySplit();

	class MappedQuiterator<T, V> extends WrappingQuiterator<T, V> {
		private final Quiterator<T> theWrapped;
		private final Function<? super T, V> theMap;
		private final Function<? super V, ? extends T> theReverse;

		public MappedQuiterator(TypeToken<V> type, Quiterator<T> wrap, Function<? super T, V> map,
			Function<? super V, ? extends T> reverse) {
			super(wrap, () -> {
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
			theWrapped = wrap;
			theMap = map;
			theReverse = reverse;
		}
	}

	class FilteredQuiterator<T> implements Quiterator<T> {
		private final Quiterator<T> theWrapped;
		private final Predicate<? super T> theFilter;

		public FilteredQuiterator(Quiterator<T> wrap, Predicate<? super T> filter) {
			theWrapped = wrap;
			theFilter = filter;
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
		public Quiterator<T> trySplit() {
			return theWrapped.trySplit().filter(theFilter);
		}
	}

	class SimpleQuiterator<T, V> implements Quiterator<V> {
		private final Spliterator<T> theWrapped;
		private final Supplier<? extends Function<? super T, ? extends CollectionElement<V>>> theMap;
		private final Function<? super T, ? extends CollectionElement<V>> theInstanceMap;

		public SimpleQuiterator(Spliterator<T> wrap,
			Supplier<? extends Function<? super T, ? extends org.qommons.collect.Quiterator.CollectionElement<V>>> map) {
			theWrapped = wrap;
			theMap = map;
			theInstanceMap = theMap.get();
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
		public Quiterator<V> trySplit() {
			Spliterator<T> split = theWrapped.trySplit();
			if (split == null)
				return null;
			return new SimpleQuiterator<>(split, theMap);
		}
	}

	class WrappingQuiterator<T, V> implements Quiterator<V> {
		private final Quiterator<? extends T> theWrapped;
		private final Supplier<? extends Function<? super CollectionElement<? extends T>, ? extends CollectionElement<V>>> theMap;
		private final Function<? super CollectionElement<? extends T>, ? extends CollectionElement<V>> theInstanceMap;

		public WrappingQuiterator(Quiterator<? extends T> wrap,
			Supplier<? extends Function<? super CollectionElement<? extends T>, ? extends CollectionElement<V>>> map) {
			theWrapped = wrap;
			theMap = map;
			theInstanceMap = theMap.get();
		}

		protected Quiterator<? extends T> getWrapped() {
			return theWrapped;
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
		public Quiterator<V> trySplit() {
			Quiterator<? extends T> wrapSplit = theWrapped.trySplit();
			if (wrapSplit == null)
				return null;
			return new WrappingQuiterator<>(wrapSplit, theMap);
		}
	}

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
}
