package org.qommons.collect;

/**
 * Represents an entry in a {@link BetterMultiMap} containing a collection of values with a common key
 * 
 * @param <K> The type of the key
 * @param <V> The type of the value
 */
public interface MultiEntryHandle<K, V> extends MultiMap.MultiEntry<K, V>, CollectionElement<K> {
	@Override
	default K get() {
		return getKey();
	}

	@Override
	BetterCollection<V> getValues();

	@Override
	default MultiEntryHandle<K, V> reverse() {
		return new ReversedMultiEntryHandle<>(this);
	}

	class ReversedMultiEntryHandle<K, V> implements MultiEntryHandle<K, V> {
		private final MultiEntryHandle<K, V> theSource;

		public ReversedMultiEntryHandle(MultiEntryHandle<K, V> source) {
			theSource = source;
		}

		protected MultiEntryHandle<K, V> getSource() {
			return theSource;
		}

		@Override
		public ElementId getElementId() {
			return theSource.getElementId().reverse();
		}

		@Override
		public K getKey() {
			return theSource.getKey();
		}

		@Override
		public BetterCollection<V> getValues() {
			return theSource.getValues().reverse();
		}
	}

	public static <K, V> MultiEntryHandle<K, V> reverse(MultiEntryHandle<K, V> entry) {
		return entry == null ? null : entry.reverse();
	}
}
