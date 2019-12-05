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
	@SuppressWarnings("static-method")
	public void testHashSet() {
		TestHelper.createTester(HashSetTester.class).withDebug(false).withFailurePersistence(false).withRandomCases(1).execute()
			.throwErrorIfFailed();
	}

	/** Tests {@link BetterHashMap} */
	@Test
	@SuppressWarnings("static-method")
	public void testHashMap() {
		QommonsTestUtils.testMap(BetterHashMap.build().unsafe().buildMap(), null, null);
	}
}
