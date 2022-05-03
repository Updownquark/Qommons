package org.qommons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.collect.BetterList;

/**
 * A map of values to classes. This structure is hierarchical, allowing query of either the most-specific or all values mapped to classes
 * which a given type extends.
 * 
 * @param <C> The super-type of classes that this subclass map can support
 * @param <V> The type of values in the map
 */
public class SubClassMap2<C, V> {
	public interface MapEntryAction<C, V> {
		boolean onEntry(SubClassMap2<? extends C, V> entry, TypeMatch match);
	}

	public enum TypeMatch {
		/** The entry's type is the same as the target type */
		EXACT,
		/** The entry's type is a sub-type of the target type */
		SUB_TYPE,
		/** The entry's type is a super-type of the target type */
		SUPER_TYPE;
	}

	static class MatchAcceptingAction<C, V> implements MapEntryAction<C, V> {
		private final TypeMatch theAcceptType;
		private final boolean isWithValueOnly;
		private final MapEntryAction<C, V> theAction;

		public MatchAcceptingAction(TypeMatch acceptType, boolean withValueOnly, MapEntryAction<C, V> action) {
			theAcceptType = acceptType;
			isWithValueOnly = withValueOnly;
			theAction = action;
		}

		@Override
		public boolean onEntry(SubClassMap2<? extends C, V> entry, TypeMatch match) {
			boolean descend = false;
			switch (match) {
			case EXACT:
				if (!isWithValueOnly || entry.getLocalValue() != null)
					descend = theAction.onEntry(entry, match);
				else
					descend = true;
				if (descend)
					descend = theAcceptType == TypeMatch.SUB_TYPE;
				break;
			case SUB_TYPE:
				if (theAcceptType == TypeMatch.SUB_TYPE) {
					if (!isWithValueOnly || entry.getLocalValue() != null)
						descend = theAction.onEntry(entry, match);
					else
						descend = true;
				} else
					descend = false;
				break;
			case SUPER_TYPE:
				if (theAcceptType == TypeMatch.SUPER_TYPE && !isWithValueOnly || entry.getLocalValue() != null)
					descend = theAction.onEntry(entry, match);
				else
					descend = true;
				break;
			}
			return descend;
		}
	}

	private final Class<C> theType;
	private final List<SubClassMap2<? extends C, V>> theSubMaps;
	private V theValue;
	private int theSize;

	/** @param type The super-type of classes that this subclass map can support */
	public SubClassMap2(Class<C> type) {
		theType = type;
		theSubMaps = new ArrayList<>(3);
	}

	/** @return The super-type of classes that this subclass map can support */
	public Class<C> getType() {
		return theType;
	}

	/** @return The value stored for this map's {@link #getType() type} */
	public V getLocalValue() {
		return theValue;
	}

	/** @return The number of values stored in this map */
	public int size() {
		return theSize;
	}

	public <C2 extends C> void descend(Class<C2> targetType, MapEntryAction<? super C, V> action) {
		if (!theType.isAssignableFrom(targetType))
			throw new IllegalArgumentException("Cannot query descend() with a target type that does not extend " + theType);
		_descend(targetType, action, false);
	}

	private <C2> void _descend(Class<C2> targetType, MapEntryAction<? super C, V> action, boolean subTarget) {
		boolean descend;
		if (subTarget)
			descend = action.onEntry(this, TypeMatch.SUB_TYPE);
		else if (targetType == theType) {
			subTarget = true;
			descend = action.onEntry(this, TypeMatch.EXACT);
		} else if (targetType.isAssignableFrom(theType)) {
			subTarget = true;
			descend = action.onEntry(this, TypeMatch.SUB_TYPE);
		} else if (theType.isAssignableFrom(targetType))
			descend = action.onEntry(this, TypeMatch.SUPER_TYPE);
		else
			descend = false;
		if (descend) {
			for (SubClassMap2<? extends C, V> subMap : theSubMaps)
				subMap._descend(targetType, action, subTarget);
		}
	}

	/**
	 * @param <C2> The type to query with
	 * @param type The class to query with
	 * @param acceptMatches The type of type-match to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the value for exactly the given type (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return the value for the given type or its least specific sub-type</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return the value for the given type or its most specific super-type</li>
	 *        </ul>
	 * @return The value for the queried type
	 */
	public synchronized <C2 extends C> V get(Class<C2> type, TypeMatch acceptMatches) {
		Object[] found = new Object[1];
		descend(type, //
			new MatchAcceptingAction<>(acceptMatches, true, (entry, match) -> {
				found[0] = entry.getLocalValue();
				return match == TypeMatch.SUPER_TYPE;
			}));
		return (V) found[0];
	}

	/**
	 * @param <C2> The type to query with
	 * @param type The class to query with
	 * @param acceptMatches The type of type-matches to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the value for exactly the given type (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return values for the given type and all sub-types</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return values for the given type and all super-types</li>
	 *        </ul>
	 * @return All values mapped to types matching the query
	 */
	public synchronized <C2 extends C> List<V> getAll(Class<C2> type, TypeMatch acceptMatches) {
		List<V>[] values = new List[1];
		descend(type, //
			new MatchAcceptingAction<>(acceptMatches, true, (entry, match) -> {
				if (values[0] == null)
					values[0] = new ArrayList<>(5);
				values[0].add(entry.getLocalValue());
				return true;
			}));
		return values[0] == null ? Collections.emptyList() : values[0];
	}

