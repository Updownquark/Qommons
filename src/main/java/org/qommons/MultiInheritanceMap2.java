package org.qommons;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.MultiInheritanceSet.Inheritance;
import org.qommons.collect.BetterList;
import org.qommons.ex.ExFunction;
import org.qommons.ex.ExSupplier;

/**
 * A map of values to classes. This structure is hierarchical, allowing query of either the most-specific or all values mapped to classes
 * which a given type extends.
 * 
 * @param <V> The type of values in the map
 */
public class MultiInheritanceMap2<K, V> {
	/**
	 * An action to perform on a node in the map tree
	 * 
	 * @param <V> The type of values in the map
	 */
	public interface MapEntryAction<K, V> {
		/**
		 * @param type The type of the encountered entry
		 * @param value The value of the encountered entry
		 * @param match The relationship of the entry's key with the target type of the query:
		 *        <ul>
		 *        <li>{@link TypeMatch#EXACT} if the entry's type and the target type are the same</li>
		 *        <li>{@link TypeMatch#SUB_TYPE} if the entry's type is a sub-type of the target type</li>
		 *        <li>{@link TypeMatch#SUPER_TYPE} if the entry's type is a super-type of the target type</li>
		 *        </ul>
		 * @return Whether the sub-maps of the given entry are of interest and should be descended into
		 */
		boolean onEntry(K type, V value, TypeMatch match);
	}

	/** An enum for the relationship between the type of a map entry and a specified target type */
	public enum TypeMatch {
		/** The entry's type is the same as the target type */
		EXACT,
		/** The entry's type is a sub-type of the target type */
		SUB_TYPE,
		/** The entry's type is a super-type of the target type */
		SUPER_TYPE;
	}

	static class MatchAcceptingAction<K, V> implements MapEntryAction<K, V> {
		private final TypeMatch theAcceptType;
		private final boolean isWithValueOnly;
		private final MapEntryAction<K, V> theAction;

		public MatchAcceptingAction(TypeMatch acceptType, boolean withValueOnly, MapEntryAction<K, V> action) {
			theAcceptType = acceptType;
			isWithValueOnly = withValueOnly;
			theAction = action;
		}

		@Override
		public boolean onEntry(K key, V value, TypeMatch match) {
			boolean descend = false;
			switch (match) {
			case EXACT:
				if (!isWithValueOnly || value != null)
					descend = theAction.onEntry(key, value, match);
				else
					descend = true;
				if (descend && theAcceptType != null)
					descend = theAcceptType == TypeMatch.SUB_TYPE;
				break;
			case SUB_TYPE:
				if (theAcceptType == null || theAcceptType == TypeMatch.SUB_TYPE) {
					if (!isWithValueOnly || value != null)
						descend = theAction.onEntry(key, value, match);
					else
						descend = true;
				} else
					descend = false;
				break;
			case SUPER_TYPE:
				if ((theAcceptType == null || theAcceptType == TypeMatch.SUPER_TYPE) && (!isWithValueOnly || value != null))
					descend = theAction.onEntry(key, value, match);
				else
					descend = true;
				break;
			}
			return descend;
		}
	}

	class ClassMapEntry {
		private final K theKey;
		private final List<ClassMapEntry> theSubMaps;
		private V theValue;
		private int theSize;

		/** @param key The super-value of keys that this entry can hold */
		ClassMapEntry(K key) {
			theKey = key;
			theSubMaps = new ArrayList<>(3);
		}

		/** @return The super-value of keys that this entry can hold */
		K getType() {
			return theKey;
		}

		/** @return The value stored for this map's {@link #getType() type} */
		V getLocalValue() {
			return theValue;
		}

		/** @return The number of values stored in this map */
		int size() {
			return theSize;
		}

		ClassMapEntry with(K key, V value) {
			compute(key, old -> value);
			return this;
		}

