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
	 * @throws IllegalArgumentException If the max prime is &lt;=0
	 */
	public Iterable<Integer> primesUntil(int maxPrime) throws IllegalArgumentException {
		if (maxPrime <= 0)
			throw new IllegalArgumentException("Max prime must be greater than zero");
		int lastPrime = getPrimeLTE(maxPrime);
		return () -> new Iterator<Integer>() {
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

	/**
	 * Breaks down a number into a list of prime numbers that all multiply to that value
	 * 
	 * @param value The value to factorize
	 * @param maxPrime The maximum prime to test against the value
	 * @return A list containing only prime numbers, the product of all of which is <code>value</code>. Unless...
	 *         <ul>
	 *         <li><code>value==1</code>, in which case the a list containing only 1 will be returned</li>
	 *         <li><code>value</code> is or is composed of a prime greater than <code>maxPrime</code>. In this case, all primes
	 *         &lt;=maxPrime will be in the return value, followed by -1.</li>
	 *         </ul>
	 * @throws IllegalArgumentException If either the value or the max prime are &lt;=0
	 */
	public IntList factorize(long value, int maxPrime) throws IllegalArgumentException {
		if (value <= 0)
			throw new IllegalArgumentException("Only positive numbers may be factorized");
		else if (value == 1)
			return new IntList(new int[] { 1 });
		Iterator<Integer> primes = primesUntil(maxPrime).iterator();
		IntList factored = new IntList(false, false);
		int lastPrime = 1;
		long remainder = value;
		while (remainder > lastPrime && primes.hasNext()) {
			int prime = primes.next();
			while ((remainder % prime) == 0) {
				factored.add(prime);
				remainder /= prime;
			}
			lastPrime = prime;
		}
		if (remainder > lastPrime) // Couldn't factorize completely
			factored.add(-1);
		else if (remainder > 1)
			factored.add((int) remainder);
		return factored;
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

	/**
	 * A utility method to factor the result of {@link #factorize(long, int)} in a pretty way
	 * 
	 * @param factorization The factorization of a number
	 * @return A pretty representation of the factorization
	 */
	public static String formatFactorization(IntList factorization) {
		if (factorization == null)
			return "";
		StringBuilder str = new StringBuilder();
		int prev = -100;
		int pow = 1;
		for (Integer factor : factorization) {
			if (factor.intValue() == prev)
				pow++;
			else if (prev > 0) {
				if (str.length() > 0)
					str.append(", ");
				str.append(prev);
				if (pow == 1) {//
				} else if (pow < 4) {
					str.append((char) ('\u00b0' + pow));
				} else if (pow < 10)
					str.append((char) ('\u2070' + pow));
				else
					str.append('^').append(pow);
				prev = -100;
				pow = 1;
			}
			if (factor.intValue() <= 0) {
				if (str.length() > 0)
					str.append(", ");
				str.append("...");
			}
			else
				prev = factor;
		}
		if (prev > 0) {
			if (str.length() > 0)
				str.append(", ");
			str.append(prev);
			if (pow == 1) {//
			} else if (pow < 4) {
				str.append((char) ('\u00b0' + pow));
			} else if (pow < 10)
				str.append((char) ('\u2070' + pow));
			else
				str.append('^').append(pow);
		}
		return str.toString();
	}
}
