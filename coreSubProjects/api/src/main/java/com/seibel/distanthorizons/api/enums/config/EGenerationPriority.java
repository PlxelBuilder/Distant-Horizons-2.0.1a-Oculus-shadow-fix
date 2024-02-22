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

package com.seibel.distanthorizons.api.enums.config;

/**
 * AUTO <br>
 * Near_First <br>
 * Far_First <br> <br>
 *
 * Determines which LODs should have priority when generating
 * outside the normal view distance.
 *
 * @author Leonardo Amato
 * @version 12-1-2021
 */
@Deprecated // not currently in use, if the config this enum represents is re-implemented, the deprecated flag can be removed
public enum EGenerationPriority
{
	// Reminder:
	// when adding items up the API minor version
	// when removing items up the API major version
	
	/** NEAR_FIRST when connected to servers and BALANCED when on single player */
	AUTO,
	
	NEAR_FIRST,
	
	BALANCED,
	
	FAR_FIRST
}
