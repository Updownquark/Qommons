package org.qommons;

import java.text.ParseException;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.qommons.TimeUtils.DateElementType;
import org.qommons.TimeUtils.RelativeTimeEvaluation;
import org.qommons.TimeUtils.TimeEvaluationOptions;

/** Tests for {@link TimeUtils} */
public class TimeUtilsTest {
	private Calendar refCal;
	private TimeZone gmt;

	/** Prepares the test */
	@Before
	public void before() {
		refCal = Calendar.getInstance();
		gmt = TimeZone.getTimeZone("GMT");
		refCal.setTimeZone(gmt);
	}

	/**
	 * Tests {@link TimeUtils#parseFlexFormatTime(CharSequence, boolean, boolean, Function)}
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
			teo -> teo.withEvaluationType(RelativeTimeEvaluation.FUTURE), cal -> {
				cal.set(Calendar.HOUR_OF_DAY, 8);
				while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
					cal.add(Calendar.DAY_OF_WEEK, 1);
			});
		test("Thurs 1300", DateElementType.Minute, //
			teo -> teo.withEvaluationType(RelativeTimeEvaluation.FUTURE), cal -> {
				cal.set(Calendar.HOUR_OF_DAY, 13);
				while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.THURSDAY)
					cal.add(Calendar.DAY_OF_WEEK, 1);
			});
		test("Jan 15 9am", DateElementType.Minute, //
			teo -> teo.withEvaluationType(RelativeTimeEvaluation.PAST), cal -> {
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
			teo -> teo.withEvaluationType(RelativeTimeEvaluation.FUTURE), //
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
			teo -> teo.withEvaluationType(RelativeTimeEvaluation.PAST), //
			cal -> {
				cal.set(1901, Calendar.JUNE, 25);
			});
		test("6/25/99", DateElementType.Day, //
			teo -> teo.withEvaluationType(RelativeTimeEvaluation.FUTURE), //
			cal -> {
				cal.set(1999, Calendar.JUNE, 25);
			});
		test("6/25/2099", DateElementType.Day, null, //
			cal -> {
				cal.set(2099, Calendar.JUNE, 25);
			});
		test("17.07.81", DateElementType.Day, null, //
			cal -> {
				cal.set(2081, Calendar.JULY, 17);
			});
		refCal.set(Calendar.YEAR, 2000);
		test("8th 10:30", DateElementType.Minute, //
			teo -> teo.withEvaluationType(RelativeTimeEvaluation.FUTURE), //
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
		Instant parsed = TimeUtils.parseFlexFormatTime(text, true, true, //
			teo -> {
				teo = teo.withTimeZone(gmt);
				if (opts != null)
					teo = opts.apply(teo);
				return teo;
			})//
			.evaluate(refS);
		expect.accept(refCal);
		Instant expected = Instant.ofEpochMilli(refCal.getTimeInMillis());
		Assert.assertEquals(expected, parsed);
	}
}
