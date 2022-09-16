package org.qommons;

import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * <p>
 * A class for generating and testing primes.
 * </p>
 * <p>
 * This class uses <a href="https://en.wikipedia.org/wiki/Sieve_of_Eratosthenes">The Sieve of Eratosthenes</a>.
 * </p>
 * <p>
 * The class is able to use any work done thus far when generating new primes.
 * </p>
 */
public class Primes {
	private BitSet theNonPrimes;
	private int thePrimesGenerated;
	private int theMaxPrimeGenerated;

	/** Creates a Primes object */
	public Primes() {
		clear();
	}

	/** @return The number of primes that this instance has generated */
	public int getPrimesGenerated() {
		return thePrimesGenerated;
	}

	/** @return The largest prime that this instance has generated */
	public int getMaxPrimeGenerated() {
		return theMaxPrimeGenerated;
	}

	/**
	 * @param number The number to generate a prime less than or equal to
	 * @return The largest prime less than than or equal to the given number
	 */
	public int getPrimeLTE(int number) {
		if (theMaxPrimeGenerated >= number)
			return theNonPrimes.previousClearBit(number);
		synchronized (this) {
			_genPrimeLTE(number);
		}
		return theNonPrimes.previousClearBit(number);
	}

	/**
	 * @param number The number to generate a prime greater than or equal to
	 * @return The smallest prime greater than than or equal to the given number
	 */
	public int getPrimeGTE(int number) {
		if (theMaxPrimeGenerated >= number)
			return theNonPrimes.nextClearBit(number);
		synchronized (this) {
			if (_genPrimeLTE(number) == number)
				return number;
			generateAtLeast(thePrimesGenerated + 1); // Need the next one
		}
		return theNonPrimes.nextClearBit(number);
	}

	private int _genPrimeLTE(int number) {
		if (theMaxPrimeGenerated >= number)
			return theNonPrimes.previousClearBit(number);
		// The Sieve of Eratosthenes
		int prime;
		for (int nonPrime = (theMaxPrimeGenerated / 2 + 1) * 2; nonPrime > 0 && nonPrime <= number; nonPrime += 2)
			theNonPrimes.set(nonPrime);
		for (prime = 3; prime <= theMaxPrimeGenerated; prime = theNonPrimes.nextClearBit(prime + 1)) {
			int doublePrime = prime << 1;
			int nonPrime = (theMaxPrimeGenerated / doublePrime + 1) * doublePrime + prime;
			for (; nonPrime > 0 && nonPrime <= number; nonPrime += doublePrime)
				theNonPrimes.set(nonPrime);
		}
		int lastPrime = prime;
		for (; prime <= number; prime = theNonPrimes.nextClearBit(prime + 1)) {
			thePrimesGenerated++;
			int doublePrime = prime << 1;
			int nonPrime = (theMaxPrimeGenerated / doublePrime + 1) * doublePrime + prime;
			for (; nonPrime > 0 && nonPrime <= number; nonPrime += doublePrime)
				theNonPrimes.set(nonPrime);
			lastPrime = prime;
		}
		theMaxPrimeGenerated = lastPrime;
		return lastPrime;
	}

	/**
	 * @param number The number to test
	 * @return Whether the given number is prime
	 */
	public boolean isPrime(int number) {
		return getPrimeLTE(number) == number;
	}

	/**
	 * Generates primes until {@link #getPrimesGenerated()} is at least <code>primes</code>
	 * 
	 * @param primes The minimum number of primes to generate
	 * @return This object
	 */
	public Primes generateAtLeast(int primes) {
		if (thePrimesGenerated >= primes)
			return this;
		synchronized (this) {
			if (thePrimesGenerated >= primes)
				return this;
			int firstPrimeGuess = (int) Math.ceil(primes * Math.log(primes) * 1.15);
			_genPrimeLTE(firstPrimeGuess);
			while (thePrimesGenerated < primes)
				_genPrimeLTE((int) Math.ceil(theMaxPrimeGenerated * 1.1));
		}
		return this;
	}

	/**
	 * @param maxPrime The maximum number to iterate to
	 * @return An iterable that iterates primes up to the largest prime less than or equal to the given number
	 */
	public Iterable<Integer> primesUntil(int maxPrime) {
		int lastPrime = getPrimeLTE(maxPrime);
		return new Iterable<Integer>() {
			@Override
			public Iterator<Integer> iterator() {
				return new Iterator<Integer>() {
					private int nextPrime = 0;
					private Boolean hasNext;

					@Override
					public boolean hasNext() {
						if (hasNext == null) {
							nextPrime = getPrimeGTE(nextPrime + 1);
							hasNext = nextPrime <= lastPrime;
						}
						return hasNext;
					}

					@Override
					public Integer next() {
						if (!hasNext())
							throw new NoSuchElementException();
						hasNext = null;
						return nextPrime;
					}
				};
			}
		};
	}

	/**
	 * Clears all primes from this object, resetting it back to its original state.
	 * 
	 * @return This object
	 */
	public synchronized Primes clear() {
		BitSet nonPrimes = new BitSet();
		nonPrimes.set(0, 2); // top index is exclusive
		thePrimesGenerated = 1;
		theMaxPrimeGenerated = 2;
		theNonPrimes = nonPrimes;
		return this;
	}
}
