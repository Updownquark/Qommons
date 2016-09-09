package org.qommons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A binary search class that makes it easy to find the best result of a computation on a list of items. This class never sorts its items;
 * the items must be pre-sorted according to the type of search desired.
 * 
 * @param <S> The type of the items to search
 */
public class BinarySearch<S> {
	/**
	 * Represents an item in a search
	 * 
	 * @param <S> The type of the search item
	 */
	public interface BinarySearchElement<S> {
		/** @return The index in the search that this element occupies */
		int getSearchIndex();
		/** @return The item in the search that this element represents */
		S getSearchItem();

		/**
		 * Allows calling code to save the results of intermediate calculations used to determine evaluation results
		 * 
		 * @param key The name to store the value by
		 * @param value The value to store for the key
		 * @return This element, for chaining
		 */
		BinarySearchElement<S> putValue(String key, Object value);

		/** Allows calling code to signal that further searching is not needed. This element will be used for the returned result. */
		void endSearch();
	}

	/**
	 * Contains the results of a search operation
	 * 
	 * @param <S> The type of items that were searched
	 */
	public interface BinarySearchResult<S> {
		/** @return The binary search that was searched to generate this result */
		BinarySearch<S> getSearch();
		/** @return The index of the result in the search */
		int getResultIndex();
		/** @return The item at the result index in the search */
		S getResultItem();
		/** @return Whether the search item passed the evaluator. If false, this is the first item in the search. */
		boolean isMatch();

		/**
		 * @return A map containing all values {@link BinarySearch.BinarySearchElement#putValue(String, Object) added} on the
		 *         {@link BinarySearch.BinarySearchElement element} for the result.
		 */
		Map<String, Object> getValues();
		/**
		 * Gets a value that was {@link BinarySearch.BinarySearchElement#putValue(String, Object) added} on the
		 * {@link BinarySearch.BinarySearchElement element} for the result.
		 * 
		 * @param <T> The compile-time type of the value to get
		 * @param key The name under which the value was added
		 * @param type The run-time class of the value to get
		 * @return The value stored for the given key in the result's element
		 */
		<T> T getValue(String key, Class<T> type);
	}

	private final List<S> theSearchItems;

	/** @param searchItems The items to search through */
	public BinarySearch(List<S> searchItems) {
		theSearchItems = Collections.unmodifiableList(new ArrayList<>(searchItems));
	}

	/** @param searchItems The items to search through */
	public BinarySearch(S... searchItems) {
		this(Arrays.asList(searchItems));
	}

	/** @return The items that this search searches */
	public List<S> getSearchItems() {
		return theSearchItems;
	}

	/**
	 * Locates a value in the list of items. The items are assumed to be pre-sorted by the given comparator.
	 * 
	 * @param value The value to search for
	 * @param compare The comparator to search by
	 * @return The index of the search key, if it is contained in the list; otherwise, (-(insertion point) - 1). The insertion point is
	 *         defined as the point at which the key would be inserted into the list: the index of the first element greater than the key,
	 *         or list.size() if all elements in the list are less than the specified key. Note that this guarantees that the return value
	 *         will be >= 0 if and only if the key is found.
	 * @see Collections#binarySearch(List, Object, Comparator)
	 */
	public int findValue(S value, Comparator<S> compare) {
		BinarySearchResult<S> result = getHighestMatch(el -> compare.compare(el.getSearchItem(), value) <= 0);
		int resultCompare = compare.compare(result.getResultItem(), value);
		if (resultCompare == 0)
			return result.getResultIndex();
		else if (resultCompare < 0)
			return -(result.getResultIndex() + 2);
		else
			return -1;
	}

