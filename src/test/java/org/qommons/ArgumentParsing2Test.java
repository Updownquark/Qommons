package org.qommons;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.regex.Matcher;

import org.junit.Assert;
import org.junit.Test;
import org.qommons.ArgumentParsing2.ArgumentParser;
import org.qommons.ArgumentParsing2.Arguments;

/** Runs some tests on {@link ArgumentParsing2} */
public class ArgumentParsing2Test {
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("ddMMMyyyy HH:mm:ss");

	enum TestEnum {
		One, Two, Three, File
	}

	private ArgumentParser theParser;

	void setup(boolean splitValues) {
		theParser = ArgumentParsing2.build()//
			.forFlagPattern(flagArgs -> flagArgs.add("flag", null))//
			.forValuePattern(splitValues ? ArgumentParsing2.SPLIT_VALUE_PATTERN : ArgumentParsing2.DEFAULT_VALUE_PATTERN,
				valueArgs -> valueArgs//
					.addBooleanArgument("bool-arg", a -> a.optional())//
					.addIntArgument("int-arg", a -> a.optional().defaultValue(20))//
					.addLongArgument("long-arg", a -> a.times(0, 2))//
					.addDoubleArgument("double-arg", a -> a.constrain(c -> c.between(0.0, 100.0)))//
					.addEnumArgument("enum-arg", TestEnum.class, a -> a.required())//
					.addInstantArgument("time-arg", a -> a.parseDefaultValue("12/25/2020 12:30am"))//
					.addDurationArgument("duration-arg", a -> a.parseDefaultValue("1m"))//
					.addStringArgument("string-arg",
						a -> a.constrain(ab -> ab.check(LambdaUtils.printablePred(s -> s.length() <= 5, "length<5", null))))//
					.addPatternArgument("pattern-arg", "(\\d+)\\-(\\d+)", null)//
					.addFileArgument("file-arg1",
						a -> a//
								// Must be specified for File type
							.when("enum-arg", TestEnum.class, c -> c.matches(ec -> ec.eq(TestEnum.File)).required())
							// Can't be specified otherwise
							.when("enum-arg", TestEnum.class, c -> c.matches(ec -> ec.neq(TestEnum.File)).forbidden()))//
					.addFileArgument("file-arg2", a -> a.optional().relativeToFileArg("file-arg1")//
						.when("file-arg1", File.class, c -> c.specified().not().forbidden())// Requires file-arg1
				)//
			).forValuePattern(splitValues ? ArgumentParsing2.SPLIT_MULTI_VALUE_PATTERN : ArgumentParsing2.DEFAULT_MULTI_VALUE_PATTERN, mvArgs -> mvArgs//
				.addIntArgument("multi-int-arg", null)//
			).build().printHelpOnError(false);
	}

	/** Tests basic parsing of valid argument sets */
	@Test
	public void testArgumentParsing() {
		setup(false);
		// Round 1
		Arguments args = theParser.parse("--flag", "--long-arg=5", "--long-arg=6", "--enum-arg=One", "--string-arg=str",
			"--pattern-arg=5-10");
		Assert.assertTrue(args.has("flag"));
		Assert.assertEquals(Arrays.asList(5L, 6L), args.getAll("long-arg"));
		Assert.assertEquals(5L, args.get("long-arg", Long.class).longValue());
		Assert.assertEquals(TestEnum.One, args.get("enum-arg", TestEnum.class));
		Assert.assertEquals("str", args.get("string-arg", String.class));
		Matcher match = args.get("pattern-arg", Matcher.class);
		Assert.assertEquals("5", match.group(1));
		Assert.assertEquals("10", match.group(2));
		Assert.assertNull(args.get("bool-arg"));
		Assert.assertNull(args.get("double-arg"));
		Assert.assertNull(args.get("file-arg1", File.class));
		Assert.assertNull(args.get("file-arg2"));
		Assert.assertEquals(20, args.get("int-arg", int.class).intValue());
		try {
			Assert.assertEquals(DATE_FORMAT.parse("25Dec2020 00:30:00").toInstant(), args.get("time-arg", Instant.class));
		} catch (ParseException e) {
			throw new IllegalStateException(e);
		}
		Assert.assertEquals(Duration.ofSeconds(60), args.get("duration-arg", Duration.class));

		// Round 2
		args = theParser.parse("--double-arg=50.5", "--double-arg=60.5", "--double-arg=70.5", "--enum-arg=File", "--bool-arg=true",
			"--file-arg1=/home/user/something", "--file-arg2=1/2/3", "--multi-int-arg=10,20,30");
		Assert.assertFalse(args.has("flag"));
		Assert.assertNull(args.get("long-arg", Long.class));
		Assert.assertEquals(50.5, args.get("double-arg", Double.class).doubleValue(), 0.0);
		Assert.assertEquals(Arrays.asList(50.5, 60.5, 70.5), args.getAll("double-arg", Double.class));
		Assert.assertEquals(TestEnum.File, args.get("enum-arg", TestEnum.class));
		Assert.assertTrue(args.get("bool-arg", Boolean.class));
		Assert.assertEquals(new File("/home/user/something"), args.get("file-arg1", File.class));
		Assert.assertEquals(new File("/home/user/something/1/2/3"), args.get("file-arg2", File.class));
		Assert.assertEquals(Arrays.asList(10, 20, 30), args.getAll("multi-int-arg", int.class));

		// Round 3
		args = theParser.parse("--enum-arg=Three", "--duration-arg=30s");
		Assert.assertEquals(TestEnum.Three, args.get("enum-arg", TestEnum.class));
		Assert.assertEquals(Duration.ofSeconds(30), args.get("duration-arg", Duration.class));
	}

