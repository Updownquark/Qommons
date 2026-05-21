package org.qommons.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

import org.qommons.Identifiable;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * {@link BetterList} implementation of {@link MappedList}
 * 
 * @param <S> The type of the source list
 * @param <T> The type of this list
 */
public class MappedBetterCollection<S, T> extends MappedCollection<S, T> implements BetterCollection<T> {
	/**
	 * Creates a mapped better collection
	 * 
	 * @param <S> The type of the source collection
	 * @param <T> The target type for the mapped collection
	 * @param source The source collection to map
	 * @param map The mapping function for source to target elements
	 * @param reverse The optional reverse function for target to source elements
	 * @return The mapped collections
	 */
	public static <S, T> MappedBetterCollection<S, T> map(BetterCollection<S> source, Function<? super S, T> map,
		Function<? super T, ? extends S> reverse) {
		if (source instanceof BetterSortedSet && reverse != null)
			return new MappedBetterSortedSet<>((BetterSortedSet<S>) source, map, reverse, null);
		else if (source instanceof BetterSortedList)
			return new MappedBetterSortedList<>((BetterSortedList<S>) source, map, reverse, null);
		else if (source instanceof BetterList)
			return new MappedBetterList<>((BetterList<S>) source, map, reverse);
		else if (source instanceof BetterSet)
			return new MappedBetterSet<>((BetterSet<S>) source, map, null, reverse);
		else
			return new MappedBetterCollection<>(source, map, reverse);
	}

	private Object theIdentity;
	private final Function<? super T, ? extends S> theReverse;

	/**
	 * @param wrapped The source collection to map
	 * @param map The mapping function
	 * @param reverse The reverse function (only used for {@link #getElement(Object, boolean)})
	 */
	public MappedBetterCollection(BetterCollection<S> wrapped, Function<? super S, T> map, Function<? super T, ? extends S> reverse) {
		super(wrapped, map);
		theReverse = reverse;
	}

	@Override
	protected BetterCollection<S> getSource() {
		return (BetterCollection<S>) super.getSource();
	}

	/** @return The reverse function to turn values from this collection into values for the source collection */
	protected Function<? super T, ? extends S> getReverse() {
		return theReverse;
	}

	@Override
	public BetterList<CollectionElement<T>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
		BetterList<CollectionElement<S>> sourceEls = getSource().getElementsBySource(sourceEl, sourceCollection);
		if (sourceEls.isEmpty())
			return BetterList.empty();
		else
			return BetterList.of(sourceEls.stream().map(el -> getElement(el.getElementId())));
	}

	@Override
	public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return BetterList.of(localElement);
		return getSource().getSourceElements(localElement, sourceCollection);
	}

	@Override
	public ElementId getEquivalentElement(ElementId equivalentEl) {
		return getSource().getEquivalentElement(equivalentEl);
	}

	@Override
	public String canAdd(T value, ElementId after, ElementId before) {
		if (theReverse == null)
			return StdMsg.UNSUPPORTED_OPERATION;
		else
			return getSource().canAdd(theReverse.apply(value), after, before);
	}

	@Override
	public String canMove(ElementId valueEl, ElementId after, ElementId before) {
		return getSource().canMove(valueEl, after, before);
	}

	@Override
	public long getStamp() {
		return getSource().getStamp();
	}

	@Override
	public Transaction lock(boolean tryOnly) {
		return getSource().lock(tryOnly);
	}

	@Override
	public Transaction lockWrite(boolean tryOnly, Object cause) {
		return getSource().lockWrite(tryOnly, cause);
	}

	@Override
	public CoreId getCoreId() {
		return getSource().getCoreId();
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return getSource().getThreadConstraint();
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return getSource().getCurrentCauses();
	}

	@Override
	public Object getIdentity() {
		if (theIdentity == null)
			theIdentity = Identifiable.wrap(getSource().getIdentity(), "map", getMap());
		return theIdentity;
	}

	@Override
	public Identifiable alias(String alias) {
		return this;
	}

	@Override
	public Set<String> getAliases() {
		return Collections.emptySet();
	}

	@Override
	public CollectionElement<T> getElement(ElementId id) {
		return wrap(getSource().getElement(id));
	}

	@Override
	public CollectionElement<T> getElement(T value, boolean first) {
		if (theReverse == null)
			return null;
		return wrap(getSource().getElement(theReverse.apply(value), first));
	}

	@Override
	public CollectionElement<T> getTerminalElement(boolean first) {
		return wrap(getSource().getTerminalElement(first));
	}

	@Override
	public MutableCollectionElement<T> mutableElement(ElementId id) {
		return wrapMutable(getSource().mutableElement(id));
	}

	@Override
	public CollectionElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		if (getReverse() == null)
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		return wrap(getSource().addElement(getReverse().apply(value), after, before, first));
	}

	@Override
	public CollectionElement<T> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		return wrap(getSource().move(valueEl, after, before, first, afterRemove));
	}

	/**
	 * @param sourceEl The source element
	 * @return The mapped target element
	 */
	protected CollectionElement<T> wrap(CollectionElement<S> sourceEl) {
		return sourceEl == null ? null : new MappedElement(sourceEl);
	}

	/**
	 * @param sourceEl The source mutable element
	 * @return The mapped target mutable element
	 */
	protected MutableCollectionElement<T> wrapMutable(MutableCollectionElement<S> sourceEl) {
		return sourceEl == null ? null : new MappedMutableElement(sourceEl);
	}

	/** Default implementation for a mapped collection element */
	protected class MappedElement implements CollectionElement<T> {
		private final CollectionElement<S> theSource;

		MappedElement(CollectionElement<S> source) {
			theSource = source;
		}

		/** @return The wrapped source element */
		protected CollectionElement<S> getSource() {
			return theSource;
		}

		@Override
		public ElementId getElementId() {
			return theSource.getElementId();
		}

		@Override
		public T get() {
			return getMap().apply(theSource.get());
		}

		@Override
		public CollectionElement<T> getAdjacent(boolean next) {
			return wrap(theSource.getAdjacent(next));
		}

		@Override
		public String toString() {
			return theSource.toString();
		}
	}

	/** Default implementation for a mapped mutable collection element */
	protected class MappedMutableElement extends MappedElement implements MutableCollectionElement<T> {
		MappedMutableElement(MutableCollectionElement<S> source) {
			super(source);
		}

		@Override
		protected MutableCollectionElement<S> getSource() {
			return (MutableCollectionElement<S>) super.getSource();
		}

		@Override
		public MutableCollectionElement<T> getAdjacent(boolean next) {
			MutableCollectionElement<S> sourceAdj = getSource().getAdjacent(next);
			return wrapMutable(sourceAdj);
		}

		@Override
		public String isEnabled() {
			// Can update even if no reverse available
			return getSource().isEnabled();
		}

		@Override
		public String isAcceptable(T value) {
			if (getMap().apply(getSource().get()) == value)
				return getSource().isAcceptable(getSource().get());
			else if (theReverse != null)
				return getSource().isAcceptable(theReverse.apply(value));
			else
				return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
			if (getMap().apply(getSource().get()) == value)
				getSource().set(getSource().get());
			else if (theReverse != null)
				getSource().set(theReverse.apply(value));
			else
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canRemove() {
			return getSource().canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			getSource().remove();
		}
	}
}
