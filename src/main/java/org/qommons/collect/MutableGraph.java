package org.qommons.collect;

import java.util.Collection;

public interface MutableGraph<N, E> extends Graph<N, E> {
	/**
	 * Adds a node to the graph
	 *
	 * @param value The value for the node to have
	 * @return The node that was created and added
	 */
	Node<N, E> addNode(N value);

	/**
	 * Adds a collection of nodes to a graph
	 *
	 * @param values The node values to add
	 */
	Collection<? extends Node<N, E>> addNodes(Collection<? extends N> values);

	/**
	 * Adds an edge between two nodes which must already be in the graph. The nodes cannot be the same.
	 *
	 * @param start The node for the edge to start at
	 * @param end The node for the edge to end at
	 * @param directed Whether the edge is directed (i.e. one-way)
	 * @param value The value to associate with the new edge
	 * @return The edge that was created and added
	 */
	Edge<N, E> addEdge(Node<N, E> start, Node<N, E> end, boolean directed, E value);

	/**
	 * @param node The node to remove from the graph along with all its edges
	 * @return Whether the node was found in the graph
	 */
	boolean removeNode(Node<N, E> node);

	/**
	 * @param edge The edge to remove from the graph
	 * @return Whether the edge was found in the graph
	 */
	boolean removeEdge(Edge<N, E> edge);

	/**
	 * Replaces a node in the graph with a new node having a different value. This method is useful because the value of a node cannot be
	 * directly modified. All edges referring to the given node will be replaced with equivalent edges referring to the new node.
	 *
	 * @param node The node to replace
	 * @param newValue The value for the new node to have
	 * @return The node that was created and added
	 */
	Node<N, E> replaceNode(Node<N, E> node, N newValue);

	/** Removes all nodes and edges from this graph */
	void clear();

	/** Removes all edges from this graph */
	void clearEdges();

	/** @return An immutable graph backed by this graph's data */
	default Graph<N, E> immutable() {
		return new Graph<N, E>() {
			@Override
			public Collection<? extends Node<N, E>> getNodes() {
				return MutableGraph.this.getNodes();
			}

			@Override
			public Collection<? extends Edge<N, E>> getEdges() {
				return MutableGraph.this.getEdges();
			}
		};
	}
}