		void descend(K targetKey, MapEntryAction<K, V> action, boolean subTarget) {
			boolean descend;
			if (subTarget)
				descend = action.onEntry(theKey, theValue, TypeMatch.SUB_TYPE);
			else if (theKey == null)
				descend = true;
			else if (targetKey == theKey) {
				subTarget = true;
				descend = action.onEntry(theKey, theValue, TypeMatch.EXACT);
			} else if (targetKey == null || theInheritance.isExtension(targetKey, theKey)) {
				subTarget = true;
				descend = action.onEntry(theKey, theValue, TypeMatch.SUB_TYPE);
			} else if (theInheritance.isExtension(theKey, targetKey))
				descend = action.onEntry(theKey, theValue, TypeMatch.SUPER_TYPE);
			else
				descend = false;
			if (descend) {
				for (ClassMapEntry subMap : theSubMaps)
					subMap.descend(targetKey, action, subTarget);
			}
		}

		V compute(K key, Function<? super V, ? extends V> value) {
			return computeEx(key, ExFunction.of(value));
		}

		<E extends Throwable> V computeEx(K key, ExFunction<? super V, ? extends V, E> value) throws E {
			if (key == theKey) {
				boolean wasNull = theValue == null;
				theValue = value == null ? null : value.apply(theValue);
				if (wasNull) {
					if (theValue != null)
						theSize++;
				} else if (theValue == null)
					theSize--;
				return theValue;
			}
			V newValue = null;
			boolean found = false;
			ListIterator<ClassMapEntry> subMapIter = theSubMaps.listIterator();
			while (subMapIter.hasNext()) {
				ClassMapEntry subMap = subMapIter.next();
				if (theInheritance.isExtension(subMap.theKey, key)) {
					found = true;
					int preSize = subMap.size();
					newValue = (subMap).computeEx(key, value);
					theSize += subMap.size() - preSize;
					if (subMap.size() == 0)
						subMapIter.remove();
					else if (subMap.theValue == null && subMap.theSubMaps.size() == 1)
						subMapIter.set(subMap.theSubMaps.get(0));
				}
			}
			if (!found) {
				newValue = value == null ? null : value.apply(null);
				if (newValue == null)
					return null;
				ClassMapEntry newSubMap = new ClassMapEntry(key).with(key, newValue);
				subMapIter = theSubMaps.listIterator();
				while (subMapIter.hasNext()) {
					ClassMapEntry subMap = subMapIter.next();
					if (theInheritance.isExtension(key, subMap.theKey)) {
						newSubMap.theSubMaps.add(subMap);
						subMapIter.remove();
					}
				}

				theSubMaps.add(newSubMap);
				theSize++;
			}
			return newValue;
		}

		void _putAll(MultiInheritanceMap2<? extends K, ? extends V>.ClassMapEntry other) {
			if (other.theValue != null)
				with(other.theKey, other.theValue);
			for (MultiInheritanceMap2<? extends K, ? extends V>.ClassMapEntry child : other.theSubMaps)
				_putAll(child);
		}

		void clear() {
			theValue = null;
			theSubMaps.clear();
			theSize = 0;
		}

		List<BiTuple<K, V>> _pathTo(K key, List<BiTuple<K, V>> list) {
			if (theValue != null)
				list.add(new BiTuple<>(theKey, theValue));
			if (key == theKey)
				return list;
			for (ClassMapEntry subMap : theSubMaps) {
				if (theInheritance.isExtension(subMap.theKey, key))
					return subMap._pathTo(key, list);
			}
			return list;
		}

		Set<K> getTopLevelKeys(Set<K> keys) {
			if (theValue != null)
				keys.add(theKey);
			else {
				for (ClassMapEntry subMap : theSubMaps)
					subMap.getTopLevelKeys(keys);
			}
			return keys;
		}

		<L extends Collection<V>> L getAllValues(L values) {
			if(theValue!=null)
				values.add(theValue);
			for (ClassMapEntry sub : theSubMaps)
				sub.getAllValues(values);
			return values;
		}

		StringBuilder append(StringBuilder str, int indent) {
			for (int i = 0; i < indent; i++)
				str.append('\t');
			str.append(theKey == null ? "<root>" : theKey);
			if (theValue != null)
				str.append('=').append(theValue);
			for (ClassMapEntry child : theSubMaps)
				child.append(str.append('\n'), indent + 1);
			return str;
		}

		@Override
		public String toString() {
			return append(new StringBuilder(), 0).toString();
		}
	}

	private final MultiInheritanceSet.Inheritance<? super K> theInheritance;
	private final ClassMapEntry theRoot = new ClassMapEntry(null);

