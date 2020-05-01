package org.qommons;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.qommons.collect.CircularArrayList;

/**
 * This class uses the Dijkstra algorithm to find the best solution to a problem that can be expressed as a graph
 * 
 * @param <S> The solution type to solve
 */
public class DijkstraSolver<S> {
	/**
	 * Provides cost functions and other information specific to the problem being solved
	 * 
	 * @param <S> The solution type
	 */
	public interface DijkstraCostFunction<S> {
		/**
		 * @param solution The solution to cost
		 * @return The fundamental cost of the solution by itself
		 */
		double getCellCost(S solution);

		/**
		 * @param source The source solution
		 * @param dest The target solution
		 * @return The distance cost of traveling from <code>source</code> to <code>dest</code>
		 */
		double getDistanceCost(S source, S dest);

		/**
		 * @param source The source solution
		 * @param dest The target solution
		 * @return Any cost traversing from <code>source</code> to <code>dest</code> that is not related to distance
		 */
		double getTraversalCost(S source, S dest);

		/**
		 * @param solution The solution to test
		 * @return The quality of the solution
		 */
		double getQuality(S solution);

		/**
		 * @param solution The source solution
		 * @param onNeighbor The consumer to accept all neighbors of the given solution
		 */
		void getNeighbors(S solution, Consumer<S> onNeighbor);
	}

	/** Represents a couple tweaks to the algorithm */
	public enum SolverMode {
		/** A faster, simpler method */
		CELL_BASED,
		/** Accounts for cost of travel through high-cost cells better */
		PATH_BASED;
	}

	private final DijkstraCostFunction<S> theCost;
	private final CircularArrayList<SolutionRoute<S>> theSolutionQueue;
	private final Set<S> theVisited;
	private SolverMode theMode;

	/**
	 * @param cost The cost function for the problem
	 * @param initial The initial solution for the problem (or null to initialize manually)
	 */
	public DijkstraSolver(DijkstraCostFunction<S> cost, S initial) {
		theCost = cost;
		theSolutionQueue = CircularArrayList.build().build();
		theVisited = new HashSet<>();
		theMode = SolverMode.PATH_BASED;
		if (initial != null)
			withInitial(initial);
	}

	/**
	 * @param mode The solver mode to use
	 * @return This solver
	 */
	public DijkstraSolver<S> setMode(SolverMode mode) {
		theMode = mode;
		return this;
	}

	/**
	 * @param initial The initial solutions to add
	 * @return This solver
	 */
	public DijkstraSolver<S> withInitial(S... initial) {
		for (S s : initial) {
			if (theVisited.add(s))
				theSolutionQueue.add(new SolutionRoute<>(s, null, 0));
		}
		return this;
	}

	/**
	 * @param initial The initial solution to add
	 * @param cost The cost for the solution
	 * @return This solver
	 */
	public DijkstraSolver<S> withInitial(S initial, double cost) {
		if (theVisited.add(initial))
			theSolutionQueue.add(new SolutionRoute<>(initial, null, cost));
		return this;
	}

	/**
	 * <p>
	 * Runs the solver, returning the first solution encountered with an infinite {@link DijkstraCostFunction#getQuality(Object) quality},
	 * or the highest-quality solution after all finite-cost possibilities are exhausted.
	 * </p>
	 * <p>
	 * This method may be run repeatedly if multiple infinite-quality solutions may exist.
	 * </p>
	 * 
	 * @return The solution
	 */
	public SolutionRoute<S> getNextBestSolution() {
		SolutionRoute<S> nextRoute = theSolutionQueue.poll();
		SolutionRoute<S> bestRoute = null;
		double bestQuality = Double.NEGATIVE_INFINITY;
		while (nextRoute != null) {
			// System.out.println(nextRoute);
			double quality = theCost.getQuality(nextRoute.solution);
			if (quality > 0 && Double.isInfinite(quality))
				return nextRoute;
			else if (quality > bestQuality) {
				bestRoute = nextRoute;
				bestQuality = quality;
			}
			SolutionRoute<S> r = nextRoute;
			theCost.getNeighbors(nextRoute.solution, neighbor -> {
				if (theVisited.add(neighbor))
					add(r, neighbor);
			});

			nextRoute = theSolutionQueue.poll();
		}
		return bestRoute;
	}

	private void add(SolutionRoute<S> source, S dest) {
		double cost = getLinkCost(source, dest);
		if (Double.isInfinite(cost) || Double.isNaN(cost))
			return;
		SolutionRoute<S> newSolution = new SolutionRoute<>(dest, source, source.cost + cost);
		int index = ArrayUtils.binarySearch(theSolutionQueue, r -> Double.compare(newSolution.cost, r.cost));
		if (index < 0) {
			index = -index - 1;
		} else {
			while (index < theSolutionQueue.size() && theSolutionQueue.get(index).cost == newSolution.cost) {
				index++;
			}
		}
		theSolutionQueue.add(index, newSolution);
	}

	/**
	 * @param source The source solution
	 * @param dest The neighbor solution
	 * @return The cost of the neighbor solution, in addition to the cost of the source
	 */
	protected double getLinkCost(SolutionRoute<S> source, S dest) {
		double dist = theCost.getDistanceCost(source.solution, dest);
		double cellCost = theCost.getCellCost(dest);
		double traverseCost = theCost.getTraversalCost(source.solution, dest);

		double totalLinkCost = 0;
		boolean hit = false;
		switch (theMode) {
		case CELL_BASED:
			totalLinkCost = dist + traverseCost + cellCost;
			hit = true;
			break;
		case PATH_BASED:
			double sourceCellCost = source.cost;
			if (source.previous != null)
				sourceCellCost -= source.previous.cost;
			double halfDist = dist / 2;
			double phase1Cost = halfDist * sourceCellCost;
			double phase2Cost = halfDist * cellCost;

			totalLinkCost = phase1Cost + phase2Cost + traverseCost;
			hit = true;
			break;
		}
		if (!hit)
			throw new IllegalStateException("Unrecognized mode: " + theMode);

		return totalLinkCost;
	}

	/**
	 * Represents a full solution route
	 * 
	 * @param <S> The solution type
	 */
	public static class SolutionRoute<S> {
		/** The solution */
		public final S solution;
		/** The previous section of the solution route */
		public final SolutionRoute<S> previous;
		/** The route cost of this solution */
		public final double cost;

		/**
		 * @param solution The solution
		 * @param previous The previous section of the solution route
		 * @param cost The route cost of this solution
		 */
		public SolutionRoute(S solution, SolutionRoute<S> previous, double cost) {
			this.solution = solution;
			this.previous = previous;
			this.cost = cost;
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			SolutionRoute<S> s = this;
			while (s != null) {
				if (str.length() > 0) {
					str.append("->");
				}
				str.append(s.solution);
				s = s.previous;
			}
			str.append(' ').append(cost);
			return str.toString();
		}
	}
}
