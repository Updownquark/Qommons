package org.qommons.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

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
public class MappedBetterSet<S, T> extends MappedSet<S, T> implements BetterSet<T> {
	private Object theIdentity;
	private final Function<? super T, ? extends S> theReverse;

	/**
	 * @param wrapped The source list to map
	 * @param map The mapping function
	 * @param reverse The reverse function (only used for {@link #getElement(Object, boolean)})
	 * @param containment
	 */
	public MappedBetterSet(BetterSet<S> wrapped, Function<? super S, T> map, Predicate<Object> containment,
		Function<? super T, ? extends S> reverse) {
		super(wrapped, map, containment);
		theReverse = reverse;
	}

	@Override
	protected BetterList<S> getSource() {
		return (BetterList<S>) super.getSource();
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
	public CollectionElement<T> getElement(ElementId id) {
		return map(getSource().getElement(id), getMap());
	}

	@Override
	public CollectionElement<T> getElement(T value, boolean first) {
		if (theReverse == null)
			return null;
		return map(getSource().getElement(theReverse.apply(value), first), getMap());
	}

	@Override
	public CollectionElement<T> getTerminalElement(boolean first) {
		return map(getSource().getTerminalElement(first), getMap());
	}

	@Override
	public MutableCollectionElement<T> mutableElement(ElementId id) {
		return new MappedMutableElement<>(getSource().mutableElement(id), getMap());
	}

	@Override
	public CollectionElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public CollectionElement<T> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		return map(getSource().move(valueEl, after, before, first, afterRemove), getMap());
	}

	@Override
	public CollectionElement<T> getOrAdd(T value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public boolean isConsistent(ElementId element) {
		return true;
	}

	@Override
	public boolean checkConsistency() {
		return false;
	}

	@Override
	public <X> boolean repair(ElementId element, RepairListener<T, X> listener) {
		return false;
	}

	@Override
	public <X> boolean repair(RepairListener<T, X> listener) {
		return false;
	}

	static <S, T> CollectionElement<T> map(CollectionElement<S> sourceEl, Function<? super S, ? extends T> map) {
		return sourceEl == null ? null : new MappedElement<>(sourceEl, map);
	}

	static class MappedElement<S, T> implements CollectionElement<T> {
		private final CollectionElement<S> theSource;
		private final Function<? super S, ? extends T> theMap;

		MappedElement(CollectionElement<S> source, Function<? super S, ? extends T> map) {
			theSource = source;
			theMap = map;
		}

		protected CollectionElement<S> getSource() {
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
		public CollectionElement<T> getAdjacent(boolean next) {
			return map(theSource.getAdjacent(next), theMap);
		}

		@Override
		public String toString() {
			return theSource.toString();
		}
	}

	static class MappedMutableElement<S, T> extends MappedElement<S, T> implements MutableCollectionElement<T> {
		MappedMutableElement(MutableCollectionElement<S> source, Function<? super S, ? extends T> map) {
			super(source, map);
		}

		@Override
		protected MutableCollectionElement<S> getSource() {
			return (MutableCollectionElement<S>) super.getSource();
		}

		@Override
		public MutableCollectionElement<T> getAdjacent(boolean next) {
			MutableCollectionElement<S> sourceAdj = getSource().getAdjacent(next);
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
