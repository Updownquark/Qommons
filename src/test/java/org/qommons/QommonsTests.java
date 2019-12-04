package org.qommons;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.qommons.collect.CircularListTest;
import org.qommons.collect.HashSetTest;
import org.qommons.tree.TreeUtilsTest;

/** A suite of tests for the Qommons library */
@RunWith(Suite.class)
@SuiteClasses({ //
	IterableUtilsTest.class, //
	TreeUtilsTest.class, //
	CircularListTest.class,
	HashSetTest.class, //
	CsvParserTest.class, //
	StringUtilsTest.class
})
public class QommonsTests {
}
