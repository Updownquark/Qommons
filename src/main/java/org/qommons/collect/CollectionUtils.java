package org.qommons.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
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
	 * Controls the {@link CollectionAdjustment#adjust(CollectionSynchronizerE, AdjustmentOrder) synchronization} of each element of two
	 * lists
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
		 * @param element The element representing an element from the left list, and one from the right list that is to be added
		 * @param mappedRight The left-typed value to be added for the right-only element
		 * @return Whether the left element should be preserved before the right element is added or vice versa
		 */
		boolean getOrder(ElementSyncInput<E1, E2> element, E1 mappedRight);

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
		private boolean isLeftFirst;
		private Comparator<? super E1> theCompare;
		private ExFunction<? super ElementSyncInput<E1, E2>, ElementSyncAction, ? extends X> theCommonHandler;

		/** @param map The (exception-throwing) function to produce left-list values from right-list ones */
		public SimpleCollectionSynchronizer(ExFunction<? super E2, ? extends E1, ? extends X> map) {
			theMap = map;
			isAdding = map != null;
			isRemoving = true;
			isLeftFirst = true;
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
		 * @param leftFirst Whether preserved left-only elements should appear before added right-only elements where the order is otherwise
		 *        unclear
		 * @return This synchronizer
		 */
		public S leftFirst(boolean leftFirst) {
			isLeftFirst = leftFirst;
			theCompare = null;
			return (S) this;
		}

		/**
		 * @param compare A comparator to determine whether a given preserved left-only element or an added right-only element should appear
		 *        first in the result
		 * @return This synchronizer
		 */
		public S withElementCompare(Comparator<? super E1> compare) {
			theCompare = compare;
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
		public boolean getOrder(ElementSyncInput<E1, E2> element, E1 mappedRight) {
			if (theCompare != null)
				return theCompare.compare(element.getLeftValue(), mappedRight) <= 0;
			else
				return isLeftFirst;
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

	/** The order that a {@link CollectionUtils#synchronize(List, List) synchronized} list should be in */
	public enum AdjustmentOrder {
		/** Preserves the order of the left list, inserting right-only elements as encountered */
		LeftOrder,
		/** Reorders the left list to be ordered like the right list */
		RightOrder,
		/**
		 * Preserves the order of the left list and inserts right-only elements into the left list using the index-less
		 * {@link List#add(Object)} method after all left-only and common elements have been synchronized. This order should be used if the
		 * left list may control the order of its content (such as a {@link BetterSortedSet}) such that adding elements at a specified
		 * position might fail.
		 */
		AddLast
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
		 * @param order The order for the adjusted list
		 * @throws X If the synchronizer throws an exception
		 */
		<X extends Throwable> void adjust(CollectionSynchronizerE<E1, E2, X> sync, AdjustmentOrder order) throws X;

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
				leftIndex = finder.findElement(left, r, leftIndex);
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
		private AdjustmentOrder theOrder;

		SimpleAdjustment(CollectionAdjustment<E1, E2> adjustment, ExFunction<? super E2, ? extends E1, ? extends X> map) {
			super(map);
			theAdjustment = adjustment;
			leftOrder();
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
		 * Sets the adjustment order to {@link AdjustmentOrder#LeftOrder left}
		 * 
		 * @return This adjustment
		 */
		public SimpleAdjustment<E1, E2, X> leftOrder() {
			theOrder = AdjustmentOrder.LeftOrder;
			return this;
		}

		/**
		 * Sets the adjustment order to {@link AdjustmentOrder#RightOrder right}
		 * 
		 * @return This adjustment
		 */
		public SimpleAdjustment<E1, E2, X> rightOrder() {
			theOrder = AdjustmentOrder.RightOrder;
			return this;
		}

		/**
		 * Sets the adjustment order to {@link AdjustmentOrder#AddLast add-last}
		 * 
		 * @return This adjustment
		 */
		public SimpleAdjustment<E1, E2, X> addLast() {
			theOrder = AdjustmentOrder.AddLast;
			return this;
		}

		/**
		 * @param order The order for this adjustment
		 * @return This adjustment
		 */
		public SimpleAdjustment<E1, E2, X> setOrder(AdjustmentOrder order) {
			theOrder = order;
			return this;
		}

		/** @return This adjustment's order */
		public AdjustmentOrder getOrder() {
			return theOrder;
		}

		/**
		 * Synchronizes the content of the two lists.
		 * 
		 * @see CollectionAdjustment#adjust(CollectionSynchronizerE, AdjustmentOrder)
		 * 
		 * @throws X If the mapping function (or {@link #handleCommon(ExFunction) custom common element handler}) throws an exception
		 */
		public void adjust() throws X {
			theAdjustment.adjust(this, theOrder);
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
		private final int rightOnly;
		private final int leftOnly;
		private final int commonCount;
		private boolean adjusted;

		public AdjustmentImpl(List<E1> left, List<E2> right, int[] leftToRight, int[] rightToLeft, int add, int remove, int common) {
			theLeft = left;
			theRight = right;
			this.leftToRight = leftToRight;
			this.rightToLeft = rightToLeft;
			rightOnly = add;
			leftOnly = remove;
			commonCount = common;
		}

		@Override
		public int getRightOnly() {
			return rightOnly;
		}

		@Override
		public int getLeftOnly() {
			return leftOnly;
		}

		@Override
		public int getCommon() {
			return commonCount;
		}

		@Override
		public <X extends Throwable> void adjust(CollectionSynchronizerE<E1, E2, X> sync, AdjustmentOrder order) throws X {
			if (adjusted)
				throw new IllegalStateException("Adjustment may only be done once");
			adjusted = true;
			new AdjustmentState()//
				.adjust(sync, order);
		}

		class AdjustmentState {
			SyncInputImpl<E1, E2> input;
			ListIterator<E1> leftIter;
			Iterator<E2> rightIter;
			boolean hasLeft;
			boolean hasRight;
			int leftIndex;
			int rightIndex;
			E1 leftVal;
			boolean updateLeft;
			E2 rightVal;
			E1 rightMapped;
			boolean leftOverstepped;
			int[] updatedLeftIndexes;

			<X extends Throwable> void adjust(CollectionSynchronizerE<E1, E2, X> sync, AdjustmentOrder order) throws X {
				input = new SyncInputImpl<>();
				leftIndex = rightIndex = -1;
				move(true, 0);
				move(false, 0);
				input.targetIndex++;
				switch (order) {
				case LeftOrder:
					while (hasLeft) {
						if (!hasRight || rightToLeft[rightIndex] >= 0 || (leftToRight[leftIndex] < 0 && compare(sync)))
							doElement(true, sync);
						else
							doElement(false, sync);
					}
					while (hasRight && rightToLeft[rightIndex] < 0)
						doElement(false, sync);
					break;
				case RightOrder:
					updatedLeftIndexes = new int[leftOnly + rightOnly + commonCount];
					for (int i = 0; i < leftToRight.length; i++)
						updatedLeftIndexes[i] = i;
					while (hasRight) {
						if (!hasLeft || leftToRight[leftIndex] >= 0 || !compare(sync))
							doElement(false, sync);
						else
							doElement(true, sync);
					}
					while (hasLeft && leftToRight[leftIndex] < 0)
						doElement(true, sync);
					break;
				case AddLast:
					while (hasLeft)
						doElement(true, sync);
					input.hasLeft = false;
					input.leftIndex = -1;
					input.leftVal = null;
					input.hasRight = true;
					input.targetIndex = -1;
					for (int right = 0; right < rightToLeft.length; right++) {
						if (rightToLeft[right] < 0) {
							move(false, right);
							input.rightIndex = right;
							input.rightVal = rightVal;
							ElementSyncAction action = sync.rightOnly(input);
							if (action instanceof ValueSyncAction)
								theLeft.add(((ValueSyncAction<E1>) action).value);
					}
				}
					break;
				}
			}

			private boolean compare(CollectionSynchronizerE<E1, E2, ?> sync) {
				input.hasLeft = input.hasRight = true;
				input.leftIndex = leftIndex;
				input.rightIndex = rightIndex;
				input.leftVal = leftVal;
				input.rightVal = rightVal;
				return sync.getOrder(input, rightMapped);
			}

			private <X extends Throwable> void doElement(boolean left, CollectionSynchronizerE<E1, E2, X> sync) throws X {
				if (left) {
					input.hasLeft = true;
					input.leftIndex = leftIndex;
					input.leftVal = leftVal;
					ElementSyncAction action;
					input.hasRight = leftToRight[leftIndex] >= 0;
					if (input.hasRight) {
						move(false, leftToRight[leftIndex]);
						input.rightIndex = rightIndex;
						input.rightVal = rightVal;
						action = sync.common(input);
					} else {
						input.rightIndex = -1;
						input.rightVal = null;
						action = sync.leftOnly(input);
					}
					if (action == PRESERVE) {//
						input.targetIndex++;
					} else if (action == REMOVE) {
						leftIter.remove();
						adjustLeftIndexes(false);
					} else if (action instanceof ValueSyncAction) {
						leftIter.set(((ValueSyncAction<E1>) action).value);
						input.targetIndex++;
					} else
						throw new IllegalArgumentException("Unrecognized action returned from synchronization: " + action);
					leftIndex++;
					hasLeft = leftIter.hasNext();
					if (hasLeft)
						leftVal = leftIter.next();
					if (input.hasRight) {
						rightIndex++;
						hasRight = rightIter.hasNext();
						if (hasRight)
							rightVal = rightIter.next();
					}
				} else {
					input.hasRight = true;
					input.rightIndex = rightIndex;
					input.rightVal = rightVal;
					ElementSyncAction action;
					input.hasLeft = rightToLeft[rightIndex] >= 0;
					if (input.hasLeft) {
						move(true, rightToLeft[rightIndex]);
						input.leftIndex = leftIndex;
						input.leftVal = leftVal;
						action = sync.common(input);
					} else {
						input.leftIndex = -1;
						input.leftVal = null;
						action = sync.rightOnly(input);
					}
					if (action == REMOVE || (action == PRESERVE && !input.hasLeft)) { // PRESERVE means do nothing for right-only
						if (input.hasLeft) {
							leftIter.remove();
							adjustLeftIndexes(false);
						}
					} else {
						move(true, leftIndex);
						E1 value;
						if (action == PRESERVE)
							value = leftVal;
						else
							value = ((ValueSyncAction<E1>) action).value;
						if (hasLeft) {
							leftIter.previous();
							leftIter.add(value);
							leftIter.next();
						} else
							leftIter.add(value);
						input.targetIndex++;
					}
					rightIndex++;
					hasRight = rightIter.hasNext();
					if (hasRight)
						rightVal = rightIter.next();
					if (input.hasLeft) {
						leftIndex++;
						hasLeft = leftIter.hasNext();
						if (hasLeft)
							leftVal = leftIter.next();
					}
				}
			}

			private boolean move(boolean left, int index) {
				if (left) {
					if (index == leftIndex)
						return false;
					if (index == 0)
						leftIter = theLeft.listIterator();
					else
						leftIter = theLeft.listIterator(index);
					leftIndex = index;
					hasLeft = leftIter.hasNext();
					if (hasLeft)
						leftVal = leftIter.next();
					else
						leftVal = null;
				} else {
					if (index == rightIndex)
						return false;
					if (index == 0)
						rightIter = theRight.iterator();
					else
						rightIter = theRight.listIterator(index);
					rightIndex = index;
					hasRight = rightIter.hasNext();
					if (hasRight)
						rightVal = rightIter.next();
					else
						rightVal = null;
				}
				return true;
			}

			private void adjustLeftIndexes(boolean add) {
				if (updatedLeftIndexes == null)
					return;
				// TODO Don't think this is right
				if (add) {
					for (int i = input.targetIndex + 1; i < updatedLeftIndexes.length; i++)
						updatedLeftIndexes[i] = updatedLeftIndexes[i - 1] + 1;
				} else {
					for (int i = input.targetIndex + 1; i < updatedLeftIndexes.length; i++)
						updatedLeftIndexes[i]--;
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
