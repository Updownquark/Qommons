package org.qommons;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.qommons.collect.MappedCollection;
import org.qommons.collect.MappedSet;
import org.qommons.ex.ExBiFunction;
import org.qommons.ex.ExSupplier;

/**
 * <p>
 * A key/value map whose keys are in a hierarchical relationship where each key may have any number of other keys it extends directly.
 * </p>
 * <p>
 * This map was designed for Java classes (as each Java class may extend another and also may inherit any number of interfaces) in
 * conjunction with {@link CachedInheritance}, but the implementation supports any multi-inheritance hierarchy.
 * </p>
 * <p>
 * The view may be queried for entries or values by key with intent to get the value for the most specific ancestor (the most common
 * use-case), least specific descendant, or just about any other type of query.
 * </p>
 * <p>
 * A view is a hierarchical collection of {@link Node} entries. In general, a view will only contain entries for keys for which a value has
 * been {@link MultiInheritanceMap2#put(Object, Object) mapped}. But if a value is removed for a key for which the map still has values for
 * keys that are descended from it, the value-less entry may be retained rather than re-structure the map.
 * </p>
 * 
 * @param <K> The type of keys in the map
 * @param <V> The type of values in the map
 */
public interface MultiInheritanceView<K, V> extends Stamped {
	/**
	 * An inheritance scheme to power a {@link MultiInheritanceView}
	 * 
	 * @param <K> The key-type to determine inheritance for
	 */
	public interface Inheritance<K> {
		/**
		 * @param superKey The super-key to test
		 * @param subKey The sub-key to test
		 * @return Whether <code>subKey</code> is an extension of <code>superKey</code>
		 */
		boolean isExtension(K superKey, K subKey);

		/**
		 * @param key The key to get the direct parents for
		 * @return All keys that are direct parents of <code>key</code> (e.g. not parents of parents)
		 */
		Iterable<? extends K> getParents(K key);
	}

	public interface Navigator<K, V> {
		Iterator<Node<K, V>> visit(Node<K, V> node);
	}

	/**
	 * <p>
	 * An entry in a {@link MultiInheritanceView}.
	 * </p>
	 * <p>
	 * This entry may or may not {@link #hasValue() contain} a {@link #getValue() value}. If it does not contain a value, it will have at
	 * least one entry in its {@link #getChildren() children}. If it does contain a value, it may or may not have children.
	 * </p>
	 * 
	 * @param <K> The type of keys in the map
	 * @param <V> The type of values in the map
	 */
	public interface Node<K, V> extends Map.Entry<K, V>, MultiInheritanceView<K, V> {
		/** @return Whether this node has a value */
		default boolean hasValue() {
			return getValue() != null;
		}

		/**
		 * @return Nodes corresponding to ancestors (see {@link Inheritance#getParents(Object) parents}) of this entry's key that contain
		 *         other values (directly or in their {@link #getChildren() children})
		 */
		Iterable<Node<K, V>> getParents();

		/**
		 * @return Nodes corresponding to descendants (see {@link Inheritance#getParents(Object) parents}) of this entry's key that contain
		 *         other values (directly or in their {@link #getChildren() children})
		 */
		Iterable<Node<K, V>> getChildren();

		/**
		 * <p>
		 * Removes this map entry and, if specified, all entries whose keys descend from this entry's key, from the map.
		 * </p>
		 * <p>
		 * If this node is not {@link #hasValue() valued} and <code>andAllDescendants</code> is false, this call will have no effect.
		 * </p>
		 * 
		 * @param andAllDescendants Whether to also remove from the map all entries for keys that descend from this entry's key
		 * @return This node
		 */
		Node<K, V> remove(boolean andAllDescendants);

		@Override
		default boolean isEmpty() {
			return false;
		}

		@Override
		default Iterable<Node<K, V>> getRoots() {
			Set<Node<K, V>> visited = new HashSet<>();
			// Do depth-first for this one
			Iterable<Node<K, V>> ancestors = IterableUtils.depthFirst(this, Node::getParents, null);
			return IterableUtils.filter(ancestors, node -> {
				if (!node.getParents().iterator().hasNext())
					return false;
				else
					return visited.add(node);
			});
		}

		@Override
		default Iterable<Node<K, V>> allEntries() {
			return IterableUtils.concat(ancestors(false, true), descendants(false, false));
		}

		@Override
		default Iterable<Node<K, V>> entries() {
			return IterableUtils.concat(ancestors(true, true), descendants(true, false));
		}

		/**
		 * @return This node and all of this node's {@link #getParents() parents} (and their parents, and so on) that are {@link #hasValue()
		 *         valued}
		 */
		default Iterable<Node<K, V>> ancestors() {
			return ancestors(true, true);
		}

		/**
		 * @param valuedOnly Whether to include only {@link #hasValue() valued} ancestors or all ancestors
		 * @return This node and all its {@link #getParents() parents} (and their parents, and so on) filtered by <code>valuedOnly</code>
		 */
		default Iterable<Node<K, V>> ancestors(boolean valuedOnly) {
			return ancestors(valuedOnly, true);
		}

		/**
		 * @param valuedOnly Whether to include only {@link #hasValue() valued} ancestors or all ancestors
		 * @param withSelf Whether to include this node at the beginning of the iteration or skip to the parents
		 * @return All this node's {@link #getParents() parents} (and their parents, and so on) filtered by <code>valuedOnly</code>, with
		 *         this node at the beginning of the iteration if specified
		 */
		default Iterable<Node<K, V>> ancestors(boolean valuedOnly, boolean withSelf) {
			Iterable<Node<K, V>> allAncestors;
			if (withSelf)
				allAncestors = IterableUtils.breadthFirst(this, Node::getParents, null);
			else
				allAncestors = IterableUtils.breadthFirstMulti(getParents(), Node::getParents, null);
			if (valuedOnly)
				return IterableUtils.filter(allAncestors, Node::hasValue);
			else
				return allAncestors;
		}

