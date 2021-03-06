package org.qommons;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hamcrest.Description;
import org.hamcrest.Matcher;

/** Testing utilities */
public class QommonsTestUtils {
	/** The recursive depth to use to test dependent collections (e.g. sub lists and sub maps) */
	public static final int COLLECTION_TEST_DEPTH = 5;

	/**
	 * Runs a barrage of tests against a collection, observable or not
	 *
	 * @param <T> The type of the collection
	 * @param coll The collection to test
	 * @param check An optional check to run against the collection after every modification
	 * @param checkGenerator Generates additional checks against collections
	 */
	public static <T extends Collection<Integer>> void testCollection(T coll, Consumer<? super T> check,
			Function<? super T, Consumer<? super T>> checkGenerator) {
		testCollection(coll, check, checkGenerator, 0);
	}

	/**
	 * Runs a barrage of tests against a collection, observable or not
	 *
	 * @param <T> The type of the collection
	 * @param coll The collection to test
	 * @param check An optional check to run against the collection after every modification
	 * @param checkGenerator Generates additional checks against collections
	 * @param depth The current recursive testing depth
	 */
	public static <T extends Collection<Integer>> void testCollection(T coll, Consumer<? super T> check,
			Function<? super T, Consumer<? super T>> checkGenerator, int depth) {
		if (checkGenerator != null) {
			Consumer<? super T> moreCheck = checkGenerator.apply(coll);
			if (moreCheck != null) {
				Consumer<? super T> fCheck = check;
				check = v -> {
					if (fCheck != null)
						fCheck.accept(v);
					moreCheck.accept(v);
				};
			}
		}

		if (coll instanceof List)
			testList((List<Integer>) coll, (Consumer<? super List<Integer>>) check, depth);
		else if (coll instanceof NavigableSet)
			testSortedSet((NavigableSet<Integer>) coll, (Consumer<? super NavigableSet<Integer>>) check);
		else if (coll instanceof Set)
			testSet((Set<Integer>) coll, (Consumer<? super Set<Integer>>) check);
		else
			testBasicCollection(coll, check);
	}

	/**
	 * Runs a collection through a set of tests designed to ensure all {@link Collection} methods are functioning correctly
	 *
	 * @param <T> The type of the collection
	 * @param coll The collection to test
	 * @param check An optional function to apply after each collection modification to ensure the structure of the collection is correct
	 *        and potentially assert other side effects of collection modification
	 */
	private static <T extends Collection<Integer>> void testBasicCollection(T coll, Consumer<? super T> check) {
		// Most basic functionality, with iterator
		assertEquals(0, coll.size()); // Start with empty list
		assertTrue(coll.add(0)); // Test add
		assertEquals(1, coll.size()); // Test size
		if (check != null)
			check.accept(coll);
		Iterator<Integer> iter = coll.iterator(); // Test iterator
		assertEquals(true, iter.hasNext());
		assertEquals(0, (int) iter.next());
		assertEquals(false, iter.hasNext());
		iter = coll.iterator();
		assertEquals(true, iter.hasNext());
		assertEquals(0, (int) iter.next());
		iter.remove(); // Test iterator remove
		assertEquals(0, coll.size());
		if (check != null)
			check.accept(coll);
		assertEquals(false, iter.hasNext());
		iter = coll.iterator();
		assertEquals(false, iter.hasNext());
		assertTrue(coll.add(0));
		assertEquals(1, coll.size());
		assertFalse(coll.isEmpty());
		if (check != null)
			check.accept(coll);
		assertThat(coll, contains(0)); // Test contains
		assertTrue(coll.remove(0)); // Test remove
		assertFalse(coll.remove(0));
		assertTrue(coll.isEmpty()); // Test isEmpty
		if (check != null)
			check.accept(coll);
		assertThat(coll, not(contains(0)));

		ArrayList<Integer> toAdd = new ArrayList<>();
		for (int i = 0; i < 50; i++)
			toAdd.add(i);
		for (int i = 99; i >= 50; i--)
			toAdd.add(i);
		assertTrue(coll.addAll(toAdd)); // Test addAll
		assertEquals(100, coll.size());
		if (check != null)
			check.accept(coll);
		assertThat(coll, containsAll(0, 75, 50, 11, 99, 50)); // 50 twice. Test containsAll
		// 100 not in coll. 50 in list twice. Test removeAll.
		assertTrue(coll.removeAll(
				// Easier to debug this way
				asList(0, 50, 100, 10, 90, 20, 80, 30, 70, 40, 60, 50)));
		assertEquals(90, coll.size());
		if (check != null)
			check.accept(coll);

		ArrayList<Integer> copy = new ArrayList<>(coll); // More iterator testing
		assertThat(copy, collectionsEqual(coll, coll instanceof List));

		assertThat(coll, containsAll(1, 2, 11, 99));
		coll.retainAll(
				// Easier to debug this way
				asList(1, 51, 101, 11, 91, 21, 81, 31, 71, 41, 61, 51)); // 101 not in coll. 51 in list twice. Test retainAll.
		assertEquals(10, coll.size());
		if (check != null)
			check.accept(coll);
		assertThat(coll, not(containsAll(1, 2, 11, 99)));
		coll.clear(); // Test clear
		assertEquals(0, coll.size());
		if (check != null)
			check.accept(coll);
		assertThat(coll, not(contains(2)));
		// Leave the collection empty

		// Not testing toArray() methods. These are pretty simple, but should probably put those in some time.
		// TODO Test ReversibleCollections
	}