	/** Tests basic parsing of valid argument sets */
	@Test
	public void testSplitArgumentParsing() {
		setup(true);
		// Round 1
		Arguments args = theParser.parse("--flag", "--long-arg", "5", "--long-arg", "6", "--enum-arg", "One", "--string-arg", "str",
			"--pattern-arg", "5-10");
		Assert.assertTrue(args.has("flag"));
		Assert.assertEquals(Arrays.asList(5L, 6L), args.getAll("long-arg"));
		Assert.assertEquals(5L, args.get("long-arg", Long.class).longValue());
		Assert.assertEquals(TestEnum.One, args.get("enum-arg", TestEnum.class));
		Assert.assertEquals("str", args.get("string-arg", String.class));
		Matcher match = args.get("pattern-arg", Matcher.class);
		Assert.assertEquals("5", match.group(1));
		Assert.assertEquals("10", match.group(2));
		Assert.assertNull(args.get("bool-arg"));
		Assert.assertNull(args.get("double-arg"));
		Assert.assertNull(args.get("file-arg1", File.class));
		Assert.assertNull(args.get("file-arg2"));
		Assert.assertEquals(20, args.get("int-arg", int.class).intValue());
		try {
			Assert.assertEquals(DATE_FORMAT.parse("25Dec2020 00:30:00").toInstant(), args.get("time-arg", Instant.class));
		} catch (ParseException e) {
			throw new IllegalStateException(e);
		}
		Assert.assertEquals(Duration.ofSeconds(60), args.get("duration-arg", Duration.class));

		// Round 2
		args = theParser.parse("--double-arg", "50.5", "--double-arg", "60.5", "--double-arg", "70.5", "--enum-arg", "File", "--bool-arg",
			"true", "--file-arg1", "/home/user/something", "--file-arg2", "1/2/3", "--multi-int-arg", "10,20,30");
		Assert.assertFalse(args.has("flag"));
		Assert.assertNull(args.get("long-arg", Long.class));
		Assert.assertEquals(50.5, args.get("double-arg", Double.class).doubleValue(), 0.0);
		Assert.assertEquals(Arrays.asList(50.5, 60.5, 70.5), args.getAll("double-arg", Double.class));
		Assert.assertEquals(TestEnum.File, args.get("enum-arg", TestEnum.class));
		Assert.assertTrue(args.get("bool-arg", Boolean.class));
		Assert.assertEquals(new File("/home/user/something"), args.get("file-arg1", File.class));
		Assert.assertEquals(new File("/home/user/something/1/2/3"), args.get("file-arg2", File.class));
		Assert.assertEquals(Arrays.asList(10, 20, 30), args.getAll("multi-int-arg", int.class));

		// Round 3
		args = theParser.parse("--enum-arg", "Three", "--duration-arg", "30s");
		Assert.assertEquals(TestEnum.Three, args.get("enum-arg", TestEnum.class));
		Assert.assertEquals(Duration.ofSeconds(30), args.get("duration-arg", Duration.class));
	}

	/** Tests parsing of bad arguments or argument sets that violate configured requirements */
	@Test
	public void testErrorCases() {
		setup(false);
		String message = null;
		try {
			message = "Missing enum-arg";
			theParser.parse("--flag");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "flag specified twice";
			theParser.parse("--enum-arg=One", "--flag", "--flag");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "int-arg specified twice";
			theParser.parse("--enum-arg=One", "--int-arg=0", "--int-arg=1");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "Bad time arg";
			theParser.parse("--enum-arg=One", "--time-arg=0");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "Bad duration arg";
			theParser.parse("--enum-arg=One", "--duration-arg=x");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "Bad pattern arg";
			theParser.parse("--enum-arg=One", "--pattern-arg=0");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "file-arg1 not specified with enum-arg=File";
			theParser.parse("--enum-arg=File");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "file-arg1 specified without enum-arg=File";
			theParser.parse("--enum-arg=One", "--file-arg1=/home/user/something");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}

		try {
			message = "file-arg2 specified without file-arg1";
			theParser.parse("--enum-arg=File", "--file-arg2=/home/user/something");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}
	}

	/** Tests parsing of arguments that violate value constraints */
	@Test
	public void testConstraints() {
		setup(false);
		String message = null;
		try {
			message = "string-arg too long";
			theParser.parse("--enum-arg=One", "--string-arg=something");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}
		try {
			message = "double-arg too small";
			theParser.parse("--enum-arg=One", "--double-arg=-1.0");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}
		try {
			message = "double-arg too large";
			theParser.parse("--enum-arg=One", "--double-arg=101");
			Assert.assertTrue(message, false);
		} catch (IllegalArgumentException e) {
			System.out.println(message + ": " + e.getMessage());
		}
	}
}
