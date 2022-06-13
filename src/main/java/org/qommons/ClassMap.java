package org.qommons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.collect.BetterList;

/**
 * A map of values to classes. This structure is hierarchical, allowing query of either the most-specific or all values mapped to classes
 * which a given type extends.
 * 
 * @param <V> The type of values in the map
 */
public class ClassMap<V> {
	/**
	 * An action to perform on a node in the map tree
	 * 
	 * @param <V> The type of values in the map
	 */
	public interface MapEntryAction<V> {
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
		boolean onEntry(Class<?> type, V value, TypeMatch match);
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

	static class MatchAcceptingAction<V> implements MapEntryAction<V> {
		private final TypeMatch theAcceptType;
		private final boolean isWithValueOnly;
		private final MapEntryAction<V> theAction;

		public MatchAcceptingAction(TypeMatch acceptType, boolean withValueOnly, MapEntryAction<V> action) {
			theAcceptType = acceptType;
			isWithValueOnly = withValueOnly;
			theAction = action;
		}

		@Override
		public boolean onEntry(Class<?> type, V value, TypeMatch match) {
			boolean descend = false;
			switch (match) {
			case EXACT:
				if (!isWithValueOnly || value != null)
					descend = theAction.onEntry(type, value, match);
				else
					descend = true;
				if (descend && theAcceptType != null)
					descend = theAcceptType == TypeMatch.SUB_TYPE;
				break;
			case SUB_TYPE:
				if (theAcceptType == null || theAcceptType == TypeMatch.SUB_TYPE) {
					if (!isWithValueOnly || value != null)
						descend = theAction.onEntry(type, value, match);
					else
						descend = true;
				} else
					descend = false;
				break;
			case SUPER_TYPE:
				if ((theAcceptType == null || theAcceptType == TypeMatch.SUPER_TYPE) && (!isWithValueOnly || value != null))
					descend = theAction.onEntry(type, value, match);
				else
					descend = true;
				break;
			}
			return descend;
		}
	}

	static class ClassMapEntry<C, V> {
		private final Class<C> theType;
		private final List<ClassMapEntry<? extends C, V>> theSubMaps;
		private V theValue;
		private int theSize;

		/** @param type The super-type of classes that this subclass map can support */
		ClassMapEntry(Class<C> type) {
			theType = type;
			theSubMaps = new ArrayList<>(3);
		}

		/** @return The super-type of classes that this subclass map can support */
		Class<C> getType() {
			return theType;
		}

		/** @return The value stored for this map's {@link #getType() type} */
		V getLocalValue() {
			return theValue;
		}

		/** @return The number of values stored in this map */
		int size() {
			return theSize;
		}

		ClassMapEntry<C, V> with(Class<? extends C> type, V value) {
			compute(type, old -> value);
			return this;
		}

		void descend(Class<?> targetType, MapEntryAction<V> action, boolean subTarget) {
			boolean descend;
			if (subTarget)
				descend = action.onEntry(theType, theValue, TypeMatch.SUB_TYPE);
			else if (theType == null)
				descend = true;
			else if (targetType == theType) {
				subTarget = true;
				descend = action.onEntry(theType, theValue, TypeMatch.EXACT);
			} else if (targetType.isAssignableFrom(theType)) {
				subTarget = true;
				descend = action.onEntry(theType, theValue, TypeMatch.SUB_TYPE);
			} else if (theType.isAssignableFrom(targetType))
				descend = action.onEntry(theType, theValue, TypeMatch.SUPER_TYPE);
			else
				descend = false;
			if (descend) {
				for (ClassMapEntry<? extends C, V> subMap : theSubMaps)
					subMap.descend(targetType, action, subTarget);
			}
		}

		<C2 extends C> V compute(Class<C2> type, Function<? super V, ? extends V> value) {
			type = wrap(type);
			if (type == theType) {
				boolean wasNull = theValue == null;
				theValue = value.apply(theValue);
				if (wasNull) {
					if (theValue != null)
						theSize++;
				} else if (theValue == null)
					theSize--;
				return theValue;
			}
			V newValue = null;
			boolean found = false;
			ListIterator<ClassMapEntry<? extends C, V>> subMapIter = theSubMaps.listIterator();
			while (subMapIter.hasNext()) {
				ClassMapEntry<? extends C, V> subMap = subMapIter.next();
				if (subMap.theType.isAssignableFrom(type)) {
					found = true;
					int preSize = subMap.size();
					newValue = ((ClassMapEntry<? super C2, V>) subMap).compute(type, value);
					theSize += subMap.size() - preSize;
					if (subMap.size() == 0)
						subMapIter.remove();
					else if (subMap.theValue == null && subMap.theSubMaps.size() == 1)
						subMapIter.set(subMap.theSubMaps.get(0));
					break;
				}
			}
			if (!found) {
				newValue = value.apply(null);
				if (newValue == null)
					return null;
				ClassMapEntry<C2, V> newSubMap = new ClassMapEntry<C2, V>(type).with(type, newValue);
				subMapIter = theSubMaps.listIterator();
				while (subMapIter.hasNext()) {
					ClassMapEntry<? extends C, V> subMap = subMapIter.next();
					if (type.isAssignableFrom(subMap.theType)) {
						newSubMap.theSubMaps.add((ClassMapEntry<? extends C2, V>) subMap);
						subMapIter.remove();
					}
				}

				theSubMaps.add(newSubMap);
				theSize++;
			}
			return newValue;
		}

