package org.qommons.collect;

import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.qommons.collect.Quiterator.CollectionElement;
import org.qommons.value.Settable;
import org.qommons.value.Value;

import com.google.common.reflect.TypeToken;

public interface Quiterator<T> extends Spliterator<T> {
	interface CollectionElement<T> extends Settable<T> {
		String canRemove();
		void remove() throws IllegalArgumentException;

		String canAdd(T toAdd);
		void add(T toAdd) throws IllegalArgumentException;
	}

	/**
	 * Iterates through each element covered by this Quiterator
	 * 
	 * @param action Accepts each element in sequence. Unless a sub-type of Quiterator or a specific supplier of a Quiterator advertises
	 *        otherwise, the element object may only be treated as valid until the next element is returned and should not be kept longer
	 *        than the reference to the Quiterator.
	 * @return false if no remaining elements existed upon entry to this method, else true.
	 */
	boolean tryAdvanceElement(Consumer<? super CollectionElement<? extends T>> action);

	default void forEachElement(Consumer<? super CollectionElement<? extends T>> action) {
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
		forEachElement(el -> action.accept(el.get()));
	}

	default <V> Quiterator<V> map(Function<? super T, V> map) {
		return new MappedQuiterator<>(this, map);
	}

	default Quiterator<T> filter(Predicate<? super T> filter) {
		return new FilteredQuiterator<>(this, filter);
	}

	default <V> Quiterator<V> filterMap(Function<? super T, V> map) {
		return new FilterMappedQuiterator<>(this, map);
	}

	@Override
	Quiterator<T> trySplit();

	class MappedQuiterator<T, V> implements Quiterator<V> {
		private final Quiterator<T> theWrapped;
		private final Function<? super T, V> theMap;
		private final Function<? super V, T> theReverse;

		public MappedQuiterator(Quiterator<T> wrap, Function<? super T, V> map, Function<? super V, T> reverse) {
			theWrapped = wrap;
			theMap = map;
			theReverse = reverse;
		}

		@Override
		public int operations() {
			if (theReverse != null)
				return theWrapped.operations();
			else
				return OperationType.without(theWrapped.operations(), OperationType.REPLACE, OperationType.ADD);
		}

		@Override
		public boolean tryAdvance(Function<? super V, Operation<? extends V>> action) {
			return theWrapped.tryAdvance(t ->{
				Operation<? extends V> op=action.apply(theMap.apply(t));
				if(op!=null){
					if(theReverse!=null)
						
				}
			});
		}

		@Override
		public Quiterator<V> trySplit() {
			return theWrapped.trySplit().map(theMap);
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
	}

	class FilteredQuiterator<T> implements Quiterator<T> {
		private final Quiterator<T> theWrapped;
		private final Predicate<? super T> theFilter;

		public FilteredQuiterator(Quiterator<T> wrap, Predicate<? super T> filter) {
			theWrapped = wrap;
			theFilter = filter;
		}

		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			boolean[] found = new boolean[1];
			while (theWrapped.tryAdvance(t -> {
				if (theFilter.test(t)) {
					found[0] = true;
					action.accept(t);
				}
			}) && !found[0]) {
			}
			return found[0];
		}

		@Override
		public long estimateSize() {
			return theWrapped.estimateSize();
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

	class FilterMappedQuiterator<T, V> implements Quiterator<V> {
		private final Quiterator<T> theWrapped;
		private final Function<? super T, V> theMap;

		public FilterMappedQuiterator(Quiterator<T> wrap, Function<? super T, V> map) {
			theWrapped = wrap;
			theMap = map;
		}

		@Override
		public boolean tryAdvance(Consumer<? super V> action) {
			return theWrapped.tryAdvance(t -> {
				V v = theMap.apply(t);
				if (v != null)
					action.accept(v);
			});
		}

		@Override
		public Quiterator<V> trySplit() {
			return theWrapped.trySplit().filterMap(theMap);
		}

		@Override
		public long estimateSize() {
			return theWrapped.estimateSize();
		}

		@Override
		public int characteristics() {
			return theWrapped.characteristics() & (~Spliterator.SIZED);
		}
	}

	class SimpleQuiterator<T, V> implements Quiterator<V> {
		private final Spliterator<T> theWrapped;
		private final Supplier<? extends Function<? super T, ? extends CollectionElement<? extends V>>> theMap;
		private final Function<? super T, ? extends CollectionElement<? extends V>> theInstanceMap;

		public SimpleQuiterator(Spliterator<T> wrap,
			Supplier<? extends Function<? super T, ? extends org.qommons.collect.Quiterator.CollectionElement<? extends V>>> map) {
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
		public boolean tryAdvanceElement(Consumer<? super CollectionElement<? extends V>> action) {
			boolean[] passed = new boolean[1];
			while (!passed[0] && theWrapped.tryAdvance(el -> {
				CollectionElement<? extends V> mapped = theInstanceMap.apply(el);
				if (mapped != null) {
					passed[0] = true;
					action.accept(mapped);
				}
			})) {
			}
			return passed[0];
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<? extends V>> action) {
			theWrapped.forEachRemaining(el -> {
				CollectionElement<? extends V> mapped = theInstanceMap.apply(el);
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
		private final Supplier<? extends Function<? super CollectionElement<? extends T>, ? extends CollectionElement<? extends V>>> theMap;
		private final Function<? super CollectionElement<? extends T>, ? extends CollectionElement<? extends V>> theInstanceMap;

		public WrappingQuiterator(Quiterator<? extends T> wrap,
			Supplier<? extends Function<? super CollectionElement<? extends T>, ? extends CollectionElement<? extends V>>> map) {
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
		public boolean tryAdvanceElement(Consumer<? super CollectionElement<? extends V>> action) {
			boolean[] passed = new boolean[1];
			while (!passed[0] && theWrapped.tryAdvanceElement(el -> {
				CollectionElement<? extends V> mapped = theInstanceMap.apply(el);
				if (mapped != null) {
					passed[0] = true;
					action.accept(mapped);
				}
			})) {
			}
			return passed[0];
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<? extends V>> action) {
			theWrapped.forEachElement(el -> {
				CollectionElement<? extends V> mapped = theInstanceMap.apply(el);
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
