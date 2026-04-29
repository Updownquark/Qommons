package org.qommons.collect;

import java.util.Comparator;
import java.util.function.Function;

/**
 * A {@link BetterSortedSet} that is the result of a mapping function applied to a different {@link BetterSortedSet}
 * 
 * @param <S> The type of the source set
 * @param <T> The type of this set
 */
public class MappedBetterSortedSet<S, T> extends MappedBetterSortedList<S, T> implements BetterSortedSet<T> {
	/**
	 * @param source The source set to wrap
	 * @param map The mapping function to produce values of this set from values of the source set
	 * @param reverse The optional mapping function to produce values of the source set from values in this set. Providing this enables the
	 *        {@link #getElement(Object, boolean)} method and other features that use it, which will otherwise fail (with nulls or false
	 *        booleans).
	 * @param sorting The sorting for values in this set
	 */
	public MappedBetterSortedSet(BetterSortedSet<S> source, Function<? super S, T> map, Function<? super T, ? extends S> reverse,
		Comparator<? super T> sorting) {
		super(source, map, reverse, sorting);
	}

	@Override
	protected BetterSortedSet<S> getSource() {
		return (BetterSortedSet<S>) super.getSource();
	}
}
