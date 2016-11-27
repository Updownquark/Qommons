package org.qommons;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

import org.junit.Test;

public class LinkedQueueTest {
	@Test
	public void testLinkedQueue() {
		LinkedQueue<Integer> queue = new LinkedQueue<>();
		queue.add(0);
		queue.add(1);
		assertEquals(asList(0, 1), new ArrayList<>(queue));

		queue.remove(0);
		assertEquals(asList(1), new ArrayList<>(queue));
	}
}
