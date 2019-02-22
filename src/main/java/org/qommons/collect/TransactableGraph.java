package org.qommons.collect;

import org.qommons.Lockable;
import org.qommons.StructuredTransactable;
import org.qommons.Transaction;

/**
 * A graph that supports transactional locking
 * 
 * @param <N> The type of node values for the graph
 * @param <E> The type of edge values for the graph
 */
public interface TransactableGraph<N, E> extends Graph<N, E>, StructuredTransactable {
	@Override
	default boolean isLockSupported() {
		return getNodes().isLockSupported();
	}

	@Override
	default Transaction lock(boolean write, boolean structural, Object cause) {
		return Lockable.lockAll(Lockable.lockable(getNodes(), write, structural, cause), //
			Lockable.lockable(getEdges(), write, structural, cause));
	}

	@Override
	default Transaction tryLock(boolean write, boolean structural, Object cause) {
		return Lockable.tryLockAll(Lockable.lockable(getNodes(), write, structural, cause), //
			Lockable.lockable(getEdges(), write, structural, cause));
	}

	@Override
	TransactableCollection<? extends org.qommons.collect.Graph.Node<N, E>> getNodes();

	@Override
	TransactableCollection<? extends org.qommons.collect.Graph.Edge<N, E>> getEdges();
}
