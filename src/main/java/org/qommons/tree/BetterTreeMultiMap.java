package org.qommons.tree;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;

import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedMultiMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MultiMapEntryHandle;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableElementSpliterator;

public class BetterTreeMultiMap<K, V> implements BetterSortedMultiMap<K, V> {
	private final BetterTreeSet<MultiEntryImpl> theEntries;
	private final BetterTreeList<KVEntry> theValues;
	private final Comparator<? super K> theKeyCompare;
	private final Comparator<? super V> theValueCompare;
	private final Comparator<MultiEntry<K, V>> theEntryCompare;
	private final boolean isUniqueValued;

	public BetterTreeMultiMap(boolean safe, Comparator<? super K> keyCompare, Comparator<? super V> valueCompare, boolean uniqueValues) {
		if (uniqueValues && valueCompare == null)
			throw new IllegalArgumentException("Values cannot be kept unique without a comparator");
		theKeyCompare = keyCompare;
		theValueCompare = valueCompare;
		theEntryCompare = (entry1, entry2) -> theKeyCompare.compare(entry1.getKey(), entry2.getKey());
		isUniqueValued = uniqueValues;
		theEntries = new BetterTreeSet<>(safe, theEntryCompare);
		theValues = new BetterTreeList<>(safe);
	}

	@Override
	public Transaction lock(boolean write, boolean structural, Object cause) {
		Transaction entryT = theEntries.lock(write, structural, cause);
		Transaction valueT = theValues.lock(write, structural, cause);
		return () -> {
			valueT.close();
			entryT.close();
		};
	}

	@Override
	public long getStamp(boolean structuralOnly) {
		return theValues.getStamp(structuralOnly);
	}

	@Override
	public BetterList<V> get(Object key) {
		if (!keySet().belongs(key))
			return isUniqueValued ? BetterSortedSet.empty(theValueCompare) : BetterList.empty();
		return isUniqueValued ? new ValueSortedSet((K) key, null) : new ValueList((K) key, null);
	}

	@Override
	public MultiEntryHandle<K, V> getEntry(ElementId keyId) {
		return theEntries.getElement(keyId).get();
	}

	@Override
	public MultiMapEntryHandle<K, V> putEntry(K key, V value, ElementId afterKey, ElementId beforeKey, boolean first) {
		if (!keySet().belongs(key))
			throw new IllegalArgumentException("Unaccepable key: " + key);
		try (Transaction t = theValues.lock(true, null)) {
			ValueList values = (ValueList) get(key);
			// Throw an exception if an illegal position is specified
			boolean acquired = values.acquireEntry();
			if (afterKey != null) {
				MultiEntryImpl entry = theEntries.getElement(afterKey).get();
				if (acquired && entry.firstEntry.compareTo(values.theEntry.firstEntry) >= 0)
					throw new IllegalArgumentException(
						"Cannot insert the given key (" + key + ") after the given entry: " + entry.theKey);
				else if (!acquired && theKeyCompare.compare(entry.theKey, key) >= 0)
					throw new IllegalArgumentException(
						"Cannot insert the given key (" + key + ") after the given entry: " + entry.theKey);
			}
			if (beforeKey != null) {
				MultiEntryImpl entry = theEntries.getElement(beforeKey).get();
				if (acquired && entry.firstEntry.compareTo(values.theEntry.firstEntry) <= 0)
					throw new IllegalArgumentException(
						"Cannot insert the given key (" + key + ") before the given entry: " + entry.theKey);
				else if (!acquired && theKeyCompare.compare(entry.theKey, key) <= 0)
					throw new IllegalArgumentException(
						"Cannot insert the given key (" + key + ") before the given entry: " + entry.theKey);
			}
			CollectionElement<V> valueEl = values.addElement(value, first);
			return (MultiMapEntryHandle<K, V>) valueEl;
		}
	}

	@Override
	public BetterSortedSet<K> keySet() {
		return new KeySet();
	}

	@Override
	public BetterSortedSet<? extends MultiEntry<K, V>> entrySet() {
		return new EntrySet();
	}

	@Override
	public MultiEntryHandle<K, V> search(Comparable<? super K> search, SortedSearchFilter filter) {
		return (MultiEntryImpl) CollectionElement.get(entrySet().search(entry -> search.compareTo(entry.getKey()), filter));
	}

