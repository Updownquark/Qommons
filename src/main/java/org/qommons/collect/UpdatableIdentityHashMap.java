package org.qommons.collect;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.qommons.collect.UpdatableSet.ElementUpdateResult;

/**
 * A convenience {@link IdentityHashMap} that implements {@link UpdatableMap}. Since keys cannot change their identity, this class does not
 * provide any additional functionality.
 * 
 * @param <K> The key-type of the map
 * @param <V> The value-type of the map
 */
public class UpdatableIdentityHashMap<K, V> extends IdentityHashMap<K, V> implements UpdatableMap<K, V> {
	@Override
	public ElementUpdateResult update(K key) {
		return containsKey(key) ? ElementUpdateResult.NotChanged : ElementUpdateResult.NotFound;
	}

	@Override
	public UpdatableSet<K> keySet() {
		Set<K> keySet = super.keySet();
		return new UpdatableSet<K>() {
			@Override
			public ElementUpdateResult update(K value) {
				return keySet.contains(value) ? ElementUpdateResult.NotChanged : ElementUpdateResult.NotFound;
			}

			@Override
			public int size() {
				return keySet.size();
			}

			@Override
			public boolean isEmpty() {
				return keySet.isEmpty();
			}

			@Override
			public boolean contains(Object o) {
				return keySet.contains(o);
			}

			@Override
			public Iterator<K> iterator() {
				return keySet.iterator();
			}

			@Override
			public Object[] toArray() {
				return keySet.toArray();
			}

			@Override
			public <T> T[] toArray(T[] a) {
				return keySet.toArray(a);
			}

			@Override
			public boolean add(K e) {
				return keySet.add(e);
			}

			@Override
			public boolean remove(Object o) {
				return keySet.remove(o);
			}

			@Override
			public boolean containsAll(Collection<?> c) {
				return keySet.containsAll(c);
			}

			@Override
			public boolean addAll(Collection<? extends K> c) {
				return keySet.addAll(c);
			}

			@Override
			public boolean removeAll(Collection<?> c) {
				return keySet.removeAll(c);
			}

			@Override
			public boolean retainAll(Collection<?> c) {
				return keySet.retainAll(c);
			}

			@Override
			public void clear() {
				keySet.clear();
			}

			@Override
			public boolean removeIf(Predicate<? super K> filter) {
				return keySet.removeIf(filter);
			}

			@Override
			public Spliterator<K> spliterator() {
				return keySet.spliterator();
			}

			@Override
			public Stream<K> stream() {
				return keySet.stream();
			}

			@Override
			public Stream<K> parallelStream() {
				return keySet.parallelStream();
			}

			@Override
			public void forEach(Consumer<? super K> action) {
				keySet.forEach(action);
			}

			@Override
			public int hashCode() {
				return keySet.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return keySet.equals(obj);
			}

			@Override
			public String toString() {
				return keySet.toString();
			}
		};
	}
}
