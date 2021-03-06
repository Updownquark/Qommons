package org.qommons.collect;

import java.util.Set;

import org.qommons.Transactable;

/**
 * A set to which modifications can be batched according to the {@link Transactable} spec.
 *
 * @param <E> The type of elements in the set
 */
public interface TransactableSet<E> extends TransactableCollection<E>, Set<E> {
}
