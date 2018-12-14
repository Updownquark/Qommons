package org.qommons.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
	/**
	 * The maximum capacity, used if a higher value is implicitly specified by either of the constructors with arguments. MUST be a power of
	 * two <= 1<<30.
	 */
	static final int MAXIMUM_CAPACITY = 1 << 30;

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

	/** @return The function producing hash codes for this set's values */
	public ToIntFunction<Object> getHasher() {
		return theHasher;
	}

	/** @return The test this set uses to determine whether two potential set elements are equivalent */
	public BiFunction<Object, Object, Boolean> getEquals() {
		return theEquals;
	}

	/**
	 * Like a bunch of the actual hashing guts in this class, this is copied from {@link java.util.HashMap}.
	 * 
	 * Returns a power of two size for the given target capacity.
	 */
	static final int tableSizeFor(int cap) {
		int n = cap - 1;
		n |= n >>> 1;
		n |= n >>> 2;
		n |= n >>> 4;
		n |= n >>> 8;
		n |= n >>> 16;
		return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
	}

	private void rehash(int expectedSize) {
		int tableSize = tableSizeFor((int) Math.ceil(expectedSize / theLoadFactor));
		HashTableEntry[] table = new BetterHashSet.HashTableEntry[tableSize];
		HashEntry entry = theFirst;
		while (entry != null) {
			insert(table, entry, -1, null);
			entry = entry.next;
		}
		theTable = table;
	}

	private void insert(HashTableEntry[] table, HashEntry entry, int tableIndex, HashEntry adjacentEntry) {
		if (tableIndex < 0)
			tableIndex = getTableIndex(table.length, entry.hashCode());
		HashTableEntry tableEntry = table[tableIndex];
		if (tableEntry == null)
			tableEntry = table[tableIndex] = new HashTableEntry(tableIndex);
		tableEntry.add(entry, adjacentEntry);
	}

	/**
	 * Ensures that this set's load factor will be satisfied if the collection grows to the given size
	 * 
	 * @param expectedSize The capacity to check for
	 * @return Whether this table was rebuilt
	 */
	public boolean ensureCapacity(int expectedSize) {
		try (Transaction t = lock(true, true, null)) {
			int neededTableSize = (int) Math.ceil(expectedSize / theLoadFactor);
			if (neededTableSize > theTable.length) {
				// Do this so we don't rehash as often when growing
				rehash((int) (expectedSize * 1.5));
				return true;
			}
			return false;
		}
	}

	/**
	 * <p>
	 * This is a very expensive (linear) call that checks this hash table for efficiency. The result is (size-numDuplicates)/size where
	 * numDuplicates is the sum of (loading-1) for each table entry.
	 * </p>
	 * <p>
	 * A value of 1.0 means that each value in this table is in its own table entry, which is perfectly efficient (for access time, though
	 * perhaps not for memory).
	 * </p>
	 * <p>
	 * A value of close to zero means that most values in the table are sharing a table entry with many other values, which will result in
	 * terrible efficiency.
	 * </p>
	 * 
	 * @return The efficiency of this table
	 */
	public double getEfficiency() {
		try (Transaction t = lock(false, true, null)) {
			int sharing = 0;
			for (HashTableEntry tableEntry : theTable) {
				if (tableEntry == null || tableEntry.entries.size() <= 1)
					continue;
				sharing += tableEntry.entries.size() - 1;
			}
			return (theSize - sharing) * 1.0 / theSize;
		}
	}

	private static int getTableIndex(int tableSize, int hashCode) {
		int h = hashCode ^ (hashCode >>> 16);
		return (tableSize - 1) & h;
	}

	private HashEntry getEntry(int hashCode, Predicate<? super E> equals) {
		HashTableEntry[] table = theTable;
		int tableIndex = getTableIndex(table.length, hashCode);
		HashTableEntry tableEntry = table[tableIndex];
		if (tableEntry == null)
			return null;
		return tableEntry.find(hashCode, equals, OptimisticContext.TRUE);
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
	public Transaction tryLock(boolean write, boolean structural, Object cause) {
		return theLocker.tryLock(write, structural, cause);
	}

	@Override
	public long getStamp(boolean structuralOnly) {
		return theLocker.getStamp(structuralOnly);
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
		return getEntry(theHasher.applyAsInt(value), equalsTest(value)) == null ? null : StdMsg.ELEMENT_EXISTS;
	}

	public CollectionElement<E> getOrAdd(int hashCode, Predicate<? super E> equals, Supplier<? extends E> value, ElementId after,
		ElementId before, boolean first, Runnable added) {
		HashTableEntry[] table = theTable;
		int tableIndex = getTableIndex(table.length, hashCode);
		HashTableEntry tableEntry = table[tableIndex];
		HashEntry entry = tableEntry == null ? null : tableEntry.findForInsert(hashCode, equals, OptimisticContext.TRUE);
		if (entry != null && entry.hashCode == hashCode && equals.test(entry.theValue))
			return entry;

		// Ordered insert is O(n), but we'll support it
		try (Transaction t = lock(true, true, null)) {
			ensureCapacity(theSize + 1);
			if (theTable != table) {
				// Table rebuilt, need to get the insertion information again
				table = theTable;
				tableIndex = getTableIndex(table.length, hashCode);
			}
			tableEntry = theTable[tableIndex];
			entry = tableEntry == null ? null : tableEntry.findForInsert(hashCode, equals, OptimisticContext.TRUE);
			if (entry != null && entry.hashCode == hashCode && equals.test(entry.theValue))
				return entry;
			HashEntry adjacent = entry;
			if (first) {
				if (after != null) {
					HashEntry afterEntry = ((HashId) after).entry;
					afterEntry.check();
					entry = new HashEntry(afterEntry.theOrder, value.get(), hashCode);
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
					entry = new HashEntry(theFirstIdCreator.getAndDecrement(), value.get(), hashCode);
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
					entry = new HashEntry(beforeEntry.theOrder, value.get(), hashCode);
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
					entry = new HashEntry(theLastIdCreator.getAndIncrement(), value.get(), hashCode);
					if (theLast != null)
						theLast.next = entry;
					entry.previous = theLast;
					theLast = entry;
					if (theFirst == null)
						theFirst = entry;
				}
			}
			insert(table, entry, tableIndex, adjacent);
			theSize++;
			if (added != null)
				added.run();
			return entry.immutable();
		}
	}

	@Override
	public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		boolean[] added = new boolean[1];
		CollectionElement<E> element = getOrAdd(theHasher.applyAsInt(value), equalsTest(value), () -> value, after, before, first,
			() -> added[0] = true);
		return added[0] ? element : null;
	}

	public Predicate<E> equalsTest(E value) {
		return v -> theEquals.apply(v, value);
	}

	@Override
	public CollectionElement<E> getElement(E value, boolean first) {
		HashEntry entry = getEntry(theHasher.applyAsInt(value), equalsTest(value));
		return entry == null ? null : entry.immutable();
	}

	public CollectionElement<E> getElement(int hashCode, Predicate<? super E> equals) {
		HashEntry entry = getEntry(hashCode, equals);
		return entry == null ? null : entry.immutable();
	}

	@Override
	public CollectionElement<E> getOrAdd(E value, boolean first, Runnable added) {
		return getOrAdd(theHasher.applyAsInt(value), equalsTest(value), () -> value, null, null, first, added);
	}

	@Override
	public CollectionElement<E> getElement(ElementId id) {
		return ((HashId) id).entry.check().immutable();
	}

	@Override
	public MutableCollectionElement<E> mutableElement(ElementId id) {
		return ((HashId) id).entry.check();
	}

	public boolean isValid(ElementId elementId){
		HashEntry entry=((HashId) elementId).entry.check();
		return entry.isValid();
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
			return !c.isEmpty();
		}
	}

	@Override
	public void clear() {
		if (isEmpty())
			return;
		try (Transaction t = lock(true, true, null)) {
			for (int i = 0; i < theTable.length; i++)
				theTable[i] = null;
			theFirst = null;
			theLast = null;
			theSize = 0;
		}
	}

	@Override
	public boolean isConsistent(ElementId element) {
		return ((HashId) element).entry.isValid();
	}

	@Override
	public boolean checkConsistency() {
		try (Transaction t = lock(false, null)) {
			HashEntry entry = theFirst;
			while (entry != null) {
				if (!entry.isValid())
					return true;
				entry = entry.next;
			}
			return false;
		}
	}

	@Override
	public <X> boolean repair(ElementId element, RepairListener<E, X> listener) {
		try (Transaction t = lock(true, null)) {
			return ((HashId) element).entry.repair(listener);
		}
	}

	@Override
	public <X> boolean repair(RepairListener<E, X> listener) {
		try (Transaction t = lock(true, null)) {
			boolean repaired = false;
			HashEntry entry = theFirst;
			while (entry != null) {
				repaired |= entry.repair(listener);
				entry = entry.next;
			}
			return repaired;
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

	protected class HashEntry implements MutableCollectionElement<E> {
		private long theOrder;
		int hashCode;
		private MutableBinaryTreeNode<HashEntry> theTreeNode;
		private E theValue;
		HashEntry next;
		HashEntry previous;
		private CollectionElement<E> wrapper;

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
		public CollectionElement<E> immutable() {
			if (wrapper == null)
				wrapper = MutableCollectionElement.super.immutable();
			return wrapper;
		}

		@Override
		public E get() {
			return theValue;
		}

		boolean isValid() {
			int newHash = theHasher.applyAsInt(theValue);
			return newHash == hashCode;
		}

		boolean repair(RepairListener<E, ?> listener) {
			int newHash = theHasher.applyAsInt(theValue);
			if (newHash == hashCode)
				return false;
			theTreeNode.remove();
			if (getEntry(newHash, equalsTest(theValue)) != null)
				listener.removed(immutable());
			else
				insert(theTable, this, -1, null);
			return true;
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
				if (getEntry(theHasher.applyAsInt(value), equalsTest(value)) != null)
					return StdMsg.ELEMENT_EXISTS;
			}
			return null;
		}

		@Override
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, false, null)) {
				if (!isPresent())
					throw new IllegalStateException("This element has been removed");
				int newHash = theHasher.applyAsInt(value);
				if (!theEquals.apply(theValue, value) && getEntry(newHash, equalsTest(value)) != null)
					throw new IllegalArgumentException(StdMsg.ELEMENT_EXISTS);
				theValue = value;
				if (hashCode != newHash) {
					theTreeNode.remove();
					hashCode = newHash;
					insert(theTable, this, -1, null);
				}
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
			}
		}

		int compareToNode(BinaryTreeNode<HashEntry> node) {
			if (hashCode < node.get().hashCode())
				return -1;
			else if (hashCode > node.get().hashCode())
				return 1;
			else
				return 0;
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

		void add(HashEntry entry, HashEntry adjacentEntry) {
			if (adjacentEntry != null) {
				ElementId id = adjacentEntry.theTreeNode.add(entry, entry.hashCode < adjacentEntry.hashCode);
				entry.placedAt(entries.mutableElement(id));
				return;
			}
			BinaryTreeNode<HashEntry> node = entries.getRoot();
			if (node == null) {
				entry.placedAt(entries.mutableNodeFor(entries.addElement(entry, false)));
				return;
			}
			node = node.findClosest(entry::compareToNode, true, false, null);
			entry.placedAt(entries.mutableNodeFor(entries.mutableNodeFor(node).add(entry, entry.hashCode() < node.get().hashCode())));
		}

		HashEntry findForInsert(int hashCode, Predicate<? super E> equals, OptimisticContext ctx) {
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
			}, true, false, ctx);
			if (!ctx.check() || node == null || node.get().hashCode() != hashCode)
				return null;
			BinaryTreeNode<HashEntry> node2 = node;
			while (ctx.check() && node2 != null && node2.get().hashCode() == hashCode) {
				if (equals.test(node2.get().get()))
					return node2.get();
				node2 = node2.getClosest(true);
			}
			if (!ctx.check())
				return null;
			node2 = node.getClosest(false);
			while (ctx.check() && node2 != null && node2.get().hashCode() == hashCode) {
				if (equals.test(node2.get().get()))
					return node2.get();
				node2 = node2.getClosest(false);
			}
			return node2 == null ? null : node2.get();
		}

		HashEntry find(int hashCode, Predicate<? super E> equals, OptimisticContext ctx) {
			HashEntry found = findForInsert(hashCode, equals, ctx);
			if (found != null && (found.hashCode != hashCode || !equals.test(found.theValue)))
				found = null;
			return found;
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