		void _putAll(ClassMapEntry<? extends C, ? extends V> other) {
			if (other.theValue != null)
				with(other.theType, other.theValue);
			for (ClassMapEntry<? extends C, ? extends V> child : other.theSubMaps)
				_putAll(child);
		}

		void clear() {
			theValue = null;
			theSubMaps.clear();
			theSize = 0;
		}

		<C2 extends C> List<? extends BiTuple<Class<? super C2>, V>> _pathTo(Class<C2> type, List<BiTuple<Class<? super C2>, V>> list) {
			if (theValue != null)
				list.add(new BiTuple<>(theType, theValue));
			if (type == theType)
				return list;
			for (ClassMapEntry<? extends C, V> subMap : theSubMaps) {
				if (subMap.theType.isAssignableFrom(type))
					return ((ClassMapEntry<? super C2, V>) subMap)._pathTo(type, list);
			}
			return list;
		}

		Set<Class<?>> getTopLevelKeys(Set<Class<?>> keys) {
			if (theValue != null)
				keys.add(theType);
			else {
				for (ClassMapEntry<? extends C, V> subMap : theSubMaps)
					subMap.getTopLevelKeys(keys);
			}
			return keys;
		}

		void append(StringBuilder str, int indent) {
			for (int i = 0; i < indent; i++)
				str.append('\t');
			str.append(theType.getName());
			if (theValue != null)
				str.append('=').append(theValue);
			for (ClassMapEntry<? extends C, V> child : theSubMaps)
				child.append(str.append('\n'), indent + 1);
		}
	}

	private final ClassMapEntry<Object, V> theRoot = new ClassMapEntry<>(null);

	private static <T> Class<T> wrap(Class<T> type) {
		if (!type.isPrimitive())
			return type;
		else if (type == boolean.class)
			return (Class<T>) Boolean.class;
		else if (type == int.class)
			return (Class<T>) Integer.class;
		else if (type == double.class)
			return (Class<T>) Double.class;
		else if (type == long.class)
			return (Class<T>) Long.class;
		else if (type == char.class)
			return (Class<T>) Character.class;
		else if (type == byte.class)
			return (Class<T>) Byte.class;
		else if (type == float.class)
			return (Class<T>) Float.class;
		else if (type == short.class)
			return (Class<T>) Short.class;
		else
			throw new IllegalStateException("Unrecognized primitive type: " + type.getName());
	}

	/**
	 * A generalized query method into the map tree
	 * 
	 * @param targetType The type to query for
	 * @param action The action to perform on map entries whose type has an inheritance relationship to the target type
	 */
	public void descend(Class<?> targetType, MapEntryAction<V> action) {
		targetType = wrap(targetType);
		theRoot.descend(targetType, action, false);
	}

	/** @return Whether this map has no values in it */
	public boolean isEmpty() {
		return theRoot.getLocalValue() == null && theRoot.size() == 0;
	}

	/**
	 * @param type The class to query with
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
	public synchronized V get(Class<?> type, TypeMatch acceptMatches) {
		Object[] found = new Object[1];
		descend(type, //
			new MatchAcceptingAction<>(acceptMatches, true, (type2, value, match) -> {
				found[0] = value;
				return match == TypeMatch.SUPER_TYPE;
			}));
		return (V) found[0];
	}

	/**
	 * @param type The class to query with
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
	public synchronized V getOrDefault(Class<?> type, TypeMatch acceptMatches, V defaultValue) {
		V value = get(type, acceptMatches);
		return value != null ? value : defaultValue;
	}

	/**
	 * @param type The class to query with
	 * @param acceptMatches The type of type-matches to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the value for exactly the given type (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return values for the given type and all sub-types</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return values for the given type and all super-types</li>
	 *        <li><code>null</code> to return all values that are related to the given type in either direction</li>
	 *        </ul>
	 * @return All values mapped to types matching the query
	 */
	public synchronized List<V> getAll(Class<?> type, TypeMatch acceptMatches) {
		List<V>[] values = new List[1];
		descend(type, //
			new MatchAcceptingAction<>(acceptMatches, true, (type2, value, match) -> {
				if (values[0] == null)
					values[0] = new ArrayList<>(5);
				values[0].add(value);
				return true;
			}));
		return values[0] == null ? Collections.emptyList() : values[0];
	}

