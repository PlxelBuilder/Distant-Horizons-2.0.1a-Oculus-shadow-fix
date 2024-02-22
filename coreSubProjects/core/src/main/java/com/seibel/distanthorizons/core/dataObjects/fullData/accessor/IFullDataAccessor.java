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

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces.IFullDataSource;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;

import java.util.Iterator;

/**
 * Contains raw full data points, which must be interpreted by the {@link FullDataPointUtil}. <br>
 * Often used by {@link IFullDataSource}'s.
 *
 * @see IFullDataSource
 * @see FullDataArrayAccessor
 * @see FullDataPointUtil
 */
public interface IFullDataAccessor
{
	FullDataPointIdMap getMapping();
	
	/** generally used for iterating through the whole data set */
	SingleColumnFullDataAccessor get(int index);
	SingleColumnFullDataAccessor get(int relativeX, int relativeZ);
	
	/** measured in full data points */
	int width();
	
	/**
	 * Creates a new {@link IFullDataAccessor} with the given width and starting at the given X and Z offsets. <br>
	 * The returned object will use the same underlining data structure (IE memory addresses) as the source {@link IFullDataAccessor}.
	 */
	IFullDataAccessor subView(int width, int xOffset, int zOffset);
	
	
	
	
	/** Returns an iterator that goes over each data column */
	default Iterator<SingleColumnFullDataAccessor> iterator()
	{
		return new Iterator<SingleColumnFullDataAccessor>()
		{
			private int index = 0;
			private final int size = width() * width();
			
			@Override
			public boolean hasNext() { return this.index < this.size; }
			
			@Override
			public SingleColumnFullDataAccessor next()
			{
				LodUtil.assertTrue(this.hasNext(), "No more data to iterate!");
				return get(this.index++);
			}
		};
	}
	
}