		/**
		 * @return This node and all of this node's {@link #getChildren() children} (and their children, and so on) that are
		 *         {@link #hasValue() valued}
		 */
		default Iterable<Node<K, V>> descendants() {
			return descendants(true, true);
		}

		/**
		 * @param valuedOnly Whether to include only {@link #hasValue() valued} descendants or all descendants
		 * @return This node and all its {@link #getChildren() children} (and their children, and so on) filtered by <code>valuedOnly</code>
		 */
		default Iterable<Node<K, V>> descendants(boolean valuedOnly) {
			return descendants(valuedOnly, true);
		}

		/**
		 * @param valuedOnly Whether to include only {@link #hasValue() valued} descendants or all descendants
		 * @param withSelf Whether to include this node at the beginning of the iteration or skip to the parents
		 * @return This node and all its {@link #getChildren() children} (and their children, and so on) filtered by <code>valuedOnly</code>
		 *         with this node at the beginning of the iteration if specified
		 */
		default Iterable<Node<K, V>> descendants(boolean valuedOnly, boolean withSelf) {
			Iterable<Node<K, V>> allDescendants;
			if (withSelf)
				allDescendants = IterableUtils.breadthFirst(this, Node::getChildren, null);
			else
				allDescendants = IterableUtils.breadthFirstMulti(getChildren(), Node::getChildren, null);
			if (valuedOnly)
				return IterableUtils.filter(allDescendants, Node::hasValue);
			else
				return allDescendants;
		}

		default Iterator<Node<K, V>> visitChildren(Navigator<K, V> navigator) {
			Iterable<Node<K, V>> flat = IterableUtils.flatten(IterableUtils.map(getChildren(), child -> () -> navigator.visit(child)));
			return flat.iterator();
		}

		/**
		 * Simple print function for a node
		 * 
		 * @param str The string builder to print to
		 * @param indent The depth of the node in the printing
		 * @return The string builder
		 */
		default StringBuilder append(StringBuilder str, int indent) {
			StringUtils.indent(str, indent);
			str.append(getKey());
			if (getValue() != null)
				str.append('=').append(getValue());
			Iterator<Node<K, V>> children = getChildren().iterator();
			if (children.hasNext()) {
				str.append('\n');
				do {
					children.next().append(str, indent + 1);
				} while (children.hasNext());
			}
			return str;
		}

		@Override
		default UnmodifiableNode<K, V> unmodifiable() {
			return new UnmodifiableNode<>(this);
		}
	}

	/**
	 * A {@link MultiInheritanceView} that supports modification
	 * 
	 * @param <K>
	 * @param <V>
	 */
	interface MultiInheritanceMap2<K, V> extends MultiInheritanceView<K, V> {
		/** @return The number of keys in this map */
		int size();

		/** @return Whether this map is empty */
		@Override
		default boolean isEmpty() {
			return size() == 0;
		}

		@Override
		Collection<Node<K, V>> getRoots();

		@Override
		Collection<Node<K, V>> allEntries();

		@Override
		Collection<Node<K, V>> entries();

		/** @return All keys in this map with a value mapped to them */
		default Set<K> keys() {
			return new MappedSet<>(entries(), Node::getKey, k -> getEntry((K) k, TypeMatch.EXACT) != null);
		}

		/** @return All values mapped to keys in this map */
		default Collection<V> values() {
			return new MappedCollection<>(entries(), Node::getValue);
		}

		/**
		 * @param <X> The type of exception that the function may throw
		 * @param key The key to put the value for
		 * @param value Computes the value for the given key and the value currently in the map for that key
		 * @return The computed value
		 * @throws X If the key currently has no entry and the producer function throws an exception
		 */
		<X extends Throwable> V computeEx(K key, ExBiFunction<? super K, ? super V, ? extends V, X> value) throws X;

		/**
		 * @param key The key to put the value for
		 * @param value Computes the value for the given key and the value currently in the map for that key
		 * @return The computed value
		 */
		default V compute(K key, BiFunction<? super K, ? super V, ? extends V> value) {
			return computeEx(key, ExBiFunction.of(value));
		}

		default <X extends Throwable> V computeIfAbsentEx(K key, ExSupplier<? extends V, X> value) throws X {
			return computeEx(key, (k, old) -> old == null ? value.get() : old);
		}

		default V computeIfAbsent(K key, Supplier<? extends V> value) {
			return compute(key, (k, old) -> old == null ? value.get() : old);
		}

		/**
		 * @param key The key to put the value for
		 * @param value The value to put
		 * @return The value that was previously in this map for the given key
		 */
		default V put(K key, V value) {
			Object[] oldValue = new Object[1];
			compute(key, (__, oldV) -> {
				oldValue[0] = oldV;
				return value;
			});
			return (V) oldValue[0];
		}

		default MultiInheritanceView<K, V> with(K key, V value) {
			put(key, value);
			return this;
		}

		/**
		 * Adds all entries from the give map into this one
		 * 
		 * @param map The map to put
		 * @return This map
		 */
		default MultiInheritanceMap2<K, V> putAll(MultiInheritanceView<? extends K, ? extends V> map) {
			for (Node<? extends K, ? extends V> entry : map.entries())
				put(entry.getKey(), entry.getValue());
			return this;
		}

		@Override
		default <K2> MultiInheritanceMap2<K2, V> keyMap(Function<? super K, ? extends K2> map, Function<? super K2, ? extends K> reverse) {
			return new KeyMappedMap<>(this, new MappedInheritance<>(getInheritance(), map, reverse));
		}
	}

	/** An enum for the relationship between the key of a map entry and a specified target key */
	public enum TypeMatch {
		/** The entry's key is the same as the target type */
		EXACT,
		/** The entry's key extends the target type */
		SUB_TYPE,
		/** The target key extends the entry's key */
		SUPER_TYPE;
	}

	/** @return This view's inheritance scheme */
	Inheritance<K> getInheritance();

	/** @return Whether this map is empty */
	boolean isEmpty();

	/** @return All entries in this view that have no {@link Node#getParents() parents} */
	Iterable<Node<K, V>> getRoots();

