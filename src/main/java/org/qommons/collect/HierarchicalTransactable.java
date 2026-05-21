package org.qommons.collect;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

import org.qommons.Causable;
import org.qommons.CausalLock;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;

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
 * Thus, modifications can be made to an object protected by this lock or its children while nothing is happening to its ancestors, but
 * siblings and other more distantly-related nodes in the hierarchy can be modified freely.
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
		if (theParent != null) {
			Set<Cause> causes = addCurrentCauses(null);
			if (causes == null)
				causes = Collections.emptySet();
			return causes;
		} else
			return myLock.getCurrentCauses();
	}

	private Set<Cause> addCurrentCauses(Set<Cause> causes) {
		if (theParent != null)
			causes = theParent.addCurrentCauses(causes);
		Collection<Cause> myCauses = myLock.getCurrentCauses();
		if (!myCauses.isEmpty()) {
			if (causes == null)
				causes = new LinkedHashSet<>();
			causes.addAll(myCauses);
		}
		return causes;
	}

	@Override
	public Collection<Cause> getUnfinishedCauses() {
		if (theParent != null) {
			Set<Cause> causes = addUnfinishedCauses(null);
			if (causes == null)
				causes = Collections.emptySet();
			return causes;
		} else
			return myLock.getUnfinishedCauses();
	}

	private Set<Cause> addUnfinishedCauses(Set<Cause> causes) {
		if (theParent != null)
			causes = theParent.addUnfinishedCauses(causes);
		for (Cause cause : myLock.getCurrentCauses()) {
			if (cause instanceof Causable && !((Causable) cause).isFinished()) {
				if (causes == null)
					causes = new LinkedHashSet<>();
				causes.add(cause);
			}
		}
		return causes;
	}

	@Override
	public boolean hasFinishingCauses() {
		if (theParent != null && theParent.hasFinishingCauses())
			return true;
		return myLock.hasFinishingCauses();
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
	public Transaction lock(boolean tryOnly) {
		return lock(tryOnly, false, null);
	}

	@Override
	public Transaction lockWrite(boolean tryOnly, Object cause) {
		return lock(tryOnly, true, cause);
	}

	@Override
	public CoreId getCoreId() {
		if (theParent == null)
			return myLock.getCoreId();
		else
			return theParent.getCoreId().and(myLock.getCoreId());
	}

	private static class LockWithCause {
		final Transaction lock;
		final Causable cause;

		LockWithCause(Transaction lock, Causable cause) {
			this.lock = lock;
			this.cause = cause;
		}
	}

	/* The only difference between the following two methods is that the first doesn't require a call to myLock.getRootCausable().
	 * This class may be called so often that this is worth having 2 methods */

	private Transaction lock(boolean justTry, boolean write, Object cause) {
		if (theParent == null)
			return lockSelf(justTry, write, cause);

		// First, obtain a read lock on the parent
		LockWithCause parentLock = theParent.lockWithCause(justTry, false, cause);
		if (parentLock == null)
			return null;
		if (parentLock.cause != null)
			cause = parentLock.cause;
		// Now, try to obtain the local lock
		return wrapWithLocalLock(parentLock.lock, justTry, write, cause);
	}

	private LockWithCause lockWithCause(boolean justTry, boolean write, Object cause) {
		if (theParent == null) {
			Transaction myT = lockSelf(justTry, write, cause);
			return myT == null ? null : new LockWithCause(myT, myLock.getRootCausable());
		}

		// First, obtain a read lock on the parent
		LockWithCause parentLock = theParent.lockWithCause(justTry, false, cause);
		if (parentLock == null)
			return null;
		if (parentLock.cause != null)
			cause = parentLock.cause;
		// Now, try to obtain the local lock
		Transaction lock = wrapWithLocalLock(parentLock.lock, justTry, write, cause);
		return lock == null ? null : new LockWithCause(lock, myLock.getRootCausable());
	}

	private Transaction lockSelf(boolean justTry, boolean write, Object cause) {
		Transaction lock;
		if (write)
			lock = myLock.lockWrite(justTry, cause);
		else
			lock = myLock.lock(justTry);
		if (!justTry) {
			while (lock == null) {
				if (write)
					lock = myLock.lockWrite(false, cause);
				else
					lock = myLock.lock(false);
			}
		}
		return lock;
	}

	private Transaction wrapWithLocalLock(Transaction parentLock, boolean justTry, boolean write, Object cause) {
		boolean success = false;
		try {
			Transaction myT = lockSelf(justTry, write, cause);
			if (myT != null) {
				success = true;
				return new HierarchicalLockTransaction(parentLock, myT);
			}
		} finally {
			if (!success) {
				parentLock.close();
			}
		}
		return null;
	}

	@Override
	public String toString() {
		int depth = 0;
		HierarchicalTransactable root = this;
		while (root.theParent != null) {
			depth++;
			root = root.theParent;
		}
		return getClass().getSimpleName() + ":" + Integer.toHexString(root.hashCode()) + "@(" + depth + ")"
			+ Integer.toHexString(root.hashCode());
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
