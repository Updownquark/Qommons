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
			.withRandomCases(25).withMaxProgressInterval(Duration.ofMillis(500)).withMaxCaseDuration(Duration.ofSeconds(5))//
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
					.execute("op");
				if (helper.isReproducing())
					System.out.println(
						"Left (" + theLeft.cardinality() + "):" + theLeft + "\nRight (" + theRight.cardinality() + "):" + theRight);
				helper.placemark();
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
			}
		}
	}
}
