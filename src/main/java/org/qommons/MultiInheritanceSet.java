package org.qommons;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A set of items in a hierarchy. For any item inserted into this set, the set will then also contain all items that the new item extends.
 * 
 * @param <T> The type of item in the set
 */
public interface MultiInheritanceSet<T> {
	/**
	 * Defines inheritance for items of a given type
	 * 
	 * @param <K> The type of items that this inheritance scheme supports
	 */
	interface Inheritance<K> {
		/**
		 * @param maybeSuper The (possibly) super-item to test
		 * @param maybeSub The (possibly) sub-item to test
		 * @return Whether <code>maybeSub</code> actually extends <code>maybeSuper</code>
		 */
		boolean isExtension(K maybeSuper, K maybeSub);
	}

	/**
	 * Enumerates all super-items extended by an item
	 * 
	 * @param <T> The type of items that this enumerator supports
	 */
	public interface InheritanceEnumerator<T> {
		/**
		 * @param value The value to enumerate
		 * @return An iterable for all items that the <code>value</code> extends directly
		 */
		Iterable<? extends T> getSupers(T value);
	}

	/** @return The number of items (not including super-items) in the set */
	int size();

	/** @return Whether this set is empty */
	default boolean isEmpty() {
		return size() == 0;
	}

	/** @return All items (not including super-items) in the set */
	Collection<T> values();

	/**
	 * @param value The value to test
	 * @return Whether this set contains the given item (either directly or as an implicitly-included super-item)
	 */
	boolean contains(T value);

	/**
	 * @param value The value to query
	 * @return An item included directly in this set which is an extension of the given item
	 */
	T getAnyExtension(T value);

	/**
	 * @param enumerator The enumerator to get super-items for each item
	 * @return An iterable of all items (including implicitly-included super-items) in this set
	 */
	Iterable<T> getExpanded(InheritanceEnumerator<T> enumerator);

	/**
	 * @param value The value to add
	 * @return Whether the given value was added to the set (false if it was already present)
	 */
	boolean add(T value);

	/**
	 * @param values The values to add to the set
	 * @return The number of values actually added to the set (less than <code>values.size()</code> if some were already present)
	 */
	default int addAll(Collection<? extends T> values) {
		int added = 0;
		for (T value : values) {
			if (add(value))
				added++;
		}
		return added;
	}

	/** @return An independent copy of this set */
	MultiInheritanceSet<T> copy();

	/**
	 * Creates a new inheritance set
	 * 
	 * @param <T> The type of items to create the set for
	 * @param inheritance The inheritance scheme for the set to use
	 * @return The new set
	 */
	public static <T> MultiInheritanceSet<T> create(Inheritance<T> inheritance) {
		return new Default<>(inheritance);
	}

	/**
	 * @param <T> The type of items in the set
	 * @param set The set to wrap
	 * @return An inheritance set with the same content as the one given, but that cannot be modified
	 */
	public static <T> MultiInheritanceSet<T> unmodifiable(MultiInheritanceSet<T> set) {
		return set instanceof Unmodifiable ? set : new Unmodifiable<>(set);
	}

	/**
	 * @param <T> The type for the set
	 * @return A empty multi-inheritance set
	 */
	public static <T> MultiInheritanceSet<T> empty() {
		class EmptyInheritanceSet implements MultiInheritanceSet<T> {
			@Override
			public int size() {
				return 0;
			}

			@Override
			public Collection<T> values() {
				return Collections.emptyList();
			}

			@Override
			public boolean contains(T value) {
				return false;
			}

			@Override
			public T getAnyExtension(T value) {
				return null;
			}

			@Override
			public Iterable<T> getExpanded(InheritanceEnumerator<T> enumerator) {
				return Collections.emptyList();
			}

			@Override
			public boolean add(T value) {
				throw new UnsupportedOperationException();
			}

			@Override
			public MultiInheritanceSet<T> copy() {
				return this;
			}
		}
		return new EmptyInheritanceSet();
	}