	/** @return All entries in this view with or without {@link Node#hasValue() values} */
	Iterable<Node<K, V>> allEntries();

	/** @return All entries with values in this map */
	Iterable<Node<K, V>> entries();

	/**
	 * @return Entries for all top-level keys with values in this map (valued entries which do not extend any other valued entries)
	 */
	default Iterable<Node<K, V>> getTopLevelEntries() {
		Iterable<Node<K, V>> allEntriesStopAtValues = IterableUtils.breadthFirstMulti(getRoots(), Node::getChildren,
			node -> !node.hasValue());
		return IterableUtils.filter(allEntriesStopAtValues, Node::hasValue);
	}

	/**
	 * @param key The key to get the value for
	 * @param match The type of key-matches to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the entries for exactly the given key (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return entries for the given key and all sub-keys</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return entries for the given key and all super-keys</li>
	 *        <li><code>null</code> to return entries that are related to the given key in either direction</li>
	 *        </ul>
	 * @return The value mapped to the entry in this map whose key is most directly related to the given key in the given direction, or null
	 *         if there is no entry in this map related to the key in that way
	 */
	default V get(K key, TypeMatch match) {
		Node<K, V> entry = getEntry(key, match);
		return entry == null ? null : entry.getValue();
	}

	/**
	 * @param key The key to get the value for
	 * @param match The type of key-matches to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the entries for exactly the given key (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return entries for the given key and all sub-keys</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return entries for the given key and all super-keys</li>
	 *        <li><code>null</code> to return entries that are related to the given key in either direction</li>
	 *        </ul>
	 * @param defaultValue The value to return if there is no entry in this map related to the key in the given direction
	 * @return The value mapped to the entry in this map whose key is most directly related to the given key in the given direction, or
	 *         <code>defaultValue</code> if there is no entry in this map related to the key in that way
	 */
	default V getOrDefault(K key, TypeMatch match, V defaultValue) {
		Node<K, V> entry = getEntry(key, match);
		return entry == null ? defaultValue : entry.getValue();
	}

	default Iterable<V> getAll(K key, TypeMatch match) {
		return IterableUtils.map(getEntries(key, match, true), Node::getValue);
	}

	/**
	 * @param key The key to get the value for
	 * @param match The type of key-matches to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the entries for exactly the given key (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return entries for the given key and all sub-keys</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return entries for the given key and all super-keys</li>
	 *        <li><code>null</code> to return entries that are related to the given key in either direction</li>
	 *        </ul>
	 * @return The valued entry in this map whose key is most directly related to the given key in the given direction, or null if there is
	 *         no entry in this map related to the key in that way
	 */
	default Node<K, V> getEntry(K key, TypeMatch match) {
		return getEntry(key, match, true);
	}

	/**
	 * @param key The key to get the value for
	 * @param match The type of key-matches to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the entries for exactly the given key (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return entries for the given key and all sub-keys</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return entries for the given key and all super-keys</li>
	 *        <li><code>null</code> to return entries that are related to the given key in either direction</li>
	 *        </ul>
	 * @param valuedOnly Whether to return the most directly-related {@link Node#hasValue() valued} entry, or the most direct entry at all
	 * @return The entry in this map whose key is most directly related to the given key in the given direction, or null if there is no
	 *         entry in this map related to the key in that way, filtered by <code>valuedOnly</code>
	 */
	default Node<K, V> getEntry(K key, TypeMatch match, boolean valuedOnly) {
		Iterator<Node<K, V>> all = getDirectEntries(key, match, valuedOnly).iterator();
		return all.hasNext() ? all.next() : null;
	}

	/**
	 * @param key The key to get the entry for
	 * @return The entry in this map corresponding to the given key, or null if there is no such entry in this map
	 */
	Node<K, V> getExactEntry(K key);

	/**
	 * @param key The key to query with
	 * @param match The type of key-matches to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the entries for exactly the given key (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return entries for the given key and all sub-keys</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return entries for the given key and all super-keys</li>
	 *        <li><code>null</code> to return entries that are related to the given key in either direction</li>
	 *        </ul>
	 * @return All entries with values in this map for keys matching the query
	 */
	default Iterable<Node<K, V>> getEntries(K key, TypeMatch match) {
		return getEntries(key, match, true);
	}

	/**
	 * @param key The key to query with
	 * @param match The type of key-matches to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the entries for exactly the given key (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return entries for the given key and all sub-keys</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return entries for the given key and all super-keys</li>
	 *        <li><code>null</code> to return entries that are related to the given key in either direction</li>
	 *        </ul>
	 * @param valuedOnly Whether to return only entries that {@link Node#hasValue() have values} or all matching entries
	 * @return All entries in this map for keys matching the query
	 */
	default Iterable<Node<K, V>> getEntries(K key, TypeMatch match, boolean valuedOnly) {
		switch (match) {
		case EXACT:
			return getDirectEntries(key, match, valuedOnly);
		case SUB_TYPE:
			return IterableUtils.breadthFirstMulti(getDirectEntries(key, match, valuedOnly), node -> node.descendants(valuedOnly, false),
				null);
		case SUPER_TYPE:
			return IterableUtils.breadthFirstMulti(getDirectEntries(key, match, valuedOnly), node -> node.ancestors(valuedOnly, false),
				null);
		}
		throw new IllegalStateException("Unrecognized type match " + match);
	}

