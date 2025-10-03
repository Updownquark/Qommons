package org.qommons.collect;

import java.util.*;

import org.qommons.ArrayUtils;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * It's infuriating to me that java's {@link EnumMap} is not a {@link NavigableMap} or even a {@link SortedMap}. So I had to write my own.
 * 
 * @param <K> The enum key type of the map
 * @param <V> The type of values stored in the map
 */
public class SortedEnumMap<K extends Enum<K>, V> extends AbstractMap<K, V> implements BetterSortedMap<K, V> {
	private static final Object NULL = new Object() {
		@Override
		public int hashCode() {
			return -1;
		}

		@Override
		public String toString() {
			return "null";
		}
	};

	private static final Comparator<Enum<?>> COMPARE = LambdaUtils.<Enum<?>> printableComparator((e1, e2) -> {
		return Integer.compare(e1.ordinal(), e2.ordinal());
	}, () -> "enumCompare", null);
	private static final Comparator<Map.Entry<? extends Enum<?>, ?>> ENTRY_COMPARE = LambdaUtils.<Map.Entry<? extends Enum<?>, ?>> printableComparator(
		(e1, e2) -> {
			return Integer.compare(e1.getKey().ordinal(), e2.getKey().ordinal());
		}, () -> "enumEntryCompare", null);

	private final Class<K> theKeyType;
	private final CollectionLockingStrategy theLocker;
	private final K[] theKeys;
	private final EnumEntry[] theEntries;

	private final KeySet theKeySet;
	private final EntrySet theEntrySet;
	private final Values theValues;

	private Object theIdentity;

	/**
	 * @param keyType The enum type to create the map for
	 * @param locker The locking strategy for the map
	 */
	public SortedEnumMap(Class<K> keyType, CollectionLockingStrategy locker) {
		theKeyType = keyType;
		theLocker = locker;
		theKeys = keyType.getEnumConstants();
		theEntries = new SortedEnumMap.EnumEntry[theKeys.length];
		theKeySet = new KeySet();
		theEntrySet = new EntrySet();
		theValues = new Values();
	}

	@Override
	public Object getIdentity() {
		if (theIdentity == null)
			theIdentity = Identifiable.baseId(theKeyType.getName() + " Map", this);
		return theIdentity;
	}

	@Override
	public SortedEnumMap<K, V> alias(String alias) {
		theIdentity = Identifiable.AliasedIdentity.alias(getIdentity(), alias);
		return this;
	}

	@Override
	public Set<String> getAliases() {
		return Identifiable.AliasedIdentity.getAliases(theIdentity);
	}

	@Override
	public boolean isLockSupported() {
		return theLocker.isLockSupported();
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return theLocker.getThreadConstraint();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theLocker.lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theLocker.tryLock(write, cause);
	}

	@Override
	public <T> T doOptimistically(T init, OptimisticOperation<T> operation) {
		return theLocker.doOptimistically(init, operation);
	}

