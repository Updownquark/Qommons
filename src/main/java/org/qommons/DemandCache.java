/*
 * DemandCache.java Created Mar 26, 2009 by Andrew Butler, PSL
 */
package org.qommons;

import java.util.*;

/**
 * A cache that purges values according to several factors:
 * <ul>
 * <li>If the cache's half-life (preferred age) is set, cache entries will be purged based on the frequency and recency
 * of use. Each time an entry is accessed, the entry is marked so that it is less likely to be purged. A
 * {@link #put(Object, Object) put} on the item is stronger at keeping an entry from being purged than a
 * {@link #get(Object) get}. the {@link #access(Object, int)} method may also be used to mark an entry as less
 * purgeable.</li>
 * <li>If the cache's qualitizer is set with the constructor, the {@link Qualitizer#quality(Object, Object)} method will
 * be used to evaluate how important an item is. More important items will tend to stay in the cache longer than less
 * important items.</li>
 * <li>If the cache's preferred size is set, cache entries will be purged based on the size of the item. The cache will
 * tend to swell to its preferred size, at which point items will start being purged. If the cache's qualitizer is set,
 * larger items (according to {@link Qualitizer#size(Object, Object)}) will tend to be purged more quickly than smaller
 * items. If the qualitizer's size and quality methods return the same value for all items, these will cancel each other
 * out and function similarly to a cache with no qualitizer. As the cache fills up, items will tend to be purged more
 * quickly to keep the size down.</li>
 * </ul>
 * All of these criteria can be used together to make a cache that tends to purge items that have not been used in a
 * given amount of time under normal load but will keep items around longer under low load or will purge them faster
 * under high demand.
 *
 * @param <K> The type of key to use for the cache
 * @param <V> The type of value to cache
 * @see #shouldRemove(Object, CacheValue, float, float, int)
 */
public class DemandCache<K, V> implements Map<K, V>
{
	/**
	 * Allows this cache to assess the quality of a cache item to determine its value to the accessor. Implementors of
	 * this class should make the methods as fast as possible to speed up the purge process.
	 *
	 * @param <K> The key of the entry to qualitize
	 * @param <T> The type of value to be qualitized
	 */
	public static interface Qualitizer<K, T>
	{
		/**
		 * @param key The key of the value to assess
		 * @param value The value to assess
		 * @return The quality of the value. Units are undefined but must be consistent. 0 to 1 is recommended but not
		 *         required.
		 */
		float quality(K key, T value);

		/**
		 * @param key the key of the value to assess
		 * @param value The value to assess
		 * @return The amount of space the value takes up. Units are undefined but must be consistent. Bytes is
		 *         recommended but not required.
		 */
		float size(K key, T value);
	}

	/**
	 * Allows classes to watch and interact with the purging capability of a DemandCache.
	 *
	 * @param <K> The key type of the cache to watch
	 * @param <V> The value type of the cache to watch
	 */
	public static interface PurgeListener<K, V>
	{
		/**
		 * Called before a value is purged and gives this listener the opportunity to veto the purge.
		 *
		 * @param key The key of the entry to be purged
		 * @param value The value of the entry to be purged
		 * @return True if the entry may be purged, false to prevent the entry from being purged
		 */
		boolean prePurge(K key, V value);

		/**
		 * Called when a purge is run and a set of entries is purged. The entries have already been purged when this
		 * method is called
		 *
		 * @param keys The keys of the entries purged
		 * @param values The values of the entries purged
		 */
		void purged(java.util.List<? extends K> keys, java.util.List<? extends V> values);
	}

	/**
	 * Access to an object in the same amount as a get operation
	 *
	 * @see #access(Object, int)
	 */
	public static final int ACCESS_GET = 1;

	/**
	 * Access to an object in the same amount as a set operation
	 *
	 * @see #access(Object, int)
	 */
	public static final int ACCESS_SET = 2;

	class CacheValue
	{
		V value;

		float demand;
	}

	private final Qualitizer<K, V> theQualitizer;

	private final java.util.concurrent.ConcurrentHashMap<K, CacheValue> theCache;

	private PurgeListener<? super K, ? super V> [] thePurgeListeners;

	private float thePreferredSize;