	/**
	 * @param key The key to query with
	 * @param match The type of key-matches to accept (see {@link #getEntries(Object, TypeMatch, boolean)})
	 * @param valuedOnly Whether to return only entries that {@link Node#hasValue() have values} or all matching entries
	 * @return Entries in this map matching the given query with no intermediate entries between them and the target key. I.e.:
	 *         <ul>
	 *         <li>For {@link TypeMatch#SUB_TYPE}, all entries for keys that extend <code>key</code> and whose {@link Node#getParents()}, if
	 *         any, are super-keys of <code>key</code></li>
	 *         <li>For {@link TypeMatch#SUPER_TYPE}, all entries for keys that are super-keys of <code>key</code> and whose
	 *         {@link Node#getParents()}, if any, are extensions of <code>key</code></li>
	 *         </ul>
	 */
	default Iterable<Node<K, V>> getDirectEntries(K key, TypeMatch match, boolean valuedOnly) {
		Node<K, V> exact = getExactEntry(key);
		if (exact != null)
			return Collections.singletonList(exact);
		else if (match == null)
			return getTopLevelEntries();
		else {
			switch (match) {
			case EXACT:
				return Collections.emptyList();
			case SUB_TYPE:
				Inheritance<? super K> inh = getInheritance();
				Navigator<K, V> nav = new Navigator<K, V>() {
					@Override
					public Iterator<Node<K, V>> visit(Node<K, V> node) {
						if (!inh.isExtension(key, node.getKey()))
							return Collections.emptyIterator();
						Iterator<Node<K, V>> childIter = node.visitChildren(this);
						if (childIter.hasNext())
							return childIter;
						else if (!valuedOnly || node.getValue() != null)
							return Collections.singleton(node).iterator();
						else
							return Collections.emptyIterator();
					}
				};
				return IterableUtils.flatten(IterableUtils.map(getRoots(), //
					root -> () -> nav.visit(root)));
			case SUPER_TYPE:
				inh = getInheritance();
				nav = new Navigator<K, V>() {
					@Override
					public Iterator<Node<K, V>> visit(Node<K, V> node) {
						if (!inh.isExtension(node.getKey(), key))
							return Collections.emptyIterator();
						Iterator<Node<K, V>> childIter = node.visitChildren(this);
						if (childIter.hasNext())
							return childIter;
						else if (!valuedOnly || node.getValue() != null)
							return Collections.singleton(node).iterator();
						else
							return Collections.emptyIterator();
					}
				};
				return IterableUtils.flatten(IterableUtils.map(getRoots(), //
					root -> () -> nav.visit(root)));
			}
			throw new IllegalStateException("Unrecognized type match " + match);
		}
	}

	/** @return An unmodifiable view of this map */
	default MultiInheritanceView<K, V> unmodifiable() {
		return new UnmodifiableView<>(this);
	}

	/**
	 * Creates a sub-view of this view consisting of all entries in this view whose keys are extensions of, or are super-keys of, a given
	 * filter key
	 * 
	 * @param key The filter key to create the view for
	 * @param extension Whether the sub-view will contain entries whose keys are extensions of the given filter key, or one whose entries
	 *        are all super-keys of the given filter key
	 * @return The filtered sub-view
	 */
	default MultiInheritanceView<K, V> subView(K key, boolean extension) {
		TypeMatch match;
		Predicate<K> filter;
		Inheritance<K> inh = getInheritance();
		if (extension) {
			match = TypeMatch.SUB_TYPE;
			filter = k -> inh.isExtension(key, k);
		} else {
			match = TypeMatch.SUPER_TYPE;
			filter = k -> inh.isExtension(k, key);
		}
		return new SubView<>(this, key, match, filter);
	}

	/**
	 * @param <K2> The key-type for the new view
	 * @param map The function to produce keys for the new map from keys in this map
	 * @param reverse The function to produce keys for this map from keys in the new map
	 * @return The key-mapped multi-inheritance view
	 */
	default <K2> MultiInheritanceView<K2, V> keyMap(Function<? super K, ? extends K2> map, Function<? super K2, ? extends K> reverse) {
		return new KeyMappedView<>(this, new MappedInheritance<>(getInheritance(), map, reverse));
	}

	default MultiInheritanceMap2<K, V> copy() {
		return MultiInheritanceView.<K, V> create(getInheritance()).putAll(this);
	}

	/**
	 * Default implementation for {@link MultiInheritanceView#hashCode()}
	 * 
	 * @param view The view to hash
	 * @return The hash code for the view
	 */
	public static int hashCode(MultiInheritanceView<?, ?> view) {
		int h = 0;
		Iterator<? extends Node<?, ?>> i = view.entries().iterator();
		while (i.hasNext())
			h += i.next().hashCode();
		return h;
	}

	/**
	 * Default implementation for {@link Node#hashCode()}
	 * 
	 * @param node The node to hash
	 * @return The hash code for the node
	 */
	public static int hashCode(Node<?, ?> node) {
		Object key = node.getKey();
		Object value = node.getValue();
		return (key == null ? 0 : key.hashCode()) //
			^ (value == null ? 0 : value.hashCode());
	}

	/**
	 * Default implementation for {@link MultiInheritanceView#equals(Object)}
	 * 
	 * @param view The view to test
	 * @param o The object to test
	 * @return Whether the given view is equivalent to the given object
	 */
	public static boolean equals(MultiInheritanceView<?, ?> view, Object o) {
		if (view == o)
			return true;
		else if (!(o instanceof MultiInheritanceView))
			return false;
		MultiInheritanceView<?, ?> other = (MultiInheritanceView<?, ?>) o;
		Iterator<? extends Node<?, ?>> entries1 = view.entries().iterator();
		Iterator<? extends Node<?, ?>> entries2 = other.entries().iterator();
		while (entries1.hasNext()) {
			if (!entries2.hasNext() || !entries1.next().equals(entries2.next()))
				return false;
		}
		return !entries2.hasNext();
	}

	/**
	 * Default implementation for {@link Node#equals(Object)}
	 * 
	 * @param node The node to test
	 * @param o The object to test
	 * @return Whether the given node is equivalent to the given object
	 */
	public static boolean equals(Node<?, ?> node, Object o) {
		if (node == o)
			return true;
		else if (!(o instanceof Node))
			return false;
		Node<?, ?> other = (Node<?, ?>) o;
		return Objects.equals(node.getKey(), other.getKey()) && Objects.equals(node.getValue(), other.getValue());
	}

