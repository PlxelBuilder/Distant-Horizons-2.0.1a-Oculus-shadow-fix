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

import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;

/**
 * Represents a single column of Full LOD data.
 *
 * @see FullDataPointUtil
 */
public class SingleColumnFullDataAccessor implements IFullDataAccessor
{
	/**
	 * A flattened 2D array (for the X and Z directions) containing an array for the Y direction.
	 * TODO the flattened array is probably to reduce garbage collection overhead, but is doing it this way worth while? Having a 3D array would be much easier to understand
	 *
	 * @see FullDataArrayAccessor#dataArrays
	 */
	private final long[][] dataArrays;
	/** indicates what index of the {@link SingleColumnFullDataAccessor#dataArrays} is used by this accessor */
	private final int dataArrayIndex;
	private final FullDataPointIdMap mapping;
	
	
	
	public SingleColumnFullDataAccessor(FullDataPointIdMap mapping, long[][] dataArrays, int dataArrayIndex)
	{
		this.dataArrays = dataArrays;
		this.dataArrayIndex = dataArrayIndex;
		this.mapping = mapping;
		
		LodUtil.assertTrue(this.dataArrayIndex < this.dataArrays.length, "dataArrays.length [" + this.dataArrays.length + "] is less than the dataArrayIndex [" + this.dataArrayIndex + "].");
	}
	
	
	
	/** @return true if any data exists in this column. */
	public boolean doesColumnExist()
	{
		long[] dataColumn = this.dataArrays[this.dataArrayIndex];
		return dataColumn != null && dataColumn.length != 0;
	}
	
	@Override
	public FullDataPointIdMap getMapping() { return this.mapping; }
	
	@Override
	public SingleColumnFullDataAccessor get(int index)
	{
		if (index != 0)
		{
			throw new IllegalArgumentException("Only contains 1 column of full data!");
		}
		
		return this;
	}
	
	@Override
	public SingleColumnFullDataAccessor get(int relativeX, int relativeZ)
	{
		if (relativeX != 0 || relativeZ != 0)
		{
			throw new IllegalArgumentException("Only contains 1 column of full data!");
		}
		
		return this;
	}
	
	/** @return the entire array of raw full data points. */
	public long[] getRaw() { return this.dataArrays[this.dataArrayIndex]; }
	
	public long getSingle(int yIndex) { return this.dataArrays[this.dataArrayIndex][yIndex]; }
	public void setSingle(int yIndex, long fullDataPoint) { this.dataArrays[this.dataArrayIndex][yIndex] = fullDataPoint; }
	
	public void setNew(long[] newArray) { this.dataArrays[this.dataArrayIndex] = newArray; }
	
	/** @return how many data points are in this column */
	public int getSingleLength() { return this.dataArrays[this.dataArrayIndex].length; }
	
	@Override
	public int width() { return 1; }
	
	@Override
	public IFullDataAccessor subView(int width, int xOffset, int zOffset)
	{
		if (width != 1 || xOffset != 1 || zOffset != 1)
		{
			throw new IllegalArgumentException("Getting invalid range of subView from SingleColumnFullDataAccessor!");
		}
		
		return this;
	}
	
	/** WARNING: This may potentially share the underlying array objects! */
	public void shadowCopyTo(SingleColumnFullDataAccessor target)
	{
		if (target.mapping.equals(this.mapping))
		{
			target.dataArrays[target.dataArrayIndex] = this.dataArrays[this.dataArrayIndex];
		}
		else
		{
			int[] remappedEntryIds = target.mapping.mergeAndReturnRemappedEntityIds(this.mapping);
			long[] sourceData = this.dataArrays[this.dataArrayIndex];
			long[] newData = new long[sourceData.length];
			
			for (int i = 0; i < newData.length; i++)
			{
				newData[i] = FullDataPointUtil.remap(remappedEntryIds, sourceData[i]);
			}
			target.dataArrays[target.dataArrayIndex] = newData;
		}
	}
	
	/** Copies both ID data and mapping data. */
	public void deepCopyTo(SingleColumnFullDataAccessor target)
	{
		if (target.mapping.equals(this.mapping))
		{
			System.arraycopy(this.dataArrays[this.dataArrayIndex], 0, target.dataArrays[target.dataArrayIndex], 0, this.dataArrays[this.dataArrayIndex].length);
		}
		else
		{
			int[] remappedEntryIds = target.mapping.mergeAndReturnRemappedEntityIds(this.mapping);
			long[] sourceData = this.dataArrays[this.dataArrayIndex];
			// FIXME sourceData.length != 0 may not be a good solution and may end up breaking issues down the line, but fixes exceptions being fired here
			if (sourceData != null && sourceData.length != 0)
			{
				long[] newData = new long[sourceData.length];
				for (int i = 0; i < newData.length; i++)
				{
					newData[i] = FullDataPointUtil.remap(remappedEntryIds, sourceData[i]);
				}
				target.dataArrays[target.dataArrayIndex] = newData;
			}
		}
	}
	
	/**
	 * Replaces this column's data with data from the input {@link IFullDataAccessor}. <br>
	 * This is used to convert higher detail LOD data to lower detail LOD data.
	 */
	public void downsampleFrom(IFullDataAccessor source)
	{
		//TODO: average the data instead of just picking the first column
		SingleColumnFullDataAccessor firstColumn = source.get(0);
		firstColumn.deepCopyTo(this);
	}
	
}