	@Override
	public int doOptimistically(int init, OptimisticIntOperation operation) {
		return theLocker.doOptimistically(init, operation);
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
	public Collection<Cause> getCurrentCauses() {
		return theLocker.getCurrentCauses();
	}

	@Override
	public boolean isConsistent(ElementId entry) {
		return true;
	}

	@Override
	public boolean checkConsistency() {
		return false;
	}

	@Override
	public <X> boolean repair(ElementId entry, MapRepairListener<K, V, X> listener) {
		return false;
	}

	@Override
	public <X> boolean repair(MapRepairListener<K, V, X> listener) {
		return false;
	}

	@Override
	public int size() {
		int size = 0;
		try (Transaction t = theLocker.lock(false, null)) {
			for (EnumEntry entry : theEntries) {
				if (entry != null)
					size++;
			}
		}
		return size;
	}

	@Override
	public boolean containsValue(Object value) {
		try (Transaction t = theLocker.lock(false, null)) {
			for (EnumEntry entry : theEntries) {
				if (entry != null && Objects.equals(entry, value))
					return true;
			}
		}
		return false;
	}

	@Override
	public boolean containsKey(Object key) {
		if (!theKeyType.isInstance(key))
			return false;
		int index = ((K) key).ordinal();
		return theEntries[index] != null;
	}

	@Override
	public V get(Object key) {
		if (!theKeyType.isInstance(key))
			return null;
		int index = ((K) key).ordinal();
		EnumEntry entry = theEntries[index];
		return entry == null ? null : entry.getValue();
	}

	@Override
	public V put(K key, V value) {
		int index = key.ordinal();
		try (Transaction t = theLocker.lock(true, null)) {
			EnumEntry entry = theEntries[index];
			theLocker.modified();
			if (entry == null) {
				theEntries[index] = new EnumEntry(key, value);
				return null;
			} else
				return entry.set(value);
		}
	}

	@Override
	public V remove(Object key) {
		if (!theKeyType.isInstance(key))
			return null;
		try (Transaction t = theLocker.lock(true, null)) {
			int index = ((K) key).ordinal();
			EnumEntry entry = theEntries[index];
			if (entry == null)
				return null;
			theEntries[index] = null;
			theLocker.modified();
			return entry.get();
		}
	}

	@Override
	public void clear() {
		try (Transaction t = theLocker.lock(true, null)) {
			if (isEmpty())
				return;
			Arrays.fill(theEntries, null);
			theLocker.modified();
		}
	}

	@Override
	public BetterSortedSet<K> keySet() {
		return theKeySet;
	}

	@Override
	public BetterSortedSet<Map.Entry<K, V>> entrySet() {
		return theEntrySet;
	}

	@Override
	public BetterList<V> values() {
		return theValues;
	}

	@Override
	public OrderedMapEntry<K, V> getEntry(K key) {
		return theEntries[key.ordinal()];
	}

	KeyId keyId(ElementId entryId) {
		if (!(entryId instanceof SortedEnumMap.KeyId))
			throw new IllegalArgumentException("ID is not from this map");
		KeyId id = (KeyId) entryId;
		if (this != id.getMap())
			throw new IllegalArgumentException("IDs are from different maps");
		else if (!id.isPresent())
			throw new IllegalArgumentException("Entry has been removed");
		return id;
	}

	@Override
	public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
		return getEntry(keyId(entryId).theKey);
	}

