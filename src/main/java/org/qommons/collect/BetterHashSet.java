package org.qommons.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
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

	/**
	 * A builder to use to create {@link BetterHashSet}s
	 * 
	 * @param <B> The sub-type of this builder
	 */
	public static class HashSetBuilder<B extends HashSetBuilder<? extends B>> extends CollectionBuilder.Default<B> {
		private ToIntFunction<Object> theHasher;
		private BiFunction<Object, Object, Boolean> theEquals;
		private int theInitExpectedSize;
		private double theLoadFactor;

		/**
		 * Creates the builder
		 * 
		 * @param initDescrip An initial (default) description of the builder
		 */
		protected HashSetBuilder(String initDescrip) {
			super(initDescrip);
			theHasher = Objects::hashCode;
			theEquals = Objects::equals;
			theInitExpectedSize = 10;
			theLoadFactor = .75;
		}

		/**
		 * Causes this builder to build a set whose hash and comparison are defined externally
		 * 
		 * @param hasher The hash function for values in the set
		 * @param equals The equivalence check for values in the set
		 * @return This builder
		 */
		public B withEquivalence(ToIntFunction<Object> hasher, BiFunction<Object, Object, Boolean> equals) {
			theHasher = hasher;
			theEquals = equals;
			return (B) this;
		}

		/**
		 * Causes this builder to build a set whose values are stored by identity, instead of notional equivalence
		 * 
		 * @return This builder
		 */
		public B identity() {
			return withEquivalence(System::identityHashCode, (o1, o2) -> o1 == o2);
		}

		/**
		 * @param loadFactor The load factor for the set that this builder creates
		 * @return This builder
		 */
		public B withLoadFactor(double loadFactor) {
			if (loadFactor < MIN_LOAD_FACTOR || loadFactor > MAX_LOAD_FACTOR)
				throw new IllegalArgumentException("Load factor must be between " + MIN_LOAD_FACTOR + " and " + MAX_LOAD_FACTOR);
			theLoadFactor = loadFactor;
			return (B) this;
		}

		/**
		 * @param initExpectedSize The number of values that the set created by this builder should accommodate without re-hashing the table
		 * @return This builder
		 */
		public B withInitialCapacity(int initExpectedSize) {
			theInitExpectedSize = initExpectedSize;
			return (B) this;
		}

		/**
		 * @param <E> The value type for the set
		 * @return An empty {@link BetterHashSet} built according to this builder's settings
		 */
		public <E> BetterHashSet<E> build() {
			return build((Collection<? extends E>) null);
		}

		/**
		 * @param <E> The value type for the set
		 * @param values The initial values to insert into the set
		 * @return A {@link BetterHashSet} built according to this builder's settings, with the given initial content
		 */
		public <E> BetterHashSet<E> build(E... values) {
			return build(Arrays.asList(values));
		}

		/**
		 * @param <E> The value type for the set
		 * @param values The initial values to insert into the set
		 * @return A {@link BetterHashSet} built according to this builder's settings, with the given initial content
		 */
		public <E> BetterHashSet<E> build(Iterable<? extends E> values) {
			return new BetterHashSet<>(getLocker(), theHasher, theEquals, theInitExpectedSize, theLoadFactor, getDescription(), values);
		}
	}

	/** @return A builder to create a {@link BetterHashSet} */
	public static HashSetBuilder<?> build() {
		return new HashSetBuilder<>("better-hash-set");
	}

	private final CollectionLockingStrategy theLocker;
	private final ToIntFunction<Object> theHasher;
	private final BiFunction<Object, Object, Boolean> theEquals;
	private final AtomicLong theFirstIdCreator;
	private final AtomicLong theLastIdCreator;
	private final Object theIdentity;

	private final double theLoadFactor;

	private HashTableEntry[] theTable;
	private HashEntry theFirst;
	private HashEntry theLast;
	private int theSize;

	private BetterHashSet(Function<Object, CollectionLockingStrategy> locker, //
		ToIntFunction<Object> hasher, BiFunction<Object, Object, Boolean> equals, //
		int initExpectedSize, double loadFactor, Object identity, //
		Iterable<? extends E> initialValues) {
		theHasher = hasher;
		theEquals = equals;
		theFirstIdCreator = new AtomicLong(-1);
		theLastIdCreator = new AtomicLong(0);
		theIdentity = identity;

		if (loadFactor < MIN_LOAD_FACTOR || loadFactor > MAX_LOAD_FACTOR)
			throw new IllegalArgumentException("Load factor must be between " + MIN_LOAD_FACTOR + " and " + MAX_LOAD_FACTOR);
		theLoadFactor = loadFactor;
		rehash(initExpectedSize);
		// Add initial values before creating the lock. Initial values are always thread-safe, since nothing else can possibly
		// have a reference to this collection yet.
		if (initialValues != null)
			addAll(initialValues);
		theLocker = locker.apply(this);
	}

	@Override
	public Object getIdentity() {
		return theIdentity;
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

	private void checkIntegrity() {
		if (!BetterCollections.isTesting())
			return;
		HashEntry entry = theFirst;
		HashEntry last = null;
		int entryCount = 0;
		while (entry != null) {
			if (!entry.getElementId().isPresent())
				throw new IllegalStateException("Entry removed: " + entry);
			entryCount++;
			last = entry;
			entry = entry.next;
		}
		if (theLast != last)
			throw new IllegalStateException("Expected last to be " + theLast + ", but was " + last);
		if (entryCount != theSize)
			throw new IllegalStateException(
				"Expected size " + theSize + " but only encountered " + entryCount + " elements: last was " + last);
	}

	private void rehash(int expectedSize) {
		int tableSize = tableSizeFor((int) Math.ceil(expectedSize / theLoadFactor));
		HashTableEntry[] table = new BetterHashSet.HashTableEntry[tableSize];
		HashEntry entry = theFirst;
		int entryCount = 0;
		while (entry != null) {
			insert(table, entry, -1, null);
			entry = entry.next;
			entryCount++;
		}
		if (entryCount != theSize)
			throw new IllegalStateException("Expected size " + theSize + " but only encountered " + entryCount + " elements");
		theTable = table;
	}

	private void insert(HashTableEntry[] table, HashEntry entry, int tableIndex, HashEntry adjacentEntry) {
		if (tableIndex < 0)
			tableIndex = getTableIndex(table.length, entry.hashCode());
		HashTableEntry tableEntry = table[tableIndex];
		if (tableEntry == null)
			tableEntry = table[tableIndex] = new HashTableEntry(tableIndex);
		tableEntry.add(entry, adjacentEntry);
		theLocker.modified();
	}

	/**
	 * Ensures that this set's load factor will be satisfied if the collection grows to the given size
	 * 
	 * @param expectedSize The capacity to check for
	 * @return Whether this table was rebuilt
	 */
	public boolean ensureCapacity(int expectedSize) {
		try (Transaction t = lock(true, null)) {
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
		try (Transaction t = lock(false, null)) {
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
	public ThreadConstraint getThreadConstraint() {
		return theLocker.getThreadConstraint();
	}

	@Override
	public boolean isLockSupported() {
		return theLocker.isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		if (theLocker == null)
			return Transaction.NONE;
		return theLocker.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		if (theLocker == null)
			return Transaction.NONE;
		return theLocker.tryLock(write, cause);
	}

	@Override
	public CoreId getCoreId() {
		return theLocker.getCoreId();
	}

	@Override
	public long getStamp() {
		return theLocker.getStamp();
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

	/**
	 * A version of {@link #getOrAdd(Object, ElementId, ElementId, boolean, Runnable, Runnable)} that allows direct specification of hash
	 * code and equality for more flexible searching of the table
	 * 
	 * @param hashCode The hash code to search for
	 * @param equals The predicate for matching a value in the table
	 * @param value Supplies the value in the case that it does not exist in the table
	 * @param after The element after which to add the value (may be null)
	 * @param before The element before which to add the value (may be null)
	 * @param first Whether to prefer adding the value closer to the after element (or the beginning of the set) or the before element (or
	 *        the end of the set)
	 * @param preAdd Will execute if the value is added, before it is added
	 * @param postAdd Will execute if the value is added, after it is added
	 * @return The existing or added element
	 */
	public CollectionElement<E> getOrAdd(int hashCode, Predicate<? super E> equals, Supplier<? extends E> value, ElementId after,
		ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
		checkIntegrity();
		HashTableEntry[] table = theTable;
		int tableIndex = getTableIndex(table.length, hashCode);
		HashTableEntry tableEntry = table[tableIndex];
		HashEntry entry = tableEntry == null ? null : tableEntry.findForInsert(hashCode, equals, OptimisticContext.TRUE);
		if (entry != null && entry.hashCode == hashCode && equals.test(entry.theValue))
			return entry;

		// Ordered insert is O(n), but we'll support it
		try (Transaction t = lock(true, null)) {
			if (preAdd != null)
				preAdd.run();
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
			entry = linkUp(hashCode, value.get(), after, before, first);
			theSize++;
			theLocker.modified();
			insert(table, entry, tableIndex, adjacent);
			if (postAdd != null)
				postAdd.run();
			checkIntegrity();
			return entry.immutable();
		}
	}

	private HashEntry linkUp(int hashCode, E value, ElementId after, ElementId before, boolean first) {
		HashEntry entry;
		if (first) {
			if (after != null) {
				HashEntry afterEntry = ((HashId) after).entry;
				afterEntry.check();
				entry = new HashEntry(afterEntry.theOrder, value, hashCode);
				entry.previous = afterEntry;
				entry.next = afterEntry.next;
				if (afterEntry.next != null)
					afterEntry.next.previous = entry;
				else
					theLast = entry;
				afterEntry.next = entry;
				while (afterEntry != null && afterEntry.theOrder == afterEntry.next.theOrder) {
					afterEntry.theOrder--;
					afterEntry = afterEntry.previous;
				}
				if (theFirst.theOrder == theFirstIdCreator.get())
					theFirstIdCreator.getAndDecrement();
			} else {
				entry = new HashEntry(theFirstIdCreator.getAndDecrement(), value, hashCode);
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
				entry = new HashEntry(beforeEntry.theOrder, value, hashCode);
				entry.next = beforeEntry;
				entry.previous = beforeEntry.previous;
				if (beforeEntry.previous != null)
					beforeEntry.previous.next = entry;
				else
					theFirst = entry;
				beforeEntry.previous = entry;
				while (beforeEntry != null && beforeEntry.theOrder == beforeEntry.previous.theOrder) {
					beforeEntry.theOrder++;
					beforeEntry = beforeEntry.next;
				}
				if (theLast.theOrder == theLastIdCreator.get())
					theLastIdCreator.getAndIncrement();
			} else {
				entry = new HashEntry(theLastIdCreator.getAndIncrement(), value, hashCode);
				if (theLast != null)
					theLast.next = entry;
				entry.previous = theLast;
				theLast = entry;
				if (theFirst == null)
					theFirst = entry;
			}
		}
		return entry;
	}

	@Override
	public CollectionElement<E> addElement(E value, ElementId after, ElementId before, boolean first)
		throws UnsupportedOperationException, IllegalArgumentException {
		boolean[] added = new boolean[1];
		CollectionElement<E> element = getOrAdd(theHasher.applyAsInt(value), equalsTest(value), () -> value, after, before, first, null,
			() -> added[0] = true);
		return added[0] ? element : null;
	}

	@Override
	public String canMove(ElementId valueEl, ElementId after, ElementId before) {
		return null;
	}

	@Override
	public CollectionElement<E> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
		throws UnsupportedOperationException, IllegalArgumentException {
		checkIntegrity();
		if (valueEl.equals(after))
			after = CollectionElement.getElementId(getAdjacentElement(after, false));
		if (valueEl.equals(before))
			before = CollectionElement.getElementId(getAdjacentElement(before, true));
		HashId hashId = (BetterHashSet<E>.HashId) valueEl;
		if (!hashId.isPresent())
			throw new NoSuchElementException("Element has been removed");
		else if (hashId.getSet() != this)
			throw new NoSuchElementException("Element does not belong to this set");
		try (Transaction t = lock(true, null)) {
			HashEntry entry = hashId.entry;
			if (first) {
				if ((after == null && entry.previous == null)
					|| (after != null && entry.previous != null && after.equals(entry.previous.getElementId())))
					return entry.immutable();
			} else {
				if ((before == null && entry.next == null)
					|| (before != null && entry.next != null && before.equals(entry.next.getElementId())))
					return entry.immutable();
			}
			// Remove the element
			if (entry.previous != null)
				entry.previous.next = entry.next;
			else
				theFirst = entry.next;
			if (entry.next != null)
				entry.next.previous = entry.previous;
			else
				theLast = entry.previous;
			HashEntry newEntry;
			ElementId prevTreeEntry = CollectionElement
				.getElementId(entry.theTableEntry.entries.getAdjacentElement(entry.theTreeNode.getElementId(), false));
			entry.theTreeNode.remove();
			entry.next = entry.previous = null;
			if (afterRemove != null) {
				long preStamp = getStamp();
				theSize--;
				afterRemove.run();
				if (getStamp() != preStamp)
					throw new IllegalStateException("after-remove callback may not modify the set");
				checkIntegrity();
				theSize++;
			}
			newEntry = linkUp(entry.hashCode, entry.theValue, after, before, first);
			ElementId added = entry.theTableEntry.entries.addElement(newEntry, prevTreeEntry, null, true).getElementId();
			newEntry.placedAt(entry.theTableEntry, entry.theTableEntry.entries.mutableElement(added));
			checkIntegrity();
			theLocker.modified();
			return newEntry.immutable();
		}
	}

	/**
	 * @param value The value to create an equality tester for
	 * @return A predicate that returns true for values that are equivalent to the given value by this set's reckoning
	 */
	public Predicate<E> equalsTest(E value) {
		return v -> theEquals.apply(v, value);
	}

	@Override
	public CollectionElement<E> getElement(E value, boolean first) {
		HashEntry entry = getEntry(theHasher.applyAsInt(value), equalsTest(value));
		return entry == null ? null : entry.immutable();
	}

	/**
	 * @param hashCode The hash code for the element to get
	 * @param equals The equality test for the element to get
	 * @return The element matching the given hash/equality in this table, or null
	 */
	public CollectionElement<E> getElement(int hashCode, Predicate<? super E> equals) {
		HashEntry entry = getEntry(hashCode, equals);
		return entry == null ? null : entry.immutable();
	}

	@Override
	public CollectionElement<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
		return getOrAdd(theHasher.applyAsInt(value), equalsTest(value), () -> value, after, before, first, preAdd, postAdd);
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
	public BetterList<CollectionElement<E>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this)
			return BetterList.of(getElement(sourceEl));
		return BetterList.empty();
	}

	@Override
	public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
		if (sourceCollection == this) {
			if (!(localElement instanceof BetterHashSet.HashId) || ((HashId) localElement).getSet() != this)
				throw new NoSuchElementException(localElement + " does not belong to this set");
			return BetterList.of(localElement);
		}
		return BetterList.empty();
	}

	@Override
	public ElementId getEquivalentElement(ElementId equivalentEl) {
		if (!(equivalentEl instanceof BetterHashSet.HashId) || ((HashId) equivalentEl).getSet() != this)
			return null;
		return equivalentEl;
	}

	/**
	 * @param elementId The element ID to check
	 * @return Whether the given element's location in the table is valid (i.e. its hash code has not changed)
	 */
	@SuppressWarnings("static-method")
	public boolean isValid(ElementId elementId){
		HashEntry entry=((HashId) elementId).entry.check();
		return entry.isValid();
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		return addAll((Iterable<? extends E>) c);
	}

	/**
	 * @param c The values to add to the set
	 * @return Whether any values were added to the et
	 */
	public boolean addAll(Iterable<? extends E> c) {
		try (Transaction t = lock(true, null); Transaction ct = Transactable.lock(c, false, null)) {
			boolean added = false;
			for (E e : c) {
				added |= add(e);
			}
			return added;
		}
	}

	@Override
	public void clear() {
		if (isEmpty())
			return;
		try (Transaction t = lock(true, null)) {
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

		BetterHashSet<E> getSet() {
			return BetterHashSet.this;
		}

		@Override
		public boolean isPresent() {
			return entry.isPresent();
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

	/** Represents the storage of one value in a {@link BetterHashSet} */
	protected class HashEntry implements MutableCollectionElement<E> {
		HashTableEntry theTableEntry;
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

		void placedAt(HashTableEntry tableEntry, MutableBinaryTreeNode<HashEntry> treeNode) {
			theTableEntry = tableEntry;
			theTreeNode = treeNode;
		}

		private boolean isPresent() {
			return theTreeNode != null && theTreeNode.getElementId().isPresent();
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

		<X> boolean repair(RepairListener<E, X> listener) {
			int newHash = theHasher.applyAsInt(theValue);
			if (newHash == hashCode)
				return false;
			X data = listener.removed(immutable());
			theTreeNode.remove();
			if (getEntry(newHash, equalsTest(theValue)) != null)
				listener.disposed(theValue, data);
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
			try (Transaction t = lock(true, null)) {
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
				checkIntegrity();
				theTreeNode.remove();
				if (theFirst == this)
					theFirst = next;
				if (theLast == this)
					theLast = previous;
				if (previous != null)
					previous.next = next;
				if (next != null)
					next.previous = previous;
				theSize--;
				theLocker.modified();
				checkIntegrity();
			}
		}

		int compareToNode(HashEntry node) {
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
			entries = BetterTreeList.<HashEntry> build().build();
		}

		void add(HashEntry entry, HashEntry adjacentEntry) {
			if (adjacentEntry != null) {
				ElementId id;
				if (entry.compareToNode(adjacentEntry) >= 0)
					id = entries.addElement(entry, adjacentEntry.theTreeNode.getElementId(), null, true).getElementId();
				else
					id = entries.addElement(entry, null, adjacentEntry.theTreeNode.getElementId(), false).getElementId();
				entry.placedAt(this, entries.mutableElement(id));
				return;
			}
			BinaryTreeNode<HashEntry> node = entries.getRoot();
			if (node == null) {
				entry.placedAt(this, entries.mutableNodeFor(entries.addElement(entry, false)));
				return;
			}
			node = node.findClosest(n -> entry.compareToNode(n.get()), true, false, null);
			ElementId id;
			if (entry.hashCode() >= node.get().hashCode())
				id = entries.addElement(entry, node.getElementId(), null, true).getElementId();
			else
				id = entries.addElement(entry, null, node.getElementId(), false).getElementId();
			entry.placedAt(this, entries.mutableElement(id));
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
			if (!ctx.getAsBoolean() || node == null || node.get().hashCode() != hashCode)
				return null;
			BinaryTreeNode<HashEntry> node2 = node;
			while (ctx.getAsBoolean() && node2 != null && node2.get().hashCode() == hashCode) {
				if (equals.test(node2.get().get()))
					return node2.get();
				node2 = node2.getClosest(true);
			}
			if (!ctx.getAsBoolean())
				return null;
			node2 = node.getClosest(false);
			while (ctx.getAsBoolean() && node2 != null && node2.get().hashCode() == hashCode) {
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

		@Override
		public String toString() {
			return "[" + theTableIndex + "]" + entries;
		}
	}
}
