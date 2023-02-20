package org.qommons.io;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Enumeration;
import java.util.List;

import org.qommons.Named;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.tree.BetterTreeList;

/** Represents an address of this local host on a network */
public interface LocalNetworkAddress extends Named {
	/** @return The short name of the network interface that this address uses */
	String getNetworkInterfaceName();

	/** @return The long (display) name of the network interface that this address uses */
	String getNetworkInterfaceDisplayName();

	/** @return The IP version of this address */
	int getIpVersion();

	/** @return The bytes of this address */
	byte[] getAddress();

	/**
	 * Prints this address's bytes in a standard IP representation
	 * 
	 * @param str The string builder to append to (or null to create a new one)
	 * @return The string builder, after appending this address's bytes
	 */
	StringBuilder printAddress(StringBuilder str);

	/**
	 * @return A string containing this address's host name and IP address in the form 'hostname@address', or simply 'address' if the host
	 *         name was not resolved
	 */
	default String printNameAndAddress() {
		String addr = printAddress(null).toString();
		String host = getName();
		if (host == null || host.equals(addr))
			return addr;
		return new StringBuilder(host).append('@').append(addr).toString();
	}

	/**
	 * <p>
	 * A more reliable way to get local addresses than {@link InetAddress#getLocalHost()}. The Java method can be fooled under some
	 * configurations, e.g. cloned VMs.
	 * </p>
	 * <p>
	 * <b><font color="red">WARNING!!!</font></b> This call is <b>SLOW</b>. E.g. &lt;1s. This is because it actually makes network calls to
	 * determine which network interfaces and addresses returned by {@link NetworkInterface#getNetworkInterfaces()} are actually addresses
	 * that other hosts can use to reach this localhost. The getNetworkInterfaces call is also quite slow.
	 * </p>
	 * <p>
	 * For this reason, an internal caching mechanism is provided so this method may be called once and the result re-used.
	 * </p>
	 * 
	 * @param minIpVersion The minimum IP version to include
	 * @param maxIpVersion The maximum IP version to include
	 * @param cached Whether to use the cached version, if this method has previously been called
	 * @return The list of reachable, non-loopback network addresses for this host
	 * @throws SocketException If an error occurs retrieving the list of network interfaces
	 */
	public static BetterList<LocalNetworkAddress> getLocalAddresses(int minIpVersion, int maxIpVersion, boolean cached)
		throws SocketException {
		BetterList<LocalNetworkAddress> addresses;
		if (cached && Internal.CACHED_ALL != null) {
			addresses = Internal.CACHED_ALL;
		} else {
			addresses = BetterTreeList.<LocalNetworkAddress> build().build();
			Enumeration<NetworkInterface> intfs = NetworkInterface.getNetworkInterfaces();
			while (intfs.hasMoreElements()) {
				NetworkInterface intf = intfs.nextElement();
				if (intf.isLoopback())
					continue;
				else if (!intf.isUp())
					continue;
				Enumeration<InetAddress> addrs = intf.getInetAddresses();
				String intfName = null, intfDisplayName = null;
				boolean triedIntfName = false, triedIntfDisplayName = false;
				while (addrs.hasMoreElements()) {
					InetAddress addr = addrs.nextElement();
					int ipVersion;
					switch (addr.getAddress().length) {
					case 4:
						ipVersion = 4;
						break;
					case 16:
						ipVersion = 6;
						break;
					default:
						System.err.println("Unrecognized address version: " + Arrays.toString(addr.getAddress()));
						continue;
					}
					try {
						// This timeout seems long, but this call is actually small fries compared to the rest of this method.
						// Using a larger value here should make this method more reliable,
						// while still not contributing significantly to the length of this call
						if (!addr.isReachable(50))
							continue;
					} catch (IOException e) {
						continue;// Do nothing, just don't include the address
					}
					if (!triedIntfName) {
						triedIntfName = true;
						intfName = intf.getName();
					}
					if (!triedIntfDisplayName) {
						triedIntfDisplayName = true;
						intfDisplayName = intf.getDisplayName();
					}
					addresses.add(new Default(intfName, intfDisplayName, ipVersion, addr.getHostName(), addr.getAddress()));
				}
			}
			addresses = BetterCollections.unmodifiableList(addresses);
			Internal.CACHED_ALL = addresses;
		}
		if (minIpVersion <= 0 && maxIpVersion >= 6)
			return addresses;
		return BetterList.of(addresses.stream().filter(addr -> addr.getIpVersion() >= minIpVersion && addr.getIpVersion() <= maxIpVersion));
	}

