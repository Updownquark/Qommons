package org.qommons.collect;

import org.qommons.tree.BinaryTreeNode;
import org.qommons.tree.BetterTreeList;

/**
 * Although not every ObservableCollection must be indexed, all ObservableCollections must have some notion of order. All change events and
 * spliterator elements from ObservableCollections provide an ElementId that not only uniquely identifies the element in the collection, but
 * allows the element's order relative to other elements to be determined.
 *
 * The equivalence and ordering of ElementIds may not change with its contents or with any other property of the collection. The ElementId
 * must remain valid until the element is removed from the collection, including while the remove event is firing.
 *
 * A collection's iteration must follow this ordering scheme as well, i.e. the ID of each element from the
 * {@link ObservableCollection#spliterator()} method must be successively greater than the previous element
 *
 * @see ObservableCollectionElement#getElementId()
 * @see ObservableCollectionEvent#getElementId()
 */
public interface ElementId extends Comparable<ElementId> {
	/** @return An element ID that behaves like this one, but orders in reverse */
	default ElementId reverse() {
		class ReversedElementId implements ElementId {
			private final ElementId theWrapped;

			ReversedElementId(ElementId wrap) {
				theWrapped = wrap;
			}

			@Override
			public int compareTo(ElementId o) {
				return -theWrapped.compareTo(((ReversedElementId) o).theWrapped);
			}

			@Override
			public ElementId reverse() {
				return theWrapped;
			}

			@Override
			public int hashCode() {
				return theWrapped.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof ReversedElementId && theWrapped.equals(((ReversedElementId) obj).theWrapped);
			}

			@Override
			public String toString() {
				return theWrapped.toString();
			}
		}
		return new ReversedElementId(this);
	}

	static SimpleElementIdGenerator createSimpleIdGenerator() {
		return new SimpleElementIdGenerator();
	}

	class SimpleElementIdGenerator {
		private final BetterTreeList<Void> theIds;

		public SimpleElementIdGenerator() {
			theIds = new BetterTreeList<>(false);
		}

		public ElementId newId() {
			return new SimpleGeneratedId(theIds.addElement(null));
		}

		public ElementId newId(ElementId relative, boolean left) {
			return ((SimpleGeneratedId) relative).nextTo(left);
		}

		public void remove(ElementId id) {
			((SimpleGeneratedId) id).remove();
		}

		public ElementId get(int index) {
			return new SimpleGeneratedId(theIds.getNode(index));
		}

		public int getElementsBefore(ElementId id) {
			return ((SimpleGeneratedId) id).theNode.getNodesBefore();
		}

		public int getElementsAfter(ElementId id) {
			return ((SimpleGeneratedId) id).theNode.getNodesAfter();
		}

		public int size() {
			return theIds.size();
		}

		public boolean isEmpty() {
			return theIds.isEmpty();
		}

		private class SimpleGeneratedId implements ElementId {
			private final BinaryTreeNode<Void> theNode;
			private int theRemovedIndex; // Keeps state after this node has been removed (while remove event is firing)

			SimpleGeneratedId(BinaryTreeNode<Void> node) {
				theNode = node;
				theRemovedIndex = -1;
			}

			@Override
			public int compareTo(ElementId o) {
				if (theNode == ((SimpleGeneratedId) o).theNode)
					return 0;
				int otherIndex = ((SimpleGeneratedId) o).theNode.getNodesBefore();
				if (theRemovedIndex < 0)
					return theNode.getNodesBefore() - otherIndex;
				else if (theRemovedIndex != otherIndex)
					return theRemovedIndex - otherIndex;
				else
					return -1; // We were before the node that replaced us at this index

			}

			SimpleGeneratedId nextTo(boolean left) {
				return new SimpleGeneratedId(
					left ? theIds.mutableNodeFor(theNode).add(null, true) : theIds.mutableNodeFor(theNode).add(null, false));
			}

			void remove() {
				theRemovedIndex = theNode.getNodesBefore();
				theIds.mutableNodeFor(theNode).remove();
			}

			@Override
			public int hashCode() {
				return theNode.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof SimpleGeneratedId && theNode.equals(((SimpleGeneratedId) obj).theNode);
			}

			@Override
			public String toString() {
				return "[" + theNode.getNodesBefore() + "]";
			}
		}
	}

	static ElementId reverse(ElementId id) {
		return id == null ? null : id.reverse();
	}
}