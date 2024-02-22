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

package com.seibel.distanthorizons.core.util;

// 

import org.jetbrains.annotations.Contract;

/**
 * A helper class that is used to access the data from a long
 * formatted as a full data point. <br>
 * A full data point contains the most information and is the
 * base truth used when creating render data. <br><br>
 *
 * To access data from a long formatted as a render data point see: {@link RenderDataPointUtil} <br><br>
 *
 * <strong>DataPoint Format: </strong><br>
 * <code>
 * ID: blockState id <br>
 * Y: Height(signed) <br>
 * DP: Depth (Depth means the length of the block!) <br>
 * BL: Block light <br>
 * SL: Sky light <br><br>
 *
 * =======Bit layout=======	<br>
 * BL BL BL BL  SL SL SL SL <-- Top bits <br>
 * YY YY YY YY  YY YY YY YY	<br>
 * YY YY YY YY  DP DP DP DP	<br>
 * DP DP DP DP  DP DP DP DP	<br>
 * ID ID ID ID  ID ID IO ID	<br>
 * ID ID ID ID  ID ID IO ID	<br>
 * ID ID ID ID  ID ID IO ID	<br>
 * ID ID ID ID  ID ID IO ID <-- Bottom bits	<br>
 * </code>
 *
 * @see RenderDataPointUtil
 */
public class FullDataPointUtil
{
	/** Represents the data held by an empty data point */
	public static final int EMPTY_DATA_POINT = 0;
	
	public static final int ID_WIDTH = 32;
	public static final int DP_WIDTH = 12;
	public static final int Y_WIDTH = 12;
	public static final int LIGHT_WIDTH = 8;
	public static final int ID_OFFSET = 0;
	public static final int DP_OFFSET = ID_OFFSET + ID_WIDTH;
	/** indicates the Y position where the LOD starts relative to the level's minimum height */
	public static final int Y_OFFSET = DP_OFFSET + DP_WIDTH;
	public static final int LIGHT_OFFSET = Y_OFFSET + Y_WIDTH;
	
	
	public static final long ID_MASK = Integer.MAX_VALUE;
	public static final long INVERSE_ID_MASK = ~ID_MASK;
	public static final int DP_MASK = (int) Math.pow(2, DP_WIDTH) - 1;
	public static final int Y_MASK = (int) Math.pow(2, Y_WIDTH) - 1;
	public static final int LIGHT_MASK = (int) Math.pow(2, LIGHT_WIDTH) - 1;
	
	
	/** creates a new datapoint with the given values */
	public static long encode(int id, int depth, int y, byte lightPair)
	{
		LodUtil.assertTrue(y >= 0 && y < RenderDataPointUtil.MAX_WORLD_Y_SIZE, "Trying to create datapoint with y[{}] out of range!", y);
		LodUtil.assertTrue(depth > 0 && depth < RenderDataPointUtil.MAX_WORLD_Y_SIZE, "Trying to create datapoint with depth[{}] out of range!", depth);
		LodUtil.assertTrue(y + depth <= RenderDataPointUtil.MAX_WORLD_Y_SIZE, "Trying to create datapoint with y+depth[{}] out of range!", y + depth);
		
		long data = 0;
		data |= id & ID_MASK;
		data |= (long) (depth & DP_MASK) << DP_OFFSET;
		data |= (long) (y & Y_MASK) << Y_OFFSET;
		data |= (long) lightPair << LIGHT_OFFSET;
		LodUtil.assertTrue(getId(data) == id && getHeight(data) == depth && getBottomY(data) == y && getLight(data) == Byte.toUnsignedInt(lightPair),
				"Trying to create datapoint with id[{}], depth[{}], y[{}], lightPair[{}] but got id[{}], depth[{}], y[{}], lightPair[{}]!",
				id, depth, y, Byte.toUnsignedInt(lightPair), getId(data), getHeight(data), getBottomY(data), getLight(data));
		
		return data;
	}
	
	/** Returns the BlockState/Biome pair ID used to identify this LOD's color */
	public static int getId(long data) { return (int) (data & ID_MASK); }
	/** Returns how many blocks tall this LOD is. */
	public static int getHeight(long data) { return (int) ((data >> DP_OFFSET) & DP_MASK); }
	/** Returns the block position of the bottom vertices for this LOD relative to the level's minimum height. */
	public static int getBottomY(long data) { return (int) ((data >> Y_OFFSET) & Y_MASK); }
	public static int getLight(long data) { return (int) ((data >> LIGHT_OFFSET) & LIGHT_MASK); }
	
	public static String toString(long data) { return "[ID:" + getId(data) + ",Y:" + getBottomY(data) + ",Height:" + getHeight(data) + ",Light:" + getLight(data) + "]"; }
	
	/** Remaps the biome/blockState ID of the given datapoint */
	@Contract(pure = true)
	public static long remap(int[] newIdMapping, long data) { return (data & INVERSE_ID_MASK) | newIdMapping[(int) data]; }
	
}
