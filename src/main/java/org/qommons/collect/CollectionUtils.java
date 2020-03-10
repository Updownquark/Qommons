package org.qommons.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.function.BiPredicate;
import java.util.function.Function;

import org.qommons.ex.ExFunction;

/** A {@link Collection} utility class */
public class CollectionUtils {
	private CollectionUtils() {}

	/**
	 * Represents an element to be synchronized from one list to another
	 * 
	 * @param <E1> The type of the list to adjust
	 * @param <E2> The type of the list to synchronize against
	 */
	public interface ElementSyncInput<E1, E2> {
		/**
		 * @return The value of the element in the left represented by this element
		 * @throws NoSuchElementException If this element does not have a representation in the left list
		 */
		E1 getLeftValue() throws NoSuchElementException;

		/**
		 * @return The index of the element in the left list prior to any synchronization, or -1 if this element does not have a
		 *         representation in the left list
		 */
		int getOriginalLeftIndex();

		/**
		 * @return The value of the element in the right represented by this element
		 * @throws NoSuchElementException If this element does not have a representation in the right list
		 */
		E2 getRightValue() throws NoSuchElementException;

		/**
		 * @return The index of the element in the right list prior to any synchronization, or -1 if this element does not have a
		 *         representation in the right list
		 */
		int getRightIndex();

		/**
		 * @return The index in the left list (after any previous synchronization) corresponding to the current element, or at which an
		 *         added element would be inserted. Will be -1 for an index-less add.
		 */
		int getTargetIndex();

		/**
		 * Signals that the element in the left list should be kept with its original value. If this is used with a right-only element, the
		 * right-only value will not be added.
		 * 
		 * @return The preserve action
		 */
		ElementSyncAction preserve();

		/**
		 * Signals that the element in the left list should be removed. If this is used with a right-only element, it is equivalent to
		 * {@link #preserve()} and the right-only value will not be added.
		 * 
		 * @return The remove action
		 */
		ElementSyncAction remove();

		/**
		 * Signals that a left-only or common element's value should be replaced with the given value, or in the case of a right-only value,
		 * that the given value should be added.
		 * 
		 * @param newValue The value to replace or add
		 * @return The value replace/add action
		 */
		ElementSyncAction useValue(E1 newValue);
	}

	/**
	 * An action command from a {@link CollectionSynchronizerE synchronizer}, signalling what an {@link CollectionAdjustment adjustment}
	 * should do with an element
	 */
	public interface ElementSyncAction {}

	/**
	 * Controls the {@link CollectionAdjustment#adjust(CollectionSynchronizerE, boolean) synchronization} of each element of two lists
	 * 
	 * @param <E1> The type of the list to adjust
	 * @param <E2> The type of the list to synchronize against
	 * @param <X> An exception type that may be thrown by any of the element methods
	 */
	public interface CollectionSynchronizerE<E1, E2, X extends Throwable> {
		/**
		 * @param element The left-only element to handle
		 * @return The action to take on the element
		 * @throws X If the operation cannot be performed
		 */
		ElementSyncAction leftOnly(ElementSyncInput<E1, E2> element) throws X;

		/**
		 * @param element The right-only element to handle
		 * @return The action to take on the element
		 * @throws X If the operation cannot be performed
		 */
		ElementSyncAction rightOnly(ElementSyncInput<E1, E2> element) throws X;

		/**
		 * @param element The common element (present in both the left and right lists) to handle
		 * @return The action to take on the element
		 * @throws X If the operation cannot be performed
		 */
		ElementSyncAction common(ElementSyncInput<E1, E2> element) throws X;

		/**
		 * Allows this synchronizer to advertise that all left-only elements will be handled in a particular way regardless of value or
		 * position
		 * 
		 * @param element An empty element used to obtain an action
		 * @return An action that will be taken by this synchronizer on all left-only elements regardless of value or position, or null if
		 *         left-only element handling may vary
		 */
		default ElementSyncAction universalLeftOnly(ElementSyncInput<E1, E2> element) {
			return null;
		}

