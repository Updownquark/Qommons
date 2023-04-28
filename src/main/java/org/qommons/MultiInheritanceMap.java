package org.qommons;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.qommons.collect.BetterList;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;

/**
 * A map for keys that extend one another in multi-inheritance relationships.
 * 
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public interface MultiInheritanceMap<K, V> {
	/** @return The number of keys in this map */
	int size();

	/** @return Whether this map is empty */
	default boolean isEmpty() {
		return size() == 0;
	}

	/** @return All keys in this map with a value mapped to them */
	Set<K> keySet();

	/** @return All values mapped to keys in this map */
	Collection<V> values();

	/** @return All key-value pairs in this map */
	Collection<Map.Entry<K, V>> entrySet();

	/**
	 * @param key The key to test
	 * @param exact Whether to test for the key EXACTLY, or to return true if a value is mapped to a key that the given key is an
	 *        {@link MultiInheritanceSet.Inheritance#isExtension(Object, Object) extension} of
	 * @return Whether a value is present in this map for the given key
	 */
	boolean containsKey(K key, boolean exact);

	/**
	 * @param key The key to get the value for
	 * @return A value that is mapped to a key in this map for which the given key is an
	 *         {@link MultiInheritanceSet.Inheritance#isExtension(Object, Object) extension}
	 */
	V getAny(K key);

	/**
	 * @param key The key to get the value for
	 * @return All values is mapped to keys in this map for which the given key is an
	 *         {@link MultiInheritanceSet.Inheritance#isExtension(Object, Object) extension}
	 */
	BetterList<V> getAll(K key);

	/**
	 * @return The entries at the root of this map (for keys that are not {@link MultiInheritanceSet.Inheritance#isExtension(Object, Object)
	 *         extensions} of other keys in this map
	 */
	Collection<Map.Entry<K, V>> getRoots();

	/**
	 * @return The entries at the leaves of this map (for keys that have no other
	 *         {@link MultiInheritanceSet.Inheritance#isExtension(Object, Object) extensions} in this map
	 */
	Collection<Map.Entry<K, V>> getTerminals();

	/**
	 * @param parent The parent entry to get children of
	 * @return Entries of keys that are direct {@link MultiInheritanceSet.Inheritance#isExtension(Object, Object) extensions} of the given
	 *         entry's key in this map
	 */
	Collection<Map.Entry<K, V>> getChildren(Map.Entry<K, V> parent);

	/**
	 * @param key The key to put the value for
	 * @param value Computes the value for the given key and the value currently in the map for that key
	 * @return The computed value
	 */
	V compute(K key, BiFunction<? super K, ? super V, ? extends V> value);

	/**
	 * @param key The key to put the value for
	 * @param value The value to put
	 * @return The value that was previously in this map for the given key
	 */
	default V put(K key, V value) {
		return compute(key, (__, ___) -> value);
	}

	/**
	 * Adds all entries from the give map into this one
	 * 
	 * @param map The map to put
	 */
	default void putAll(MultiInheritanceMap<? extends K, ? extends V> map) {
		for (Map.Entry<? extends K, ? extends V> entry : map.entrySet())
			put(entry.getKey(), entry.getValue());
	}

	/**
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param inheritance The inheritance for the map
	 * @return The new map
	 */
	public static <K, V> MultiInheritanceMap<K, V> create(MultiInheritanceSet.Inheritance<K> inheritance) {
		return new Default<>(inheritance);
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to wrap
	 * @return A map with the same data as the given map, but which cannot be modified directly
	 */
	public static <K, V> MultiInheritanceMap<K, V> unmodifiable(MultiInheritanceMap<K, V> map) {
		return map instanceof Unmodifiable ? map : new Unmodifiable<>(map);
	}

	/**
	 * Default {@link MultiInheritanceMap} implementation
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public class Default<K, V> implements MultiInheritanceMap<K, V> {
		private final MultiInheritanceSet.Inheritance<K> theInheritance;
		private final Map<K, Node<K, V>> theNodes;
		private final List<Node<K, V>> theRoots;
		private final AtomicInteger theNodeIdCounter;

		/** @param inheritance The inheritance for this map */
		public Default(MultiInheritanceSet.Inheritance<K> inheritance) {
			theInheritance = inheritance;
			theNodes = new LinkedHashMap<>();
			theRoots = new ArrayList<>();
			theNodeIdCounter = new AtomicInteger();
		}

		@Override
		public int size() {
			return theNodes.size();
		}

		@Override
		public Set<K> keySet() {
			return Collections.unmodifiableSet(theNodes.keySet());
		}

		@Override
		public Collection<V> values() {
			return Collections.unmodifiableCollection(
				theNodes.values().stream().map(Node::getValue).collect(Collectors.toCollection(() -> new ArrayList<>(theNodes.size()))));
		}

		@Override
		public Collection<Map.Entry<K, V>> entrySet() {
			return Collections.unmodifiableCollection(theNodes.values());
		}

		@Override
		public boolean containsKey(K key, boolean exact) {
			if (exact)
				return theNodes.containsKey(key);
			for (Node<K, V> node : theRoots) {
				if (theInheritance.isExtension(node.key, key))
					return true;
			}
			return false;
		}

		@Override
		public V getAny(K key) {
			Node<K, V> node = theNodes.get(key);
			if (node != null)
				return node.value;
			IntList visited = new IntList(true, true);
			CircularArrayList<Node<K, V>> matches = CircularArrayList.build().build();
			for (Node<K, V> root : theRoots) {
				if (theInheritance.isExtension(root.key, key)) {
					visited.add(root.id);
					matches.add(root);
				}
			}
			Node<K, V> bestMatch = null;
			while (!matches.isEmpty()) {
				bestMatch = matches.get(0);
				int matchSize = matches.size();
				for (int i = 0; i < matchSize; i++) {
					Node<K, V> match = matches.get(i);
					if (match.children == null)
						continue;
					for (Node<K, V> child : match.children) {
						if (visited.add(child.id) && theInheritance.isExtension(child.key, key))
							matches.add(child);
					}
				}
				matches.removeRange(0, matchSize);
			}
			return bestMatch == null ? null : bestMatch.value;
		}

		@Override
		public BetterList<V> getAll(K key) {
			IntList visited = new IntList(true, true);
			CircularArrayList<Node<K, V>> matches = CircularArrayList.build().build();
			for (Node<K, V> root : theRoots) {
				if (theInheritance.isExtension(root.key, key)) {
					visited.add(root.id);
					matches.add(root);
				}
			}
			if (matches.isEmpty())
				return BetterList.empty();
			BetterList<V> values = BetterTreeList.<V> build().build();
			for (Node<K, V> match : matches)
				values.add(match.value);
			while (!matches.isEmpty()) {
				int matchSize = matches.size();
				for (int i = 0; i < matchSize; i++) {
					Node<K, V> match = matches.get(i);
					if (match.children == null)
						continue;
					for (Node<K, V> child : match.children) {
						if (visited.add(child.id) && theInheritance.isExtension(child.key, key)) {
							matches.add(child);
							values.add(child.value);
						}
					}
				}
				matches.removeRange(0, matchSize);
			}
			return values;
		}

		@Override
		public List<Map.Entry<K, V>> getRoots() {
			return Collections.unmodifiableList(theRoots);
		}

		@Override
		public Collection<Map.Entry<K, V>> getTerminals() {
			Set<Map.Entry<K, V>> terminals = new LinkedHashSet<>();
			for (Node<K, V> root : theRoots)
				addTerminals(root, terminals);
			return Collections.unmodifiableSet(terminals);
		}

		@Override
		public Collection<Entry<K, V>> getChildren(Entry<K, V> parent) {
			if (!(parent instanceof Node))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			Node<K, V> node = (Node<K, V>) parent;
			if (node.children == null || node.children.isEmpty())
				return Collections.emptyList();
			else
				return Collections.unmodifiableList(node.children);
		}

		private void addTerminals(Node<K, V> node, Set<Entry<K, V>> terminals) {
			if (node.children == null || node.children.isEmpty())
				terminals.add(node);
			else {
				for (Node<K, V> child : node.children)
					addTerminals(child, terminals);
			}
		}

		@Override
		public V compute(K key, BiFunction<? super K, ? super V, ? extends V> value) {
			Node<K, V> node = theNodes.get(key);
			if (node != null)
				return node.setValue(value.apply(key, node.value));
			node = new Node<>(theNodeIdCounter.getAndIncrement(), key, value.apply(key, null));
			theNodes.put(key, node);
			IntList visited = new IntList(true, true);
			Iterator<Node<K, V>> iter = theRoots.iterator();
			while (iter.hasNext()) {
				Node<K, V> root = iter.next();
				if (theInheritance.isExtension(root.key, key))
					addChild(root, node, visited);
				else if (theInheritance.isExtension(key, root.key)) {
					addChild(node, root, null);
					iter.remove();
				}
			}
			if (node.parentCount == 0)
				theRoots.add(node);
			return null;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder().append('{');
			boolean first = true;
			for (Node<K, V> node : theNodes.values()) {
				if (first)
					first = false;
				else
					str.append(", ");
				str.append(node);
			}
			return str.append('}').toString();
		}

		private void addChild(Node<K, V> parent, Node<K, V> child, IntList visited) {
			if (parent.children == null)
				parent.children = new ArrayList<>(5);
			if (visited != null && !visited.add(parent.id))
				return;
			Iterator<Node<K, V>> childIter = parent.children.iterator();
			boolean isDirectChild = true;
			while (childIter.hasNext()) {
				Node<K, V> oldChild = childIter.next();
				if (theInheritance.isExtension(oldChild.key, child.key)) {
					isDirectChild = false;
					addChild(oldChild, child, visited);
				} else if (theInheritance.isExtension(child.key, oldChild.key)) {
					oldChild.parentCount--;
					childIter.remove();
					addChild(child, oldChild, null);
				}
			}
			if (isDirectChild) {
				parent.children.add(child);
				child.parentCount++;
			}
		}

		private static class Node<K, V> implements Map.Entry<K, V> {
			final int id;
			final K key;
			V value;
			List<Node<K, V>> children;
			int parentCount;

			Node(int id, K key, V value) {
				this.id = id;
				this.key = key;
				this.value = value;
			}

			@Override
			public K getKey() {
				return key;
			}

			@Override
			public V getValue() {
				return value;
			}

			@Override
			public V setValue(V value) {
				V old = this.value;
				this.value = value;
				return old;
			}

			@Override
			public int hashCode() {
				return key.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof Map.Entry && key.equals(((Map.Entry<?, ?>) obj).getKey());
			}

			@Override
			public String toString() {
				return key + "=" + value;
			}
		}
	}

	/**
	 * Implementation for {@link MultiInheritanceMap#unmodifiable(MultiInheritanceMap)}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public class Unmodifiable<K, V> implements MultiInheritanceMap<K, V> {
		private final MultiInheritanceMap<K, V> theBacking;

		/** @param backing The map to wrap */
		public Unmodifiable(MultiInheritanceMap<K, V> backing) {
			theBacking = backing;
		}

		@Override
		public int size() {
			return theBacking.size();
		}

		@Override
		public Set<K> keySet() {
			return theBacking.keySet();
		}

		@Override
		public Collection<V> values() {
			return theBacking.values();
		}

		@Override
		public Collection<Map.Entry<K, V>> entrySet() {
			return wrap(theBacking.entrySet());
		}

		private Collection<Map.Entry<K, V>> wrap(Collection<Map.Entry<K, V>> entries) {
			return Collections.unmodifiableList(
				entries.stream().map(UnmodifiableEntry::new).collect(Collectors.toCollection(() -> new ArrayList<>(size()))));
		}

		@Override
		public boolean containsKey(K key, boolean exact) {
			return theBacking.containsKey(key, exact);
		}

		@Override
		public V getAny(K key) {
			return theBacking.getAny(key);
		}

		@Override
		public BetterList<V> getAll(K key) {
			return theBacking.getAll(key);
		}

		@Override
		public Collection<Entry<K, V>> getRoots() {
			return wrap(theBacking.getRoots());
		}

		@Override
		public Collection<Entry<K, V>> getTerminals() {
			return wrap(theBacking.getTerminals());
		}

		@Override
		public Collection<Entry<K, V>> getChildren(Entry<K, V> parent) {
			if (parent instanceof UnmodifiableEntry)
				parent = ((UnmodifiableEntry<K, V>) parent).theBacking;
			return wrap(theBacking.getChildren(parent));
		}

		@Override
		public void putAll(MultiInheritanceMap<? extends K, ? extends V> map) {
			if (!map.isEmpty())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public V compute(K key, BiFunction<? super K, ? super V, ? extends V> value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public int hashCode() {
			return theBacking.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theBacking.equals(obj);
		}

		@Override
		public String toString() {
			return theBacking.toString();
		}

		static class UnmodifiableEntry<K, V> implements Map.Entry<K, V> {
			private final Map.Entry<K, V> theBacking;

			UnmodifiableEntry(Entry<K, V> backing) {
				theBacking = backing;
			}

			@Override
			public K getKey() {
				return theBacking.getKey();
			}

			@Override
			public V getValue() {
				return theBacking.getValue();
			}

			@Override
			public V setValue(V value) {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public int hashCode() {
				return theBacking.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return theBacking.equals(obj);
			}

			@Override
			public String toString() {
				return theBacking.toString();
			}
		}
	}
}
