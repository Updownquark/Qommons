package org.qommons;

import org.junit.Assert;
import org.junit.Test;

/** A test for {@link Primes} */
public class PrimesTest {
	/**
	 * Ensures that the prime numbers generated by {@link Primes} are actually prime and that there are no prime numbers that are not
	 * reported by {@link Primes}
	 */
	@SuppressWarnings("static-method")
	@Test
	public void testPrimes() {
		Primes primes = new Primes();
		int maxPrime = 100_000;
		primes.getPrimeLTE(maxPrime);
		Iterable<Integer> iter = primes.primesUntil(maxPrime);
		int prevPrime = 2;
		for (int prime : iter) {
			for (int p2 = prevPrime + 1; p2 < prime; p2++) {
				boolean isPrime = true;
				for (int prime2 : iter) {
					if (prime2 == prime)
						break;
					if (p2 % prime2 == 0) {
						isPrime = false;
						break;
					}
				}
				if (isPrime)
					Assert.assertFalse(p2 + " is prime", isPrime);
			}
			for (int prime2 : iter) {
				if (prime2 == prime)
					break;
				if (prime % prime2 == 0)
					Assert.assertTrue(prime + " is divisible by " + prime2, false);
			}
			prevPrime = prime;
		}
	}
}