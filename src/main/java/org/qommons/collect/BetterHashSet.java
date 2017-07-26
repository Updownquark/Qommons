package org.qommons.collect;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.MutableElementHandle.ReversedMutableElement;
import org.qommons.collect.MutableElementHandle.StdMsg;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BinaryTreeNode;
import org.qommons.tree.MutableBinaryTreeNode;

public class BetterHashSet<E> implements BetterSet<E> {
	public static final double MIN_LOAD_FACTOR = 0.2;
	public static final double MAX_LOAD_FACTOR = 0.9;

	public static class Builder<E> {
		private boolean isSafe;
		private ToIntFunction<Object> theHasher;
		private BiFunction<Object, Object, Boolean> theEquals;
		private int theInitExpectedSize;
		private double theLoadFactor;

		Builder() {
			isSafe = true;
			theHasher = Objects::hashCode;
			theEquals = Objects::equals;
			theInitExpectedSize = 10;
			theLoadFactor = .75;
		}

		public Builder<E> unsafe() {
			isSafe = false;
			return this;
		}

		public Builder<E> withEquivalence(ToIntFunction<Object> hasher, BiFunction<Object, Object, Boolean> equals) {
			theHasher = hasher;
			theEquals = equals;
			return this;
		}

		public Builder<E> identity() {
			return withEquivalence(System::identityHashCode, (o1, o2) -> o1 == o2);
		}

		public Builder<E> withLoadFactor(double loadFactor) {
			if (loadFactor < MIN_LOAD_FACTOR || loadFactor > MAX_LOAD_FACTOR)
				throw new IllegalArgumentException("Load factor must be between " + MIN_LOAD_FACTOR + " and " + MAX_LOAD_FACTOR);
			theLoadFactor = loadFactor;
			return this;
		}

		public Builder<E> withInitialCapacity(int initExpectedSize) {
			theInitExpectedSize = initExpectedSize;
			return this;
		}

		public BetterHashSet<E> build() {
			return new BetterHashSet<>(isSafe ? new StampedLockingStrategy() : new FastFailLockingStrategy(), theHasher, theEquals,
				theInitExpectedSize, theLoadFactor);
		}
	}

