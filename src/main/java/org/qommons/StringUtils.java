package org.qommons;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class StringUtils {
	public static <T> StringBuilder print(CharSequence delimiter, Iterable<? extends T> values,
		Function<? super T, ? extends CharSequence> format) {
		return print(new StringBuilder(), delimiter, values, (v, str) -> str.append(format == null ? String.valueOf(v) : format.apply(v)));
	}

	public static <T> StringBuilder print(StringBuilder into, CharSequence delimiter, Iterable<? extends T> values,
		BiConsumer<? super T, ? super StringBuilder> format) {
		if (into == null)
			into = new StringBuilder();
		boolean first = true;
		for (T value : values) {
			if (first)
				first = false;
			else
				into.append(delimiter);
			if (format != null)
				format.accept(value, into);
			else
				into.append(value);
		}
		return into;
	}
}
