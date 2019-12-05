package org.qommons;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.qommons.collect.CLQTest;
import org.qommons.collect.CircularListTest;
import org.qommons.collect.HashSetTest;
import org.qommons.collect.ListenerListTest;
import org.qommons.tree.TreeUtilsTest;

/** A suite of tests for the Qommons library */
@RunWith(Suite.class)
@SuiteClasses({ //
	CsvParserTest.class, //
	IterableUtilsTest.class, //
	QommonsUtilsTests.class, //
	StringUtilsTest.class, //
	CircularListTest.class,
	CLQTest.class, //
	HashSetTest.class, //
	ListenerListTest.class, //
	TreeUtilsTest.class //
})
public class QommonsTests {
}
