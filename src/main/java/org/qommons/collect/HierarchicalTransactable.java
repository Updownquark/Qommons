package org.qommons.collect;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
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
	private final HierarchicalTransactable theParent;
	private final Function<? super HierarchicalTransactable, ? extends CausalLock> theLockMaker;

	private final int theDepth;
	private final CausalLock myLock;
	private final List<HierarchicalTransactable> theChildren;
	// private final List<HierarchicalLockTransaction> theLocks;
	private boolean isRemoved;

	HierarchicalTransactable(HierarchicalTransactable parent, Function<? super HierarchicalTransactable, ? extends CausalLock> lockMaker) {
		theParent = parent;
		if (parent == null) {
			theDepth = 0;
		} else {
			theDepth = parent.theDepth + 1;
		}
		theLockMaker = lockMaker;
		this.myLock = theLockMaker.apply(this);
		theChildren = new ArrayList<>();
		// theLocks = new ArrayList<>();
	}

	/** @return The currently active causes of write locks. This value may not be unmodifiable for performance purposes. */
	@Override
	public Collection<Cause> getCurrentCauses() {
		return myLock.getCurrentCauses();
	}

	@Override
	public Causable getRootCausable() {
		Causable parentC = theParent == null ? null : theParent.getRootCausable();
		if (parentC != null)
			return parentC;
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
			HierarchicalTransactable newChild = new HierarchicalTransactable(this, theLockMaker);
			theChildren.add(newChild);
			return newChild;
		}
	}

	/**
	 * @param lockMaker The function to create locks the new child and its descendants
	 * @return A Transactable that locks this transactable as its parent
	 */
	public HierarchicalTransactable createChild(Function<? super HierarchicalTransactable, ? extends CausalLock> lockMaker) {
		try (Transaction t = lock(false, true, null)) {
			HierarchicalTransactable newChild = new HierarchicalTransactable(this, lockMaker);
			theChildren.add(newChild);
			return newChild;
		}
	}

	/** Removes this transactable from its parent */
	public void remove() {
		if (theParent == null || isRemoved)
			return;
		try (Transaction t = theParent.lock(false, true, null)) {
			if (isRemoved)
				return;
			isRemoved = true;
			// while (!theLocks.isEmpty())
			// theLocks.remove(theLocks.size() - 1).closeParent();
			int index = theParent.theChildren.indexOf(this);
			theParent.theChildren.remove(index);
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
		return accumulateCores(myLock.getCoreId(), Ternian.NONE);
	}

	private Transaction lock(boolean justTry, boolean write, Object cause) {
		boolean success = false;
		// First, obtain a read lock on the parent if applicable
		Transaction parentT;
		if (theParent == null || isRemoved)
			parentT = Transaction.NONE;
		else {
			parentT = theParent.lock(justTry, false, cause);
			if (parentT == null)
				return null;
			Causable parentCause = theParent.getRootCausable();
			if (parentCause != null)
				cause = parentCause;
		}

		Transaction myT;
		try {
			// Now, try to obtain our own lock
			myT = justTry ? myLock.tryLock(write, cause) : myLock.lock(write, cause);
			if (myT == null)
				return null;
			success = true;
		} finally {
			if (!success)
				parentT.close();
		}

		HierarchicalLockTransaction release = new HierarchicalLockTransaction(parentT, myT);
		// theLocks.add(release);
		return release;
	}

	class HierarchicalLockTransaction implements Transaction {
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
			// theLocks.remove(this);
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

	private CoreId accumulateCores(CoreId core, Ternian fromBelow) {
		if (theParent != null && !isRemoved && fromBelow != Ternian.TRUE)
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
	 * @param lockCreator The function to create locks from parent locks
	 * @return The new hierarchical transactable
	 */
	public static HierarchicalTransactable create(Function<? super HierarchicalTransactable, ? extends CausalLock> lockCreator) {
		return new HierarchicalTransactable(null, lockCreator);
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
