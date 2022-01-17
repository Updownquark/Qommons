package org.qommons;

import java.util.function.BiConsumer;

import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterMultiMap;
import org.qommons.tree.BetterTreeMultiMap;

public class StatusReportAccumulator<T> {
	public enum Status {
		Info, Warn, Error, Fatal
	}

	public static class StatusReport<T> {
		private final T theTarget;
		private final Status theStatus;
		private final String theMessage;
		private final Throwable theException;

		public StatusReport(T target, Status status, String message, Throwable exception) {
			theTarget = target;
			theStatus = status;
			theMessage = message;
			theException = exception;
		}

		public T getTarget() {
			return theTarget;
		}

		public Status getStatus() {
			return theStatus;
		}

		public String getMessage() {
			return theMessage;
		}

		public Throwable getException() {
			return theException;
		}
	}

	private final BetterMultiMap<Status, StatusReport<T>> theReports;

	public StatusReportAccumulator() {
		theReports = BetterTreeMultiMap.<Status, StatusReport<T>> build(Status::compareTo).buildMultiMap();
	}

	public BetterCollection<StatusReport<T>> get(Status status) {
		return theReports.get(status);
	}

	public BetterCollection<StatusReport<T>> getAll() {
		return theReports.values();
	}

	public StatusReport<T> status(T target, Status status, String message, Throwable ex) {
		StatusReport<T> report = new StatusReport<>(target, status, message, ex);
		theReports.add(status, report);
		return report;
	}

	public StatusReport<T> info(T target, String message) {
		return status(target, Status.Info, message, null);
	}

	public StatusReport<T> warn(T target, String message) {
		return status(target, Status.Warn, message, null);
	}

	public StatusReport<T> warn(T target, String message, Throwable ex) {
		return status(target, Status.Warn, message, ex);
	}

	public StatusReport<T> error(T target, String message) {
		return status(target, Status.Error, message, null);
	}

	public StatusReport<T> error(T target, String message, Throwable ex) {
		return status(target, Status.Error, message, ex);
	}

	public StatusReport<T> fatal(T target, String message) {
		return status(target, Status.Fatal, message, null);
	}

	public StatusReport<T> fatal(T target, String message, Throwable ex) {
		return status(target, Status.Fatal, message, ex);
	}

	public StringBuilder print(Status status, BiConsumer<StringBuilder, T> format, int stackDepth, StringBuilder into) {
		return print(status, status, format, stackDepth, into);
	}

	public StringBuilder print(Status minStatus, Status maxStatus, BiConsumer<StringBuilder, T> format, int stackDepth,
		StringBuilder into) {
		if (into == null)
			into = new StringBuilder();
		Status s = minStatus;
		while (s.compareTo(maxStatus) <= 0) {
			for (StatusReport<T> report : get(s)) {
				into.append('\n');
				if (format != null)
					format.accept(into, report.getTarget());
				else
					into.append(report.getTarget());
				into.append(" [").append(s).append("] ").append(report.getMessage());
				if (report.getException() != null)
					printException(report.getException(), stackDepth, null, 1, into);
			}
			if (s.ordinal() == Status.values().length - 1)
				break;
			else
				s = Status.values()[s.ordinal() + 1];
		}
		return into;
	}

	private static int printException(Throwable exception, int stackDepth, String prefix, int exDepth, StringBuilder into) {
		into.append('\n');
		for (int i = 0; i < exDepth; i++)
			into.append('\t');
		if (prefix != null)
			into.append(prefix).append(' ');
		into.append(exception.getClass().getName()).append(' ');
		if (exception.getMessage() != null)
			into.append(exception.getMessage());
		stackDepth--;
		if (stackDepth == 0)
			return 0;
		for (StackTraceElement el : exception.getStackTrace()) {
			into.append("\n\t");
			for (int i = 0; i < exDepth; i++)
				into.append('\t');
			into.append(el);
			if (--stackDepth == 0)
				break;
		}
		if (stackDepth > 0) {
			if (exception.getCause() != null && exception.getCause() != exception)
				stackDepth = printException(exception.getCause(), stackDepth, "Caused by: ", exDepth + 1, into);
			for (Throwable suppressed : exception.getSuppressed()) {
				if (stackDepth == 0)
					break;
				stackDepth = printException(suppressed, stackDepth, "Suppressed: ", exDepth + 1, into);
			}
		}
		return stackDepth;
	}
}