	private class KVEntry implements MultiMapEntryHandle<K, V> {
		final MultiEntryImpl theEntry;
		ElementId theElement;
		V theValue;

		KVEntry(BetterTreeMultiMap<K, V>.MultiEntryImpl entry, V value) {
			theEntry = entry;
			theValue = value;
		}

		@Override
		public ElementId getKeyId() {
			return theEntry.getElementId();
		}

		@Override
		public K getKey() {
			return theEntry.theKey;
		}

		@Override
		public ElementId getElementId() {
			return theElement;
		}

		@Override
		public V get() {
			return theValue;
		}
	}

	private class MultiEntryImpl implements MultiEntryHandle<K, V> {
		final K theKey;
		ElementId theEntryId;
		MultiEntryImpl previous;
		MultiEntryImpl next;
		ElementId firstEntry;

		MultiEntryImpl(K key) {
			theKey = key;
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public ElementId getElementId() {
			return theEntryId;
		}

		@Override
		public BetterCollection<V> getValues() {
			return theValueCompare == null ? new ValueList(theKey, this) : new ValueSortedSet(theKey, this);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theKey);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof BetterTreeMultiMap.MultiEntryImpl && Objects.equals(theKey, ((MultiEntryImpl) obj).theKey);
		}

		@Override
		public String toString() {
			return new StringBuilder().append(theKey).append('=').append(getValues()).toString();
		}
	}

	class EntrySet implements BetterSortedSet<MultiEntry<K, V>> {
		@Override
		public boolean belongs(Object o) {
			return o instanceof BetterTreeMultiMap.MultiEntryImpl;
		}

		@Override
		public Comparator<? super MultiEntry<K, V>> comparator() {
			return theEntryCompare;
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theEntries.getStamp(structuralOnly);
		}

		@Override
		public boolean isLockSupported() {
			return theEntries.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return BetterTreeMultiMap.this.lock(write, structural, cause);
		}

		@Override
		public boolean isEmpty() {
			return theEntries.isEmpty();
		}

		@Override
		public int size() {
			return theEntries.size();
		}

		@Override
		public CollectionElement<MultiEntry<K, V>> getElement(ElementId id) {
			return new EntryElement(theEntries.getElement(id).get());
		}

		@Override
		public CollectionElement<MultiEntry<K, V>> getTerminalElement(boolean first) {
			CollectionElement<KVEntry> valueEl = theValues.getTerminalElement(first);
			return valueEl == null ? null : new EntryElement(valueEl.get().theEntry);
		}

		@Override
		public CollectionElement<MultiEntry<K, V>> getAdjacentElement(ElementId elementId, boolean next) {
			MultiEntryImpl adj = CollectionElement.get(theEntries.getAdjacentElement(elementId, next));
			return adj == null ? null : new EntryElement(adj);
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return theEntries.getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return theEntries.getElementsAfter(id);
		}

		@Override
		public MutableCollectionElement<MultiEntry<K, V>> mutableElement(ElementId id) {
			return new MutableEntryElement(theEntries.getElement(id).get(), null);
		}

