/* SubclassMap.java Created Jul 9, 2009 by Andrew Butler, PSL */
package org.qommons;

/**
 * A map of values by class. This differs significantly from a normal key-value map in that a value may be returned for a class input if an
 * entry exists for one of the class's superclasses (or inherited interfaces); an entry is not required for the class itself.
 *
 * @param <C> The type whose subtypes are used as keys in this map
 * @param <V> The type of value stored in this map
 */
public class SubClassMap<C, V> implements Sealable {
	class MapItem {
		final Class<? extends C> theClass;

		V theValue;

		MapItem(Class<? extends C> clazz, V value) {
			theClass = clazz;
			theValue = value;
		}

		@Override
		public String toString() {
			return theClass.getName() + "=" + theValue;
		}
	}

	private MapItem [] theItems;

	private boolean isSealed;

	/** Creates a SubClassMap */
	public SubClassMap() {
		theItems = new SubClassMap.MapItem[0];
	}

	@Override
	public boolean isSealed() {
		return isSealed;
	}

	@Override
	public void seal() {
		isSealed = true;
	}

	private void assertUnsealed() {
		if(isSealed) {
			throw new SealedException(this);
		}
	}

	/**
	 * Adds or sets a value in this map
	 *
	 * @param clazz The class to set the value for. After this, calls to {@link #get(Class)} with an argument that is <code>clazz</code> or
	 *            one of its subclasses will return <code>value</code> unless there is another entry in the map for a subtype of
	 *            <code>clazz</code> that is also a supertype of the argument to get(Class).
	 * @param value The value to store in the map
	 */
	public void put(Class<? extends C> clazz, V value) {
		assertUnsealed();
		for(MapItem item : theItems) {
			if(item.theClass == clazz) {
				item.theValue = value;
				return;
			}
		}
		theItems = ArrayUtils.add(theItems, new MapItem(clazz, value));
	}

	/**
	 * Removes a value from this map
	 *
	 * @param clazz The class to remove the value for. After this. calls to {@link #get(Class)} with an argument that is <code>clazz</code>
	 *            or one of its subclasses will return the value associated with <code>clazz</code>'s nearest superclass unless there is
	 *            another entry in the map for a subtype of <code>clazz</code> that is also a supertype of the argument to get(Class).
	 * @param allDescending Whether all subclasses of <code>clazz</code> should be removed as well
	 */
	public void remove(Class<? extends C> clazz, boolean allDescending) {
		assertUnsealed();
		for(int i = 0; i < theItems.length; i++) {
			if(theItems[i].theClass == clazz
				|| (allDescending && clazz != null && theItems[i].theClass != null && clazz.isAssignableFrom(theItems[i].theClass))) {
				theItems = ArrayUtils.remove(theItems, i);
			}
		}
	}

	/**
	 * Gets the value assigned to <code>subClass</code> or its nearest supertype for which there is an entry in this map.
	 *
	 * @param subClass The type to get the value for
	 * @return The value associated with the given type or its nearest superclass for which this map has an entry
	 */
	public V get(Class<? extends C> subClass) {
		MapItem [] matches = new SubClassMap.MapItem[0];
		for(MapItem item : theItems) {
			if(item.theClass == subClass) {
				return item.theValue;
			} else if(item.theClass != null && item.theClass.isAssignableFrom(subClass)) {
				matches = ArrayUtils.add(matches, item);
			}
		}
		if(matches.length == 0) {
			return null;
		}
		int minDist = 0;
		MapItem match = null;
		for(MapItem item : matches) {
			int dist = getTypeDistance(item.theClass, subClass);
			if(dist >= 0 && (match == null || dist < minDist)) {
				minDist = dist;
				match = item;
				if(dist == 0) {
					break;
				}
			}
		}
		return match.theValue;
	}

	private static int getTypeDistance(Class<?> clazz, Class<?> subClass) {
		if(clazz == subClass) {
			return 0;
		}
		if(clazz == null || subClass == null) {
			return -1;
		}
		int dist = getTypeDistance(clazz, subClass.getSuperclass());
		if(dist >= 0) {
			return dist + 1;
		}
		for(Class<?> intf : subClass.getInterfaces()) {
			dist = getTypeDistance(clazz, intf);
			if(dist >= 0) {
				return dist + 1;
			}
		}
		return -1;
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder("{");
		for(MapItem item : theItems) {
			ret.append('\n').append(item);
		}
		ret.append("\n}");
		return ret.toString();
	}
}
