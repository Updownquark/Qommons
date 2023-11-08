package org.qommons.collect;

import org.qommons.Lockable;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.Transaction;

/**
 * A graph that supports transactional locking
 * 
 * @param <N> The type of node values for the graph
 * @param <E> The type of edge values for the graph
 */
public interface TransactableGraph<N, E> extends Graph<N, E>, Transactable {
	@Override
	default ThreadConstraint getThreadConstraint() {
		return getNodes().getThreadConstraint();
	}

	@Override
	default boolean isLockSupported() {
		return getNodes().isLockSupported();
	}

	@Override
	default Transaction lock(boolean write, Object cause) {
		return Lockable.lockAll(Lockable.lockable(getNodes(), write, cause), //
			Lockable.lockable(getEdges(), write, cause));
	}

	@Override
	default Transaction tryLock(boolean write, Object cause) {
		return Lockable.tryLockAll(Lockable.lockable(getNodes(), write, cause), //
			Lockable.lockable(getEdges(), write, cause));
	}

	@Override
	default CoreId getCoreId() {
		return Lockable.getCoreId(Lockable.lockable(getNodes(), false, null), //
			Lockable.lockable(getEdges(), false, null));
	}

	@Override
	TransactableCollection<? extends org.qommons.collect.Graph.Node<N, E>> getNodes();

	@Override
	TransactableCollection<? extends org.qommons.collect.Graph.Edge<N, E>> getEdges();
}