	/**
	 * @param <C2> The type to query with
	 * @param type The class to query with
	 * @param acceptMatches The type of type-matches to accept:
	 *        <ul>
	 *        <li>{@link TypeMatch#EXACT} to return only the entries for exactly the given type (if it exists)</li>
	 *        <li>{@link TypeMatch#SUB_TYPE} to return entries for the given type and all sub-types</li>
	 *        <li>{@link TypeMatch#SUPER_TYPE} to return entries for the given type and all super-types</li>
	 *        </ul>
	 * @return All entries mapped to types matching the query
	 */
	public synchronized <C2 extends C> List<BiTuple<Class<? extends C>, V>> getAllEntries(Class<C2> type, TypeMatch acceptMatches) {
		List<BiTuple<Class<? extends C>, V>>[] entries = new List[1];
		descend(type, //
			new MatchAcceptingAction<>(acceptMatches, true, (entry, match) -> {
				if (entries[0] == null)
					entries[0] = new ArrayList<>(5);
				entries[0].add(new BiTuple<>(entry.getType(), entry.getLocalValue()));
				return true;
			}));
		return entries[0] == null ? Collections.emptyList() : entries[0];
	}

	/** @return All entries in this map */
	public synchronized List<BiTuple<Class<? extends C>, V>> getAllEntries() {
		return getAllEntries(theType, TypeMatch.SUB_TYPE);
	}

	/**
	 * Maps a value to a class
	 * 
	 * @param <C2> The type to map the value to
	 * @param type The class to map the value to
	 * @param value The value to map to the class
	 * @return This map
	 */
	public synchronized <C2 extends C> SubClassMap2<C, V> with(Class<C2> type, V value) {
		_compute(type, old -> value);
		return this;
	}

	/**
	 * Maps a value to the class unless one is already mapped
	 * 
	 * @param <C2> The type to map the value to
	 * @param type The class to map the value to
	 * @param value The value to map to the class if one is not already mapped
	 * @return The new value mapped to the class
	 */
	public synchronized <C2 extends C> V computeIfAbsent(Class<C2> type, Supplier<V> value) {
		return _compute(type, old -> old != null ? old : value.get());
	}

	/**
	 * Alters the value mapped to a class
	 * 
	 * @param <C2> The type to alter the mapped value for
	 * @param type The class to alter the mapped value for
	 * @param value The function to alter the value for the class
	 * @return The new value mapped to the class
	 */
	public synchronized <C2 extends C> V compute(Class<C2> type, Function<? super V, ? extends V> value) {
		return _compute(type, value);
	}

	private <C2 extends C> V _compute(Class<C2> type, Function<? super V, ? extends V> value) {
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
		ListIterator<SubClassMap2<? extends C, V>> subMapIter = theSubMaps.listIterator();
		while (subMapIter.hasNext()) {
			SubClassMap2<? extends C, V> subMap = subMapIter.next();
			if (subMap.theType.isAssignableFrom(type)) {
				found = true;
				int preSize = subMap.size();
				newValue = ((SubClassMap2<? super C2, V>) subMap)._compute(type, value);
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
			SubClassMap2<C2, V> newSubMap = new SubClassMap2<C2, V>(type).with(type, newValue);
			subMapIter = theSubMaps.listIterator();
			while (subMapIter.hasNext()) {
				SubClassMap2<? extends C, V> subMap = subMapIter.next();
				if (type.isAssignableFrom(subMap.theType)) {
					newSubMap.theSubMaps.add((SubClassMap2<? extends C2, V>) subMap);
					subMapIter.remove();
				}
			}

			theSubMaps.add(newSubMap);
			theSize++;
		}
		return newValue;
	}

	/** @param other Another subclass map whose values to put in this map */
	public void putAll(SubClassMap2<? extends C, ? extends V> other) {
		_putAll(other);
	}

	private void _putAll(SubClassMap2<? extends C, ? extends V> other) {
		with(other.theType, other.theValue);
		for (SubClassMap2<? extends C, ? extends V> child : other.theSubMaps)
			_putAll(child);
	}

	/**
	 * @param <C2> The type to query for
	 * @param type The class to query for
	 * @return The path in this map to the most specific super-class of the given class
	 */
	public synchronized <C2 extends C> BetterList<SubClassMap2<? extends C, V>> pathTo(Class<C2> type) {
		return BetterList.of((List<SubClassMap2<? extends C, V>>) _pathTo(type, new ArrayList<>()));
	}

	private <C2 extends C> List<? extends SubClassMap2<?, V>> _pathTo(Class<C2> type, List<SubClassMap2<?, V>> list) {
		list.add(this);
		if (type == theType)
			return list;
		for (SubClassMap2<? extends C, V> subMap : theSubMaps) {
			if (subMap.theType.isAssignableFrom(type))
				return ((SubClassMap2<? super C2, V>) subMap)._pathTo(type, list);
		}
		return list;
	}

	/** Removes all values from this map */
	public void clear() {
		theValue = null;
		theSubMaps.clear();
		theSize = 0;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		append(str, 0);
		return str.toString();
	}

	private void append(StringBuilder str, int indent) {
		for (int i = 0; i < indent; i++)
			str.append('\t');
		str.append(theType.getName());
		if (theValue != null)
			str.append('=').append(theValue);
		for (SubClassMap2<? extends C, V> child : theSubMaps)
			child.append(str.append('\n'), indent + 1);
	}
}