		/**
		 * Allows this synchronizer to advertise that all right-only elements will be handled in a particular way regardless of value or
		 * position
		 * 
		 * @param element An empty element used to obtain an action
		 * @return An action that will be taken by this synchronizer on all right-only elements regardless of value or position, or null if
		 *         right-only element handling may vary
		 */
		default ElementSyncAction universalRightOnly(ElementSyncInput<E1, E2> element) {
			return null;
		}

		/**
		 * Allows this synchronizer to advertise that all common elements will be handled in a particular way regardless of value or
		 * position
		 * 
		 * @param element An empty element used to obtain an action
		 * @return An action that will be taken by this synchronizer on all common elements regardless of value or position, or null if
		 *         common element handling may vary
		 */
		default ElementSyncAction universalCommon(ElementSyncInput<E1, E2> element) {
			return null;
		}
	}

	/**
	 * A {@link CollectionSynchronizerE} that cannot throw any checked exceptions
	 * 
	 * @param <E1> The type of the list to adjust
	 * @param <E2> The type of the list to synchronize against
	 */
	public interface CollectionSynchronizer<E1, E2> extends CollectionSynchronizerE<E1, E2, RuntimeException> {
		@Override
		ElementSyncAction leftOnly(ElementSyncInput<E1, E2> element);

		@Override
		ElementSyncAction rightOnly(ElementSyncInput<E1, E2> element);

		@Override
		ElementSyncAction common(ElementSyncInput<E1, E2> element);
	}

	/**
	 * A simple synchronizer (for any of the {@link CollectionUtils#synchronize(List, List, ElementFinder) synchronize} methods) whose
	 * left-only and right-only behaviors (i.e. whether an element will be in the left list) does not depend on element value or position.
	 * 
	 * @param <E1> The type of the list to adjust
	 * @param <E2> The type of the list to synchronize against
	 * @param <X> An exception type that may be thrown by the mapping function
	 * @param <S> Self parameter, making chain methods of this class easier with subclasses
	 */
	public static class SimpleCollectionSynchronizer<E1, E2, X extends Throwable, S extends SimpleCollectionSynchronizer<E1, E2, X, S>>
		implements CollectionSynchronizerE<E1, E2, X> {
		private static final ExFunction<ElementSyncInput<?, ?>, ElementSyncAction, RuntimeException> PRESERVE_COMMON = el -> el.preserve();

		private final ExFunction<? super E2, ? extends E1, ? extends X> theMap;
		private boolean isAdding;
		private boolean isRemoving;
		private ExFunction<? super ElementSyncInput<E1, E2>, ElementSyncAction, ? extends X> theCommonHandler;

		/** @param map The (exception-throwing) function to produce left-list values from right-list ones */
		public SimpleCollectionSynchronizer(ExFunction<? super E2, ? extends E1, ? extends X> map) {
			theMap = map;
			isAdding = map != null;
			isRemoving = true;
			if (map != null)
				commonUsesRight(false);
			else
				commonUsesLeft();
		}

		/**
		 * @param add Whether to add right-only elements to the left list
		 * @return This synchronizer
		 */
		public S withAdd(boolean add) {
			if (add && theMap == null)
				throw new IllegalStateException("A mapping must be specified for the synchronizer if right values are to be used");
			isAdding = add;
			return (S) this;
		}

		/**
		 * @param remove Whether to remove left-only elements
		 * @return This synchronizer
		 */
		public S withRemove(boolean remove) {
			isRemoving = remove;
			return (S) this;
		}

		/**
		 * Specifies that when common elements (in both the left and right lists) are encountered, the left value should be used with no
		 * update
		 * 
		 * @return This synchronizer
		 */
		public S commonUsesLeft() {
			theCommonHandler = (ExFunction<? super ElementSyncInput<E1, E2>, ElementSyncAction, ? extends X>) PRESERVE_COMMON;
			return (S) this;
		}

		/**
		 * Specifies that when common elements (in both the left and right lists) are encountered, the mapped right value should be used
		 * 
		 * @param update Whether to update the left value of common elements when the map of the right value is identical to the left value
		 * @return This synchronizer
		 */
		public S commonUsesRight(boolean update) {
			if (theMap == null)
				throw new IllegalStateException("A mapping must be specified for the synchronizer if right values are to be used");
			theCommonHandler = el -> {
				E1 mapped = theMap.apply(el.getRightValue());
				if (update || el.getLeftValue() != mapped)
					return el.useValue(mapped);
				else
					return el.preserve();
			};
			return (S) this;
		}

		/**
		 * @param left Whether to use the left or mapped right value when common elements (in both the left and right lists) are encountered
		 * @param update Whether to update the left value when the map of the right value is identical to the left value (if
		 *        <code>left==false</code>) or always (if <code>left==true</code>)
		 * @return This synchronizer
		 */
		public S commonUses(boolean left, boolean update) {
			if (left) {
				if (update) {
					theCommonHandler = el -> el.useValue(el.getLeftValue());
					return (S) this;
				} else
					return commonUsesLeft();
			} else
				return commonUsesRight(update);
		}

		/**
		 * Specifies that common elements (in both the left and right lists) should be removed
		 * 
		 * @return This synchronizer
		 */
		public S removeCommon() {
			theCommonHandler = null;
			return (S) this;
		}

		/**
		 * @param commonHandler Custom handler for common elements (in both the left and right lists)
		 * @return This synchronizer
		 */
		public S handleCommon(
			ExFunction<ElementSyncInput<E1, E2>, ElementSyncAction, ? extends X> commonHandler) {
			theCommonHandler = commonHandler;
			return (S) this;
		}

		@Override
		public ElementSyncAction leftOnly(ElementSyncInput<E1, E2> element) throws X {
			return isRemoving ? element.remove() : element.preserve();
		}

		@Override
		public ElementSyncAction rightOnly(ElementSyncInput<E1, E2> element) throws X {
			return isAdding ? element.useValue(theMap.apply(element.getRightValue())) : element.preserve();
		}

		@Override
		public ElementSyncAction common(ElementSyncInput<E1, E2> element) throws X {
			if (theCommonHandler == null)
				return element.remove();
			else
				return theCommonHandler.apply(element);
		}

		@Override
		public ElementSyncAction universalLeftOnly(ElementSyncInput<E1, E2> element) {
			return isRemoving ? element.remove() : element.preserve();
		}

		@Override
		public ElementSyncAction universalRightOnly(ElementSyncInput<E1, E2> element) {
			return isAdding ? null : element.preserve();
		}

		@Override
		public ElementSyncAction universalCommon(ElementSyncInput<E1, E2> element) {
			if (theCommonHandler == null)
				return element.remove();
			else if (theCommonHandler == PRESERVE_COMMON)
				return element.preserve();
			else
				return null;
		}
	}

