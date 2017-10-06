package org.qommons.collect;

import static org.junit.Assert.assertEquals;
import static org.qommons.QommonsUtils.printTimeLength;

import java.text.DecimalFormat;
import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.QommonsTestUtils;
import org.qommons.TestHelper;

/** Tests {@link CircularArrayList} */
public class CircularListTest {
	private static boolean WITH_PROGRESS = false;
	private static final DecimalFormat PC_FMT = new DecimalFormat("0%");

	/**
	 * Runs the basic
	 * {@link QommonsTestUtils#testCollection(java.util.Collection, java.util.function.Consumer, java.util.function.Function)} collection
	 * test suite against a basic {@link CircularArrayList}.
	 */
	@Test
	public void safeCALTest() {
		TestHelper.testSingle(//
			helper -> QommonsTestUtils.testCollection(new CircularArrayList<>(), list -> list.checkValid(), null, helper), //
			1, -1);
	}

	/**
	 * Runs the basic
	 * {@link QommonsTestUtils#testCollection(java.util.Collection, java.util.function.Consumer, java.util.function.Function)} collection
	 * test suite against a basic {@link CircularArrayList}.
	 */
	@Test
	public void unsafeCALTest() {
		TestHelper.testSingle(//
			helper -> QommonsTestUtils.testCollection(CircularArrayList.build().unsafe().withMinCapacity(20).withMinOccupancy(0.5).build(),
				list -> list.checkValid(), null, helper), //
			1, -1);
	}

	/** Runs a gauntlet of tests against */
	//@Test
	public void testPerformance() {
		TestHelper.testSingle(helper -> {
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
				QommonsTestUtils.testCollection(java, null, null, helper);
				long next = System.nanoTime();
				if (i >= preTries)
					javaTime += next - now;
				System.out.print(".");
				System.out.flush();
				now = next;
				QommonsTestUtils.testCollection(unsafe, null, null, helper);
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
			System.out.println(
				"Java: " + printTime(javaTime) + ", unsafe: " + printTime(unsafeTime) + ": " + PC_FMT.format(unsafeTime * 1.0 / javaTime));
			// System.out.println("Safe: " + org.qommons.QommonsUtils.printTimeLength(safeTime / 1000000));
		}, 1, -1);
	}

	/**
	 * Ensures the thread-unsafe performance of CircularArrayList does not regress.
	 * 
	 * This method tests operations where CircularArrayList excels versus java's ArrayList due to its ability to move at most half of its
	 * elements for any operation.
	 */
	@Test
	public void testRandomAddPerformance() {
		ArrayList<Integer> java = new ArrayList<>();
		CircularArrayList<Integer> unsafe = CircularArrayList.build().unsafe().build();

		int size = 100000; // CAL performs relatively well for large sizes

		long javaAddTime = 0;
		long unsafeAddTime = 0;
		if (WITH_PROGRESS) {
			System.out.print("0%");
			System.out.flush();
		}
		while (java.size() < size) {
			int index = (int) (Math.random() * java.size());

			long start = System.nanoTime();
			java.add(index, index);
			javaAddTime += System.nanoTime() - start;
			start = System.nanoTime();
			unsafe.add(index, index);
			unsafeAddTime += System.nanoTime() - start;

			if (WITH_PROGRESS && (java.size() * 100) % size == 0) {
				int div = java.size() * 100 / size;
				if (div % 10 == 0)
					System.out.print(div + "%");
				else
					System.out.print('.');
				System.out.flush();
			}
		}
		if (WITH_PROGRESS)
			System.out.println();

		long javaRemoveTime = 0;
		long unsafeRemoveTime = 0;
		while (java.size() > 0) {
			if (WITH_PROGRESS && (java.size() * 100) % size == 0) {
				int div = java.size() * 100 / size;
				if (div % 10 == 0)
					System.out.print(div + "%");
				else
					System.out.print('.');
				System.out.flush();
			}

			int index = (int) (Math.random() * java.size());

			long start = System.nanoTime();
			java.remove(index);
			javaRemoveTime += System.nanoTime() - start;
			start = System.nanoTime();
			unsafe.remove(index);
			unsafeRemoveTime += System.nanoTime() - start;
		}
		if (WITH_PROGRESS) {
			System.out.println("0%");
		}

		double prop = 0.6;
		if (WITH_PROGRESS || !checkTime(unsafeAddTime, javaAddTime, prop) || !checkTime(unsafeRemoveTime, javaRemoveTime, prop)) {
			System.out.println("Random Add: Java: " + printTime(javaAddTime) + ", CAL: " + printTime(unsafeAddTime) + ": "
				+ PC_FMT.format(unsafeAddTime * 1.0 / javaAddTime));
			System.out.println(
				"Random Remove: Java: " + printTime(javaRemoveTime) + ", CAL: " + printTime(unsafeRemoveTime) + ": "
					+ PC_FMT.format(unsafeRemoveTime * 1.0 / javaRemoveTime));
			Assert.assertTrue(checkTime(unsafeAddTime, javaRemoveTime, prop));
			Assert.assertTrue(checkTime(unsafeRemoveTime, javaRemoveTime, prop));
		}
	}

