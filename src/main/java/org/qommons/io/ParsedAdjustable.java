package org.qommons.io;

import java.util.List;

public interface ParsedAdjustable<T extends ParsedAdjustable<T, C>, C extends AdjustableComponent> {
	List<C> getComponents();

	default C getComponent(int position) {
		C lastComponent = null;
		for (C component : getComponents()) {
			if (position == component.getStart())
				return component;
			else if (position > component.getStart()) {
				if (position < component.getEnd()) {
					return component;
				} else if (position == component.getEnd())
					lastComponent = component;
			} else
				break;
		}
		return lastComponent;
	}

	T adjust(int position, int amount);
}
