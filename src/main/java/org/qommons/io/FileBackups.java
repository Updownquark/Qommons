package org.qommons.io;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;

/** A backup strategy for frequently-updated files, e.g. application config */
public class FileBackups {
	/** The format to store the date of the backup in the file name */
	public static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal
		.withInitial(() -> new SimpleDateFormat("ddMMMyyyy_HHmmss.SSS"));
	/** The backup times to keep files for */
	public static final BetterSortedSet<Duration> BACKUP_TIMES = BetterCollections.unmodifiableSortedSet(//
		BetterTreeSet.buildTreeSet(Duration::compareTo).safe(false).build().with(//
			Duration.ofSeconds(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(30), //
			Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(3), Duration.ofHours(4), //
			Duration.ofHours(6), Duration.ofHours(12), Duration.ofHours(18), //
			Duration.ofDays(1), Duration.ofDays(2), Duration.ofDays(3), Duration.ofDays(4), Duration.ofDays(5), Duration.ofDays(6), //
			Duration.ofDays(7), Duration.ofDays(10), Duration.ofDays(14), Duration.ofDays(21), Duration.ofDays(30), //
			Duration.ofDays(60), Duration.ofDays(90), Duration.ofDays(120), Duration.ofDays(180)));

	private final BetterFile theTargetFile;
	private final String thePrefix;
	private final String theSuffix;

	/** @param targetFile The file to back up */
	public FileBackups(BetterFile targetFile) {
		theTargetFile = targetFile;
		int dot = targetFile.getName().lastIndexOf('.');
		thePrefix = dot < 0 ? targetFile.getName() : targetFile.getName().substring(0, dot + 1);
		theSuffix = dot < 0 ? "" : targetFile.getName().substring(dot);
	}

	/** @return The file being backed up */
	public BetterFile getTargetFile() {
		return theTargetFile;
	}

	/**
	 * Tells this backup strategy that the target file has changed, so that it manages and prunes existing backups
	 * 
	 * @throws IOException If an exception occurs with the backup operation
	 */
	public void fileChanged() throws IOException {
		BetterSortedMap<Long, BetterFile> backups = BetterTreeMap.build(Long::compareTo).safe(false).buildMap();
		long now = System.currentTimeMillis();
		BetterFile newBackup = theTargetFile.getParent().at(//
			new StringBuilder(thePrefix).append(DATE_FORMAT.get().format(new Date(now))).append(theSuffix).toString());
		FileUtils.sync().from(theTargetFile).to(newBackup).sync();
		backups.put(now, theTargetFile);
		for (BetterFile file : theTargetFile.getParent().listFiles()) {
			long backupTime;
			try {
				backupTime = getBackupTime(file.getName());
				if (backupTime >= 0) {
					backups.put(backupTime, file);
				}
			} catch (ParseException e) {
				System.err.println(thePrefix + " backup " + file.getName() + " not parseable as a backup: " + e);
			}
		}
		Duration lastBackupTime = null;
		long lastBackup = 0;
		for (Map.Entry<Long, BetterFile> backup : backups.entrySet()) {
			Duration time = Duration.ofMillis(now - backup.getKey());
			Duration backupTime = BACKUP_TIMES.search(time, SortedSearchFilter.PreferGreater).get();
			if (lastBackupTime != null && backupTime.compareTo(lastBackupTime) > 0)
				backupTime = lastBackupTime;
			if (backupTime.equals(lastBackupTime)) {
				if (Math.abs(now - backup.getKey() - backupTime.toMillis()) < Math.abs(now - lastBackup - backupTime.toMillis()))
					backups.remove(lastBackup).delete(null);
				else
					backupTime = BACKUP_TIMES.lower(backupTime);
			}
			lastBackup = backup.getKey();
			lastBackupTime = backupTime;
		}
	}

	/** @return The times of all current backup files */
	public BetterSortedSet<Instant> getBackups() {
		BetterSortedSet<Instant> backups = BetterTreeSet.buildTreeSet(Instant::compareTo).safe(false).build();
		for (BetterFile file : theTargetFile.getParent().listFiles()) {
			long backupTime;
			try {
				backupTime = getBackupTime(file.getName());
				if (backupTime >= 0)
					backups.add(Instant.ofEpochMilli(backupTime));
			} catch (ParseException e) {
				System.err.println(thePrefix + " backup " + file.getName() + " not parseable as a backup: " + e);
			}
		}
		return backups;
	}

	/**
	 * @param fileName The name of a potential backup file
	 * @return The backup time of the file (millis since epoch) or -1 if the file is not a backup file
	 * @throws ParseException If the file name has the form of a backup file, but its date cannot be parsed
	 */
	public long getBackupTime(String fileName) throws ParseException {
		if (!fileName.startsWith(thePrefix) || !fileName.endsWith(theSuffix) //
			|| fileName.length() - thePrefix.length() - theSuffix.length() < 10)
			return -1;
		fileName = fileName.substring(thePrefix.length(), fileName.length() - theSuffix.length());
		return DATE_FORMAT.get().parse(fileName).getTime();
	}

	/**
	 * @param backupTime The backup time
	 * @return The file in this backup set with the given time (whether it exists or not)
	 */
	public BetterFile getBackup(Instant backupTime) {
		return theTargetFile.getParent().at(//
			new StringBuilder(thePrefix).append(DATE_FORMAT.get().format(new Date(backupTime.toEpochMilli()))).append(theSuffix)
				.toString());
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
			long backupTime;
			try {
				backupTime = oldBackup.getBackupTime(backup.getName());
			} catch (ParseException e) {
				backupTime = -1;
			}
			if (backupTime >= 0) {
				BetterFile newBackup = theTargetFile.getParent().at(//
					new StringBuilder(thePrefix).append(DATE_FORMAT.get().format(new Date(backupTime))).append(theSuffix).toString());
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
