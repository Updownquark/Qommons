package org.qommons.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.ex.ExConsumer;
import org.qommons.ex.ExFunction;

/** A {@link Collection} utility class */
public class CollectionUtils {
	private CollectionUtils() {}

	/**
	 * Represents an element to be synchronized from one list to another
	 * 
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 */
	public interface ElementSyncInput<L, R> {
		/**
		 * @return The value of the element in the left represented by this element
		 * @throws NoSuchElementException If this element does not have a representation in the left list
		 */
		L getLeftValue() throws NoSuchElementException;

		/**
		 * @return The index of the element in the left list prior to any synchronization, or -1 if this element does not have a
		 *         representation in the left list
		 */
		int getOriginalLeftIndex();

		/**
		 * @return The value of the element in the right represented by this element
		 * @throws NoSuchElementException If this element does not have a representation in the right list
		 */
		R getRightValue() throws NoSuchElementException;

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
		ElementSyncAction useValue(L newValue);
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
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 * @param <X> An exception type that may be thrown by any of the element methods
	 */
	public interface CollectionSynchronizerE<L, R, X extends Throwable> {
		/**
		 * @param element The left-only element to handle
		 * @return The action to take on the element
		 * @throws X If the operation cannot be performed
		 */
		ElementSyncAction leftOnly(ElementSyncInput<L, R> element) throws X;

		/**
		 * @param element The right-only element to handle
		 * @return The action to take on the element
		 * @throws X If the operation cannot be performed
		 */
		ElementSyncAction rightOnly(ElementSyncInput<L, R> element) throws X;

		/**
		 * @param element The common element (present in both the left and right lists) to handle
		 * @return The action to take on the element
		 * @throws X If the operation cannot be performed
		 */
		ElementSyncAction common(ElementSyncInput<L, R> element) throws X;

		/**
		 * @param element The element representing an element from the left list, and one from the right list that is to be added
		 * @return Whether the left element should be preserved before the right element is added or vice versa
		 * @throws X If the operation cannot be performed
		 */
		boolean getOrder(ElementSyncInput<L, R> element) throws X;