	private static <T extends Set<Integer>> void testSet(T set, Consumer<? super T> check) {
		testBasicCollection(set, check);

		assertTrue(set.add(0));
		assertEquals(1, set.size());
		if (check != null)
			check.accept(set);
		assertTrue(set.add(1));
		assertEquals(2, set.size());
		if (check != null)
			check.accept(set);
		assertFalse(set.add(0)); // Test uniqueness
		assertEquals(2, set.size());
		if (check != null)
			check.accept(set);
		assertTrue(set.remove(0));
		assertEquals(1, set.size());
		if (check != null)
			check.accept(set);
		set.clear();
		assertEquals(0, set.size());
		if (check != null)
			check.accept(set);
	}

	private static <T extends NavigableSet<Integer>> void testSortedSet(T set, Consumer<? super T> check) {
		testSet(set, coll -> {
			Comparator<? super Integer> comp = set.comparator();
			Integer last = null;
			for (Integer el : coll) {
				if (last != null)
					assertThat(el, greaterThanOrEqual(last, comp));
				last = el;
			}

			if (check != null)
				check.accept(coll);
		});

		// Test the special find methods of NavigableSet
		set.addAll(sequence(30, v -> v * 2, true));
		assertEquals(30, set.size());
		if (check != null)
			check.accept(set);
		assertEquals((Integer) 0, set.first());
		assertEquals((Integer) 58, set.last());
		assertEquals((Integer) 14, set.lower(16));
		assertEquals((Integer) 18, set.higher(16));
		assertEquals((Integer) 14, set.floor(15));
		assertEquals((Integer) 16, set.ceiling(15));
		assertEquals((Integer) 16, set.floor(16));
		assertEquals((Integer) 16, set.ceiling(16));
		assertEquals((Integer) 0, set.pollFirst());
		assertEquals(29, set.size());
		if (check != null)
			check.accept(set);
		assertEquals((Integer) 58, set.pollLast());
		assertEquals(28, set.size());
		if (check != null)
			check.accept(set);
		assertEquals((Integer) 2, set.pollFirst());
		assertEquals(27, set.size());
		if (check != null)
			check.accept(set);
		assertEquals((Integer) 56, set.pollLast());
		assertEquals(26, set.size());
		if (check != null)
			check.accept(set);

		Iterator<Integer> desc = set.descendingIterator(); // Test descendingIterator
		Integer last = null;
		while (desc.hasNext()) {
			Integer el = desc.next();
			if (last != null)
				assertThat(el, not(greaterThanOrEqual(last, set.comparator()))); // Strictly less than
			last = el;
		}

		// Test subsets
		Consumer<NavigableSet<Integer>> ssListener = ss -> {
			if (check != null)
				check.accept(set);
		};
		TreeSet<Integer> copy = new TreeSet<>(set);
		NavigableSet<Integer> subSet = (NavigableSet<Integer>) set.headSet(30);
		NavigableSet<Integer> copySubSet = (NavigableSet<Integer>) copy.headSet(30);
		assertThat(subSet, collectionsEqual(copySubSet, true));
		testSubSet(subSet, null, true, 30, false, ssListener);

		subSet = set.headSet(30, true);
		copySubSet = copy.headSet(30, true);
		assertThat(subSet, collectionsEqual(copySubSet, true));
		testSubSet(subSet, null, true, 30, true, ssListener);

		subSet = (NavigableSet<Integer>) set.tailSet(30);
		copySubSet = (NavigableSet<Integer>) copy.tailSet(30);
		assertThat(subSet, collectionsEqual(copySubSet, true));
		testSubSet(subSet, 30, true, null, true, ssListener);

		subSet = set.tailSet(30, false);
		copySubSet = copy.tailSet(30, false);
		assertThat(subSet, collectionsEqual(copySubSet, true));
		testSubSet(set.tailSet(30, false), 30, false, null, true, ssListener);

		ssListener.accept(set);

		subSet = (NavigableSet<Integer>) set.subSet(15, 45);
		copySubSet = (NavigableSet<Integer>) copy.subSet(15, 45);
		assertThat(subSet, collectionsEqual(copySubSet, true));
		testSubSet(subSet, 15, true, 45, false, ssListener);
	}

