package org.qommons;

import java.util.LinkedHashSet;
import java.util.function.Consumer;

/** An efficient abstract implementation of Causable */
public abstract class AbstractCausable implements Causable {
	private final Object theCause;
	private LinkedHashSet<Consumer<Object>> theActions;
	private LinkedHashSet<Consumer<Object>> theRootActions;

	/** @param cause The cause of this causable */
	public AbstractCausable(Object cause) {
		theCause = cause;
	}

	@Override
	public Object getCause() {
		return theCause;
	}

	@Override
	public Causable onFinish(Consumer<Object> action) {
		getActions().add(action);
		return this;
	}

	@Override
	public Causable onRootFinish(Consumer<Object> action) {
		LinkedHashSet<Consumer<Object>> actions = getRootActions();
		if (actions != null)
			actions.add(action);
		else // The cause is a causable that doesn't extend AbstractCausable
			((Causable) theCause).onRootFinish(action);
		return this;
	}

	@Override
	public void finish() {
		if (theActions != null) {
			for (Consumer<Object> action : theActions)
				action.accept(this);
			theActions.clear();
		}
	}

	private boolean isRoot() {
		return !(theCause instanceof Causable);
	}

	private LinkedHashSet<Consumer<Object>> getActions() {
		if (theActions != null)
			return theActions;
		else if (isRoot())
			return theActions = getRootActions();
		else
			return theActions = new LinkedHashSet<>();
	}

	private LinkedHashSet<Consumer<Object>> getRootActions() {
		if (theRootActions != null)
			return theRootActions;
		else if (isRoot())
			return theRootActions = new LinkedHashSet<>();
		else if (theCause instanceof AbstractCausable)
			return theRootActions = ((AbstractCausable) theCause).getRootActions();
		else
			return null;
	}
}
