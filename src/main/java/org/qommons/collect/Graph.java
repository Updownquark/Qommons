package org.qommons.collect;

import java.util.Collection;
import java.util.stream.Collectors;

public interface Graph<N, E> {
	/**
	 * A node in a graph
	 *
	 * @param <N> The type of values stored in the nodes of the graph
	 * @param <E> The type of values stored in the edges of the graph
	 */
	interface Node<N, E> {
		/** @return All edges that go to or from this node */
		Collection<? extends Edge<N, E>> getEdges();

		/** @return The value associated with this node */
		N getValue();

		/** @return The collection of edges going outward from this node */
		default Collection<? extends Edge<N, E>> getOutward() {
			return getEdges().stream().filter(edge -> edge.getStart() == Node.this).collect(Collectors.toList());
		}

		/** @return The collection of edges going inward toward this node */
		default Collection<? extends Edge<N, E>> getInward() {
			return getEdges().stream().filter(edge -> edge.getEnd() == Node.this).collect(Collectors.toList());
		}
	}

	/**
	 * An edge between two nodes in a graph
	 *
	 * @param <N> The type of values stored in the nodes of the graph
	 * @param <E> The type of values stored in the edges of the graph
	 */
	interface Edge<N, E> {
		/** @return The node that this edge starts from */
		Node<N, E> getStart();

		/** @return The node that this edge goes to */
		Node<N, E> getEnd();

		/**
		 * @return Whether this graph edge is to be interpreted as directional, i.e. if true, this edge does not represent a connection from
		 *         {@link #getEnd() end} to {@link #getStart() start}.
		 */
		boolean isDirected();

		/** @return The value associated with this edge */
		E getValue();
	}

	/** @return An observable collection containing all nodes stored in this graph */
	Collection<? extends Node<N, E>> getNodes();

	/** @return An observable collection containing all edges stored in this graph */
	Collection<? extends Edge<N, E>> getEdges();
}
