package org.qommons.collect;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.QommonsTestUtils;
import org.qommons.TestHelper;

/** Tests for {@link CollectionUtils} utilities */
public class CollectionUtilsTests {
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
			Set<String> originalLC = new HashSet<>();
			for (int i = 0; i < originalLength; i++) {
				String str = helper.getAlphaNumericString(3, 8);
				original.add(str);
				originalLC.add(str.toLowerCase());
			}

			int adjustLength = helper.getInt(2, 15);
			List<String> adjust = new ArrayList<>(adjustLength);
			boolean add = helper.getBoolean(0.8);
			boolean remove = helper.getBoolean();
			boolean changeCase = helper.getBoolean();
			CollectionUtils.AdjustmentOrder order = CollectionUtils.AdjustmentOrder.values()[helper.getInt(0, 3)];
			boolean leftFirst = helper.getBoolean();
			int[] map = new int[originalLength];
			int[] reverse = new int[adjustLength];
			Arrays.fill(map, -1);
			Arrays.fill(reverse, -1);
			for (int i = 0; i < adjustLength; i++) {
				int adjI = i;
				adjust.add(//
					helper.<String> createSupplier()//
						.or(1, () -> {
							String str = helper.getAlphaNumericString(3, 8);
							while (originalLC.contains(str.toLowerCase()))
								str = helper.getAlphaNumericString(3, 8);
							return str;
						})//
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
			int i = 0, j = 0;
			if (order != CollectionUtils.AdjustmentOrder.RightOrder) {
				boolean indexed = order == CollectionUtils.AdjustmentOrder.LeftOrder;
				for (; i < originalLength; i++) {
					if (map[i] >= 0) {
						if (indexed && add) {
							for (; j < adjustLength && reverse[j] < 0; j++)
								expect.add(adjust.get(j));
						}
						j = map[i];
						if (changeCase)
							expect.add(adjust.get(j));
						else
							expect.add(original.get(i));
						j++;
					} else {
						if (indexed && add && !leftFirst) {
							for (; j < adjustLength && reverse[j] < 0; j++)
								expect.add(adjust.get(j));
						}
						if (!remove)
							expect.add(original.get(i));
					}
				}
				if (add) {
					if (!indexed)
						j = 0;
					for (; j < adjustLength; j++) {
						if (reverse[j] < 0)
							expect.add(adjust.get(j));
						else if (indexed)
							break;
					}
				}
			} else {
				for (; j < adjustLength; j++) {
					if (reverse[j] >= 0) {
						if (!remove) {
							for (; i < originalLength && map[i] < 0; i++)
								expect.add(original.get(i));
						}
						i = reverse[j];
						if (changeCase)
							expect.add(adjust.get(j));
						else
							expect.add(original.get(i));
						i++;
					} else {
						if (!remove && leftFirst) {
							for (; i < originalLength && map[i] < 0; i++)
								expect.add(original.get(i));
						}
						if (add)
							expect.add(adjust.get(j));
					}
				}
				if (!remove) {
					for (; i < originalLength; i++) {
						if (map[i] < 0)
							expect.add(original.get(i));
						else
							break;
					}
				}
			}

			List<String> adjusted = new ArrayList<>(originalLength);
			adjusted.addAll(original);
			helper.placemark("test");
			CollectionUtils.synchronize(adjusted, adjust, (s1, s2) -> s1.equalsIgnoreCase(s2)).simple(v -> v)//
				.withAdd(add).withRemove(remove).commonUses(!changeCase, false).leftFirst(leftFirst).setOrder(order)//
				.adjust();

			Assert.assertThat(adjusted, QommonsTestUtils.collectionsEqual(expect, true));
		}
	}
}
