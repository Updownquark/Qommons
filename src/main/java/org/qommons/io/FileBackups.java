package org.qommons.io;

import java.io.IOException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import org.qommons.ArrayUtils;
import org.qommons.TimeUtils;

public class FileBackups {
	public static final Format<Instant> DATE_FORMAT = Format.flexibleDate("ddMMMyyyy", TimeZone.getDefault());

	private static class BackupFile {
		final String fileName;
		final String suffix;

		BackupFile(String fileName) {
			int lastDot = fileName.lastIndexOf('.');
			if (lastDot > 0) { // Strictly greater so "hidden" initial-dot files don't look like extensions
				suffix = fileName.substring(lastDot);
				fileName = fileName.substring(0, lastDot + 1);
			} else
				suffix = "";
			this.fileName = fileName;
		}

		public Instant getBackupTime(String backupFileName) {
			if (backupFileName.startsWith(fileName) && backupFileName.endsWith(suffix)) {
				try {
					Instant time = DATE_FORMAT
						.parse(backupFileName.substring(fileName.length(), backupFileName.length() - suffix.length()));
					return time;
				} catch (ParseException e) {
					return null;
				}
			} else
				return null;
		}

		public String getBackupFileName(Instant backupTime) {
			return fileName + DATE_FORMAT.format(backupTime) + suffix;
		}
	}

	private final BetterFile theTargetFile;
	private final BackupFile theTargetBackup;
	private final BetterFile theDirectory;
	private final Duration theMinDuration;
	private final Duration theMaxDuration;
	private final int theMinBackupCount;
	private final int theMaxBackupCount;
	private final long theMinBackupSize;
	private final long theMaxBackupSize;

	private FileBackups(Builder builder) {
		theTargetFile = builder.theTargetFile;
		theTargetBackup = new BackupFile(theTargetFile.getName());
		theDirectory = builder.theDirectory;

		theMinDuration = builder.theMinDuration;
		theMaxDuration = builder.theMaxDuration;
		theMinBackupCount = builder.theMinBackupCount;
		theMaxBackupCount = builder.theMaxBackupCount;
		theMinBackupSize = builder.theMinBackupSize;
		theMaxBackupSize = builder.theMaxBackupSize;
	}

	public BetterFile getTargetFile() {
		return theTargetFile;
	}

	public BetterFile getDirectory() {
		return theDirectory;
	}

	public Duration getMinDuration() {
		return theMinDuration;
	}

	public Duration getMaxDuration() {
		return theMaxDuration;
	}

	public int getMinBackupCount() {
		return theMinBackupCount;
	}

	public int getMaxBackupCount() {
		return theMaxBackupCount;
	}

	public long getMinBackupSize() {
		return theMinBackupSize;
	}

	public long getMaxBackupSize() {
		return theMaxBackupSize;
	}

	public void saveLatest(boolean moveOrCopy) throws IOException {
		purgeIfNeeded(theTargetFile.length());
		Instant time = Instant.now();
		BetterFile backupFile = theDirectory.at(theTargetBackup.getBackupFileName(time));
		if (!moveOrCopy || theTargetFile.move(backupFile) == null) {
			FileUtils.sync().from(theTargetFile).to(backupFile).sync();
		}
	}

	private void purgeIfNeeded(long addedSize) {
		int count = 1;
		long totalSize = addedSize;
		List<? extends BetterFile> files = theDirectory.listFiles();
		Instant[] backupTimes = new Instant[files.size()];
		long[] sizes = new long[files.size()];
		for (int i = 0; i < backupTimes.length; i++) {
			Instant time = theTargetBackup.getBackupTime(files.get(i).getName());
			backupTimes[i] = time;
			if (time != null) {
				count++;
				long size = files.get(i).length();
				sizes[i] = size;
				totalSize += size;
			}
		}
		// Ignore non-backup files
		int lastValid = 0;
		for (int i = 0; i < backupTimes.length; i++) {
			if (backupTimes[i] != null) {
				if (i != lastValid) {
					backupTimes[lastValid] = backupTimes[i];
					sizes[lastValid] = sizes[i];
				}
				lastValid++;
			}
		}
		lastValid--;
		ArrayUtils.sort(backupTimes, new ArrayUtils.SortListener<Instant>() {
			@Override
			public int compare(Instant o1, Instant o2) {
				if (o1 == null) {
					if (o2 == null)
						return 0;
					else
						return 1;
				} else if (o2 == null)
					return -1;
				else
					return -o1.compareTo(o2); // Newest first
			}

			@Override
			public void swapped(Instant o1, int idx1, Instant o2, int idx2) {
				long sz1 = sizes[idx1];
				sizes[idx1] = sizes[idx2];
				sizes[idx2] = sz1;
			}
		});

		Instant now = Instant.now();
		while (lastValid >= 0 && needsPurge(count, totalSize, TimeUtils.between(backupTimes[lastValid], now))) {
			getBackup(backupTimes[lastValid]).delete(null);
			totalSize -= sizes[lastValid];
			lastValid--;
		}
	}

