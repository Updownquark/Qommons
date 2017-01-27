package org.qommons.collect;

import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Quiterator<T> extends Spliterator<T> {
	interface CollectionElement<T> extends Settable<T> {
		boolean canRemove();
		boolean canAdd(T toAdd);

		void remove();
		boolean add(T toAdd);
		boolean replace(T replacement);
	}

	enum OperationType {
		REMOVE(1), REPLACE(2), ADD(4);

		public final int mask;

		private OperationType(int mask) {
			this.mask = mask;
		}

		public boolean has(int bitMask) {
			return (mask & bitMask) != 0;
		}

		public static int maskFor(OperationType... types) {
			int mask = 0;
			for (OperationType type : types)
				mask |= type.mask;
			return mask;
		}

		public static int without(int mask, OperationType... types) {
			return mask & (~maskFor(types));
		}
	}

	final class Operation<T> {
		public final OperationType type;
		public final T value;

		public Operation(OperationType type, T value) {
			this.type = type;
			this.value = value;
		}

		public <V> Operation<V> map(Function<? super T, V> map) {
			return new Operation<>(type, type == OperationType.REMOVE ? null : map.apply(value));
		}
	}

	static <T> Operation<T> remove() {
		return new Operation<>(OperationType.REMOVE, null);
	}

	static <T> Operation<T> replace(T value) {
		return new Operation<>(OperationType.REPLACE, value);
	}

	static <T> Operation<T> add(T value) {
		return new Operation<>(OperationType.ADD, value);
	}

	/** @return A mask containing the mask bit of each operation type supported by this Quiterator */
	int operations();

	boolean tryAdvance(Function<? super T, Operation<? extends T>> action);

	@Override
	default boolean tryAdvance(Consumer<? super T> action) {
		return tryAdvance(v->{
			action.accept(v);
			return null;
		});
	}

	@Override
	default void forEachRemaining(Consumer<? super T> action) {
		while(tryAdvance(v->{
			action.accept(v);
			return null;
		})) {
		}
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
}