	/**
	 * @param minIpVersion The minimum IP version to include
	 * @param maxIpVersion The maximum IP version to include
	 * @param cached Whether to use the cached version, if this method has previously been called
	 * @return The first found reachable, non-loopback network address for this host within an IP version in the given range
	 * @throws SocketException If an error occurs retrieving the network interface
	 * @see #getLocalAddresses(int, int, boolean)
	 */
	public static LocalNetworkAddress getLocalAddress(int minIpVersion, int maxIpVersion, boolean cached) throws SocketException {
		if (cached) {
			if (Internal.CACHED_FIRST.size() > minIpVersion)
				return Internal.CACHED_FIRST.get(Math.min(maxIpVersion, Internal.CACHED_FIRST.size() - 1));
			else if (Internal.CACHED_ALL != null) {
				// Return the first, highest-version address that matches the query
				int bestVersion = 0;
				LocalNetworkAddress bestAddr = null;
				for (LocalNetworkAddress addr : Internal.CACHED_ALL) {
					if (addr.getIpVersion() < minIpVersion || addr.getIpVersion() > maxIpVersion)
						continue;
					if (addr.getIpVersion() > bestVersion) {
						bestVersion = addr.getIpVersion();
						bestAddr = addr;
					}
				}
				if (bestAddr != null)
					return bestAddr;
			}
		}
		synchronized (Internal.class) {
			Enumeration<NetworkInterface> intfs = NetworkInterface.getNetworkInterfaces();
			BitSet replacedIpVersions = new BitSet();
			while (intfs.hasMoreElements()) {
				NetworkInterface intf = intfs.nextElement();
				if (intf.isLoopback())
					continue;
				else if (!intf.isUp())
					continue;
				Enumeration<InetAddress> addrs = intf.getInetAddresses();
				String intfName = null, intfDisplayName = null;
				boolean triedIntfName = false, triedIntfDisplayName = false;
				while (addrs.hasMoreElements()) {
					InetAddress addr = addrs.nextElement();
					int ipVersion;
					switch (addr.getAddress().length) {
					case 4:
						ipVersion = 4;
						break;
					case 16:
						ipVersion = 6;
						break;
					default:
						System.err.println("Unrecognized address version: " + Arrays.toString(addr.getAddress()));
						continue;
					}
					try {
						// This timeout seems long, but this call is actually small fries compared to the rest of this method.
						// Using a larger value here should make this method more reliable,
						// while still not contributing significantly to the length of this call
						if (!addr.isReachable(50))
							continue;
					} catch (IOException e) {
						continue;// Do nothing, just don't include the address
					}
					if (!triedIntfName) {
						triedIntfName = true;
						intfName = intf.getName();
					}
					if (!triedIntfDisplayName) {
						triedIntfDisplayName = true;
						intfDisplayName = intf.getDisplayName();
					}
					LocalNetworkAddress lna = null;
					// The CACHED_FIRST list is the first address of the each version
					if (!replacedIpVersions.get(ipVersion)) {
						lna = new Default(intfName, intfDisplayName, ipVersion, addr.getHostName(), addr.getAddress());
						replacedIpVersions.set(ipVersion);
						while (Internal.CACHED_FIRST.size() <= ipVersion)
							Internal.CACHED_FIRST.add(null);
						Internal.CACHED_FIRST.set(ipVersion, lna);
					}
					if (ipVersion >= minIpVersion && ipVersion <= maxIpVersion) {
						if (lna == null)
							lna = new Default(intfName, intfDisplayName, ipVersion, addr.getHostName(), addr.getAddress());
						return lna;
					}
				}
			}
		}
		return null;
	}