		/**
		 * Allows this synchronizer to advertise that all left-only elements will be handled in a particular way regardless of value or
		 * position
		 * 
		 * @param element An empty element used to obtain an action
		 * @return An action that will be taken by this synchronizer on all left-only elements regardless of value or position, or null if
		 *         left-only element handling may vary
		 */
		default ElementSyncAction universalLeftOnly(ElementSyncInput<L, R> element) {
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
		default ElementSyncAction universalRightOnly(ElementSyncInput<L, R> element) {
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
		default ElementSyncAction universalCommon(ElementSyncInput<L, R> element) {
			return null;
		}
	}

	/**
	 * A {@link CollectionSynchronizerE} that cannot throw any checked exceptions
	 * 
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 */
	public interface CollectionSynchronizer<L, R> extends CollectionSynchronizerE<L, R, RuntimeException> {
		@Override
		ElementSyncAction leftOnly(ElementSyncInput<L, R> element);

		@Override
		ElementSyncAction rightOnly(ElementSyncInput<L, R> element);

		@Override
		ElementSyncAction common(ElementSyncInput<L, R> element);
	}

	/**
	 * A simple synchronizer (for any of the {@link CollectionUtils#synchronize(List, List, ElementFinder) synchronize} methods) whose
	 * left-only and right-only behaviors (i.e. whether an element will be in the left list) does not depend on element value or position.
	 * 
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 * @param <X> An exception type that may be thrown by the mapping function
	 * @param <S> Self parameter, making chain methods of this class easier with subclasses
	 */
	public static class SimpleCollectionSynchronizer<L, R, X extends Throwable, S extends SimpleCollectionSynchronizer<L, R, X, S>>
		implements CollectionSynchronizerE<L, R, X> {
		private static final ExFunction<ElementSyncInput<?, ?>, ElementSyncAction, RuntimeException> PRESERVE_COMMON = el -> el.preserve();

		private final ExFunction<? super R, ? extends L, ? extends X> theMap;
		private boolean isAdding;
		private boolean isRemoving;
		private boolean isLeftFirst;
		private Comparator<? super L> theCompare;
		private ExFunction<? super ElementSyncInput<L, R>, ElementSyncAction, ? extends X> theCommonHandler;
		private ExConsumer<ElementSyncInput<L, R>, ? extends X> theLeftListener;
		private ExConsumer<ElementSyncInput<L, R>, ? extends X> theRightListener;
		private ExConsumer<ElementSyncInput<L, R>, ? extends X> theCommonListener;

		/** @param map The (exception-throwing) function to produce left-list values from right-list ones */
		public SimpleCollectionSynchronizer(ExFunction<? super R, ? extends L, ? extends X> map) {
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
		public S withElementCompare(Comparator<? super L> compare) {
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
			theCommonHandler = (ExFunction<? super ElementSyncInput<L, R>, ElementSyncAction, ? extends X>) PRESERVE_COMMON;
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
				L mapped = theMap.apply(el.getRightValue());
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
			ExFunction<ElementSyncInput<L, R>, ElementSyncAction, ? extends X> commonHandler) {
			theCommonHandler = commonHandler;
			return (S) this;
		}

		/**
		 * @param onLeft The listener to be notified for each left-only element encountered in the adjustment
		 * @return This synchronizer
		 */
		public S onLeftX(ExConsumer<ElementSyncInput<L, R>, ? extends X> onLeft) {
			theLeftListener = onLeft;
			return (S) this;
		}

		/**
		 * @param onLeft The listener to be notified for each left-only element encountered in the adjustment
		 * @return This synchronizer
		 */
		public S onLeft(Consumer<ElementSyncInput<L, R>> onLeft) {
			return onLeftX(ExConsumer.wrap(onLeft));
		}

		/**
		 * @param onRight The listener to be notified for each right-only element encountered in the adjustment
		 * @return This synchronizer
		 */
		public S onRightX(ExConsumer<ElementSyncInput<L, R>, ? extends X> onRight) {
			theRightListener = onRight;
			return (S) this;
		}

		/**
		 * @param onRight The listener to be notified for each right-only element encountered in the adjustment
		 * @return This synchronizer
		 */
		public S onRight(Consumer<ElementSyncInput<L, R>> onRight) {
			return onRightX(ExConsumer.wrap(onRight));
		}

		/**
		 * @param onCommon The listener to be notified for each common element encountered in the adjustment
		 * @return This synchronizer
		 */
		public S onCommonX(ExConsumer<ElementSyncInput<L, R>, ? extends X> onCommon) {
			theCommonListener = onCommon;
			return (S) this;
		}

		/**
		 * @param onCommon The listener to be notified for each common element encountered in the adjustment
		 * @return This synchronizer
		 */
		public S onCommon(Consumer<ElementSyncInput<L, R>> onCommon) {
			return onCommonX(ExConsumer.wrap(onCommon));
		}

		@Override
		public ElementSyncAction leftOnly(ElementSyncInput<L, R> element) throws X {
			if (theLeftListener != null)
				theLeftListener.accept(element);
			return isRemoving ? element.remove() : element.preserve();
		}

		@Override
		public ElementSyncAction rightOnly(ElementSyncInput<L, R> element) throws X {
			if (theRightListener != null)
				theRightListener.accept(element);
			return isAdding ? element.useValue(theMap.apply(element.getRightValue())) : element.preserve();
		}

		@Override
		public ElementSyncAction common(ElementSyncInput<L, R> element) throws X {
			if (theCommonListener != null)
				theCommonListener.accept(element);
			if (theCommonHandler == null)
				return element.remove();
			else
				return theCommonHandler.apply(element);
		}

		@Override
		public boolean getOrder(ElementSyncInput<L, R> element) throws X {
			if (theCompare != null) {
				L mappedRight = theMap.apply(element.getRightValue());
				return theCompare.compare(element.getLeftValue(), mappedRight) <= 0;
			} else
				return isLeftFirst;
		}

		@Override
		public ElementSyncAction universalLeftOnly(ElementSyncInput<L, R> element) {
			if (theLeftListener != null)
				return null;
			return isRemoving ? element.remove() : element.preserve();
		}

		@Override
		public ElementSyncAction universalRightOnly(ElementSyncInput<L, R> element) {
			if (theRightListener != null)
				return null;
			return isAdding ? null : element.preserve();
		}

		@Override
		public ElementSyncAction universalCommon(ElementSyncInput<L, R> element) {
			if (theCommonListener != null)
				return null;
			if (theCommonHandler == null)
				return element.remove();
			else if (theCommonHandler == PRESERVE_COMMON)
				return element.preserve();
			else
				return null;
		}
	}

	/**
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 * @param <X> An exception type that the mapping function may throw
	 * @param map The function to produce left-type values from right-type ones
	 * @return A simple synchronizer to configure
	 */
	public static <L, R, X extends Throwable> SimpleCollectionSynchronizer<L, R, X, ?> simpleSyncE(
		ExFunction<? super R, ? extends L, ? extends X> map) {
		return new SimpleCollectionSynchronizer<>(map);
	}

	/**
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 * @param map The function to produce left-type values from right-type ones
	 * @return A simple synchronizer to configure
	 */
	public static <L, R> SimpleCollectionSynchronizer<L, R, RuntimeException, ?> simpleSync(Function<? super R, ? extends L> map) {
		return simpleSyncE(R -> map.apply(R));
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
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 */
	public interface CollectionAdjustment<L, R> {
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
		<X extends Throwable> void adjust(CollectionSynchronizerE<L, R, X> sync, AdjustmentOrder order) throws X;

		/**
		 * @param <X> An exception type that the mapping function may throw
		 * @param map A mapping from right to left values
		 * @return A SimpleAdjustment to configure
		 */
		default <X extends Throwable> SimpleAdjustment<L, R, X> simpleE(ExFunction<? super R, ? extends L, ? extends X> map) {
			return new SimpleAdjustment<>(this, map);
		}

		/**
		 * @param map A mapping from right to left values
		 * @return A SimpleAdjustment to configure
		 */
		default SimpleAdjustment<L, R, RuntimeException> simple(Function<? super R, ? extends L> map) {
			return simpleE(R -> map.apply(R));
		}
	}

	/**
	 * A function to find values in a list
	 * 
	 * @param <L> The type of the list to find values in
	 * @param <R> The type of the values to find in the list
	 */
	public interface ElementFinder<L, R> {
		/**
		 * @param list The list to find the value in
		 * @param value The value to find
		 * @param after The index after which to find the value (strictly after)
		 * @return The index of the element in the list with given value, or -1 if the value could not be found after the given index
		 */
		int findElement(List<L> list, R value, int after);
	}

	/**
	 * Produces a {@link CollectionAdjustment} for two lists
	 * 
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 * @param left The list to adjust
	 * @param right The list to synchronize against
	 * @param finder The function to find values from the right list in the left list
	 * @return An adjustment detailing the synchronization goals and the ability to do the adjustment
	 */
	public static <L, R> CollectionAdjustment<L, R> synchronize(List<L> left, List<R> right, ElementFinder<L, ? super R> finder) {
		int[] leftToRight = new int[left.size()];
		int[] rightToLeft = new int[right.size()];
		Arrays.fill(leftToRight, -1);
		Arrays.fill(rightToLeft, -1);
		int add = right.size(), remove = left.size(), common = 0;
		int rightIndex = 0;
		for (R r : right) {
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
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 * @param left The list to adjust
	 * @param right The list to synchronize against
	 * @return An adjustment detailing the synchronization goals and the ability to do the adjustment
	 */
	public static <L, R extends L> CollectionAdjustment<L, R> synchronize(List<L> left, List<R> right) {
		return synchronize(left, right, new ElementFinder<L, R>() {
			@Override
			public int findElement(List<L> list, R value, int after) {
				List<L> search = after < 0 ? list : list.subList(after + 1, list.size());
				return search.indexOf(value);
			}
		});
	}

	/**
	 * Produces a {@link CollectionAdjustment} for two lists
	 * 
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 * @param left The list to adjust
	 * @param right The list to synchronize against
	 * @param equals Tests values from the left and right lists for equality
	 * @return An adjustment detailing the synchronization goals and the ability to do the adjustment
	 */
	public static <L, R> CollectionAdjustment<L, R> synchronize(List<L> left, List<R> right, BiPredicate<? super L, ? super R> equals) {
		return synchronize(left, right, new ElementFinder<L, R>() {
			@Override
			public int findElement(List<L> list, R value, int after) {
				int index = after + 1;
				ListIterator<L> iter = list.listIterator(index);
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
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 * @param <X> An exception type that may be thrown by the mapping function
	 */
	public static class SimpleAdjustment<L, R, X extends Throwable>
		extends SimpleCollectionSynchronizer<L, R, X, SimpleAdjustment<L, R, X>> {
		private final CollectionAdjustment<L, R> theAdjustment;
		private AdjustmentOrder theOrder;

		SimpleAdjustment(CollectionAdjustment<L, R> adjustment, ExFunction<? super R, ? extends L, ? extends X> map) {
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
		public SimpleAdjustment<L, R, X> leftOrder() {
			theOrder = AdjustmentOrder.LeftOrder;
			return this;
		}

		/**
		 * Sets the adjustment order to {@link AdjustmentOrder#RightOrder right}
		 * 
		 * @return This adjustment
		 */
		public SimpleAdjustment<L, R, X> rightOrder() {
			theOrder = AdjustmentOrder.RightOrder;
			return this;
		}

		/**
		 * Sets the adjustment order to {@link AdjustmentOrder#AddLast add-last}
		 * 
		 * @return This adjustment
		 */
		public SimpleAdjustment<L, R, X> addLast() {
			theOrder = AdjustmentOrder.AddLast;
			return this;
		}

		/**
		 * @param order The order for this adjustment
		 * @return This adjustment
		 */
		public SimpleAdjustment<L, R, X> setOrder(AdjustmentOrder order) {
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

	static class AdjustmentImpl<L, R> implements CollectionAdjustment<L, R> {
		static final ElementSyncAction PRESERVE = new ElementSyncAction() {
			@Override
			public String toString() {
				return "PRESERVE";
			}
		};
		static final ElementSyncAction REMOVE = new ElementSyncAction() {
			@Override
			public String toString() {
				return "REMOVE";
			}
		};
		final List<L> theLeft;
		final List<R> theRight;
		final int[] leftToRight;
		final int[] rightToLeft;
		final int rightOnly;
		final int leftOnly;
		final int commonCount;
		private boolean adjusted;

		public AdjustmentImpl(List<L> left, List<R> right, int[] leftToRight, int[] rightToLeft, int add, int remove, int common) {
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
		public <X extends Throwable> void adjust(CollectionSynchronizerE<L, R, X> sync, AdjustmentOrder order) throws X {
			if (adjusted)
				throw new IllegalStateException("Adjustment may only be done once");
			adjusted = true;
			AdjustmentState state = null;
			switch (order) {
			case LeftOrder:
			case AddLast:
				state = new LeftOrderAdjustmentState();
				break;
			case RightOrder:
				state = new RightOrderAdjustmentState();
				break;
			}
			if (state == null)
				throw new IllegalStateException("Unhandled adjustment order: " + order);
			state.adjust(sync, order);
		}

		abstract class AdjustmentState {
			SyncInputImpl<L, R> input;
			ListIterator<L> leftIter;
			Iterator<R> rightIter;
			boolean hasLeft;
			int leftIndex;
			L leftVal;
			boolean hasRight;
			int rightIndex;
			R rightVal;
			ElementSyncAction universalLeft;
			ElementSyncAction universalRight;
			ElementSyncAction universalCommon;

			boolean init(CollectionSynchronizerE<L, R, ?> sync) {
				input = new SyncInputImpl<>();
				universalLeft = sync.universalLeftOnly(input);
				if (universalLeft instanceof ValueSyncAction)
					throw new IllegalStateException("A value action cannot be returned for universal handling");
				universalRight = sync.universalRightOnly(input);
				if (universalRight == PRESERVE)
					universalRight = REMOVE;
				else if (universalRight instanceof ValueSyncAction)
					throw new IllegalStateException("A value action cannot be returned for universal handling");
				universalCommon = sync.universalCommon(input);
				if (universalCommon instanceof ValueSyncAction)
					throw new IllegalStateException("A value action cannot be returned for universal handling");

				if (universalLeft != null && leftOnly == theLeft.size()) {
					if (universalLeft == REMOVE)
						theLeft.clear();
					if (universalRight == REMOVE)
						return true;
				} else if (universalCommon != null && commonCount == theLeft.size()) {
					if (universalCommon == REMOVE)
						theLeft.clear();
					if (universalRight == REMOVE)
						return true;
				} else if (universalLeft != null && universalLeft == universalCommon && leftOnly + commonCount == theLeft.size()) {
					if (universalLeft == REMOVE)
						theLeft.clear();
					if (universalRight == REMOVE)
						return true;
				}

				leftIter = theLeft.listIterator();
				hasLeft = leftIter.hasNext();
				if (hasLeft)
					leftVal = leftIter.next();
				leftIndex = input.targetIndex = 0;
				rightIter = theRight.iterator();
				hasRight = rightIter.hasNext();
				if (hasRight)
					rightVal = rightIter.next();
				rightIndex = 0;
				return false;
			}

			abstract <X extends Throwable> void adjust(CollectionSynchronizerE<L, R, X> sync, AdjustmentOrder order) throws X;

			<X extends Throwable> boolean compare(CollectionSynchronizerE<L, R, X> sync) throws X {
				input.hasLeft = input.hasRight = true;
				input.leftIndex = leftIndex;
				input.rightIndex = rightIndex;
				input.leftVal = leftVal;
				input.rightVal = rightVal;
				int preTarget = input.targetIndex;
				input.targetIndex = -1;
				boolean order = sync.getOrder(input);
				input.targetIndex = preTarget;
				return order;
			}
			
			<X extends Throwable> ElementSyncAction getRightOnlyAction(CollectionSynchronizerE<L, R, X> sync) throws X{
				ElementSyncAction action=sync.rightOnly(input);
				if(action==PRESERVE) // Both PRESERVE and REMOVE mean do nothing for right-only
					action=REMOVE;
				return action;
			}
		}

		class LeftOrderAdjustmentState extends AdjustmentState {
			@Override
			<X extends Throwable> void adjust(CollectionSynchronizerE<L, R, X> sync, AdjustmentOrder order) throws X {
				if (init(sync))
					return;

				switch (order) {
				case LeftOrder:
					while (hasLeft) {
						if (!hasRight || rightToLeft[rightIndex] >= 0 || (leftToRight[leftIndex] < 0 && compare(sync)))
							doElement(true, sync);
						else
							doElement(false, sync);
					}
					if (universalRight != REMOVE) {
						while (hasRight && rightToLeft[rightIndex] < 0)
							doElement(false, sync);
					}
					break;
				case RightOrder:
					throw new IllegalStateException();
				case AddLast:
					while (hasLeft)
						doElement(true, sync);
					if (universalRight != REMOVE) {
						input.hasLeft = false;
						input.leftIndex = -1;
						input.leftVal = null;
						input.hasRight = true;
						input.targetIndex = -1;
						for (int right = 0; right < rightToLeft.length; right++) {
							if (rightToLeft[right] < 0) {
								moveRight(right);
								input.rightIndex = right;
								input.rightVal = rightVal;
								ElementSyncAction action = getRightOnlyAction(sync);
								if (action instanceof ValueSyncAction)
									theLeft.add(((ValueSyncAction<L>) action).value);
							}
						}
					}
					break;
				}
			}

			private <X extends Throwable> void doElement(boolean left, CollectionSynchronizerE<L, R, X> sync) throws X {
				if (left) {
					input.hasLeft = true;
					input.leftIndex = leftIndex;
					input.leftVal = leftVal;
					ElementSyncAction action;
					input.hasRight = leftToRight[leftIndex] >= 0;
					if (input.hasRight) {
						moveRight(leftToRight[leftIndex]);
						if (universalCommon != null)
							action = universalCommon;
						else {
							input.rightIndex = rightIndex;
							input.rightVal = rightVal;
							action = sync.common(input);
						}
					} else {
						if (universalLeft != null) {
							action = universalLeft;
						} else {
							input.rightIndex = -1;
							input.rightVal = null;
							action = sync.leftOnly(input);
						}
					}
					if (action == PRESERVE) {
						input.targetIndex++;
					} else if (action == REMOVE) {
						leftIter.remove();
					} else if (action instanceof ValueSyncAction) {
						leftIter.set(((ValueSyncAction<L>) action).value);
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
					ElementSyncAction action;
					if (universalRight != null)
						action = universalRight;
					else {
						input.hasLeft = false;
						input.leftIndex = -1;
						input.leftVal = null;
						input.hasRight = true;
						input.rightIndex = rightIndex;
						input.rightVal = rightVal;
						action = getRightOnlyAction(sync);
					}
					if (action == REMOVE) {//
					} else {
						L toAdd = ((ValueSyncAction<L>) action).value;
						if (hasLeft) {
							leftIter.previous();
							leftIter.add(toAdd);
							leftIter.next();
						} else
							leftIter.add(toAdd);
						input.targetIndex++;
					}
					rightIndex++;
					hasRight = rightIter.hasNext();
					if (hasRight)
						rightVal = rightIter.next();
				}
			}

			private boolean moveRight(int index) {
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
				return true;
			}
		}

		class RightOrderAdjustmentState extends AdjustmentState {
			int[] updatedLeftIndexes;
			NavigableMap<Integer, L> passedCommonLeft;

			@Override
			<X extends Throwable> void adjust(CollectionSynchronizerE<L, R, X> sync, AdjustmentOrder order) throws X {
				if (init(sync))
					return;
				switch (order) {
				case LeftOrder:
				case AddLast:
					throw new IllegalStateException();
				case RightOrder:
					break;
				}
				updatedLeftIndexes = new int[leftToRight.length];
				for (int i = 0; i < leftToRight.length; i++)
					updatedLeftIndexes[i] = i;
				passedCommonLeft = new TreeMap<>();
				while (hasRight) {
					if (!hasLeft || leftToRight[leftIndex] >= 0 || (rightToLeft[rightIndex] < 0 && !compare(sync)))
						doElement(false, sync);
					else
						doElement(true, sync);
				}
				if (universalLeft != PRESERVE) {
					while (hasLeft) {
						if (leftToRight[leftIndex] < 0)
							doElement(true, sync);
						else {
							leftIter.remove();
							leftIndex++;
							hasLeft = leftIter.hasNext();
							if (hasLeft)
								leftVal = leftIter.next();
						}
					}
				}
			}

			private <X extends Throwable> void doElement(boolean left, CollectionSynchronizerE<L, R, X> sync) throws X {
				if (left) {
					ElementSyncAction action;
					if (universalLeft != null)
						action = universalLeft;
					else {
						input.hasLeft = true;
						input.leftIndex = leftIndex;
						input.leftVal = leftVal;
						input.hasRight = leftToRight[leftIndex] >= 0;
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
						leftIter.set(((ValueSyncAction<L>) action).value);
						input.targetIndex++;
					} else
						throw new IllegalArgumentException("Unrecognized action returned from synchronization: " + action);
					leftIndex++;
					hasLeft = leftIter.hasNext();
					if (hasLeft)
						leftVal = leftIter.next();
				} else {
					input.hasRight = true;
					input.rightIndex = rightIndex;
					input.rightVal = rightVal;
					ElementSyncAction action;
					input.hasLeft = rightToLeft[rightIndex] >= 0;
					if (input.hasLeft) {
						if (universalCommon != null)
							action = universalCommon;
						else {
							input.leftIndex = rightToLeft[rightIndex];
							if (input.leftIndex == leftIndex)
								input.leftVal = leftVal;
							else {
								input.leftVal = theLeft.remove(updatedLeftIndexes[input.leftIndex]);
								leftIter = theLeft.listIterator(input.targetIndex);
								leftIter.next();
							}
							action = sync.common(input);
						}
					} else {
						if (universalRight != null)
							action = universalRight;
						else {
							input.leftIndex = -1;
							input.leftVal = null;
							action = getRightOnlyAction(sync);
						}
					}
					if (action == REMOVE) {
						if (input.hasLeft && input.leftIndex == leftIndex) {
							leftIter.remove();
							adjustLeftIndexes(false);
						}
					} else {
						L value;
						if (action == PRESERVE)
							value = input.leftVal;
						else
							value = ((ValueSyncAction<L>) action).value;
						if (input.hasLeft)
							leftIter.set(value);
						else if (hasLeft) {
							leftIter.previous();
							leftIter.add(value);
							leftIter.next();
						} else
							leftIter.add(value);
						input.targetIndex++;
						if (!input.hasLeft)
							adjustLeftIndexes(true);
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

			private boolean moveLeft(int index) {
				int target;
				if (updatedLeftIndexes == null) {
					target = index;
					if (leftIndex == index)
						return false;
				} else {
					target = updatedLeftIndexes[index];
					if (input.targetIndex == target)
						return false;
				}
				if (target == 0)
					leftIter = theLeft.listIterator();
				else
					leftIter = theLeft.listIterator(target);
				leftIndex = index;
				input.targetIndex = target;
				hasLeft = leftIter.hasNext();
				if (hasLeft)
					leftVal = leftIter.next();
				else
					leftVal = null;
				return true;
			}

			private void adjustLeftIndexes(boolean add) {
				if (updatedLeftIndexes == null)
					return;
				if (add) {
					for (int i = leftIndex; i < updatedLeftIndexes.length; i++)
						updatedLeftIndexes[i]++;
				} else {
					for (int i = leftIndex + 1; i < updatedLeftIndexes.length; i++)
						updatedLeftIndexes[i]--;
				}
			}
		}

		static class ValueSyncAction<L> implements ElementSyncAction {
			L value;

			@Override
			public String toString() {
				return "VALUE=" + value;
			}
		}

		static class SyncInputImpl<L, R> implements ElementSyncInput<L, R> {
			private final ValueSyncAction<L> valueAction;

			boolean hasLeft = false;
			L leftVal;
			int leftIndex = -1;
			boolean hasRight = false;
			R rightVal;
			int rightIndex = -1;
			int targetIndex = -1;

			SyncInputImpl() {
				this.valueAction = new ValueSyncAction<>();
			}

			@Override
			public L getLeftValue() {
				if (!hasLeft)
					throw new NoSuchElementException();
				return leftVal;
			}

			@Override
			public int getOriginalLeftIndex() {
				return leftIndex;
			}

			@Override
			public R getRightValue() {
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
			public ElementSyncAction useValue(L newValue) {
				valueAction.value = newValue;
				return valueAction;
			}

			@Override
			public String toString() {
				StringBuilder str = new StringBuilder();
				if (hasLeft) {
					if (!hasRight) {
						str.append("-[").append(leftIndex).append("]=").append(leftVal);
					} else if (targetIndex >= 0) {
						str.append("[").append(leftIndex).append("]=").append(leftVal).append("->[").append(rightIndex).append("]=")
							.append(rightVal);
					} else {
						str.append("[").append(leftIndex).append("]=").append(leftVal).append("\u0394 [").append(rightIndex).append("]=")
							.append(rightVal);
					}
				} else
					str.append("+[").append(rightIndex).append("]=").append(rightVal);
				if (targetIndex >= 0)
					str.append('@').append(targetIndex);
				return str.toString();
			}
		}
	}
}
