package org.qommons.collect;

import java.util.Collection;

import org.qommons.Transactable;
import org.qommons.Transaction;

/**
 * A collection to which modifications can be batched according to the {@link Transactable} spec.
 * 
 * @param <E> The type of elements in the collection
 */
public interface TransactableCollection<E> extends Collection<E>, Transactable {
	@Override
	abstract boolean isLockSupported();

	@Override
	default Transaction lock(boolean write, Object cause) {
		return lock(write, write, cause);
	}

	/**
	 * <p>
	 * Obtains a reentrant lock on this collection. The <code>structural</code> boolean allows finer-grained control of the collection with
	 * regard to updates.
	 * </p>
	 * <p>
	 * There are 2 types of changes to a collection. Addition/removal operations are <b>structural</b> changes and can have repercussions
	 * for certain types of views into the collection (e.g. {@link #spliterator() spliterators}, {@link #iterator() iterators}, and
	 * {@link java.util.List#subList(int, int) sub-lists}). Modifications which only affect the content of the elements (or pure update
	 * operations, set operations with the same reference value which may fire change events but have no effect on the content of the
	 * collection) are <b>update</b> operations, which intrinsically cannot interfere with other views.
	 * </p>
	 * 
	 * It is sometimes useful to forbid structural changes while permitting updates. The following table details the interplay of the
	 * read/write and structural/update flags for this method:
	 * <table border="1">
	 * <tr>
	 * <td></td>
	 * <td><b>Read</b></td>
	 * <td><b>Write</b></td>
	 * </tr>
	 * <tr>
	 * <td><b>Update</b></td>
	 * <td>Forbids any type of change to the collection by any thread (unless the current thread holds or obtains a separate write lock)
	 * while the lock is held and forbids other threads from obtaining a write lock.</td>
	 * <td>Allows the current thread to perform updates on the collection, but not structural changes while the lock is held. Other threads
	 * may obtain read locks on structural changes, but not for updates.</td>
	 * </tr>
	 * <tr>
	 * <td><b>Structural</b></td>
	 * <td>Forbids only structural changes to the collection by any thread (unless the current thread holds or obtains a separate write
	 * lock) while the lock is held. Any thread, including the current thread, may still perform update operations or obtain an update write
	 * lock.</td>
	 * <td>Allows the current thread to perform any type of change on the collection and forbids any other thread from obtaining any kind of
	 * lock.</td>
	 * </tr>
	 * </table>
	 * 
	 * For each category, if a write lock is held, a read lock may also be obtained. If a read lock is held and a write lock is requested,
	 * the upgrade may be attempted, but will fail with an {@link IllegalStateException} if any lock of the same category is currently held
	 * by a different thread. As such, this upgrade capability should not be relied on.
	 * 
	 * Transactions obtained with this method must be closed in reverse order. I.e. a transaction must not be closed before all subsequent
	 * transactions obtained on the same thread are also closed.
	 * 
	 * @param write Whether to obtain an exclusive, write-permitting lock or an unexclusive, read-only lock
	 * @param structural For a <code>write</code> lock, whether to obtain a lock that permits structural (add/remove) operations; for a read
	 *        lock, whether to obtain a lock that forbids such operations.
	 * @param cause The cause of changes (for <code>write</code>) that may occur during the transaction
	 * @return A transaction to {@link Transaction#close() close} to release the lock
	 */
	Transaction lock(boolean write, boolean structural, Object cause);
}
