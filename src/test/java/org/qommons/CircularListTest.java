package org.qommons;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.qommons.collect.CircularArrayList;

/** Tests {@link CircularArrayList} */
public class CircularListTest {
	/**
	 * Runs the basic
	 * {@link QommonsTestUtils#testCollection(java.util.Collection, java.util.function.Consumer, java.util.function.Function)} collection
	 * test suite against a basic {@link CircularArrayList}.
	 */
	@Test
	public void basicCALTest() {
		QommonsTestUtils.testCollection(new CircularArrayList<>(), null, null);
	}

	/** Tests {@link CircularArrayList}'s {@link CircularArrayList#setMaxCapacity(int) max capacity} capability */
	@Test
	public void maxCapTest() {
		CircularArrayList<Integer> list = new CircularArrayList<>();
		list.setMaxCapacity(10000);
		// Run a basic test with a capacity sufficient to avoid dropping elements
		QommonsTestUtils.testCollection(list, null, null);

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
	 * 
	 * Add toArray and reversibility tests in QommonsTestUtils
	 */
}