	private long theHalfLife;

	private float theReference;

	private long theCheckedTime;

	private long thePurgeTime;

	private float thePurgeMods;

	/** Creates a DemandCache with default values */
	public DemandCache()
	{
		this(null, -1, -1);
	}

	/**
	 * Creates a DemandCache
	 *
	 * @param qualitizer The qualitizer to qualitize the values by
	 * @param prefSize The preferred size of this cache, or <=0 if this cache should have no preferred size
	 * @param halfLife The preferred entry age of this cache
	 */
	public DemandCache(Qualitizer<K, V> qualitizer, float prefSize, long halfLife)
	{
		theQualitizer = qualitizer;
		theCache = new java.util.concurrent.ConcurrentHashMap<>();
		thePreferredSize = prefSize;
		theHalfLife = halfLife;
		theReference = 1;
		theCheckedTime = System.currentTimeMillis();
		thePurgeTime = theCheckedTime;
		thePurgeListeners = new PurgeListener[0];
	}

	/**
	 * Creates a DemandCache
	 *
	 * @param qualitizer The qualitizer to qualitize the values by
	 * @param prefSize The preferred size of this cache, or <=0 if this cache should have no preferred size
	 * @param halfLife The preferred entry age of this cache
	 * @param cap The initial capacity for the cache
	 */
	public DemandCache(Qualitizer<K, V> qualitizer, float prefSize, long halfLife, int cap) {
		theQualitizer = qualitizer;
		theCache = new java.util.concurrent.ConcurrentHashMap<>(cap);
		thePreferredSize = prefSize;
		theHalfLife = halfLife;
		theReference = 1;
		theCheckedTime = System.currentTimeMillis();
		thePurgeTime = theCheckedTime;
		thePurgeListeners = new PurgeListener [0];
	}

	/** @return The preferred size of this cache, or <=0 if this cache has no preferred size */
	public float getPreferredSize()
	{
		return thePreferredSize;
	}

	/**
	 * @param prefSize The preferred size for this cache or <=0 if this cache should have no preferred size
	 */
	public void setPreferredSize(float prefSize)
	{
		boolean mod = prefSize <= 0 ? false : prefSize < thePreferredSize;
		thePreferredSize = prefSize;
		if(mod)
			purge(true);
	}

	/** @return The preferred age of items in this cache */
	public long getHalfLife()
	{
		return theHalfLife;
	}

	/** @param halfLife The preferred agefor items in this cache */
	public void setHalfLife(long halfLife)
	{
		boolean mod = halfLife <= 0 ? false : halfLife < theHalfLife;
		theHalfLife = halfLife;
		if(mod)
			purge(true);
	}

	/** @param listener The listener to watch this cache's purging with */
	public void addPurgeListener(PurgeListener<? super K, ? super V> listener)
	{
		if(!ArrayUtils.contains(thePurgeListeners, listener))
			thePurgeListeners = ArrayUtils.add(thePurgeListeners, listener);
	}

	/** @param listener The listener to remove from watching this cache's purging */
	public void removePurgeListener(PurgeListener<? super K, ? super V> listener)
	{
		thePurgeListeners = ArrayUtils.remove(thePurgeListeners, listener);
	}

	@Override
	public V get(Object key)
	{
		CacheValue value = theCache.get(key);
		if(value == null)
			return null;
		_access(value, ACCESS_GET);
		return value.value;
	}

	@Override
	public V put(K key, V value)
	{
		V ret = _put(key, value);
		purge(false);
		return ret;
	}

	private V _put(K key, V value)
	{
		CacheValue oldValue = theCache.get(key);
		if(oldValue != null && oldValue.value == value)
			return value;
		CacheValue newValue = new CacheValue();
		newValue.value = value;
		_access(newValue, ACCESS_SET);
		oldValue = theCache.put(key, newValue);
		if(theQualitizer == null || thePreferredSize <= 0)
		{
			if(oldValue == null)
				thePurgeMods++;
		}
		else
		{
			thePurgeMods += s(key, value);
			if(oldValue != null)
				thePurgeMods -= s(key, oldValue.value);
		}
		return oldValue == null ? null : oldValue.value;
	}