	/**
	 * @param <E1> The type of the list to adjust
	 * @param <E2> The type of the list to synchronize against
	 * @param <X> An exception type that the mapping function may throw
	 * @param map The function to produce left-type values from right-type ones
	 * @return A simple synchronizer to configure
	 */
	public static <E1, E2, X extends Throwable> SimpleCollectionSynchronizer<E1, E2, X, ?> simpleSyncE(
		ExFunction<? super E2, ? extends E1, ? extends X> map) {
		return new SimpleCollectionSynchronizer<>(map);
	}

	/**
	 * @param <E1> The type of the list to adjust
	 * @param <E2> The type of the list to synchronize against
	 * @param map The function to produce left-type values from right-type ones
	 * @return A simple synchronizer to configure
	 */
	public static <E1, E2> SimpleCollectionSynchronizer<E1, E2, RuntimeException, ?> simpleSync(Function<? super E2, ? extends E1> map) {
		return simpleSyncE(e2 -> map.apply(e2));
	}

	/**
	 * Describes the "goals" of a synchronization operation and allows the user to do the adjustment
	 * 
	 * @param <E1> The type of the list to adjust
	 * @param <E2> The type of the list to synchronize against
	 */
	public interface CollectionAdjustment<E1, E2> {
		/** @return The number of elements in the right list that do not have a one-to-one mapping with an element in the left list */
		int getRightOnly();

		/** @return The number of elements in the left list that do not have a one-to-one mapping with an element in the right list */
		int getLeftOnly();

		/** @return The number of elements common between the left and right lists */
		int getCommon();

