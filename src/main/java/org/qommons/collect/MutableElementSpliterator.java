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
 * A {@link Spliterator} that allows the option of providing its values wrapped in a {@link MutableElementHandle}, which allows elements in the
 * source collection to be replaced (using {@link Settable#set(Object, Object)}) or {@link MutableElementHandle#remove() removed} during
 * iteration.
 * 
 * ElementSpliterators are {@link #trySplit() splittable} just as Spliterators are, though the added functionality (particularly
 * {@link MutableElementHandle#remove()}) may be disabled for split spliterators.
 * 
 * @param <E> The type of values that this MutableElementSpliterator provides
 */
public interface MutableElementSpliterator<E> extends ElementSpliterator<E> {
	/**
	 * Like {@link #tryAdvanceElement(Consumer)}, but provides a mutable element handle
	 * 
	 * @param action The action to perform on the element
	 * @return false if no element was available
	 */
	boolean tryAdvanceElementM(Consumer<? super MutableElementHandle<E>> action);

	/**
	 * Like {@link #tryReverseElement(Consumer)}, but provides a mutable element handle
	 * 
	 * @param action The action to perform on the element
	 * @return false if no element was available
	 */
	boolean tryReverseElementM(Consumer<? super MutableElementHandle<E>> action);

	/**
	 * Operates on each element remaining in this MutableElementSpliterator
	 * 
	 * @param action The action to perform on each element
	 */
	default void forEachElementM(Consumer<? super MutableElementHandle<E>> action) {
		while (tryAdvanceElementM(action)) {
		}
	}

	/**
	 * Operates on each previous element remaining in this MutableElementSpliterator
	 * 
	 * @param action The action to perform on each element
	 */
	default void forEachElementReverseM(Consumer<? super MutableElementHandle<E>> action) {
		while (tryReverseElementM(action)) {
		}
	}

	@Override
	MutableElementSpliterator<E> trySplit();

	@Override
	default MutableElementSpliterator<E> reverse() {
		return new ReversedMutableSpliterator<>(this);
	}

	/** @return An immutable spliterator backed by this spliterator */
	default ElementSpliterator<E> immutable() {
		return new ImmutableElementSpliterator<>(this);
	}

	/**
	 * @param <E> The type for the spliterator
	 * @return An empty MutableElementSpliterator of the given type
	 */
	static <E> MutableElementSpliterator<E> empty() {
		return new EmptyMutableSpliterator<>();
	}

	interface ElementSpliteratorMap<E, T> {
		TypeToken<T> getType();

		T map(E value);

		E reverse(T value);

		String filterEnabled(MutableElementHandle<E> el);

		String filterRemove(MutableElementHandle<E> sourceEl);

		default boolean canFilterValues() {
			return true;
		}
		boolean test(E srcValue);
		default boolean test(MutableElementHandle<E> el) {
			return test(el.get());
		}

		default String filterAccept(T value) {
			return null;
		}

		default long filterEstimatedSize(long srcSize) {
			return srcSize;
		}

		default long filterExactSize(long srcSize) {
			return -1;
		}

		default int modifyCharacteristics(int srcChars) {
			return srcChars;
		}

		default Comparator<? super T> mapComparator(Comparator<? super E> srcCompare) {
			return null;
		}
	}

	default <T> MutableElementSpliterator<T> map(ElementSpliteratorMap<E, T> map) {
		return new MappedElementSpliterator<>(this, map);
	}

	class ReversedMutableSpliterator<E> extends ReversedElementSpliterator<E> implements MutableElementSpliterator<E> {
		public ReversedMutableSpliterator(ElementSpliterator<E> wrap) {
			super(wrap);
		}

		@Override
		protected MutableElementSpliterator<E> getWrapped() {
			return (MutableElementSpliterator<E>) super.getWrapped();
		}

		@Override
		public boolean tryAdvanceElementM(Consumer<? super MutableElementHandle<E>> action) {
			return getWrapped().tryReverseElementM(el -> action.accept(el.reverse()));
		}

		@Override
		public boolean tryReverseElementM(Consumer<? super MutableElementHandle<E>> action) {
			return getWrapped().tryAdvanceElementM(el -> action.accept(el.reverse()));
		}

		@Override
		public MutableElementSpliterator<E> reverse() {
			return getWrapped();
		}

		@Override
		public MutableElementSpliterator<E> trySplit() {
			MutableElementSpliterator<E> split = getWrapped().trySplit();
			return split == null ? null : new ReversedMutableSpliterator<>(split);
		}
	}

	class ImmutableElementSpliterator<E> implements ElementSpliterator<E> {
		private final MutableElementSpliterator<E> theWrapped;

		public ImmutableElementSpliterator(MutableElementSpliterator<E> wrapped) {
			theWrapped = wrapped;
		}

		protected MutableElementSpliterator<E> getWrapped() {
			return theWrapped;
		}

		@Override
		public boolean tryAdvanceElement(Consumer<? super ElementHandle<E>> action) {
			return theWrapped.tryAdvanceElement(action);
		}

		@Override
		public boolean tryReverseElement(Consumer<? super ElementHandle<E>> action) {
			return theWrapped.tryAdvanceElement(action);
		}

		@Override
		public ElementSpliterator<E> trySplit() {
			MutableElementSpliterator<E> split = theWrapped.trySplit();
			return split == null ? null : split.immutable();
		}

		@Override
		public long estimateSize() {
			return theWrapped.estimateSize();
		}

		@Override
		public long getExactSizeIfKnown() {
			return theWrapped.getExactSizeIfKnown();
		}

		@Override
		public int characteristics() {
			return theWrapped.characteristics();
		}

		@Override
		public Comparator<? super E> getComparator() {
			return theWrapped.getComparator();
		}
	}

	class EmptyMutableSpliterator<E> extends EmptyElementSpliterator<E> implements MutableElementSpliterator<E> {
		@Override
		public boolean tryAdvanceElementM(Consumer<? super MutableElementHandle<E>> action) {
			return false;
		}

		@Override
		public boolean tryReverseElementM(Consumer<? super MutableElementHandle<E>> action) {
			return false;
		}
	}

	/**
	 * An MutableElementSpliterator mapped by an {@link MutableElementSpliterator.ElementSpliteratorMap}
	 * 
	 * @param <E> The type of the source spliterator
	 * @param <T> The type of this spliterator
	 */
	class MappedElementSpliterator<E, T> implements MutableElementSpliterator<T> {
		private final MutableElementSpliterator<E> theSource;
		private final ElementSpliteratorMap<E, T> theMap;

		private TypeToken<T> theType;
		private final MappedElement<E, T> theElement;

		public MappedElementSpliterator(MutableElementSpliterator<E> source, ElementSpliteratorMap<E, T> map) {
			theSource = source;
			theMap = map;
			theElement = createElement();
		}

		protected MutableElementSpliterator<E> getSource() {
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
		public boolean tryAdvanceElement(Consumer<? super MutableElementHandle<T>> action) {
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
		public void forEachElement(Consumer<? super MutableElementHandle<T>> action) {
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
				return MutableElementSpliterator.super.tryAdvance(action);
		}

		@Override
		public void forEachRemaining(Consumer<? super T> action) {
			if (theMap.canFilterValues()) {
				theSource.forEachRemaining(v -> {
					if (theMap.test(v))
						action.accept(theMap.map(v));
				});
			} else
				MutableElementSpliterator.super.forEachRemaining(action);
		}

		@Override
		public MutableElementSpliterator<T> trySplit() {
			MutableElementSpliterator<E> split = theSource.trySplit();
			return split == null ? null : split.map(theMap);
		}

		@Override
		public Spliterator<T> immutable() {
			if (theMap.canFilterValues()) {
				Spliterator<E> srcSplit = theSource.immutable();
				return new MappedSpliterator<>(srcSplit, theMap);
			} else
				return MutableElementSpliterator.super.immutable();
		}

		protected static class MappedElement<E, T> implements MutableElementHandle<T> {
			private MutableElementHandle<E> theSourceEl;
			private final ElementSpliteratorMap<E, T> theMap;
			private final Supplier<TypeToken<T>> theType;
			private boolean isAccepted;

			protected MappedElement(ElementSpliteratorMap<E, T> map, Supplier<TypeToken<T>> type) {
				theMap = map;
				theType = type;
			}

			protected void setSource(MutableElementHandle<E> sourceEl) {
				isAccepted = theMap.test(sourceEl);
				theSourceEl = isAccepted ? sourceEl : null;
			}

			protected MutableElementHandle<E> getSourceEl() {
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
					return preFilter;
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
			public void remove(Object cause) throws UnsupportedOperationException {
				String msg = theMap.filterRemove(theSourceEl);
				if (msg != null)
					throw new UnsupportedOperationException(msg);
				theSourceEl.remove(cause);
			}

			@Override
			public String canAdd(T value, boolean before) {
				String preFilter = theMap.filterAccept(value);
				if (preFilter != null)
					return preFilter;
				return theSourceEl.canAdd(theMap.reverse(value), before);
			}

			@Override
			public void add(T value, boolean before, Object cause) throws UnsupportedOperationException, IllegalArgumentException {
				String preFilter = theMap.filterAccept(value);
				if (preFilter != null)
					throw new IllegalArgumentException(preFilter);
				theSourceEl.set(theMap.reverse(value), cause);
			}

			@Override
			public String toString() {
				return String.valueOf(get());
			}
		}
	}

	/**
	 * A Spliterator mapped by an {@link MutableElementSpliterator.ElementSpliteratorMap}
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

		protected Spliterator<T> getSource() {
			return theSource;
		}

		protected ElementSpliteratorMap<T, E> getMap() {
			return theMap;
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
	 * A MutableElementSpliterator whose elements are the result of some filter-map operation on a vanilla {@link Spliterator}'s elements
	 * 
	 * @param <T> The type of elements in the wrapped Spliterator
	 * @param <V> The type of this MutableElementSpliterator's elements
	 */
	class SimpleSpliterator<T, V> implements MutableElementSpliterator<V> {
		private final Spliterator<T> theWrapped;
		private final TypeToken<V> theType;
		private final Supplier<? extends Function<? super T, ? extends MutableElementHandle<V>>> theMap;
		private final Function<? super T, ? extends MutableElementHandle<V>> theInstanceMap;

		public SimpleSpliterator(Spliterator<T> wrap, TypeToken<V> type,
			Supplier<? extends Function<? super T, ? extends MutableElementHandle<V>>> map) {
			theWrapped = wrap;
			theType = type;
			theMap = map;
			theInstanceMap = theMap.get();
		}

		protected Spliterator<T> getWrapped() {
			return theWrapped;
		}

		protected Supplier<? extends Function<? super T, ? extends MutableElementHandle<V>>> getMap() {
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
		public boolean tryAdvanceElement(Consumer<? super MutableElementHandle<V>> action) {
			boolean[] passed = new boolean[1];
			while (!passed[0] && theWrapped.tryAdvance(el -> {
				MutableElementHandle<V> mapped = theInstanceMap.apply(el);
				if (mapped != null) {
					passed[0] = true;
					action.accept(mapped);
				}
			})) {
			}
			return passed[0];
		}

		@Override
		public void forEachElement(Consumer<? super MutableElementHandle<V>> action) {
			theWrapped.forEachRemaining(el -> {
				MutableElementHandle<V> mapped = theInstanceMap.apply(el);
				if (mapped != null)
					action.accept(mapped);
			});
		}

		@Override
		public MutableElementSpliterator<V> trySplit() {
			Spliterator<T> split = theWrapped.trySplit();
			if (split == null)
				return null;
			return new SimpleSpliterator<>(split, theType, theMap);
		}
	}

	/**
	 * A MutableElementSpliterator whose elements are the result of some filter-map operation on another MutableElementSpliterator's elements
	 * 
	 * @param <T> The type of elements in the wrapped MutableElementSpliterator
	 * @param <V> The type of this MutableElementSpliterator's elements
	 */
	class WrappingSpliterator<T, V> implements MutableElementSpliterator<V> {
		private final MutableElementSpliterator<? extends T> theWrapped;
		private final TypeToken<V> theType;
		private final Supplier<? extends Function<? super MutableElementHandle<? extends T>, ? extends MutableElementHandle<V>>> theMap;
		private final Function<? super MutableElementHandle<? extends T>, ? extends MutableElementHandle<V>> theInstanceMap;

		public WrappingSpliterator(MutableElementSpliterator<? extends T> wrap, TypeToken<V> type,
			Supplier<? extends Function<? super MutableElementHandle<? extends T>, ? extends MutableElementHandle<V>>> map) {
			theWrapped = wrap;
			theType = type;
			theMap = map;
			theInstanceMap = theMap.get();
		}

		@Override
		public TypeToken<V> getType() {
			return theType;
		}

		protected MutableElementSpliterator<? extends T> getWrapped() {
			return theWrapped;
		}

		protected Supplier<? extends Function<? super MutableElementHandle<? extends T>, ? extends MutableElementHandle<V>>> getMap() {
			return theMap;
		}

		protected Function<? super MutableElementHandle<? extends T>, ? extends MutableElementHandle<V>> getInstanceMap() {
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
		public boolean tryAdvanceElement(Consumer<? super MutableElementHandle<V>> action) {
			boolean[] passed = new boolean[1];
			while (!passed[0] && theWrapped.tryAdvanceElement(el -> {
				MutableElementHandle<V> mapped = theInstanceMap.apply(el);
				if (mapped != null) {
					passed[0] = true;
					action.accept(mapped);
				}
			})) {
			}
			return passed[0];
		}

		@Override
		public void forEachElement(Consumer<? super MutableElementHandle<V>> action) {
			theWrapped.forEachElement(el -> {
				MutableElementHandle<V> mapped = theInstanceMap.apply(el);
				if (mapped != null)
					action.accept(mapped);
			});
		}

		@Override
		public MutableElementSpliterator<V> trySplit() {
			MutableElementSpliterator<? extends T> wrapSplit = theWrapped.trySplit();
			if (wrapSplit == null)
				return null;
			return new WrappingSpliterator<>(wrapSplit, theType, theMap);
		}
	}

	/**
	 * An element returned from {@link MutableElementSpliterator.WrappingSpliterator}
	 * 
	 * @param <T> The type of value in the element wrapped by this element
	 * @param <V> The type of this element
	 */
	abstract class WrappingElement<T, V> implements MutableElementHandle<V> {
		private final TypeToken<V> theType;
		private final MutableElementHandle<? extends T>[] theWrapped;

		public WrappingElement(TypeToken<V> type, MutableElementHandle<? extends T>[] wrapped) {
			theType = type;
			theWrapped = wrapped;
		}

		protected MutableElementHandle<? extends T> getWrapped() {
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
		public void remove(Object cause) {
			theWrapped[0].remove(cause);
		}
	}

	/**
	 * An iterator backed by an {@link MutableElementSpliterator}
	 *
	 * @param <E> The type of elements to iterate over
	 */
	public static class SpliteratorBetterator<E> implements Betterator<E> {
		private final MutableElementSpliterator<E> theSpliterator;

		private boolean isNextCached;
		private boolean isDone;
		private MutableElementHandle<? extends E> cachedNext;

		/** @param spliterator The spliterator to back this iterator */
		public SpliteratorBetterator(MutableElementSpliterator<E> spliterator) {
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
			cachedNext.remove(null);
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
			return ((MutableElementHandle<E>) cachedNext).isAcceptable(value);
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
			return ((MutableElementHandle<E>) cachedNext).set(value, cause);
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
