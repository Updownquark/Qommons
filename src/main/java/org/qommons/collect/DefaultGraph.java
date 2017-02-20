package org.qommons.collect;

import java.util.*;

public class DefaultGraph<N, E> implements MutableGraph<N, E> {
	private final Collection<DefaultNode<N, E>> theNodes;
	private final Collection<DefaultEdge<N, E>> theEdges;

	public DefaultGraph() {
		theNodes = new LinkedList<>();
		theEdges = new LinkedList<>();
	}

	@Override
	public Collection<DefaultNode<N, E>> getNodes() {
		return Collections.unmodifiableCollection(theNodes);
	}

	@Override
	public Collection<DefaultEdge<N, E>> getEdges() {
		return Collections.unmodifiableCollection(theEdges);
	}

	@Override
	public Node<N, E> nodeFor(N value) {
		for (Node<N, E> node : theNodes)
			if (Objects.equals(node.getValue(), value))
				return node;
		return null;
	}

	@Override
	public DefaultNode<N, E> addNode(N value) {
		DefaultNode<N, E> newNode = new DefaultNode<>(this, value);
		theNodes.add(newNode);
		return newNode;
	}

	@Override
	public Collection<DefaultNode<N, E>> addNodes(Collection<? extends N> values) {
		ArrayList<DefaultNode<N, E>> added = new ArrayList<>(values.size());
		for (N value : values) {
			added.add(addNode(value));
		}
		return added;
	}

	@Override
	public DefaultEdge<N, E> addEdge(Node<N, E> start, Node<N, E> end, boolean directed, E value) {
		if (!(start instanceof DefaultNode) || ((DefaultNode<N, E>) start).theGraph != this)
			throw new IllegalArgumentException("Start node " + start + " is not a member of this graph");
		if (!(end instanceof DefaultNode) || ((DefaultNode<N, E>) end).theGraph != this)
			throw new IllegalArgumentException("End node " + start + " is not a member of this graph");
		DefaultNode<N, E> startNode = (DefaultNode<N, E>) start;
		DefaultNode<N, E> endNode = (DefaultNode<N, E>) end;
		if (startNode.isRemoved)
			throw new IllegalArgumentException("Start node " + start + " has been removed from this graph");
		if (endNode.isRemoved)
			throw new IllegalArgumentException("End node " + end + " has been removed from this graph");

		DefaultEdge<N, E> newEdge = new DefaultEdge<>(this, startNode, endNode, value, directed);
		theEdges.add(newEdge);
		startNode.theEdges.add(newEdge);
		endNode.theEdges.add(newEdge);
		return newEdge;
	}

	@Override
	public boolean removeNode(Node<N, E> node) {
		if (!(node instanceof DefaultNode) || ((DefaultNode<N, E>) node).theGraph != this || ((DefaultNode<N, E>) node).isRemoved)
			return false;
		DefaultNode<N, E> defNode = (DefaultNode<N, E>) node;
		for (DefaultEdge<N, E> edge : defNode.theEdges) {
			edge.isRemoved = true;
			if (edge.getStart() == defNode)
				edge.getEnd().theEdges.remove(edge);
			else
				edge.getStart().theEdges.remove(edge);
		}
		theEdges.removeAll(defNode.theEdges);
		defNode.theEdges.clear();
		defNode.isRemoved = true;
		theNodes.remove(defNode);
		return true;
	}

	@Override
	public boolean removeEdge(Edge<N, E> edge) {
		if (!(edge instanceof DefaultEdge) || ((DefaultEdge<N, E>) edge).theGraph != this || ((DefaultEdge<N, E>) edge).isRemoved)
			return false;
		DefaultEdge<N, E> defEdge = (DefaultEdge<N, E>) edge;
		defEdge.getStart().theEdges.remove(defEdge);
		defEdge.getEnd().theEdges.remove(defEdge);
		defEdge.isRemoved = true;
		theEdges.remove(defEdge);
		return true;
	}

	@Override
	public DefaultNode<N, E> replaceNode(Node<N, E> node, N newValue) {
		if (!(node instanceof DefaultNode) || ((DefaultNode<N, E>) node).theGraph != this)
			throw new IllegalArgumentException("Node " + node + " is not a member of this graph");
		DefaultNode<N, E> defNode = (DefaultNode<N, E>) node;
		if (defNode.isRemoved)
			throw new IllegalArgumentException("Node " + node + " has been removed from this graph");
		DefaultNode<N, E> newNode = addNode(newValue);

		for (DefaultEdge<N, E> oldEdge : defNode.theEdges) {
			DefaultEdge<N, E> newEdge;
			ListIterator<DefaultEdge<N, E>> edgeIter;
			if (oldEdge.getStart() == defNode) {
				newEdge = new DefaultEdge<>(this, newNode, oldEdge.getEnd(), oldEdge.getValue(), oldEdge.isDirected);
				edgeIter = oldEdge.getEnd().theEdges.listIterator();
			} else {
				newEdge = new DefaultEdge<>(this, oldEdge.getStart(), newNode, oldEdge.getValue(), oldEdge.isDirected);
				edgeIter = oldEdge.getStart().theEdges.listIterator();
			}
			while (edgeIter.hasNext()) {
				if (edgeIter.next() == oldEdge) {
					edgeIter.set(newEdge);
					break;
				}
			}
		}
		return newNode;
	}

	@Override
	public void clear() {
		for (DefaultEdge<N, E> edge : theEdges)
			edge.isRemoved = true;
		for (DefaultNode<N, E> node : theNodes) {
			node.theEdges.clear();
			node.isRemoved = true;
		}
		theEdges.clear();
		theNodes.clear();
	}

	@Override
	public void clearEdges() {
		theEdges.clear();
	}

	public static class DefaultNode<N, E> implements Graph.Node<N, E> {
		final DefaultGraph<N, E> theGraph;
		private final N theValue;
		final LinkedList<DefaultEdge<N, E>> theEdges;
		boolean isRemoved;

		DefaultNode(DefaultGraph<N, E> graph, N value) {
			theGraph = graph;
			theValue = value;
			theEdges = new LinkedList<>();
		}

		@Override
		public Collection<DefaultEdge<N, E>> getEdges() {
			return Collections.unmodifiableCollection(theEdges);
		}

		@Override
		public N getValue() {
			return theValue;
		}
	}

	public static class DefaultEdge<N, E> implements Graph.Edge<N, E> {
		final DefaultGraph<N, E> theGraph;
		private final DefaultNode<N, E> theStart;
		private final DefaultNode<N, E> theEnd;
		private final E theValue;
		private final boolean isDirected;
		boolean isRemoved;

		DefaultEdge(DefaultGraph<N, E> graph, DefaultNode<N, E> start, DefaultNode<N, E> end, E value, boolean directed) {
			theGraph = graph;
			theStart = start;
			theEnd = end;
			theValue = value;
			isDirected = directed;
		}

		@Override
		public DefaultNode<N, E> getStart() {
			return theStart;
		}

		@Override
		public DefaultNode<N, E> getEnd() {
			return theEnd;
		}

		@Override
		public boolean isDirected() {
			return isDirected;
		}

		@Override
		public E getValue() {
			return theValue;
		}
	}
}
