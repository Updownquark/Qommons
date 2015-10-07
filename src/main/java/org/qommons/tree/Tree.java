package org.qommons.tree;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Tree<N extends TreeNode<N, V>, V> {
	private final NavigableSet<N> theRoots;

	private final Comparator<? super V> theCompare;

	private final Function<? super V, V> theParentGetter;

	public Tree(Function<? super V, V> parentGetter) {
		this(null, parentGetter);
	}

	public Tree(Comparator<? super V> compare, Function<? super V, V> parentGetter) {
		if(compare == null) {
			theRoots = new TreeSet<>();
		} else {
			theRoots = new TreeSet<>((n1, n2) -> compare.compare(n1.getValue(), n2.getValue()));
		}
		theCompare = compare;
		theParentGetter = parentGetter;
	}

	public int size() {
		int ret = 0;
		for(N root : theRoots) {
			ret += size(root);
		}
		return ret;
	}

	private static int size(TreeNode<?, ?> node) {
		int ret = 1;
		for(TreeNode<?, ?> child : node.getChildren()) {
			ret += size(child);
		}
		return ret;
	}

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
						while(!theStack.isEmpty() && !theStack.getLast().hasNext()) {
							theStack.removeLast();
						}
						return !theStack.isEmpty();
					}

					@Override
					public N next() {
						if(!hasNext()) {
							throw new NoSuchElementException();
						}
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
				return Tree.this.size();
			}
		};
	}

	public Iterable<V> values() {
		Iterable<N> nodes = nodes();
		return () -> new Iterator<V>() {
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

	/** @param value The value to remove from this tree */
	public void remove(V value) {
		N node = getNode(value, null);
		if(node != null) {
			if(node.getParent() != null) {
				node.getParent().getChildren().remove(node);
			} else {
				theRoots.remove(node);
			}
		}
	}

	public void clear() {
		theRoots.clear();
	}

	public void addRoot(N node) {
		theRoots.add(node);
	}

	public N getNode(V type, BiFunction<V, N, N> creator) {
		List<V> descent = getDescent(type);
		return getNode(null, theRoots, descent, creator);
	}

	private N getNode(N parent, Collection<N> nodes, List<V> descent, BiFunction<V, N, N> creator) {
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
			} else {
				return null;
			}
		}
		descent.remove(0);
		if(descent.isEmpty()) {
			return found;
		} else {
			return getNode(found, found.getChildren(), descent, creator);
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

	public Tree<N, V> copy(BiFunction<N, N, N> nodeCopier) {
		Tree<N, V> ret = new Tree<>(theCompare, theParentGetter);
		for(N root : theRoots) {
			ret.theRoots.add(copy(root, null, nodeCopier));
		}
		return ret;
	}

	private N copy(N node, N parent, BiFunction<N, N, N> nodeCopier) {
		N ret = nodeCopier.apply(node, parent);
		for(N child : node.getChildren()) {
			ret.getChildren().add(copy(child, ret, nodeCopier));
		}
		return ret;
	}
}
