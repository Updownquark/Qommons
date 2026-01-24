package org.qommons;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.qommons.collect.*;
import org.qommons.config.QommonsConfigTester;
import org.qommons.io.MinMLTest;
import org.qommons.io.QonsoleTest;
import org.qommons.io.ReaderInputStreamTest;
import org.qommons.threading.ElasticExecutorTest;
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
	ArgumentParsingTest.class, //
	CircularListTest.class, //
	SimpleDequeTest.class, //
	CollectionUtilsTests.class, //
	HashSetTest.class, //
	ListenerListTest.class, //
	TreeUtilsTest.class, //
	QommonsConfigTester.class, //
	RangeTest.class, //
	ElasticExecutorTest.class, //
	QommonsTimerTest.class, //
	TimeUtilsTest.class, //
	ClassMapTest.class, //
	PrimesTest.class, //
	BetterBitSetTest.class, //
	QuarkApplicationVersioningTests.class, //
	MinMLTest.class
})
public class QommonsTests {
}