		/**
		 * <p>
		 * Synchronizes the content of the two lists.
		 * </p>
		 * <p>
		 * This method will, for each element in the left list, allow the {@link CollectionSynchronizerE synchronizer} to determine what to
		 * do with the element using either the {@link CollectionSynchronizerE#leftOnly(ElementSyncInput) leftOnly} or
		 * {@link CollectionSynchronizerE#common(ElementSyncInput) common} method, as appropriate. For each un-mapped element in the right
		 * list, the synchronizer will determine whether it should be added to the left list with the
		 * {@link CollectionSynchronizerE#rightOnly(ElementSyncInput) rightOnly} method.
		 * </p>
		 * <p>
		 * If the <code>indexedAdd</code> flag is false, all right-only additions will be attempted only after all left-only and common
		 * adjustments are made, via the index-less {@link List#add(Object)} method.
		 * </p>
		 * <p>
		 * If the <code>indexedAdd</code> flag is true, right-only content will be appended after the previous common element. E.g.
		 * synchronizing lists <br>
		 * <code>
		 * &nbsp;&nbsp;&nbsp;&nbsp;List<String> left=Arrays.asList("A", "B", "C");<br>
		 * &nbsp;&nbsp;&nbsp;&nbsp;List<String> right=Arrays.asList("D", "B", "E", "F", "C", "G");
		 * </code><br>
		 * with a simple synchronizer that preserves all left and content and adds all right content would produce a list with the sequence
		 * <br>
		 * <code>
		 * &nbsp;&nbsp;&nbsp;&nbsp;D, A, B, E, F, C, G</code><br>
		 * by inserting "D" at the beginning (because it has no previous common element); "E" and "F" immediately after "B", their previous
		 * common element; and "G" after "C", its previous common element.
		 * </p>
		 * 
		 * @param <X> The type of the exception that the synchronizer may throw
		 * @param sync The synchronizer to govern the adjustment
		 * @param indexedAdd Whether to insert right-only elements into the left list in order of occurrence, instead of using the
		 *        index-less {@link List#add(Object)} method. This parameter should be false if the left list may control the order of its
		 *        content (such as a {@link BetterSortedSet}) such that adding elements at a specified position might fail.
		 * @throws X If the synchronizer throws an exception
		 */
		<X extends Throwable> void adjust(CollectionSynchronizerE<E1, E2, X> sync, boolean indexedAdd) throws X;

		/**
		 * @param <X> An exception type that the mapping function may throw
		 * @param map A mapping from right to left values
		 * @return A SimpleAdjustment to configure
		 */
		default <X extends Throwable> SimpleAdjustment<E1, E2, X> simpleE(ExFunction<? super E2, ? extends E1, ? extends X> map) {
			return new SimpleAdjustment<>(this, map);
		}

		/**
		 * @param map A mapping from right to left values
		 * @return A SimpleAdjustment to configure
		 */
		default SimpleAdjustment<E1, E2, RuntimeException> simple(Function<? super E2, ? extends E1> map) {
			return simpleE(e2 -> map.apply(e2));
		}
	}

	/**
	 * A function to find values in a list
	 * 
	 * @param <E1> The type of the list to find values in
	 * @param <E2> The type of the values to find in the list
	 */
	public interface ElementFinder<E1, E2> {
		/**
		 * @param list The list to find the value in
		 * @param value The value to find
		 * @param after The index after which to find the value (strictly after)
		 * @return The index of the element in the list with given value, or -1 if the value could not be found after the given index
		 */
		int findElement(List<E1> list, E2 value, int after);
	}

	/**
	 * Produces a {@link CollectionAdjustment} for two lists
	 * 
	 * @param <E1> The type of the list to adjust
	 * @param <E2> The type of the list to synchronize against
	 * @param left The list to adjust
	 * @param right The list to synchronize against
	 * @param finder The function to find values from the right list in the left list
	 * @return An adjustment detailing the synchronization goals and the ability to do the adjustment
	 */
	public static <E1, E2> CollectionAdjustment<E1, E2> synchronize(List<E1> left, List<E2> right, ElementFinder<E1, ? super E2> finder) {
		int[] leftToRight = new int[left.size()];
		int[] rightToLeft = new int[right.size()];
		Arrays.fill(leftToRight, -1);
		Arrays.fill(rightToLeft, -1);
		int add = right.size(), remove = left.size(), common = 0;
		int rightIndex = 0;
		for (E2 r : right) {
			int leftIndex = -1;
			do {
				int newLeftIndex = finder.findElement(left, r, leftIndex);
				leftIndex = newLeftIndex < 0 ? -1 : leftIndex + 1 + newLeftIndex;
			} while (leftIndex >= 0 && leftToRight[leftIndex] >= 0);
			if (leftIndex >= 0) {
				add--;
				remove--;
				common++;
				rightToLeft[rightIndex] = leftIndex;
				leftToRight[leftIndex] = rightIndex;
			}
			rightIndex++;
		}

		return new AdjustmentImpl<>(left, right, leftToRight, rightToLeft, add, remove, common);
	}

