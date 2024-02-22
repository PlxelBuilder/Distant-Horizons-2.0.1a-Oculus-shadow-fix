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
 * CONSTANT <br>
 * FREQUENT <br>
 * NORMAL <br>
 * RARE <br> <br>
 *
 * Determines how fast the buffers should be regenerated
 *
 * @author Leonardo Amato
 * @version 9-25-2021
 */
@Deprecated // not currently in use, if the config this enum represents is re-implemented, the deprecated flag can be removed
public enum EBufferRebuildTimes
{
	CONSTANT(0, 0, 0, 1),
	
	FREQUENT(1000, 500, 2500, 1),
	
	NORMAL(2000, 1000, 5000, 4),
	
	RARE(5000, 2000, 10000, 16);
	
	public final int playerMoveTimeout;
	public final int renderedChunkTimeout;
	public final int chunkChangeTimeout;
	public final int playerMoveDistance;
	
	EBufferRebuildTimes(int playerMoveTimeout, int renderedChunkTimeout, int chunkChangeTimeout, int playerMoveDistance)
	{
		this.playerMoveTimeout = playerMoveTimeout;
		this.renderedChunkTimeout = renderedChunkTimeout;
		this.chunkChangeTimeout = chunkChangeTimeout;
		this.playerMoveDistance = playerMoveDistance;
	}
}
