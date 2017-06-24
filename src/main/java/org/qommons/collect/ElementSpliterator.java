package org.qommons.collect;

import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
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
 * @param <E> The type of values that this ElementSpliterator provides
 */
public interface ElementSpliterator<E> extends Spliterator<E> {
	/** Message returned for attempting to put an element into a space that is not compatible with its type */
	static String BAD_TYPE = "Object is the wrong type for this collection";

	/** @return The type of elements returned by this ElementSpliterator */
	TypeToken<E> getType();

	/**
	 * Iterates through each element covered by this ElementSpliterator
	 * 
	 * @param action Accepts each element in sequence. Unless a sub-type of ElementSpliterator or a specific supplier of a ElementSpliterator advertises
	 *        otherwise, the element object may only be treated as valid until the next element is returned and also should not be kept
	 *        longer than the reference to the ElementSpliterator.
	 * @return false if no remaining elements existed upon entry to this method, else true.
	 */
	boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action);

	/**
	 * Operates on each element remaining in this ElementSpliterator
	 * 
	 * @param action The action to perform on each element
	 */
	default void forEachElement(Consumer<? super CollectionElement<E>> action) {
		while (tryAdvanceElement(action)) {
		}
	}

	@Override
	default boolean tryAdvance(Consumer<? super E> action) {
		return tryAdvanceElement(v -> {
			action.accept(v.get());
		});
	}

	@Override
	default void forEachRemaining(Consumer<? super E> action) {
		while (tryAdvance(v -> action.accept(v))) {
		}
	}

	@Override
	ElementSpliterator<E> trySplit();

	interface ElementSpliteratorMap<E, T> {
		TypeToken<T> getType();

		T map(E value);

		E reverse(T value);

		String filterEnabled(CollectionElement<E> el);

		String filterRemove(CollectionElement<E> sourceEl);

		default boolean canFilterValues() {
			return true;
		}
		boolean test(E srcValue);
		default boolean test(CollectionElement<E> el) {
			return test(el.get());
		}

		default String filterAccept(T value) throws IllegalArgumentException, UnsupportedOperationException {
			return null;
		}

		default long filterEstimatedSize(long srcSize) {
			return srcSize;
		}

		default int filterExactSize(long srcSize) {
			return -1;
		}

		default int modifyCharacteristics(int srcChars) {
			return srcChars;
		}

		default Comparator<? super T> mapComparator(Comparator<? super E> srcCompare) {
			return null;
		}
	}

	default <T> ElementSpliterator<T> map(ElementSpliteratorMap<E, T> map) {
		return new MappedElementSpliterator<>(this, map);
	}

	/** @return An immutable spliterator backed by this spliterator */
	default Spliterator<E> immutable() {
		return new Spliterator<E>() {
			@Override
			public boolean tryAdvance(Consumer<? super E> action) {
				return ElementSpliterator.this.tryAdvance(action);
			}

			@Override
			public void forEachRemaining(Consumer<? super E> action) {
				ElementSpliterator.this.forEachRemaining(action);
			}

			@Override
			public Spliterator<E> trySplit() {
				ElementSpliterator<E> split = ElementSpliterator.this.trySplit();
				return split == null ? null : split.immutable();
			}

			@Override
			public long estimateSize() {
				return ElementSpliterator.this.estimateSize();
			}

			@Override
			public long getExactSizeIfKnown() {
				return ElementSpliterator.this.getExactSizeIfKnown();
			}

			@Override
			public int characteristics() {
				return ElementSpliterator.this.characteristics();
			}

			@Override
			public Comparator<? super E> getComparator() {
				return ElementSpliterator.this.getComparator();
			}
		};
	}

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
	 * An ElementSpliterator mapped by an {@link ElementSpliterator.ElementSpliteratorMap}
	 * 
	 * @param <E> The type of the source spliterator
	 * @param <T> The type of this spliterator
	 */
	class MappedElementSpliterator<E, T> implements ElementSpliterator<T> {
		private final ElementSpliterator<E> theSource;
		private final ElementSpliteratorMap<E, T> theMap;

		private TypeToken<T> theType;
		private final MappedElement<E, T> theElement;

		public MappedElementSpliterator(ElementSpliterator<E> source, ElementSpliteratorMap<E, T> map) {
			theSource = source;
			theMap = map;
			theElement = createElement();
		}

		protected ElementSpliterator<E> getSource() {
			return theSource;
		}

		protected ElementSpliteratorMap<E, T> getMap() {
			return theMap;
		}

		protected MappedElement<E, T> getElement() {
			return theElement;
		}

		protected MappedElement<E, T> createElement() {
			return new MappedElement<>(theMap, this::getType);
		}

		@Override
		public long estimateSize() {
			return theMap.filterEstimatedSize(theSource.estimateSize());
		}

		@Override
		public long getExactSizeIfKnown() {
			return theMap.filterExactSize(theSource.getExactSizeIfKnown());
		}

		@Override
		public int characteristics() {
			return theMap.modifyCharacteristics(theSource.characteristics());
		}

		@Override
		public Comparator<? super T> getComparator() {
			return theMap.mapComparator(theSource.getComparator());
		}

		@Override
		public TypeToken<T> getType() {
			if (theType == null)
				theType = theMap.getType();
			return theType;
		}

		@Override
		public boolean tryAdvanceElement(Consumer<? super CollectionElement<T>> action) {
			while (theSource.tryAdvanceElement(el -> {
				theElement.setSource(el);
				if (theElement.isAccepted())
					action.accept(theElement);
			})) {
				if (theElement.isAccepted())
					return true;
			}
			return false;
		}

		@Override
		public void forEachElement(Consumer<? super CollectionElement<T>> action) {
			theSource.forEachElement(el -> {
				theElement.setSource(el);
				if (theElement.isAccepted())
					action.accept(theElement);
			});
		}

		@Override
		public boolean tryAdvance(Consumer<? super T> action) {
			if (theMap.canFilterValues()) {
				boolean[] accepted = new boolean[1];
				while (!accepted[0] && theSource.tryAdvance(v -> {
					accepted[0] = theMap.test(v);
					if (accepted[0])
						action.accept(theMap.map(v));
				})) {
				}
				return accepted[0];
			} else
				return ElementSpliterator.super.tryAdvance(action);
		}

		@Override
		public void forEachRemaining(Consumer<? super T> action) {
			if (theMap.canFilterValues()) {
				theSource.forEachRemaining(v -> {
					if (theMap.test(v))
						action.accept(theMap.map(v));
				});
			} else
				ElementSpliterator.super.forEachRemaining(action);
		}

		@Override
		public ElementSpliterator<T> trySplit() {
			ElementSpliterator<E> split = theSource.trySplit();
			return split == null ? null : split.map(theMap);
		}

		@Override
		public Spliterator<T> immutable() {
			if (theMap.canFilterValues()) {
				Spliterator<E> srcSplit = theSource.immutable();
				return new MappedSpliterator<>(srcSplit, theMap);
			} else
				return ElementSpliterator.super.immutable();
		}

		protected static class MappedElement<E, T> implements CollectionElement<T> {
			private CollectionElement<E> theSourceEl;
			private final ElementSpliteratorMap<E, T> theMap;
			private final Supplier<TypeToken<T>> theType;
			private boolean isAccepted;

			protected MappedElement(ElementSpliteratorMap<E, T> map, Supplier<TypeToken<T>> type) {
				theMap = map;
				theType = type;
			}

			protected void setSource(CollectionElement<E> sourceEl) {
				isAccepted = theMap.test(sourceEl);
				theSourceEl = isAccepted ? sourceEl : null;
			}

			protected CollectionElement<E> getSourceEl() {
				return theSourceEl;
			}

			protected boolean isAccepted() {
				return isAccepted;
			}

			@Override
			public TypeToken<T> getType() {
				return theType.get();
			}

			@Override
			public T get() {
				return theMap.map(theSourceEl.get());
			}

			@Override
			public Value<String> isEnabled() {
				return new Value<String>() {
					@Override
					public TypeToken<String> getType() {
						return TypeToken.of(String.class);
					}

					@Override
					public String get() {
						String filterEnabled = theMap.filterEnabled(theSourceEl);
						if (filterEnabled != null)
							return filterEnabled;
						return theSourceEl.isEnabled().get();
					}
				};
			}

			@Override
			public <V extends T> String isAcceptable(V value) {
				String preFilter = theMap.filterAccept(value);
				if (preFilter != null)
					throw new IllegalArgumentException(preFilter);
				return theSourceEl.isAcceptable(theMap.reverse(value));
			}

			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
				String preFilter = theMap.filterAccept(value);
				if (preFilter != null)
					throw new IllegalArgumentException(preFilter);
				return theMap.map(theSourceEl.set(theMap.reverse(value), cause));
			}

			@Override
			public String canRemove() {
				String msg = theMap.filterRemove(theSourceEl);
				if (msg != null)
					return msg;
				return theSourceEl.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				String msg = theMap.filterRemove(theSourceEl);
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				theSourceEl.remove();
			}

			@Override
			public String toString() {
				return String.valueOf(get());
			}
		}
	}

	/**
	 * A Spliterator mapped by an {@link ElementSpliterator.ElementSpliteratorMap}
	 * 
	 * @param <T> The type of the source spliterator
	 * @param <E> The type of this spliterator
	 */
	class MappedSpliterator<T, E> implements Spliterator<E> {
		private final Spliterator<T> theSource;
		private final ElementSpliteratorMap<T, E> theMap;

		public MappedSpliterator(Spliterator<T> source, ElementSpliteratorMap<T, E> map) {
			theSource = source;
			theMap = map;
		}

		@Override
		public boolean tryAdvance(Consumer<? super E> action) {
			boolean [] accepted=new boolean[1];
			while(!accepted[0] && theSource.tryAdvance(v->{
				accepted[0] = theMap.test(v);
				if (accepted[0])
					action.accept(theMap.map(v));
			})) {
			}
			return accepted[0];
		}

		@Override
		public void forEachRemaining(Consumer<? super E> action) {
			theSource.forEachRemaining(v -> {
				if (theMap.test(v))
					action.accept(theMap.map(v));
			});
		}

		@Override
		public Spliterator<E> trySplit() {
			Spliterator<T> split = theSource.trySplit();
			return new MappedSpliterator<>(split, theMap);
		}

		@Override
		public long estimateSize() {
			return theMap.filterEstimatedSize(theSource.estimateSize());
		}

		@Override
		public int characteristics() {
			return theMap.modifyCharacteristics(theSource.characteristics());
		}

		@Override
		public long getExactSizeIfKnown() {
			return theMap.filterExactSize(theSource.getExactSizeIfKnown());
		}

		@Override
		public Comparator<? super E> getComparator() {
			return theMap.mapComparator(theSource.getComparator());
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

		protected Spliterator<T> getWrapped() {
			return theWrapped;
		}

		protected Supplier<? extends Function<? super T, ? extends CollectionElement<V>>> getMap() {
			return theMap;
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
