package org.qommons.tree;

import static org.qommons.testing.QommonsTestUtils.testCollection;
import static org.qommons.testing.QommonsTestUtils.testMap;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.LambdaUtils;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ValueStoredCollection;
import org.qommons.testing.QommonsTestUtils;
import org.qommons.testing.TestHelper;

/** Runs tests on the red-black tree structures behind the ObServe tree collections */
public class TreeUtilsTest {
	private static boolean PRINT = false;
	
	/**
	 * A testing method. Adds sequential nodes into a tree and removes them, checking validity of the tree at each step.
	 *
	 * @param <T> The type of values to put in the tree
	 * @param tree The tree
	 * @param nodes The sequence of nodes to add to the tree. Must repeat.
	 */
	public static <T> void test(RedBlackTree<T> tree, Iterable<T> nodes) {
		RedBlackNode.DEBUG_PRINT = PRINT;
		Iterator<T> iter = nodes.iterator();
		iter.next(); // Skip the first value, assuming that's what's in the tree
		if(PRINT) {
			System.out.println(RedBlackNode.print(tree.getRoot()));
			System.out.println(" ---- ");
		}
		while(iter.hasNext()) {
			T value = iter.next();
			if(PRINT)
				System.out.println("Adding " + value);
			tree.getRoot().getTerminal(false, () -> true).add(new RedBlackNode<>(tree, value), false);
			if(PRINT)
				System.out.println(RedBlackNode.print(tree.getRoot()));
			tree.getRoot().checkValid();
			if(PRINT)
				System.out.println(" ---- ");
		}
		if(PRINT)
			System.out.println(" ---- \n ---- \nDeleting:");

		iter = nodes.iterator();
		while(iter.hasNext()) {
			T value = iter.next();
			if(PRINT)
				System.out.println("Deleting " + value);
			tree.getRoot().getTerminal(true, () -> true).delete();
			if(PRINT)
				System.out.println(RedBlackNode.print(tree.getRoot()));
			if (tree.getRoot() != null)
				tree.getRoot().checkValid();
			if(PRINT)
				System.out.println(" ---- ");
		}
	}

	/**
	 * Iterates through the alphabet from 'a' up to the given character
	 *
	 * @param last The last letter to be returned from the iterator
	 * @return An alphabet iterable
	 */
	protected static final Iterable<String> alphaBet(char last) {
		return () -> {
			return new Iterator<String>() {
				private char theNext = 'a';

				@Override
				public boolean hasNext() {
					return theNext <= last;
				}

				@Override
				public String next() {
					String ret = "" + theNext;
					theNext++;
					return ret;
				}
			};
		};
	}

	/** A simple test against {@link RedBlackNode} */
	@Test
	@SuppressWarnings("static-method")
	public void testTreeBasic() {
		RedBlackTree<String> tree = new RedBlackTree<>();
		tree.setRoot(new RedBlackNode<>(tree, "a"));
		test(tree, alphaBet('z'));
	}

	/** Tests {@link RedBlackNode#compare(RedBlackNode, RedBlackNode, java.util.function.BooleanSupplier)} */
	@Test
	@SuppressWarnings("static-method")
	public void testTreeNodeCompare() {
		TestHelper.createTester(NodeCompareTester.class).withRandomCases(1)//
			.withDebug(false)//
			.withFailurePersistence(false)//
			.execute().throwErrorIfFailed();
	}

	static class NodeCompareTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			RedBlackTree<String> tree = new RedBlackTree<>();
			// Build up a-z
			for (String s : alphaBet('z')) {
				if (tree.getRoot() == null)
					tree.setRoot(new RedBlackNode<>(tree, s));
				else
					tree.getTerminal(false).add(new RedBlackNode<>(tree, s), false);
			}