	private float q(K key, V value)
	{
		if(theQualitizer == null)
			return 1;
		float ret = theQualitizer.quality(key, value);
		if(Float.isNaN(ret))
			throw new IllegalStateException("NaN returned by quality method of qualitizer " + theQualitizer);
		if(ret < 0)
			throw new IllegalStateException("Negative value returned by quality method of qualitizer " + theQualitizer);
		return ret;
	}

	private float s(K key, V value)
	{
		if(theQualitizer == null)
			return 1;
		float ret = theQualitizer.size(key, value);
		if(Float.isNaN(ret))
			throw new IllegalStateException("NaN returned by size method of qualitizer " + theQualitizer);
		if(ret < 0)
			throw new IllegalStateException("Negative value returned by size method of qualitizer " + theQualitizer);
		return ret;
	}

	@Override
	public V remove(Object key)
	{
		CacheValue oldValue = theCache.remove(key);
		if(oldValue != null)
		{
			if(theQualitizer == null || thePreferredSize <= 0)
				thePurgeMods--;
			else
				thePurgeMods -= s((K) key, oldValue.value);
		}
		return oldValue == null ? null : oldValue.value;
	}

	@Override
	public void clear()
	{
		thePurgeMods = 0;
		theCache.clear();
	}

	@Override
	public int size()
	{
		return theCache.size();
	}

	@Override
	public boolean isEmpty()
	{
		return theCache.isEmpty();
	}

	/**
	 * Checks for the presence of a given key in the cache. This method does not affect the purgability of the entry
	 * accessed.
	 *
	 * @see Map#containsKey(Object)
	 */
	@Override
	public boolean containsKey(Object key)
	{
		return theCache.containsKey(key);
	}

	/**
	 * Checks for the presence of a given value in the cache. This method does not affect the purgability of the entry
	 * accessed.
	 *
	 * @see Map#containsValue(Object)
	 */
	@Override
	public boolean containsValue(Object value)
	{
		for(CacheValue val : theCache.values())
			if(value.equals(val.value))
				return true;
		return false;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m)
	{
		for(Map.Entry<? extends K, ? extends V> entry : m.entrySet())
			_put(entry.getKey(), entry.getValue());
		purge(false);
	}

	/**
	 * Gets the set of all keys to which values are mapped in this cache. The sets returned from {@link #keySet()},
	 * {@link #values()}, and {@link #entrySet()} have access to all the cache's data, but calling methods on these sets
	 * will not affect when any entries are purged as calls to {@link #get(Object)} would.
	 *
	 * @see Map#keySet()
	 */
	@Override
	public Set<K> keySet()
	{
		final Object [] keys;
		purge(false);
		keys = theCache.keySet().toArray();
		return new java.util.AbstractSet<K>()
		{
			@Override
			public java.util.Iterator<K> iterator()
			{
				return new java.util.Iterator<K>()
				{
					private int index = 0;

					@Override
					public boolean hasNext()
					{
						return index < keys.length;
					}

					@Override
					public K next()
					{
						if(index >= keys.length)
							throw new java.util.NoSuchElementException();
						index++;
						return (K) keys[index - 1];
					}

					@Override
					public void remove()
					{
						DemandCache.this.remove(keys[index - 1]);
					}
				};
			}

			@Override
			public int size()
			{
				return keys.length;
			}
		};
	}

