package org.qommons.io;

import java.util.Iterator;

/**
 * A {@link ParsedAdjustable} adjustable value whose components are typed by an enumeration and have integer values
 * 
 * @param <F> The field-type enum
 * @param <C> The type of {@link FieldedComponent component} composing this adjustable
 * @param <T> This fielded adjustable sub-type
 */
public interface FieldedAdjustable<F extends Enum<F>, C extends FieldedComponent<F>, T extends FieldedAdjustable<F, C, T>>
	extends ParsedAdjustable<T, C>, Comparable<T> {
	/** @return The field-type enum class */
	Class<F> getFieldType();

	/**
	 * @param type The field type
	 * @return The component in this adjustable with the given type, or null if this value does not have such a component
	 */
	C getField(F type);

	/**
	 * @param type The field type to set
	 * @param value The value for the field
	 * @return A new adjustable that is identical to this one except for the value of the given field
	 */
	T with(F type, int value);

	@Override
	default T adjust(int position, int amount) {
		C component = getComponent(position);
		if (component == null)
			return null;
		return with(component.getField(), component.getValue() + amount);
	}

	@Override
	default int compareTo(T o) {
		Iterator<C> thisIter = getComponents().iterator();
		Iterator<C> otherIter = o.getComponents().iterator();
		while (thisIter.hasNext() && otherIter.hasNext()) {
			FieldedComponent<F> thisComp = thisIter.next();
			FieldedComponent<F> otherComp = otherIter.next();
			int comp = thisComp.getField().compareTo(otherComp.getField());
			if (comp == 0)
				comp = Integer.compare(thisComp.getValue(), otherComp.getValue());
			if (comp != 0)
				return comp;
		}
		if (thisIter.hasNext())
			return 1;
		else if (otherIter.hasNext())
			return -1;
		else
			return 0;
	}
}
