package org.qommons;

import java.util.LinkedHashSet;

/** An efficient abstract implementation of Causable */
public abstract class AbstractCausable implements Causable {
	private final Object theCause;
	private LinkedHashSet<Runnable> theActions;
	private LinkedHashSet<Runnable> theRootActions;

	/** @param cause The cause of this causable */
	public AbstractCausable(Object cause) {
		theCause = cause;
	}

	@Override
	public Object getCause() {
		return theCause;
	}

	@Override
	public Causable onFinish(Runnable action) {
		getActions().add(action);
		return this;
	}

	@Override
	public Causable onRootFinish(Runnable action) {
		LinkedHashSet<Runnable> actions = getRootActions();
		if (actions != null)
			actions.add(action);
		else // The cause is a causable that doesn't extend AbstractCausable
			((Causable) theCause).onRootFinish(action);
		return this;
	}

	@Override
	public void finish() {
		if (theActions != null) {
			for (Runnable action : theActions)
				action.run();
			theActions.clear();
		}
	}

	private boolean isRoot() {
		return !(theCause instanceof Causable);
	}

	private LinkedHashSet<Runnable> getActions() {
		if (theActions != null)
			return theActions;
		else if (isRoot())
			return theActions = getRootActions();
		else
			return theActions = new LinkedHashSet<>();
	}

	private LinkedHashSet<Runnable> getRootActions() {
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
