package org.qommons.collect;

import org.junit.Test;
import org.qommons.QommonsTestUtils;
import org.qommons.TestHelper;

/** Tests {@link BetterHashSet} and {@link BetterHashMap} */
public class HashSetTest {
	/** Tests {@link BetterHashSet} */
	@Test
	public void testHashSet() {
		TestHelper.testSingle(//
			helper -> QommonsTestUtils.testCollection(BetterHashSet.build().unsafe().buildSet(), null, null, helper), //
			1, -1);
	}

	/** Tests {@link BetterHashMap} */
	@Test
	public void testHashMap() {
		QommonsTestUtils.testMap(BetterHashMap.build().unsafe().buildMap(), null, null);
	}
}
