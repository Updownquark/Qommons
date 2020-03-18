package org.qommons.collect;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.QommonsTestUtils;
import org.qommons.TestHelper;
import org.qommons.collect.CollectionUtils.AdjustmentOrder;
import org.qommons.collect.CollectionUtils.ElementSyncAction;
import org.qommons.collect.CollectionUtils.ElementSyncInput;

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
			Map<String, Integer> originalLC = new HashMap<>();
			for (int i = 0; i < originalLength; i++) {
				String str = helper.getAlphaNumericString(3, 8);
				original.add(str);
				originalLC.putIfAbsent(str.toLowerCase(), i);
			}

			int adjustLength = helper.getInt(2, 15);
			List<String> adjust = new ArrayList<>(adjustLength);
			boolean add = helper.getBoolean(0.8);
			boolean remove = helper.getBoolean();
			boolean changeCase = helper.getBoolean();
			AdjustmentOrder order;
			// order = AdjustmentOrder.values()[helper.getInt(0, 3)]; // TODO Enable this when right-order is complete
			order = helper.getBoolean() ? CollectionUtils.AdjustmentOrder.LeftOrder : CollectionUtils.AdjustmentOrder.AddLast;
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
							while (originalLC.containsKey(str.toLowerCase()))
								str = helper.getAlphaNumericString(3, 8);
							return str;
						})//
						.or(1, () -> {
							int index = helper.getInt(0, originalLength);
							// If two strings in the original are equal (ignoring case) and we choose the second,
							// the algorithm will choose the first, so we need to account for this
							index = originalLC.get(original.get(index).toLowerCase());
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
			if (order != AdjustmentOrder.RightOrder) {
				boolean indexed = order == AdjustmentOrder.LeftOrder;
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

			boolean useUniversal = helper.getBoolean(.75);
			List<String> adjusting = new ArrayList<>(originalLength);
			class TestSync extends CollectionUtils.SimpleCollectionSynchronizer<String, String, RuntimeException, TestSync> {
				TestSync() {
					super(v -> v);
				}

				@Override
				public ElementSyncAction leftOnly(ElementSyncInput<String, String> element) {
					helper.placemark();
					Assert.assertEquals(original.get(element.getOriginalLeftIndex()), element.getLeftValue());
					if (!useUniversal)
						Assert.assertEquals(adjusting.get(element.getTargetIndex()), element.getLeftValue());
					Assert.assertEquals(-1, element.getRightIndex());
					if (remove && !useUniversal)
						adjusting.remove(element.getTargetIndex());
					return super.leftOnly(element);
				}

				@Override
				public ElementSyncAction rightOnly(ElementSyncInput<String, String> element) {
					helper.placemark();
					Assert.assertEquals(adjust.get(element.getRightIndex()), element.getRightValue());
					Assert.assertEquals(-1, element.getOriginalLeftIndex());
					if (add && !useUniversal) {
						if (order == AdjustmentOrder.AddLast) {
							Assert.assertEquals(-1, element.getTargetIndex());
							adjusting.add(element.getRightValue());
						} else
							adjusting.add(element.getTargetIndex(), element.getRightValue());
					}
					return super.rightOnly(element);
				}

				@Override
				public ElementSyncAction common(ElementSyncInput<String, String> element) {
					helper.placemark();
					Assert.assertEquals(original.get(element.getOriginalLeftIndex()), element.getLeftValue());
					if (order != AdjustmentOrder.RightOrder && !useUniversal)
						Assert.assertEquals(adjusting.get(element.getTargetIndex()), element.getLeftValue());
					Assert.assertEquals(adjust.get(element.getRightIndex()), element.getRightValue());
					Assert.assertTrue(element.getLeftValue().equalsIgnoreCase(element.getRightValue()));
					if (changeCase && !useUniversal)
						adjusting.set(element.getTargetIndex(), element.getRightValue());
					return super.common(element);
				}

				@Override
				public boolean getOrder(ElementSyncInput<String, String> element) {
					helper.placemark();
					Assert.assertEquals(original.get(element.getOriginalLeftIndex()), element.getLeftValue());
					Assert.assertEquals(-1, element.getTargetIndex());
					Assert.assertEquals(adjust.get(element.getRightIndex()), element.getRightValue());

					return super.getOrder(element);
				}

				@Override
				public ElementSyncAction universalLeftOnly(ElementSyncInput<String, String> element) {
					if (useUniversal)
						return super.universalLeftOnly(element);
					else
						return null;
				}

				@Override
				public ElementSyncAction universalRightOnly(ElementSyncInput<String, String> element) {
					if (useUniversal)
						return super.universalRightOnly(element);
					else
						return null;
				}

				@Override
				public ElementSyncAction universalCommon(ElementSyncInput<String, String> element) {
					if (useUniversal)
						return super.universalCommon(element);
					else
						return null;
				}
			}
			TestSync sync = new TestSync().withAdd(add).withRemove(remove).commonUses(!changeCase, false).leftFirst(leftFirst);
			boolean sorted = order != CollectionUtils.AdjustmentOrder.AddLast && helper.getBoolean();
			BiPredicate<String, String> equals;
			if (sorted) {
				equals = String::equalsIgnoreCase;
				original.sort(String::compareToIgnoreCase);
				adjust.sort(String::compareToIgnoreCase);
				expect.sort(String::compareToIgnoreCase);
				sync.withElementCompare(String::compareToIgnoreCase);
			} else
				equals = String::equals;
			adjusting.addAll(original);
			List<String> adjusted = new ArrayList<>(originalLength);
			adjusted.addAll(original);

			helper.placemark("test");
			CollectionUtils.synchronize(adjusted, adjust, (s1, s2) -> s1.equalsIgnoreCase(s2))//
				.adjust(sync, order);

			Assert.assertThat(adjusted, QommonsTestUtils.collectionsEqual(expect, true, equals));
			if (!useUniversal)
				Assert.assertThat(adjusting, QommonsTestUtils.collectionsEqual(expect, true, equals));
		}
	}
}