			for (int i = 0; i < 100000; i++) {
				int idx1 = helper.getInt(0, 26);
				int idx2 = helper.getInt(0, 26);
				int idxCompare = idx1 - idx2;
				if (idxCompare < 0)
					idxCompare = -1;
				else if (idxCompare > 0)
					idxCompare = 1;
				RedBlackNode<String> node1 = tree.getRoot().get(idx1, () -> true);
				RedBlackNode<String> node2 = tree.getRoot().get(idx2, () -> true);
				int nodeCompare = RedBlackNode.compare(node1, node2, null);
				if (nodeCompare < 0)
					nodeCompare = -1;
				else if (nodeCompare > 0)
					nodeCompare = 1;
				Assert.assertEquals(idxCompare, nodeCompare);
			}
		}
	}

	/** Barrage-tests {@link RedBlackNode#splitBetween(RedBlackNode, RedBlackNode, java.util.function.BooleanSupplier)} */
	@Test
	@SuppressWarnings("static-method")
	public void testTreeNodeSplit() {
		TestHelper.createTester(TreeSplitTester.class).withRandomCases(1)//
			.withDebug(true)//
			.withFailurePersistence(true)//
			.execute().throwErrorIfFailed();
	}

	static class TreeSplitTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			RedBlackTree<String> tree = new RedBlackTree<>();
			// Build up a-z
			for (String s : alphaBet('z')) {
				if (tree.getRoot() == null)
					tree.setRoot(new RedBlackNode<>(tree, s));
				else
					tree.getTerminal(false).add(new RedBlackNode<>(tree, s), false);
			}

			for (int i = 0; i < 100000; i++) {
				int idx1 = helper.getInt(0, 26);
				int idx2 = helper.getInt(0, 26);
				RedBlackNode<String> node1 = tree.getRoot().get(idx1, () -> true);
				RedBlackNode<String> node2 = tree.getRoot().get(idx2, () -> true);
				int nodeCompare = RedBlackNode.compare(node1, node2, null);
				RedBlackNode<String> split = RedBlackNode.splitBetween(node1, node2, null);
				if (split == null) {
					Assert.assertTrue(idx2 <= idx1 + 1);
				} else {
					Assert.assertEquals(nodeCompare, RedBlackNode.compare(node1, split, null));
					Assert.assertEquals(nodeCompare, RedBlackNode.compare(split, node2, null));
				}
			}
		}
	}

	/** Tests {@link RedBlackTree#copy()} */
	@Test
	public void testTreeCopy() {
		RedBlackTree<Integer> tree = new RedBlackTree<>();
		for (int i = 0; i < 100000; i++) {
			if (tree.getRoot() == null)
				tree.setRoot(new RedBlackNode<>(tree, i));
			else
				tree.getTerminal(false).add(new RedBlackNode<>(tree, i), false);
		}
		checkIntegrity(tree);

		RedBlackTree<Integer> copy = tree.copy();
		checkIntegrity(copy);
	}

	/**
	 * Tests {@link RedBlackNode#build(RedBlackTree, Iterable, java.util.function.Function)} against java {@link TreeSet} and
	 * {@link TreeMap}
	 */
	@Test
	public void testTreeCopyFromJavaTree() {
		TreeSet<Integer> tree = new TreeSet<>();
		for (int i = 0; i < 100000; i++) {
			tree.add(i);
		}

		RedBlackTree<Integer> copy = new RedBlackTree<>();
		Assert.assertTrue(RedBlackNode.build(copy, tree, v -> v));
		checkIntegrity(copy);

		TreeMap<Integer, Integer> map = new TreeMap<>();
		for (int i = 0; i < 100000; i++) {
			map.put(i, i);
		}

		RedBlackTree<Integer> mapCopy = new RedBlackTree<>();
		Assert.assertTrue(RedBlackNode.build(mapCopy, map.keySet(), v -> v));
		checkIntegrity(mapCopy);

		mapCopy = new RedBlackTree<>();
		Assert.assertTrue(RedBlackNode.build(mapCopy, map.entrySet(), e -> e.getKey()));
		checkIntegrity(mapCopy);

		mapCopy = new RedBlackTree<>();
		Assert.assertTrue(RedBlackNode.build(mapCopy, map.values(), v -> v));
		checkIntegrity(mapCopy);

		mapCopy = new RedBlackTree<>();
		Assert.assertTrue(RedBlackNode.build(mapCopy, new TreeSet<>(map.keySet()), v -> v));
		checkIntegrity(mapCopy);
	}

	private void checkIntegrity(RedBlackTree<Integer> tree) {
		checkIntegrity(tree.getRoot());
		RedBlackNode<Integer> node = tree.getFirst();
		Assert.assertEquals(Integer.valueOf(0), node.getValue());
		RedBlackNode<Integer> next = node.getClosest(false);
		int count = 1;
		while (next != null) {
			Assert.assertEquals("[" + count + "]", node.getValue() + 1, next.getValue().intValue());
			node = next;
			count++;
			next = node.getClosest(false);
		}
		Assert.assertEquals(tree.size(), count);
		Assert.assertEquals(tree.getLast().getValue(), node.getValue());

		count = 1;
		RedBlackNode<Integer> prev = node.getClosest(true);
		while (prev != null) {
			Assert.assertEquals("[" + count + "]", node.getValue() - 1, prev.getValue().intValue());
			node = prev;
			count++;
			prev = node.getClosest(true);
		}
		Assert.assertEquals(tree.size(), count);
		Assert.assertEquals(tree.getFirst().getValue(), node.getValue());
	}

	private void checkIntegrity(RedBlackNode<?> node) {
		int size = 1;
		if (node.getLeft() != null) {
			checkIntegrity(node.getLeft());
			Assert.assertEquals(node, node.getLeft().getParent());
			size += node.getLeft().size();
		}
		if (node.getRight() != null) {
			checkIntegrity(node.getRight());
			Assert.assertEquals(node, node.getRight().getParent());
			size += node.getRight().size();
		}
		Assert.assertEquals(size, node.size());
	}

	static class TreeRepairTester implements TestHelper.Testable {
		private static Comparator<? super IntHolder>[] SORT_STRATEGIES = new Comparator[] {
			LambdaUtils.<IntHolder> printableComparator((h1, h2) -> Integer.compare(h1.value, h2.value), () -> "asc"), //
			LambdaUtils.<IntHolder> printableComparator((h1, h2) -> -Integer.compare(h1.value, h2.value), () -> "desc"), //
			LambdaUtils.<IntHolder> printableComparator((h1, h2) -> compareIntAbs(h1.value, h2.value), () -> "abs asc"), //
			LambdaUtils.<IntHolder> printableComparator((h1, h2) -> -compareIntAbs(h1.value, h2.value), () -> "abs desc"), //
			LambdaUtils.<IntHolder> printableComparator((h1, h2) -> compareIntStr(h1.value, h2.value), () -> "str"), //
			LambdaUtils.<IntHolder> printableComparator((h1, h2) -> -compareIntStr(h1.value, h2.value), () -> "str desc")//
		};

		// This differs from just comparing the absolute values in that if the absolute values are equal but the values themselves aren't,
		// this method returns non-zero.
		private static int compareIntAbs(int i1, int i2) {
			boolean i1Neg = i1 < 0;
			if (i1Neg)
				i1 = -i1;
			boolean i2Neg = i2 < 0;
			if (i2Neg)
				i2 = -i2;
			int comp = Integer.compare(i1, i2);
			if (comp == 0 && i1Neg != i2Neg)
				return i1Neg ? -1 : 1;
			return comp;
		}

		private static int compareIntStr(int i1, int i2) {
			return String.valueOf(i1).compareTo(String.valueOf(i2));
		}

		@Override
		public void accept(TestHelper t) {
			int[] sortType = new int[] { t.getInt(0, SORT_STRATEGIES.length) };
			// Build a random tree set
			BetterTreeSet<IntHolder> set = BetterTreeSet.<IntHolder> buildTreeSet(LambdaUtils.<IntHolder> printableComparator(//
				(h1, h2) -> SORT_STRATEGIES[sortType[0]].compare(h1, h2), () -> SORT_STRATEGIES[sortType[0]].toString()))
				.build();
			int length = t.getInt(2, 10);
			// int length = t.getInt(2, 10000);
			for (int i = 0; i < length; i++)
				set.add(new IntHolder(t.getInt(-100, 100)));
			if (t.isReproducing())
				System.out.println("Original: " + set);
			List<Integer> copy = new ArrayList<>(set.size());
			for (IntHolder v : set)
				copy.add(v.value);
			ValueStoredCollection.RepairListener<IntHolder, Void> repair = new ValueStoredCollection.RepairListener<IntHolder, Void>() {
				@Override
				public Void removed(CollectionElement<IntHolder> element) {
					copy.remove(set.getElementsBefore(element.getElementId()));
					return null;
				}

				@Override
				public void disposed(IntHolder value, Void data) {
				}

				@Override
				public void transferred(CollectionElement<IntHolder> element, Void data) {
					copy.add(set.getElementsBefore(element.getElementId()), element.get().value);
				}
			};

			if (t.getBoolean()) {
				// Update some elements with random values, storing the element IDs updated
				int updates = t.getInt(1, Math.min(length - 1, 2));
				if (t.isReproducing())
					System.out.println("Updating " + updates + " random element(s)");
				// int updates = t.getInt(1, length);
				List<ElementId> toUpdate = new ArrayList<>(updates);
				for (int i = 0; i < updates; i++) {
					int index = t.getInt(0, set.size());
					int newValue = t.getInt(-100, 100);
					ElementId el = set.getElement(index).getElementId();
					set.getElement(el).get().value = newValue;
					copy.set(index, newValue);
					toUpdate.add(el);
				}
				if (t.isReproducing())
					System.out.println("Updated:  " + set);

				// Inform the set that the updated elements have changed and allow it to fix itself
				// Fix the copy in tandem
				for (ElementId el : toUpdate) {
					t.placemark();
					// The element may have been removed due to previous operations
					if (!el.isPresent())
						continue;
					set.repair(el, repair);
				}
			} else {
				// Completely switch out the sort strategy to ensure repair can fix anything
				int oldSort = sortType[0];
				do {
					sortType[0] = t.getInt(0, SORT_STRATEGIES.length);
				} while (sortType[0] == oldSort);
				if (t.isReproducing())
					System.out.println("Switch sorting from " + SORT_STRATEGIES[oldSort] + " to " + SORT_STRATEGIES[sortType[0]]);
				// Use the element-less repair method
				set.repair(repair);
			}
			if (t.isReproducing())
				System.out.println("Repaired: " + set);

			t.placemark();
			// Verify that the copy and the set have the same values and that they are sorted correctly
			Assert.assertEquals(set.size(), copy.size());
			int i = 0;
			IntHolder prev = null;
			for (IntHolder v : set) {
				if (i > 0 && SORT_STRATEGIES[sortType[0]].compare(prev, v) > 0)
					throw new AssertionError("Tree repair falure at " + i + ": " + set);
				prev = v;
				Assert.assertEquals(v.value, copy.get(i++).intValue());
			}
		}

		static class IntHolder {
			int value;

			IntHolder(int value) {
				this.value = value;
			}

			@Override
			public int hashCode() {
				return Integer.hashCode(value);
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof IntHolder && ((IntHolder) obj).value == value;
			}

			@Override
			public String toString() {
				return String.valueOf(value);
			}
		}
	}

	/** Tests the {@link ValueStoredCollection} repair API */
	@Test
	@SuppressWarnings("static-method")
	public void testTreeRepair() {
		TestHelper.createTester(TreeRepairTester.class).withDebug(true)
			.withPersistenceDir(new File("src/test/java/org/qommons/tree"), false).revisitKnownFailures(true)//
			.withRandomCases(300).withMaxFailures(1)//
			.execute().throwErrorIfFailed();
	}

	static class TreeSetTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			BetterTreeSet<Integer> set = BetterTreeSet.<Integer> buildTreeSet(Integer::compareTo).build();
			testCollection(set, s -> s.checkValid(), null, helper);
		}
	}

	/**
	 * Runs the
	 * {@link QommonsTestUtils#testCollection(java.util.Collection, java.util.function.Consumer, java.util.function.Function, TestHelper)}
	 * tests against {@link BetterTreeSet}
	 */
	@Test
	@SuppressWarnings("static-method")
	public void testTreeSet() {
		TestHelper.createTester(TreeSetTester.class).withDebug(false).withFailurePersistence(false).withRandomCases(1).execute()
			.throwErrorIfFailed();
	}

	/**
	 * Runs the {@link QommonsTestUtils#testMap(Map, java.util.function.Consumer, java.util.function.Function)} tests against
	 * {@link BetterTreeMap}
	 */
	@Test
	@SuppressWarnings("static-method")
	public void testTreeMap() {
		BetterTreeMap<Integer, Integer> map = BetterTreeMap.<Integer> build(new Comparator<Integer>() {
			@Override
			public int compare(Integer o1, Integer o2) {
				return o1.compareTo(o2);
			}
		}).buildMap();
		testMap(map, s -> s.checkValid(), null);
	}

	static class TreeListTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			BetterTreeList<Integer> list = BetterTreeList.<Integer> build().build();
			testCollection(list, l -> list.checkValid(), null, helper);

			// I want the tree list to tolerate appending to the list during iteration.
			// In particular, I want to make sure that if a value is added to the list on the last value iterated, then the iteration will
			// continue with the new value. This property is important for the listener list in SimpleObservable
			testIterationAdd(list);
			testIterationAdd(BetterTreeList.<Integer> build().build());
		}
	}

	/**
	 * Runs the
	 * {@link QommonsTestUtils#testCollection(java.util.Collection, java.util.function.Consumer, java.util.function.Function, TestHelper)}
	 * tests against {@link BetterTreeList}
	 */
	@Test
	@SuppressWarnings("static-method")
	public void testTreeList() {
		TestHelper.createTester(TreeListTester.class).withDebug(true).revisitKnownFailures(true).withFailurePersistence(true)
			.withRandomCases(1).execute().throwErrorIfFailed();
	}

	private static void testIterationAdd(BetterTreeList<Integer> list) {
		list.addAll(QommonsTestUtils.sequence(5, v -> v, false));
		boolean fiveIsNext = false;
		for (Integer v : list) {
			if (fiveIsNext)
				org.junit.Assert.assertEquals((Integer) 5, v);
			else if (v == 4) {
				list.add(5);
				fiveIsNext = true;
			}
		}
	}
}
