package org.qommons;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.testing.TestHelper;

/** Tests for some of the methods in {@link QommonsUtils} */
public class QommonsUtilsTests {
	/** Tests {@link QommonsUtils#compareDoubleBits(long, long)} */
	@SuppressWarnings("static-method")
	@Test
	public void testDoubleBitCompare() {
		TestHelper.createTester(DoubleBitCompareTester.class).withFailurePersistence(true).revisitKnownFailures(true).withDebug(true)//
			.withMaxFailures(1).withRandomCases(10).execute().throwErrorIfFailed();
	}

	static class DoubleBitCompareTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper t) {
			for (int i = 0; i < 1_000_000; i++) {
				double v1 = t.getBoolean(0.01) ? (t.getBoolean() ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY) : t.getAnyDouble();
				double v2 = t.getBoolean(0.01) ? (t.getBoolean() ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY) : t.getAnyDouble();
				int comp = QommonsUtils.compareDoubleBits(//
					Double.doubleToLongBits(v1), Double.doubleToLongBits(v2));
				comp = comp < 0 ? -1 : (comp > 0 ? 1 : comp);
				int expectedComp;
				if (Double.isNaN(v1)) {
					if (Double.isNaN(v2))
						expectedComp = 0;
					else
						expectedComp = 1;
				} else if (Double.isNaN(v2))
					expectedComp = -1;
				else
					expectedComp = v1 < v2 ? -1 : (v1 > v2 ? 1 : 0);

				Assert.assertEquals(expectedComp, comp);
			}
		}
	}
}
