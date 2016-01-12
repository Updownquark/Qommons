package org.qommons.tree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface Tree<V, T extends Tree<V, T, C>, C extends Collection<T>> {
	V getValue();

	C getChildren();

	default int size() {
		int ret = 1;
		for (T child : getChildren())
			ret += child.size();
		return ret;
	}

	default Iterable<T> depthFirst() {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private boolean hasReturnedSelf;
					private Iterator<T> childIterator = getChildren().iterator();
					private Iterator<T> descendantIterator;

					@Override
					public boolean hasNext() {
						if (!hasReturnedSelf)
							return true;
						while ((descendantIterator == null || !descendantIterator.hasNext()) && childIterator.hasNext())
							descendantIterator = childIterator.next().depthFirst().iterator();
						return descendantIterator != null && descendantIterator.hasNext();
					}

					@Override
					public T next() {
						if (!hasReturnedSelf) {
							hasReturnedSelf = true;
							return (T) Tree.this;
						}
						if ((descendantIterator == null || !descendantIterator.hasNext()) && !hasNext())
							throw new NoSuchElementException();
						return descendantIterator.next();
					}
				};
			}
		};
	}

	default <V2, T2 extends Tree<V2, T2, Collection<T2>>> T2 map(Function<? super V, V2> map) {
		Tree<V, T, C> outer = this;
		return (T2) new Tree<V2, T2, Collection<T2>>() {
			@Override
			public V2 getValue() {
				return map.apply(outer.getValue());
			}

			@Override
			public Collection<T2> getChildren() {
				return outer.getChildren().stream().map(child -> (T2) (Tree<?, ?, ?>) child.map(map)).collect(Collectors.toList());
			}
		};
	}

	default List<T> find(V value) {
		return find(v -> value.equals(v));
	}

	default List<T> find(Predicate<? super V> test) {
		ArrayList<T> ret = new ArrayList<>();
		if (findInto(test, ret)) {
			ret.trimToSize();
			return Collections.unmodifiableList(ret);
		}
		return null;
	}

	default boolean findInto(Predicate<? super V> test, List<T> path) {
		path.add((T) this);
		if (test.test(getValue()))
			return true;
		for (T child : getChildren()) {
			if (child.findInto(test, path))
				return true;
		}
		path.remove(path.size() - 1);
		return false;
	}
}
