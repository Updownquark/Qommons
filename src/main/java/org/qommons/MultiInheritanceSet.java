package org.qommons;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;

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

	/** Removes all values from this set */
	void clear();

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
		if (set instanceof Unmodifiable || set == EMPTY || set instanceof Singleton)
			return set;
		return new Unmodifiable<>(set);
	}

	/**
	 * @param <T> The type for the set
	 * @return A empty multi-inheritance set
	 */
	public static <T> MultiInheritanceSet<T> empty() {
		return (MultiInheritanceSet<T>) EMPTY;
	}

	/** Implements {@link MultiInheritanceSet#empty()} */
	public static final MultiInheritanceSet<?> EMPTY = new MultiInheritanceSet<Object>() {
		@Override
		public int size() {
			return 0;
		}

		@Override
		public Collection<Object> values() {
			return Collections.emptyList();
		}

		@Override
		public boolean contains(Object value) {
			return false;
		}

		@Override
		public Object getAnyExtension(Object value) {
			return null;
		}

		@Override
		public Iterable<Object> getExpanded(InheritanceEnumerator<Object> enumerator) {
			return Collections.emptyList();
		}

		@Override
		public boolean add(Object value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
		}

		@Override
		public MultiInheritanceSet<Object> copy() {
			return this;
		}

		@Override
		public String toString() {
			return "[]";
		}
	};

	/**
	 * @param <T> The type of the value
	 * @param value The value for the set
	 * @param inheritance The inheritance scheme for the set
	 * @return An immutable {@link MultiInheritanceSet} containing only the given element
	 */
	public static <T> Singleton<T> singleton(T value, Inheritance<T> inheritance) {
		return new Singleton<>(inheritance, value);
	}

	/**
	 * @param set The set to hash
	 * @return The hash code for the set
	 */
	public static int hashCode(MultiInheritanceSet<?> set) {
		int hash = 0;
		for (Object node : set.values())
			hash += Objects.hashCode(node);
		return hash;
	}

	/**
	 * @param set The set to compare
	 * @param obj The object to compare with the set
	 * @return Whether the set and the object are equivalent
	 */
	public static boolean equals(MultiInheritanceSet<?> set, Object obj) {
		if (obj == set)
			return true;
		else if (!(obj instanceof MultiInheritanceSet))
			return false;
		Collection<?> setNodes = set.values();
		Collection<?> otherNodes = ((MultiInheritanceSet<?>) obj).values();
		return otherNodes.size() == setNodes.size() && setNodes.containsAll(otherNodes);
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
			// Make it better to avoid ConcurrentModificationExceptions
			theNodes = BetterTreeList.<T> build().build();
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
			if (value == null)
				throw new NullPointerException();
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
		public void clear() {
			theNodes.clear();
		}

		@Override
		public MultiInheritanceSet<T> copy() {
			Default<T> copy = new Default<>(theInheritance);
			copy.addAll(values());
			return copy;
		}

		@Override
		public int hashCode() {
			return MultiInheritanceSet.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return MultiInheritanceSet.equals(this, obj);
		}

		@Override
		public String toString() {
			return theNodes.toString();
		}
	}

	/**
	 * Default implementation of {@link MultiInheritanceSet#getExpanded(InheritanceEnumerator)}
	 * 
	 * @param <T> The type of values in the iterator
	 */
	class InheritanceIterator<T> implements Iterator<T> {
		private final InheritanceEnumerator<T> theEnumerator;
		private final LinkedList<Iterator<? extends T>> theIterStack;
		private boolean hasNext;

		InheritanceIterator(InheritanceEnumerator<T> enumerator, Iterator<T> init) {
			if (enumerator == null || init == null)
				throw new NullPointerException();
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
		public void clear() {
			if (!theBacking.isEmpty())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public MultiInheritanceSet<T> copy() {
			return theBacking.copy();
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

	/**
	 * An immutable {@link MultiInheritanceSet} containing a single value
	 * 
	 * @param <T> The type of the set
	 */
	public class Singleton<T> implements MultiInheritanceSet<T> {
		private final Inheritance<T> theInheritance;
		private final T theValue;

		/**
		 * @param inheritance The inheritance for this set
		 * @param value The value for this set
		 */
		public Singleton(Inheritance<T> inheritance, T value) {
			theInheritance = inheritance;
			theValue = value;
		}

		@Override
		public int size() {
			return 1;
		}

		@Override
		public Collection<T> values() {
			return Collections.singleton(theValue);
		}

		@Override
		public boolean contains(T value) {
			return Objects.equals(theValue, value) //
				|| theInheritance.isExtension(theValue, value);
		}

		@Override
		public T getAnyExtension(T value) {
			if (Objects.equals(theValue, value) || theInheritance.isExtension(value, theValue))
				return theValue;
			return null;
		}

		@Override
		public Iterable<T> getExpanded(InheritanceEnumerator<T> enumerator) {
			return () -> new InheritanceIterator<>(enumerator, Collections.singleton(theValue).iterator());
		}

		@Override
		public boolean add(T value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public MultiInheritanceSet<T> copy() {
			MultiInheritanceSet<T> copy = create(theInheritance);
			copy.add(theValue);
			return copy;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theValue);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			else if (!(obj instanceof MultiInheritanceSet))
				return false;
			else if (((MultiInheritanceSet<?>) obj).size() != 1)
				return false;
			else
				return Objects.equals(theValue, ((MultiInheritanceSet<?>) obj).values().iterator().next());
		}

		@Override
		public String toString() {
			return "{" + theValue + "}";
		}
	}
}