	/**
	 * Gets the set of key/value mappings in this cache. The sets returned from {@link #keySet()}, {@link #values()},
	 * and {@link #entrySet()} have access to all the cache's data, but calling methods on these sets will not affect
	 * when any entries are purged as calls to {@link #get(Object)} would.
	 *
	 * @see Map#entrySet()
	 */
	@Override
	public Set<Map.Entry<K, V>> entrySet()
	{
		final Map.Entry<K, CacheValue> [] entries;
		purge(false);
		entries = theCache.entrySet().toArray(new Map.Entry [0]);
		return new java.util.AbstractSet<Map.Entry<K, V>>()
		{
			@Override
			public java.util.Iterator<Map.Entry<K, V>> iterator()
			{
				return new java.util.Iterator<Map.Entry<K, V>>()
				{
					int index = 0;

					@Override
					public boolean hasNext()
					{
						return index < entries.length;
					}

					@Override
					public Map.Entry<K, V> next()
					{
						Map.Entry<K, V> ret = new Map.Entry<K, V>()
						{
							private final int entryIndex = index;

							@Override
							public K getKey()
							{
								return entries[entryIndex].getKey();
							}

							@Override
							public V getValue()
							{
								return entries[entryIndex].getValue().value;
							}

							@Override
							public V setValue(V value)
							{
								V retValue = entries[entryIndex].getValue().value;
								entries[entryIndex].getValue().value = value;
								return retValue;
							}

							@Override
							public String toString()
							{
								return entries[entryIndex].getKey().toString() + "="
									+ entries[entryIndex].getValue().value;
							}
						};
						index++;
						return ret;
					}

					@Override
					public void remove()
					{
						DemandCache.this.remove(entries[index - 1].getKey());
					}
				};
			}

			@Override
			public int size()
			{
				return entries.length;
			}
		};
	}

	/**
	 * Gets the set of all values mapped to keys in this cache. The sets returned from {@link #keySet()},
	 * {@link #values()}, and {@link #entrySet()} have access to all the cache's data, but calling methods on these sets
	 * will not affect when any entries are purged as calls to {@link #get(Object)} would.
	 *
	 * @see Map#values()
	 */
	@Override
	public Set<V> values()
	{
		final Set<Entry<K, V>> entrySet = entrySet();
		return new java.util.AbstractSet<V>()
		{
			@Override
			public Iterator<V> iterator()
			{
				final java.util.Iterator<Map.Entry<K, V>> entryIter;
				entryIter = entrySet.iterator();
				return new java.util.Iterator<V>()
				{
					@Override
					public boolean hasNext()
					{
						return entryIter.hasNext();
					}

					@Override
					public V next()
					{
						return entryIter.next().getValue();
					}

					@Override
					public void remove()
					{
						entryIter.remove();
					}
				};
			}

			@Override
			public int size()
			{
				return entrySet.size();
			}
		};
	}

	/**
	 * @return The total size of the data in this cache, according to {@link Qualitizer#size(Object, Object)}. This
	 *         value will return the same as {@link #size()} if the qualitizer was not set in the constructor.
	 */
	public float getTotalSize()
	{
		if(theQualitizer == null)
			return theCache.size();
		float ret = 0;
		for(Entry<K, CacheValue> entry : theCache.entrySet())
			ret += s(entry.getKey(), entry.getValue().value);
		return ret;
	}

	/**
	 * @return The average quality of the values in this cache, according to {@link Qualitizer#quality(Object, Object)}.
	 *         This value will return 1 if the qualitizer was not set in the constructor.
	 */
	public float getAverageQuality()
	{
		if(theQualitizer == null)
			return theCache.size();
		float ret = 0;
		int count = 0;
		for(Entry<K, CacheValue> entry : theCache.entrySet())
		{
			count++;
			ret += q(entry.getKey(), entry.getValue().value);
		}
		return ret / count;
	}

	private void _access(CacheValue value, int weight)
	{
		if(weight <= 0 || theHalfLife <= 0)
			return;
		updateReference();
		if(weight > 10)
			weight = 10;
		float ref = theReference * weight;
		if(value.demand <= 1)
			value.demand += ref;
		else if(value.demand < ref * 2)
			value.demand += ref / value.demand;
	}

	/**
	 * Performs an access operation on a cache item, causing it to live longer in the cache
	 *
	 * @param key The key of the item to access
	 * @param weight The weight of the access--higher weight will result in a more persistent cache item. 1-10 are
	 *        supported. A guideline is that the cache item will survive longer by {@link #getHalfLife()} x
	 *        <code>weight</code>.
	 * @see #ACCESS_GET
	 * @see #ACCESS_SET
	 */
	public void access(K key, int weight)
	{
		CacheValue value = theCache.get(key);
		if(value != null)
			_access(value, weight);
	}

