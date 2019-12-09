package org.qommons.io;

import java.util.Iterator;

public interface FieldedAdjustable<F extends Enum<F>, C extends FieldedComponent<F>, T extends FieldedAdjustable<F, C, T>>
	extends ParsedAdjustable<T, C>, Comparable<T> {
	Class<F> getFieldType();

	C getField(F type);

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
