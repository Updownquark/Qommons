package org.qommons;

/**
 * A tri-tuple for three comparable types
 *
 * @param <V1> The type of the first value
 * @param <V2> The type of the second value
 * @param <V3> The type of the third value
 */
public class ComparableTriTuple<V1 extends Comparable<V1>, V2 extends Comparable<V2>, V3 extends Comparable<V3>>
	extends TriTuple<V1, V2, V3> implements Comparable<ComparableTriTuple<V1, V2, V3>> {

	/** @see TriTuple#TriTuple(Object, Object, Object) */
	public ComparableTriTuple(V1 v1, V2 v2, V3 v3) {
		super(v1, v2, v3);
	}

	@Override
	public int compareTo(ComparableTriTuple<V1, V2, V3> o) {
		int compare = getValue1().compareTo(o.getValue1());
		if(compare != 0)
			return compare;
		compare = getValue2().compareTo(o.getValue2());
		if(compare != 0)
			return compare;
		compare = getValue3().compareTo(o.getValue3());
		return compare;
	}
}