	/**
	 * Purges the cache of values that are deemed of less use to the accessor. The behavior of this method depends the
	 * behavior of {@link #shouldRemove(Object, CacheValue, float, float, int)}
	 *
	 * @param force If false, the purge operation will only be performed if this cache determines that a purge is needed
	 *        (determined by the number of modifications directly to this cache and time elapsed since the last purge).
	 *        <code>force</code> may be used to cause a purge without a perceived "need", such as may be warranted if a
	 *        cached item's size or quality changes drastically.
	 */
	public void purge(boolean force)
	{
		if(!force)
		{
			float purgeNeed = 0;
			if(theHalfLife > 0 && theCheckedTime > thePurgeTime)
				purgeNeed += (System.currentTimeMillis() - thePurgeTime) * 10.0f / theHalfLife;
			if(thePreferredSize > 0)
				purgeNeed += thePurgeMods / thePreferredSize * 10;
			else
				purgeNeed += thePurgeMods / 100;
			if(purgeNeed < 1)
				return;
		}
		updateReference();
		scaleReference();
		int count = 0;
		float totalSize = 0;
		float totalQuality = 0;
		if(theQualitizer == null)
		{
			count = theCache.size();
			totalSize = count;
			totalQuality = count;
		}
		else
			for(Entry<K, CacheValue> entry : theCache.entrySet())
			{
				count++;
				totalSize += s(entry.getKey(), entry.getValue().value);
				totalQuality += q(entry.getKey(), entry.getValue().value);
			}

		java.util.ArrayList<K> purgeKeys = new java.util.ArrayList<>();
		java.util.Iterator<Entry<K, CacheValue>> iter = theCache.entrySet().iterator();
		final PurgeListener<? super K, ? super V> [] pls = thePurgeListeners;
		while(iter.hasNext())
		{
			Entry<K, CacheValue> entry = iter.next();
			CacheValue value = entry.getValue();
			if(!shouldRemove(entry.getKey(), value, totalSize, totalQuality / count, count))
				continue;
			boolean purge = true;
			for(int i = 0; i < pls.length && purge; i++)
				purge = pls[i].prePurge(entry.getKey(), value.value);
			if(!purge)
				continue;
			count--;
			if(theQualitizer != null)
			{
				totalSize -= s(entry.getKey(), value.value);
				totalQuality -= q(entry.getKey(), value.value);
			}
			else
			{
				totalSize--;
				totalQuality--;
			}
			purgeKeys.add(entry.getKey());
		}
		thePurgeTime = System.currentTimeMillis();
		thePurgeMods = 0;

		if(purgeKeys.isEmpty())
			return;
		java.util.ArrayList<V> values = new java.util.ArrayList<>();
		for(int i = 0; i < purgeKeys.size(); i++)
		{
			CacheValue cv = theCache.remove(purgeKeys.get(i));
			if(cv != null)
				values.add(cv.value);
			else
			{
				purgeKeys.remove(i);
				i--;
			}
		}
		if(purgeKeys.isEmpty())
			return;
		for(int i = 0; i < pls.length; i++)
			pls[i].purged(purgeKeys, values);
	}

	/**
	 * Determines whether a cache value should be removed from the cache. The behavior of this method depends on many
	 * variables:
	 * <ul>
	 * <li>How frequently and recently the value has been accessed</li>
	 * <li>The quality of the value according to {@link Qualitizer#quality(Object, Object)} compared to the average
	 * quality of the cache</li>
	 * <li>The size of the value according to {@link Qualitizer#size(Object, Object)} compared to the average size of
	 * the cache's values</li>
	 * <li>The total size of the cache compared to its preferred size (assuming this is set to a value greater than 0)
	 * </ul>
	 *
	 * @param key The key whose value to determine the quality of
	 * @param value The value to determine the quality of
	 * @param totalSize The total size (determined by the Qualitizer) of this cache
	 * @param avgQuality The average quality of the cache
	 * @param entryCount The number of entries in this cache
	 * @return Whether the value should be removed from the cache
	 */
	protected boolean shouldRemove(K key, CacheValue value, float totalSize, float avgQuality, int entryCount)
	{
		float quality = q(key, value.value);
		float size = s(key, value.value);
		if(quality == 0)
			return true; // Remove if the value has no quality
		if(size == 0)
			return false; // Don't remove if the value takes up no space

		/* Take into account how frequently and recently the value was accessed */
		float valueQuality = 1;
		if(value.demand > 0)
			valueQuality = value.demand / theReference;
		/* Take into account the inherent quality in the value compareed to the average */
		valueQuality *= quality / avgQuality;
		/* Take into account the value's size compared with the average size */
		float avgSize = totalSize / entryCount;
		valueQuality /= size / avgSize;
		/* Take into account the overall size of this cache compared with the preferred size
		 * (Whether it is too big or has room to spare) */
		if(thePreferredSize > 0)
		{
			if(totalSize > thePreferredSize * 2)
				return size >= avgSize * .99;
				else if(totalSize > thePreferredSize)
				{
					float sizeToQual = (size / avgSize) / (quality / avgQuality);
					valueQuality /= Math.pow(2, totalSize / thePreferredSize * 4 * sizeToQual * sizeToQual);
				}
				else
					valueQuality /= totalSize / thePreferredSize;
		}
		return valueQuality < 0.5f;
	}

