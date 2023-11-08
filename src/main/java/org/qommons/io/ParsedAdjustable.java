package org.qommons.io;

import java.util.List;

/**
 * A type of parsed value that knows how to adjust itself for easy integration with {@link SpinnerFormat}, typically composed of
 * {@link AdjustableComponent}s
 * 
 * @param <T> This {@link ParsedAdjustable} sub-type
 * @param <C> The type of adjustable component that composes this adjustable
 */
public interface ParsedAdjustable<T extends ParsedAdjustable<T, C>, C extends AdjustableComponent> {
	/** @return The components of this value */
	List<C> getComponents();

	/**
	 * @param position The character position to get the component at
	 * @return The component that should be adjusted for the given cursor position
	 */
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

	/**
	 * @param position The cursor position to adjust at
	 * @param amount The amount to adjust this value by at the given position
	 * @return The adjusted value
	 */
	T adjust(int position, int amount);
}
