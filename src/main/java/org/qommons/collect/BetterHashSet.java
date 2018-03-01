package org.qommons.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BinaryTreeNode;
import org.qommons.tree.MutableBinaryTreeNode;

/**
 * A hash-based implementation of {@link BetterSet}
 * 
 * @param <E> The type of values in the set
 */
public class BetterHashSet<E> implements BetterSet<E> {
	/** The minimum allowed {@link HashSetBuilder#withLoadFactor(double) load factor} for maps of this type */
	public static final double MIN_LOAD_FACTOR = 0.2;
	/** The maximum allowed {@link HashSetBuilder#withLoadFactor(double) load factor} for maps of this type */
	public static final double MAX_LOAD_FACTOR = 0.9;

	/** A builder to use to create {@link BetterHashSet}s */
	public static class HashSetBuilder {
		private boolean isSafe;
		private ToIntFunction<Object> theHasher;
		private BiFunction<Object, Object, Boolean> theEquals;
		private int theInitExpectedSize;
		private double theLoadFactor;

		/** Creates the builder */
		protected HashSetBuilder() {
			isSafe = true;
			theHasher = Objects::hashCode;
			theEquals = Objects::equals;
			theInitExpectedSize = 10;
			theLoadFactor = .75;
		}

		/**
		 * Causes this builder to build a set that is not internally thread-safe
		 * 
		 * @return This builder
		 */
		public HashSetBuilder unsafe() {
			isSafe = false;
			return this;
		}

		/**
		 * Causes this builder to build a set whose hash and comparison are defined externally
		 * 
		 * @param hasher The has function for values in the set
		 * @param equals The equivalence check for values in the set
		 * @return This builder
		 */
		public HashSetBuilder withEquivalence(ToIntFunction<Object> hasher, BiFunction<Object, Object, Boolean> equals) {
			theHasher = hasher;
			theEquals = equals;
			return this;
		}

		/**
		 * Causes this builder to build a set whose values are stored by identity, instead of notional equivalence
		 * 
		 * @return This builder
		 */
		public HashSetBuilder identity() {
			return withEquivalence(System::identityHashCode, (o1, o2) -> o1 == o2);
		}

		/**
		 * @param loadFactor The load factor for the set that this builder creates
		 * @return This builder
		 */
		public HashSetBuilder withLoadFactor(double loadFactor) {
			if (loadFactor < MIN_LOAD_FACTOR || loadFactor > MAX_LOAD_FACTOR)
				throw new IllegalArgumentException("Load factor must be between " + MIN_LOAD_FACTOR + " and " + MAX_LOAD_FACTOR);
			theLoadFactor = loadFactor;
			return this;
		}

		/**
		 * @param initExpectedSize The number of values that the set created by this builder should accommodate without re-hashing the table
		 * @return This builder
		 */
		public HashSetBuilder withInitialCapacity(int initExpectedSize) {
			theInitExpectedSize = initExpectedSize;
			return this;
		}

		/**
		 * @param <E> The value type for the set
		 * @return An empty {@link BetterHashSet} built according to this builder's settings
		 */
		public <E> BetterHashSet<E> buildSet() {
			return new BetterHashSet<>(isSafe ? new StampedLockingStrategy() : new FastFailLockingStrategy(), theHasher, theEquals,
				theInitExpectedSize, theLoadFactor);
		}

		/**
		 * @param <E> The value type for the set
		 * @param values The initial values to insert into the set
		 * @return A {@link BetterHashSet} built according to this builder's settings, with the given initial content
		 */
		public <E> BetterHashSet<E> buildSet(E... values) {
			return buildSet(Arrays.asList(values));
		}

		/**
		 * @param <E> The value type for the set
		 * @param values The initial values to insert into the set
		 * @return A {@link BetterHashSet} built according to this builder's settings, with the given initial content
		 */
		public <E> BetterHashSet<E> buildSet(Collection<? extends E> values) {
			BetterHashSet<E> set = buildSet();
			set.addAll(values);
			return set;
		}
	}