	/** Default implementation */
	public static class Default implements LocalNetworkAddress {
		private final String theNetworkInterfaceName;
		private final String theNetworkInterfaceDisplayName;
		private final int theIpVersion;
		private final String theHostName;
		private final byte[] theAddress;

		/**
		 * @param networkInterfaceName The short name of the network interface that the address uses
		 * @param networkInterfaceDisplayName The long (display) name of the network interface that the address uses
		 * @param ipVersion The IP version of the address
		 * @param hostName The host name of the address
		 * @param address The bytes of the address
		 */
		public Default(String networkInterfaceName, String networkInterfaceDisplayName, int ipVersion, String hostName, byte[] address) {
			theNetworkInterfaceName = networkInterfaceName;
			theNetworkInterfaceDisplayName = networkInterfaceDisplayName;
			theIpVersion = ipVersion;
			theHostName = hostName;
			theAddress = address;
		}

		@Override
		public String getName() {
			return theHostName;
		}

		@Override
		public String getNetworkInterfaceName() {
			return theNetworkInterfaceName;
		}

		@Override
		public String getNetworkInterfaceDisplayName() {
			return theNetworkInterfaceDisplayName;
		}

		@Override
		public int getIpVersion() {
			return theIpVersion;
		}

		@Override
		public byte[] getAddress() {
			return theAddress.clone();
		}

		private static final char[] HEX = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

		@Override
		public StringBuilder printAddress(StringBuilder str) {
			if (str == null)
				str = new StringBuilder();
			if (theIpVersion == 6) {
				boolean wasZero = false;
				boolean wasMultiZero = false;
				boolean first = true;
				boolean odd = true;
				byte oddByte = 0;
				for (byte b : theAddress) {
					if (odd)
						oddByte = b;
					else {
						if (b == 0 && oddByte == 0) {
							if (wasZero)
								wasMultiZero = true;
							else
								wasZero = true;
						} else {
							if (wasMultiZero)
								str.append("::");
							else if (wasZero)
								str.append(":0:");
							else if (!first)
								str.append(':');
							wasZero = wasMultiZero = false;
							int print = b;
							if (print < 0)
								print += 0x100;
							if (oddByte < 0)
								print |= (oddByte + 0x100) << 8;
							else
								print |= oddByte << 8;
							if (print > 0xfff)
								str.append(HEX[print >> 24]);
							if (print > 0xff)
								str.append(HEX[(print >> 16) & 0xf]);
							if (print > 0xf)
								str.append(HEX[(print >> 8) & 0xf]);
							str.append(HEX[print & 0xf]);
						}
						first = false;
					}
					odd = !odd;
				}
				if (wasMultiZero)
					str.append("::");
				else if (wasZero)
					str.append(":0");
			} else {
				boolean first = true;
				for (byte b : theAddress) {
					if (first)
						first = false;
					else
						str.append('.');
					str.append((b + 0x100) % 0x100);
				}
			}
			return str;
		}

		@Override
		public int hashCode() {
			int hash = 0;
			for (byte b : theAddress)
				hash = (hash << 5) ^ ((theAddress[b] + 0x100) % 0x100);
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			else if (!(obj instanceof LocalNetworkAddress))
				return false;
			return Arrays.equals(theAddress, ((LocalNetworkAddress) obj).getAddress());
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(theNetworkInterfaceName);
			if (theNetworkInterfaceDisplayName != null)
				str.append(" (").append(theNetworkInterfaceDisplayName).append(')');
			str.append('@');
			printAddress(str);
			if (theHostName != null)
				str.append(" (").append(theHostName).append(')');
			return str.toString();
		}
	}

	/** Internal resources for use by {@link LocalNetworkAddress} */
	class Internal {
		static BetterList<LocalNetworkAddress> CACHED_ALL;
		static final List<LocalNetworkAddress> CACHED_FIRST = new ArrayList<>();

		private Internal() {
		}
	}
}
