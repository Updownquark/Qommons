package org.qommons.collect;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * <p>
 * Stores a set of values uniquely by identity.
 * </p>
 * <p>
 * This class implements {@link UpdatableSet}, but since an object's identity can never change, this is just for convenience and the
 * {@link #update(Object) update} method just delegates to {@link #contains(Object) contains}.
 * </p>
 * 
 * @param <E> The type of values in the set
 */
public class IdentityHashSet<E> implements UpdatableSet<E> {
	private IdentityHashMap<E, E> backing = new IdentityHashMap<>();

	/** Creates an empty set */
	public IdentityHashSet() {}

	/**
	 * Creates a set with some initial values
	 * 
	 * @param collection The initial values for the set
	 */
	public IdentityHashSet(Collection<? extends E> collection) {
		addAll(collection);
	}

	@Override
	public int size() {
		return backing.size();
	}

	@Override
	public boolean isEmpty() {
		return backing.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return backing.containsKey(o);
	}

	@Override
	public Iterator<E> iterator() {
		return backing.values().iterator();
	}

	@Override
	public Object[] toArray() {
		return backing.values().toArray();
	}

	@Override
	public <T> T[] toArray(T[] array) {
		return backing.values().toArray(array);
	}

	@Override
	public boolean containsAll(Collection<?> coll) {
		for (Object value : coll) {
			if (!contains(value)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean add(E o) {
		return backing.put(o, o) == null;
	}

	@Override
	public boolean addAll(Collection<? extends E> coll) {
		boolean result = false;
		for (E e : coll) {
			result |= backing.put(e, e) != e;
		}
		return result;
	}

	@Override
	public boolean remove(Object o) {
		return backing.remove(o) != null;
	}

	@Override
	public boolean retainAll(Collection<?> coll) {
		boolean ret = false;
		Iterator<?> iter = iterator();
		while (iter.hasNext()) {
			if (!coll.contains(iter.next())) {
				iter.remove();
				ret = true;
			}
		}
		return ret;
	}

	@Override
	public boolean removeAll(Collection<?> coll) {
		boolean ret = false;
		for (Object value : coll) {
			ret |= remove(value);
		}
		return ret;
	}

	@Override
	public void clear() {
		backing.clear();
	}

	@Override
	public ElementUpdateResult update(E value) {
		return contains(value) ? ElementUpdateResult.NotChanged : ElementUpdateResult.NotFound;
	}

	@Override
	public int hashCode() {
		int ret = 0;
		for (Object obj : this) {
			ret = ret * 3 + obj.hashCode();
		}
		return ret;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Set)) {
			return false;
		}
		return containsAll((Set<?>) o) && ((Set<?>) o).containsAll(this);
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder("{");
		for (Object obj : this) {
			if (ret.length() > 1) {
				ret.append(", ");
			}
			ret.append(obj);
		}
		ret.append("}");
		return ret.toString();
	}
}