	public MultiInheritanceMap2(Inheritance<? super K> inheritance) {
		theInheritance = inheritance;
	}

	/**
	 * A generalized query method into the map tree
	 * 
	 * @param key The key to query for
	 * @param action The action to perform on map entries whose type has an inheritance relationship to the target type
	 */
	public void descend(K key, MapEntryAction<K, V> action) {
		theRoot.descend(key, action, false);
	}

	/** @return Whether this map has no values in it */
	public boolean isEmpty() {
		return theRoot.getLocalValue() == null && theRoot.size() == 0;
	}

	/**
	 * @param key The key to query with
	 * @param acceptMatches The type of type-match to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the value for exactly the given type (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return the value for the given type or its least specific sub-type</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return the value for the given type or its most specific super-type</li>
	 *        <li><code>null</code> to return the first encountered value for a type that is related to the given type in either
	 *        direction</li>
	 *        </ul>
	 * @return The value for the queried type
	 */
	public synchronized V get(K key, TypeMatch acceptMatches) {
		Object[] found = new Object[1];
		descend(key, //
			new MatchAcceptingAction<>(acceptMatches, true, (type2, value, match) -> {
				found[0] = value;
				return match == TypeMatch.SUPER_TYPE;
			}));
		return (V) found[0];
	}

	/**
	 * @param key The key to query with
	 * @param acceptMatches The type of type-match to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the value for exactly the given type (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return the value for the given type or its least specific sub-type</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return the value for the given type or its most specific super-type</li>
	 *        <li><code>null</code> to return the first encountered value for a type that is related to the given type in either
	 *        direction</li>
	 *        </ul>
	 * @return The value for the queried type (as {@link BiTuple#getValue2() value 2} with the actual class the value was stored under (as
	 *         {@link BiTuple#getValue1() value 1}
	 */
	public synchronized BiTuple<K, V> getEntry(K key, TypeMatch acceptMatches) {
		BiTuple<K, V>[] found = new BiTuple[1];
		descend(key, //
			new MatchAcceptingAction<>(acceptMatches, true, (type2, value, match) -> {
				found[0] = new BiTuple<>(type2, value);
				return match == TypeMatch.SUPER_TYPE;
			}));
		return found[0];
	}

	/**
	 * @param key The key to query with
	 * @param acceptMatches The type of type-match to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the value for exactly the given type (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return the value for the given type or its least specific sub-type</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return the value for the given type or its most specific super-type</li>
	 *        <li><code>null</code> to return the first encountered value for a type that is related to the given type in either
	 *        direction</li>
	 *        </ul>
	 * @param defaultValue The value to return if no matching value is stored in this map
	 * @return The value for the queried type
	 */
	public synchronized V getOrDefault(K key, TypeMatch acceptMatches, V defaultValue) {
		V value = get(key, acceptMatches);
		return value != null ? value : defaultValue;
	}

	/**
	 * @param key The key to query with
	 * @param acceptMatches The type of type-matches to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the value for exactly the given type (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return values for the given type and all sub-types</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return values for the given type and all super-types</li>
	 *        <li><code>null</code> to return all values that are related to the given type in either direction</li>
	 *        </ul>
	 * @return All values mapped to types matching the query
	 */
	public synchronized BetterList<V> getAll(K key, TypeMatch acceptMatches) {
		List<V>[] values = new List[1];
		descend(key, //
			new MatchAcceptingAction<>(acceptMatches, true, (type2, value, match) -> {
				if (values[0] == null)
					values[0] = new ArrayList<>(5);
				values[0].add(value);
				return true;
			}));
		return values[0] == null ? BetterList.empty() : BetterList.of(values[0]);
	}

	/** @return A set containing all top-level classes with values in this map */
	public synchronized Set<K> getTopLevelKeys() {
		if (isEmpty())
			return Collections.emptySet();
		return theRoot.getTopLevelKeys(new LinkedHashSet<>());
	}

