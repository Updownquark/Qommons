package org.qommons.collect;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A set of nodes, interconnected by a set of edges, each of which may or may not be directed. Each node and each edge in the graph may have
 * a value.
 * 
 * @param <N> The type of values stored in this graph's nodes
 * @param <E> The type of values stored in this graph's edges
 */
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

	/**
	 * Receives steps of a graph walk
	 * 
	 * @param <N> The type of node value this walker can understand
	 * @param <E> The type of edge value this walker can understand
	 */
	interface GraphWalkListener<N, E> {
		/**
		 * @param from The node being walked from. This may be start or the end of the node if backward walking is enabled.
		 * @param path The edge being walked
		 * @param to The node being walked to. This may be the start or the end of the node if backward walking is enabled.
		 * @return Whether the walk should continue after this step
		 */
		boolean step(Node<? extends N, ? extends E> from, Edge<? extends N, ? extends E> path, Node<? extends N, ? extends E> to);
	}

	/** @return An observable collection containing all nodes stored in this graph */
	Collection<? extends Node<N, E>> getNodes();

	/** @return An observable collection containing all edges stored in this graph */
	Collection<? extends Edge<N, E>> getEdges();

	/**
	 * @param value The node value to get the node for
	 * @return The node in this graph with the given value, or null if no such node exists in this graph
	 */
	Node<N, E> nodeFor(N value);

	/**
	 * Walks this graph from a starting point, stepping through each edge connected to the given node
	 * 
	 * @param start The starting point of the walk
	 * @param onlyForward Whether to prevent walking backward along directed edges
	 * @param listener The listener to be notified of each step in the walk
	 * @return Whether the entire sub-graph connected to the given starting point was walked through
	 */
	default boolean walk(Node<N, E> start, boolean onlyForward, GraphWalkListener<? super N, ? super E> listener) {
		return new Walker<N, E>(listener).walk(start, onlyForward);
	}

	/**
	 * Walks this graph completely, stepping through each edge
	 * 
	 * @param onlyForward Whether to prevent walking backward along directed edges
	 * @param listener The listener to be notified of each step in the walk
	 * @return Whether the entire graph was walked through
	 */
	default boolean walkAll(boolean onlyForward, GraphWalkListener<? super N, ? super E> listener) {
		Walker<N, E> walker = new Walker<>(listener);
		boolean finished = true;
		for (Node<N, E> node : getNodes()) {
			finished = walker.walk(node, onlyForward);
			if (finished)
				break;
		}
		return finished;
	}

	/**
	 * @param subGraphSupplier A function to generate new graphs for each independent sub-graph in this graph
	 * @return A list of sub-graphs, totalling all nodes and edges in this graph, which are not connected by any edges in this graph
	 */
	default List<MutableGraph<N, E>> split(
		Function<? super Node<? extends N, ? extends E>, ? extends MutableGraph<N, E>> subGraphSupplier) {
		class SubGraphBuilder implements Graph.GraphWalkListener<N, E> {
			private final MutableGraph<N, E> theSubGraph;
			/** For caching. Makes retrieval of the from node copy in the sub-graph quicker for linear walks */
			private Node<N, E> lastNode;

			SubGraphBuilder(MutableGraph<N, E> subGraph, Node<? extends N, ? extends E> node) {
				theSubGraph = subGraph;
				lastNode = subGraph.addNode(node.getValue());
			}

			@Override
			public boolean step(Node<? extends N, ? extends E> from, Graph.Edge<? extends N, ? extends E> path,
				Node<? extends N, ? extends E> to) {
				Node<N, E> fromNode = lastNode.getValue() == from.getValue() ? lastNode : theSubGraph.nodeFor(from.getValue());
				if (fromNode == null)
					return false;
				Node<N, E> toNode = theSubGraph.nodeFor(to.getValue());
				if (toNode == null)
					toNode = theSubGraph.addNode(to.getValue());
				theSubGraph.addEdge(fromNode, toNode, false, path.getValue());
				lastNode = toNode;
				return true;
			}
		}
		
		List<MutableGraph<N, E>> subGraphs = new LinkedList<>();
		Walker<N, E>[] walker = new Walker[1];
		GraphWalkListener<N, E> outerListener = new GraphWalkListener<N, E>() {
			private SubGraphBuilder theBuilder;

			@Override
			public boolean step(Node<? extends N, ? extends E> from, Edge<? extends N, ? extends E> path,
				Node<? extends N, ? extends E> to) {
				if (theBuilder == null || !theBuilder.step(from, path, to)) {
					theBuilder = createNewSubGraph(from);
					theBuilder.step(from, path, to);
				}
				return true;
			}

			private SubGraphBuilder createNewSubGraph(Node<? extends N, ? extends E> from) {
				MutableGraph<N, E> newGraph = subGraphSupplier.apply(from);
				subGraphs.add(newGraph);
				return new SubGraphBuilder(newGraph, newGraph.addNode(from.getValue()));
			}
		};
		walker[0] = new Walker<>(outerListener);
		for (Node<N, E> node : getNodes())
			walker[0].walk(node, true);
		return Collections.unmodifiableList(subGraphs);
	}

	/**
	 * A helper for walking a graph
	 * 
	 * @param <N> Types of node-values this walker supports
	 * @param <E> Types of edge-values this walker supports
	 */
	class Walker<N, E> {
		private static class VisitingStruct<N, E> {
			final Node<N, E> from;
			final Iterator<? extends Edge<N, E>> edgeIter;

			VisitingStruct(Node<N, E> node) {
				from = node;
				edgeIter = node.getEdges().iterator();
			}
		}

		private final GraphWalkListener<? super N, ? super E> theListener;

		private final Set<Node<N, E>> theVisited;

		/** @param listener The listener to notify for each step */
		public Walker(GraphWalkListener<? super N, ? super E> listener) {
			theListener = listener;
			theVisited = new HashSet<>();
		}

		/**
		 * Walks through all edges of the given node to previously unvisited edges
		 * 
		 * @param start The node to start walking from
		 * @param onlyForward Whether to prevent backward walking along directed edges
		 * @return Whether the graph was walked completely through the node's connected edges that have not already been visited
		 */
		public boolean walk(Node<N, E> start, boolean onlyForward) {
			if (!theVisited.add(start))
				return true;
			List<VisitingStruct<N, E>> visiting = new ArrayList<>();
			visiting.add(new VisitingStruct<>(start));

			outer: while (!visiting.isEmpty()) {
				while (!visiting.get(visiting.size() - 1).edgeIter.hasNext()) {
					visiting.remove(visiting.size() - 1);
					continue outer;
				}
				VisitingStruct<N, E> top = visiting.get(visiting.size() - 1);
				Edge<N, E> nextEdge = top.edgeIter.next();
				Node<N, E> nextNode;
				if (nextEdge.getStart() == top.from)
					nextNode = nextEdge.getEnd();
				else if (onlyForward && nextEdge.isDirected())
					continue;
				else
					nextNode = nextEdge.getStart();
				if (!theListener.step(top.from, nextEdge, nextNode))
					return false;
				if (theVisited.add(nextNode))
					visiting.add(new VisitingStruct<>(nextNode));
			}
			return true;
		}

		public boolean hasVisited(Node<N, E> node) {
			return theVisited.contains(node);
		}
	}
}
