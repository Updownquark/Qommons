package org.qommons;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.qommons.collect.CircularListTest;
import org.qommons.collect.HashSetTest;
import org.qommons.tree.TreeUtilsTest;

@RunWith(Suite.class)
@SuiteClasses({ //
	IterableUtilsTest.class, //
	TreeUtilsTest.class, //
	CircularListTest.class, //
	HashSetTest.class//
})
public class QommonsTests {
}
