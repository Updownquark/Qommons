package org.qommons.tree;

import static org.qommons.QommonsTestUtils.testCollection;
import static org.qommons.QommonsTestUtils.testMap;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.QommonsTestUtils;
import org.qommons.TestHelper;

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
	public void testTreeBasic() {
		RedBlackTree<String> tree = new RedBlackTree<>();
		tree.setRoot(new RedBlackNode<>(tree, "a"));
		test(tree, alphaBet('z'));
	}

	/** Tests {@link RedBlackNode#compare(RedBlackNode, RedBlackNode, java.util.function.BooleanSupplier)} */
	@Test
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

	@Test
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

	@Test
	public void testTreeCopyFromJavaTree() {
		TreeSet<Integer> tree = new TreeSet<>();
		for (int i = 0; i < 100000; i++) {
			tree.add(i);
		}

		RedBlackTree<Integer> copy = new RedBlackTree<>();
		Assert.assertTrue(RedBlackNode.build(copy, tree));
		checkIntegrity(copy);

		TreeMap<Integer, Integer> map = new TreeMap<>();
		for (int i = 0; i < 100000; i++) {
			map.put(i, i);
		}

		RedBlackTree<Integer> mapCopy = new RedBlackTree<>();
		Assert.assertTrue(RedBlackNode.build(mapCopy, map, (k, v) -> k));
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

	static class TreeSetTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			BetterTreeSet<Integer> set = new BetterTreeSet<>(false, Integer::compareTo);
			testCollection(set, s -> s.checkValid(), null, helper);
		}
	}

	/**
	 * Runs the
	 * {@link QommonsTestUtils#testCollection(java.util.Collection, java.util.function.Consumer, java.util.function.Function, TestHelper)}
	 * tests against {@link BetterTreeSet}
	 */
	@Test
	public void testTreeSet() {
		TestHelper.createTester(TreeSetTester.class).withDebug(false).withFailurePersistence(false).withRandomCases(1).execute()
			.throwErrorIfFailed();
	}

	/**
	 * Runs the {@link QommonsTestUtils#testMap(Map, java.util.function.Consumer, java.util.function.Function)} tests against
	 * {@link BetterTreeMap}
	 */
	@Test
	public void testTreeMap() {
		BetterTreeMap<Integer, Integer> map = new BetterTreeMap<>(false, new Comparator<Integer>() {
			@Override
			public int compare(Integer o1, Integer o2) {
				return o1.compareTo(o2);
			}
		});
		testMap(map, s -> s.checkValid(), null);
	}

	static class TreeListTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			BetterTreeList<Integer> list = new BetterTreeList<>(false);
			testCollection(list, l -> list.checkValid(), null, helper);

			// I want the tree list to tolerate appending to the list during iteration.
			// In particular, I want to make sure that if a value is added to the list on the last value iterated, then the iteration will
			// continue with the new value. This property is important for the listener list in SimpleObservable
			testIterationAdd(list);
			testIterationAdd(new BetterTreeList<>(true));
		}
	}

	/**
	 * Runs the
	 * {@link QommonsTestUtils#testCollection(java.util.Collection, java.util.function.Consumer, java.util.function.Function, TestHelper)}
	 * tests against {@link BetterTreeList}
	 */
	@Test
	public void testTreeList() {
		TestHelper.createTester(TreeListTester.class).withDebug(false).withFailurePersistence(false).withRandomCases(1).execute()
			.throwErrorIfFailed();
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
