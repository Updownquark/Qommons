package org.qommons.collect;

import org.qommons.Lockable;
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
	default Transaction lock(boolean tryOnly) {
		return Lockable.lockAll(tryOnly, getNodes(), getEdges());
	}

	@Override
	default Transaction lockWrite(boolean tryOnly, Object cause) {
		return Lockable.lockAll(tryOnly, Transactable.asWriteLockable(getNodes(), cause), //
			Transactable.asWriteLockable(getEdges(), cause));
	}

	@Override
	default CoreId getCoreId() {
		return Lockable.getCoreId(getNodes(), getEdges());
	}

	@Override
	TransactableCollection<? extends org.qommons.collect.Graph.Node<N, E>> getNodes();

	@Override
	TransactableCollection<? extends org.qommons.collect.Graph.Edge<N, E>> getEdges();
}
