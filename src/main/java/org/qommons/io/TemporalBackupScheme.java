package org.qommons.io;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.qommons.TimeUtils;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;

/**
 * Logic utility for managing backups of a data set. This utility is agnostic as to what kind of data is it managing and only knows how to
 * manage a time-stamped set of backups so that a variety of backup ages (but not too many) are kept.
 */
public class TemporalBackupScheme {
	/**
	 * A manager for a particular set of backups
	 * 
	 * @param <T> The type of backup to manage
	 * @param <X> The type of exception this manager may throw
	 */
	public interface BackupManager<T, X extends Exception> {
		/**
		 * @return All current backups available for the data source
		 * @throws X If inspection of the set of backups fails
		 */
		Iterable<? extends T> getCurrentBackups() throws X;

		/**
		 * @param backup The backup to get the date for
		 * @return The time stamp of the data for which the backup was created
		 * @throws X If inspection fails
		 */
		Instant getDate(T backup) throws X;

		/**
		 * Notifies this manager that the given backup (from {@link #getCurrentBackups()}) should be preserved
		 * 
		 * @param backup The backup
		 * @throws X If this manager fails in any work it may need to do to preserve the backup
		 */
		void preserve(T backup) throws X;

		/**
		 * Notifies this manager that the given backup (from {@link #getCurrentBackups()}) should be deleted
		 * 
		 * @param backup The backup
		 * @throws X If this manager fails in any work it may need to do to delete the backup
		 */
		void delete(T backup) throws X;
	}

	/** The default set of backup ages to keep data for */
	public static final BetterSortedSet<Duration> DEFAULT_BACKUP_AGES = BetterCollections.unmodifiableSortedSet(//
		BetterTreeSet.buildTreeSet(Duration::compareTo).build().with(//
			Duration.ofSeconds(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(30), //
			Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(3), Duration.ofHours(4), //
			Duration.ofHours(6), Duration.ofHours(12), Duration.ofHours(18), //
			Duration.ofDays(1), Duration.ofDays(2), Duration.ofDays(3), Duration.ofDays(4), Duration.ofDays(5), Duration.ofDays(6), //
			Duration.ofDays(7), Duration.ofDays(10), Duration.ofDays(14), Duration.ofDays(21), Duration.ofDays(30), //
			Duration.ofDays(60), Duration.ofDays(90), Duration.ofDays(120), Duration.ofDays(180)));

	private static final Object CURRENT_BACKUP = new Object();

	private BetterSortedSet<Duration> theBackupAges;

	/** Creates a backup scheme with a {@link #DEFAULT_BACKUP_AGES default} set of backup ages */
	public TemporalBackupScheme() {
		theBackupAges = DEFAULT_BACKUP_AGES;
	}

	/**
	 * @param backupAges The set of ages to back up data for
	 * @return This backup scheme
	 */
	public TemporalBackupScheme setBackupAges(BetterSortedSet<Duration> backupAges) {
		if (backupAges == null)
			throw new NullPointerException();
		theBackupAges = backupAges;
		return this;
	}

	/**
	 * Tells this scheme that new data has been added. This scheme will then look at all copies of the data including the new one and
	 * determine which, if any, existing backups to preserve.
	 * 
	 * @param <T> The type of the backed up item
	 * @param <X> The type of exception the backup manager may throw
	 * @param now The time stamp of the new copy of the data
	 * @param manager The backup manager to provide data on the current set of backups as well as handle changes to that set
	 * @return Whether the current data should be copied into a new backup
	 * @throws X If the backup manager throws it
	 */
	public <T, X extends Exception> boolean dataRenewed(Instant now, BackupManager<T, X> manager) throws X {
		BetterSortedMap<Instant, T> backups = BetterTreeMap.create(Instant::compareTo);
		backups.put(now, (T) CURRENT_BACKUP);
		for (T backup : manager.getCurrentBackups()) {
			Instant date = manager.getDate(backup);
			if (date != null && date.compareTo(now) < 0) { // Only manage actual backups that are older than the current time
				T prev = backups.putIfAbsent(date, backup);
				if (prev != null)
					manager.delete(backup);
			}
		}

		Instant lastBackupTime = null;
		Duration lastBackupAge = null;
		// How close the previous (next older) backup was to the configured backup age
		Duration lastBackupAdherence = null;
		for (Map.Entry<Instant, T> backup : backups.entrySet()) {
			Duration time = TimeUtils.between(now, backup.getKey());
			if (time.isNegative())
				continue;
			Duration backupAge = theBackupAges.search(time, SortedSearchFilter.PreferGreater).get();
			if (lastBackupAge != null && backupAge.compareTo(lastBackupAge) > 0)
				backupAge = lastBackupAge;
			Duration thisBackupAdherence = time.minus(backupAge).abs();
			boolean preserveThis;
			if (backupAge.equals(lastBackupAge)) {
				// This backup fits into the same configured backup slot as the previous backup.
				// Preserve the one that is closest to the configured backup time and delete the other
				preserveThis = thisBackupAdherence.compareTo(lastBackupAdherence) < 0;
				if (preserveThis) {
					manager.delete(backups.remove(lastBackupTime));
				}
			} else { // The backup fits into a configured backup slot that up to this point may be unoccupied
				preserveThis = true;
				if (lastBackupTime != null)
					manager.preserve(backups.get(lastBackupTime));
			}
			if (preserveThis) {
				lastBackupTime = backup.getKey();
				lastBackupAge = backupAge;
				lastBackupAdherence = thisBackupAdherence;
			} else
				backups.remove(backup.getKey());
		}
		if (lastBackupTime != null) {
			T lastBackup = backups.get(lastBackupTime);
			if (lastBackup == CURRENT_BACKUP)
				return true;
			manager.preserve(lastBackup);
		}
		return now.equals(backups.keySet().peekLast());
	}
}
