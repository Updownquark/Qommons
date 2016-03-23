package org.qommons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.junit.Test;

public class IterableUtilsTest {
	@Test
	public void testCombine() {
		final String[][] path = new String[7][];
		path[0] = new String[] { "This", "That" };
		path[1] = new String[] { "is", "isn't" };
		path[2] = new String[] { "the" };
		path[3] = new String[] { "stupidest", "smartest", "worst", "best" };
		path[4] = new String[] { "test", "unit test" };
		path[5] = new String[] { "ever" };
		path[6] = new String[] { "written", "coded", "thought of" };
		ArrayList<String> sentences = new ArrayList<>();
		StringBuilder sentence = new StringBuilder();
		for (int i0 = 0; i0 < path[0].length; i0++) {
			sentence.append(path[0][i0]);
			for (int i1 = 0; i1 < path[1].length; i1++) {
				sentence.append(' ').append(path[1][i1]);
				for (int i2 = 0; i2 < path[2].length; i2++) {
					sentence.append(' ').append(path[2][i2]);
					for (int i3 = 0; i3 < path[3].length; i3++) {
						sentence.append(' ').append(path[3][i3]);
						for (int i4 = 0; i4 < path[4].length; i4++) {
							sentence.append(' ').append(path[4][i4]);
							for (int i5 = 0; i5 < path[5].length; i5++) {
								sentence.append(' ').append(path[5][i5]);
								for (int i6 = 0; i6 < path[6].length; i6++) {
									sentence.append(' ').append(path[6][i6]);
									sentences.add(sentence.toString());
									sentence.delete(sentence.length() - path[6][i6].length() - 1, sentence.length());
								}
								sentence.delete(sentence.length() - path[5][i5].length() - 1, sentence.length());
							}
							sentence.delete(sentence.length() - path[4][i4].length() - 1, sentence.length());
						}
						sentence.delete(sentence.length() - path[3][i3].length() - 1, sentence.length());
					}
					sentence.delete(sentence.length() - path[2][i2].length() - 1, sentence.length());
				}
				sentence.delete(sentence.length() - path[1][i1].length() - 1, sentence.length());
			}
			sentence.delete(sentence.length() - path[0][i0].length(), sentence.length());
		}

		Iterable<String>[] pathIter = new Iterable[path.length];
		for (int i = 0; i < pathIter.length; i++) {
			pathIter[i] = Arrays.asList(path[i]);
		}
		Iterator<String[]> paths = IterableUtils.combine(String.class, pathIter).iterator();
		for (String sentence2 : sentences) {
			assertTrue(paths.hasNext());
			String pathSentence = String.join(" ", paths.next());
			assertEquals(sentence2, pathSentence);
		}
		assertFalse(paths.hasNext());
	}
}
