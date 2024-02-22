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

package com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces;

import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.SingleColumnFullDataAccessor;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.CompleteFullDataSource;
import com.seibel.distanthorizons.core.pos.DhLodPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.LodUtil;

public interface IIncompleteFullDataSource extends IFullDataSource
{
	/**
	 * Overwrites data in this object with non-null data from the input {@link IFullDataSource}. <br><br>
	 *
	 * This can be used to either merge same sized data sources or downsample to
	 */
	default void sampleFrom(IFullDataSource inputSource)
	{
		DhSectionPos inputPos = inputSource.getSectionPos();
		DhSectionPos thisPos = this.getSectionPos();
		LodUtil.assertTrue(inputPos.getDetailLevel() < thisPos.getDetailLevel());
		LodUtil.assertTrue(inputPos.overlapsExactly(this.getSectionPos()), "input source at pos: "+inputPos+" doesn't overlap with this source's pos: "+thisPos);
		
		if (inputSource.isEmpty())
		{
			return;
		}
		
		
		this.markNotEmpty();
		
		DhLodPos baseOffset = thisPos.getMinCornerLodPos(this.getDataDetailLevel());
		DhSectionPos inputOffset = inputPos.convertNewToDetailLevel(this.getDataDetailLevel());
		int offsetX = inputOffset.getX() - baseOffset.x;
		int offsetZ = inputOffset.getZ() - baseOffset.z;
		
		
		int numberOfDataPointsToUpdate = this.getWidthInDataPoints() / thisPos.getWidthCountForLowerDetailedSection(inputSource.getSectionPos().getDetailLevel()); // can be 0 if the input source is significantly smaller than this data source
		// should be 1 at minimum, to prevent divide by zero errors (and because trying to get 0 or a fractional data point doesn't make any sense)
		numberOfDataPointsToUpdate = Math.max(1, numberOfDataPointsToUpdate);
		
		
		int inputFractionWidth = inputSource.getWidthInDataPoints() / numberOfDataPointsToUpdate;
		for (int x = 0; x < numberOfDataPointsToUpdate; x++)
		{
			for (int z = 0; z < numberOfDataPointsToUpdate; z++)
			{
				SingleColumnFullDataAccessor thisDataColumn = this.getOrCreate(offsetX + x, offsetZ + z);
				SingleColumnFullDataAccessor inputDataColumn = inputSource.tryGet(inputFractionWidth * x, inputFractionWidth * z);
				
				if (inputDataColumn != null)
				{
					inputDataColumn.deepCopyTo(thisDataColumn);
				}
			}
		}
	}
	
	/**
	 * Attempts to convert this {@link IIncompleteFullDataSource} into a {@link CompleteFullDataSource}.
	 *
	 * @return a new {@link CompleteFullDataSource} if successful, this if the promotion failed, .
	 */
	IFullDataSource tryPromotingToCompleteDataSource();
	
	boolean hasBeenPromoted();
	
}
