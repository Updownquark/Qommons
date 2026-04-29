package org.qommons.io;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

import org.qommons.StringUtils;
import org.qommons.collect.BetterSortedSet;
import org.qommons.tree.BetterTreeSet;

/** A backup strategy for frequently-updated files, e.g. application config */
public class FileBackups {
	private static final String DATE_PATTERN = "ddMMMyyyy_HHmmss.SSS";
	/** The format to store the date of the backup in the file name */
	public static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal.withInitial(() -> new SimpleDateFormat(DATE_PATTERN));

	/** {@link TemporalBackupScheme.BackupManager} for files or directories */
	public static class FileBackupManager implements TemporalBackupScheme.BackupManager<BetterFile, IOException> {
		private final BetterFile theRootDataDir;
		private final String thePrefix;
		private final String theSuffix;

		/**
		 * @param rootDataDir The data directory in which the data and the backups will be located
		 * @param prefix The prefix for the backup file/directory names (before the time stamp)
		 * @param suffix The suffix for the backup file/directory names (after the time stamp)
		 */
		public FileBackupManager(BetterFile rootDataDir, String prefix, String suffix) {
			theRootDataDir = rootDataDir;
			thePrefix = prefix;
			theSuffix = suffix;
		}

		/** @return The prefix for the backup file/directory names (before the time stamp) */
		public String getPrefix() {
			return thePrefix;
		}

		/** @return The suffix for the backup file/directory names (after the time stamp) */
		public String getSuffix() {
			return theSuffix;
		}

		@Override
		public Iterable<? extends BetterFile> getCurrentBackups() {
			return theRootDataDir.listFiles();
		}

		@Override
		public Instant getDate(BetterFile backup) {
			String fileName = backup.getName();
			return getDate(fileName);
		}

		/**
		 * @param fileName The name of the file or directory that may be a backup
		 * @return The time stamp of the backup, or null if the name is not that of a backup managed by this manager
		 */
		public Instant getDate(String fileName) {
			if (!StringUtils.startsWithIgnoreCase(fileName, thePrefix) || !StringUtils.endsWithIgnoreCase(fileName, theSuffix) //
				|| fileName.length() - thePrefix.length() - theSuffix.length() < 10)
				return null;
			fileName = fileName.substring(thePrefix.length(), fileName.length() - theSuffix.length());
			if (fileName.length() != DATE_PATTERN.length())
				return null;
			try {
				return DATE_FORMAT.get().parse(fileName).toInstant();
			} catch (ParseException e) {
				return null;
			}
		}

		@Override
		public void preserve(BetterFile backup) {
		}

		@Override
		public void delete(BetterFile backup) throws IOException {
			backup.delete(null);
		}
	}

	private final BetterFile theTargetFile;
	private TemporalBackupScheme theBackupScheme;
	private final FileBackupManager theBackupManager;

	/** @param targetFile The file to back up */
	public FileBackups(BetterFile targetFile) {
		theTargetFile = targetFile;
		theBackupScheme = new TemporalBackupScheme();
		int dot = targetFile.getName().lastIndexOf('.');
		theBackupManager = new FileBackupManager(targetFile.getParent(), //
			dot < 0 ? targetFile.getName() : targetFile.getName().substring(0, dot + 1), //
			dot < 0 ? "" : targetFile.getName().substring(dot));
	}

	/** @return The file being backed up */
	public BetterFile getTargetFile() {
		return theTargetFile;
	}

	/** @return The backup scheme controlling the set of backups kept by this backup manager */
	public TemporalBackupScheme getBackupScheme() {
		return theBackupScheme;
	}

	/**
	 * @param backupScheme The backup scheme to control the set of backups kept by this backup manager
	 * @return This file backup utility
	 */
	public FileBackups setBackupScheme(TemporalBackupScheme backupScheme) {
		theBackupScheme = backupScheme;
		return this;
	}

	/**
	 * Tells this backup strategy that the target file has changed, so that it manages and prunes existing backups
	 * 
	 * @throws IOException If an exception occurs with the backup operation
	 */
	public void fileChanged() throws IOException {
		Instant now = Instant.now();
		if (theBackupScheme.dataRenewed(now, theBackupManager)) {
			// The new data fits in a now-unoccupied backup slot. Copy it to a new backup
			BetterFile newBackup = getBackup(now);
			FileUtils.sync().from(theTargetFile).to(newBackup).sync();
		}
	}

	/** @return The times of all current backup files */
	public BetterSortedSet<Instant> getBackups() {
		BetterSortedSet<Instant> backups = BetterTreeSet.buildTreeSet(Instant::compareTo).build();
		for (BetterFile file : theTargetFile.getParent().listFiles()) {
			Instant time = theBackupManager.getDate(file);
			if (time != null)
				backups.add(time);
		}
		return backups;
	}

	/**
	 * @param fileName The name of a potential backup file
	 * @return The backup time of the file (millis since epoch) or -1 if the file is not a backup file
	 */
	public Instant getBackupTime(String fileName) {
		return theBackupManager.getDate(fileName);
	}

	/**
	 * @param backupTime The backup time
	 * @return The file in this backup set with the given time (whether it exists or not)
	 */
	public BetterFile getBackup(Instant backupTime) {
		return theTargetFile.getParent().at(//
			new StringBuilder(theBackupManager.getPrefix()).append(DATE_FORMAT.get().format(new Date(backupTime.toEpochMilli())))
				.append(theBackupManager.getSuffix()).toString());
	}

	/**
	 * Backs up the target file to get given time
	 * 
	 * @param backupTime The backup time to back up to
	 * @throws IOException If The backup file does not exist or an error occurs during the backup operation
	 */
	public void restore(Instant backupTime) throws IOException {
		BetterFile backupFile = getBackup(backupTime);
		if (!backupFile.exists())
			throw new IllegalArgumentException("No such backup for " + DATE_FORMAT.get().format(backupTime));
		FileUtils.sync().from(backupFile).to(theTargetFile).sync();
	}

	/** @param file The target file that was being backed up before, before it was renamed to this backup's target file */
	public void renamedFrom(BetterFile file) {
		FileBackups oldBackup = new FileBackups(file);
		try {
			oldBackup.getTargetFile().move(theTargetFile);
		} catch (IOException e) {
			System.err.println("Could not move " + oldBackup.getTargetFile() + " to " + theTargetFile);
			e.printStackTrace();
		}
		for (BetterFile backup : file.getParent().listFiles()) {
			Instant backupTime = oldBackup.getBackupTime(backup.getName());
			if (backupTime != null) {
				BetterFile newBackup = getBackup(backupTime);
				try {
					backup.move(newBackup);
				} catch (IOException e) {
					System.err.println("Could not move " + backup + " to " + newBackup);
					e.printStackTrace();
				}
			}
		}
	}
}
