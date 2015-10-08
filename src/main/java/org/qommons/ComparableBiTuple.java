package org.qommons;

/**
 * A bi-tuple for two comparable types
 *
 * @param <V1> The type of the first value
 * @param <V2> The type of the second value
 */
public class ComparableBiTuple<V1 extends Comparable<V1>, V2 extends Comparable<V2>> extends BiTuple<V1, V2>
	implements Comparable<ComparableBiTuple<V1, V2>> {

	/** @see BiTuple#BiTuple(Object, Object) */
	public ComparableBiTuple(V1 v1, V2 v2) {
		super(v1, v2);
	}

	@Override
	public int compareTo(ComparableBiTuple<V1, V2> o) {
		int compare = getValue1().compareTo(o.getValue1());
		if(compare != 0)
			return compare;
		compare = getValue2().compareTo(o.getValue2());
		return compare;
	}
}
