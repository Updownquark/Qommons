package org.qommons.collect;

import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A simple collection whose elements are a map of another collection
 * 
 * @param <S> The type of the source collection
 * @param <T> The type of this set
 */
public class MappedSet<S, T> extends MappedCollection<S, T> implements Set<T> {
	private final Predicate<Object> theContainment;

	/**
	 * @param source The source collection to map
	 * @param map The mapping function
	 * @param containment The optional containment test to quickly determine whether this mapped set contains an object
	 */
	public MappedSet(Collection<S> source, Function<? super S, T> map, Predicate<Object> containment) {
		super(source, map);
		theContainment = containment;
	}

	@Override
	public boolean contains(Object o) {
		if (theContainment != null)
			return theContainment.test(o);
		else
			return super.contains(o);
	}
}
