package org.qommons.collect;

import java.util.*;
import java.util.function.Function;

import org.qommons.*;
import org.qommons.Lockable.CoreId;

/**
 * <p>
 * This class is a {@link Transactable} that may have a parent and/or children.
 * </p>
 * 
 * <p>
 * It is capable of protecting an object in a hierarchy which is affected by its parent and affects its children. Such objects may not
 * affect their parents or be affected by their children.
 * </p>
 * 
 * <p>
 * When an attempt is made to lock an instance of this class, it performs the following sequence:
 * <ol>
 * <li>If it is not the root of its hierarchy, a read lock will be obtained on all its ancestors.</li>
 * <li>The local read/write lock will be obtained.
 * <li></li>The read/write lock of all its descendants will be obtained.</li>
 * </ol>
 * </p>
 * 
 * <p>
 * Thus, modifications can be made to an object protected by this class only while nothing is happening either to its ancestors or its
 * children, but siblings and other more distantly-related nodes in the hierarchy can be modified freely.
 * </p>
 */
public class HierarchicalTransactable implements CausalLock {
	private final Root theRoot;
	private final HierarchicalTransactable theParent;
	private final int theDepth;
	private final CausalLock myLock;
	private final List<HierarchicalTransactable> theChildren;

	HierarchicalTransactable(HierarchicalTransactable parent, CausalLock myLock) {
		if (parent == null) {
			theRoot = (Root) this;
			theDepth = 0;
		} else {
			theRoot = parent.theRoot;
			theDepth = parent.theDepth + 1;
		}
		theParent = parent;
		this.myLock = myLock;
		theChildren = new ArrayList<>();
	}

	/** @return The currently active causes of write locks. This value may not be unmodifiable for performance purposes. */
	@Override
	public Collection<Cause> getCurrentCauses() {
		List<Collection<Cause>> causeStack = new ArrayList<>(theDepth + 1);
		HierarchicalTransactable node = this;
		while (node != null) {
			causeStack.add(0, node.myLock.getCurrentCauses());
			node = node.theParent;
		}
		return new FlattenedCollection<>(causeStack);
	}

	@Override
	public Causable getRootCausable() {
		for (Cause cause : myLock.getCurrentCauses()) {
			if (cause instanceof Causable && !((Causable) cause).isTerminated())
				return (Causable) cause;
		}
		return null;
	}

	@Override
	public <T> T doOptimistically(T init, OptimisticOperation<T> operation) {
		return myLock.doOptimistically(init, operation);
	}

	@Override
	public int doOptimistically(int init, OptimisticIntOperation operation) {
		return myLock.doOptimistically(init, operation);
	}

	/** @return A Transactable that locks this transactable as its parent */
	public HierarchicalTransactable createChild() {
		try (Transaction t = lock(false, true, null, Ternian.FALSE)) { // No need to lock our descendants here
			HierarchicalTransactable newChild = new HierarchicalTransactable(this, theRoot.makeLock(this));
			theChildren.add(newChild);
			return newChild;
		}
	}

	/** Removes this transactable from its parent */
	public void remove() {
		if (theParent == null)
			return;
		try (Transaction t = theParent.lock(false, true, null, Ternian.FALSE)) { // No need to lock the parent's descendants here
			theChildren.remove(this);
		}
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return myLock.getThreadConstraint();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return lock(false, write, cause, Ternian.NONE);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return lock(true, write, cause, Ternian.NONE);
	}

	@Override
	public CoreId getCoreId() {
		return accumulateCores(myLock.getCoreId(), Ternian.NONE);
	}

	private Transaction lock(boolean justTry, boolean write, Object cause, Ternian fromBelow) {
		boolean success = false;
		// First, obtain a read lock on the parent if applicable
		Transaction parentT;
		if (theParent == null || fromBelow == Ternian.TRUE)
			parentT = Transaction.NONE;
		else {
			parentT = theParent.lock(justTry, false, cause, Ternian.FALSE);
			if (parentT == null)
				return null;
			Causable parentCause = theParent.getRootCausable();
			if (parentCause != null)
				cause = parentCause;
		}

		Transaction myT;
		Transaction[] childTs;
		try {
			// Now, try to obtain our own lock
			myT = justTry ? myLock.tryLock(write, cause) : myLock.lock(write, cause);
			if (myT == null)
				return null;
			cause = getRootCausable();

			try {
				// Now, try to obtain read or write locks on the children if applicable
				childTs = new Transaction[theChildren.size()];
				if (fromBelow != Ternian.FALSE) {
					try {
						for (int c = 0; c < childTs.length; c++) {
							Transaction childT = theChildren.get(c).lock(justTry, write, cause, Ternian.TRUE);
							if (childT == null)
								return null;
							childTs[c] = childT;
						}
						success = true;
					} finally {
						if (!success) {
							for (int c = childTs.length - 1; c >= 0; c--) {
								if (childTs[c] != null)
									childTs[c].close();
							}
						}
					}
				} else {
					Arrays.fill(childTs, Transaction.NONE);
					success = true;
				}

			} finally {
				if (!success)
					myT.close();
			}
		} finally {
			if (!success)
				parentT.close();
		}

		return new Transaction() {
			private boolean isClosed;

			@Override
			public void close() {
				if (isClosed)
					throw new IllegalStateException();
				isClosed = true;
				for (int c = childTs.length - 1; c >= 0; c--)
					childTs[c].close();
				myT.close();
				parentT.close();
			}
		};
	}

	private CoreId accumulateCores(CoreId core, Ternian fromBelow) {
		if (theParent != null && fromBelow != Ternian.TRUE)
			core = theParent.accumulateCores(core, Ternian.FALSE);
		if (fromBelow != Ternian.FALSE) {
			// This method is not protected by any locks, but this call is thread safe for ArrayList
			Object[] children = theChildren.toArray();
			for (Object child : children)
				core = ((HierarchicalTransactable) child).accumulateCores(core, Ternian.TRUE);
		}
		return core;
	}

	/**
	 * @param rootLock The root lock
	 * @param lockCreator The function to create locks from parent locks
	 * @return The new hierarchical transactable
	 */
	public static HierarchicalTransactable create(CausalLock rootLock, Function<CausalLock, CausalLock> lockCreator) {
		return new Root(rootLock, lockCreator);
	}

	static class Root extends HierarchicalTransactable {
		private final Function<CausalLock, CausalLock> theLockCreator;

		Root(CausalLock rootLock, Function<CausalLock, CausalLock> lockCreator) {
			super(null, rootLock);
			theLockCreator = lockCreator;
		}

		CausalLock makeLock(CausalLock parent) {
			return theLockCreator.apply(parent);
		}
	}

	static class FlattenedCollection<T> extends AbstractCollection<T> {
		private final Collection<? extends Collection<? extends T>> theCollections;

		public FlattenedCollection(Collection<? extends Collection<? extends T>> collections) {
			theCollections = collections;
		}

		@Override
		public Iterator<T> iterator() {
			return IterableUtils.flatten(theCollections).iterator();
		}

		@Override
		public int size() {
			int size = 0;
			for (Collection<? extends T> coll : theCollections)
				size += coll.size();
			return size;
		}
	}
}
