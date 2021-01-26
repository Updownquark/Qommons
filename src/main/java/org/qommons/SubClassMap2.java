package org.qommons;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Function;
import java.util.function.Supplier;

import org.qommons.collect.BetterList;

public class SubClassMap2<C, V> {
	private final Class<C> theType;
	private final List<SubClassMap2<? extends C, V>> theSubMaps;
	private V theValue;
	private int theSize;

	public SubClassMap2(Class<C> type) {
		theType = type;
		theSubMaps = new ArrayList<>(3);
	}

	public Class<C> getType() {
		return theType;
	}

	public V getLocalValue() {
		return theValue;
	}

	public int size() {
		return theSize;
	}

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

	public synchronized <C2 extends C> SubClassMap2<C, V> with(Class<C2> type, V value) {
		_compute(type, old -> value);
		return this;
	}

	public synchronized <C2 extends C> V computeIfAbsent(Class<C2> type, Supplier<V> value) {
		return _compute(type, old -> old != null ? old : value.get());
	}

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

	public void clear() {
		theValue = null;
		theSubMaps.clear();
		theSize = 0;
	}
}
