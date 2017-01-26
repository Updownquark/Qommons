package org.qommons.collect;

import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Betterator<E> extends Iterator<E> {
	interface Betterable<E> extends Iterable<E> {
		@Override
		Betterator<E> iterator();
	}

	default <V> Betterator<V> map(Function<? super E, V> map) {}

	default Betterator<E> filter(Predicate<? super E> filter) {}

	default <V> Betterator<V> filterMap(Function<? super E, V> map) {}

	class MappedBetterator<E, V> implements Betterator<V> {
		private final Betterator<E> theWrapped;
		private final Function<? super E, V> theMap;

		public MappedBetterator(Betterator<E> wrap, Function<? super E, V> map) {
			theWrapped = wrap;
			theMap = map;
		}
	}

	class FilteredBetterator<E> implements Betterator<E> {
		private final Betterator<E> theWrapped;
		private final Predicate<? super E> theFilter;

		private E theNextReturn;
		private boolean calledHasNext;

		public FilteredBetterator(Betterator<E> wrap, Predicate<? super E> filter) {
			theWrapped = wrap;
			theFilter = filter;
		}

		@Override
		public boolean hasNext() {
			calledHasNext = true;
			while (theNextReturn == null && theWrapped.hasNext()) {
				theNextReturn = theWrapped.next();
				if (!theFilter.test(theNextReturn))
					theNextReturn = null;
			}
			return theNextReturn != null;
		}

		@Override
		public E next() {
			if (!calledHasNext && !hasNext()) {
				theWrapped.next(); // Let the wrapped iterator throw the exception
				throw new java.util.NoSuchElementException();
			}
			E ret = theNextReturn;
			theNextReturn = null;
			return ret;
		}

		@Override
		public void remove() {
			theWrapped.remove();
		}
	}

	class FilterMappedBetterator<E, V> implements Betterator<V> {
		private final Betterator<E> theWrapped;
		private final Function<? super E, V> theMap;

		private V theNextReturn;
		private boolean calledHasNext;

		public FilterMappedBetterator(Betterator<E> wrap, Function<? super E, V> map) {
			theWrapped = wrap;
			theMap = map;
		}

		@Override
		public boolean hasNext() {
			calledHasNext = true;
			while (theNextReturn == null && theWrapped.hasNext())
				theNextReturn = theMap.apply(theWrapped.next());
			return theNextReturn != null;
		}

		@Override
		public V next() {
			if (!calledHasNext && !hasNext()) {
				theWrapped.next(); // Let the wrapped iterator throw the exception
				throw new java.util.NoSuchElementException();
			}
			V ret = theNextReturn;
			theNextReturn = null;
			return ret;
		}

		@Override
		public void remove() {
			theWrapped.remove();
		}
	}
}
