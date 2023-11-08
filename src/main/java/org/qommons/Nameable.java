package org.qommons;

/** A named object whose name is settable directly */
public interface Nameable extends Named {
	/**
	 * @param name The name for this object
	 * @return This object
	 */
	Nameable setName(String name);
}
