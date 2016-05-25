package org.qommons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class IterableUtilsTest {
	private List<String[]> thePaths;
	private List<String> theSentences;

	@Before
	public void preTest() {
		List<String[]> paths = new ArrayList<>();
		paths.add(new String[] { "This", "That" });
		paths.add(new String[] { "is", "isn't" });
		paths.add(new String[] { "the" });
		paths.add(new String[] { "stupidest", "smartest", "worst", "best" });
		paths.add(new String[] { "test", "unit test" });
		paths.add(new String[] { "ever" });
		paths.add(new String[] { "written", "coded", "thought of" });
		thePaths = Collections.unmodifiableList(paths);

		List<String> sentences = new ArrayList<>();
		addSentences(paths, 0, sentences, new StringBuilder());
		theSentences = Collections.unmodifiableList(sentences);
	}

	private void addSentences(List<String[]> paths, int pathIdx, List<String> sentences, StringBuilder sentence) {
		if (pathIdx == paths.size()) {
			sentences.add(sentence.toString().substring(0, sentence.length() - 1)); // Remove terminal space
			return;
		}
		int preLen = sentence.length();
		for (int i = 0; i < paths.get(pathIdx).length; i++) {
			sentence.append(paths.get(pathIdx)[i]).append(' ');
			addSentences(paths, pathIdx + 1, sentences, sentence);
			sentence.delete(preLen, sentence.length());
		}
	}

	@Test
	public void testCombine() {
		Iterable<String>[] pathIter = new Iterable[thePaths.size()];
		for (int i = 0; i < pathIter.length; i++) {
			pathIter[i] = Arrays.asList(thePaths.get(i));
		}

		Iterator<List<String>> paths = IterableUtils.combine(Arrays.asList(pathIter).iterator()).iterator();
		for (String sentence2 : theSentences) {
			assertTrue(paths.hasNext());
			String pathSentence = String.join(" ", paths.next());
			assertEquals(sentence2, pathSentence);
		}
		assertFalse(paths.hasNext());
	}
}
