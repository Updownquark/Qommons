package org.qommons.collect;

import org.junit.Test;
import org.qommons.QommonsTestUtils;

/** Tests {@link BetterHashSet} and {@link BetterHashMap} */
public class HashSetTest {
	/** Tests {@link BetterHashSet} */
	@Test
	public void testHashSet() {
		QommonsTestUtils.testCollection(BetterHashSet.build().unsafe().buildSet(), null, null);
	}

	/** Tests {@link BetterHashMap} */
	@Test
	public void testHashMap() {
		QommonsTestUtils.testMap(BetterHashMap.build().unsafe().buildMap(), null, null);
	}
}