	private static void testSubSet(NavigableSet<Integer> subSet, Integer min, boolean minInclude, Integer max, boolean maxInclude,
			Consumer<? super NavigableSet<Integer>> check) {
		int startSize = subSet.size();
		int size = startSize;
		ArrayList<Integer> remove = new ArrayList<>();
		if (min != null) {
			if (minInclude) {
				if (!subSet.contains(min)) {
					remove.add(min);
					size++;
				}
				subSet.add(min);
				assertEquals(size, subSet.size());
				check.accept(subSet);
			}
			try {
				if (minInclude) {
					subSet.add(min - 1);
				} else
					subSet.add(min);
				assertTrue("SubSet should have thrown argument exception", false);
			} catch (IllegalArgumentException e) {
			}
		} else {
			subSet.add(Integer.MIN_VALUE);
			size++;
			assertEquals(size, subSet.size());
			check.accept(subSet);
		}
		if (max != null) {
			if (maxInclude) {
				if (!subSet.contains(max)) {
					remove.add(max);
					size++;
				}
				subSet.add(max);
				assertEquals(size, subSet.size());
				check.accept(subSet);
			}
			try {
				if (maxInclude)
					subSet.add(max + 1);
				else
					subSet.add(max);
				assertTrue("SubSet should have thrown argument exception", false);
			} catch (IllegalArgumentException e) {
			}
		} else {
			subSet.add(Integer.MAX_VALUE);
			size++;
			assertEquals(size, subSet.size());
			check.accept(subSet);
		}
		remove.add(Integer.MIN_VALUE);
		remove.add(Integer.MAX_VALUE);
		subSet.removeAll(remove);
		assertEquals(startSize, subSet.size());
		check.accept(subSet);
	}

	private static <T extends List<Integer>> void testList(T list, Consumer<? super T> check, int depth) {
		testBasicCollection(list, check);

		assertTrue(list.addAll(
				// Easier to debug this way
				sequence(10, null, false)));
		assertEquals(10, list.size());
		if (check != null)
			check.accept(list);
		assertTrue(list.add(0)); // Test non-uniqueness
		assertEquals(11, list.size());
		if (check != null)
			check.accept(list);
		assertEquals((Integer) 0, list.remove(10));
		assertEquals(10, list.size());
		if (check != null)
			check.accept(list);
		assertTrue(list.addAll(
				// Easier to debug this way
				sequence(10, v -> v + 20, false)));
		assertEquals(20, list.size());
		if (check != null)
			check.accept(list);
		assertTrue(list.addAll(10,
				// Easier to debug this way
				sequence(10, v -> v + 10, false))); // Test addAll at index
		assertEquals(30, list.size());
		if (check != null)
			check.accept(list);
		for (int i = 0; i < 30; i++)
			assertEquals((Integer) i, list.get(i)); // Test get

		// Test range checks
		try {
			list.remove(-1);
			assertTrue("List should have thrown out of bounds exception", false);
		} catch (IndexOutOfBoundsException e) {
		}
		try {
			list.remove(list.size());
			assertTrue("List should have thrown out of bounds exception", false);
		} catch (IndexOutOfBoundsException e) {
		}
		try {
			list.add(-1, 0);
			assertTrue("List should have thrown out of bounds exception", false);
		} catch (IndexOutOfBoundsException e) {
		}
		try {
			list.add(list.size() + 1, 0);
			assertTrue("List should have thrown out of bounds exception", false);
		} catch (IndexOutOfBoundsException e) {
		}
		try {
			list.set(-1, 0);
			assertTrue("List should have thrown out of bounds exception", false);
		} catch (IndexOutOfBoundsException e) {
		}
		try {
			list.set(list.size(), 0);
			assertTrue("List should have thrown out of bounds exception", false);
		} catch (IndexOutOfBoundsException e) {
		}

		for (int i = 0; i < 30; i++)
			assertEquals(i, list.indexOf(i)); // Test indexOf
		list.add(0);
		list.add(1);
		if (check != null)
			check.accept(list);
		assertEquals(0, list.indexOf(0)); // Test indexOf with duplicate values
		assertEquals(30, list.lastIndexOf(0)); // Test lastIndexOf
		list.remove(31);
		list.remove(30);
		for (int i = 0; i < 30; i++) {
			assertEquals("i=" + i, (Integer) i, list.set(i, 30 - i - 1)); // Test set
			if (check != null)
				check.accept(list);
		}
		assertEquals(30, list.size());
		for (int i = 0; i < 30; i++)
			assertEquals(30 - i - 1, list.indexOf(i));
		if (check != null)
			check.accept(list);
		for (int i = 0; i < 30; i++) {
			assertEquals((Integer) (30 - i - 1), list.set(i, 30 - i - 1));
			if (check != null)
				check.accept(list);
		}
		assertTrue(list.remove((Integer) 10));
		assertEquals(29, list.size());
		if (check != null)
			check.accept(list);
		list.add(10, 10); // Test add at index
		assertEquals(30, list.size());
		if (check != null)
			check.accept(list);

		{// This is here so I'm sure this part of the test is valid
			ArrayList<Integer> test00 = new ArrayList<>(list.size());
			for (int i = 0; i < list.size(); i++)
				test00.add(null);
			ArrayList<Integer> test0 = new ArrayList<>(sequence(30, null, false));
			ListIterator<Integer> listIter01 = test0.listIterator(test0.size() / 2);
			ListIterator<Integer> listIter02 = test0.listIterator(test0.size() / 2);
			while (true) {
				boolean stop = true;
				if (listIter01.hasPrevious()) {
					test00.set(listIter01.previousIndex(), listIter01.previous());
					stop = false;
				}
				if (listIter02.hasNext()) {
					test00.set(listIter02.nextIndex(), listIter02.next());
					stop = false;
				}
				if (stop)
					break;
			}
			assertThat(test00, equalTo(test0));
		}

		// Test listIterator
		ArrayList<Integer> test = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++)
			test.add(null);
		ListIterator<Integer> listIter1 = list.listIterator(list.size() / 2); // Basic bi-directional read-only functionality
		ListIterator<Integer> listIter2 = list.listIterator(list.size() / 2);
		while (true) {
			boolean stop = true;
			if (listIter1.hasPrevious()) {
				test.set(listIter1.previousIndex(), listIter1.previous());
				stop = false;
			}
			if (listIter2.hasNext()) {
				test.set(listIter2.nextIndex(), listIter2.next());
				stop = false;
			}
			if (stop)
				break;
		}
		assertThat(test, equalTo(list));

