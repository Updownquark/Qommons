package org.qommons.collect;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.QommonsTestUtils;
import org.qommons.TestHelper;

/** Tests for {@link CollectionUtils} utilities */
public class CollectionsUtilsTests {
	/** Tests {@link CollectionUtils#synchronize(List, List) list synchronization} */
	@SuppressWarnings("static-method")
	@Test
	public void testCollectionAdjustment() {
		TestHelper.createTester(CollectionAdjustmentTester.class).revisitKnownFailures(true).withDebug(true).withFailurePersistence(true)//
			.withMaxTotalDuration(Duration.ofSeconds(1)).withPlacemarks("test")//
			.execute().throwErrorIfFailed();
	}

	static class CollectionAdjustmentTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			int originalLength = helper.getInt(2, 15);
			List<String> original = new ArrayList<>(originalLength);
			for (int i = 0; i < originalLength; i++)
				original.add(helper.getAlphaNumericString(3, 8));

			int adjustLength = helper.getInt(2, 15);
			List<String> adjust = new ArrayList<>(adjustLength);
			boolean add = helper.getBoolean(0.8);
			boolean remove = helper.getBoolean();
			boolean changeCase = helper.getBoolean();
			boolean indexed = helper.getBoolean(0.75);
			int[] map = new int[originalLength];
			int[] reverse = new int[adjustLength];
			Arrays.fill(map, -1);
			Arrays.fill(reverse, -1);
			for (int i = 0; i < adjustLength; i++) {
				int adjI = i;
				adjust.add(//
					helper.<String> createSupplier()//
						.or(1, () -> helper.getAlphaNumericString(3, 8))//
						.or(1, () -> {
							int index = helper.getInt(0, originalLength);
							if (map[index] < 0) {
								map[index] = adjI;
								reverse[adjI] = index;
							}
							return original.get(index).toLowerCase();
						})//
						.get(null));
			}

			List<String> expect = new ArrayList<>(originalLength + adjustLength);
			if (indexed && add) {
				for (int i = 0; i < adjustLength && reverse[i] < 0; i++)
					expect.add(adjust.get(i));
			}
			for (int i = 0; i < originalLength; i++) {
				if (map[i] >= 0) {
					if (changeCase)
						expect.add(adjust.get(map[i]));
					else
						expect.add(original.get(i));
					if (add && indexed) {
						for (int adjI = map[i] + 1; adjI < adjustLength && reverse[adjI] < 0; adjI++)
							expect.add(adjust.get(adjI));
					}
				} else if (!remove)
					expect.add(original.get(i));
			}
			if (add && !indexed) {
				for (int i = 0; i < adjustLength; i++) {
					if (reverse[i] < 0)
						expect.add(adjust.get(i));
				}
			}

			List<String> adjusted = new ArrayList<>(originalLength);
			adjusted.addAll(original);
			helper.placemark("test");
			CollectionUtils.synchronize(adjusted, adjust, (s1, s2) -> s1.equalsIgnoreCase(s2)).simple(v -> v)//
				.withAdd(add).withRemove(remove).commonUses(!changeCase, false)//
				.adjust(indexed);

			Assert.assertThat(adjusted, QommonsTestUtils.collectionsEqual(expect, true));
		}
	}
}
