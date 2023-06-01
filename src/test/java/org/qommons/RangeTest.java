package org.qommons;

import java.time.Duration;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.Range.RangeCompareResult;
import org.qommons.testing.TestHelper;
import org.qommons.testing.TestHelper.Testable;

/** Tests {@link Range} */
public class RangeTest {
	/** Performs the test */
	@SuppressWarnings("static-method")
	@Test
	public void testRange() {
		TestHelper.createTester(RangeTestable.class).revisitKnownFailures(true).withDebug(true).withFailurePersistence(true)
			.withPlacemarks("range").withMaxCaseDuration(Duration.ofSeconds(1)).withRandomCases(100).execute().throwErrorIfFailed();

	}

	private static class RangeTestable implements Testable {
		@Override
		public void accept(TestHelper helper) {
			Range<Integer> previous = null;
			for (int i = 0; i < 10_000; i++) {
				helper.placemark("range");
				Range.Bound<Integer> low;
				if (helper.getBoolean(0.05))
					low = Range.emptyBound();
				else
					low = Range.bound(helper.getAnyInt(), helper.getBoolean());
				Range.Bound<Integer> high;
				if (helper.getBoolean(0.01))
					high = low.clone();
				else if (helper.getBoolean(0.05))
					high = Range.emptyBound();
				else
					high = Range.bound(helper.getAnyInt(), helper.getBoolean());
				if (low.isPresent() && high.isPresent() && low.getValue() > high.getValue()) {
					Range.Bound<Integer> temp = low;
					low = high;
					high = temp;
				}
				Range<Integer> range = Range.of(low, high);

				for (int j = 0; j < 50; j++) {
					int test = helper.getAnyInt();
					boolean contained = range.contains(test);
					boolean shouldBeContained = true;
					if (range.getLowerBound().isPresent()) {
						int comp = Integer.compare(test, range.getLowerBound().getValue());
						if (comp < 0 || (comp == 0 && !range.getLowerBound().isClosed()))
							shouldBeContained = false;
					}
					if (shouldBeContained && range.getUpperBound().isPresent()) {
						int comp = Integer.compare(test, range.getUpperBound().getValue());
						if (comp > 0 || (comp == 0 && !range.getUpperBound().isClosed()))
							shouldBeContained = false;
					}
					Assert.assertEquals(shouldBeContained, contained);
				}

				if (previous != null) {
					RangeCompareResult compare = range.compareTo(previous);
					int lowComp = range.getLowerBound().compareTo(previous.getLowerBound(), Integer::compareTo);
					int highComp = range.getUpperBound().compareTo(previous.getUpperBound(), Integer::compareTo);
					if (compare.getLowCompare() <= 0)
						Assert.assertTrue(lowComp <= 0);
					else
						Assert.assertTrue(lowComp > 0);
					if (compare.getHighCompare() >= 0)
						Assert.assertTrue(highComp >= 0);
					else
						Assert.assertTrue(highComp < 0);
				}
				previous = range;
			}
		}
	}
}
