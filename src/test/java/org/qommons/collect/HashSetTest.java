package org.qommons.collect;

import org.junit.Test;
import org.qommons.QommonsTestUtils;
import org.qommons.TestHelper;

/** Tests {@link BetterHashSet} and {@link BetterHashMap} */
public class HashSetTest {
	static class HashSetTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			QommonsTestUtils.testCollection(BetterHashSet.build().unsafe().buildSet(), null, null, helper);
		}
	}

	/** Tests {@link BetterHashSet} */
	@Test
	public void testHashSet() {
		TestHelper.createTester(HashSetTester.class).withDebug(false).withFailurePersistence(false).withRandomCases(1).execute();
	}

	/** Tests {@link BetterHashMap} */
	@Test
	public void testHashMap() {
		QommonsTestUtils.testMap(BetterHashMap.build().unsafe().buildMap(), null, null);
	}
}
