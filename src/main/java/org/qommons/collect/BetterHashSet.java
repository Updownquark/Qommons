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
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BinaryTreeNode;
import org.qommons.tree.MutableBinaryTreeNode;

public class BetterHashSet<E> implements BetterSet<E> {
	public static final double MIN_LOAD_FACTOR = 0.2;
	public static final double MAX_LOAD_FACTOR = 0.9;

	public static class HashSetBuilder {
		private boolean isSafe;
		private ToIntFunction<Object> theHasher;
		private BiFunction<Object, Object, Boolean> theEquals;
		private int theInitExpectedSize;
		private double theLoadFactor;

		protected HashSetBuilder() {
			isSafe = true;
			theHasher = Objects::hashCode;
			theEquals = Objects::equals;
			theInitExpectedSize = 10;
			theLoadFactor = .75;
		}

		public HashSetBuilder unsafe() {
			isSafe = false;
			return this;
		}

		public HashSetBuilder withEquivalence(ToIntFunction<Object> hasher, BiFunction<Object, Object, Boolean> equals) {
			theHasher = hasher;
			theEquals = equals;
			return this;
		}

		public HashSetBuilder identity() {
			return withEquivalence(System::identityHashCode, (o1, o2) -> o1 == o2);
		}

		public HashSetBuilder withLoadFactor(double loadFactor) {
			if (loadFactor < MIN_LOAD_FACTOR || loadFactor > MAX_LOAD_FACTOR)
				throw new IllegalArgumentException("Load factor must be between " + MIN_LOAD_FACTOR + " and " + MAX_LOAD_FACTOR);
			theLoadFactor = loadFactor;
			return this;
		}

		public HashSetBuilder withInitialCapacity(int initExpectedSize) {
			theInitExpectedSize = initExpectedSize;
			return this;
		}

		public <E> BetterHashSet<E> buildSet() {
			return new BetterHashSet<>(isSafe ? new StampedLockingStrategy() : new FastFailLockingStrategy(), theHasher, theEquals,
				theInitExpectedSize, theLoadFactor);
		}

		public <E> BetterHashSet<E> buildSet(E... values) {
			return buildSet(Arrays.asList(values));
		}

		public <E> BetterHashSet<E> buildSet(Collection<? extends E> values) {
			BetterHashSet<E> set = buildSet();
			set.addAll(values);
			return set;
		}
	}

	public static HashSetBuilder build() {
		return new HashSetBuilder();
	}

	private final CollectionLockingStrategy theLocker;
	private final ToIntFunction<Object> theHasher;
	private final BiFunction<Object, Object, Boolean> theEquals;
	private final AtomicLong theIdCreator;

	private double theLoadFactor;

	private HashTableEntry[] theTable;
	private HashEntry theFirst;
	private HashEntry theLast;
	private int theSize;

	private BetterHashSet(CollectionLockingStrategy locker, ToIntFunction<Object> hasher, BiFunction<Object, Object, Boolean> equals,
		int initExpectedSize, double loadFactor) {
		theLocker = locker;
		theHasher = hasher;
		theEquals = equals;
		theIdCreator = new AtomicLong();

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
		int tableIndex = entry.hashCode() % theTable.length;
		HashTableEntry tableEntry = theTable[tableIndex];
		if (tableEntry == null)
			tableEntry = theTable[tableIndex] = new HashTableEntry(tableIndex);
		tableEntry.add(entry);
	}

	protected void ensureCapacity(int expectedSize) {
		try (Transaction t = lock(false, false, null)) {
			int neededTableSize = (int) Math.ceil(expectedSize / theLoadFactor);
			if (neededTableSize > theTable.length)
				rehash(expectedSize);
		}
	}

	private HashEntry getEntry(int hashCode, E value) {
		int tableIndex = hashCode % theTable.length;
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
	public String canAdd(E value) {
		return null;
	}

	@Override
	public CollectionElement<E> addElement(E value, boolean first) {
		try (Transaction t = lock(true, true, null)) {
			int hashCode = theHasher.applyAsInt(value);
			HashEntry entry = getEntry(hashCode, value);
			if (entry != null)
				return null;
			ensureCapacity(theSize + 1);
			entry = new HashEntry(value, hashCode);
			insert(entry);
			if (theLast != null)
				theLast.next = entry;
			entry.previous = theLast;
			theLast = entry;
			if (theFirst == null)
				theFirst = entry;
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
			return Long.compare(entry.theId, ((HashId) o).entry.theId);
		}

		@Override
		public int hashCode() {
			return entry.hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			return entry == ((HashId) obj).entry;
		}
	}

	private class HashEntry implements MutableCollectionElement<E> {
		private final long theId;
		private final int hashCode;
		private MutableBinaryTreeNode<HashEntry> theTreeNode;
		private E theValue;
		HashEntry next;
		HashEntry previous;

		HashEntry(E value, int hashCode) {
			theId = theIdCreator.getAndIncrement();
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
			if (hashCode != theHasher.applyAsInt(value))
				return StdMsg.ILLEGAL_ELEMENT;
			return null;
		}

		@Override
		public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, false, null)) {
				if (!isPresent())
					throw new IllegalStateException("This element has been removed");
				if (hashCode != theHasher.applyAsInt(value))
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
				theValue = value;
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
		public String canAdd(E value, boolean before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
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

			@Override
			public String canAdd(E value, boolean before) {
				return theEntry.canAdd(value, before);
			}

			@Override
			public ElementId add(E value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
				return theEntry.add(value, before);
			}
		}
	}
}