	/** @return A builder to create a {@link BetterHashSet} */
	public static HashSetBuilder build() {
		return new HashSetBuilder();
	}

	private final CollectionLockingStrategy theLocker;
	private final ToIntFunction<Object> theHasher;
	private final BiFunction<Object, Object, Boolean> theEquals;
	private final AtomicLong theFirstIdCreator;
	private final AtomicLong theLastIdCreator;

	private final double theLoadFactor;

	private HashTableEntry[] theTable;
	private HashEntry theFirst;
	private HashEntry theLast;
	private int theSize;

	private BetterHashSet(CollectionLockingStrategy locker, ToIntFunction<Object> hasher, BiFunction<Object, Object, Boolean> equals,
		int initExpectedSize, double loadFactor) {
		theLocker = locker;
		theHasher = hasher;
		theEquals = equals;
		theFirstIdCreator = new AtomicLong(-1);
		theLastIdCreator = new AtomicLong(0);

		if (loadFactor < MIN_LOAD_FACTOR || loadFactor > MAX_LOAD_FACTOR)
			throw new IllegalArgumentException("Load factor must be between " + MIN_LOAD_FACTOR + " and " + MAX_LOAD_FACTOR);
		theLoadFactor = loadFactor;
		rehash(initExpectedSize);
	}

	private void rehash(int expectedSize) {
		int tableSize = (int) Math.ceil(expectedSize / theLoadFactor);
		theTable = new BetterHashSet.HashTableEntry[tableSize];
		HashEntry entry = theFirst;
		while (entry != null) {
			insert(entry);
			entry = entry.next;
		}
	}

	private void insert(HashEntry entry) {
		int tableIndex = getTableIndex(entry.hashCode());
		HashTableEntry tableEntry = theTable[tableIndex];
		if (tableEntry == null)
			tableEntry = theTable[tableIndex] = new HashTableEntry(tableIndex);
		tableEntry.add(entry);
	}

	/**
	 * Ensures that this set's load factor will be satisfied if the collection grows to the given size
	 * 
	 * @param expectedSize The capacity to check for
	 */
	protected void ensureCapacity(int expectedSize) {
		try (Transaction t = lock(false, false, null)) {
			int neededTableSize = (int) Math.ceil(expectedSize / theLoadFactor);
			if (neededTableSize > theTable.length)
				rehash(expectedSize);
		}
	}

	private int getTableIndex(int hashCode) {
		int h = hashCode ^ (hashCode >>> 16);
		return (theTable.length - 1) & h;
	}

	private HashEntry getEntry(int hashCode, E value) {
		int tableIndex = getTableIndex(hashCode);
		HashTableEntry tableEntry = theTable[tableIndex];
		if (tableEntry == null)
			return null;
		return tableEntry.find(hashCode, value);
	}

	@Override
	public boolean belongs(Object o) {
		return true;
	}

	@Override
	public boolean isLockSupported() {
		return theLocker.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, boolean structural, Object cause) {
		return theLocker.lock(write, structural, cause);
	}

	@Override
	public long getStamp(boolean structuralOnly) {
		return theLocker.getStatus(structuralOnly);
	}

	@Override
	public int size() {
		return theSize;
	}

	@Override
	public boolean isEmpty() {
		return theSize == 0;
	}

	@Override
	public Object[] toArray() {
		return BetterSet.super.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return BetterSet.super.toArray(a);
	}

	@Override
	public CollectionElement<E> getTerminalElement(boolean first) {
		return MutableCollectionElement.immutable(first ? theFirst : theLast);
	}

	@Override
	public CollectionElement<E> getAdjacentElement(ElementId elementId, boolean next) {
		HashEntry entry = ((HashId) elementId).entry;
		return MutableCollectionElement.immutable(next ? entry.next : entry.previous);
	}

