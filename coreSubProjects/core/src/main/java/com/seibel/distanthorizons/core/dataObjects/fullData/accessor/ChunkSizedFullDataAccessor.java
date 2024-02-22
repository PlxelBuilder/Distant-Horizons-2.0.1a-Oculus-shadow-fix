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

package com.seibel.distanthorizons.core.dataObjects.fullData.accessor;

import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;

/**
 * A more specific version of {@link FullDataArrayAccessor}
 * that only contains full data for a single chunk.
 *
 * @see FullDataPointUtil
 */
public class ChunkSizedFullDataAccessor extends FullDataArrayAccessor
{
	public final DhChunkPos chunkPos;
	public final DhSectionPos sectionPos;
	
	// TODO replace this var with LodUtil.BLOCK_DETAIL_LEVEL 
	public final byte detailLevel = LodUtil.BLOCK_DETAIL_LEVEL;
	
	
	
	public ChunkSizedFullDataAccessor(DhChunkPos chunkPos)
	{
		super(new FullDataPointIdMap(new DhSectionPos(chunkPos)),
				new long[LodUtil.CHUNK_WIDTH * LodUtil.CHUNK_WIDTH][0],
				LodUtil.CHUNK_WIDTH);
		
		this.chunkPos = chunkPos;
		// TODO the fact this is using a LodUtil detail level instead of the DhSectionPos detail level may cause confusion and trouble down the line
		this.sectionPos = new DhSectionPos(LodUtil.CHUNK_DETAIL_LEVEL, this.chunkPos.x, this.chunkPos.z);
	}
	
	
	
	public void setSingleColumn(long[] data, int xRelative, int zRelative) { this.dataArrays[xRelative * LodUtil.CHUNK_WIDTH + zRelative] = data; }
	
	public long nonEmptyCount()
	{
		long count = 0;
		for (long[] data : this.dataArrays)
		{
			if (data.length != 0)
			{
				count += 1;
			}
		}
		return count;
	}
	
	public long emptyCount() { return (LodUtil.CHUNK_WIDTH * LodUtil.CHUNK_WIDTH) - this.nonEmptyCount(); }
	
	public DhSectionPos getSectionPos() { return this.sectionPos; }
	
	@Override
	public String toString() { return this.chunkPos + " " + this.nonEmptyCount(); }
	
}