	/**
	 * Produces a {@link CollectionAdjustment} for two lists of related types (via the left list's {@link List#indexOf(Object) indexOf}
	 * method, using {@link List#subList(int, int) sub-lists} for post-index search)
	 * 
	 * @param <E1> The type of the list to adjust
	 * @param <E2> The type of the list to synchronize against
	 * @param left The list to adjust
	 * @param right The list to synchronize against
	 * @return An adjustment detailing the synchronization goals and the ability to do the adjustment
	 */
	public static <E1, E2 extends E1> CollectionAdjustment<E1, E2> synchronize(List<E1> left, List<E2> right) {
		return synchronize(left, right, new ElementFinder<E1, E2>() {
			@Override
			public int findElement(List<E1> list, E2 value, int after) {
				List<E1> search = after < 0 ? list : list.subList(after + 1, list.size());
				return search.indexOf(value);
			}
		});
	}

	/**
	 * Produces a {@link CollectionAdjustment} for two lists
	 * 
	 * @param <E1> The type of the list to adjust
	 * @param <E2> The type of the list to synchronize against
	 * @param left The list to adjust
	 * @param right The list to synchronize against
	 * @param equals Tests values from the left and right lists for equality
	 * @return An adjustment detailing the synchronization goals and the ability to do the adjustment
	 */
	public static <E1, E2> CollectionAdjustment<E1, E2> synchronize(List<E1> left, List<E2> right,
		BiPredicate<? super E1, ? super E2> equals) {
		return synchronize(left, right, new ElementFinder<E1, E2>() {
			@Override
			public int findElement(List<E1> list, E2 value, int after) {
				int index = after + 1;
				ListIterator<E1> iter = list.listIterator(index);
				while (iter.hasNext()) {
					if (equals.test(iter.next(), value))
						return index;
					index++;
				}
				return -1;
			}
		});
	}

	/**
	 * A {@link CollectionAdjustment} look-alike that does not require an external synchronizer, but uses itself. Implementing The behavior
	 * of this adjustment can be configured using the {@link SimpleCollectionSynchronizer} methods.
	 * 
	 * @param <E1> The type of the list to adjust
	 * @param <E2> The type of the list to synchronize against
	 * @param <X> An exception type that may be thrown by the mapping function
	 */
	public static class SimpleAdjustment<E1, E2, X extends Throwable>
		extends SimpleCollectionSynchronizer<E1, E2, X, SimpleAdjustment<E1, E2, X>> {
		private final CollectionAdjustment<E1, E2> theAdjustment;

		SimpleAdjustment(CollectionAdjustment<E1, E2> adjustment, ExFunction<? super E2, ? extends E1, ? extends X> map) {
			super(map);
			theAdjustment = adjustment;
		}

		/**
		 * @return The number of elements in the right list that do not have a one-to-one mapping with an element in the left list
		 * @see CollectionAdjustment#getRightOnly()
		 */
		public int getRightOnly() {
			return theAdjustment.getRightOnly();
		}

		/**
		 * @return The number of elements in the left list that do not have a one-to-one mapping with an element in the right list
		 * @see CollectionAdjustment#getLeftOnly()
		 */
		public int getLeftOnly() {
			return theAdjustment.getLeftOnly();
		}

		/**
		 * @return The number of elements common between the left and right lists
		 * @see CollectionAdjustment#getCommon()
		 */
		public int getCommon() {
			return theAdjustment.getCommon();
		}

		/**
		 * Synchronizes the content of the two lists.
		 * 
		 * @see CollectionAdjustment#adjust(CollectionSynchronizerE, boolean)
		 * 
		 * @param indexedAdd Whether to insert right-only elements into the left list in order of occurrence, instead of using the
		 *        index-less {@link List#add(Object)} method. This parameter should be false if the left list may control the order of its
		 *        content (such as a {@link BetterSortedSet}) such that adding elements at a specified position might fail.
		 * @throws X If the mapping function (or {@link #handleCommon(ExFunction) custom common element handler}) throws an exception
		 */
		public void adjust(boolean indexedAdd) throws X {
			theAdjustment.adjust(this, indexedAdd);
		}
	}