	/**
	 * Ensures the thread-unsafe performance of CircularArrayList does not regress.
	 * 
	 * This method tests operations where CircularArrayList must do more work than java's ArrayList due to its support of features like
	 * thread-safety and max capacity, even if these features are not used. Hence the checks allow for significantly worse performance than
	 * ArrayList.
	 */
	@Test
	public void testAgainstJava() {
		ArrayList<Integer> java = new ArrayList<>();
		CircularArrayList<Integer> unsafe = CircularArrayList.build().unsafe().build();

		int size = 100; // CAL performs relatively poorly for small sizes

		long javaAddTime = 0;
		long unsafeAddTime = 0;
		int tries = 100;

		// Sequential add
		for (int t = 0; t < tries; t++) {
			java.clear();
			unsafe.clear();
			for (int i = 0; i < size; i++) {
				long start = System.nanoTime();
				java.add(i);
				javaAddTime += System.nanoTime() - start;
				start = System.nanoTime();
				unsafe.add(i);
				unsafeAddTime += System.nanoTime() - start;
			}
		}

		long javaGetTime = 0;
		long unsafeGetTime = 0;
		tries = 25000000;

		// Random get
		for (int t = 0; t < tries; t++) {
			int index = (int) (Math.random() * size);
			long start = System.nanoTime();
			java.get(index);
			javaGetTime += System.nanoTime() - start;
			start = System.nanoTime();
			unsafe.get(index);
			unsafeGetTime += System.nanoTime() - start;
		}
		// Currently, CAL is 2x to 3x slower than java for sequential add and random get for this list size
		if (WITH_PROGRESS || !checkTime(unsafeAddTime, javaAddTime, 3.25)) {
			System.out.println("Seq add: Java: " + printTime(javaAddTime) + ", CAL: " + printTime(unsafeAddTime) + ": "
				+ PC_FMT.format(unsafeAddTime * 1.0 / javaAddTime));
			Assert.assertTrue(checkTime(unsafeAddTime, javaAddTime, 3.25));
		}
		if (WITH_PROGRESS || !checkTime(unsafeGetTime, javaGetTime, 3.25)) {
			System.out.println("Random get: Java: " + printTime(javaGetTime) + ", CAL: " + printTime(unsafeGetTime) + ": "
				+ PC_FMT.format(unsafeGetTime * 1.0 / javaGetTime));
			Assert.assertTrue(checkTime(unsafeGetTime, javaGetTime, 3.25));
		}
	}

	private static String printTime(long nanos) {
		long millis = nanos / 1000000;
		if (millis != 0)
			return printTimeLength(millis) + " " + (nanos % 1000000) + "ns";
		else
			return (nanos % 1000000) + "ns";
	}

	private static boolean checkTime(long testing, long standard, double tolerance) {
		return testing <= standard * tolerance;
	}

	/** Tests {@link CircularArrayList}'s {@link CircularArrayList#setMaxCapacity(int) max capacity} capability */
	@Test
	public void maxCapTest() {
		TestHelper.testSingle(helper -> {
			CircularArrayList<Integer> list = new CircularArrayList<>();
			list.setMaxCapacity(10000);
			// Run a basic test with a capacity sufficient to avoid dropping elements
			QommonsTestUtils.testCollection(list, c -> c.checkValid(), null, helper);

			ArrayList<Integer> check = new ArrayList<>();
			// Test basic element-dropping
			list.addAll(QommonsTestUtils.sequence(150, null, false));
			assertEquals(150, list.size());
			list.setMaxCapacity(100);
			assertEquals(100, list.size());
			for (int i = 0; i < 100; i++)
				assertEquals(Integer.valueOf(i + 50), list.get(i));
			check.addAll(QommonsTestUtils.sequence(100, v -> v + 50, false));
			Assert.assertThat(list, QommonsTestUtils.collectionsEqual(check, true));

			list.addAll(// Broken up for debugging
				QommonsTestUtils.sequence(50, i -> i + 150, false));
			check.removeAll(QommonsTestUtils.sequence(50, i -> i + 50, false));
			check.addAll(QommonsTestUtils.sequence(50, i -> i + 150, false));
			assertEquals(100, list.size());
			for (int i = 0; i < 100; i++)
				assertEquals(Integer.valueOf(i + 100), list.get(i));
			Assert.assertThat(list, QommonsTestUtils.collectionsEqual(check, true));

			list.removeIf(i -> i % 2 == 0);
			check.removeIf(i -> i % 2 == 0);
			assertEquals(50, list.size());
			list.addAll(QommonsTestUtils.sequence(100, i -> i * 2, false));
			for (int i = 50; i >= 0; i++)
				check.remove(i);
			check.addAll(QommonsTestUtils.sequence(100, i -> i * 2, false));
			assertEquals(100, list.size());
			for (int i = 0; i < 100; i++)
				assertEquals(Integer.valueOf(i * 2), list.get(i));
			Assert.assertThat(list, QommonsTestUtils.collectionsEqual(check, true));

			list.addAll(list.size() / 4, QommonsTestUtils.sequence(50, null, false));
			// TODO What should this look like

			// Test element-dropping as a result of modification of views (sublists and iterators)
			list.clear();
			list.addAll(QommonsTestUtils.sequence(100, null, false));
			BetterList<Integer> sub1 = list.subList(25, 75);
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
		}, 1, -1);
	}

	/* TODO Test:
	 * Thread safeness
	 */
}
