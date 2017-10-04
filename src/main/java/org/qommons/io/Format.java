package org.qommons.io;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.qommons.QommonsUtils;

public interface Format<T> {
	void append(StringBuilder text, T value);

	default String format(T value) {
		StringBuilder s = new StringBuilder();
		append(s, value);
		return s.toString();
	}

	T parse(CharSequence text) throws ParseException;

	public static Format<Instant> dateFormat(SimpleDateFormat dateFormat) {
		return new Format<Instant>() {
			@Override
			public void append(StringBuilder text, Instant value) {
				text.append(dateFormat.format(Date.from(value)));
			}

			@Override
			public Instant parse(CharSequence text) throws ParseException {
				return dateFormat.parse(text.toString()).toInstant();
			}
		};
	}

	public static Format<Duration> durationFormat() {
		return new Format<Duration>() {
			@Override
			public void append(StringBuilder text, Duration value) {
				if (value.isNegative()) {
					text.append('-');
					value = value.abs();
				}
				if (value.isZero())
					text.append("0s");
				else if (value.compareTo(Duration.ofSeconds(1)) < 0) {
					appendNanos(text, value.getNano());
				} else {
					boolean appended = false;
					long seconds = value.getSeconds();
					int dayLength = 24 * 60 * 60;
					double yearLength = 365.25 * dayLength;
					int years = (int) Math.floor(seconds / yearLength);
					if (years > 0) {
						text.append(years).append('y');
						seconds -= Math.floor(years * yearLength);
						appended = true;
					}
					long days = (int) (seconds / dayLength);
					if (days > 0) {
						if (appended)
							text.append(' ');
						text.append(days).append('d');
						seconds -= days * dayLength;
						appended = true;
					}
					int hours = (int) seconds / 60 / 60;
					if (hours > 0) {
						if (appended)
							text.append(' ');
						text.append(hours).append('h');
						seconds -= hours * 60 * 60;
						appended = true;
					}
					int minutes = (int) seconds / 60;
					if (minutes > 0) {
						if (appended)
							text.append(' ');
						text.append(minutes).append('m');
						seconds -= minutes * 60;
						appended = true;
					}
					int ns = value.getNano();
					if (seconds > 0) {
						if (appended)
							text.append(' ');
						text.append(seconds);
						if (ns > 1000000) {
							text.append('.');
							int divisor = 100000000;
							while (ns > 0 && divisor > 0) {
								text.append(ns / divisor);
								ns %= divisor;
								divisor /= 10;
							}
						} else if (ns > 0) {
							text.append(' ');
							appendNanos(text, ns);
						}
						text.append('s');
					} else if (ns > 0) {
						if (appended)
							text.append(' ');
						appendNanos(text, ns);
					}
				}
			}

			private void appendNanos(StringBuilder text, int ns) {
				if (ns < 1000000)
					text.append(ns).append("ns");
				else if (ns % 1000000 == 0)
					text.append(ns / 1000000).append("ms");
				else {
					text.append(ns / 1000000).append('.');
					buffer(text, ns % 1000000, 6);
					text.append("ms");
				}
			}

			private StringBuilder buffer(StringBuilder text, int value, int length) {
				int minForLength = 1;
				for (int i = 0; i < length; i++) {
					minForLength *= 10;
					if (value < minForLength)
						text.append('0');
				}
				text.append(value);
				return text;
			}

			@Override
			public Duration parse(CharSequence text) throws ParseException {
				return QommonsUtils.parseDuration(text);
			}
		};
	}
}
