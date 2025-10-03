package org.qommons.tree;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.qommons.Transaction;
import org.qommons.collect.*;

/**
 * A tree-based {@link BetterSortedMultiMap}
 * 
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public class BetterTreeMultiMap<K, V> extends AbstractBetterMultiMap<K, V> implements BetterSortedMultiMap<K, V> {
	/**
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param keyCompare The key comparator for the map
	 * @return A builder that can be used to build a {@link BetterTreeMultiMap}
	 */
	public static <K, V> Builder<K, V, ?> build(Comparator<? super K> keyCompare) {
		return new Builder<>(keyCompare);
	}

	/**
	 * Builds a {@link BetterTreeMultiMap}
	 * 
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param <B> The sub-type of this builder
	 */
	public static class Builder<K, V, B extends Builder<K, V, ? extends B>> extends AbstractBetterMultiMap.Builder<K, V, B> {
		private final Comparator<? super K> theKeyCompare;

		private Builder(Comparator<? super K> keyCompare) {
			super("BetterTreeMultiMap");
			theKeyCompare = keyCompare;
		}

		/** @return The key comparator for the map */
		protected Comparator<? super K> getKeyCompare() {
			return theKeyCompare;
		}

		@Override
		public BetterTreeMultiMap<K, V> buildMultiMap() {
			return new BetterTreeMultiMap<>(//
				getLocker(), getKeyCompare(), getValues(), getDescription(), getInitialValues());
		}
	}

	private final Comparator<? super K> theKeyCompare;

	private BetterTreeMultiMap(Function<Object, CollectionLockingStrategy> locking, Comparator<? super K> keyCompare,
		ValueCollectionSupplier<? super K, ? super V> values, String description, Map<K, List<V>> initialValues) {
		super(locking, new BetterTreeMap<>(__ -> new FastFailLockingStrategy(), description, keyCompare), values,
			description, initialValues);
		theKeyCompare = keyCompare;
	}

	@Override
	protected BetterSet<K> createKeySet(BetterSet<K> backing) {
		return new BetterTreeMultiMapKeySet(backing);
	}

	@Override
	public TreeBasedSet<K> keySet() {
		return (TreeBasedSet<K>) super.keySet();
	}

	@Override
	public OrderedMultiEntry<K, V> getEntryById(ElementId keyId) {
		return (OrderedMultiEntry<K, V>) super.getEntryById(keyId);
	}

	@Override
	public OrderedMultiEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
		ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd) {
		return (OrderedMultiEntry<K, V>) super.getOrPutEntry(key, value, afterKey, beforeKey, first, preAdd, postAdd);
	}

	@Override
	protected OrderedMultiEntry<K, V> entryFor(MapEntryHandle<K, BetterCollection<V>> mapEntry) {
		return mapEntry == null ? null : new DefaultOrderedEntryHandle((OrderedMapEntry<K, BetterCollection<V>>) mapEntry);
	}

	class DefaultOrderedEntryHandle extends DefaultEntryHandle implements OrderedMultiEntry<K, V> {
		protected DefaultOrderedEntryHandle(OrderedMapEntry<K, BetterCollection<V>> mapEntry) {
			super(mapEntry);
		}

		@Override
		protected OrderedMapEntry<K, BetterCollection<V>> getMapEntry() {
			return (OrderedMapEntry<K, BetterCollection<V>>) super.getMapEntry();
		}

		@Override
		public int getElementsBefore() {
			return getMapEntry().getElementsBefore();
		}

		@Override
		public int getElementsAfter() {
			return getMapEntry().getElementsAfter();
		}

		@Override
		public OrderedMultiEntry<K, V> getAdjacent(boolean next) {
			return entryFor(getMapEntry().getAdjacent(next));
		}
	}

	class BetterTreeMultiMapKeySet extends BetterMultiMapKeySet implements TreeBasedSet<K> {
		protected BetterTreeMultiMapKeySet(BetterSet<K> backing) {
			super(backing);
		}

		@Override
		protected TreeBasedSet<K> getBacking() {
			return (TreeBasedSet<K>) super.getBacking();
		}

		@Override
		public Comparator<? super K> comparator() {
			return theKeyCompare;
		}

		@Override
		public BinaryTreeNode<K> getElement(int index) {
			try (Transaction t = lock(false, null)) {
				return getBacking().getElement(index);
			}
		}

		@Override
		public BinaryTreeNode<K> getRoot() {
			return getBacking().getRoot();
		}

		@Override
		public BinaryTreeNode<K> splitBetween(ElementId element1, ElementId element2) {
			try (Transaction t = lock(false, null)) {
				return getBacking().splitBetween(element1, element2);
			}
		}

		@Override
		public BinaryTreeNode<K> search(Comparable<? super K> search, BetterSortedList.SortedSearchFilter filter) {
			try (Transaction t = lock(false, null)) {
				return getBacking().search(search, filter);
			}
		}

		@Override
		public BinaryTreeNode<K> getElement(K value, boolean first) {
			return (BinaryTreeNode<K>) super.getElement(value, first);
		}

		@Override
		public BinaryTreeNode<K> getElement(ElementId id) {
			return (BinaryTreeNode<K>) super.getElement(id);
		}

		@Override
		public BinaryTreeNode<K> getTerminalElement(boolean first) {
			return (BinaryTreeNode<K>) super.getTerminalElement(first);
		}

		@Override
		public BinaryTreeNode<K> addElement(K value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (BinaryTreeNode<K>) super.addElement(value, after, before, first);
		}

		@Override
		public BinaryTreeNode<K> getOrAdd(K value, ElementId after, ElementId before, boolean first, Runnable preAdd, Runnable postAdd) {
			return (BinaryTreeNode<K>) super.getOrAdd(value, after, before, first, preAdd, postAdd);
		}

		@Override
		public BinaryTreeNode<K> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (BinaryTreeNode<K>) super.move(valueEl, after, before, first, afterRemove);
		}

		@Override
		public MutableBinaryTreeNode<K> mutableElement(ElementId id) {
			MutableCollectionElement<K> mutable = super.mutableElement(id);
			BinaryTreeNode<K> treeNode = getBacking().getElement(id);
			return new MutableBinaryTreeNode<K>() {
				@Override
				public String isEnabled() {
					return mutable.isEnabled();
				}

				@Override
				public String isAcceptable(K value) {
					return mutable.isAcceptable(value);
				}

				@Override
				public void set(K value) throws UnsupportedOperationException, IllegalArgumentException {
					mutable.set(value);
				}

				@Override
				public String canRemove() {
					return mutable.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					mutable.remove();
				}

				@Override
				public int size() {
					return treeNode.size();
				}

				@Override
				public boolean getSide() {
					return treeNode.getSide();
				}

				@Override
				public int getElementsBefore() {
					return treeNode.getElementsBefore();
				}

				@Override
				public int getElementsAfter() {
					return treeNode.getElementsAfter();
				}

				@Override
				public ElementId getElementId() {
					return mutable.getElementId();
				}

				@Override
				public K get() {
					return mutable.get();
				}

				@Override
				public MutableBinaryTreeNode<K> getParent() {
					BinaryTreeNode<K> relative = treeNode.getParent();
					return relative == null ? null : mutableElement(relative.getElementId());
				}

				@Override
				public MutableBinaryTreeNode<K> getLeft() {
					BinaryTreeNode<K> relative = treeNode.getLeft();
					return relative == null ? null : mutableElement(relative.getElementId());
				}

				@Override
				public MutableBinaryTreeNode<K> getRight() {
					BinaryTreeNode<K> relative = treeNode.getRight();
					return relative == null ? null : mutableElement(relative.getElementId());
				}

				@Override
				public MutableBinaryTreeNode<K> getAdjacent(boolean next) {
					BinaryTreeNode<K> relative = treeNode.getAdjacent(next);
					return relative == null ? null : mutableElement(relative.getElementId());
				}

				@Override
				public MutableBinaryTreeNode<K> getRoot() {
					BinaryTreeNode<K> relative = treeNode.getRoot();
					return relative == null ? null : mutableElement(relative.getElementId());
				}

				@Override
				public MutableBinaryTreeNode<K> getSibling() {
					BinaryTreeNode<K> relative = treeNode.getSibling();
					return relative == null ? null : mutableElement(relative.getElementId());
				}

				@Override
				public MutableBinaryTreeNode<K> get(int index, OptimisticContext ctx) {
					BinaryTreeNode<K> relative = treeNode.get(index, ctx);
					return relative == null ? null : mutableElement(relative.getElementId());
				}

				@Override
				public MutableBinaryTreeNode<K> findClosest(Comparable<BinaryTreeNode<K>> finder, boolean lesser, boolean strictly,
					OptimisticContext ctx) {
					BinaryTreeNode<K> relative = treeNode.findClosest(finder, lesser, strictly, ctx);
					return relative == null ? null : mutableElement(relative.getElementId());
				}
			};
		}

		@Override
		public int indexFor(Comparable<? super K> search) {
			return getBacking().indexFor(search);
		}
	}
}