	static class AdjustmentImpl<E1, E2> implements CollectionAdjustment<E1, E2> {
		private static final ElementSyncAction PRESERVE = new ElementSyncAction() {
			@Override
			public String toString() {
				return "PRESERVE";
			}
		};
		private static final ElementSyncAction REMOVE = new ElementSyncAction() {
			@Override
			public String toString() {
				return "REMOVE";
			}
		};
		private final List<E1> theLeft;
		private final List<E2> theRight;
		private final int[] leftToRight;
		private final int[] rightToLeft;
		private final int toAdd;
		private final int toRemove;
		private final int commonCount;
		private boolean adjusted;

		public AdjustmentImpl(List<E1> left, List<E2> right, int[] leftToRight, int[] rightToLeft, int add, int remove, int common) {
			theLeft = left;
			theRight = right;
			this.leftToRight = leftToRight;
			this.rightToLeft = rightToLeft;
			toAdd = add;
			toRemove = remove;
			commonCount = common;
		}

		@Override
		public int getRightOnly() {
			return toAdd;
		}

		@Override
		public int getLeftOnly() {
			return toRemove;
		}

		@Override
		public int getCommon() {
			return commonCount;
		}

		@Override
		public <X extends Throwable> void adjust(CollectionSynchronizerE<E1, E2, X> sync, boolean indexedAdd) throws X {
			if (adjusted)
				throw new IllegalStateException("Adjustment may only be done once");
			adjusted = true;
			SyncInputImpl<E1, E2> input = new SyncInputImpl<>();

			boolean hasAdds;
			if (toAdd == 0)
				hasAdds = false;
			else {
				ElementSyncAction rightAction = sync.universalRightOnly(input);
				hasAdds = rightAction != PRESERVE && rightAction != REMOVE;
			}
			boolean doLeftSync = true;
			if (leftToRight.length == 0)
				doLeftSync = false;
			else {
				ElementSyncAction leftAction = sync.universalLeftOnly(input);
				ElementSyncAction commonAction = sync.universalCommon(input);
				if (leftAction == commonAction) {
					if (leftAction == REMOVE) {
						doLeftSync = false;
						theLeft.clear();
					} else if (leftAction == PRESERVE && (!hasAdds || !indexedAdd))
						doLeftSync = false;
				}
				if (!doLeftSync) {//
				} else if (toRemove == leftToRight.length) {
					if (leftAction == REMOVE) {
						doLeftSync = false;
						theLeft.clear();
					} else if (leftAction == PRESERVE)
						doLeftSync = false;
				} else if (commonCount == leftToRight.length) {
					if (commonAction == REMOVE) {
						doLeftSync = false;
						theLeft.clear();
					} else if (commonAction == PRESERVE && (!hasAdds || !indexedAdd))
						doLeftSync = false;
				}
			}

			ListIterator<E1> leftIter = null;
			Iterator<E2> rightIter = null;
			int nextRightIndex = -1;
			if (indexedAdd && hasAdds && rightToLeft[0] < 0) {
				// Add initial values only present in the right list before any that map to an element in the left
				leftIter = theLeft.listIterator();
				rightIter = theRight.iterator();
				nextRightIndex = 0;
				input.hasRight = true;
				for (input.rightIndex = 0; input.rightIndex < rightToLeft.length && rightToLeft[input.rightIndex] < 0; input.rightIndex++) {
					input.rightVal = rightIter.next();
					nextRightIndex++;
					ElementSyncAction action = sync.rightOnly(input);
					if (action instanceof ValueSyncAction) {
						leftIter.add(((ValueSyncAction<E1>) action).value);
						input.targetIndex++;
					}
				}
				if (input.rightIndex == rightToLeft.length)
					hasAdds = false; // Already added everything, no more adds
			}
			if (doLeftSync) {
				if (leftIter == null)
					leftIter = theLeft.listIterator();

				input.hasLeft = true;
				input.targetIndex = 0;
				for (input.leftIndex = 0; input.leftIndex < leftToRight.length; input.leftIndex++) {
					input.leftVal = leftIter.next();
					input.rightIndex = leftToRight[input.leftIndex];
					ElementSyncAction action;
					if (input.rightIndex >= 0) {
						input.hasRight = true;
						if (input.rightIndex != nextRightIndex) {
							rightIter = theRight.listIterator(input.rightIndex);
							nextRightIndex = input.rightIndex;
						}
						input.rightVal = rightIter.next();
						nextRightIndex++;
						action = sync.common(input);
					} else {
						input.hasRight = false;
						action = sync.leftOnly(input);
					}
					if (action == REMOVE)
						leftIter.remove();
					else {
						if (action instanceof ValueSyncAction)
							leftIter.set(((ValueSyncAction<E1>) action).value);
						input.targetIndex++;
					}
					if (indexedAdd && hasAdds && input.hasRight) {
						// Add subsequent right-only values present in the right list before the next that maps to an element in the left
						for (input.rightIndex++; input.rightIndex < rightToLeft.length
							&& rightToLeft[input.rightIndex] < 0; input.rightIndex++) {
							input.rightVal = rightIter.next();
							action = sync.rightOnly(input);
							if (action instanceof ValueSyncAction) {
								leftIter.add(((ValueSyncAction<E1>) action).value);
								input.targetIndex++;
							}
						}
					}
					input.rightVal = null;
				}
			}

			if (hasAdds && (!indexedAdd || !doLeftSync)) {
				input.hasLeft = false;
				input.leftIndex = -1;
				input.leftVal = null;
				input.targetIndex = indexedAdd ? leftToRight.length : -1;
				input.hasRight = true;
				for (input.rightIndex = 0; input.rightIndex < rightToLeft.length; input.rightIndex++) {
					if (rightToLeft[input.rightIndex] < 0) {
						if (input.rightIndex != nextRightIndex) {
							rightIter = theRight.listIterator(input.rightIndex);
							nextRightIndex = input.rightIndex;
						}
						input.rightVal = rightIter.next();
						nextRightIndex++;
						ElementSyncAction action = sync.rightOnly(input);
						if (action instanceof ValueSyncAction) {
							theLeft.add(((ValueSyncAction<E1>) action).value);
							if (indexedAdd)
								input.targetIndex++;
						}
					}
				}
			}
		}

