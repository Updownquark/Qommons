package org.qommons.collect;

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
	abstract boolean isLockSupported();

	@Override
	default Transaction lock(boolean write, Object cause) {
		return lock(write, write, cause);
	}

	/**
	 * <p>
	 * Obtains a lock on this collection. The <code>structural</code> boolean allows finer-grained control of the collection with regard to
	 * updates.
	 * </p>
	 * <p>
	 * There are 2 types of changes to a collection. Addition/removal operations are <b>structural</b> changes and can have repercussions
	 * for certain types of views into the collection (e.g. {@link java.util.Collection#spliterator() spliterators},
	 * {@link java.util.Collection#iterator() iterators}, and {@link java.util.List#subList(int, int) sub-lists}). Modifications which only
	 * affect the content of the elements (or pure update operations, set operations with the same reference value which may fire change
	 * events but have no effect on the content of the collection) are <b>update</b> operations, which intrinsically cannot interfere with
	 * other views.
	 * </p>
	 * 
	 * It is sometimes useful to forbid structural changes while permitting updates. The following table details the interplay of the
	 * read/write and structural/update flags for this method:
	 * <table>
	 * <tr>
	 * <td></td>
	 * <td><b>read</b></td>
	 * <td><b>write</b></td>
	 * </tr>
	 * <tr>
	 * <td><b>update</b></td>
	 * <td>Forbids any type of change to the collection while the lock is held and forbids other threads from obtaining a write lock.</td>
	 * <td>Allows the current thread to perform updates on the collection, but not structural changes while the lock is held. Other threads
	 * may obtain read locks on structural changes, but not for updates.</td>
	 * </tr>
	 * <tr>
	 * <td><b>structural</b></td>
	 * <td>Forbids only structural changes to the collection while the lock is held. Any thread, including the current thread, may still
	 * perform update operations or obtain an update write lock.</td>
	 * <td>Allows the current thread to perform any type of change on the collection and forbids any other thread from obtaining any kind of
	 * lock.</td>
	 * </tr>
	 * </table>
	 * 
	 * @param write Whether to obtain an exclusive, write-permitting lock or an unexclusive, read-only lock
	 * @param structural For a <code>write</code> lock, whether to obtain a lock that permits structural (add/remove) operations; for a read
	 *        lock, whether to obtain a lock that forbids such operations.
	 * @param cause The cause of changes (for <code>write</code>) that may occur during the transaction
	 * @return A transaction to {@link Transaction#close() close} to release the lock
	 */
	default Transaction lock(boolean write, boolean structural, Object cause) {
		Transaction nodeT = getNodes().lock(write, structural, cause);
		Transaction edgeT = getEdges().lock(write, structural, cause);
		return () -> {
			edgeT.close();
			nodeT.close();
		};
	}

	@Override
	TransactableCollection<? extends org.qommons.collect.Graph.Node<N, E>> getNodes();

	@Override
	TransactableCollection<? extends org.qommons.collect.Graph.Edge<N, E>> getEdges();
}
