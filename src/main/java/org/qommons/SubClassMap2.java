package org.qommons;

import java.util.ArrayList;
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

	/**
	 * @param <C2> The type to query with
	 * @param type The class to query with
	 * @param exactMatchOnly Whether to search for a value mapped to the exact type, or to allow that mapped to the most specific super-type
	 * @return The value for the queried type
	 */
	public synchronized <C2 extends C> V get(Class<C2> type, boolean exactMatchOnly) {
		return _get(type, exactMatchOnly);
	}

	private <C2 extends C> V _get(Class<C2> type, boolean exactMatchOnly) {
		if (type == theType)
			return theValue;
		for (SubClassMap2<? extends C, V> subMap : theSubMaps) {
			if (subMap.theType.isAssignableFrom(type)) {
				V value = ((SubClassMap2<? super C2, V>) subMap)._get(type, exactMatchOnly);
				if (value != null || !exactMatchOnly)
					return value;
			}
		}
		return exactMatchOnly ? null : theValue;
	}

	/**
	 * @param <C2> The type to query with
	 * @param type The class to query with
	 * @return All values mapped to the type or any of its super types
	 */
	public synchronized <C2 extends C> List<V> getAll(Class<C2> type) {
		return _getAll(type, null);
	}

	private <C2 extends C> List<V> _getAll(Class<C2> type, List<V> values) {
		if (!theType.isAssignableFrom(type))
			return values;
		if (theValue != null) {
			if (values == null)
				values = new ArrayList<>(5);
			values.add(theValue);
		}
		for (SubClassMap2<? extends C, V> child : theSubMaps) {
			if (child.theType.isAssignableFrom(type))
				values = ((SubClassMap2<C, V>) child)._getAll(type, values);
		}
		return values;
	}

	/**
	 * @param <C2> The type to query with
	 * @param type The class to query with
	 * @return All values mapped to the type or any of its super types, coupled with the types each value is mapped to
	 */
	public synchronized <C2 extends C> List<BiTuple<Class<? extends C>, V>> getAllEntries(Class<C2> type) {
		return _getAllEntries(type, null);
	}

	private <C2 extends C> List<BiTuple<Class<? extends C>, V>> _getAllEntries(Class<C2> type,
		List<BiTuple<Class<? extends C>, V>> values) {
		if (!theType.isAssignableFrom(type))
			return values;
		if (theValue != null) {
			if (values == null)
				values = new ArrayList<>(5);
			values.add(new BiTuple<>(theType, theValue));
		}
		for (SubClassMap2<? extends C, V> child : theSubMaps) {
			if (child.theType.isAssignableFrom(type))
				values = ((SubClassMap2<C, V>) child)._getAllEntries(type, values);
		}
		return values;
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
				if (subMap.size() == 1) {
					subMapIter.remove();
					theSize--;
				} else {
					theSize += subMap.size() - preSize;
					if (subMap.theValue == null && subMap.theSubMaps.size() == 1)
						subMapIter.set(subMap.theSubMaps.get(0));
				}
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