	private boolean needsPurge(int count, long totalSize, Duration oldestBackup) {
		if (count <= theMinBackupCount)
			return false;
		else if (totalSize <= theMinBackupSize)
			return false;
		else if (oldestBackup.compareTo(theMinDuration) <= 0)
			return false;
		if (count > theMaxBackupCount)
			return true;
		else if (totalSize > theMaxBackupSize)
			return true;
		else if (oldestBackup.compareTo(theMaxDuration) > 0)
			return true;
		return false;
	}

	public List<Instant> getBackups() {
		List<Instant> backups = new ArrayList<>();
		for (BetterFile file : theDirectory.listFiles()) {
			Instant time = theTargetBackup.getBackupTime(file.getName());
			if (time != null)
				backups.add(time);
		}
		return backups;
	}

	public BetterFile getBackup(Instant backupTime) {
		return theDirectory.at(theTargetBackup.getBackupFileName(backupTime));
	}

	public void restore(Instant backupTime) throws IOException {
		BetterFile backupFile = getBackup(backupTime);
		if (!backupFile.exists())
			throw new IllegalArgumentException("No such backup for " + DATE_FORMAT.format(backupTime));
		FileUtils.sync().from(backupFile).to(theTargetFile).sync();
	}

	public void renamedFrom(String fileName) {
		BackupFile oldBackup = new BackupFile(fileName);
		List<? extends BetterFile> files = theDirectory.listFiles();
		for (BetterFile file : files) {
			Instant backupTime = oldBackup.getBackupTime(file.getName());
			if (backupTime != null)
				file.move(theDirectory.at(theTargetBackup.getBackupFileName(backupTime)));
		}
	}

	public static Builder build(BetterFile targetFile) {
		return new Builder(targetFile);
	}

	public static class Builder {
		private final BetterFile theTargetFile;
		private BetterFile theDirectory;
		private Duration theMinDuration;
		private Duration theMaxDuration;
		private int theMinBackupCount;
		private int theMaxBackupCount;
		private long theMinBackupSize;
		private long theMaxBackupSize;

		Builder(BetterFile targetFile) {
			theTargetFile = targetFile;
			int dotIdx = theTargetFile.getName().lastIndexOf('.');
			String backupFolderName;
			if (dotIdx < 0)
				backupFolderName = theTargetFile.getName() + ".BAK";
			else
				backupFolderName = theTargetFile.getName().substring(0, dotIdx) + ".BAK";
			theDirectory = targetFile.getParent().at(backupFolderName);

			theMinDuration = Duration.ZERO;
			theMaxDuration = Duration.ofDays(30);

			theMinBackupCount = 1;
			theMaxBackupCount = 100;

			theMinBackupSize = 0;
			theMaxBackupSize = 100L * 1024 * 1024;
		}

		public BetterFile getTargetFile() {
			return theTargetFile;
		}

		public BetterFile getBackupDirectory() {
			return theDirectory;
		}

		public Builder withBackupDirectory(BetterFile directory) {
			theDirectory = directory;
			return this;
		}

		public Duration getMinDuration() {
			return theMinDuration;
		}

		public Duration getMaxDuration() {
			return theMaxDuration;
		}

		public Builder withDuration(Duration minDuration, Duration maxDuration) {
			if (minDuration.compareTo(maxDuration) > 0)
				throw new IllegalArgumentException(minDuration + ">" + maxDuration);
			theMinDuration = minDuration;
			theMaxDuration = maxDuration;
			return this;
		}

		public int getMinBackupCount() {
			return theMinBackupCount;
		}

		public int getMaxBackupCount() {
			return theMaxBackupCount;
		}

		public Builder withBackupCount(int minBackupCount, int maxBackupCount) {
			if (minBackupCount > maxBackupCount)
				throw new IllegalArgumentException(minBackupCount + ">" + maxBackupCount);
			theMinBackupCount = minBackupCount;
			theMaxBackupCount = maxBackupCount;
			return this;
		}

		public long getMinBackupSize() {
			return theMinBackupSize;
		}

		public long getMaxBackupSize() {
			return theMaxBackupSize;
		}

		public Builder withBackupSize(long minBackupSize, long maxBackupSize) {
			if (minBackupSize > maxBackupSize)
				throw new IllegalArgumentException(minBackupSize + ">" + maxBackupSize);
			theMinBackupSize = minBackupSize;
			theMaxBackupSize = maxBackupSize;
			return this;
		}

		public FileBackups build() {
			return new FileBackups(this);
		}
	}
}
