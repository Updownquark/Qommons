package org.qommons.tree;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Maintains a collection of trees of a common type representing related data
 *
 * @param <N> The type of nodes in this tree
 * @param <V> The type of values held by nodes in this tree
 */
public class TreeBuilder<N extends Tree<V, N, ?>, V> {
	private final Collection<N> theRoots;

	private final Comparator<? super V> theCompare;

	private final Function<? super V, V> theParentGetter;

	/** @param parentGetter Gets the logical parent value of a value */
	public TreeBuilder(Function<? super V, V> parentGetter) {
		this(null, parentGetter);
	}

	/**
	 * @param compare Allows sorting of values
	 * @param parentGetter Gets the logical parent value of a value
	 */
	public TreeBuilder(Comparator<? super V> compare, Function<? super V, V> parentGetter) {
		if (compare == null)
			theRoots = new TreeSet<>();
		else
			theRoots = new TreeSet<>(new Comparator<N>(){
				@Override
				public int compare(N n1, N n2){
					return compare.compare(n1.getValue(), n2.getValue());
				}
			});
		theCompare = compare;
		theParentGetter = parentGetter;
	}

	/** @return The number of nodes in this tree */
	public int size() {
		int ret = 0;
		for (N root : theRoots)
			ret += root.size();
		return ret;
	}

	/** @return A collection of this tree's nodes */
	public Collection<N> nodes() {
		return new AbstractCollection<N>() {
			@Override
			public Iterator<N> iterator() {
				return new Iterator<N>() {
					private LinkedList<Iterator<N>> theStack;

					{
						theStack = new LinkedList<>();
						theStack.add(theRoots.iterator());
					}

					@Override
					public boolean hasNext() {
						while (!theStack.isEmpty() && !theStack.getLast().hasNext())
							theStack.removeLast();
						return !theStack.isEmpty();
					}

					@Override
					public N next() {
						if (!hasNext())
							throw new NoSuchElementException();
						N ret = theStack.getLast().next();
						theStack.add(ret.getChildren().iterator());
						return ret;
					}

					@Override
					public void remove() {
						theStack.getLast().remove();
					}
				};
			}

			@Override
			public int size() {
				return TreeBuilder.this.size();
			}
		};
	}

	/** @return An iterable over this tree's values */
	public Iterable<V> values() {
		Iterable<N> nodes = nodes();
		return new Iterable<V>(){
			@Override
			public Iterator<V> iterator(){
				return new Iterator<V>() {
					private final Iterator<N> backing = nodes.iterator();
		
					@Override
					public boolean hasNext() {
						return backing.hasNext();
					}
		
					@Override
					public V next() {
						return backing.next().getValue();
					}
		
					@Override
					public void remove() {
						backing.remove();
					}
				};
			}
		};
	}

	/** Removes all nodes from this tree */
	public void clear() {
		theRoots.clear();
	}

	/** @param node The root value to add to this tree */
	public void addRoot(N node) {
		theRoots.add(node);
	}

	/** @param value The value to remove from this tree */
	public void remove(V value) {
		Object[] nap = getNodeAndParent(value, null);
		if (nap != null) {
			N node = (N) nap[0];
			N parent = (N) nap[1];
			if (parent != null)
				parent.getChildren().remove(node);
			else
				theRoots.remove(node);
		}
	}

	/**
	 * @param value The value to add to this structure
	 * @param creator Creates a node from the value if needed
	 * @return The node that the value is not stored in
	 */
	public N getNode(V value, BiFunction<V, N, N> creator) {
		Object[] nap = getNodeAndParent(value, creator);
		if (nap == null)
			return null;
		return (N) nap[0];
	}

	private Object[] getNodeAndParent(V value, BiFunction<V, N, N> creator) {
		List<V> descent = getDescent(value);
		return getNodeAndParent(null, theRoots, descent, creator);
	}

	private Object[] getNodeAndParent(N parent, Collection<N> nodes, List<V> descent, BiFunction<V, N, N> creator) {
		N found = null;
		for(N child : nodes) {
			if(child.getValue().equals(descent.get(0))) {
				found = child;
				break;
			}
		}
		if(found == null) {
			if(creator != null) {
				found = creator.apply(descent.get(0), parent);
				nodes.add(found);
			} else
				return null;
		}
		descent.remove(0);
		if(descent.isEmpty()) {
			return new Object[] { found, parent };
		} else {
			return getNodeAndParent(found, found.getChildren(), descent, creator);
		}
	}

	private List<V> getDescent(V value) {
		List<V> ret = new LinkedList<>();
		while(value != null) {
			ret.add(0, value);
			value = theParentGetter.apply(value);
		}
		return ret;
	}

	/**
	 * Makes a copy of this tree
	 * 
	 * @param nodeCopier Creates new nodes given the node to copy (first argument) and the parent for the new node (second argument)
	 * @return The copied tree structure
	 */
	public TreeBuilder<N, V> copy(BiFunction<N, N, N> nodeCopier) {
		TreeBuilder<N, V> ret = new TreeBuilder<>(theCompare, theParentGetter);
		for (N root : theRoots)
			ret.theRoots.add(copyNode(root, null, nodeCopier));
		return ret;
	}

	private N copyNode(N node, N parent, BiFunction<N, N, N> nodeCopier) {
		N ret = nodeCopier.apply(node, parent);
		Collection<N> nodeChildren = node.getChildren();
		for (N child : nodeChildren) {
			ret.getChildren().add(copyNode(child, ret, nodeCopier));
		}
		return ret;
	}
}