		@Override
		public String canAdd(MultiEntry<K, V> value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<MultiEntry<K, V>> addElement(MultiEntry<K, V> value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public CollectionElement<MultiEntry<K, V>> getElement(int index) {
			MultiEntryImpl entry = theEntries.getElement(index).get();
			return entry == null ? null : new EntryElement(entry);
		}

		@Override
		public CollectionElement<MultiEntry<K, V>> search(Comparable<? super MultiEntry<K, V>> search, SortedSearchFilter filter) {
			MultiEntryImpl entry = CollectionElement.get(theEntries.search(search, filter));
			return entry == null ? null : new EntryElement(entry);
		}

		@Override
		public int indexFor(Comparable<? super MultiEntry<K, V>> search) {
			return theEntries.indexFor(search);
		}

		@Override
		public void clear() {
			try (Transaction t = BetterTreeMultiMap.this.lock(true, null)) {
				theEntries.clear();
				theValues.clear();
			}
		}

		private class EntryElement implements CollectionElement<MultiEntry<K, V>> {
			final MultiEntryImpl theEntry;

			EntryElement(MultiEntryImpl entry) {
				theEntry = entry;
			}

			@Override
			public ElementId getElementId() {
				return theEntry.getElementId();
			}

			@Override
			public MultiEntry<K, V> get() {
				return theEntry;
			}
		}

		private class MutableEntryElement extends EntryElement implements MutableCollectionElement<MultiEntry<K, V>> {
			private final Runnable thePreRemove;

			MutableEntryElement(MultiEntryImpl entry, Runnable preRemove) {
				super(entry);
				thePreRemove = preRemove;
			}

			@Override
			public BetterCollection<MultiEntry<K, V>> getCollection() {
				return EntrySet.this;
			}

			@Override
			public String isEnabled() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public String isAcceptable(MultiEntry<K, V> value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void set(MultiEntry<K, V> value) throws UnsupportedOperationException, IllegalArgumentException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String canRemove() {
				return null;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				if (thePreRemove != null)
					thePreRemove.run();
				new ValueList(theEntry.theKey, theEntry).clear();
			}
		}
	}

	class KeySet implements BetterSortedSet<K> {
		@Override
		public Comparator<? super K> comparator() {
			return theKeyCompare;
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theEntries.getStamp(structuralOnly);
		}

		@Override
		public boolean isLockSupported() {
			return theEntries.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return BetterTreeMultiMap.this.lock(write, structural, cause);
		}

		@Override
		public boolean belongs(Object o) {
			return true;
		}

		@Override
		public boolean isEmpty() {
			return theEntries.isEmpty();
		}

		@Override
		public int size() {
			return theEntries.size();
		}

		@Override
		public CollectionElement<K> getTerminalElement(boolean first) {
			MultiEntryImpl entry = CollectionElement.get(theEntries.getTerminalElement(first));
			return entry == null ? null : new KeyElement(entry);
		}

		@Override
		public CollectionElement<K> getAdjacentElement(ElementId elementId, boolean next) {
			MultiEntryImpl entry = CollectionElement.get(theEntries.getAdjacentElement(elementId, next));
			return entry == null ? null : new KeyElement(entry);
		}

		@Override
		public CollectionElement<K> getElement(ElementId id) {
			return new KeyElement(theEntries.getElement(id).get());
		}

		@Override
		public MutableCollectionElement<K> mutableElement(ElementId id) {
			return new MutableKeyElement(theEntries.getElement(id).get(), null);
		}

		@Override
		public CollectionElement<K> getElement(int index) {
			return new KeyElement(theEntries.getElement(index).get());
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return theEntries.getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return theEntries.getElementsAfter(id);
		}

		@Override
		public String canAdd(K value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public CollectionElement<K> addElement(K value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public CollectionElement<K> search(Comparable<? super K> search, SortedSearchFilter filter) {
			MultiEntryImpl entry = CollectionElement.get(theEntries.search(e -> search.compareTo(e.theKey), filter));
			return entry == null ? null : new KeyElement(entry);
		}

		@Override
		public int indexFor(Comparable<? super K> search) {
			return theEntries.indexFor(entry -> search.compareTo(entry.theKey));
		}

		@Override
		public void clear() {
			try (Transaction t = BetterTreeMultiMap.this.lock(true, null)) {
				theEntries.clear();
				theValues.clear();
			}
		}

		private class KeyElement implements CollectionElement<K> {
			final MultiEntryImpl theEntry;

			KeyElement(MultiEntryImpl entry) {
				theEntry = entry;
			}

			@Override
			public ElementId getElementId() {
				return theEntry.getElementId();
			}

			@Override
			public K get() {
				return theEntry.theKey;
			}
		}

		private class MutableKeyElement extends KeyElement implements MutableCollectionElement<K> {
			private final Runnable thePreRemove;

			MutableKeyElement(BetterTreeMultiMap<K, V>.MultiEntryImpl entry, Runnable preRemove) {
				super(entry);
				thePreRemove = preRemove;
			}

			@Override
			public BetterCollection<K> getCollection() {
				return KeySet.this;
			}

			@Override
			public String isEnabled() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public String isAcceptable(K value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void set(K value) throws UnsupportedOperationException, IllegalArgumentException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String canRemove() {
				return null;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				if (thePreRemove != null)
					thePreRemove.run();
				new ValueList(theEntry.theKey, theEntry).clear();
			}
		}
	}

	class ValueList implements BetterList<V> {
		final K theKey;
		MultiEntryImpl theEntry;

		ValueList(K key, MultiEntryImpl entry) {
			theKey = key;
			theEntry = entry;
		}

		@Override
		public boolean isContentControlled() {
			return theValueCompare != null;
		}

		@Override
		public boolean isLockSupported() {
			return theValues.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theValues.lock(write, structural, cause);
		}

		protected boolean acquireEntry() {
			if (theEntry == null || !theEntry.getElementId().isPresent()) {
				theEntry = (MultiEntryImpl) BetterTreeMultiMap.this.getEntry(theKey);
				return theEntry == null;
			} else
				return true;
		}

		@Override
		public int size() {
			try (Transaction t = lock(false, true, null)) {
				if (!acquireEntry())
					return 0;
				int start = theValues.getElementsBefore(theEntry.firstEntry);
				int end = theEntry.next == null ? theValues.size() : theValues.getElementsBefore(theEntry.next.firstEntry);
				return end - start;
			}
		}

		@Override
		public boolean isEmpty() {
			try (Transaction t = lock(false, true, null)) {
				return !acquireEntry();
			}
		}

		@Override
		public boolean belongs(Object o) {
			return true; // No way to know here
		}

		@Override
		public long getStamp(boolean structuralOnly) {
			return theValues.getStamp(structuralOnly); // Can't differentiate between the keys
		}

		@Override
		public CollectionElement<V> getElement(V value, boolean first) {
			// This method handles value searching when values are not unique.
			// When values are unique, ValueSortedSet delegates to BetterSortedSet.search
			try (Transaction t = theValues.lock(false, null)) {
				if (!acquireEntry())
					return null;
				if (theValueCompare != null)
					return search(value, first);
				CollectionElement<KVEntry> start;
				if (first)
					start = theValues.getElement(theEntry.firstEntry);
				else if (theEntry.next != null)
					start = theValues.getAdjacentElement(theEntry.next.firstEntry, false);
				else
					start = theValues.getTerminalElement(false);
				int[] valueCompare;
				if (theValueCompare != null)
					valueCompare = new int[] { theValueCompare.compare(start.get().theValue, value) };
				else
					valueCompare = null;
				do {
					if (theValueCompare != null && valueCompare[0] == 0)
						return start.get();
					else if (theValueCompare == null && Objects.equals(start.get().theValue, value))
						return start.get();


					start = theValues.getAdjacentElement(start.getElementId(), first);
				} while (keepSearching(start, value, first, valueCompare));
				return null; // Not found
			}
		}

		private boolean keepSearching(CollectionElement<KVEntry> entry, V value, boolean first, int[] valueCompare) {
			if (entry == null || entry.get().theEntry != theEntry)
				return false; // Passed the key's domain
			if (theValueCompare != null) {
				valueCompare[0] = theValueCompare.compare(entry.get().theValue, value);
				if (valueCompare[0] == 0)
					return true;
				else if (first && valueCompare[0] > 0)
					return false;
				else if (!first && valueCompare[0] < 0)
					return false;
			}
			return true;
		}

		protected CollectionElement<V> search(V value, boolean first) {
			BinaryTreeNode<KVEntry> leftMost = theValues.getElement(theEntry.firstEntry);
			BinaryTreeNode<KVEntry> rightMost;
			if (theEntry.next != null)
				rightMost = theValues.getAdjacentElement(theEntry.next.firstEntry, false);
			else
				rightMost = theValues.getTerminalElement(false);
			BinaryTreeNode<KVEntry> found = BinaryTreeNode.findWithin(leftMost, rightMost, //
				node -> theEntryCompare.compare(theEntry, node.get().theEntry), //
				first, true);
			return CollectionElement.get(found);
		}

		@Override
		public CollectionElement<V> getElement(ElementId id) {
			try (Transaction t = theValues.lock(false, true, null)) {
				if (!acquireEntry())
					throw new IllegalArgumentException("Element does not exist in this collection");
				CollectionElement<KVEntry> valueEl = theValues.getElement(id);
				if (theEntry != valueEl.get().theEntry)
					throw new IllegalArgumentException(
						"The given element ID is not for this key (" + valueEl.get().theEntry.theKey + " vs. " + theEntry.theKey + ")");
				return valueEl.get();
			}
		}

		@Override
		public CollectionElement<V> getTerminalElement(boolean first) {
			try (Transaction t = theValues.lock(false, true, null)) {
				if (!acquireEntry())
					return null;
				if (first)
					return theValues.getElement(theEntry.firstEntry).get();
				else if (theEntry.next != null)
					return theValues.getAdjacentElement(theEntry.next.firstEntry, false).get();
				else
					return theValues.getTerminalElement(false).get();
			}
		}

		@Override
		public CollectionElement<V> getAdjacentElement(ElementId elementId, boolean next) {
			try (Transaction t = theValues.lock(false, true, null)) {
				if (!acquireEntry())
					throw new IllegalArgumentException("Element does not exist in this collection");
				CollectionElement<KVEntry> valueEl = theValues.getElement(elementId);
				if (theEntry != valueEl.get().theEntry)
					throw new IllegalArgumentException(
						"The given element ID is not for this key (" + valueEl.get().theEntry.theKey + " vs. " + theEntry.theKey + ")");
				CollectionElement<KVEntry> adj = theValues.getAdjacentElement(valueEl.getElementId(), next);
				if (adj == null || theEntry != adj.get().theEntry)
					return null;
				return adj.get();
			}
		}

		@Override
		public CollectionElement<V> getElement(int index) {
			if (index < 0)
				throw new IndexOutOfBoundsException("" + index);
			try (Transaction t = theValues.lock(false, true, null)) {
				if (!acquireEntry())
					throw new IndexOutOfBoundsException(index + " of 0");
				int start = theValues.getElementsBefore(theEntry.firstEntry);
				return theValues.getElement(index - start).get();
			}
		}

		@Override
		public int getElementsBefore(ElementId id) {
			try (Transaction t = theValues.lock(false, true, null)) {
				if (!acquireEntry())
					throw new IllegalArgumentException("Element does not exist in this collection");
				CollectionElement<KVEntry> valueEl = theValues.getElement(id);
				if (theEntry != valueEl.get().theEntry)
					throw new IllegalArgumentException(
						"The given element ID is not for this key (" + valueEl.get().theEntry.theKey + " vs. " + theEntry.theKey + ")");
				return theValues.getElementsBefore(id) - theValues.getElementsBefore(theEntry.firstEntry);
			}
		}

		@Override
		public int getElementsAfter(ElementId id) {
			try (Transaction t = theValues.lock(false, true, null)) {
				if (!acquireEntry())
					throw new IllegalArgumentException("Element does not exist in this collection");
				CollectionElement<KVEntry> valueEl = theValues.getElement(id);
				if (theEntry != valueEl.get().theEntry)
					throw new IllegalArgumentException(
						"The given element ID is not for this key (" + valueEl.get().theEntry.theKey + " vs. " + theEntry.theKey + ")");
				int size = theEntry.next == null ? theValues.size() : theValues.getElementsBefore(theEntry.next.firstEntry);
				return size - theValues.getElementsBefore(id) - 1;
			}
		}

		@Override
		public MutableCollectionElement<V> mutableElement(ElementId id) {
			try (Transaction t = BetterTreeMultiMap.this.lock(false, true, null)) {
				if (!acquireEntry())
					throw new IllegalArgumentException("Element does not exist in this collection");
				CollectionElement<KVEntry> valueEl = theValues.getElement(id);
				if (theEntry != valueEl.get().theEntry)
					throw new IllegalArgumentException(
						"The given element ID is not for this key (" + valueEl.get().theEntry.theKey + " vs. " + theEntry.theKey + ")");
				return new MutableValueElement(valueEl.get(), null);
			}
		}

		@Override
		public String canAdd(V value, ElementId after, ElementId before) {
			if (!belongs(value))
				return StdMsg.ILLEGAL_ELEMENT;
			try (Transaction t = BetterTreeMultiMap.this.lock(false, true, null)) {
				if (!acquireEntry()) {
					if (after != null || before != null)
						throw new IllegalArgumentException("Element does not exist in this collection");
					return null;
				}
				CollectionElement<KVEntry> valueAfter = null;
				if (after != null) {
					valueAfter = theValues.getElement(after);
					if (theEntry != valueAfter.get().theEntry)
						throw new IllegalArgumentException("The given element ID is not for this key (" + valueAfter.get().theEntry.theKey
							+ " vs. " + theEntry.theKey + ")");
				}
				CollectionElement<KVEntry> valueBefore = null;
				if (before != null) {
					valueBefore = theValues.getElement(before);
					if (theEntry != valueBefore.get().theEntry)
						throw new IllegalArgumentException("The given element ID is not for this key (" + valueBefore.get().theEntry.theKey
							+ " vs. " + theEntry.theKey + ")");
				}
				if (theValueCompare != null) {
					if (valueAfter != null) {
						int comp = theValueCompare.compare(valueAfter.get().theValue, value);
						if (comp > 0)
							return StdMsg.ILLEGAL_ELEMENT_POSITION;
						else if (isUniqueValued && comp == 0)
							return StdMsg.ELEMENT_EXISTS;
					}
					if (valueBefore != null) {
						int comp = theValueCompare.compare(valueBefore.get().theValue, value);
						if (comp < 0)
							return StdMsg.ILLEGAL_ELEMENT_POSITION;
						else if (isUniqueValued && comp == 0)
							return StdMsg.ELEMENT_EXISTS;
					}
				}
				return null;
			}
		}

		@Override
		public CollectionElement<V> addElement(V value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = BetterTreeMultiMap.this.lock(true, null)) {
				String msg = canAdd(value, after, before);
				if (msg != null)
					throw new IllegalArgumentException(msg);
				boolean newEntry = theEntry == null;
				if (newEntry) {
					// First element for the key
					// So we know that after and before are both null
					theEntry = new MultiEntryImpl(theKey);
					CollectionElement<MultiEntryImpl> entryEl = theEntries.search(//
						entry -> theKeyCompare.compare(theKey, entry.getKey()), SortedSearchFilter.Greater);
					if (entryEl != null) // Add it before the first value of the next greater entry
						before = entryEl.get().firstEntry;
					else // There is no greater entry; add it at the end
						after = CollectionElement.getElementId(theValues.getTerminalElement(false));
				} else if (theValueCompare != null) {
					int todo = todo;// TODO Constrain the addition by the value compare
				}
				KVEntry newValueEntry = new KVEntry(theEntry, value);
				ElementId element = theValues.addElement(newValueEntry, after, before, first).getElementId();
				newValueEntry.theElement = element;
				if (theEntry.firstEntry == null || element.compareTo(theEntry.firstEntry) < 0)
					theEntry.firstEntry = element;
				if (newEntry) {
					// Link up the previous/next links for all affected entries
					if (after != null) {
						theEntry.previous = theValues.getElement(after).get().theEntry;
						theEntry.next = theEntry.previous.next;
					} else if (before != null) {
						theEntry.next = theValues.getElement(before).get().theEntry;
						theEntry.previous = theEntry.next.previous;
					}
					if (theEntry.previous != null)
						theEntry.previous.next = theEntry;
					if (theEntry.next != null)
						theEntry.next.previous = theEntry;
					theEntry.theEntryId = theEntries.addElement(theEntry, false).getElementId();
				}
				return newValueEntry;
			}
		}

		@Override
		public void clear() {
			try (Transaction t = BetterTreeMultiMap.this.lock(true, null)) {
				if (!acquireEntry())
					return;
				CollectionElement<KVEntry> el = theValues.getElement(theEntry.firstEntry);
				while (el != null && el.get().theEntry == theEntry) {
					ElementId id = el.getElementId();
					el = theValues.getAdjacentElement(id, true);
					theValues.mutableElement(id).remove();
				}
				if (theEntry.getElementId().isPresent()) // May have been removed by spliterator
					theEntries.mutableElement(theEntry.getElementId()).remove();
			}
		}

		private class MutableValueElement implements MutableCollectionElement<V> {
			private final KVEntry theValueEntry;
			private final Runnable thePreRemove;

			MutableValueElement(KVEntry valueEntry, Runnable preRemove) {
				theValueEntry = valueEntry;
				thePreRemove = preRemove;
			}

			@Override
			public ElementId getElementId() {
				return theValueEntry.theElement;
			}

			@Override
			public V get() {
				return theValueEntry.theValue;
			}

			@Override
			public BetterCollection<V> getCollection() {
				return ValueList.this;
			}

			@Override
			public String isEnabled() {
				return null;
			}

			@Override
			public String isAcceptable(V value) {
				if (!ValueList.this.belongs(value))
					return StdMsg.ILLEGAL_ELEMENT;
				if (theValueCompare != null) {
					try (Transaction t = theValues.lock(false, false, null)) {
						CollectionElement<KVEntry> adj = theValues.getAdjacentElement(theValueEntry.theElement, false);
						if (adj != null && adj.get().theEntry == theEntry) {
							int comp = theValueCompare.compare(adj.get().theValue, value);
							if (comp > 0)
								return StdMsg.ILLEGAL_ELEMENT_POSITION;
							else if (isUniqueValued && comp == 0)
								return StdMsg.ELEMENT_EXISTS;
						}
						adj = theValues.getAdjacentElement(theValueEntry.theElement, true);
						if (adj != null && adj.get().theEntry == theEntry) {
							int comp = theValueCompare.compare(adj.get().theValue, value);
							if (comp < 0)
								return StdMsg.ILLEGAL_ELEMENT_POSITION;
							else if (isUniqueValued && comp == 0)
								return StdMsg.ELEMENT_EXISTS;
						}
					}
				}
				return null;
			}

			@Override
			public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
				try (Transaction t = theValues.lock(true, false, null)) {
					String msg = isAcceptable(value);
					if (msg != null)
						throw new IllegalArgumentException(msg);
					theValueEntry.theValue = value;
				}
			}

			@Override
			public String canRemove() {
				return null;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				try (Transaction t = lock(true, null)) {
					CollectionElement<KVEntry> left = theValues.getAdjacentElement(theValueEntry.theElement, false);
					CollectionElement<KVEntry> right = theValues.getAdjacentElement(theValueEntry.theElement, true);
					if (thePreRemove != null)
						thePreRemove.run();
					if (theValueEntry.theElement.isPresent())
						theValues.mutableElement(theValueEntry.theElement).remove();
					if (left == null || left.get().theEntry != theEntry) {
						// This element was the first value for the entry
						if (right == null || right.get().theEntry != theEntry) {
							// No more values for the entry, remove it
							theEntries.mutableElement(theEntry.getElementId()).remove();
							if (theEntry.next != null)
								theEntry.next.previous = theEntry.previous;
							if (theEntry.previous != null)
								theEntry.previous.next = theEntry.next;
						} else {
							theEntry.firstEntry = right.getElementId();
						}
					}
				}
			}
		}
	}

	class ValueSortedSet extends ValueList implements BetterSortedSet<V> {
		ValueSortedSet(K key, MultiEntryImpl entry) {
			super(key, entry);
		}

		@Override
		public CollectionElement<V> getElement(V value, boolean first) {
			return BetterSortedSet.super.getElement(value, first);
		}

		@Override
		public Comparator<? super V> comparator() {
			return theValueCompare;
		}

		@Override
		public CollectionElement<V> search(Comparable<? super V> search, SortedSearchFilter filter) {
			try (Transaction t = theValues.lock(false, null)) {
				if (!acquireEntry())
					return null;
				CollectionElement<KVEntry> el = theValues.getRoot().findClosest(node -> {
					int comp = theKeyCompare.compare(theEntry.theKey, node.get().theEntry.theKey);
					if (comp == 0)
						comp = search.compareTo(node.get().theValue);
					return comp;
				}, filter.less.withDefault(true), filter.strict);
				if (el != null && el.get().theEntry != theEntry) {
					if (filter.strict)
						return null;
					int comp = theKeyCompare.compare(el.get().theEntry.theKey, theEntry.theKey);
					el = theValues.getAdjacentElement(el.getElementId(), comp < 0);
				}
				return el == null ? null : el.get();
			}
		}

		@Override
		public int indexFor(Comparable<? super V> search) {
			try (Transaction t = theValues.lock(false, null)) {
				if (!acquireEntry())
					return -1;
				int idx = theValues.getRoot().indexFor(node -> {
					int comp = theKeyCompare.compare(theEntry.theKey, node.get().theEntry.theKey);
					if (comp == 0)
						comp = search.compareTo(node.get().theValue);
					return comp;
				});
				int start = theValues.getElementsBefore(theEntry.firstEntry);
				if (idx < 0) {
					idx = -idx - 1;
					idx -= start;
					return -idx - 1;
				} else
					return idx - start;
			}
		}
	}
}
