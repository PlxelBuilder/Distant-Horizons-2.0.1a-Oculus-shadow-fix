/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020-2023 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.coreapi;

/**
 * This file is similar to mcmod.info
 * and contains most meta-information related to Distant Horizons.
 */
public final class ModInfo
{
	public static final String ID = "distanthorizons";
	/** The internal protocol version used for networking */
	public static final int PROTOCOL_VERSION = 1;
	/** The protocol version used for multiverse networking */
	public static final int MULTIVERSE_PLUGIN_PROTOCOL_VERSION = 1;
	/** The internal mod name */
	public static final String NAME = "DistantHorizons";
	/** Human-readable version of NAME */
	public static final String READABLE_NAME = "Distant Horizons";
	public static final String VERSION = "2.0.1-a";
	/** Returns true if the current build is an unstable developer build, false otherwise. */
	public static boolean IS_DEV_BUILD = VERSION.toLowerCase().contains("dev");
	
	/** This version should only be updated when breaking changes are introduced to the DH API */
	public static final int API_MAJOR_VERSION = 1;
	/** This version should be updated whenever new methods are added to the DH API */
	public static final int API_MINOR_VERSION = 0;
	/** This version should be updated whenever non-breaking fixes are added to the DH API */
	public static final int API_PATH_VERSION = 0;
	
	public static final String NETWORKING_RESOURCE_NAMESPACE = "distant_horizons";
	public static final String MULTIVERSE_PLUGIN_NAMESPACE = "world_control";
	
	
}
