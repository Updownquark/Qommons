package org.qommons.collect;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;

import org.qommons.IterableUtils;
import org.qommons.debug.Debug;
import org.qommons.ex.ExBiConsumer;
import org.qommons.ex.ExBiFunction;
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
		 * @return The index of the left element in a hypothetical list that was equal to the left list before the adjustment and has been
		 *         updated with appropriate add/remove/move operations during the adjustment, or -1 if this element does not have a
		 *         representation in the left list.<br />
		 *         This is only different from {@link #getTargetIndex()} if the element is to be moved within the collection.
		 */
		int getUpdatedLeftIndex();

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
		 * @return The index in the left list (after any previous synchronization operations) at which the current element would be
		 *         preserved, set, inserted, or moved. Will be -1 for an index-less add.
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
	 * An action command from a {@link CollectionSynchronizerX synchronizer}, signalling what an {@link CollectionAdjustment adjustment}
	 * should do with an element
	 */
	public interface ElementSyncAction {}

	/**
	 * Controls the {@link CollectionAdjustment#adjust(CollectionSynchronizerX, AdjustmentOrder) synchronization} of each element of two
	 * lists
	 * 
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 * @param <X> An exception type that may be thrown by any of the element methods
	 */
	public interface CollectionSynchronizerX<L, R, X extends Throwable> {
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
	 * A {@link CollectionSynchronizerX} that cannot throw any checked exceptions
	 * 
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 */
	public interface CollectionSynchronizer<L, R> extends CollectionSynchronizerX<L, R, RuntimeException> {
		@Override
		boolean getOrder(ElementSyncInput<L, R> element);

		@Override
		ElementSyncAction leftOnly(ElementSyncInput<L, R> element);

		@Override
		ElementSyncAction rightOnly(ElementSyncInput<L, R> element);

		@Override
		ElementSyncAction common(ElementSyncInput<L, R> element);
	}

	/**
	 * A simple synchronizer (for any of the {@link CollectionUtils#synchronize2(List, List, ElementFinder) synchronize} methods) whose
	 * left-only and right-only behaviors (i.e. whether an element will be in the left list) does not depend on element value or position.
	 * 
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 * @param <X> An exception type that may be thrown by the mapping function
	 * @param <S> Self parameter, making chain methods of this class easier with subclasses
	 */
	public static class SimpleCollectionSynchronizer<L, R, X extends Throwable, S extends SimpleCollectionSynchronizer<L, R, X, S>>
		implements CollectionSynchronizerX<L, R, X> {
		private static final ExFunction<ElementSyncInput<?, ?>, ElementSyncAction, RuntimeException> PRESERVE_COMMON = el -> el.preserve();

		private final ExFunction<? super R, ? extends L, ? extends X> theMap;
		private boolean isAdding;
		private boolean isRemoving;
		private boolean isLeftFirst;
		private boolean isReplacingCommon;
		private Comparator<? super L> theCompare;
		private ExFunction<? super ElementSyncInput<L, R>, ElementSyncAction, ? extends X> theLeftHandler;
		private ExFunction<? super ElementSyncInput<L, R>, ElementSyncAction, ? extends X> theRightHandler;
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
		 * @param leftHandler Custom handler for left-only elements
		 * @return This synchronizer
		 */
		public S handleLeft(ExFunction<? super ElementSyncInput<L, R>, ElementSyncAction, ? extends X> leftHandler) {
			theLeftHandler = leftHandler;
			return (S) this;
		}

		/**
		 * @param rightHandler Custom handler for right-only elements
		 * @return This synchronizer
		 */
		public S handleRight(ExFunction<? super ElementSyncInput<L, R>, ElementSyncAction, ? extends X> rightHandler) {
			theRightHandler = rightHandler;
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
			isReplacingCommon = false;
			theCommonHandler = (ExFunction<? super ElementSyncInput<L, R>, ElementSyncAction, ? extends X>) PRESERVE_COMMON;
			return (S) this;
		}

		/**
		 * Specifies that when common elements (in both the left and right lists) are encountered, the left value should be used with no
		 * update
		 * 
		 * @param update Determines whether to fire an update on an element
		 * @return This synchronizer
		 */
		public S commonUsesLeft(BiPredicate<? super L, ? super R> update) {
			isReplacingCommon = false;
			theCommonHandler = el -> {
				if (update.test(el.getLeftValue(), el.getRightValue()))
					return el.useValue(el.getLeftValue());
				else
					return el.preserve();
			};

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
			isReplacingCommon = update;
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
		 * Specifies that when common elements (in both the left and right lists) are encountered, the mapped right value should be used
		 * 
		 * @param update Whether to update the left value of common elements when the map of the right value is identical to the left value
		 * @return This synchronizer
		 */
		public S commonUsesRight(BiPredicate<? super L, ? super R> update) {
			if (theMap == null)
				throw new IllegalStateException("A mapping must be specified for the synchronizer if right values are to be used");
			isReplacingCommon = false;
			theCommonHandler = el -> {
				L mapped = theMap.apply(el.getRightValue());
				if (el.getLeftValue() != mapped || update.test(el.getLeftValue(), el.getRightValue()))
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
					isReplacingCommon = false;
					theCommonHandler = el -> el.useValue(el.getLeftValue());
					return (S) this;
				} else
					return commonUsesLeft();
			} else
				return commonUsesRight(update);
		}

		/**
		 * @param left Whether to use the left or mapped right value when common elements (in both the left and right lists) are encountered
		 * @param update Whether to update the left value when the map of the right value is identical to the left value (if
		 *        <code>left==false</code>) or always (if <code>left==true</code>)
		 * @return This synchronizer
		 */
		public S commonUses(boolean left, BiPredicate<? super L, ? super R> update) {
			if (left)
				return commonUsesLeft(update);
			else
				return commonUsesRight(update);
		}

		/**
		 * Specifies that common elements (in both the left and right lists) should be removed
		 * 
		 * @return This synchronizer
		 */
		public S removeCommon() {
			theCommonHandler = null;
			isReplacingCommon = false;
			return (S) this;
		}

		/**
		 * @param handler The function to update the left value using the right
		 * @return This synchronizer
		 */
		public S updateCommon(ExBiFunction<L, R, L, X> handler) {
			return handleCommon(el -> {
				L newLeft = handler.apply(el.getLeftValue(), el.getRightValue());
				if (newLeft == el.getLeftValue())
					return el.preserve();
				else
					return el.useValue(newLeft);
			});
		}

		/**
		 * @param handler The function to modify the left value using the right
		 * @return This synchronizer
		 */
		public S adjustCommon(ExBiConsumer<L, R, X> handler) {
			return handleCommon(el -> {
				handler.accept(el.getLeftValue(), el.getRightValue());
				return el.preserve();
			});
		}

		/**
		 * @param commonHandler Custom handler for common elements (in both the left and right lists)
		 * @return This synchronizer
		 */
		public S handleCommon(ExFunction<ElementSyncInput<L, R>, ElementSyncAction, ? extends X> commonHandler) {
			isReplacingCommon = false;
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
			ElementSyncAction action;
			if (theLeftHandler != null)
				action = theLeftHandler.apply(element);
			else
				action = isRemoving ? element.remove() : element.preserve();
			if (theLeftListener != null)
				theLeftListener.accept(element);
			return action;
		}

		@Override
		public ElementSyncAction rightOnly(ElementSyncInput<L, R> element) throws X {
			ElementSyncAction action;
			if (theRightHandler != null)
				action = theRightHandler.apply(element);
			else
				action = isAdding ? element.useValue(theMap.apply(element.getRightValue())) : element.preserve();
			if (theRightListener != null)
				theRightListener.accept(element);
			return action;
		}

		@Override
		public ElementSyncAction common(ElementSyncInput<L, R> element) throws X {
			ElementSyncAction action;
			if (theCommonHandler == null)
				action = element.remove();
			else
				action = theCommonHandler.apply(element);
			if (theCommonListener != null)
				theCommonListener.accept(element);
			return action;
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
			if (theLeftListener != null || theLeftHandler != null)
				return null;
			return isRemoving ? element.remove() : element.preserve();
		}

		@Override
		public ElementSyncAction universalRightOnly(ElementSyncInput<L, R> element) {
			if (theRightListener != null || theRightHandler != null)
				return null;
			if (isAdding)
				return theMap == ExFunction.IDENTITY ? element.useValue((L) element.getRightValue()) : null;
			else
				return element.preserve();
		}

		@Override
		public ElementSyncAction universalCommon(ElementSyncInput<L, R> element) {
			if (theCommonListener != null)
				return null;
			if (theCommonHandler == null)
				return element.remove();
			else if (theCommonHandler == PRESERVE_COMMON)
				return element.preserve();
			else if (isReplacingCommon && theMap == ExFunction.IDENTITY)
				return element.useValue((L) element.getRightValue());
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
	public static <L, R, X extends Throwable> SimpleCollectionSynchronizer<L, R, X, ?> simpleSyncX(
		ExFunction<? super R, ? extends L, ? extends X> map) {
		return new SimpleCollectionSynchronizer<>(map);
	}

	/**
	 * @param <T> The super-type of the collections
	 * @param collections The collections to concatenate
	 * @return A collection containing all elements of the given collections
	 */
	public static <T> Collection<T> concat(Collection<? extends T>... collections) {
		return new ConcatenatedCollection<>(Arrays.asList(collections));
	}

	/**
	 * @param <T> The super-type of the collections
	 * @param collections The collections to concatenate
	 * @return A collection containing all elements of the given collections
	 */
	public static <T> Collection<T> concat(Collection<? extends Collection<? extends T>> collections) {
		return new ConcatenatedCollection<>(collections);
	}

	/**
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 * @param map The function to produce left-type values from right-type ones
	 * @return A simple synchronizer to configure
	 */
	public static <L, R> SimpleCollectionSynchronizer<L, R, RuntimeException, ?> simpleSync(Function<? super R, ? extends L> map) {
		return simpleSyncX(map::apply);
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
		 * This method will, for each element in the left list, allow the {@link CollectionSynchronizerX synchronizer} to determine what to
		 * do with the element using either the {@link CollectionSynchronizerX#leftOnly(ElementSyncInput) leftOnly} or
		 * {@link CollectionSynchronizerX#common(ElementSyncInput) common} method, as appropriate. For each un-mapped element in the right
		 * list, the synchronizer will determine whether it should be added to the left list with the
		 * {@link CollectionSynchronizerX#rightOnly(ElementSyncInput) rightOnly} method.
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
		<X extends Throwable> void adjust(CollectionSynchronizerX<L, ? super R, X> sync, AdjustmentOrder order) throws X;

		/**
		 * @param <X> An exception type that the mapping function may throw
		 * @param map A mapping from right to left values
		 * @return A SimpleAdjustment to configure
		 */
		default <X extends Throwable> SimpleAdjustment<L, R, X> simpleX(ExFunction<? super R, ? extends L, ? extends X> map) {
			return new SimpleAdjustment<>(this, map);
		}

		/**
		 * @param map A mapping from right to left values
		 * @return A SimpleAdjustment to configure
		 */
		default SimpleAdjustment<L, R, RuntimeException> simple(Function<? super R, ? extends L> map) {
			return simpleX(map == null ? null : R -> map.apply(R));
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
		 * @param start The index to start looking in the list at
		 * @param filter A test for whether a particular index in the left list is as yet unmatched
		 * @param after The index after which to find the value (strictly after)
		 * @return The index of the element in the list with given value, or -1 if the value could not be found after the given index
		 */
		int findElement(List<? extends L> list, R value, int start, IntPredicate filter);
	}

	/**
	 * Enables probabilistic matching between elements in lists
	 * 
	 * @param <L> The type of values in the left list (the one to be modified)
	 * @param <R> The type of values in the right list (the one whose content is being used to modify the left list)
	 */
	public interface ProbabilisticMatcher<L, R> {
		/**
		 * @return The minimum threshold for a combination of elements for a potential match. The result of
		 *         {@link #getMatchProbability(Object, Object)} between 2 elements must be <b>GREATER</b> than this value for a potential
		 *         match.
		 */
		double getMinimumMatchThreshold();

		/**
		 * @param leftValue The value from the left list (the one to be modified)
		 * @param rightValue The value from the right list (the one whose content is being used to modify the left list)
		 * @return The probability that the two values are a match. No constraints are placed on the range of values that may be returned
		 *         here (e.g. no assumption is made that it will be between 0 and 1). The only meaning assumed is that values
		 *         &lt;={@link #getMinimumMatchThreshold()} cannot be matches, and larger numbers will take precedence over smaller ones.
		 */
		double getMatchProbability(L leftValue, R rightValue);
	}

	/**
	 * Just a simple functional interface that returns a double value given two typed arguments
	 * 
	 * @param <L> The type of the first argument
	 * @param <R> The type of the second argument
	 */
	public interface BiDoubleFunction<L, R> {
		/**
		 * @param lv The first argument
		 * @param rv The second argument
		 * @return The result of the operation
		 */
		double apply(L lv, R rv);
	}

	/**
	 * A structure returned from {@link CollectionUtils#buildSync(List, List)} that allows configuration of a synchronization operation
	 * between two lists.
	 * 
	 * @param <L> The type of values in the left list (the one to be modified)
	 * @param <R> The type of values in the right list (the one whose content is being used to modify the left list)
	 */
	public static class CollectionAdjustmentPrecursor<L, R> {
		private final List<L> theLeft;
		private final List<? extends R> theRight;
		private ProbabilisticMatcher<? super L, ? super R> theProbabilisticMatcher;

		CollectionAdjustmentPrecursor(List<L> left, List<? extends R> right) {
			theLeft = left;
			theRight = right;
		}

		/**
		 * @param finder The function to find values from the right list in the left list
		 * @return The adjustment operation
		 */
		public CollectionAdjustment<L, R> withFinder(ElementFinder<? super L, ? super R> finder) {
			return synchronize2(theLeft, theRight, finder, theProbabilisticMatcher);
		}

		/**
		 * @param matcher Tests values from the left and right lists for a positive match
		 * @return The adjustment operation
		 */
		public CollectionAdjustment<L, R> withMatcher(BiPredicate<? super L, ? super R> matcher) {
			return withFinder(new DefaultElementFinder<>(matcher));
		}

		/**
		 * Enables probabilistic matching between elements that are not matched by the non-probabilistic matcher, which has precedence
		 * 
		 * @param matcher A probabilistic matcher for finding matches between elements in the two lists
		 * @return This adjustment precursor
		 */
		public CollectionAdjustmentPrecursor<L, R> withProbabilisticMatcher(ProbabilisticMatcher<? super L, ? super R> matcher) {
			theProbabilisticMatcher = matcher;
			return this;
		}

		/**
		 * Enables probabilistic matching between elements that are not matched by the non-probabilistic matcher, which has precedence
		 * 
		 * @param minMatchThreshold The minimum threshold for a possible match. The value returned by the probability function must be
		 *        <b>GREATER</b> than this value for a match to be made.
		 * @param matchProbability A function to produce a probability of a match between an element in the left list and one in the right
		 *        list
		 * @return This adjustment precursor
		 */
		public CollectionAdjustmentPrecursor<L, R> withProbabilisticMatcher(double minMatchThreshold,
			BiDoubleFunction<? super L, ? super R> matchProbability) {
			return withProbabilisticMatcher(new ProbabilisticMatcher<L, R>() {
				@Override
				public double getMinimumMatchThreshold() {
					return minMatchThreshold;
				}

				@Override
				public double getMatchProbability(L leftValue, R rightValue) {
					return matchProbability.apply(leftValue, rightValue);
				}
			});
		}
	}

	/**
	 * Creates a precursor structure for configuring an adjustment operation between the two lists
	 * 
	 * @param <L> The type of values in the left list
	 * @param <R> The type of values in the right list
	 * @param left The list to adjust based on the content of the right list
	 * @param right The list whose content to use to adjust the content of the left list
	 * @return A precursor structure for configuring the adjustment operation
	 */
	public static <L, R> CollectionAdjustmentPrecursor<L, R> buildSync(List<L> left, List<? extends R> right) {
		return new CollectionAdjustmentPrecursor<>(left, right);
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
	public static <L, R> CollectionAdjustment<L, R> synchronize2(List<L> left, List<? extends R> right,
		ElementFinder<? super L, ? super R> finder) {
		return synchronize2(left, right, finder, null);
	}

	/**
	 * Produces a {@link CollectionAdjustment} for two lists
	 * 
	 * @param <L> The type of the list to adjust
	 * @param <R> The type of the list to synchronize against
	 * @param left The list to adjust
	 * @param right The list to synchronize against
	 * @param finder The function to find values from the right list in the left list
	 * @param probabilisiticMatcher A probabilistic matcher for finding matches between elements in the two lists
	 * @return An adjustment detailing the synchronization goals and the ability to do the adjustment
	 */
	public static <L, R> CollectionAdjustment<L, R> synchronize2(List<L> left, List<? extends R> right,
		ElementFinder<? super L, ? super R> finder, ProbabilisticMatcher<? super L, ? super R> probabilisiticMatcher) {
		if (finder == null && probabilisiticMatcher == null)
			throw new IllegalArgumentException("Either an element finder or a probabilistic matcher must be provided");
		int[] leftToRight = new int[left.size()];
		int[] rightToLeft = new int[right.size()];
		Arrays.fill(leftToRight, -1);
		Arrays.fill(rightToLeft, -1);
		int startLeft = 0;
		int add = right.size(), remove = left.size(), common = 0;
		if (finder != null) {
			int rightIndex = 0;
			IntPredicate filter = idx -> leftToRight[idx] < 0;
			for (R r : right) {
				if (startLeft == leftToRight.length)
					break;
				int leftIndex = startLeft;
				int newLeft = finder.findElement(left, r, leftIndex, filter);
				while (newLeft >= leftIndex && leftToRight[leftIndex] >= 0) {
					leftIndex = newLeft;
					leftIndex = finder.findElement(left, r, leftIndex + 1, filter);
				}
				if (newLeft < leftIndex)
					leftIndex = -1;
				else
					leftIndex = newLeft;

				if (leftIndex >= 0) {
					add--;
					remove--;
					common++;
					rightToLeft[rightIndex] = leftIndex;
					leftToRight[leftIndex] = rightIndex;
					if (leftIndex == startLeft) {
						do {
							startLeft++;
						} while (startLeft < leftToRight.length && leftToRight[startLeft] >= 0);
					}
				}
				rightIndex++;
			}
		}
		if (probabilisiticMatcher != null && !isFilled(leftToRight) && !isFilled(rightToLeft)) {
			// Find most probable matches for left-to-right and right-to-left.
			ProbableMatch[] leftToRightMatches = new ProbableMatch[leftToRight.length];
			ProbableMatch[] rightToLeftMatches = new ProbableMatch[rightToLeft.length];
			int leftIndex = 0;
			double threshold = probabilisiticMatcher.getMinimumMatchThreshold();
			for (L lv : left) {
				if (leftToRight[leftIndex] < 0) {
					int rightIndex = 0;
					for (R rv : right) {
						if (rightToLeft[rightIndex] < 0) {
							double matchProbability = probabilisiticMatcher.getMatchProbability(lv, rv);
							if (matchProbability > threshold) {
								if (leftToRightMatches[leftIndex] == null || matchProbability > leftToRightMatches[leftIndex].probability)
									leftToRightMatches[leftIndex] = new ProbableMatch(rightIndex, matchProbability);
								if (rightToLeftMatches[rightIndex] == null || matchProbability > rightToLeftMatches[rightIndex].probability)
									rightToLeftMatches[rightIndex] = new ProbableMatch(leftIndex, matchProbability);
							}
						}
						rightIndex++;
					}
				}
				leftIndex++;
			}

			// For each left element, if the most probable right match matches the corresponding most probable left match
			// for that right element, the match them.
			// Otherwise, it means that the right element was a better match to a different left element
			for (leftIndex = 0; leftIndex < leftToRight.length; leftIndex++) {
				ProbableMatch rightMatch = leftToRightMatches[leftIndex];
				if (rightMatch != null && rightToLeftMatches[rightMatch.index].index == leftIndex) {
					leftToRight[leftIndex] = rightMatch.index;
					rightToLeft[rightMatch.index] = leftIndex;
				}
			}
		}

		return new AdjustmentImpl<>(left, right, leftToRight, rightToLeft, add, remove, common);
	}

	private static boolean isFilled(int[] indexes) {
		for (int idx : indexes) {
			if (idx < 0)
				return false;
		}
		return true;
	}

	private static class ProbableMatch {
		final int index;
		final double probability;

		ProbableMatch(int index, double probability) {
			this.index = index;
			this.probability = probability;
		}
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
		return synchronize2(left, right, new DefaultElementFinder<>(Objects::equals));
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
	public static <L, R> CollectionAdjustment<L, R> synchronize(List<L> left, List<? extends R> right,
		BiPredicate<? super L, ? super R> equals) {
		return synchronize2(left, right, new ElementFinder<L, R>() {
			@Override
			public int findElement(List<? extends L> list, R value, int start, IntPredicate filter) {
				int index = start;
				ListIterator<? extends L> iter = list.listIterator(index);
				while (iter.hasNext()) {
					L next = iter.next();
					if (filter.test(iter.previousIndex()) && equals.test(next, value))
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
		 * @see CollectionAdjustment#adjust(CollectionSynchronizerX, AdjustmentOrder)
		 * 
		 * @throws X If the mapping function (or {@link #handleCommon(ExFunction) custom common element handler}) throws an exception
		 */
		public void adjust() throws X {
			theAdjustment.adjust(this, theOrder);
		}
	}

	/** An element finder that is slightly more efficient when none other is provided */
	static class DefaultElementFinder<L, R> implements ElementFinder<L, R> {
		private final BiPredicate<? super L, ? super R> theMatcher;

		public DefaultElementFinder(BiPredicate<? super L, ? super R> matcher) {
			theMatcher = matcher;
		}

		@Override
		public int findElement(List<? extends L> list, R value, int start, IntPredicate filter) {
			int index = start;
			ListIterator<? extends L> iter = list.listIterator(index);
			while (iter.hasNext()) {
				L next = iter.next();
				if (filter.test(index) && theMatcher.test(next, value))
					return index;
				index++;
			}
			return -1;
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
		static final Object ADD_RIGHT = new Object() {
			@Override
			public String toString() {
				return "Add Right";
			}
		};

		final List<L> theLeft;
		final List<? extends R> theRight;
		final int[] leftToRight;
		final int[] rightToLeft;
		final int rightOnly;
		final int leftOnly;
		final int commonCount;
		private boolean adjusted;
		boolean debug;

		public AdjustmentImpl(List<L> left, List<? extends R> right, int[] leftToRight, int[] rightToLeft, int add, int remove,
			int common) {
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
		public <X extends Throwable> void adjust(CollectionSynchronizerX<L, ? super R, X> sync, AdjustmentOrder order) throws X {
			debug = Debug.d().debug(this).is("debugging");
			if (adjusted)
				throw new IllegalStateException("Adjustment may only be done once");
			adjusted = true;
			AdjustmentState state = new AdjustmentState();
			// This cast is safe, because R-typed values are only returned, no accepting methods exist
			state.adjust((CollectionSynchronizerX<L, R, X>) sync, order);
		}

		class AdjustmentState {
			SyncInputImpl<L, R> input;
			ListIterator<L> leftIter;
			Iterator<? extends R> rightIter;
			boolean hasLeft;
			int leftIndex;
			L leftVal;
			boolean hasRight;
			int rightIndex;
			R rightVal;
			ElementSyncAction universalLeft;
			ElementSyncAction universalRight;
			boolean simpleAddRight;
			ElementSyncAction universalCommon;
			boolean simpleReplaceCommon;

			// Right-order fields
			int[] updatedLeftIndexes;
			int leftTarget;

			boolean init(CollectionSynchronizerX<L, R, ?> sync, AdjustmentOrder order) {
				input = new SyncInputImpl<>();
				universalLeft = sync.universalLeftOnly(input);
				if (universalLeft instanceof ValueSyncAction)
					throw new IllegalStateException("A value action cannot be returned for universal left handling");
				input.hasRight = true;
				input.rightVal = (R) ADD_RIGHT;
				universalRight = sync.universalRightOnly(input);
				if (universalRight == PRESERVE)
					universalRight = REMOVE;
				else if (universalRight instanceof ValueSyncAction) {
					if (((ValueSyncAction<L>) universalRight).value != ADD_RIGHT)
						throw new IllegalStateException(
							"A value action returned for universal right handling may only use the right value");
					simpleAddRight = true;
				}
				universalCommon = sync.universalCommon(input);
				if (universalCommon instanceof ValueSyncAction) {
					if (((ValueSyncAction<L>) universalCommon).value != ADD_RIGHT)
						throw new IllegalStateException(
							"A value action returned for universal common handling may only use the right value");
					simpleReplaceCommon = true;
				}

				ElementSyncAction easyLeft;
				if (leftToRight.length == 0)
					easyLeft = REMOVE;
				else if (universalLeft != null && leftOnly == leftToRight.length)
					easyLeft = universalLeft;
				else if (universalCommon != null && commonCount == leftToRight.length)
					easyLeft = universalCommon;
				else if (universalLeft != null && universalLeft == universalCommon && leftOnly + commonCount == leftToRight.length)
					easyLeft = universalLeft;
				else
					easyLeft = null;
				if (easyLeft != null) {
					if (easyLeft == REMOVE) {
						if (simpleAddRight) {
							if (!theLeft.isEmpty())
								theLeft.clear();
							theLeft.addAll((List<? extends L>) theRight);
							return true;
						} else if (universalRight == REMOVE || rightToLeft.length == 0) {
							if (!theLeft.isEmpty())
								theLeft.clear();
							return true;
						}
					} else if (easyLeft == PRESERVE) {
						if (rightToLeft.length == 0)
							return true;
						if (universalRight == REMOVE && (commonCount == 0 || order != AdjustmentOrder.RightOrder))
							return true;
						else if (simpleAddRight && order == AdjustmentOrder.AddLast
							&& (rightOnly == rightToLeft.length || theLeft instanceof Set)) {
							theLeft.addAll((List<? extends L>) theRight);
							return true;
						}
					} // else commonCount>0 && simpleReplaceCommon==true; Can't take any short cuts
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

			<X extends Throwable> void adjust(CollectionSynchronizerX<L, R, X> sync, AdjustmentOrder order) throws X {
				if (debug) {
					System.out.println("\t" + Arrays.toString(leftToRight));
					System.out.println("\t" + Arrays.toString(rightToLeft));
				}
				if (init(sync, order))
					return;

				switch (order) {
				case LeftOrder:
					while (hasLeft) {
						if (!hasRight || rightToLeft[rightIndex] >= 0 || (leftToRight[leftIndex] < 0 && compare(sync)))
							doElement(true, sync);
						else
							doElement(false, sync);
						if (debug) {
							printValue();
							System.out.println("\tleftI=" + leftIndex + ", hasLeft=" + hasLeft);
							System.out.println("\trightI=" + rightIndex + ", targetI=" + input.targetIndex + ", hasRight=" + hasRight);
							System.out.println("\tUpdated indexes: " + Arrays.toString(updatedLeftIndexes));
						}
					}
					if (universalRight != REMOVE) {
						while (hasRight && rightToLeft[rightIndex] < 0)
							doElement(false, sync);
					}
					break;
				case RightOrder:
					updatedLeftIndexes = new int[leftToRight.length];
					for (int i = 0; i < leftToRight.length; i++)
						updatedLeftIndexes[i] = i;
					while (hasRight) {
						if (leftTarget == leftToRight.length || leftToRight[leftTarget] >= 0
							|| (rightToLeft[rightIndex] < 0 && !compare(sync)))
							doElement(false, sync);
						else
							doElement(true, sync);
						if (debug) {
							printValue();
							System.out.println("\tleftI=" + leftIndex + ", leftT=" + leftTarget + ", hasLeft=" + hasLeft);
							System.out.println("\trightI=" + rightIndex + ", targetI=" + input.targetIndex + ", hasRight=" + hasRight);
							System.out.println("\tUpdated indexes: " + Arrays.toString(updatedLeftIndexes));
						}
					}
					if (universalLeft != PRESERVE) {
						while (leftTarget < leftToRight.length && leftToRight[leftTarget] < 0) {
							if (debug)
								printValue();
							doElement(true, sync);
						}
					}
					break;
				case AddLast:
					while (hasLeft)
						doElement(true, sync);
					if (universalRight != REMOVE) {
						input.hasLeft = false;
						input.leftIndex = input.updatedLeftIndex = -1;
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
				if (debug)
					System.out.println("\tValue=" + theLeft);
			}

			<X extends Throwable> boolean compare(CollectionSynchronizerX<L, R, X> sync) throws X {
				input.hasLeft = input.hasRight = true;
				input.leftIndex = leftIndex;
				input.updatedLeftIndex = input.targetIndex;
				input.rightIndex = rightIndex;
				input.leftVal = leftVal;
				input.rightVal = rightVal;
				int preTarget = input.targetIndex;
				input.targetIndex = -1;
				boolean order = sync.getOrder(input);
				input.targetIndex = preTarget;
				return order;
			}

			<X extends Throwable> ElementSyncAction getRightOnlyAction(CollectionSynchronizerX<L, R, X> sync) throws X {
				ElementSyncAction action = sync.rightOnly(input);
				if (action == PRESERVE) // Both PRESERVE and REMOVE mean do nothing for right-only
					action = REMOVE;
				return action;
			}

			private void printValue() {
				System.out.print("\tValue=[");
				for (int i = 0; i < theLeft.size(); i++) {
					if (i > 0)
						System.out.print(", ");
					if (leftTarget < leftToRight.length && getUpdatedLeftIndex(leftTarget) == i)
						System.out.print('*');
					System.out.print(theLeft.get(i));
					if (leftTarget == leftToRight.length && i == theLeft.size() - 1)
						System.out.print('*');
					if (leftIndex == leftToRight.length || getUpdatedLeftIndex(leftIndex) == i)
						System.out.print('^');
				}
				System.out.println("]");
			}

			private <X extends Throwable> void doElement(boolean left, CollectionSynchronizerX<L, R, X> sync) throws X {
				if (left)
					doLeftAction(sync);
				else
					doRightAction(sync);
			}

			<X extends Throwable> void doLeftAction(CollectionSynchronizerX<L, R, X> sync) throws X {
				input.hasLeft = true;
				input.leftIndex = leftTarget;
				input.updatedLeftIndex = getUpdatedLeftIndex(leftTarget);
				input.hasRight = leftToRight[leftTarget] >= 0;
				ElementSyncAction action;
				String debugMsg = "";
				try {
					if (input.hasRight) {
						if (debug)
							debugMsg = "Common " + leftTarget + ":" + input.updatedLeftIndex + "/" + rightIndex + "->" + input.targetIndex;
						int targetRight = leftToRight[leftTarget];
						if (targetRight != rightIndex)
							moveRight(targetRight);
						input.rightIndex = targetRight;
						input.rightVal = rightVal;
					} else {
						input.rightIndex = -1;
						input.rightVal = null;
						if (debug)
							debugMsg = "Left-only " + leftTarget + ":" + input.updatedLeftIndex + "->" + input.targetIndex;
					}
					boolean moveLeft = leftTarget != leftIndex;
					boolean betterMove;
					if (moveLeft) {
						betterMove = theLeft instanceof BetterList;
						if (betterMove) {
							BetterList<L> betterLeft = (BetterList<L>) theLeft;
							betterMove = betterLeft.canMove(//
								betterLeft.getElement(input.updatedLeftIndex).getElementId(), //
								input.targetIndex == 0 ? null : betterLeft.getElement(input.targetIndex - 1).getElementId(),
								betterLeft.getElement(input.targetIndex).getElementId()) == null;
						}
						if (betterMove) {
							// Implementations of BetterList may be able to use the move() method to perform move operations
							// that wouldn't be possible just by removing and re-adding
							// or they may be able to do the operation more efficiently this way.
							input.leftVal = theLeft.get(input.updatedLeftIndex);
							if (debug)
								debugMsg += " " + input.leftVal + " (may use better move)";
						} else {
							input.leftVal = theLeft.remove(input.updatedLeftIndex);
							if (debug) {
								debugMsg += " " + input.leftVal;
								if (input.hasRight)
									debugMsg += "->" + input.rightVal;
								debugMsg += " (removing pre-move)";
							}
							adjustLeftIndexes(false, leftTarget);
							// Reposition the iterator to avoid the ConcurrentModificationException
							leftIter = theLeft.listIterator(getUpdatedLeftIndex(leftIndex));
							if (hasLeft)
								leftIter.next();
						}
					} else {
						betterMove = false;
						input.leftVal = leftVal;
						if (debug)
							debugMsg += " " + input.leftVal;
					}

					if (input.hasRight) {
						if (simpleReplaceCommon)
							action = input.useValue((L) rightVal);
						else if (universalCommon != null)
							action = universalCommon;
						else {
							input.rightIndex = rightIndex;
							input.rightVal = rightVal;
							action = sync.common(input);
						}
						if (action == null)
							throw new IllegalArgumentException("Synchronizer returned null sync action for common value " + input);
					} else {
						if (universalLeft != null)
							action = universalLeft;
						else
							action = sync.leftOnly(input);
						if (action == null)
							throw new IllegalArgumentException("Synchronizer returned null sync action for left-only value " + input);
					}

					if (action == REMOVE) {
						if (debug)
							debugMsg += " Removing";
						if (!moveLeft) {
							leftIter.remove();
							adjustLeftIndexes(false, leftIndex);
						} else if (betterMove) {
							theLeft.remove(input.updatedLeftIndex);
							adjustLeftIndexes(false, leftTarget);
						}
					} else {
						L value;
						if (action == PRESERVE) {
							if (debug)
								debugMsg += " Preserving";
							value = input.leftVal;
						} else {
							value = ((ValueSyncAction<L>) action).value;
							if (debug)
								debugMsg += " Updating to " + value;
						}
						if (!moveLeft) { //
							if (action == PRESERVE) {
								if (debug)
									debugMsg += " (skipping)";
							} else {
								if (debug)
									debugMsg += " (simple set)";
								leftIter.set(value);
							}
						} else if (betterMove) {
							if (action == PRESERVE || value == leftVal) {
								if (debug)
									debugMsg += " (moving " + input.updatedLeftIndex + "->" + input.targetIndex + ")";
								BetterList<L> betterLeft = (BetterList<L>) theLeft;
								betterLeft.move(//
									betterLeft.getElement(input.updatedLeftIndex).getElementId(), //
									input.targetIndex == 0 ? null : betterLeft.getElement(input.targetIndex - 1).getElementId(),
									betterLeft.getElement(input.targetIndex).getElementId(), false, null);
								adjustLeftIndexes(false, leftTarget);
								adjustLeftIndexes(true, leftIndex);
							} else {
								theLeft.remove(input.updatedLeftIndex);
								if (debug)
									debugMsg += " (removing pre-update)";
								adjustLeftIndexes(false, leftTarget);
								debugMsg = addValue(value, debugMsg);
							}

							// Because movement consists of 2 operations internally (remove and add),
							// this operation voids the contract of the iterator, meaning we need to re-obtain it
							leftIter = theLeft.listIterator(getUpdatedLeftIndex(leftIndex));
							if (hasLeft)
								leftIter.next();
						} else
							debugMsg = addValue(value, debugMsg);
						input.targetIndex++;
					}
					if (input.hasRight) {
						rightIndex++;
						hasRight = rightIter.hasNext();
						rightVal = hasRight ? rightIter.next() : null;
					}
					if (leftIndex == leftTarget)
						advanceLeft();
					leftTarget++;
				} finally {
					if (debugMsg.length() > 0)
						System.out.println(debugMsg);
				}
			}

			<X extends Throwable> void doRightAction(CollectionSynchronizerX<L, R, X> sync) throws X {
				if (rightToLeft[rightIndex] >= 0) {
					leftTarget = rightToLeft[rightIndex];
					doLeftAction(sync);
					return;
				}
				input.hasRight = true;
				input.rightIndex = rightIndex;
				input.rightVal = rightVal;
				ElementSyncAction action;
				boolean actionHasLeft = input.hasLeft = false;
				String debugMsg = "";
				if (debug)
					debugMsg = "Right-only " + rightIndex + "->" + input.targetIndex + " " + input.rightVal;
				try {
					if (simpleAddRight)
						action = input.useValue((L) rightVal);
					else if (universalRight != null)
						action = universalRight;
					else {
						input.leftIndex = -1;
						input.leftVal = null;
						action = getRightOnlyAction(sync);
					}

					if (action == REMOVE) {
						if (debug)
							debugMsg += " Removing";
					} else {
						L value;
						if (action == PRESERVE) {
							if (debug) {
								if (actionHasLeft)
									debugMsg += " Preserving";
								else
									debugMsg += " Ignoring";
							}
							value = input.leftVal;
						} else {
							value = ((ValueSyncAction<L>) action).value;
							if (debug) {
								if (actionHasLeft)
									debugMsg += " Updating to " + value;
								else
									debugMsg += " Adding";
							}
						}
						debugMsg = addValue(value, debugMsg);
						input.targetIndex++;
					}
					if (actionHasLeft) {
						if (leftIndex == leftTarget)
							advanceLeft();
						leftTarget++;
					}
					rightIndex++;
					hasRight = rightIter.hasNext();
					rightVal = hasRight ? rightIter.next() : null;
				} finally {
					if (debugMsg.length() > 0)
						System.out.println(debugMsg);
				}
			}

			String addValue(L value, String debugMsg) {
				if (leftIndex < leftToRight.length) {
					if (debug)
						debugMsg += " (backstep add)";
					leftIter.previous();
					leftIter.add(value);
					leftIter.next();
					adjustLeftIndexes(true, leftIndex);
				} else {
					if (debug)
						debugMsg += " (terminal add)";
					leftIter.add(value);
					// No point adjusting left indexes--there are no more
				}
				return debugMsg;
			}

			int getUpdatedLeftIndex(int leftIdx) {
				if (updatedLeftIndexes != null)
					return updatedLeftIndexes[leftIdx];
				else if (leftIdx == leftIndex)
					return input.targetIndex;
				else
					throw new IllegalStateException("Should not be here for a left order");
			}

			void adjustLeftIndexes(boolean add, int from) {
				if (updatedLeftIndexes == null)
					return;
				if (add) {
					for (int i = from; i < updatedLeftIndexes.length; i++)
						updatedLeftIndexes[i]++;
				} else {
					for (int i = from + 1; i < updatedLeftIndexes.length; i++)
						updatedLeftIndexes[i]--;
				}
			}

			void advanceLeft() {
				if (updatedLeftIndexes == null)
					leftIndex++;
				else { // Right-order advancement needs to skip over left indexes that have already been handled out of left order
					boolean wasCommonLeftUsed = false;
					while (true) {
						leftIndex++;
						if (leftIndex == leftToRight.length)
							break;
						else if (leftToRight[leftIndex] < 0) {
							if (!wasCommonLeftUsed)
								break;
						} else if (leftToRight[leftIndex] < rightIndex)
							wasCommonLeftUsed = true;
						else
							break;
					}
				}
				hasLeft = leftIter.hasNext();
				leftVal = hasLeft ? leftIter.next() : null;
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
				rightVal = hasRight ? rightIter.next() : null;
				return true;
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
			int updatedLeftIndex = -1;
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
			public int getUpdatedLeftIndex() {
				return updatedLeftIndex;
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
				hasLeft = true;
				leftVal = newValue;
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

	static class ConcatenatedCollection<T> extends AbstractCollection<T> {
		private final Collection<? extends Collection<? extends T>> theCollections;

		public ConcatenatedCollection(Collection<? extends Collection<? extends T>> collections) {
			theCollections = collections;
		}

		@Override
		public Iterator<T> iterator() {
			return IterableUtils.flatten(theCollections).iterator();
		}

		@Override
		public int size() {
			int size = 0;
			for (Collection<? extends T> coll : theCollections)
				size += coll.size();
			return size;
		}
	}
}