		// Test listIterator modification
		listIter1 = list.listIterator(list.size() / 2);
		listIter2 = test.listIterator(list.size() / 2);
		int i;
		for (i = 0; listIter2.hasPrevious(); i++) {
			assertTrue("On Iteration " + i, listIter1.hasPrevious());
			int prev = listIter1.previous();
			assertThat("On Iteration " + i, listIter2.previous(), equalTo(prev));
			switch (i % 5) {
			case 0:
				int toAdd = i * 17 + 100;
				listIter1.add(toAdd);
				listIter2.add(toAdd);
				assertTrue("On Iteration " + i, listIter1.hasPrevious());
				assertThat("On Iteration " + i, listIter1.previous(), equalTo(toAdd)); // Back up over the added value
				listIter2.previous();
				break;
			case 1:
				listIter1.remove();
				listIter2.remove();
				break;
			case 2:
				listIter1.set(prev + 50);
				listIter2.set(prev + 50);
				break;
			}
			assertThat("On Iteration " + i, list, collectionsEqual(test, true));
			if (check != null)
				check.accept(list);
		}
		for (i = 0; listIter2.hasNext(); i++) {
			assertTrue("On Iteration " + i, listIter1.hasNext());
			int next = listIter1.next();
			assertThat("On Iteration " + i, listIter2.next(), equalTo(next));
			switch (i % 5) {
			case 0:
				int toAdd = i * 53 + 1000;
				listIter1.add(toAdd);
				listIter2.add(toAdd);
				assertTrue("On Iteration " + i, listIter1.hasPrevious());
				assertThat("On Iteration " + i, toAdd, equalTo(listIter1.previous()));
				listIter1.next();
				break;
			case 1:
				listIter1.remove();
				listIter2.remove();
				break;
			case 2:
				listIter1.set(next + 1000);
				listIter2.set(next + 1000);
				break;
			}
			assertThat("On Iteration " + i, test, equalTo(list));
			if (check != null)
				check.accept(list);
		}

