package org.qommons.collect;

import org.junit.Test;
import org.qommons.QommonsTestUtils;
import org.qommons.TestHelper;

/** Tests {@link BetterHashSet} and {@link BetterHashMap} */
public class HashSetTest {
	/** Tests {@link BetterHashSet} */
	@Test
	@SuppressWarnings("static-method")
	public void testHashSet() {
		TestHelper.createTester(HashSetTester.class).withDebug(true).withFailurePersistence(true).withRandomCases(1).execute()
			.throwErrorIfFailed();
	}

	static class HashSetTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			QommonsTestUtils.testCollection(BetterHashSet.build().build(), null, null, helper);
		}
	}

	/** Tests {@link BetterHashMap} */
	@Test
	@SuppressWarnings("static-method")
	public void testHashMap() {
		TestHelper.createTester(HashMapTester.class).withDebug(true).withFailurePersistence(true).withRandomCases(1).execute()
			.throwErrorIfFailed();
	}

	static class HashMapTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			QommonsTestUtils.testMap(BetterHashMap.build().build(), null, null);
		}
	}
}
