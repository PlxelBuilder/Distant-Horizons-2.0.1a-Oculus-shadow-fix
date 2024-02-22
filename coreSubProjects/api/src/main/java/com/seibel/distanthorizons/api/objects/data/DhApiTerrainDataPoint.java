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

package com.seibel.distanthorizons.api.objects.data;

import com.seibel.distanthorizons.api.interfaces.block.IDhApiBiomeWrapper;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;

/**
 * Holds a single datapoint of terrain data.
 *
 * @author James Seibel
 * @version 2022-11-13
 * @since API 1.0.0
 */
public class DhApiTerrainDataPoint
{
	/**
	 * 0 = block <br>
	 * 1 = 2x2 blocks <br>
	 * 2 = 4x4 blocks <br>
	 * 4 = chunk (16x16 blocks) <br>
	 * 9 = region (512x512 blocks) <br>
	 */
	public final byte detailLevel;
	
	public final int lightLevel;
	public final int topYBlockPos;
	public final int bottomYBlockPos;
	
	public final IDhApiBlockStateWrapper blockStateWrapper;
	public final IDhApiBiomeWrapper biomeWrapper;
	
	
	
	public DhApiTerrainDataPoint(byte detailLevel, int lightLevel, int topYBlockPos, int bottomYBlockPos, IDhApiBlockStateWrapper blockStateWrapper, IDhApiBiomeWrapper biomeWrapper)
	{
		this.detailLevel = detailLevel;
		
		this.lightLevel = lightLevel;
		this.topYBlockPos = topYBlockPos;
		this.bottomYBlockPos = bottomYBlockPos;
		
		this.blockStateWrapper = blockStateWrapper;
		this.biomeWrapper = biomeWrapper;
	}
	
}