	@Override
	public String canAdd(E value, ElementId after, ElementId before) {
		return getEntry(theHasher.applyAsInt(value), value) == null ? null : StdMsg.ELEMENT_EXISTS;
	}

	@Override
	public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		// Ordered insert is O(n), but we'll support it
		try (Transaction t = lock(true, true, null)) {
			int valueHashCode = theHasher.applyAsInt(value);
			HashEntry entry = getEntry(valueHashCode, value);
			if (entry != null)
				return null;
			ensureCapacity(theSize + 1);
			if (first) {
				if (after != null) {
					HashEntry afterEntry = ((HashId) after).entry;
					afterEntry.check();
					entry = new HashEntry(afterEntry.theOrder, value, valueHashCode);
					entry.previous = afterEntry;
					entry.next = afterEntry.next;
					if (afterEntry.next != null)
						afterEntry.next.previous = entry;
					else
						theLast = entry;
					afterEntry.next = entry;
					while (afterEntry != null) {
						afterEntry.theOrder--;
						afterEntry = afterEntry.previous;
					}
					if (theFirst.theOrder == theFirstIdCreator.get())
						theFirstIdCreator.getAndDecrement();
				} else {
					entry = new HashEntry(theFirstIdCreator.getAndDecrement(), value, valueHashCode);
					entry.next = theFirst;
					if (theFirst != null)
						theFirst.previous = entry;
					theFirst = entry;
					if (theLast == null)
						theLast = entry;
				}
			} else {
				if (before != null) {
					HashEntry beforeEntry = ((HashId) before).entry;
					beforeEntry.check();
					entry = new HashEntry(beforeEntry.theOrder, value, valueHashCode);
					entry.next = beforeEntry;
					entry.previous = beforeEntry.previous;
					if (beforeEntry.previous != null)
						beforeEntry.previous.next = entry;
					else
						theFirst = entry;
					beforeEntry.previous = entry;
					while (beforeEntry != null) {
						beforeEntry.theOrder++;
						beforeEntry = beforeEntry.next;
					}
					if (theLast.theOrder == theLastIdCreator.get())
						theLastIdCreator.getAndIncrement();
				} else {
					entry = new HashEntry(theLastIdCreator.getAndIncrement(), value, valueHashCode);
					if (theLast != null)
						theLast.next = entry;
					entry.previous = theLast;
					theLast = entry;
					if (theFirst == null)
						theFirst = entry;
				}
			}
			insert(entry);
			theSize++;
			theLocker.changed(true);
			return entry.immutable();
		}
	}

	@Override
	public CollectionElement<E> getElement(E value, boolean first) {
		try (Transaction t = lock(false, true, null)) {
			HashEntry entry = getEntry(theHasher.applyAsInt(value), value);
			return entry == null ? null : entry.immutable();
		}
	}

	@Override
	public CollectionElement<E> getElement(ElementId id) {
		return ((HashId) id).entry.check().immutable();
	}

	@Override
	public MutableCollectionElement<E> mutableElement(ElementId id) {
		return ((HashId) id).entry.check();
	}

	@Override
	public MutableElementSpliterator<E> spliterator(ElementId element, boolean asNext) {
		return new MutableHashSpliterator(((HashId) element).entry.check(), asNext);
	}

	@Override
	public MutableElementSpliterator<E> spliterator(boolean fromStart) {
		return new MutableHashSpliterator(fromStart ? theFirst : theLast, fromStart);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		try (Transaction t = lock(true, true, null); Transaction ct = Transactable.lock(c, false, null)) {
			for (E e : c)
				add(e);
			theLocker.changed(true);
			return !c.isEmpty();
		}
	}

	@Override
	public void clear() {
		try (Transaction t = lock(true, true, null)) {
			for (int i = 0; i < theTable.length; i++)
				theTable[i] = null;
			theFirst = null;
			theLast = null;
			theSize = 0;
			theLocker.changed(true);
		}
	}

	@Override
	public String toString() {
		return BetterCollection.toString(this);
	}

	private class HashId implements ElementId {
		final HashEntry entry;

		HashId(HashEntry entry) {
			this.entry = entry;
		}

		@Override
		public boolean isPresent() {
			return entry.next != null || theLast == entry;
		}

		@Override
		public int compareTo(ElementId o) {
			return Long.compare(entry.theOrder, ((HashId) o).entry.theOrder);
		}

		@Override
		public int hashCode() {
			return entry.hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof BetterHashSet.HashId && entry == ((HashId) obj).entry;
		}

		@Override
		public String toString() {
			return entry.toString();
		}
	}

	private class HashEntry implements MutableCollectionElement<E> {
		private long theOrder;
		private int hashCode;
		private MutableBinaryTreeNode<HashEntry> theTreeNode;
		private E theValue;
		HashEntry next;
		HashEntry previous;

		HashEntry(long order, E value, int hashCode) {
			theOrder = order;
			theValue = value;
			this.hashCode = hashCode;
		}

		void placedAt(MutableBinaryTreeNode<HashEntry> treeNode) {
			theTreeNode = treeNode;
		}

		private boolean isPresent() {
			return next != null || theLast == this;
		}

		HashEntry check() {
			if (!isPresent())
				throw new IllegalArgumentException(StdMsg.NOT_FOUND);
			return this;
		}

		@Override
		public BetterCollection<E> getCollection() {
			return BetterHashSet.this;
		}

		@Override
		public ElementId getElementId() {
			return new HashId(this);
		}

		@Override
		public E get() {
			return theValue;
		}

		@Override
		public String isEnabled() {
			return null;
		}

		@Override
		public String isAcceptable(E value) {
			try (Transaction t = lock(false, null)) {
				if (!isPresent())
					throw new IllegalStateException("This element has been removed");
				if (theEquals.apply(theValue, value))
					return null;
				if (getEntry(theHasher.applyAsInt(value), value) != null)
					return StdMsg.ELEMENT_EXISTS;
			}
			return null;
		}

		@Override
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, false, null)) {
				if (!isPresent())
					throw new IllegalStateException("This element has been removed");
				if (!theEquals.apply(theValue, value) && getEntry(theHasher.applyAsInt(value), value) != null)
					throw new IllegalArgumentException(StdMsg.ELEMENT_EXISTS);
				int newHash = theHasher.applyAsInt(value);
				theValue = value;
				if (hashCode != newHash) {
					theTreeNode.remove();
					hashCode = newHash;
					insert(this);
				}
				theLocker.changed(false);
			}
		}

		@Override
		public String canRemove() {
			return theTreeNode.canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			try (Transaction t = lock(true, null)) {
				if (!isPresent())
					throw new IllegalStateException("This element has been removed");
				theTreeNode.remove();
				if (theFirst == this)
					theFirst = next;
				if (theLast == this)
					theLast = previous;
				if (previous != null)
					previous.next = next;
				if (next != null)
					next.previous = previous;
				next = null;
				previous = null;
				theSize--;
				theLocker.changed(true);
			}
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			return obj == this;
		}

		@Override
		public String toString() {
			return String.valueOf(theValue);
		}
	}

	class HashTableEntry {
		final int theTableIndex;
		final BetterTreeList<HashEntry> entries;

		HashTableEntry(int index) {
			theTableIndex = index;
			entries = new BetterTreeList<>(false);
		}

		void add(HashEntry entry) {
			BinaryTreeNode<HashEntry> node = entries.getRoot();
			if (node == null) {
				entry.placedAt(entries.mutableNodeFor(entries.addElement(entry, false)));
				return;
			}
			node = node.findClosest(n -> {
				if (entry.hashCode() < n.get().hashCode())
					return -1;
				else if (entry.hashCode > n.get().hashCode())
					return 1;
				else
					return 0;
			}, true, false);
			entry.placedAt(entries.mutableNodeFor(entries.mutableNodeFor(node).add(entry, entry.hashCode() < node.get().hashCode())));
		}

		HashEntry find(int hashCode, E value) {
			BinaryTreeNode<HashEntry> node = entries.getRoot();
			if (node == null)
				return null;
			node = node.findClosest(n -> {
				if (hashCode < n.get().hashCode())
					return -1;
				else if (hashCode > n.get().hashCode())
					return 1;
				else
					return 0;
			}, true, true);
			if (node == null || node.get().hashCode() != hashCode)
				return null;
			BinaryTreeNode<HashEntry> node2 = node;
			while (node2 != null && node2.get().hashCode() == hashCode) {
				if (theEquals.apply(node2.get().get(), value))
					return node2.get();
				node2 = node2.getClosest(true);
			}
			node2 = node.getClosest(false);
			while (node2 != null && node2.get().hashCode() == hashCode) {
				if (theEquals.apply(node2.get().get(), value))
					return node2.get();
				node2 = node2.getClosest(false);
			}
			return null;
		}
	}

	class MutableHashSpliterator extends MutableElementSpliterator.SimpleMutableSpliterator<E> {
		private HashEntry current;
		private boolean currentIsNext;

		MutableHashSpliterator(HashEntry current, boolean next) {
			super(BetterHashSet.this);
			this.current = current;
			this.currentIsNext = next;
		}

		@Override
		public long estimateSize() {
			return BetterHashSet.this.size();
		}

		@Override
		public long getExactSizeIfKnown() {
			return estimateSize();
		}

		@Override
		public int characteristics() {
			return SIZED;
		}

		protected boolean tryElement(boolean forward) {
			if (current == null)
				current = currentIsNext ? theFirst : theLast;
			if (current == null)
				return false;
			// We can tolerate external modification as long as the node that this spliterator is anchored to has not been removed
			// This situation is easy to detect
			if (current.next == null && theLast != current)
				throw new ConcurrentModificationException(
					"The collection has been modified externally such that this spliterator has been orphaned");
			if (currentIsNext != forward) {
				HashEntry next = forward ? current.next : current.previous;
				if (next != null)
					current = next;
				else {
					currentIsNext = !forward;
					return false;
				}
			} else
				currentIsNext = !forward;
			return true;
		}

		@Override
		protected boolean internalForElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
			if (!tryElement(forward))
				return false;
			action.accept(current.immutable());
			return true;
		}

		@Override
		protected boolean internalForElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
			if (!tryElement(forward))
				return false;
			action.accept(new MutableSpliteratorEntry(current));
			return true;
		}

		@Override
		public MutableElementSpliterator<E> trySplit() {
			return null; // No real good way to do this
		}

		class MutableSpliteratorEntry implements MutableCollectionElement<E> {
			private final HashEntry theEntry;

			MutableSpliteratorEntry(HashEntry entry) {
				theEntry = entry;
			}

			@Override
			public BetterCollection<E> getCollection() {
				return BetterHashSet.this;
			}

			@Override
			public ElementId getElementId() {
				return theEntry.getElementId();
			}

			@Override
			public E get() {
				return theEntry.get();
			}

			@Override
			public String isEnabled() {
				return theEntry.isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				return theEntry.isAcceptable(value);
			}

			@Override
			public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
				theEntry.set(value);
			}

			@Override
			public String canRemove() {
				return theEntry.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				try (Transaction t = lock(true, null)) {
					HashEntry newCurrent;
					boolean newWasNext;
					if (theEntry == current) {
						newCurrent = current.previous;
						if (newCurrent != null)
							newWasNext = false;
						else {
							newCurrent = current.next;
							newWasNext = true;
						}
						current = newCurrent;
					} else {
						newCurrent = current;
						newWasNext = currentIsNext;
					}
					theEntry.remove();
					current = newCurrent;
					currentIsNext = newWasNext;
				}
			}
		}
	}
}