		static class ValueSyncAction<E1> implements ElementSyncAction {
			E1 value;

			@Override
			public String toString() {
				return "VALUE=" + value;
			}
		}

		static class SyncInputImpl<E1, E2> implements ElementSyncInput<E1, E2> {
			private final ValueSyncAction<E1> valueAction;

			boolean hasLeft = false;
			E1 leftVal;
			int leftIndex = -1;
			boolean hasRight = false;
			E2 rightVal;
			int rightIndex = -1;
			int targetIndex = -1;

			SyncInputImpl() {
				this.valueAction = new ValueSyncAction<>();
			}

			@Override
			public E1 getLeftValue() {
				if (!hasLeft)
					throw new NoSuchElementException();
				return leftVal;
			}

			@Override
			public int getOriginalLeftIndex() {
				return leftIndex;
			}

			@Override
			public E2 getRightValue() {
				if (!hasRight)
					throw new NoSuchElementException();
				return rightVal;
			}

			@Override
			public int getRightIndex() {
				return rightIndex;
			}

			@Override
			public int getTargetIndex() {
				return targetIndex;
			}

			@Override
			public ElementSyncAction preserve() {
				return PRESERVE;
			}

			@Override
			public ElementSyncAction remove() {
				return REMOVE;
			}

			@Override
			public ElementSyncAction useValue(E1 newValue) {
				valueAction.value = newValue;
				return valueAction;
			}
		}
	}
}