	/**
	 * Default, modifiable {@link MultiInheritanceSet} implementation
	 * 
	 * @param <T> The type of items in the set
	 */
	public class Default<T> implements MultiInheritanceSet<T> {
		private final MultiInheritanceSet.Inheritance<T> theInheritance;
		private final List<T> theNodes;

		/** @param inheritance The inheritance for the set to use */
		public Default(MultiInheritanceSet.Inheritance<T> inheritance) {
			theInheritance = inheritance;
			theNodes = new ArrayList<>(5);
		}

		@Override
		public int size() {
			return theNodes.size();
		}

		@Override
		public Collection<T> values() {
			return Collections.unmodifiableList(theNodes);
		}

		@Override
		public boolean contains(T value) {
			return getAnyExtension(value) != null;
		}

		@Override
		public T getAnyExtension(T value) {
			for (T node : theNodes)
				if (theInheritance.isExtension(value, node))
					return node;
			return null;
		}

		@Override
		public Iterable<T> getExpanded(InheritanceEnumerator<T> enumerator) {
			return () -> new InheritanceIterator<>(enumerator, theNodes.iterator());
		}

		@Override
		public boolean add(T value) {
			int added = -1;
			int size = theNodes.size();
			for (int i = 0; i < size; i++) {
				if (theInheritance.isExtension(value, theNodes.get(i))) {
					if (added >= 0) {
						theNodes.remove(added);
						size--;
					}
					return false;
				} else if (theInheritance.isExtension(theNodes.get(i), value)) {
					if (added >= 0) {
						theNodes.remove(i);
						size--;
						i--;
					} else {
						added = i;
						theNodes.set(i, value);
					}
				}
			}
			if (added < 0)
				theNodes.add(value);
			return true;
		}

		@Override
		public MultiInheritanceSet<T> copy() {
			Default<T> copy = new Default<>(theInheritance);
			copy.addAll(values());
			return copy;
		}

		@Override
		public String toString() {
			return theNodes.toString();
		}

		static class InheritanceIterator<T> implements Iterator<T> {
			private final InheritanceEnumerator<T> theEnumerator;
			private final LinkedList<Iterator<? extends T>> theIterStack;
			private boolean hasNext;

			InheritanceIterator(InheritanceEnumerator<T> enumerator, Iterator<T> init) {
				theEnumerator = enumerator;
				theIterStack = new LinkedList<>();
				theIterStack.add(init);
			}

			@Override
			public boolean hasNext() {
				if (hasNext)
					return true;
				while (!theIterStack.isEmpty() && !theIterStack.getLast().hasNext())
					theIterStack.removeLast();
				hasNext = !theIterStack.isEmpty();
				return hasNext;
			}

			@Override
			public T next() {
				if (!hasNext())
					throw new NoSuchElementException();
				hasNext = false;
				T next = theIterStack.getLast().next();
				Iterable<? extends T> supers = theEnumerator.getSupers(next);
				theIterStack.add(supers.iterator());
				return next;
			}
		}
	}

	/**
	 * Implements {@link MultiInheritanceSet#unmodifiable(MultiInheritanceSet)}
	 * 
	 * @param <T> The type of items in the set
	 */
	public class Unmodifiable<T> implements MultiInheritanceSet<T> {
		private final MultiInheritanceSet<T> theBacking;

		/** @param backing The set to wrap */
		public Unmodifiable(MultiInheritanceSet<T> backing) {
			theBacking = backing;
		}

		@Override
		public int size() {
			return theBacking.size();
		}

		@Override
		public Collection<T> values() {
			return theBacking.values();
		}

		@Override
		public boolean contains(T value) {
			return theBacking.contains(value);
		}

		@Override
		public T getAnyExtension(T value) {
			return theBacking.getAnyExtension(value);
		}

		@Override
		public Iterable<T> getExpanded(InheritanceEnumerator<T> enumerator) {
			return theBacking.getExpanded(enumerator);
		}

		@Override
		public boolean add(T value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public MultiInheritanceSet<T> copy() {
			return new Unmodifiable<>(theBacking.copy());
		}

		@Override
		public int hashCode() {
			return theBacking.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theBacking.equals(obj);
		}

		@Override
		public String toString() {
			return theBacking.toString();
		}
	}

}
