package org.qommons.tree;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.AbstractBetterMultiMap;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedMultiMap;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.FastFailLockingStrategy;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.OptimisticContext;

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
		super(locking, new BetterTreeMap<>(__ -> new FastFailLockingStrategy(ThreadConstraint.ANY), description, keyCompare), values,
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
		public int getElementsBefore(ElementId id) {
			try (Transaction t = lock(false, null)) {
				return getBacking().getElementsBefore(id);
			}
		}

		@Override
		public int getElementsAfter(ElementId id) {
			try (Transaction t = lock(false, null)) {
				return getBacking().getElementsAfter(id);
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
		public BinaryTreeNode<K> getAdjacentElement(ElementId elementId, boolean next) {
			return (BinaryTreeNode<K>) super.getAdjacentElement(elementId, next);
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
		public MutableBinaryTreeNode<K> mutableElement(ElementId id) {
			MutableCollectionElement<K> mutable = super.mutableElement(id);
			BinaryTreeNode<K> treeNode = getBacking().getElement(id);
			return new MutableBinaryTreeNode<K>() {
				@Override
				public BetterCollection<K> getCollection() {
					return BetterTreeMultiMapKeySet.this;
				}

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
				public int getNodesBefore() {
					try (Transaction t = lock(false, null)) {
						return treeNode.getNodesBefore();
					}
				}

				@Override
				public int getNodesAfter() {
					try (Transaction t = lock(false, null)) {
						return treeNode.getNodesAfter();
					}
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
				public MutableBinaryTreeNode<K> getClosest(boolean left) {
					try (Transaction t = lock(false, null)) {
						BinaryTreeNode<K> relative = treeNode.getClosest(left);
						return relative == null ? null : mutableElement(relative.getElementId());
					}
				}

				@Override
				public MutableBinaryTreeNode<K> getRoot() {
					BinaryTreeNode<K> relative = treeNode.getRoot();
					return relative == null ? null : mutableElement(relative.getElementId());
				}

				@Override
				public MutableBinaryTreeNode<K> getSibling() {
					try (Transaction t = lock(false, null)) {
						BinaryTreeNode<K> relative = treeNode.getSibling();
						return relative == null ? null : mutableElement(relative.getElementId());
					}
				}

				@Override
				public MutableBinaryTreeNode<K> get(int index, OptimisticContext ctx) {
					try (Transaction t = lock(false, null)) {
						BinaryTreeNode<K> relative = treeNode.get(index, ctx);
						return relative == null ? null : mutableElement(relative.getElementId());
					}
				}

				@Override
				public MutableBinaryTreeNode<K> findClosest(Comparable<BinaryTreeNode<K>> finder, boolean lesser, boolean strictly,
					OptimisticContext ctx) {
					try (Transaction t = lock(false, null)) {
						BinaryTreeNode<K> relative = treeNode.findClosest(finder, lesser, strictly, ctx);
						return relative == null ? null : mutableElement(relative.getElementId());
					}
				}
			};
		}

		@Override
		public int indexFor(Comparable<? super K> search) {
			return getBacking().indexFor(search);
		}
	}
}
