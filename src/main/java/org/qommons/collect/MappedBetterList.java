package org.qommons.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;

import org.qommons.Identifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * {@link BetterList} implementation of {@link MappedList}
 * 
 * @param <S> The type of the source list
 * @param <T> The type of this list
 */
public class MappedBetterList<S, T> extends MappedList<S, T> implements BetterList<T> {
	private Object theIdentity;
	private final Function<? super T, ? extends S> theReverse;

	/**
	 * @param wrapped The source list to map
	 * @param map The mapping function
	 * @param reverse The reverse function (only used for {@link #getElement(Object, boolean)})
	 */
	public MappedBetterList(BetterList<S> wrapped, Function<? super S, T> map, Function<? super T, ? extends S> reverse) {
		super(wrapped, map);
		theReverse = reverse;
	}

	@Override
	protected BetterList<S> getSource() {
		return (BetterList<S>) super.getSource();
	}

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
		return StdMsg.UNSUPPORTED_OPERATION;
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
	public Transaction lock(boolean write, Object cause) {
		return getSource().lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return getSource().tryLock(write, cause);
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
	public ListElement<T> getElement(int index) throws IndexOutOfBoundsException {
		return map(getSource().getElement(index), getMap());
	}

	@Override
	public boolean isContentControlled() {
		return getSource().isContentControlled();
	}

	@Override
	public ListElement<T> getElement(ElementId id) {
		return map(getSource().getElement(id), getMap());
	}

	@Override
	public ListElement<T> getElement(T value, boolean first) {
		if (theReverse == null)
			return null;
		return map(getSource().getElement(theReverse.apply(value), first), getMap());
	}

	@Override
	public ListElement<T> getTerminalElement(boolean first) {
		return map(getSource().getTerminalElement(first), getMap());
	}

	@Override
	public MutableListElement<T> mutableElement(ElementId id) {
		return new MappedMutableElement<>(getSource().mutableElement(id), getMap());
	}

	@Override
	public ListElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public ListElement<T> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		return map(getSource().move(valueEl, after, before, first, afterRemove), getMap());
	}

	@Override
	public BetterList<T> subList(int fromIndex, int toIndex) {
		return BetterList.super.subList(fromIndex, toIndex);
	}

	@Override
	public void removeRange(int fromIndex, int toIndex) {
		super.removeRange(fromIndex, toIndex);
	}

	static <S, T> ListElement<T> map(ListElement<S> sourceEl, Function<? super S, ? extends T> map) {
		return sourceEl == null ? null : new MappedElement<>(sourceEl, map);
	}

	static class MappedElement<S, T> implements ListElement<T> {
		private final ListElement<S> theSource;
		private final Function<? super S, ? extends T> theMap;

		MappedElement(ListElement<S> source, Function<? super S, ? extends T> map) {
			theSource = source;
			theMap = map;
		}

		protected ListElement<S> getSource() {
			return theSource;
		}

		protected Function<? super S, ? extends T> getMap() {
			return theMap;
		}

		@Override
		public ElementId getElementId() {
			return theSource.getElementId();
		}

		@Override
		public T get() {
			return theMap.apply(theSource.get());
		}

		@Override
		public ListElement<T> getAdjacent(boolean next) {
			return map(theSource.getAdjacent(next), theMap);
		}

		@Override
		public int getElementsBefore() {
			return theSource.getElementsBefore();
		}

		@Override
		public int getElementsAfter() {
			return theSource.getElementsAfter();
		}

		@Override
		public String toString() {
			return theSource.toString();
		}
	}

	static class MappedMutableElement<S, T> extends MappedElement<S, T> implements MutableListElement<T> {
		MappedMutableElement(MutableListElement<S> source, Function<? super S, ? extends T> map) {
			super(source, map);
		}

		@Override
		protected MutableListElement<S> getSource() {
			return (MutableListElement<S>) super.getSource();
		}

		@Override
		public MutableListElement<T> getAdjacent(boolean next) {
			MutableListElement<S> sourceAdj = getSource().getAdjacent(next);
			return sourceAdj == null ? null : new MappedMutableElement<>(sourceAdj, getMap());
		}

		@Override
		public String isEnabled() {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public String isAcceptable(T value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void set(T value) throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canRemove() {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}
	}
}
