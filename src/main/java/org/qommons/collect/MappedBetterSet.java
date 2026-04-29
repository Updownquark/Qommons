package org.qommons.collect;

import java.util.function.Function;
import java.util.function.Predicate;

import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * {@link BetterList} implementation of {@link MappedList}
 * 
 * @param <S> The type of the source list
 * @param <T> The type of this list
 */
public class MappedBetterSet<S, T> extends MappedBetterCollection<S, T> implements BetterSet<T> {
	private final Predicate<Object> theContainment;

	/**
	 * @param wrapped The source list to map
	 * @param map The mapping function
	 * @param reverse The reverse function (used for {@link #getElement(Object, boolean)} and modification)
	 * @param containment
	 */
	public MappedBetterSet(BetterSet<S> wrapped, Function<? super S, T> map, Predicate<Object> containment,
		Function<? super T, ? extends S> reverse) {
		super(wrapped, map, reverse);
		theContainment = containment;
	}

	@Override
	protected BetterSet<S> getSource() {
		return (BetterSet<S>) super.getSource();
	}

	protected Predicate<Object> getContainment() {
		return theContainment;
	}

	@Override
	public boolean contains(Object o) {
		if (theContainment != null)
			return theContainment.test(o);
		else
			return super.contains(o);
	}

	@Override
	public CollectionElement<T> getOrAdd(T value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
		if (getReverse() == null)
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		return wrap(getSource().getOrAdd(getReverse().apply(value), after, before, first, preAdd, postAdd));
	}

	@Override
	public boolean isConsistent(ElementId element) {
		return getSource().isConsistent(element);
	}

	@Override
	public boolean checkConsistency() {
		return getSource().checkConsistency();
	}

	@Override
	public <X> boolean repair(ElementId element, RepairListener<T, X> listener) {
		return getSource().repair(element, listener == null ? null : new MappedRepairListener<>(listener, getMap()));
	}

	@Override
	public <X> boolean repair(RepairListener<T, X> listener) {
		return getSource().repair(listener == null ? null : new MappedRepairListener<>(listener, getMap()));
	}

	class MappedRepairListener<X> implements RepairListener<S, X> {
		private final RepairListener<T, X> theSourceListener;
		private final Function<? super S, ? extends T> theMap;

		MappedRepairListener(RepairListener<T, X> sourceListener, Function<? super S, ? extends T> map) {
			theSourceListener = sourceListener;
			theMap = map;
		}

		@Override
		public X removed(CollectionElement<S> element) {
			return theSourceListener.removed(wrap(element));
		}

		@Override
		public void disposed(S value, X data) {
			theSourceListener.disposed(theMap.apply(value), data);
		}

		@Override
		public void transferred(CollectionElement<S> element, X data) {
			theSourceListener.transferred(wrap(element), data);
		}
	}
}
