package org.qommons.collect;

import java.util.Collection;
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
 * </ol>
 * The child locks are not notified and are only <i>effectively</i> locked at the same level on the same thread as the parent, since the
 * first thing a child lock does when attempting a lock is obtain the lock from the parent.
 * </p>
 * 
 * <p>
 * Thus, modifications can be made to an object protected by this class only while nothing is happening either to its ancestors or its
 * children, but siblings and other more distantly-related nodes in the hierarchy can be modified freely.
 * </p>
 */
public class HierarchicalTransactable implements CausalLock {
	private final HierarchicalTransactable theParent;
	private final Function<? super HierarchicalTransactable, ? extends CausalLock> theLockMaker;

	private final CausalLock myLock;

	HierarchicalTransactable(HierarchicalTransactable parent, Function<? super HierarchicalTransactable, ? extends CausalLock> lockMaker) {
		theParent = parent;
		theLockMaker = lockMaker;
		this.myLock = theLockMaker.apply(this);
	}

	/** @return The currently active causes of write locks. This value may not be unmodifiable for performance purposes. */
	@Override
	public Collection<Cause> getCurrentCauses() {
		if(theParent!=null)
			return IterableUtils.concat(theParent.getCurrentCauses(), myLock.getCurrentCauses());
		else
			return myLock.getCurrentCauses();
	}

	@Override
	public Causable getRootCausable() {
		if (theParent != null) {
			Causable parentC = theParent.getRootCausable();
			if (parentC != null)
				return parentC;
		}
		return myLock.getRootCausable();
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
		try (Transaction t = lock(false, true, null)) {
			return new HierarchicalTransactable(this, theLockMaker);
		}
	}

	/**
	 * @param lockMaker The function to create locks the new child and its descendants
	 * @return A Transactable that locks this transactable as its parent
	 */
	public HierarchicalTransactable createChild(Function<? super HierarchicalTransactable, ? extends CausalLock> lockMaker) {
		try (Transaction t = lock(false, true, null)) {
			return new HierarchicalTransactable(this, lockMaker);
		}
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return myLock.getThreadConstraint();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return lock(false, write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return lock(true, write, cause);
	}

	@Override
	public CoreId getCoreId() {
		if (theParent == null)
			return myLock.getCoreId();
		else
			return theParent.getCoreId().and(myLock.getCoreId());
	}

	private Transaction lock(boolean justTry, boolean write, Object cause) {
		boolean success = false;
		// First, obtain a read lock on the parent if applicable
		Transaction parentT;
		if (theParent == null)
			parentT = Transaction.NONE;
		else {
			parentT = theParent.lock(justTry, false, cause);
			if (parentT == null)
				return null;
			Causable parentCause = theParent.getRootCausable();
			if (parentCause != null)
				cause = parentCause;
		}

		// Now, try to obtain our own lock
		Transaction myT;
		try {
			do {
				myT = myLock.tryLock(write, cause);
			} while (!justTry && myT == null);
			success = myT != null;
		} finally {
			if (!success) {
				parentT.close();
				return null;
			}
		}

		return new HierarchicalLockTransaction(parentT, myT);
	}

	static class HierarchicalLockTransaction implements Transaction {
		private final Transaction theParentLock;
		private final Transaction theLocalLock;
		private boolean isClosed;
		private boolean isParentClosed;

		HierarchicalLockTransaction(Transaction parentLock, Transaction localLock) {
			theParentLock = parentLock;
			theLocalLock = localLock;
		}

		@Override
		public void close() {
			if (isClosed)
				return;
			isClosed = true;
			theLocalLock.close();
			if (!isParentClosed) {
				isParentClosed = true;
				theParentLock.close();
			}
		}

		void closeParent() {
			if (isClosed || isParentClosed)
				return;
			isParentClosed = true;
			theParentLock.close();
		}
	}

	/**
	 * @param lockCreator The function to create locks from parent locks
	 * @return The new hierarchical transactable
	 */
	public static HierarchicalTransactable create(Function<? super HierarchicalTransactable, ? extends CausalLock> lockCreator) {
		return new HierarchicalTransactable(null, lockCreator);
	}
}
