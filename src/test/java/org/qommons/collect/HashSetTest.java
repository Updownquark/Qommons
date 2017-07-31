package org.qommons.collect;

import org.junit.Test;
import org.qommons.QommonsTestUtils;

public class HashSetTest {
	@Test
	public void testHashSet() {
		QommonsTestUtils.testCollection(BetterHashSet.build().unsafe().buildSet(), null, null);
	}
}
