package org.qommons.collect;

import org.qommons.AbstractCausable;

public class SimpleCause extends AbstractCausable {
	public SimpleCause() {
		this(null);
	}

	public SimpleCause(Object cause) {
		super(cause);
	}
}
