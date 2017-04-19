package org.qommons;

import org.junit.Test;
import org.qommons.collect.CircularArrayList;

/** Tests {@link CircularArrayList} */
public class CircularListTest {
	@Test
	public void basicCALTest() {
		QommonsTestUtils.testCollection(new CircularArrayList<>(), null, null);
	}

	/* TODO Test:
	 * Max capacity
	 * Thread safeness
	 * 
	 * Add toArray and reversibility tests in QommonsTestUtils
	 */
}
