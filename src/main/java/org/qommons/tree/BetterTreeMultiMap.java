package org.qommons.tree;

import java.util.Comparator;

import org.qommons.collect.AbstractBetterMultiMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedMultiMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.MultiEntryHandle;

public class BetterTreeMultiMap<K, V> extends AbstractBetterMultiMap<K, V> implements BetterSortedMultiMap<K, V> {
	public static <K, V> Builder<K, V> build(Comparator<? super K> keyCompare) {
		return new Builder<>(keyCompare);
	}

	public static class Builder<K, V> extends AbstractBetterMultiMap.Builder<K, V> {
		private final Comparator<? super K> theKeyCompare;

		public Builder(Comparator<? super K> keyCompare) {
			super("BetterTreeMultiMap");
			theKeyCompare = keyCompare;
		}

		@Override
		public Builder<K, V> safe(boolean safe) {
			super.safe(safe);
			return this;
		}

		@Override
		public Builder<K, V> withLocking(CollectionLockingStrategy locking) {
			super.withLocking(locking);
			return this;
		}

		@Override
		public Builder<K, V> withSortedValues(Comparator<? super V> valueCompare, boolean distinctValues) {
			super.withSortedValues(valueCompare, distinctValues);
			return this;
		}

		@Override
		public Builder<K, V> withValues(ValueCollectionSupplier<? super K, ? super V> values) {
			super.withValues(values);
			return this;
		}

		protected Comparator<? super K> getKeyCompare() {
			return theKeyCompare;
		}

		@Override
		public BetterTreeMultiMap<K, V> buildMultiMap() {
			return new BetterTreeMultiMap<>(//
				getLocking(), getKeyCompare(), getValues(), getDescription());
		}
	}

	private final Comparator<? super K> theKeyCompare;

	private BetterTreeMultiMap(CollectionLockingStrategy locking, Comparator<? super K> keyCompare,
		ValueCollectionSupplier<? super K, ? super V> values, String description) {
		super(locking, new BetterTreeMap<>(locking, keyCompare), values, description);
		theKeyCompare = keyCompare;
	}

	@Override
	protected BetterSet<K> createKeySet(BetterSet<K> backing) {
		return new BetterTreeMultiMapKeySet(backing);
	}

	@Override
	public BetterSortedSet<K> keySet() {
		return (BetterSortedSet<K>) super.keySet();
	}

	@Override
	public MultiEntryHandle<K, V> search(Comparable<? super K> search, BetterSortedList.SortedSearchFilter filter) {
		return CollectionElement.get(entrySet().search(entry -> search.compareTo(entry.getKey()), filter));
	}

	class BetterTreeMultiMapKeySet extends BetterMultiMapKeySet implements BetterSortedSet<K> {
		protected BetterTreeMultiMapKeySet(BetterSet<K> backing) {
			super(backing);
		}

		@Override
		protected BetterSortedSet<K> getBacking() {
			return (BetterSortedSet<K>) super.getBacking();
		}

		@Override
		public Comparator<? super K> comparator() {
			return theKeyCompare;
		}

		@Override
		public CollectionElement<K> getElement(int index) {
			return getBacking().getElement(index);
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return getBacking().getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return getBacking().getElementsAfter(id);
		}

		@Override
		public CollectionElement<K> search(Comparable<? super K> search, BetterSortedList.SortedSearchFilter filter) {
			return getBacking().search(search, filter);
		}

		@Override
		public int indexFor(Comparable<? super K> search) {
			return getBacking().indexFor(search);
		}
	}
}