	/**
	 * Gets the search result on the highest-index element in this search that passes the evaluator, or the result on the first item in this
	 * search if no items pass the test
	 * 
	 * @param evaluator The test for each search item
	 * @return The search result, which the result value being whether the item passed the test
	 */
	public BinarySearchResult<S> getHighestMatch(Predicate<BinarySearchElement<S>> evaluator) {
		BinarySearchElementImpl<S> bestElement = null;
		int min = 0, max = theSearchItems.size() - 1;
		boolean match = false;
		while (min < max) {
			int mid = (min + max + 1) / 2;
			BinarySearchElementImpl<S> element = new BinarySearchElementImpl<>(mid, theSearchItems.get(mid));
			match = evaluator.test(element);
			if (match) {
				bestElement = element;
				min = mid;
			} else
				max = mid - 1;
			if (element.isSearchEnded)
				break;
		}
		// min is the correct result index, but the binary search above will exit once it determines the index without necessarily
		// evaluating the result
		if (bestElement != null && bestElement.getSearchIndex() == min)
			return new EvaluatedBinarySearchResult(min, theSearchItems.get(min), match, bestElement.theValues);
		else
			return new LazyBinarySearchResult(min, theSearchItems.get(min), evaluator);
	}

	private static class BinarySearchElementImpl<S> implements BinarySearchElement<S> {
		private final int theSearchIndex;
		private final S theSearchItem;
		Map<String, Object> theValues;
		boolean isSearchEnded;

		BinarySearchElementImpl(int searchIndex, S searchItem) {
			theSearchIndex = searchIndex;
			theSearchItem = searchItem;
		}

		@Override
		public int getSearchIndex() {
			return theSearchIndex;
		}

		@Override
		public S getSearchItem() {
			return theSearchItem;
		}

		@Override
		public BinarySearchElement<S> putValue(String key, Object value) {
			if (theValues == null)
				theValues = new HashMap<>();
			theValues.put(key, value);
			return this;
		}

		@Override
		public void endSearch() {
			isSearchEnded = true;
		}
	}

	private abstract class BinarySearchResultImpl implements BinarySearchResult<S> {
		private final int theResultIndex;
		private final S theResultItem;

		BinarySearchResultImpl(int resultIndex, S resultItem) {
			theResultIndex = resultIndex;
			theResultItem = resultItem;
		}

		@Override
		public BinarySearch<S> getSearch() {
			return BinarySearch.this;
		}

		@Override
		public int getResultIndex() {
			return theResultIndex;
		}

		@Override
		public S getResultItem() {
			return theResultItem;
		}
	}

	private class EvaluatedBinarySearchResult extends BinarySearchResultImpl {
		private final boolean isMatch;
		private final Map<String, Object> theValues;

		EvaluatedBinarySearchResult(int resultIndex, S resultItem, boolean match, Map<String, Object> values) {
			super(resultIndex, resultItem);
			isMatch = match;
			theValues = values == null ? Collections.emptyMap() : Collections.unmodifiableMap(values);
		}

		@Override
		public boolean isMatch() {
			return isMatch;
		}

		@Override
		public Map<String, Object> getValues() {
			return theValues;
		}

		@Override
		public <T> T getValue(String key, Class<T> type) {
			if (type == null)
				return (T) theValues.get(key);
			return type.cast(theValues.get(key));
		}
	}

	private class LazyBinarySearchResult extends BinarySearchResultImpl {
		private final Predicate<BinarySearchElement<S>> theEvaluator;
		private boolean isEvaluated;
		private boolean isMatch;
		private Map<String, Object> theValues;

		LazyBinarySearchResult(int resultIndex, S resultItem, Predicate<BinarySearchElement<S>> evaluator) {
			super(resultIndex, resultItem);
			theEvaluator = evaluator;
		}

		private void evaluate() {
			if (isEvaluated)
				return;
			isEvaluated = true;
			BinarySearchElementImpl<S> element = new BinarySearchElementImpl<>(getResultIndex(), getResultItem());
			isMatch = theEvaluator.test(element);
			theValues = element.theValues == null ? Collections.emptyMap() : Collections.unmodifiableMap(element.theValues);
		}

		@Override
		public boolean isMatch() {
			evaluate();
			return isMatch;
		}

		@Override
		public Map<String, Object> getValues() {
			evaluate();
			return theValues;
		}

		@Override
		public <T> T getValue(String key, Class<T> type) {
			evaluate();
			if (type == null)
				return (T) theValues.get(key);
			return type.cast(theValues.get(key));
		}
	}
}