	@Override
	public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
		return new MutableEntry((EnumEntry) getEntryById(entryId));
	}

	@Override
	public String canPut(K key, V value) {
		return null;
	}

	@Override
	public OrderedMapEntry<K, V> searchEntries(Comparable<? super Entry<K, V>> search, SortedSearchFilter filter) {
		return (OrderedMapEntry<K, V>) entrySet().search(search, filter);
	}

	@Override
	public OrderedMapEntry<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
		if (after != null && key.compareTo(keyId(after).theKey) < 0)
			throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
		if (before != null && key.compareTo(keyId(before).theKey) > 0)
			throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
		try (Transaction t = lock(true, null)) {
			int index = key.ordinal();
			EnumEntry entry = theEntries[index];
			if (entry == null)
				theEntries[index] = entry = new EnumEntry(key, value);
			else
				entry.set(value);
			theLocker.modified();
			return entry;
		}
	}

	class KeyId implements ElementId {
		final K theKey;

		KeyId(K key) {
			theKey = key;
		}

		SortedEnumMap<K, V> getMap() {
			return SortedEnumMap.this;
		}

		@Override
		public int compareTo(ElementId o) {
			return theKey.compareTo(keyId(o).theKey);
		}

		@Override
		public boolean isPresent() {
			EnumEntry entry = theEntries[theKey.ordinal()];
			return entry != null && entry.getElementId() == this;
		}

		@Override
		public int hashCode() {
			return theKey.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof SortedEnumMap.EnumEntry))
				return false;
			EnumEntry entry = (EnumEntry) obj;
			if (getMap() != entry.getMap())
				return false;
			return theKey.equals(entry.getKey());
		}

		@Override
		public String toString() {
			return theKey.toString();
		}

	}

	class EnumEntry implements OrderedMapEntry<K, V> {
		private final KeyId theId;
		private Object theValue;

		EnumEntry(K key, V value) {
			theId = new KeyId(key);
			set(value);
		}

		SortedEnumMap<K, V> getMap() {
			return SortedEnumMap.this;
		}

		@Override
		public KeyId getElementId() {
			return theId;
		}

		@Override
		public K getKey() {
			return theId.theKey;
		}

		@Override
		public V get() {
			return theValue == NULL ? null : (V) theValue;
		}

		V set(V newValue) {
			V old = get();
			theValue = newValue == null ? NULL : newValue;
			return old;
		}

		@Override
		public int getElementsBefore() {
			K key = theId.theKey;
			int index = 0;
			try (Transaction t = lock(false, null)) {
				for (int i = 0; i < theKeys.length; i++) {
					if (theKeys[i] == key)
						return index;
					else if (theEntries[i] != null)
						index++;
				}
			}
			throw new IllegalStateException();
		}

		@Override
		public int getElementsAfter() {
			K key = theId.theKey;
			int index = 0;
			try (Transaction t = lock(false, null)) {
				for (int i = theKeys.length - 1; i >= 0; i--) {
					if (theKeys[i] == key)
						return index;
					else if (theEntries[i] != null)
						index++;
				}
			}
			throw new IllegalStateException();
		}

		@Override
		public EnumEntry getAdjacent(boolean next) {
			int index = theId.theKey.ordinal();
			if (next) {
				for (index++; index < theEntries.length; index++) {
					EnumEntry entry = theEntries[index];
					if (entry != null)
						return entry;
				}
			} else {
				for (index--; index >= 0; index--) {
					EnumEntry entry = theEntries[index];
					if (entry != null)
						return entry;
				}
			}
			return null;
		}

		@Override
		public int hashCode() {
			return theId.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof MapEntryHandle && theId.equals(((MapEntryHandle<?, ?>) obj).getElementId());
		}

		@Override
		public String toString() {
			return theId.theKey + "=" + theValue;
		}
	}

	class MutableEntry implements MutableOrderedMapEntry<K, V> {
		private final EnumEntry theEntry;

		MutableEntry(EnumEntry entry) {
			theEntry = entry;
		}

		@Override
		public K getKey() {
			return theEntry.getKey();
		}

		@Override
		public int getElementsBefore() {
			return theEntry.getElementsBefore();
		}

		@Override
		public int getElementsAfter() {
			return theEntry.getElementsAfter();
		}

		@Override
		public MutableOrderedMapEntry<K, V> getAdjacent(boolean next) {
			EnumEntry adj = theEntry.getAdjacent(next);
			return adj == null ? null : new MutableEntry(adj);
		}

		@Override
		public String isEnabled() {
			if (!theEntry.getElementId().isPresent())
				return StdMsg.ELEMENT_REMOVED;
			else
				return null;
		}

		@Override
		public String isAcceptable(V value) {
			if (!theEntry.getElementId().isPresent())
				return StdMsg.ELEMENT_REMOVED;
			else
				return null;
		}

		@Override
		public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
			try (Transaction t = lock(true, null)) {
				if (!theEntry.getElementId().isPresent())
					throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
				theEntry.set(value);
				theLocker.modified();
			}
		}

		@Override
		public String canRemove() {
			if (!theEntry.getElementId().isPresent())
				return StdMsg.ELEMENT_REMOVED;
			else
				return null;
		}

		@Override
		public void remove() throws UnsupportedOperationException {
			try (Transaction t = lock(true, null)) {
				if (!theEntry.getElementId().isPresent())
					throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
				theEntries[theEntry.getElementId().theKey.ordinal()] = null;
				theLocker.modified();
			}
		}

		@Override
		public ElementId getElementId() {
			return theEntry.getElementId();
		}

		@Override
		public V get() {
			return theEntry.get();
		}

		@Override
		public int hashCode() {
			return theEntry.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return theEntry.equals(obj);
		}

		@Override
		public String toString() {
			return theEntry.toString();
		}
	}

	class KeySet extends AbstractIdentifiable implements BetterSortedSet<K> {
		@Override
		public long getStamp() {
			return SortedEnumMap.this.getStamp();
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return SortedEnumMap.this.getCurrentCauses();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return SortedEnumMap.this.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return SortedEnumMap.this.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return SortedEnumMap.this.getCoreId();
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return SortedEnumMap.this.getThreadConstraint();
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(SortedEnumMap.this.getIdentity(), "keySet");
		}

		@Override
		public Comparator<? super K> comparator() {
			return COMPARE;
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return true;
		}

		@Override
		public boolean checkConsistency() {
			return false;
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<K, X> listener) {
			return false;
		}

		@Override
		public <X> boolean repair(RepairListener<K, X> listener) {
			return false;
		}

		@Override
		public ListElement<K> search(Comparable<? super K> search, SortedSearchFilter filter) {
			K[] keys = toArray();
			if (keys.length == 0)
				return null;
			int index = ArrayUtils.binarySearch(keys, search);
			if (index >= 0)
				return getElement(keys[index], true);
			index = -index - 1;
			switch (filter) {
			case OnlyMatch:
				return null;
			case Less:
				return index > 0 ? getElement(keys[index - 1], true) : null;
			case PreferLess:
				return getElement(keys[index > 0 ? index - 1 : index], true);
			case Greater:
				return index < keys.length ? getElement(keys[index], true) : null;
			case PreferGreater:
				return getElement(keys[index < keys.length ? index : index - 1], true);
			}
			throw new IllegalStateException();
		}

		@Override
		public int indexFor(Comparable<? super K> search) {
			K[] keys = toArray();
			return ArrayUtils.binarySearch(keys, search);
		}

		@Override
		public ListElement<K> getElement(int index) throws IndexOutOfBoundsException {
			try (Transaction t = lock(false, null)) {
				K[] keys = toArray();
				return getElement(keys[index], true);
			}
		}

		@Override
		public ListElement<K> getElement(ElementId id) {
			return new KeyElement(keyId(id));
		}

		@Override
		public ListElement<K> getTerminalElement(boolean first) {
			if (first) {
				for (EnumEntry entry : theEntries) {
					if (entry != null)
						return new KeyElement(entry.getElementId());
				}
			} else {
				for (int i = theEntries.length - 1; i >= 0; i--) {
					EnumEntry entry = theEntries[i];
					if (entry != null)
						return new KeyElement(entry.getElementId());
				}
			}
			return null;
		}

		@Override
		public MutableListElement<K> mutableElement(ElementId id) {
			return new MutableKeyElement(keyId(id));
		}

		@Override
		public BetterList<CollectionElement<K>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (!(sourceEl instanceof SortedEnumMap.KeyId))
				return BetterList.empty();
			KeyId id = (KeyId) sourceEl;
			if (SortedEnumMap.this != id.getMap())
				return BetterList.empty();
			return BetterList.of(new KeyElement(id));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (!(localElement instanceof SortedEnumMap.KeyId))
				return BetterList.empty();
			KeyId id = (KeyId) localElement;
			if (SortedEnumMap.this != id.getMap())
				return BetterList.empty();
			return BetterList.of(localElement);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			if (!(equivalentEl instanceof SortedEnumMap.KeyId))
				return null;
			KeyId id = (KeyId) equivalentEl;
			if (SortedEnumMap.this != id.getMap())
				return null;
			return equivalentEl;
		}

		@Override
		public String canAdd(K value, ElementId after, ElementId before) {
			if (after != null && value.compareTo(keyId(after).theKey) < 0)
				return StdMsg.ILLEGAL_ELEMENT_POSITION;
			else if (before != null && value.compareTo(keyId(before).theKey) > 0)
				return StdMsg.ILLEGAL_ELEMENT_POSITION;
			else if (value == null)
				return StdMsg.ILLEGAL_ELEMENT;
			else if (theEntries[value.ordinal()] != null)
				return StdMsg.ELEMENT_EXISTS;
			else
				return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ListElement<K> addElement(K value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			if (after != null && value.compareTo(keyId(after).theKey) < 0)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
			else if (before != null && value.compareTo(keyId(before).theKey) > 0)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT_POSITION);
			else if (value == null)
				throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			else if (theEntries[value.ordinal()] != null)
				throw new IllegalArgumentException(StdMsg.ELEMENT_EXISTS);
			else
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public Iterator<K> iterator() {
			return new Iterator<K>() {
				private int theIndex;

				@Override
				public boolean hasNext() {
					while (theIndex < theEntries.length && theEntries[theIndex] == null)
						theIndex++;
					return theIndex < theEntries.length;
				}

				@Override
				public K next() {
					if (!hasNext())
						throw new NoSuchElementException();
					K key = theKeys[theIndex];
					theIndex++;
					return key;
				}

				@Override
				public void remove() {
					try (Transaction t = lock(true, null)) {
						if (theIndex == 0 || theEntries[theIndex - 1] == null)
							throw new IllegalStateException("remove() must be called after next()");
						theEntries[theIndex - 1] = null;
						theLocker.modified();
					}
				}
			};
		}

		@Override
		public int size() {
			return SortedEnumMap.this.size();
		}

		@Override
		public K[] toArray() {
			try (Transaction t = lock(false, null)) {
				List<K> list = new ArrayList<>(theKeys.length);
				for (int i = 0; i < theKeys.length; i++) {
					if (theEntries[i] != null)
						list.add(theKeys[i]);
				}
				return list.toArray(Arrays.copyOf(theKeys, list.size()));
			}
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return BetterSortedSet.super.toArray(a);
		}

		@Override
		public void clear() {
			SortedEnumMap.this.clear();
		}

		@Override
		public boolean isEmpty() {
			return SortedEnumMap.this.isEmpty();
		}

		class KeyElement implements ListElement<K> {
			private final KeyId theId;

			KeyElement(SortedEnumMap<K, V>.KeyId id) {
				theId = id;
			}

			@Override
			public KeyId getElementId() {
				return theId;
			}

			@Override
			public K get() {
				return theId.theKey;
			}

			@Override
			public ListElement<K> getAdjacent(boolean next) {
				int index = theId.theKey.ordinal();
				if (next) {
					for (index++; index < theEntries.length; index++) {
						EnumEntry entry = theEntries[index];
						if (entry != null)
							return new KeyElement(entry.getElementId());
					}
				} else {
					for (index--; index >= 0; index--) {
						EnumEntry entry = theEntries[index];
						if (entry != null)
							return new KeyElement(entry.getElementId());
					}
				}
				return null;
			}

			@Override
			public int getElementsBefore() {
				K key = theId.theKey;
				int index = 0;
				try (Transaction t = lock(false, null)) {
					for (int i = 0; i < theKeys.length; i++) {
						if (theKeys[i] == key)
							return index;
						else if (theEntries[i] != null)
							index++;
					}
				}
				throw new IllegalStateException();
			}

			@Override
			public int getElementsAfter() {
				K key = theId.theKey;
				int index = 0;
				try (Transaction t = lock(false, null)) {
					for (int i = theKeys.length - 1; i >= 0; i--) {
						if (theKeys[i] == key)
							return index;
						else if (theEntries[i] != null)
							index++;
					}
				}
				throw new IllegalStateException();
			}

			@Override
			public int hashCode() {
				return theId.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof SortedEnumMap.KeySet.KeyElement && theId.equals(((CollectionElement<?>) obj).getElementId());
			}

			@Override
			public String toString() {
				return theId.theKey.toString();
			}
		}

		class MutableKeyElement extends KeyElement implements MutableListElement<K> {
			MutableKeyElement(SortedEnumMap<K, V>.KeyId id) {
				super(id);
			}
			@Override
			public MutableListElement<K> getAdjacent(boolean next) {
				int index = getElementId().theKey.ordinal();
				if (next) {
					for (index++; index < theEntries.length; index++) {
						EnumEntry entry = theEntries[index];
						if (entry != null)
							return new MutableKeyElement(entry.getElementId());
					}
				} else {
					for (index--; index >= 0; index--) {
						EnumEntry entry = theEntries[index];
						if (entry != null)
							return new MutableKeyElement(entry.getElementId());
					}
				}
				return null;
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
				EnumEntry entry = theEntries[getElementId().theKey.ordinal()];
				if (entry == null || entry.getElementId() != getElementId())
					return StdMsg.ELEMENT_REMOVED;
				else
					return null;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				try (Transaction t = lock(true, null)) {
					int index = getElementId().theKey.ordinal();
					if (theEntries[index] == null || theEntries[index].getElementId() != getElementId())
						throw new IllegalArgumentException(StdMsg.ELEMENT_REMOVED);
					theEntries[index] = null;
					theLocker.modified();
				}
			}
		}
	}

	class EntrySet extends AbstractIdentifiable implements BetterSortedSet<Map.Entry<K, V>> {
		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(SortedEnumMap.this.getIdentity(), "entrySet");
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return SortedEnumMap.this.getThreadConstraint();
		}

		@Override
		public long getStamp() {
			return SortedEnumMap.this.getStamp();
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return SortedEnumMap.this.getCurrentCauses();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return SortedEnumMap.this.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return SortedEnumMap.this.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return SortedEnumMap.this.getCoreId();
		}

		@Override
		public boolean isConsistent(ElementId element) {
			return true;
		}

		@Override
		public boolean checkConsistency() {
			return false;
		}

		@Override
		public <X> boolean repair(ElementId element, RepairListener<Map.Entry<K, V>, X> listener) {
			return false;
		}

		@Override
		public <X> boolean repair(RepairListener<Map.Entry<K, V>, X> listener) {
			return false;
		}

		@Override
		public ListElement<Map.Entry<K, V>> getElement(int index) throws IndexOutOfBoundsException {
			try (Transaction t = lock(false, null)) {
				K[] keys = theKeySet.toArray();
				return new EntryElement(theEntries[keys[index].ordinal()]);
			}
		}

		@Override
		public Comparator<? super Map.Entry<K, V>> comparator() {
			return ENTRY_COMPARE;
		}

		@Override
		public ListElement<Map.Entry<K, V>> search(Comparable<? super Map.Entry<K, V>> search, SortedSearchFilter filter) {
			CollectionElement<K> found = theKeySet.search(k -> search.compareTo(getEntry(k)), filter);
			return found == null ? null : getElement(found.getElementId());
		}

		@Override
		public int indexFor(Comparable<? super Map.Entry<K, V>> search) {
			return theKeySet.indexFor(k -> search.compareTo(getEntry(k)));
		}

		@Override
		public ListElement<Map.Entry<K, V>> getElement(ElementId id) {
			return new EntryElement((EnumEntry) getEntryById(id));
		}

		@Override
		public ListElement<Map.Entry<K, V>> getTerminalElement(boolean first) {
			return entryElementFor(getTerminalEntry(first));
		}

		EntryElement entryElementFor(MapEntryHandle<K, V> entry) {
			return entry == null ? null : new EntryElement((EnumEntry) entry);
		}

		@Override
		public MutableListElement<Map.Entry<K, V>> mutableElement(ElementId id) {
			return new MutableEntryElement((EnumEntry) getEntryById(id));
		}

		@Override
		public BetterList<CollectionElement<Map.Entry<K, V>>> getElementsBySource(ElementId sourceEl,
			BetterCollection<?> sourceCollection) {
			if (!(sourceEl instanceof SortedEnumMap.KeyId))
				return BetterList.empty();
			KeyId id = (KeyId) sourceEl;
			if (SortedEnumMap.this != id.getMap())
				return BetterList.empty();
			return BetterList.of(getElement(id));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (!(localElement instanceof SortedEnumMap.KeyId))
				return BetterList.empty();
			KeyId id = (KeyId) localElement;
			if (SortedEnumMap.this != id.getMap())
				return BetterList.empty();
			return BetterList.of(localElement);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			if (!(equivalentEl instanceof SortedEnumMap.KeyId))
				return null;
			KeyId id = (KeyId) equivalentEl;
			if (SortedEnumMap.this != id.getMap())
				return null;
			return equivalentEl;
		}

		@Override
		public String canAdd(Map.Entry<K, V> value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ListElement<Map.Entry<K, V>> addElement(Map.Entry<K, V> value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public int size() {
			return SortedEnumMap.this.size();
		}

		@Override
		public void clear() {
			SortedEnumMap.this.clear();
		}

		@Override
		public boolean isEmpty() {
			return SortedEnumMap.this.isEmpty();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return BetterSortedSet.super.toArray(a);
		}

		class EntryElement implements ListElement<Map.Entry<K, V>> {
			private final EnumEntry theEntry;

			EntryElement(EnumEntry entry) {
				theEntry = entry;
			}

			EnumEntry getEntry() {
				return theEntry;
			}

			@Override
			public KeyId getElementId() {
				return theEntry.getElementId();
			}

			@Override
			public Map.Entry<K, V> get() {
				return theEntry;
			}

			@Override
			public ListElement<Entry<K, V>> getAdjacent(boolean next) {
				EnumEntry adj = theEntry.getAdjacent(next);
				return adj == null ? null : new EntryElement(adj);
			}

			@Override
			public int getElementsBefore() {
				return theEntry.getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return theEntry.getElementsAfter();
			}

			@Override
			public int hashCode() {
				return theEntry.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof SortedEnumMap.EntrySet.EntryElement
					&& theEntry.getElementId().equals(((CollectionElement<?>) obj).getElementId());
			}

			@Override
			public String toString() {
				return theEntry.toString();
			}
		}

		class MutableEntryElement extends EntryElement implements MutableListElement<Map.Entry<K, V>> {
			MutableEntryElement(EnumEntry entry) {
				super(entry);
			}

			@Override
			public MutableListElement<Map.Entry<K, V>> getAdjacent(boolean next) {
				EnumEntry adj = getEntry().getAdjacent(next);
				return adj == null ? null : new MutableEntryElement(adj);
			}

			@Override
			public String isEnabled() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public String isAcceptable(Map.Entry<K, V> value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void set(Map.Entry<K, V> value) throws UnsupportedOperationException, IllegalArgumentException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String canRemove() {
				return mutableEntry(getElementId()).canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				mutableEntry(getElementId()).remove();
			}
		}
	}

	class Values extends AbstractIdentifiable implements BetterList<V> {
		@Override
		public ThreadConstraint getThreadConstraint() {
			return SortedEnumMap.this.getThreadConstraint();
		}

		@Override
		public long getStamp() {
			return SortedEnumMap.this.getStamp();
		}

		@Override
		public Collection<Cause> getCurrentCauses() {
			return SortedEnumMap.this.getCurrentCauses();
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(SortedEnumMap.this.getIdentity(), "values");
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return SortedEnumMap.this.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return SortedEnumMap.this.tryLock(write, cause);
		}

		@Override
		public CoreId getCoreId() {
			return getCoreId();
		}

		@Override
		public OrderedMapEntry<K, V> getElement(V value, boolean first) {
			try (Transaction t = lock(false, null)) {
				if (first) {
					for (int i = 0; i < theEntries.length; i++) {
						if (theEntries[i] != null && Objects.equals(theEntries[i].get(), value))
							return theEntries[i];
					}
				} else {
					for (int i = theEntries.length - 1; i >= 0; i--) {
						if (theEntries[i] != null && Objects.equals(theEntries[i].get(), value))
							return theEntries[i];
					}
				}
			}
			return null;
		}

		@Override
		public OrderedMapEntry<K, V> getElement(ElementId id) {
			return SortedEnumMap.this.getEntryById(id);
		}

		@Override
		public OrderedMapEntry<K, V> getTerminalElement(boolean first) {
			return SortedEnumMap.this.getTerminalEntry(first);
		}

		@Override
		public MutableOrderedMapEntry<K, V> mutableElement(ElementId id) {
			return SortedEnumMap.this.mutableEntry(id);
		}

		@Override
		public BetterList<CollectionElement<V>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (!(sourceEl instanceof SortedEnumMap.KeyId))
				return BetterList.empty();
			KeyId id = (KeyId) sourceEl;
			if (SortedEnumMap.this != id.getMap())
				return BetterList.empty();
			return BetterList.of(getElement(id));
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (!(localElement instanceof SortedEnumMap.KeyId))
				return BetterList.empty();
			KeyId id = (KeyId) localElement;
			if (SortedEnumMap.this != id.getMap())
				return BetterList.empty();
			return BetterList.of(localElement);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			if (!(equivalentEl instanceof SortedEnumMap.KeyId))
				return null;
			KeyId id = (KeyId) equivalentEl;
			if (SortedEnumMap.this != id.getMap())
				return null;
			return equivalentEl;
		}

		@Override
		public String canAdd(V value, ElementId after, ElementId before) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ListElement<V> addElement(V value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			return theKeySet.canMove(valueEl, after, before);
		}

		@Override
		public OrderedMapEntry<K, V> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			theKeySet.move(valueEl, after, before, first, null); // Let the sorted set throw the exception
			return getElement(valueEl);
		}

		@Override
		public OrderedMapEntry<K, V> getElement(int index) throws IndexOutOfBoundsException {
			try (Transaction t = lock(false, null)) {
				int i = 0;
				for (int j = 0; j < theEntries.length; j++) {
					if (theEntries[j] != null) {
						if (i == index)
							return theEntries[j];
						else
							i++;
					}
				}
				throw new ArrayIndexOutOfBoundsException(index + " of " + i);
			}
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public V get(int index) {
			return CollectionElement.get(getElement(index));
		}

		@Override
		public int size() {
			return SortedEnumMap.this.size();
		}

		@Override
		public boolean isEmpty() {
			return SortedEnumMap.this.isEmpty();
		}

		@Override
		public void clear() {
			SortedEnumMap.this.clear();
		}
	}
}