		list.clear();
		if (check != null)
			check.accept(list);
		if (depth + 1 < COLLECTION_TEST_DEPTH) {
			// Test subList
			list.addAll(sequence(30, null, false));
			if (check != null)
				check.accept(list);
			int subIndex = list.size() / 2;
			List<Integer> subList = list.subList(subIndex, subIndex + 5);
			assertEquals(5, subList.size());
			for (i = 0; i < subList.size(); i++)
				assertEquals((Integer) (subIndex + i), subList.get(i));
			i = 0;
			for (Integer el : subList)
				assertEquals((Integer) (subIndex + i++), el);
			subList.remove(0);
			assertEquals(4, subList.size());
			assertThat(list, not(contains(subIndex)));
			if (check != null)
				check.accept(list);
			subList.add(0, subIndex);
			assertThat(list, contains(subIndex));
			if (check != null)
				check.accept(list);
			try {
				subList.remove(-1);
				assertTrue("SubList should have thrown out of bounds exception", false);
			} catch (IndexOutOfBoundsException e) {
			}
			try {
				subList.remove(subList.size());
				assertTrue("SubList should have thrown out of bounds exception", false);
			} catch (IndexOutOfBoundsException e) {
			}
			assertEquals(30, list.size());
			assertEquals(5, subList.size());
			subList.clear();
			assertEquals(25, list.size());
			assertEquals(0, subList.size());
			if (check != null)
				check.accept(list);

			testCollection(subList, sl -> {
				assertEquals(list.size(), 25 + sl.size());
				for (int j = 0; j < list.size(); j++) {
					if (j < subIndex)
						assertEquals((Integer) j, list.get(j));
					else if (j < subIndex + sl.size())
						assertEquals(sl.get(j - subIndex), list.get(j));
					else
						assertEquals((Integer) (j - sl.size() + 5), list.get(j));
				}
				if (check != null)
					check.accept(list);
			} , null, depth + 1);
		}
		list.clear();
		assertEquals(0, list.size());
		if (check != null)
			check.accept(list);
	}

	/**
	 * Runs a barrage of tests against a map, observable or not
	 *
	 * @param <T> The type of the map
	 * @param map The map to test
	 * @param check An optional check to run against the map after every modification
	 * @param checkGenerator Generates additional checks against maps
	 */
	public static <T extends Map<Integer, Integer>> void testMap(T map, Consumer<? super T> check,
			Function<? super T, Consumer<? super T>> checkGenerator) {
		testMap(map, check, checkGenerator, 0);
	}

	/**
	 * Runs a barrage of tests against a map, observable or not
	 *
	 * @param <T> The type of the map
	 * @param map The map to test
	 * @param check An optional check to run against the map after every modification
	 * @param checkGenerator Generates additional checks against maps
	 * @param depth The current recursive testing depth
	 */
	public static <T extends Map<Integer, Integer>> void testMap(T map, Consumer<? super T> check,
			Function<? super T, Consumer<? super T>> checkGenerator, int depth) {
		if (checkGenerator != null) {
			Consumer<? super T> moreCheck = checkGenerator.apply(map);
			if (moreCheck != null) {
				Consumer<? super T> fCheck = check;
				check = v -> {
					if (fCheck != null)
						fCheck.accept(v);
					moreCheck.accept(v);
				};
			}
		}

		if (map instanceof NavigableMap)
			testSortedMap((NavigableMap<Integer, Integer>) map, (Consumer<? super NavigableMap<Integer, Integer>>) check);
		else
			testBasicMap(map, check);
	}

	/**
	 * Runs a map through a set of tests designed to ensure all {@link Map} methods are functioning correctly
	 *
	 * @param <T> The type of the map
	 * @param map The map to test
	 * @param check An optional function to apply after each map modification to ensure the structure of the map is correct and potentially
	 *        assert other side effects of map modification
	 */
	private static <T extends Map<Integer, Integer>> void testBasicMap(T map, Consumer<? super T> check) {
		// Most basic functionality, with iterator
		assertEquals(0, map.size()); // Start with empty map
		assertEquals(null, map.put(0, 1)); // Test put
		assertEquals(1, map.size()); // Test size
		if (check != null)
			check.accept(map);
		Iterator<Integer> iter = map.keySet().iterator(); // Test key iterator
		assertEquals(true, iter.hasNext());
		assertEquals(0, (int) iter.next());
		assertEquals(false, iter.hasNext());
		iter = map.keySet().iterator();
		assertEquals(true, iter.hasNext());
		assertEquals(0, (int) iter.next());
		iter.remove(); // Test iterator remove
		assertEquals(0, map.size());
		if (check != null)
			check.accept(map);
		assertEquals(false, iter.hasNext());
		iter = map.keySet().iterator();
		assertEquals(false, iter.hasNext());
		assertEquals(null, map.put(0, 1));
		assertEquals(1, map.size());
		assertFalse(map.isEmpty());
		if (check != null)
			check.accept(map);
		assertThat(map, containsKey(0)); // Test containsKey
		assertThat(map, containsValue(1)); // Test containsValue
		assertEquals(1, (int) map.get(0));
		assertEquals(1, (int) map.remove(0)); // Test remove
		assertEquals(null, map.remove(0));
		assertTrue(map.isEmpty()); // Test isEmpty
		if (check != null)
			check.accept(map);
		assertThat(map, not(containsKey(0)));
		assertThat(map, not(containsValue(1)));
		assertEquals(null, map.get(0));

		Map<Integer, Integer> toAdd = new HashMap<>();
		for (int i = 0; i < 50; i++)
			toAdd.put(i, i + 1);
		for (int i = 99; i >= 50; i--)
			toAdd.put(i, i + 1);
		map.putAll(toAdd); // Test putAll
		assertEquals(100, map.size());
		if (check != null)
			check.accept(map);
		assertThat(map.keySet(), containsAll(0, 75, 50, 11, 99, 50)); // 50 twice. Test containsAll
		// 100 not in map. 50 in list twice. Test removeAll.
		assertTrue(map.keySet().removeAll(
				// Easier to debug this way
				asList(0, 50, 100, 10, 90, 20, 80, 30, 70, 40, 60, 50)));
		assertEquals(90, map.size());
		if (check != null)
			check.accept(map);

		Map<Integer, Integer> copy = new HashMap<>(map); // More iterator testing
		assertThat(copy, mapsEqual(map, false));

		assertThat(map.keySet(), containsAll(1, 2, 11, 99));
		map.keySet().retainAll(
				// Easier to debug this way
				asList(1, 51, 101, 11, 91, 21, 81, 31, 71, 41, 61, 51)); // 101 not in map. 51 in list twice. Test retainAll.
		assertEquals(10, map.size());
		if (check != null)
			check.accept(map);
		assertThat(map.keySet(), not(containsAll(1, 2, 11, 99)));
		map.clear(); // Test clear
		assertEquals(0, map.size());
		if (check != null)
			check.accept(map);
		assertThat(map, not(containsKey(2)));
		// Leave the map empty

		assertEquals(null, map.put(0, 1));
		assertEquals(1, map.size());
		if (check != null)
			check.accept(map);
		assertEquals(null, map.put(1, 2));
		assertEquals(2, map.size());
		if (check != null)
			check.accept(map);
		assertEquals((Integer) 1, map.put(0, 2)); // Test uniqueness
		assertEquals((Integer) 2, map.put(0, 1));
		assertEquals(2, map.size());
		if (check != null)
			check.accept(map);
		assertEquals((Integer) 1, map.remove(0));
		assertEquals(1, map.size());
		if (check != null)
			check.accept(map);
		map.clear();
		assertEquals(0, map.size());
		if (check != null)
			check.accept(map);
	}

	private static <T extends NavigableMap<Integer, Integer>> void testSortedMap(T map, Consumer<? super T> check) {
		testBasicMap(map, coll -> {
			Comparator<? super Integer> comp = map.comparator();
			Integer last = null;
			for (Integer el : map.keySet()) {
				if (last != null)
					assertThat(el, greaterThanOrEqual(last, comp));
				last = el;
			}

			if (check != null)
				check.accept(coll);
		});

		// Test the special find methods of NavigableSet
		for (Integer v : sequence(30, v -> v * 2, true))
			map.put(v, v + 1);
		assertEquals(30, map.size());
		if (check != null)
			check.accept(map);
		assertEquals((Integer) 0, map.firstKey());
		assertEquals((Integer) 58, map.lastKey());
		assertEquals((Integer) 14, map.lowerKey(16));
		assertEquals((Integer) 18, map.higherKey(16));
		assertEquals((Integer) 14, map.floorKey(15));
		assertEquals((Integer) 16, map.ceilingKey(15));
		assertEquals((Integer) 16, map.floorKey(16));
		assertEquals((Integer) 16, map.ceilingKey(16));
		assertEquals((Integer) 0, map.pollFirstEntry().getKey());
		assertEquals(29, map.size());
		if (check != null)
			check.accept(map);
		assertEquals((Integer) 58, map.pollLastEntry().getKey());
		assertEquals(28, map.size());
		if (check != null)
			check.accept(map);
		assertEquals((Integer) 2, map.pollFirstEntry().getKey());
		assertEquals(27, map.size());
		if (check != null)
			check.accept(map);
		assertEquals((Integer) 56, map.pollLastEntry().getKey());
		assertEquals(26, map.size());
		if (check != null)
			check.accept(map);

		Iterator<Integer> desc = map.descendingKeySet().iterator(); // Test descendingIterator
		Integer last = null;
		while (desc.hasNext()) {
			Integer el = desc.next();
			if (last != null)
				assertThat(el, not(greaterThanOrEqual(last, map.comparator()))); // Strictly less than
			last = el;
		}

		// Test subsets
		Consumer<NavigableMap<Integer, Integer>> ssListener = ss -> {
			if (check != null)
				check.accept(map);
		};
		TreeMap<Integer, Integer> copy = new TreeMap<>(map);
		NavigableMap<Integer, Integer> subSet = (NavigableMap<Integer, Integer>) map.headMap(30);
		NavigableMap<Integer, Integer> copySubSet = (NavigableMap<Integer, Integer>) copy.headMap(30);
		assertThat(subSet, mapsEqual(copySubSet, true));
		testSubMap(subSet, null, true, 30, false, ssListener);

		subSet = map.headMap(30, true);
		copySubSet = copy.headMap(30, true);
		assertThat(subSet, mapsEqual(copySubSet, true));
		testSubMap(subSet, null, true, 30, true, ssListener);

		subSet = (NavigableMap<Integer, Integer>) map.tailMap(30);
		copySubSet = (NavigableMap<Integer, Integer>) copy.tailMap(30);
		assertThat(subSet, mapsEqual(copySubSet, true));
		testSubMap(subSet, 30, true, null, true, ssListener);

		subSet = map.tailMap(30, false);
		copySubSet = copy.tailMap(30, false);
		assertThat(subSet, mapsEqual(copySubSet, true));
		testSubMap(map.tailMap(30, false), 30, false, null, true, ssListener);

		subSet = (NavigableMap<Integer, Integer>) map.subMap(15, 45);
		copySubSet = (NavigableMap<Integer, Integer>) copy.subMap(15, 45);
		assertThat(subSet, mapsEqual(copySubSet, true));
		testSubMap(subSet, 15, true, 45, false, ssListener);
	}

	private static void testSubMap(NavigableMap<Integer, Integer> subMap, Integer min, boolean minInclude, Integer max, boolean maxInclude,
			Consumer<? super NavigableMap<Integer, Integer>> check) {
		int startSize = subMap.size();
		int size = startSize;
		ArrayList<Integer> remove = new ArrayList<>();
		if (min != null) {
			if (minInclude) {
				if (!subMap.containsKey(min)) {
					remove.add(min);
					size++;
				}
			}
			try {
				if (minInclude) {
					subMap.put(min - 1, 0);
				} else
					subMap.put(min, 0);
				assertTrue("SubSet should have thrown argument exception", false);
			} catch (IllegalArgumentException e) {
			}
		} else {
			subMap.put(Integer.MIN_VALUE, 0);
			size++;
			assertEquals(size, subMap.size());
			check.accept(subMap);
		}
		if (max != null) {
			if (maxInclude) {
				if (!subMap.containsKey(max)) {
					remove.add(max);
					size++;
				}
			}
			try {
				if (maxInclude)
					subMap.put(max + 1, 0);
				else
					subMap.put(max, 0);
				assertTrue("SubSet should have thrown argument exception", false);
			} catch (IllegalArgumentException e) {
			}
		} else {
			subMap.put(Integer.MAX_VALUE, -1);
			size++;
			assertEquals(size, subMap.size());
			check.accept(subMap);
		}
		remove.add(Integer.MIN_VALUE);
		remove.add(Integer.MAX_VALUE);
		for (Integer rem : remove)
			subMap.remove(rem);
		assertEquals(startSize, subMap.size());
		check.accept(subMap);
	}

	/**
	 * @param <T> The type of the value to check containment for
	 * @param value The value to check containment for
	 * @return A matcher that matches a collection if it contains the given value
	 */
	public static <T> Matcher<Collection<T>> contains(T value) {
		return new org.hamcrest.BaseMatcher<Collection<T>>() {
			@Override
			public boolean matches(Object arg0) {
				return ((Collection<T>) arg0).contains(value);
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("collection contains ").appendValue(value);
			}
		};
	}

	/**
	 * @param <T> The type of the values to check containment for
	 * @param values The values to check containment for
	 * @return A matcher that matches a collection if it contains all of the given values
	 */
	public static <T> Matcher<Collection<T>> containsAll(T... values) {
		return containsAll(asList(values));
	}

	/**
	 * @param <T> The type of the values to check containment for
	 * @param values The values to check containment for
	 * @return A matcher that matches a collection if it contains all of the given values
	 */
	public static <T> Matcher<Collection<T>> containsAll(Collection<T> values) {
		return new org.hamcrest.BaseMatcher<Collection<T>>() {
			@Override
			public boolean matches(Object arg0) {
				return ((Collection<T>) arg0).containsAll(values);
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("collection contains all of ").appendValue(values);
			}
		};
	}

	/**
	 * @param <T> The element type of the collection
	 * @param values The collection to test against
	 * @param ordered Whether to test the equality of the collections as if order matters
	 * @return A matcher that matches a collection c if c is equivalent to the given collection, in an ordered way if specified
	 */
	public static <T> Matcher<Collection<T>> collectionsEqual(Collection<T> values, boolean ordered) {
		return new org.hamcrest.BaseMatcher<Collection<T>>() {
			@Override
			public boolean matches(Object arg0) {
				Collection<T> arg = (Collection<T>) arg0;
				if (arg.size() != values.size())
					return false;
				if (ordered) {
					// Must be equivalent
					Iterator<T> vIter = values.iterator();
					Iterator<T> aIter = arg.iterator();
					while (vIter.hasNext() && aIter.hasNext())
						if (!Objects.equals(vIter.next(), aIter.next()))
							return false;
					if (vIter.hasNext() || aIter.hasNext())
						return false;
					return true;
				} else {
					// Split out here for debugging
					if (values.containsAll(arg))
						return true;
					else
						return false;
				}
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("collection equivalent to ").appendValue(values);
			}
		};
	}

	/**
	 * Creates a sequence of values to test against
	 *
	 * @param <T> The type of the sequence
	 * @param num The number of items for the sequence
	 * @param map The map to transform the sequence indexes into sequence values
	 * @param scramble Whether to scramble the sequence in a reproducible way
	 * @return The sequence
	 */
	public static <T> Collection<T> sequence(int num, Function<Integer, T> map, boolean scramble) {
		ArrayList<T> ret = new ArrayList<>();
		for (int i = 0; i < num; i++)
			ret.add(null);
		for (int i = 0; i < num; i++) {
			T value;
			if (map != null)
				value = map.apply(i);
			else
				value = (T) (Integer) i;
			int index = i;
			if (scramble) {
				switch (i % 3) {
				case 0:
					index = i;
					break;
				case 1:
					index = i + 3;
					if (index >= num)
						index = 1;
					break;
				default:
					index = ((num / 3) - (i / 3) - 1) * 3 + 2;
					break;
				}
			}
			ret.set(index, value);
		}

		return Collections.unmodifiableCollection(ret);
	}

	/**
	 * @param <T> The value type
	 * @param value The value to compare against
	 * @param comp The comparator to do the comparison
	 * @return A matcher that matches a value v if v is greater than or equal to the <code>value</code>.
	 */
	public static <T> Matcher<T> greaterThanOrEqual(T value, Comparator<? super T> comp) {
		return new org.hamcrest.BaseMatcher<T>() {
			@Override
			public boolean matches(Object arg0) {
				return comp.compare((T) arg0, value) >= 1;
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("value is not greater than " + value);
			}
		};
	}

	/**
	 * @param <T> The type of the key to check containment for
	 * @param value The key to check containment for
	 * @return A matcher that matches a map if it contains the given key
	 */
	public static <T> Matcher<Map<T, ?>> containsKey(T value) {
		return new org.hamcrest.BaseMatcher<Map<T, ?>>() {
			@Override
			public boolean matches(Object arg0) {
				return ((Map<T, ?>) arg0).containsKey(value);
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("collection contains ").appendValue(value);
			}
		};
	}

	/**
	 * @param <T> The type of the value to check containment for
	 * @param value The value to check containment for
	 * @return A matcher that matches a map if it contains the given value
	 */
	public static <T> Matcher<Map<?, T>> containsValue(T value) {
		return new org.hamcrest.BaseMatcher<Map<?, T>>() {
			@Override
			public boolean matches(Object arg0) {
				return ((Map<?, T>) arg0).containsValue(value);
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("collection contains ").appendValue(value);
			}
		};
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param values The map to test against
	 * @param ordered Whether to test the equality of the map as if order matters
	 * @return A matcher that matches a map m if m is equivalent to the given map, in an ordered way if specified
	 */
	public static <K, V> Matcher<Map<K, V>> mapsEqual(Map<K, V> values, boolean ordered) {
		Matcher<Collection<K>> keyMatcher = collectionsEqual(values.keySet(), ordered);
		return new org.hamcrest.BaseMatcher<Map<K, V>>() {
			@Override
			public boolean matches(Object arg0) {
				Map<K, V> arg = (Map<K, V>) arg0;
				if (!keyMatcher.matches(arg.keySet()))
					return false;
				for (Map.Entry<K, V> entry : values.entrySet())
					if (!Objects.equals(entry.getValue(), arg.get(entry.getKey())))
						return false;
				return true;
			}

			@Override
			public void describeTo(Description arg0) {
				arg0.appendText("map equivalent to ").appendValue(values);
			}
		};
	}
}
