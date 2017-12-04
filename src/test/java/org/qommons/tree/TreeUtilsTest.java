package org.qommons.tree;

import static org.qommons.QommonsTestUtils.testCollection;
import static org.qommons.QommonsTestUtils.testMap;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;

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
	 * @param tree The initial tree node
	 * @param nodes The sequence of nodes to add to the tree. Must repeat.
	 */
	public static <T> void test(RedBlackNode<T> tree, Iterable<T> nodes) {
		RedBlackNode.DEBUG_PRINT = PRINT;
		Iterator<T> iter = nodes.iterator();
		iter.next(); // Skip the first value, assuming that's what's in the tree
		if(PRINT) {
			System.out.println(RedBlackNode.print(tree));
			System.out.println(" ---- ");
		}
		while(iter.hasNext()) {
			T value = iter.next();
			if(PRINT)
				System.out.println("Adding " + value);
			tree = tree.getTerminal(false, () -> true).add(new RedBlackNode<>(value), false);
			if(PRINT)
				System.out.println(RedBlackNode.print(tree));
			tree.checkValid();
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
			tree = tree.getTerminal(true, ()->true).delete();
			if(PRINT)
				System.out.println(RedBlackNode.print(tree));
			if(tree != null)
				tree.checkValid();
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
		test(new RedBlackNode<>("a"), alphaBet('z'));
	}

	static class TreeSetTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			BetterTreeSet<Integer> set = new BetterTreeSet<>(false, Integer::compareTo);
			testCollection(set, s -> s.checkValid(), null, helper);
		}
	}

	/**
	 * Runs the {@link QommonsTestUtils#testCollection(java.util.Collection, java.util.function.Consumer, java.util.function.Function)}
	 * tests against {@link BetterTreeSet}
	 */
	@Test
	public void testTreeSet() {
		TestHelper.createTester(TreeSetTester.class).withDebug(false).withFailurePersistence(false).withRandomCases(1).execute();
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
	 * Runs the {@link QommonsTestUtils#testCollection(java.util.Collection, java.util.function.Consumer, java.util.function.Function)}
	 * tests against {@link BetterTreeList}
	 */
	@Test
	public void testTreeList() {
		TestHelper.createTester(TreeListTester.class).withDebug(false).withFailurePersistence(false).withRandomCases(1).execute();
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
