package org.qommons.collect;

import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.function.Consumer;

import org.qommons.Transaction;

public class DefaultBetterSpliterator<E> implements MutableElementSpliterator<E> {
	private final BetterCollection<E> theCollection;
	private final Comparator<? super E> theComparator;
	private final int theCharacteristics;
	private CollectionElement<E> theCurrent;
	private boolean currentIsNext;

	public DefaultBetterSpliterator(BetterCollection<E> collection, Comparator<? super E> compare, int characteristics, CollectionElement<E> current,
		boolean currentIsNext) {
		theCollection = collection;
		theComparator = compare;
		characteristics |= SIZED | ORDERED;
		if (compare != null)
			characteristics |= SORTED;
		theCharacteristics=characteristics;
		this.theCurrent = current;
		this.currentIsNext = currentIsNext;
	}

	protected BetterCollection<E> getCollection() {
		return theCollection;
	}

	protected CollectionElement<E> getCurrent() {
		return theCurrent;
	}

	protected boolean isCurrentNext() {
		return currentIsNext;
	}

	@Override
	public int characteristics() {
		return theCharacteristics;
	}

	@Override
	public long estimateSize() {
		return theCollection.size();
	}

	@Override
	public long getExactSizeIfKnown() {
		return estimateSize();
	}

	@Override
	public Comparator<? super E> getComparator() {
		return theComparator;
	}

	protected boolean tryElement(boolean right) {
		if (theCurrent == null) {
			theCurrent = theCollection.getTerminalElement(currentIsNext);
			currentIsNext = right;
		}
		if (theCurrent == null)
			return false;
		// We can tolerate external modification as long as the node that this spliterator is anchored to has not been removed
		// This situation is easy to detect
		if (!theCurrent.getElementId().isPresent())
			throw new ConcurrentModificationException(
				"The collection has been modified externally such that this spliterator has been orphaned");
		if (currentIsNext != right) {
			CollectionElement<E> next = theCollection.getAdjacentElement(theCurrent.getElementId(), right);
			if (next != null && isIncluded(next)) {
				theCurrent = next;
				currentIsNext = !right;
			} else
				return false;
		} else
			currentIsNext = !currentIsNext;
		return true;
	}

	protected boolean isIncluded(CollectionElement<E> element) {
		return true;
	}

	@Override
	public boolean forElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
		try (Transaction t = theCollection.lock(false, null)) {
			if (!tryElement(forward))
				return false;
			action.accept(theCurrent);
			return true;
		}
	}

	@Override
	public void forEachElement(Consumer<? super CollectionElement<E>> action, boolean forward) {
		try (Transaction t = theCollection.lock(false, null)) {
			while (tryElement(forward))
				action.accept(theCurrent);
		}
	}

	@Override
	public boolean forElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
		try (Transaction t = theCollection.lock(true, null)) {
			if (!tryElement(forward))
				return false;
			action.accept(wrap(theCollection.mutableElement(theCurrent.getElementId())));
			return true;
		}
	}

	@Override
	public void forEachElementM(Consumer<? super MutableCollectionElement<E>> action, boolean forward) {
		try (Transaction t = theCollection.lock(true, null)) {
			while (tryElement(forward))
				action.accept(//
					wrap(theCollection.mutableElement(theCurrent.getElementId())));
		}
	}

	@Override
	public MutableElementSpliterator<E> trySplit() {
		return null;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		CollectionElement<E> node = theCollection.getTerminalElement(true);
		if (theCurrent == null && currentIsNext)
			str.append('^');
		while (node != null) {
			if (theCurrent != null && node.getElementId().equals(theCurrent.getElementId())) {
				if (currentIsNext)
					str.append('^');
				str.append('[');
			}
			str.append(node.get());
			if (theCurrent != null && node.getElementId().equals(theCurrent.getElementId())) {
				str.append(']');
				if (!currentIsNext)
					str.append('^');
			}
			node = theCollection.getAdjacentElement(node.getElementId(), true);
			if (node != null)
				str.append(", ");
		}
		if (theCurrent == null && !currentIsNext)
			str.append('^');
		return str.toString();
	}

	private MutableCollectionElement<E> wrap(MutableCollectionElement<E> element) {
		class SpliteratorMutableElement implements MutableCollectionElement<E> {
			@Override
			public ElementId getElementId() {
				return element.getElementId();
			}

			@Override
			public E get() {
				return element.get();
			}

			@Override
			public BetterCollection<E> getCollection() {
				return element.getCollection();
			}

			@Override
			public String isEnabled() {
				return element.isEnabled();
			}

			@Override
			public String isAcceptable(E value) {
				return element.isAcceptable(value);
			}

			@Override
			public void set(E value) throws UnsupportedOperationException, IllegalArgumentException {
				element.set(value);
			}

			@Override
			public String canRemove() {
				return element.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				CollectionElement<E> newCurrent;
				boolean newNext;
				if (element.getElementId().equals(theCurrent.getElementId())) {
					newCurrent = theCollection.getAdjacentElement(theCurrent.getElementId(), true);
					newNext = newCurrent != null;
				} else {
					newCurrent = theCurrent;
					newNext = currentIsNext;
				}
				element.remove();
				theCurrent = newCurrent;
				currentIsNext = newNext;
			}

			@Override
			public int hashCode() {
				return element.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return element.equals(obj);
			}

			@Override
			public String toString() {
				return element.toString();
			}
		}
		return new SpliteratorMutableElement();
	}
}
