package org.qommons.collect;

import java.util.List;

import org.qommons.Betterable;

/**
 * A list that extends Betterable
 * 
 * @param <E> The type of values in the list
 */
public interface BetterableList<E> extends List<E>, Betterable<E> {
}
