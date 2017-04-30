package org.qommons.collect;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

import org.junit.Test;
import org.qommons.QommonsTestUtils;

/** Tests {@link CircularArrayList} */
public class CircularListTest {
	/**
	 * Runs the basic
	 * {@link QommonsTestUtils#testCollection(java.util.Collection, java.util.function.Consumer, java.util.function.Function)} collection
	 * test suite against a basic {@link CircularArrayList}.
	 */
	@Test
	public void safeCALTest() {
		QommonsTestUtils.testCollection(new CircularArrayList<>(), list -> list.check(), null);
	}

	/**
	 * Runs the basic
	 * {@link QommonsTestUtils#testCollection(java.util.Collection, java.util.function.Consumer, java.util.function.Function)} collection
	 * test suite against a basic {@link CircularArrayList}.
	 */
	@Test
	public void unsafeCALTest() {
		QommonsTestUtils.testCollection(CircularArrayList.build().unsafe().build(), list -> list.check(), null);
	}

	/** Runs a gauntlet of tests against */
	//@Test
	public void testPerformance() {
		ArrayList<Integer> java = new ArrayList<>();
		CircularArrayList<Integer> unsafe = CircularArrayList.build().unsafe().build();
		// CircularArrayList<Integer> safe = new CircularArrayList<>();

		long javaTime = 0;
		// long safeTime = 0;
		long unsafeTime = 0;
		int preTries = 5;
		int tries = 200;
		System.out.print(tries);
		System.out.flush();
		for (int i = 0; i < tries + preTries; i++) {
			long now = System.nanoTime();
			QommonsTestUtils.testCollection(java, null, null);
			long next = System.nanoTime();
			if (i >= preTries)
				javaTime += next - now;
			System.out.print(".");
			System.out.flush();
			now = next;
			QommonsTestUtils.testCollection(unsafe, null, null);
			next = System.nanoTime();
			if (i >= preTries)
				unsafeTime += next - now;
			// System.out.print(".");
			// System.out.flush();
			// now = next;
			// QommonsTestUtils.testCollection(safe, null, null);
			// if (i >= preTries)
			// safeTime += System.nanoTime() - now;
			System.out.print(tries + preTries - 1 - i);
			System.out.flush();
		}
		System.out.println();
		System.out.println("Java: " + org.qommons.QommonsUtils.printTimeLength(javaTime / 1000000));
		System.out.println("Unsafe: " + org.qommons.QommonsUtils.printTimeLength(unsafeTime / 1000000));
		// System.out.println("Safe: " + org.qommons.QommonsUtils.printTimeLength(safeTime / 1000000));
	}

	/** Tests {@link CircularArrayList}'s {@link CircularArrayList#setMaxCapacity(int) max capacity} capability */
	@Test
	public void maxCapTest() {
		CircularArrayList<Integer> list = new CircularArrayList<>();
		list.setMaxCapacity(10000);
		// Run a basic test with a capacity sufficient to avoid dropping elements
		QommonsTestUtils.testCollection(list, c -> c.check(), null);

		// Test basic element-dropping
		list.addAll(QommonsTestUtils.sequence(150, null, false));
		assertEquals(150, list.size());
		list.setMaxCapacity(100);
		assertEquals(100, list.size());
		for (int i = 0; i < 100; i++)
			assertEquals(Integer.valueOf(i + 50), list.get(i));
		list.addAll(// Broken up for debugging
			QommonsTestUtils.sequence(50, i -> i + 150, false));
		assertEquals(100, list.size());
		for (int i = 0; i < 100; i++)
			assertEquals(Integer.valueOf(i + 100), list.get(i));
		list.removeIf(i -> i % 2 == 0);
		assertEquals(50, list.size());
		list.addAll(QommonsTestUtils.sequence(100, i -> i * 2, false));
		assertEquals(100, list.size());
		for (int i = 0; i < 100; i++)
			assertEquals(Integer.valueOf(i * 2), list.get(i));

		// Test element-dropping as a result of modification of views (sublists and iterators)
		list.clear();
		list.addAll(QommonsTestUtils.sequence(100, null, false));
		CircularArrayList<Integer>.SubList sub1 = list.subList(25, 75);
		sub1.removeRange(0, 10);
		assertEquals(40, sub1.size());
		assertEquals(90, list.size());
		sub1.addAll(//
			QommonsTestUtils.sequence(20, null, false));
		// Should have pushed 10 elements off of the beginning of the list
		assertEquals(60, sub1.size());
		assertEquals(100, list.size());
		for (int i = 0; i < 100; i++) {
			if (i < 15)
				assertEquals(Integer.valueOf(i + 10), list.get(i));
			else if (i < 55) {
				assertEquals(Integer.valueOf(i + 20), list.get(i));
				assertEquals(Integer.valueOf(i + 20), sub1.get(i - 15));
			} else if (i < 75) {
				assertEquals(Integer.valueOf(i - 55), list.get(i));
				assertEquals(Integer.valueOf(i - 55), sub1.get(i - 15));
			} else
				assertEquals(Integer.valueOf(i), list.get(i));
		}
	}

	/* TODO Test:
	 * Thread safeness
	 */
}
