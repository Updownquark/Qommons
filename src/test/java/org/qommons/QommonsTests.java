package org.qommons;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.qommons.collect.CLQTest;
import org.qommons.collect.CircularListTest;
import org.qommons.collect.CollectionUtilsTests;
import org.qommons.collect.HashSetTest;
import org.qommons.collect.ListenerListTest;
import org.qommons.io.QonsoleTest;
import org.qommons.io.ReaderInputStreamTest;
import org.qommons.threading.QommonsTimerTest;
import org.qommons.tree.TreeUtilsTest;

/** A suite of tests for the Qommons library */
@RunWith(Suite.class)
@SuiteClasses({ //
	CsvParserTest.class, //
	QonsoleTest.class, //
	ReaderInputStreamTest.class, //
	IterableUtilsTest.class, //
	QommonsUtilsTests.class, //
	StringUtilsTest.class, //
	ArgumentParsing2Test.class, //
	CircularListTest.class,
	CLQTest.class, //
	CollectionUtilsTests.class, //
	HashSetTest.class, //
	ListenerListTest.class, //
	TreeUtilsTest.class, //
	RangeTest.class, //
	QommonsTimerTest.class
})
public class QommonsTests {
}
