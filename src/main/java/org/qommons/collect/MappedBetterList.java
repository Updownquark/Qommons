package org.qommons.collect;

import java.util.function.Function;

/**
 * {@link BetterList} implementation of {@link MappedList}
 * 
 * @param <S> The type of the source list
 * @param <T> The type of this list
 */
public class MappedBetterList<S, T> extends MappedBetterCollection<S, T> implements BetterList<T> {
	private Object theIdentity;

	/**
	 * @param wrapped The source list to map
	 * @param map The mapping function
	 * @param reverse The reverse function (used for {@link #getElement(Object, boolean)} and modification operations)
	 */
	public MappedBetterList(BetterList<S> wrapped, Function<? super S, T> map, Function<? super T, ? extends S> reverse) {
		super(wrapped, map, reverse);
	}

	@Override
	protected BetterList<S> getSource() {
		return (BetterList<S>) super.getSource();
	}

	@Override
	public ListElement<T> getElement(int index) throws IndexOutOfBoundsException {
		return wrap(getSource().getElement(index));
	}

	@Override
	public boolean isContentControlled() {
		return getSource().isContentControlled();
	}

	@Override
	public ListElement<T> getElement(ElementId id) {
		return (ListElement<T>) super.getElement(id);
	}

	@Override
	public ListElement<T> getElement(T value, boolean first) {
		return (ListElement<T>) super.getElement(value, first);
	}

	@Override
	public ListElement<T> getTerminalElement(boolean first) {
		return (ListElement<T>) super.getTerminalElement(first);
	}

	@Override
	public MutableListElement<T> mutableElement(ElementId id) {
		return (MutableListElement<T>) super.mutableElement(id);
	}

	@Override
	public ListElement<T> addElement(T value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		return (ListElement<T>) super.addElement(value, after, before, first);
	}

	@Override
	public ListElement<T> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		return (ListElement<T>) super.move(valueEl, after, before, first, afterRemove);
	}

	@Override
	public BetterList<T> subList(int fromIndex, int toIndex) {
		return BetterList.super.subList(fromIndex, toIndex);
	}

	@Override
	public void removeRange(int fromIndex, int toIndex) {
		getSource().removeRange(fromIndex, toIndex);
	}

	@Override
	protected ListElement<T> wrap(CollectionElement<S> sourceEl) {
		return sourceEl == null ? null : new MappedListElement((ListElement<S>) sourceEl);
	}

	class MappedListElement extends MappedElement implements ListElement<T> {
		MappedListElement(ListElement<S> source) {
			super(source);
		}

		@Override
		protected ListElement<S> getSource() {
			return (ListElement<S>) super.getSource();
		}

		@Override
		public ListElement<T> getAdjacent(boolean next) {
			return (ListElement<T>) super.getAdjacent(next);
		}

		@Override
		public int getElementsBefore() {
			return getSource().getElementsBefore();
		}

		@Override
		public int getElementsAfter() {
			return getSource().getElementsAfter();
		}
	}

	class MappedMutableListElement extends MappedMutableElement implements MutableListElement<T> {
		MappedMutableListElement(MutableListElement<S> source) {
			super(source);
		}

		@Override
		protected MutableListElement<S> getSource() {
			return (MutableListElement<S>) super.getSource();
		}

		@Override
		public MutableListElement<T> getAdjacent(boolean next) {
			return (MutableListElement<T>) super.getAdjacent(next);
		}

		@Override
		public int getElementsBefore() {
			return getSource().getElementsBefore();
		}

		@Override
		public int getElementsAfter() {
			return getSource().getElementsAfter();
		}
	}
}
