package org.qommons.collect;

import java.time.Duration;
import java.util.BitSet;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.testing.TestHelper;
import org.qommons.testing.TestHelper.Testable;

/**
 * Tests the functionality of all the functionality in {@link BetterBitSet} over and above what java's own {@link BitSet} does. I'll just
 * assume that what's there is perfect.
 */
public class BetterBitSetTest {
	/** Tests added {@link BetterBitSet} functionality */
	@Test
	public void testBetterBitSet() {
		TestHelper.createTester(BetterBitSetTester.class)//
			.withPlacemarks("op")//
			.withFailurePersistence(true).revisitKnownFailures(true).withDebug(true)//
			.withRandomCases(25).withMaxProgressInterval(Duration.ofMillis(1000)).withMaxCaseDuration(Duration.ofSeconds(5))//
			.execute().throwErrorIfFailed();
	}

	/** {@link Testable} for {@link BetterBitSetTest#testBetterBitSet()} */
	public static class BetterBitSetTester implements Testable {
		private final BetterBitSet theLeft = new BetterBitSet();
		private final BetterBitSet theRight = new BetterBitSet();

		@Override
		public void accept(TestHelper helper) {
			int ops = 5_000;
			for (int i = 0; i < ops; i++) {
				boolean left = helper.getBoolean();
				BetterBitSet target = left ? theLeft : theRight;
				if (helper.isReproducing()) {
					System.out.print("[" + i + "] " + (left ? "Left " : "Right "));
					System.out.flush();
				}
				helper.createAction()//
					.or(.6, () -> { // Flip a bit
						int index = helper.getInt(0, 1_000);
						boolean pre = target.get(index);
						if (helper.isReproducing())
							System.out.println("flipping " + index + " " + pre + "->" + (!pre));
						int preCard = target.cardinality();
						int setIndex = target.countBitsSetBetween(0, index);
						int clearIndex = index - setIndex;
						int prevSet = target.previousSetBit(index - 1);
						int nextSet = target.nextSetBit(index + 1);
						int prevClr = target.previousClearBit(index - 1);
						int nextClr = target.nextClearBit(index + 1);

						target.flip(index);

						Assert.assertEquals(preCard + (pre ? -1 : 1), target.cardinality());
						if (pre) {
							int newNextSet = target.nextSetBit(prevSet + 1);
							Assert.assertTrue(newNextSet < 0 || newNextSet > index);
							Assert.assertEquals(index, target.nextClearBit(prevClr + 1));
							int newIndex = target.indexOfNthSetBit(setIndex);
							Assert.assertTrue(newIndex < 0 || newIndex > index);
							if (nextSet >= 0)
								Assert.assertTrue(target.previousSetBit(nextSet - 1) - 1 < index);
							Assert.assertTrue(target.previousClearBit(nextClr - 1) == index);
							Assert.assertEquals(index, target.indexOfNthClearBit(clearIndex));
						} else {
							Assert.assertEquals(index, target.nextSetBit(prevSet + 1));
							if (nextSet < 0)
								Assert.assertTrue(target.size() > index);
							else
								Assert.assertEquals(index, target.previousSetBit(nextSet - 1));
							int newIndex = target.indexOfNthClearBit(clearIndex);
							Assert.assertTrue(newIndex > index);
							Assert.assertTrue(target.previousClearBit(nextClr - 1) < index);
							int newNextClr = target.nextClearBit(prevClr + 1);
							Assert.assertTrue(newNextClr < 0 || newNextClr > index);
							Assert.assertEquals(index, target.indexOfNthSetBit(setIndex));
						}
					})//
					.or(.2, () -> { // Insert interval
						int index, amount;
						if (helper.getBoolean(0.025)) { // Special case
							amount = helper.getInt(0, 12) * 8;
							if (helper.getBoolean(0.1)) // Ultra-special case
								index = helper.getInt(0, target.size() / 64) * 64;
							else
								index = helper.getInt(0, target.size());
						} else {
							index = helper.getInt(0, target.size());
							amount = helper.getInt(0, 100);
						}
						if (helper.isReproducing())
							System.out.println("inserting " + amount + "@" + index);
						int[] indexes = target.stream().toArray();
						for (int j = 0; j < indexes.length; j++) {
							if (indexes[j] >= index)
								indexes[j] += amount;
						}
						target.insertInterval(index, amount);
						int j = 0;
						for (int k = target.nextSetBit(0); k >= 0; k = target.nextSetBit(k + 1)) {
							Assert.assertEquals(indexes[j], k);
							j++;
						}
						Assert.assertEquals(indexes.length, j);
					})//
					.or(.2, () -> { // Remove interval
						int index, amount;
						if (helper.getBoolean(0.025)) { // Special case
							amount = helper.getInt(0, 12) * 8;
							if (helper.getBoolean(0.1)) // Ultra-special case
								index = helper.getInt(0, target.size() / 64) * 64;
							else
								index = helper.getInt(0, target.size());
						} else {
							index = helper.getInt(0, target.size());
							amount = helper.getInt(0, 100);
						}
						if (helper.isReproducing())
							System.out.println("removing " + amount + "@" + index);
						int[] indexes = target.stream()//
							.filter(ti -> ti < index || ti >= index + amount)//
							.map(ti -> ti < index ? ti : ti - amount)//
							.toArray();
						target.removeInterval(index, amount);
						int j = 0;
						for (int k = target.nextSetBit(0); k >= 0; k = target.nextSetBit(k + 1)) {
							Assert.assertEquals(indexes[j], k);
							j++;
						}
						Assert.assertEquals(indexes.length, j);
					})//
					.or(.1, () -> { // Set a bunch of bits at once
						int length = helper.getInt(0, 65);
						int index = helper.getInt(0, Math.max(100, target.length() - 50));
						long value = helper.getAnyLong();
						long expected = 0;
						long mask = 1;
						for (int j = 0; j < length; j++) {
							if (target.get(index + j))
								expected |= mask;
							mask <<= 1;
						}
						if (helper.isReproducing()) {
							StringBuilder str = new StringBuilder("bulk setting ");
							mask = 1L;
							for (int j = 0; j < length; j++) {
								str.append((value & mask) == 0 ? '0' : '1');
								mask <<= 1;
							}
							str.append('(').append(length).append(") @").append(index);
							System.out.println(str.toString());
						}
						long result = target.getAndSetBits(index, length, value);
						Assert.assertEquals(expected, result);
						mask = 1;
						for (int j = 0; j < length; j++) {
							boolean expectedBit = ((value & mask) != 0);
							Assert.assertEquals(expectedBit, target.get(index + j));
							mask <<= 1;
						}
					})//
					.execute("op");
				if (helper.isReproducing()) {
					System.out.println(
						"Left (" + theLeft.cardinality() + "):" + theLeft + "\n\t" + theLeft.printBits(null, " ", "\n\t", "\n\t\t"));
					System.out.println(
						"Right (" + theRight.cardinality() + "):" + theRight + "\n\t" + theRight.printBits(null, " ", "\n\t", "\n\t\t"));
				}
				helper.placemark();

				// Testing difference
				int leftIndex = theLeft.nextSetBit(0);
				int rightIndex = theRight.nextSetBit(0);
				int nextDifference = theLeft.nextDifference(theRight, 0);
				Assert.assertEquals(nextDifference < 0, theLeft.equals(theRight));
				while (leftIndex >= 0 && rightIndex >= 0) {
					helper.placemark();
					if (leftIndex == rightIndex) {
						Assert.assertTrue(nextDifference < 0 || leftIndex < nextDifference);
						leftIndex = theLeft.nextSetBit(leftIndex + 1);
						rightIndex = theRight.nextSetBit(rightIndex + 1);
					} else if (leftIndex < rightIndex) {
						Assert.assertEquals(leftIndex, nextDifference);
						leftIndex = theLeft.nextSetBit(leftIndex + 1);
						int lastDifference = nextDifference;
						nextDifference = theLeft.nextDifference(theRight, nextDifference + 1);
						if (nextDifference > 0)
							Assert.assertEquals(lastDifference, theLeft.previousDifference(theRight, nextDifference - 1));
					} else {
						Assert.assertEquals(rightIndex, nextDifference);
						rightIndex = theRight.nextSetBit(rightIndex + 1);
						int lastDifference = nextDifference;
						nextDifference = theLeft.nextDifference(theRight, nextDifference + 1);
						if (nextDifference > 0)
							Assert.assertEquals(lastDifference, theLeft.previousDifference(theRight, nextDifference - 1));
					}
				}

				// Test grabbing a bunch of bits at once
				int length = helper.getInt(0, Math.min(65, target.length()));
				int index = helper.getInt(0, target.length());
				long result = target.getBits(index, length, 0);
				long mask = 1;
				for (int j = 0; j < 64; j++) {
					boolean expectedBit = j < length ? target.get(index + j) : false;
					boolean resultBit = (result & mask) != 0;
					Assert.assertEquals(expectedBit, resultBit);
					mask <<= 1;
				}

				// Test navigation by modulus
				int divisor = helper.getInt(2, 16);
				int modulus = helper.getInt(0, divisor);
				int prev = 0;
				int next = target.nextSetBitMatching(0, divisor, modulus);
				for (int j = target.nextSetBit(0); j >= 0; j = target.nextSetBit(j + 1)) {
					if (j % divisor == modulus) {
						if (j != next) {
							target.nextSetBitMatching(prev + 1, divisor, modulus); // DEBUG
							Assert.assertEquals(j, next);
						}
						prev = j;
						next = target.nextSetBitMatching(j + 1, divisor, modulus);
					} else
						Assert.assertNotEquals(j, next);
				}
			}
		}
	}
}