	/**
	 * Default implementation for {@link MultiInheritanceView#toString()}
	 * 
	 * @param view The map to print
	 * @return The printed map
	 */
	public static String toString(MultiInheritanceView<?, ?> view) {
		StringBuilder str = new StringBuilder().append('{');
		boolean first = true;
		for (Node<?, ?> entry : view.getRoots()) {
			if (first)
				first = false;
			else
				str.append('\n');
			entry.append(str, 0);
		}
		return str.append('}').toString();
	}

	/**
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param inheritance The inheritance for the map
	 * @return A new, mutable multi-inheritance map
	 */
	public static <K, V> MultiInheritanceMap2<K, V> create(Inheritance<K> inheritance) {
		return new Default<>(inheritance);
	}

	/** {@link MultiInheritanceView} inheritance for {@link CachedInheritance} */
	static Inheritance<CachedInheritance<?>> CI_INHERITANCE = new Inheritance<CachedInheritance<?>>() {
		@Override
		public boolean isExtension(CachedInheritance<?> superKey, CachedInheritance<?> subKey) {
			return superKey.type.isAssignableFrom(subKey.type);
		}

		@Override
		public Iterable<? extends CachedInheritance<?>> getParents(CachedInheritance<?> key) {
			return key.getParents();
		}
	};