	/** Updates {@link #theReference} to devalue all items in the cache with age. */
	private void updateReference()
	{
		if(theHalfLife <= 0)
			return;
		long time = System.currentTimeMillis();
		if(time - theCheckedTime < theHalfLife / 100)
			return;
		theReference *= Math.pow(2, (time - theCheckedTime) * 1.0 / theHalfLife);
		theCheckedTime = time;
	}

	/**
	 * Scales all {@link CacheValue#demand} values to keep them and {@link #theReference} small. This allows the cache
	 * to be kept for long periods of time. The write lock must be obtained before calling this method.
	 */
	private void scaleReference()
	{
		if(theHalfLife <= 0)
			return;
		if(theReference > 1e7)
		{
			for(CacheValue value : theCache.values())
				value.demand /= theReference;
			theReference = 1;
		}
	}

	private static final boolean DO_HL_TEST = false;

	private static final boolean DO_PS_TEST = false;

	private static final boolean DO_MIX_TEST = true;

	/**
	 * Unit-test
	 *
	 * @param args Command-line args, ignored
	 */
	public static void main(String [] args)
	{
		DemandCache<Integer, Long> cache;
		if(DO_HL_TEST)
		{
			System.out.println("Testing the half life functionality");
			cache = new DemandCache<>();
			cache.setHalfLife(10000); // 10 seconds
			cache.addPurgeListener(new PurgeListener<Integer, Long>()
			{
				@Override
				public boolean prePurge(Integer key, Long value)
				{
					return true;
				}

				@Override
				public void purged(List<? extends Integer> keys, List<? extends Long> values)
				{
					long now = System.currentTimeMillis();
					if(values.size() == 1)
					{
						System.out.println("Purged 1 value, age "
 + QommonsUtils.printTimeLength(now - values.get(0).longValue()));
						return;
					}
					long min = Long.MAX_VALUE, max = Long.MIN_VALUE, mean = 0;
					for(Long v : values)
					{
						if(v.longValue() < min)
							min = v.longValue();
						if(v.longValue() > max)
							max = v.longValue();
						mean += v.longValue();
					}
					mean /= values.size();
					min = now - min;
					max = now - max;
					mean = now - mean;
					System.out.println("Purged " + values.size() + " values, age min="
 + QommonsUtils.printTimeLength(max) + ", avg="
						+ QommonsUtils.printTimeLength(mean) + ", max=" + QommonsUtils.printTimeLength(min));
				}
			});
			final long begin = System.currentTimeMillis();
			long now = begin;
			try
			{
				while(now - begin < 60000) // 1 minute
				{
					now = System.currentTimeMillis();
					cache.put(Integer.valueOf(QommonsUtils.getRandomInt()), Long.valueOf(now));
				}
			} catch(OutOfMemoryError e)
			{
				System.out.println(cache.size());
				return;
			}

			now = System.currentTimeMillis();
			long min = Long.MAX_VALUE, max = Long.MIN_VALUE, mean = 0;
			for(Entry<Integer, Long> entry : cache.entrySet())
			{
				if(entry.getValue().longValue() < min)
					min = entry.getValue().longValue();
				if(entry.getValue().longValue() > max)
					max = entry.getValue().longValue();
				mean += entry.getValue().longValue();
			}
			mean /= cache.size();
			min = now - min;
			max = now - max;
			mean = now - mean;
			System.out.println("Half-life testing completed. Retained " + cache.size() + " entries, age min="
 + QommonsUtils.printTimeLength(max)
					+ ", avg=" + QommonsUtils.printTimeLength(mean) + ", max=" + QommonsUtils.printTimeLength(min));
			cache.clear();
			cache = null;
		}

		if(DO_PS_TEST)
		{
			System.out.println("Testing the preferred size functionality");
			cache = new DemandCache<>(new Qualitizer<Integer, Long>()
			{
				@Override
				public float quality(Integer key, Long value)
				{
					return 1;
				}

				@Override
				public float size(Integer key, Long value)
				{
					return value.floatValue() / 100;
				}
			}, 1000000, -1);
			cache.addPurgeListener(new PurgeListener<Integer, Long>()
			{
				@Override
				public boolean prePurge(Integer key, Long value)
				{
					return true;
				}

				@Override
				public void purged(List<? extends Integer> keys, List<? extends Long> values)
				{
					long min = Long.MAX_VALUE, max = Long.MIN_VALUE, mean = 0;
					for(Long v : values)
					{
						if(v.longValue() < min)
							min = v.longValue();
						if(v.longValue() > max)
							max = v.longValue();
						mean += v.longValue();
					}
					mean /= values.size();
					min /= 100;
					max /= 100;
					mean /= 100;
					System.out.println("Purged " + values.size() + " values, size min=" + min + ", avg=" + mean
						+ ", max=" + max);
				}
			});
			final long begin = System.currentTimeMillis();
			long now = begin;
			try
			{
				while(now - begin < 60000) // 1 minute
				{
					now = System.currentTimeMillis();
					int val = QommonsUtils.getRandomInt() >>> 1;
				val %= 100000;
				cache.put(Integer.valueOf(val), Long.valueOf(val));
				}
			} catch(OutOfMemoryError e)
			{
				System.out.println(cache.size());
				return;
			}
			long min = Long.MAX_VALUE, max = Long.MIN_VALUE, total = 0, mean = 0;
			for(Entry<Integer, Long> entry : cache.entrySet())
			{
				if(entry.getValue().longValue() < min)
					min = entry.getValue().longValue();
				if(entry.getValue().longValue() > max)
					max = entry.getValue().longValue();
				total += entry.getValue().longValue();
			}
			min /= 100;
			max /= 100;
			total /= 100;
			mean = total / cache.size();
			System.out.println("Preferred size testing completed. Retained " + cache.size() + " entries, size min="
				+ min + ", avg=" + mean + ", max=" + max + ", total=" + total);
			cache.clear();
			cache = null;
		}
		if(DO_MIX_TEST)
		{
			class TestItem
			{
				float theSize;

				float theQuality;
			}
			System.out.println("Testing the combined functionality");
			DemandCache<TestItem, TestItem> cache2;
			cache2 = new DemandCache<>(new Qualitizer<TestItem, TestItem>()
			{
				@Override
				public float quality(TestItem key, TestItem value)
				{
					return value.theQuality;
				}

				@Override
				public float size(TestItem key, TestItem value)
				{
					return value.theSize;
				}
			}, 10000, 60000);
			Random rand = new Random();
			long count = 0;
			long time = System.currentTimeMillis();
			while(true)
			{
				TestItem item = new TestItem();
				item.theSize = 2 + rand.nextFloat() * 8;
				item.theQuality = rand.nextFloat() * 100;
				cache2.put(item, item);
				count++;
				if(count % 100 == 0 && count > 0)
				{
					long time2 = System.currentTimeMillis();
					if(time2 >= time + 5000)
					{
						time = time2;
						System.out.println(count + " items inserted, retaining " + cache2.size() + ", total size="
							+ cache2.getTotalSize() + ", avg size=" + (cache2.getTotalSize() / cache2.size())
							+ ", avg qual=" + cache2.getAverageQuality());
					}
				}
				if(count % 1000 == 0)
				{
					for(TestItem item2 : cache2.values())
						item2.theSize *= 1 + rand.nextFloat() / 3;
				}
			}
		}
	}
}
