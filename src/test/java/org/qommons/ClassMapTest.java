package org.qommons;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.ClassMap.TypeMatch;

/** Tests {@link ClassMap} */
public class ClassMapTest {
	/** A simple test on {@link ClassMap} */
	@Test
	public void testClassMap() {
		ClassMap<Integer> classMap = new ClassMap<>();

		classMap.with(B.class, 1);
		check(null, classMap.get(A.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(A.class, TypeMatch.EXACT));
		check(1, classMap.get(A.class, TypeMatch.SUB_TYPE));
		check(1, classMap.get(B.class, TypeMatch.SUPER_TYPE));
		check(1, classMap.get(B.class, TypeMatch.EXACT));
		check(1, classMap.get(B.class, TypeMatch.SUB_TYPE));
		check(1, classMap.get(C.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(C.class, TypeMatch.EXACT));
		check(null, classMap.get(C.class, TypeMatch.SUB_TYPE));
		check(null, classMap.get(D.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(D.class, TypeMatch.EXACT));
		check(null, classMap.get(D.class, TypeMatch.SUB_TYPE));

		classMap.with(A.class, 0);
		check(0, classMap.get(A.class, TypeMatch.SUPER_TYPE));
		check(0, classMap.get(A.class, TypeMatch.EXACT));
		check(0, classMap.get(A.class, TypeMatch.SUB_TYPE));
		check(1, classMap.get(B.class, TypeMatch.SUPER_TYPE));
		check(1, classMap.get(B.class, TypeMatch.EXACT));
		check(1, classMap.get(B.class, TypeMatch.SUB_TYPE));
		check(1, classMap.get(C.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(C.class, TypeMatch.EXACT));
		check(null, classMap.get(C.class, TypeMatch.SUB_TYPE));
		check(0, classMap.get(D.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(D.class, TypeMatch.EXACT));
		check(null, classMap.get(D.class, TypeMatch.SUB_TYPE));

		classMap.with(B.class, 2);
		check(0, classMap.get(A.class, TypeMatch.SUPER_TYPE));
		check(0, classMap.get(A.class, TypeMatch.EXACT));
		check(0, classMap.get(A.class, TypeMatch.SUB_TYPE));
		check(2, classMap.get(B.class, TypeMatch.SUPER_TYPE));
		check(2, classMap.get(B.class, TypeMatch.EXACT));
		check(2, classMap.get(B.class, TypeMatch.SUB_TYPE));
		check(2, classMap.get(C.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(C.class, TypeMatch.EXACT));
		check(null, classMap.get(C.class, TypeMatch.SUB_TYPE));
		check(0, classMap.get(D.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(D.class, TypeMatch.EXACT));
		check(null, classMap.get(D.class, TypeMatch.SUB_TYPE));

		classMap.with(Number.class, 3);
		check(null, classMap.get(Object.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(Object.class, TypeMatch.EXACT));
		check(3, classMap.get(Object.class, TypeMatch.SUB_TYPE));
		check(3, classMap.get(Number.class, TypeMatch.SUPER_TYPE));
		check(3, classMap.get(Number.class, TypeMatch.EXACT));
		check(3, classMap.get(Number.class, TypeMatch.SUB_TYPE));
		check(3, classMap.get(Integer.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(Integer.class, TypeMatch.EXACT));
		check(null, classMap.get(Integer.class, TypeMatch.SUB_TYPE));
		check(null, classMap.get(String.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(String.class, TypeMatch.EXACT));
		check(null, classMap.get(String.class, TypeMatch.SUB_TYPE));

		classMap.with(Object.class, 4);
		check(4, classMap.get(Object.class, TypeMatch.SUPER_TYPE));
		check(4, classMap.get(Object.class, TypeMatch.EXACT));
		check(4, classMap.get(Object.class, TypeMatch.SUB_TYPE));
		check(3, classMap.get(Number.class, TypeMatch.SUPER_TYPE));
		check(3, classMap.get(Number.class, TypeMatch.EXACT));
		check(3, classMap.get(Number.class, TypeMatch.SUB_TYPE));
		check(3, classMap.get(Integer.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(Integer.class, TypeMatch.EXACT));
		check(null, classMap.get(Integer.class, TypeMatch.SUB_TYPE));
		check(4, classMap.get(String.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(String.class, TypeMatch.EXACT));
		check(null, classMap.get(String.class, TypeMatch.SUB_TYPE));

		classMap.with(Number.class, 5);
		check(4, classMap.get(Object.class, TypeMatch.SUPER_TYPE));
		check(4, classMap.get(Object.class, TypeMatch.EXACT));
		check(4, classMap.get(Object.class, TypeMatch.SUB_TYPE));
		check(5, classMap.get(Number.class, TypeMatch.SUPER_TYPE));
		check(5, classMap.get(Number.class, TypeMatch.EXACT));
		check(5, classMap.get(Number.class, TypeMatch.SUB_TYPE));
		check(5, classMap.get(Integer.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(Integer.class, TypeMatch.EXACT));
		check(null, classMap.get(Integer.class, TypeMatch.SUB_TYPE));
		check(4, classMap.get(String.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(String.class, TypeMatch.EXACT));
		check(null, classMap.get(String.class, TypeMatch.SUB_TYPE));

		// Check that the interface values are unaffected
		check(0, classMap.get(A.class, TypeMatch.SUPER_TYPE));
		check(0, classMap.get(A.class, TypeMatch.EXACT));
		check(0, classMap.get(A.class, TypeMatch.SUB_TYPE));
		check(2, classMap.get(B.class, TypeMatch.SUPER_TYPE));
		check(2, classMap.get(B.class, TypeMatch.EXACT));
		check(2, classMap.get(B.class, TypeMatch.SUB_TYPE));
		check(2, classMap.get(C.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(C.class, TypeMatch.EXACT));
		check(null, classMap.get(C.class, TypeMatch.SUB_TYPE));
		check(0, classMap.get(D.class, TypeMatch.SUPER_TYPE));
		check(null, classMap.get(D.class, TypeMatch.EXACT));
		check(null, classMap.get(D.class, TypeMatch.SUB_TYPE));
	}

	static void check(Integer expected, Integer actual) {
		Assert.assertEquals(expected, actual);
	}

	interface A {
	}

	interface B extends A {
	}

	interface C extends B {
	}

	interface D extends A {
	}
}