	/**
	 * @param <V> The type of values for the map
	 * @return A {@link MultiInheritanceMap2} using java's class inheritance (supporting extends and implements)
	 */
	public static <V> MultiInheritanceMap2<Class<?>, V> createClassMap() {
		return new Default<CachedInheritance<?>, V>(CI_INHERITANCE).keyMap(ci -> ci.type, CachedInheritance::get);
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to wrap
	 * @return A map with the same data as the given map, but which cannot be modified directly
	 */
	public static <K, V> MultiInheritanceView<K, V> unmodifiable(MultiInheritanceView<K, V> map) {
		return map == null ? empty() : map.unmodifiable();
	}

	/**
	 * @param <K> The key-type for the map
	 * @param <V> The value-type for the map
	 * @return An empty multi-inheritance map
	 */
	static <K, V> MultiInheritanceView<K, V> empty() {
		return (MultiInheritanceView<K, V>) EMPTY;
	}

	/** Singleton empty multi-inheritance map */
	static MultiInheritanceView<Object, Object> EMPTY = new Empty<>();

	/**
	 * Default {@link MultiInheritanceMap2} implementation
	 * 
	 * @param <K> The type of keys in the map
	 * @param <V> The type of values in the map
	 */
	static class Default<K, V> implements MultiInheritanceMap2<K, V> {
		private final Inheritance<K> theInheritance;
		private final List<NodeImpl> theRoots;
		private final Map<K, NodeImpl> theNodesByKey;
		private final Map<K, NodeImpl> theValuedEntries;
		private long theStamp;

		Default(Inheritance<K> inheritance) {
			theInheritance = inheritance;
			theRoots = new ArrayList<>();
			theNodesByKey = new HashMap<>();
			theValuedEntries = new HashMap<>();
		}

		@Override
		public Inheritance<K> getInheritance() {
			return theInheritance;
		}

		@Override
		public Node<K, V> getExactEntry(K key) {
			return theNodesByKey.get(key);
		}

		@Override
		public long getStamp() {
			return theStamp;
		}

		@Override
		public int size() {
			return theValuedEntries.size();
		}

		@Override
		public Collection<Node<K, V>> getRoots() {
			return Collections.unmodifiableCollection(theRoots);
		}

		@Override
		public Collection<Node<K, V>> allEntries() {
			return Collections.unmodifiableCollection(theNodesByKey.values());
		}

		@Override
		public Collection<Node<K, V>> entries() {
			return new ValuedEntryCollection();
		}

		@Override
		public <X extends Throwable> V computeEx(K key, ExBiFunction<? super K, ? super V, ? extends V, X> value) throws X {
			NodeImpl node = theNodesByKey.get(key);
			if (node != null) {
				V newValue = value.apply(key, node.getValue());
				if (node.getValue() != newValue)
					node.setValue(newValue);
				return newValue;
			}
			V newValue = value.apply(key, null);
			if (newValue == null)
				return null;
			node = new NodeImpl(key);
			node.setValue(newValue);
			theNodesByKey.put(key, node);
			boolean isRoot = true;
			Iterator<NodeImpl> roots = theRoots.iterator();
			while (roots.hasNext()) {
				NodeImpl root = roots.next();
				if (theInheritance.isExtension(root.getKey(), key)) {
					isRoot = false;
					root.addDescendant(node);
				} else if (theInheritance.isExtension(key, root.getKey())) {
					roots.remove();
					node.theChildren.add(root);
					root.theParents.add(node);
				}
			}
			if (isRoot)
				theRoots.add(node);
			return newValue;
		}

		@Override
		public int hashCode() {
			return MultiInheritanceView.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return MultiInheritanceView.equals(this, obj);
		}

		@Override
		public String toString() {
			return MultiInheritanceView.toString(this);
		}

		class NodeImpl implements Node<K, V> {
			private final List<NodeImpl> theParents;
			private final K theKey;
			private V theValue;
			private final List<NodeImpl> theChildren;

			NodeImpl(K key) {
				theParents = new ArrayList<>();
				theKey = key;
				theChildren = new ArrayList<>();
				theNodesByKey.put(key, this);
			}

			private void addDescendant(NodeImpl node) {
				boolean isDirectChild = true;
				Iterator<NodeImpl> children = theChildren.iterator();
				while (children.hasNext()) {
					NodeImpl child = children.next();
					if (theInheritance.isExtension(child.getKey(), node.getKey())) {
						isDirectChild = false;
						child.addDescendant(node);
					} else if (theInheritance.isExtension(node.getKey(), child.getKey())) {
						children.remove();
						node.theChildren.add(child);
						child.theParents.add(node);
					}
				}
				if (isDirectChild)
					theChildren.add(node);
			}

			@Override
			public Iterable<Node<K, V>> getParents() {
				return IterableUtils.unmodifiable(theParents);
			}

			@Override
			public Iterable<Node<K, V>> getChildren() {
				return IterableUtils.unmodifiable(theChildren);
			}

			@Override
			public K getKey() {
				return theKey;
			}

			@Override
			public V getValue() {
				return theValue;
			}

			@Override
			public V setValue(V value) {
				if (value == null) {
					removeValue(true);
					return theValue;
				} else {
					V oldValue = theValue;
					theValue = value;
					theStamp++;
					if (oldValue == null) {
						theValuedEntries.put(theKey, this);
					}
					return oldValue;
				}
			}

			void removeValue(boolean fromValuedEntries) {
				if (fromValuedEntries)
					theValuedEntries.remove(theKey);
				if (theChildren.isEmpty())
					remove();
			}

			private void remove() {
				theNodesByKey.remove(theKey);
				if (theParents.isEmpty())
					theRoots.remove(this);
				else {
					for (NodeImpl parent : theParents)
						parent.theChildren.remove(this);
				}
			}

			@Override
			public Node<K, V> remove(boolean andAllDescendants) {
				if (andAllDescendants)
					remove();
				else
					removeValue(true);
				return this;
			}

			@Override
			public long getStamp() {
				return theStamp;
			}

			@Override
			public Inheritance<K> getInheritance() {
				return theInheritance;
			}

			@Override
			public Node<K, V> getExactEntry(K key) {
				if (theKey.equals(key))
					return this;
				else if (theInheritance.isExtension(theKey, key) || theInheritance.isExtension(key, theKey))
					return Default.this.getExactEntry(key);
				else
					return null;
			}

			@Override
			public int hashCode() {
				return MultiInheritanceView.hashCode(this);
			}

			@Override
			public boolean equals(Object obj) {
				return MultiInheritanceView.equals(this, obj);
			}

			@Override
			public String toString() {
				return append(new StringBuilder(), 0).toString();
			}
		}

		class ValuedEntryCollection extends AbstractCollection<Node<K, V>> {
			@Override
			public Iterator<Node<K, V>> iterator() {
				return new Iterator<Node<K, V>>() {
					final Iterator<NodeImpl> backing = theValuedEntries.values().iterator();
					NodeImpl last;

					@Override
					public boolean hasNext() {
						return backing.hasNext();
					}

					@Override
					public Node<K, V> next() {
						last = backing.next();
						return last;
					}

					@Override
					public void remove() {
						backing.remove();
						last.removeValue(false);
					}
				};
			}

			@Override
			public int size() {
				return theValuedEntries.size();
			}
		}
	}

	/**
	 * Default implementation for {@link MultiInheritanceView#subView(Object, boolean)}
	 * 
	 * @param <K> The type of keys in the map
	 * @param <V> The type of values in the map
	 */
	static class SubView<K, V> implements MultiInheritanceView<K, V> {
		private final MultiInheritanceView<K, V> theParent;
		private final K theFilterKey;
		private final TypeMatch theMatch;
		private final Predicate<K> theFilter;

		protected SubView(MultiInheritanceView<K, V> parent, K filterKey, TypeMatch match, Predicate<K> filter) {
			theParent = parent;
			theFilterKey = filterKey;
			theMatch = match;
			theFilter = filter;
		}

		protected MultiInheritanceView<K, V> getParent() {
			return theParent;
		}

		protected K getFilterKey() {
			return theFilterKey;
		}

		@Override
		public Inheritance<K> getInheritance() {
			return theParent.getInheritance();
		}

		@Override
		public long getStamp() {
			return theParent.getStamp();
		}

		@Override
		public boolean isEmpty() {
			return theParent.getEntry(theFilterKey, null) != null;
		}

		@Override
		public Iterable<Node<K, V>> getRoots() {
			if (theMatch == TypeMatch.SUB_TYPE) {
				Node<K, V> exact = getExactEntry(theFilterKey);
				if (exact == null)
					return Collections.emptyList();
				return Collections.singletonList(new SubViewNode<>(exact, theFilterKey, theMatch, theFilter));
			} else
				return wrap(theParent.getRoots(), true);
		}

		@Override
		public Node<K, V> getExactEntry(K key) {
			if (!theFilter.test(key))
				return null;
			Node<K, V> entry = theParent.getExactEntry(key);
			return entry == null ? null : new SubViewNode<>(entry, theFilterKey, theMatch, theFilter);
		}

		@Override
		public Iterable<Node<K, V>> allEntries() {
			return wrap(theParent.getEntries(theFilterKey, theMatch, false), false);
		}

		@Override
		public Iterable<Node<K, V>> entries() {
			return wrap(theParent.getEntries(theFilterKey, theMatch, true), false);
		}

		@Override
		public int hashCode() {
			return MultiInheritanceView.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return MultiInheritanceView.equals(this, obj);
		}

		@Override
		public String toString() {
			return MultiInheritanceView.toString(this);
		}

		protected Iterable<Node<K, V>> wrap(Iterable<Node<K, V>> parentNodes, boolean filter) {
			Iterable<Node<K, V>> filtered;
			K filterKey = getFilterKey();
			if (filter) {
				filtered = IterableUtils.filter(parentNodes, n -> theFilter.test(n.getKey()));
			} else
				filtered = parentNodes;
			return IterableUtils.map(filtered, n -> new SubViewNode<>(n, filterKey, theMatch, theFilter));
		}
	}

	/**
	 * A node in a {@link MultiInheritanceView#subView(Object, boolean) sub-view}
	 * 
	 * @param <K> The type of keys in the map
	 * @param <V> The type of values in the map
	 */
	static class SubViewNode<K, V> extends SubView<K, V> implements Node<K, V> {
		public SubViewNode(Node<K, V> parent, K key, TypeMatch match, Predicate<K> filter) {
			super(parent, key, match, filter);
		}

		@Override
		protected Node<K, V> getParent() {
			return (Node<K, V>) super.getParent();
		}

		@Override
		public K getKey() {
			return getParent().getKey();
		}

		@Override
		public V getValue() {
			return getParent().getValue();
		}

		@Override
		public V setValue(V value) {
			return null;
		}

		@Override
		public Iterable<Node<K, V>> getParents() {
			return wrap(getParent().getParents(), true);
		}

		@Override
		public Iterable<Node<K, V>> getChildren() {
			return wrap(getParent().getChildren(), false);
		}

		@Override
		public Node<K, V> remove(boolean andAllDescendants) {
			getParent().remove(andAllDescendants);
			return this;
		}

		@Override
		public int hashCode() {
			return MultiInheritanceView.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return MultiInheritanceView.equals(this, obj);
		}

		@Override
		public String toString() {
			return append(new StringBuilder(), 0).toString();
		}
	}

	/**
	 * Default implementation for {@link MultiInheritanceView#unmodifiable()}
	 * 
	 * @param <K> The type of keys in the map
	 * @param <V> The type of values in the map
	 */
	static class UnmodifiableView<K, V> implements MultiInheritanceView<K, V> {
		private final MultiInheritanceView<K, V> theWrapped;

		public UnmodifiableView(MultiInheritanceView<K, V> toWrap) {
			theWrapped = toWrap;
		}

		protected MultiInheritanceView<K, V> getWrapped() {
			return theWrapped;
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		public Inheritance<K> getInheritance() {
			return theWrapped.getInheritance();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public Iterable<Node<K, V>> getRoots() {
			return wrap(theWrapped.getRoots());
		}

		@Override
		public Iterable<Node<K, V>> allEntries() {
			return wrap(theWrapped.allEntries());
		}

		@Override
		public Iterable<Node<K, V>> entries() {
			return wrap(theWrapped.entries());
		}

		@Override
		public Node<K, V> getExactEntry(K key) {
			Node<K, V> exact = theWrapped.getExactEntry(key);
			return exact == null ? null : new UnmodifiableNode<>(exact);
		}

		@Override
		public MultiInheritanceView<K, V> unmodifiable() {
			return this;
		}

		protected Iterable<Node<K, V>> wrap(Iterable<Node<K, V>> parentNodes) {
			return IterableUtils.map(parentNodes, n -> new UnmodifiableNode<>(n));
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * A node in an {@link MultiInheritanceView#unmodifiable() unmodifiable} multi-inheritance map. Also the default implementation of
	 * {@link Node#unmodifiable()}.
	 * 
	 * @param <K> The type of keys in the map
	 * @param <V> The type of values in the map
	 */
	static class UnmodifiableNode<K, V> extends UnmodifiableView<K, V> implements Node<K, V> {
		public UnmodifiableNode(Node<K, V> parent) {
			super(parent);
		}

		@Override
		protected Node<K, V> getWrapped() {
			return (Node<K, V>) super.getWrapped();
		}

		@Override
		public K getKey() {
			return getWrapped().getKey();
		}

		@Override
		public V getValue() {
			return getWrapped().getValue();
		}

		@Override
		public V setValue(V value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Iterable<Node<K, V>> getRoots() {
			return wrap(getWrapped().getRoots());
		}

		@Override
		public Iterable<Node<K, V>> getParents() {
			return wrap(getWrapped().getParents());
		}

		@Override
		public Iterable<Node<K, V>> getChildren() {
			return wrap(getWrapped().getChildren());
		}

		@Override
		public Node<K, V> remove(boolean andAllDescendants) {
			throw new UnsupportedOperationException();
		}

		@Override
		public UnmodifiableNode<K, V> unmodifiable() {
			return this;
		}

		@Override
		public int hashCode() {
			return getWrapped().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return getWrapped().equals(obj);
		}

		@Override
		public String toString() {
			return getWrapped().toString();
		}
	}

	/**
	 * Default implementation for {@link MultiInheritanceView#empty()}
	 * 
	 * @param <K> The key-type in the map
	 * @param <V> The key-type in the map
	 */
	static class Empty<K, V> implements MultiInheritanceView<K, V> {
		private final Inheritance<K> theInheritance = new Inheritance<K>() {
			@Override
			public boolean isExtension(K superKey, K subKey) {
				return false;
			}

			@Override
			public Iterable<? extends K> getParents(K key) {
				return Collections.emptyList();
			}
		};

		@Override
		public long getStamp() {
			return 0;
		}

		@Override
		public Inheritance<K> getInheritance() {
			return theInheritance;
		}

		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public Iterable<Node<K, V>> getRoots() {
			return Collections.emptyList();
		}

		@Override
		public Iterable<Node<K, V>> allEntries() {
			return Collections.emptyList();
		}

		@Override
		public Iterable<Node<K, V>> entries() {
			return Collections.emptyList();
		}

		@Override
		public Node<K, V> getExactEntry(K key) {
			return null;
		}

		@Override
		public String toString() {
			return "{}";
		}
	}

	/**
	 * {@link Inheritance} based on another inheritance scheme translated by function mapping
	 * 
	 * @param <K1> The type of keys supported by the wrapped inheritance scheme
	 * @param <K2> The type of keys supported by this inheritance scheme
	 */
	static class MappedInheritance<K1, K2> implements Inheritance<K2> {
		private final Inheritance<K1> theWrapped;
		private final Function<? super K1, ? extends K2> theMap;
		private final Function<? super K2, ? extends K1> theReverse;

		public MappedInheritance(Inheritance<K1> wrapped, Function<? super K1, ? extends K2> map,
			Function<? super K2, ? extends K1> reverse) {
			theWrapped = wrapped;
			theMap = map;
			theReverse = reverse;
		}

		/** @return The inheritance scheme this inheritance is mapped from */
		public Inheritance<K1> getWrapped() {
			return theWrapped;
		}

		/** @return The function producing values for this inheritance from values in the wrapped inheritance scheme */
		public Function<? super K1, ? extends K2> getMap() {
			return theMap;
		}

		/** @return The function producing values for the wrapped inheritance from values in this inheritance scheme */
		public Function<? super K2, ? extends K1> getReverse() {
			return theReverse;
		}

		public K2 map(K1 key) {
			return theMap.apply(key);
		}

		public K1 reverse(K2 key) {
			return theReverse.apply(key);
		}

		@Override
		public boolean isExtension(K2 superKey, K2 subKey) {
			return theWrapped.isExtension(theReverse.apply(superKey), theReverse.apply(subKey));
		}

		@Override
		public Iterable<? extends K2> getParents(K2 key) {
			return IterableUtils.map(theWrapped.getParents(theReverse.apply(key)), theMap);
		}
	}

	/**
	 * Default implementation of {@link MultiInheritanceView#keyMap(Function, Function)}
	 * 
	 * @param <K1> The key-type of the wrapped inheritance view
	 * @param <K2> The key-type of this inheritance view
	 * @param <V> The value-type of the inheritance view
	 */
	static class KeyMappedView<K1, K2, V> implements MultiInheritanceView<K2, V> {
		private final MultiInheritanceView<K1, V> theWrapped;
		private final MappedInheritance<K1, K2> theInheritance;

		public KeyMappedView(MultiInheritanceView<K1, V> wrapped, MappedInheritance<K1, K2> inheritance) {
			theWrapped = wrapped;
			theInheritance = inheritance;
		}

		protected MultiInheritanceView<K1, V> getWrapped() {
			return theWrapped;
		}

		@Override
		public MappedInheritance<K1, K2> getInheritance() {
			return theInheritance;
		}

		@Override
		public long getStamp() {
			return theWrapped.getStamp();
		}

		@Override
		public boolean isEmpty() {
			return theWrapped.isEmpty();
		}

		@Override
		public Iterable<Node<K2, V>> getRoots() {
			return wrap(theWrapped.getRoots());
		}

		@Override
		public Iterable<Node<K2, V>> allEntries() {
			return wrap(theWrapped.allEntries());
		}

		@Override
		public Iterable<Node<K2, V>> entries() {
			return wrap(theWrapped.entries());
		}

		@Override
		public Node<K2, V> getExactEntry(K2 key) {
			Node<K1, V> exact = theWrapped.getExactEntry(theInheritance.reverse(key));
			return exact == null ? null : new KeyMappedNode<>(exact, theInheritance);
		}

		@Override
		public int hashCode() {
			return MultiInheritanceView.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return MultiInheritanceView.equals(this, obj);
		}

		@Override
		public String toString() {
			return MultiInheritanceView.toString(this);
		}

		Iterable<Node<K2, V>> wrap(Iterable<Node<K1, V>> nodes) {
			return IterableUtils.map(nodes, n -> new KeyMappedNode<>(n, theInheritance));
		}
	}

	/**
	 * A node in a {@link KeyMappedView}
	 * 
	 * @param <K1> The key-type of the wrapped inheritance view
	 * @param <K2> The key-type of this inheritance view
	 * @param <V> The value-type of the inheritance view
	 */
	static class KeyMappedNode<K1, K2, V> extends KeyMappedView<K1, K2, V> implements Node<K2, V> {
		public KeyMappedNode(Node<K1, V> wrapped, MappedInheritance<K1, K2> inheritance) {
			super(wrapped, inheritance);
		}

		@Override
		protected Node<K1, V> getWrapped() {
			return (Node<K1, V>) super.getWrapped();
		}

		@Override
		public K2 getKey() {
			return getInheritance().map(getWrapped().getKey());
		}

		@Override
		public V getValue() {
			return getWrapped().getValue();
		}

		@Override
		public V setValue(V value) {
			return getWrapped().setValue(value);
		}

		@Override
		public Iterable<Node<K2, V>> getParents() {
			return wrap(getWrapped().getParents());
		}

		@Override
		public Iterable<Node<K2, V>> getChildren() {
			return wrap(getWrapped().getChildren());
		}

		@Override
		public Node<K2, V> remove(boolean andAllDescendants) {
			getWrapped().remove(andAllDescendants);
			return this;
		}

		@Override
		public int hashCode() {
			return MultiInheritanceView.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return MultiInheritanceView.equals(this, obj);
		}

		@Override
		public String toString() {
			return append(new StringBuilder(), 0).toString();
		}
	}

	/**
	 * Default implementation for {@link MultiInheritanceMap2#keyMap(Function, Function)}
	 * 
	 * @param <K1> The key-type of the wrapped inheritance map
	 * @param <K2> The key-type of this inheritance map
	 * @param <V> The value-type of the inheritance map
	 */
	static class KeyMappedMap<K1, K2, V> extends KeyMappedView<K1, K2, V> implements MultiInheritanceMap2<K2, V> {
		public KeyMappedMap(MultiInheritanceMap2<K1, V> wrapped, MappedInheritance<K1, K2> inheritance) {
			super(wrapped, inheritance);
		}

		@Override
		protected MultiInheritanceMap2<K1, V> getWrapped() {
			return (MultiInheritanceMap2<K1, V>) super.getWrapped();
		}

		@Override
		public Collection<Node<K2, V>> getRoots() {
			MappedInheritance<K1, K2> inh = getInheritance();
			return new MappedCollection<>(getWrapped().getRoots(), n -> new KeyMappedNode<>(n, inh));
		}

		@Override
		public Collection<Node<K2, V>> allEntries() {
			MappedInheritance<K1, K2> inh = getInheritance();
			return new MappedCollection<>(getWrapped().allEntries(), n -> new KeyMappedNode<>(n, inh));
		}

		@Override
		public Collection<Node<K2, V>> entries() {
			MappedInheritance<K1, K2> inh = getInheritance();
			return new MappedCollection<>(getWrapped().entries(), n -> new KeyMappedNode<>(n, inh));
		}

		@Override
		public int size() {
			return getWrapped().size();
		}

		@Override
		public <X extends Throwable> V computeEx(K2 key, ExBiFunction<? super K2, ? super V, ? extends V, X> value) throws X {
			return getWrapped().computeEx(getInheritance().reverse(key), (__, oldV) -> value.apply(key, oldV));
		}
	}
}
