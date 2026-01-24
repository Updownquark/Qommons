package org.qommons.collect;

import org.junit.Test;
import org.qommons.testing.QommonsTestUtils;
import org.qommons.testing.TestHelper;

/** Tests {@link SimpleDeque} */
public class SimpleDequeTest {
	static class SimpleDequeTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			QommonsTestUtils.testCollection(new SimpleDeque<>(),
				list -> list.checkValid(), null, helper);
		}
	}

	/**
	 * Runs the basic
	 * {@link QommonsTestUtils#testCollection(java.util.Collection, java.util.function.Consumer, java.util.function.Function, TestHelper)}
	 * collection test suite against a basic {@link SimpleDeque}.
	 */
	@Test
	@SuppressWarnings("static-method")
	public void simpleDequeTest() {
		TestHelper.createTester(SimpleDequeTester.class).withDebug(false).withFailurePersistence(true).revisitKnownFailures(true)
			.withDebug(true).withRandomCases(1).execute().throwErrorIfFailed();
	}
}