	public static <E> Builder<E> build() {
		return new Builder<>();
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
		theTable = new BetterHashSet.HashTableEntry[(int) Math.ceil(initExpectedSize / theLoadFactor)];
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
		int neededTableSize = (int) Math.ceil(expectedSize / theLoadFactor);
		if (neededTableSize > theTable.length)
			rehash(expectedSize);
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
	public Transaction lock(boolean write, Object cause) {
		return theLocker.lock(write, cause);
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
	public ElementId addElement(E value) {
		try (Transaction t = lock(true, null)) {
			int hashCode = theHasher.applyAsInt(value);
			HashEntry entry = getEntry(hashCode, value);
			if (entry != null)
				return null;
			ensureCapacity(theSize + 1);
			entry = new HashEntry(value, hashCode);
			insert(entry);
			entry.previous = theLast;
			theLast = entry;
			if (theFirst == null)
				theFirst = entry;
			theSize++;
			return entry.immutable();
		}
	}

	@Override
	public boolean forElement(E value, Consumer<? super ElementHandle<? extends E>> onElement, boolean first) {
		try (Transaction t = lock(false, null)) {
			HashEntry entry = getEntry(theHasher.applyAsInt(value), value);
			if (entry != null)
				onElement.accept(entry.immutable());
			return entry != null;
		}
	}

	@Override
	public boolean forMutableElement(E value, Consumer<? super MutableElementHandle<? extends E>> onElement, boolean first) {
		try (Transaction t = lock(true, null)) {
			HashEntry entry = getEntry(theHasher.applyAsInt(value), value);
			if (entry != null)
				onElement.accept(entry);
			return entry != null;
		}
	}

	@Override
	public <T> T ofElementAt(ElementId elementId, Function<? super ElementHandle<? extends E>, T> onElement) {
		if (!(elementId instanceof BetterHashSet.HashEntry))
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		HashEntry entry = (HashEntry) elementId;
		try (Transaction t = lock(false, null)) {
			entry.check();
			return onElement.apply(entry.immutable());
		}
	}

	@Override
	public <T> T ofMutableElementAt(ElementId elementId, Function<? super MutableElementHandle<? extends E>, T> onElement) {
		if (!(elementId instanceof BetterHashSet.HashEntry))
			throw new IllegalArgumentException(StdMsg.NOT_FOUND);
		HashEntry entry = (HashEntry) elementId;
		try (Transaction t = lock(false, null)) {
			entry.check();
			return onElement.apply(entry);
		}
	}

	@Override
	public MutableElementSpliterator<E> mutableSpliterator(boolean fromStart) {
		class MutableHashSpliterator implements MutableElementSpliterator<E> {
			private HashEntry current;
			private boolean wasNext;

			MutableHashSpliterator(HashEntry current, boolean next) {
				this.current = current;
				this.wasNext = !next;
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

			protected boolean tryElement(boolean left) {
				if (current == null)
					current = wasNext ? theLast : theFirst;
				if (current == null)
					return false;
				// We can tolerate external modification as long as the node that this spliterator is anchored to has not been removed
				// This situation is easy to detect
				if (current.next == null && theLast != current)
					throw new ConcurrentModificationException(
						"The collection has been modified externally such that this spliterator has been orphaned");
				if (wasNext != left) {
					HashEntry next = current.previous;
					if (next != null)
						current = next;
					else {
						wasNext = left;
						return false;
					}
				}
				return true;
			}

			@Override
			public boolean tryAdvanceElement(Consumer<? super ElementHandle<E>> action) {
				try (Transaction t = lock(false, null)) {
					if (!tryElement(false))
						return false;
					action.accept(current.immutable());
					return true;
				}
			}

			@Override
			public boolean tryReverseElement(Consumer<? super ElementHandle<E>> action) {
				try (Transaction t = lock(false, null)) {
					if (!tryElement(true))
						return false;
					action.accept(current.immutable());
					return true;
				}
			}

			@Override
			public boolean tryAdvanceElementM(Consumer<? super MutableElementHandle<E>> action) {
				try (Transaction t = lock(true, null)) {
					if (!tryElement(false))
						return false;
					action.accept(new MutableSpliteratorEntry(current));
					return true;
				}
			}

			@Override
			public boolean tryReverseElementM(Consumer<? super MutableElementHandle<E>> action) {
				try (Transaction t = lock(true, null)) {
					if (!tryElement(true))
						return false;
					action.accept(new MutableSpliteratorEntry(current));
					return true;
				}
			}

			@Override
			public void forEachElement(Consumer<? super ElementHandle<E>> action) {
				try (Transaction t = lock(false, null)) {
					while (tryElement(false))
						action.accept(current);
				}
			}

			@Override
			public void forEachElementReverse(Consumer<? super ElementHandle<E>> action) {
				try (Transaction t = lock(false, null)) {
					while (tryElement(true))
						action.accept(current);
				}
			}

			@Override
			public void forEachElementM(Consumer<? super MutableElementHandle<E>> action) {
				try (Transaction t = lock(true, null)) {
					while (tryElement(false))
						action.accept(new MutableSpliteratorEntry(current));
				}
			}

			@Override
			public void forEachElementReverseM(Consumer<? super MutableElementHandle<E>> action) {
				try (Transaction t = lock(true, null)) {
					while (tryElement(true))
						action.accept(new MutableSpliteratorEntry(current));
				}
			}

			@Override
			public MutableElementSpliterator<E> trySplit() {
				return null; // No real good way to do this
			}

			class MutableSpliteratorEntry implements MutableElementHandle<E> {
				private final HashEntry theEntry;

				MutableSpliteratorEntry(HashEntry entry) {
					theEntry = entry;
				}

				@Override
				public ElementId getElementId() {
					return theEntry;
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
							newWasNext = wasNext;
						}
						theEntry.remove();
						current = newCurrent;
						wasNext = newWasNext;
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
		return new MutableHashSpliterator(fromStart ? theFirst : theLast, fromStart);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		try (Transaction t = lock(true, null); Transaction ct = Transactable.lock(c, false, null)) {
			for (E e : c)
				add(e);
			return !c.isEmpty();
		}
	}

	@Override
	public void clear() {
		try (Transaction t = lock(true, null)) {
			for (int i = 0; i < theTable.length; i++)
				theTable[i] = null;
			theFirst = null;
			theLast = null;
			theSize = 0;
		}
	}

	private class HashEntry implements ElementId, MutableElementHandle<E> {
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

		void check() {
			if (next == null && theLast != this)
				throw new IllegalArgumentException("Element has been removed");
		}

		@Override
		public ElementId getElementId() {
			return this;
		}

		@Override
		public E get() {
			return theValue;
		}

		@Override
		public int compareTo(ElementId o) {
			return Long.compare(theId, ((HashEntry) o).theId);
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
			if (hashCode != theHasher.applyAsInt(value))
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			theValue = value;
		}

		@Override
		public String canRemove() {
			return theTreeNode.canRemove();
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			try (Transaction t = lock(true, null)) {
				theTreeNode.remove();
				if (theFirst == this)
					theFirst = next;
				if (theLast == this)
					theLast = previous;
				next = null;
				previous = null;
				theSize--;
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
		public ReversedHashEntry reverse() {
			return new ReversedHashEntry(this);
		}

		@Override
		public ImmutableEntry immutable() {
			return new ImmutableEntry(this);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			return theEquals.apply(theValue, obj);
		}

		@Override
		public String toString() {
			return String.valueOf(theValue);
		}
	}

	class ImmutableEntry implements ElementHandle<E>, ElementId {
		private final HashEntry theEntry;

		ImmutableEntry(BetterHashSet<E>.HashEntry entry) {
			theEntry = entry;
		}

		protected HashEntry getEntry() {
			return theEntry;
		}

		@Override
		public ElementId getElementId() {
			return this;
		}

		@Override
		public E get() {
			return theEntry.get();
		}

		@Override
		public int compareTo(ElementId o) {
			if (o instanceof BetterHashSet.ImmutableEntry)
				o = ((ImmutableEntry) o).theEntry;
			return theEntry.compareTo(o);
		}

		@Override
		public ImmutableEntry reverse() {
			return new ReversedImmutableEntry(theEntry);
		}

		@Override
		public int hashCode() {
			return theEntry.hashCode;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof BetterHashSet.ImmutableEntry)
				obj = ((ImmutableEntry) obj).theEntry;
			return theEntry.equals(obj);
		}

		@Override
		public String toString() {
			return theEntry.toString();
		}
	}

	class ReversedHashEntry extends ReversedMutableElement<E> implements ElementId {
		ReversedHashEntry(BetterHashSet<E>.HashEntry entry) {
			super(entry);
		}

		@Override
		protected HashEntry getWrapped() {
			return (BetterHashSet<E>.HashEntry) super.getWrapped();
		}

		@Override
		public ElementId getElementId() {
			return this;
		}

		@Override
		public int compareTo(ElementId o) {
			return -getWrapped().compareTo(o.reverse());
		}

		@Override
		public HashEntry reverse() {
			return getWrapped();
		}
	}

	class ReversedImmutableEntry extends ImmutableEntry {
		ReversedImmutableEntry(BetterHashSet<E>.HashEntry entry) {
			super(entry);
		}

		@Override
		public int compareTo(ElementId o) {
			return -super.compareTo(o.reverse());
		}

		@Override
		public ImmutableEntry reverse() {
			return new ImmutableEntry(getEntry());
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
				entry.placedAt(entries.mutableNodeFor(entries.addElement(entry)));
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
}
