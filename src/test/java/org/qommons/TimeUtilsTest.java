package org.qommons;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.qommons.TimeUtils.DateElementType;
import org.qommons.TimeUtils.RelativeInstantEvaluation;
import org.qommons.TimeUtils.TimeEvaluationOptions;
import org.qommons.testing.TestHelper;

/** Tests for {@link TimeUtils} */
public class TimeUtilsTest {
	private static final SimpleDateFormat STD_FORMAT = new SimpleDateFormat("ddMMMyyyy HH:mm:ss.SSS");
	private static final long MAX_TIME;
	static {
		STD_FORMAT.setTimeZone(TimeUtils.GMT);
		try {
			MAX_TIME = STD_FORMAT.parse("31Dec9999 23:59:59.999").getTime();
		} catch (ParseException e) {
			throw new IllegalStateException(e);
		}
	}

	private Calendar refCal;

	/** Prepares the test */
	@Before
	public void before() {
		refCal = Calendar.getInstance();
		refCal.setTimeZone(TimeUtils.GMT);
	}

	/**
	 * Tests {@link TimeUtils#parseInstant(CharSequence, boolean, boolean, Function)}
	 * 
	 * @throws ParseException If one of the format tests fails
	 */
	@Test
	public void testInstantFormats() throws ParseException {
		test("20061203", DateElementType.Day, null, //
			cal -> {
				cal.set(2006, 11, 3);
			});
		test("Mon 8am", DateElementType.Minute, //
			teo -> teo.withEvaluationType(RelativeInstantEvaluation.Future), cal -> {
				cal.set(Calendar.HOUR_OF_DAY, 8);
				while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
					cal.add(Calendar.DAY_OF_WEEK, 1);
			});
		test("Thurs 1300", DateElementType.Minute, //
			teo -> teo.withEvaluationType(RelativeInstantEvaluation.Future), cal -> {
				cal.set(Calendar.HOUR_OF_DAY, 13);
				while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.THURSDAY)
					cal.add(Calendar.DAY_OF_WEEK, 1);
			});
		test("Jan 15 9am", DateElementType.Minute, //
			teo -> teo.withEvaluationType(RelativeInstantEvaluation.Past), cal -> {
				cal.set(Calendar.HOUR_OF_DAY, 9);
				cal.set(Calendar.DAY_OF_MONTH, 15);
				while (cal.get(Calendar.MONTH) != Calendar.JANUARY)
					cal.add(Calendar.MONTH, -1);
			});
		test("Jan 15, 2015 9:06pm", DateElementType.Minute, null, //
			cal -> {
				cal.set(2015, Calendar.JANUARY, 15, 21, 6, 0);
			});
		test("Dec 31, 2015 12pm", DateElementType.Hour, null, //
			cal -> {
				cal.set(2015, Calendar.DECEMBER, 31, 12, 0, 0);
			});
		test("Mon, 8am", DateElementType.Hour, //
			teo -> teo.withEvaluationType(RelativeInstantEvaluation.Future), //
			cal -> {
				cal.set(Calendar.HOUR_OF_DAY, 8);
				while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
					cal.add(Calendar.DAY_OF_MONTH, 1);
			});
		test("9-18-2017", DateElementType.Day, null, //
			cal -> {
				cal.set(2017, Calendar.SEPTEMBER, 18);
			});
		test("9-Apr-2017", DateElementType.Day, null, //
			cal -> {
				cal.set(2017, Calendar.APRIL, 9);
			});
		test("6/25/99", DateElementType.Day, null, //
			cal -> {
				cal.set(1999, Calendar.JUNE, 25);
			});
		test("6/25/01", DateElementType.Day, //
			teo -> teo.withEvaluationType(RelativeInstantEvaluation.Past), //
			cal -> {
				cal.set(1901, Calendar.JUNE, 25);
			});
		test("6/25/99", DateElementType.Day, //
			teo -> teo.withEvaluationType(RelativeInstantEvaluation.Future), //
			cal -> {
				cal.set(1999, Calendar.JUNE, 25);
			});
		test("6/25/2099", DateElementType.Day, null, //
			cal -> {
				cal.set(2099, Calendar.JUNE, 25);
			});
		// This format is ambiguous
		// test("17.07.81", DateElementType.Day, null, //
		// cal -> {
		// cal.set(2081, Calendar.JULY, 17);
		// });
		refCal.setTime(STD_FORMAT.parse("17Jul2000 00:00:00.000"));
		test("8th 10:30", DateElementType.Minute, //
			teo -> teo.withEvaluationType(RelativeInstantEvaluation.Future), //
			cal -> {
				cal.set(2000, Calendar.AUGUST, 8, 10, 30, 0);
			});
		test("0700", DateElementType.Minute, null, //
			cal -> {
				cal.set(2000, Calendar.AUGUST, 8, 7, 0, 0);
			});
		test("2021-Sep-30", DateElementType.Day, null, //
			cal -> {
				cal.set(2021, Calendar.SEPTEMBER, 30);
			});
		test("2019-02-19", DateElementType.Day, null, //
			cal -> {
				cal.set(2019, Calendar.FEBRUARY, 19);
			});
		test("20210206 08:00:17.600", DateElementType.SubSecond, null, //
			cal -> {
				cal.set(2021, Calendar.FEBRUARY, 6, 8, 0, 17);
				cal.set(Calendar.MILLISECOND, 600);
			});
	}

	private void test(String text, DateElementType resolution, Function<TimeEvaluationOptions, TimeEvaluationOptions> opts,
		Consumer<Calendar> expect) throws ParseException {
		for (int i = DateElementType.values().length - 1; i >= 0; i--) {
			DateElementType type = DateElementType.values()[i];
			if (type == resolution)
				break;
			switch (type) {
			case SubSecond:
				refCal.set(Calendar.MILLISECOND, 0);
				break;
			case Second:
				refCal.set(Calendar.SECOND, 0);
				break;
			case Minute:
				refCal.set(Calendar.MINUTE, 0);
				break;
			case Hour:
				refCal.set(Calendar.HOUR_OF_DAY, 0);
				break;
			case Day:
				refCal.set(Calendar.DAY_OF_MONTH, 0);
				break;
			default:
				break;
			}
		}
		Instant ref = Instant.ofEpochMilli(refCal.getTimeInMillis());
		Supplier<Instant> refS = () -> ref;
		Instant parsed = TimeUtils.parseInstant(text, true, true, //
			teo -> {
				teo = teo.gmt();
				if (opts != null)
					teo = opts.apply(teo);
				return teo;
			})//
			.evaluate(refS);
		expect.accept(refCal);
		Instant expected = Instant.ofEpochMilli(refCal.getTimeInMillis());
		Assert.assertEquals(expected, parsed);
	}

	/**
	 * Tests the parsing and printing abilities of {@link TimeUtils.DayFormat} with a few times
	 * 
	 * @throws ParseException If something can't be parsed
	 */
	@Test
	public void testFastFormat() throws ParseException {
		List<String> dates = Arrays.asList(//
			"31Mar2000 18:25:30.111", //
			"31Mar2005 18:25:30.111", //
			"28Feb2024 11:37:18.056", //
			"29Feb2024 11:37:18.056", //
			"28Feb1970 11:37:18.056", //
			"01Jun1970 11:37:18.056", //
			"01Jun1969 11:37:18.056", //
			"01Jun1900 11:37:18.056" //
		);
		SimpleDateFormat defaultFormat = new SimpleDateFormat("ddMMMyyyy HH:mm:ss.SSS");
		defaultFormat.setTimeZone(TimeUtils.GMT);
		TimeUtils.DayFormat fastFormat = TimeUtils.DayFormat.DDMMMYYYY_HH_MM_SS_SSS;
		for (String date : dates) {
			String defStr = defaultFormat.format(defaultFormat.parse(date));
			String fastStr = defaultFormat.format(//
				new Date(fastFormat.parseTime(date)));
			Assert.assertEquals(defStr, fastStr);
		}
	}

	@SuppressWarnings("unused")
	private static final TimeUtils.TimeEvaluationOptions OPTS = TimeUtils.DEFAULT_OPTIONS.gmt();
	private static long stdParseTime;
	private static long stdPrintTime;
	private static long flexParseTime;
	private static long flexPrintTime;
	private static long fastParseTime;
	private static long fastPrintTime;

	/** Tests the parsing and printing abilities of {@link TimeUtils.DayFormat} with a random sequence of times */
	@Test
	public void testFastFormatRandomly() {
		TestHelper.createTester(FastFormatTestable.class).revisitKnownFailures(true).withDebug(true).withFailurePersistence(true)
			.withMaxCaseDuration(Duration.ofSeconds(1)).withRandomCases(1000).execute().throwErrorIfFailed();

		System.out.println("SDF Parsing:   " + QommonsUtils.printTimeLength(stdParseTime));
		System.out.println("SDF Printing:  " + QommonsUtils.printTimeLength(stdPrintTime));
		if(flexParseTime>0)
			System.out.println("Flex Parsing:  " + QommonsUtils.printTimeLength(flexParseTime));
		if (flexPrintTime > 0)
			System.out.println("Flex Printing: " + QommonsUtils.printTimeLength(flexPrintTime));
		System.out.println("Fast Parsing:  " + QommonsUtils.printTimeLength(fastParseTime));
		System.out.println("Fast Printing: " + QommonsUtils.printTimeLength(fastPrintTime));
	}

	static class FastFormatTestable implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			STD_FORMAT.setTimeZone(TimeUtils.GMT);
			TimeUtils.DayFormat fastFormat = TimeUtils.DayFormat.DDMMMYYYY_HH_MM_SS_SSS;
			StringBuilder str = new StringBuilder();
			try {
				for (int i = 0; i < 100; i++) {
					long time = (long) (helper.getDouble() * MAX_TIME);
					@SuppressWarnings("unused")
					Instant inst = Instant.ofEpochMilli(time);
					Date date = new Date(time);
					long now = System.currentTimeMillis(), newNow;

					String defStr = STD_FORMAT.format(date);
					newNow = System.currentTimeMillis();
					stdPrintTime += (newNow - now);
					now = newNow;

					STD_FORMAT.parse(defStr);
					newNow = System.currentTimeMillis();
					stdParseTime += (newNow - now);
					now = newNow;

					/* These 2 are just to compare performance.
					 * Generally the flexible parsing is slightly slower than SDF parsing, and printing is about twice as slow.
					 * Uncomment these to see if anything has changed.
					 */
					/*
					TimeUtils.parseInstant(defStr, true, true, __ -> OPTS);
					newNow = System.currentTimeMillis();
					flexParseTime += (newNow - now);
					now = newNow;
					
					TimeUtils.asFlexInstant(inst, "ddMMMyyyy", __ -> OPTS).toString();
					newNow = System.currentTimeMillis();
					flexPrintTime += (newNow - now);
					now = newNow;*/

					long fastParsed = fastFormat.parseTime(defStr);
					newNow = System.currentTimeMillis();
					fastParseTime += (newNow - now);
					now = newNow;
					Assert.assertEquals(time, fastParsed);

					fastFormat.append(str, time);
					newNow = System.currentTimeMillis();
					fastPrintTime += (newNow - now);
					now = newNow;
					Assert.assertEquals(defStr, str.toString());
					str.setLength(0);
				}
			} catch (ParseException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}
