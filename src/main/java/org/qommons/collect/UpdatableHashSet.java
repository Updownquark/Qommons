package org.qommons.collect;

import java.util.*;

/**
 * A hash implementation of {@link UpdatableSet}
 * 
 * @param <E> The type of values in the set
 */
public class UpdatableHashSet<E> extends AbstractSet<E> implements UpdatableSet<E> {
	/**
	 * A wrapper that caches a value's hash code so it can be found in a map after its internal data has changed such that its hash code may
	 * have changed
	 * 
	 * @param <E> The type of value in the wrapper
	 */
	public static class HashWrapper<E> {
		/** This wrapper's value */
		public final E value;
		int hashCode;

		/** @param value The value for the wrapper */
		public HashWrapper(E value) {
			this.value = value;
			updateHashCode();
		}

		/** Updates this wrapper's cached hash code from its value */
		public void updateHashCode() {
			hashCode = Objects.hashCode(value);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof HashWrapper)
				return Objects.equals(value, ((HashWrapper<?>) obj).value);
			else
				return false;
		}

		@Override
		public String toString() {
			return String.valueOf(value);
		}
	}

	private final LinkedHashMap<HashWrapper<E>, HashWrapper<E>> theWrapperSet;
	private final IdentityHashMap<E, HashWrapper<E>> theWrappersById;

	/** Creates the hash set */
	public UpdatableHashSet() {
		theWrapperSet = new LinkedHashMap<>();
		theWrappersById = new IdentityHashMap<>();
	}

	@Override
	public boolean update(E value) {
		HashWrapper<E> wrapper = theWrappersById.get(value);
		if (wrapper != null) {
			theWrapperSet.remove(wrapper);
			wrapper.updateHashCode();
			theWrapperSet.put(wrapper, wrapper);
			return true;
		}
		return false;
	}

	@Override
	public int size() {
		return theWrapperSet.size();
	}

	@Override
	public boolean isEmpty() {
		return theWrapperSet.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return theWrapperSet.containsKey(new HashWrapper<>(o));
	}

	@Override
	public Iterator<E> iterator() {
		Iterator<HashWrapper<E>> iter = theWrapperSet.keySet().iterator();
		return new Iterator<E>() {
			@Override
			public boolean hasNext() {
				return iter.hasNext();
			}

			@Override
			public E next() {
				return iter.next().value;
			}

			@Override
			public void remove() {
				iter.remove();
			}
		};
	}

	@Override
	public boolean add(E e) {
		HashWrapper<E> wrapper = new HashWrapper<>(e);
		if (!theWrapperSet.containsKey(wrapper)) {
			theWrapperSet.put(wrapper, wrapper);
			theWrappersById.put(e, wrapper);
			return true;
		}
		return false;
	}

	@Override
	public boolean remove(Object o) {
		HashWrapper<E> wrapper = theWrapperSet.remove(new HashWrapper<>(o));
		if (wrapper != null)
			theWrappersById.remove(wrapper.value);
		return wrapper != null;
	}

	@Override
	public void clear() {
		theWrapperSet.clear();
		theWrappersById.clear();
	}
}