	/** @return A set containing all top-level classes with values in this map */
	public synchronized Set<Class<?>> getTopLevelKeys() {
		if (isEmpty())
			return Collections.emptySet();
		return theRoot.getTopLevelKeys(new LinkedHashSet<>());
	}

	/**
	 * @param type The class to query with
	 * @param acceptMatches The type of type-matches to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the entries for exactly the given type (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return entries for the given type and all sub-types</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return entries for the given type and all super-types</li>
	 *        <li><code>null</code> to return entries that are related to the given type in either direction</li>
	 *        </ul>
	 * @return All entries mapped to types matching the query
	 */
	public synchronized List<BiTuple<Class<?>, V>> getAllEntries(Class<?> type, TypeMatch acceptMatches) {
		List<BiTuple<Class<?>, V>>[] entries = new List[1];
		descend(type, //
			new MatchAcceptingAction<>(acceptMatches, true, (type2, value, match) -> {
				if (entries[0] == null)
					entries[0] = new ArrayList<>(5);
				entries[0].add(new BiTuple<>(type2, value));
				return true;
			}));
		return entries[0] == null ? Collections.emptyList() : entries[0];
	}

	/**
	 * @param <C> The type to query with
	 * @param type The class to query with
	 * @return All entries mapped to sub-types of the given type
	 */
	public synchronized <C> List<BiTuple<Class<? extends C>, V>> getAllSubEntries(Class<C> type) {
		return (List<BiTuple<Class<? extends C>, V>>) (List<?>) getAllEntries(type, TypeMatch.SUB_TYPE);
	}

	/**
	 * @param <C> The type to query with
	 * @param type The class to query with
	 * @return All entries mapped to sub-types of the given type
	 */
	public synchronized <C> List<BiTuple<Class<? super C>, V>> getAllSuperEntries(Class<C> type) {
		return (List<BiTuple<Class<? super C>, V>>) (List<?>) getAllEntries(type, TypeMatch.SUPER_TYPE);
	}

	/** @return All entries in this map */
	public synchronized List<BiTuple<Class<?>, V>> getAllEntries() {
		return getAllEntries(Object.class, null);
	}

	/**
	 * Maps a value to a class
	 * 
	 * @param type The class to map the value to
	 * @param value The value to map to the class
	 * @return This map
	 */
	public synchronized ClassMap<V> with(Class<?> type, V value) {
		theRoot.compute(type, old -> value);
		return this;
	}

	/**
	 * Maps a value to the class unless one is already mapped
	 * 
	 * @param type The class to map the value to
	 * @param value The value to map to the class if one is not already mapped
	 * @return The new value mapped to the class
	 */
	public synchronized V computeIfAbsent(Class<?> type, Supplier<V> value) {
		return theRoot.compute(type, old -> old != null ? old : value.get());
	}

	/**
	 * Alters the value mapped to a class
	 * 
	 * @param type The class to alter the mapped value for
	 * @param value The function to alter the value for the class
	 * @return The new value mapped to the class
	 */
	public synchronized V compute(Class<?> type, Function<? super V, ? extends V> value) {
		return theRoot.compute(type, value);
	}

	/**
	 * @param other Another subclass map whose values to put in this map
	 * @return This ClassMap
	 */
	public ClassMap<V> putAll(ClassMap<? extends V> other) {
		theRoot._putAll(other.theRoot);
		return this;
	}

	/**
	 * @param <C> The type to query for
	 * @param type The class to query for
	 * @return The path in this map to the most specific super-class of the given class
	 */
	public synchronized <C> BetterList<BiTuple<Class<? super C>, V>> pathTo(Class<C> type) {
		return BetterList.of(theRoot._pathTo(type, new ArrayList<>()));
	}

	/** Removes all values from this map */
	public void clear() {
		theRoot.clear();
	}

	/** @return An independent copy of this class map */
	public ClassMap<V> copy() {
		return new ClassMap<V>().putAll(this);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		theRoot.append(str, 0);
		return str.toString();
	}
}