	/**
	 * @param key The key to query with
	 * @param acceptMatches The type of type-matches to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the entries for exactly the given type (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return entries for the given type and all sub-types</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return entries for the given type and all super-types</li>
	 *        <li><code>null</code> to return entries that are related to the given type in either direction</li>
	 *        </ul>
	 * @return All entries mapped to types matching the query
	 */
	public synchronized BetterList<BiTuple<K, V>> getAllEntries(K key, TypeMatch acceptMatches) {
		List<BiTuple<K, V>>[] entries = new List[1];
		descend(key, //
			new MatchAcceptingAction<>(acceptMatches, true, (type2, value, match) -> {
				if (entries[0] == null)
					entries[0] = new ArrayList<>(5);
				entries[0].add(new BiTuple<>(type2, value));
				return true;
			}));
		return entries[0] == null ? BetterList.empty() : BetterList.of(entries[0]);
	}

	/**
	 * @param key The class to query with
	 * @return All entries mapped to sub-types of the given type
	 */
	public synchronized BetterList<BiTuple<K, V>> getAllSubEntries(K key) {
		return (BetterList<BiTuple<K, V>>) (List<?>) getAllEntries(key, TypeMatch.SUB_TYPE);
	}

	/**
	 * @param key The key to query with
	 * @return All entries mapped to sub-types of the given type
	 */
	public synchronized BetterList<BiTuple<K, V>> getAllSuperEntries(K key) {
		return getAllEntries(key, TypeMatch.SUPER_TYPE);
	}

	/** @return All entries in this map */
	public synchronized BetterList<BiTuple<K, V>> getAllEntries() {
		return getAllEntries(null, null);
	}

	/** @return All values in this map */
	public synchronized BetterList<V> getAllValues() {
		if (isEmpty())
			return BetterList.empty();
		return BetterList.of(theRoot.getAllValues(new ArrayList<>()));
	}

	/**
	 * Maps a value to a key
	 * 
	 * @param key The key to map the value to
	 * @param value The value to map to the class
	 * @return This map
	 */
	public synchronized MultiInheritanceMap2<K, V> with(K key, V value) {
		theRoot.compute(key, old -> value);
		return this;
	}

	/**
	 * Synonym for {@link #with(Object, Object)}
	 * 
	 * @param key The key to map the value to
	 * @param value The value to map to the key
	 * @return This map
	 */
	public synchronized MultiInheritanceMap2<K, V> put(K key, V value) {
		return with(key, value);
	}

	/**
	 * Maps a value to the key unless one is already mapped
	 * 
	 * @param key The key to map the value to
	 * @param value The value to map to the key if one is not already mapped
	 * @return The new value mapped to the key
	 */
	public synchronized V computeIfAbsent(K key, Supplier<V> value) {
		return theRoot.compute(key, old -> old != null ? old : value.get());
	}

	/**
	 * Maps a value to the key unless one is already mapped
	 * 
	 * @param <E> The type of exception that the supplier may throw
	 * @param key The key to map the value to
	 * @param value The value to map to the key if one is not already mapped
	 * @return The new value mapped to the key
	 * @throws E If the supplier throws an exception when attempting to supply the missing value
	 */
	public synchronized <E extends Throwable> V computeIfAbsentEx(K key, ExSupplier<V, E> value) throws E {
		return theRoot.computeEx(key, old -> old != null ? old : value.get());
	}

	/**
	 * Alters the value mapped to a key
	 * 
	 * @param key The key to alter the mapped value for
	 * @param value The function to alter the value for the key
	 * @return The new value mapped to the key
	 */
	public synchronized V compute(K key, Function<? super V, ? extends V> value) {
		return theRoot.compute(key, value);
	}

	/**
	 * @param other Another subclass map whose values to put in this map
	 * @return This ClassMap
	 */
	public MultiInheritanceMap2<K, V> putAll(MultiInheritanceMap2<? extends K, ? extends V> other) {
		theRoot._putAll(other.theRoot);
		return this;
	}

	/**
	 * @param key The key to query for
	 * @return The path in this map to the most specific super-key of the given key
	 */
	public synchronized BetterList<BiTuple<K, V>> pathTo(K key) {
		return BetterList.of(theRoot._pathTo(key, new ArrayList<>()));
	}

	/** Removes all values from this map */
	public void clear() {
		theRoot.clear();
	}

	/** @return An independent copy of this map */
	public MultiInheritanceMap2<K, V> copy() {
		return new MultiInheritanceMap2<K, V>(theInheritance).putAll(this);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		theRoot.append(str, 0);
		return str.toString();
	}
}
