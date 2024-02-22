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

package com.seibel.distanthorizons.core.generation;

import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.SingleColumnFullDataAccessor;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces.IFullDataSource;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Handles finding any positions in a given {@link IFullDataSource} that
 * aren't generated.
 */
public class MissingWorldGenPositionFinder
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	/**
	 * @param generatorDetailLevel the detail level that the un-generated positions should be split into.
	 * @param onlyReturnPositionsTheGeneratorCanAccept
	 *      If true this will only return positions with the same detail level as generatorDetailLevel. <br>
	 *      If false this will return the lowest detail level possible for totally empty sections. <br>
	 *      As of 2023-9-28 both have been tested and confirmed working with the Batch world generator, the only difference is that
	 *      the task list will be deceptively small if this value is false.
	 *
	 * @return the list of {@link DhSectionPos} that aren't generated in this data source.
	 */
	public static ArrayList<DhSectionPos> getUngeneratedPosList(IFullDataSource dataSource, byte generatorDetailLevel, boolean onlyReturnPositionsTheGeneratorCanAccept)
	{
		ArrayList<DhSectionPos> posArray = getUngeneratedPosListForQuadrant(dataSource, dataSource.getSectionPos(), generatorDetailLevel);
		
		if (onlyReturnPositionsTheGeneratorCanAccept)
		{
			LinkedList<DhSectionPos> posList = new LinkedList<>(posArray);
			
			// subdivide positions until they match the generatorDetailLevel
			ArrayList<DhSectionPos> cleanedPosArray = new ArrayList<>();
			while (posList.size() > 0)
			{
				DhSectionPos pos = posList.remove();
				if (pos.getDetailLevel() > generatorDetailLevel)
				{
					pos.forEachChild((childPos) -> posList.push(childPos));
				}
				else
				{
					cleanedPosArray.add(pos);
				}
			}
			
			return cleanedPosArray;
		}
		else
		{
			return posArray;
		}
	}
	private static ArrayList<DhSectionPos> getUngeneratedPosListForQuadrant(IFullDataSource dataSource, DhSectionPos quadrantPos, byte generatorDetailLevel)
	{
		ArrayList<DhSectionPos> ungeneratedPosList = new ArrayList<>();
		
		int sourceRelWidthInDataPoints = dataSource.getWidthInDataPoints();
		
		
		if (quadrantPos.getDetailLevel() == generatorDetailLevel)
		{
			// we are at the highest detail level the world generator can accept,
			// we either need to generate this whole section, or not at all
			
			ESectionPopulationState populationState = getPopulationStateForPos(dataSource, quadrantPos, sourceRelWidthInDataPoints);
			if (populationState != ESectionPopulationState.COMPLETE)
			{
				// at least 1 data point is missing,
				// this whole section must be generated
				ungeneratedPosList.add(quadrantPos);
			}
		}
		else if (quadrantPos.getDetailLevel() > generatorDetailLevel)
		{
			// detail level too low for world generator, check child positions
			for (int i = 0; i < 4; i++)
			{
				DhSectionPos inputPos = quadrantPos.getChildByIndex(i);
				
				ESectionPopulationState populationState = getPopulationStateForPos(dataSource, inputPos, sourceRelWidthInDataPoints);
				if (populationState == ESectionPopulationState.COMPLETE)
				{
					// no generation necessary
					continue;
				}
				else if (populationState == ESectionPopulationState.EMPTY)
				{
					// nothing exists for this sub quadrant, add this sub-quadrant's position
					ungeneratedPosList.add(inputPos);
				}
				else if (populationState == ESectionPopulationState.PARTIAL)
				{
					// some data exists in this quadrant, but not all that we need
					// recurse down to determine which sub-quadrant positions will need generation
					ungeneratedPosList.addAll(getUngeneratedPosListForQuadrant(dataSource, inputPos, generatorDetailLevel));
				}
			}
		}
		else
		{
			throw new IllegalArgumentException("detail level lower than world generator can accept.");
		}
		
		return ungeneratedPosList;
	}
	private static ESectionPopulationState getPopulationStateForPos(IFullDataSource dataSource, DhSectionPos inputPos, int sourceRelWidthInDataPoints)
	{
		// TODO comment
		
		byte childDetailLevel = inputPos.getDetailLevel();
		
		int quadrantDetailLevelDiff = dataSource.getSectionPos().getDetailLevel() - childDetailLevel;
		int widthInSecPos = BitShiftUtil.powerOfTwo(quadrantDetailLevelDiff);
		int relWidthForSecPos = sourceRelWidthInDataPoints / widthInSecPos;
		
		DhSectionPos minSecPos = dataSource.getSectionPos().convertNewToDetailLevel(childDetailLevel);
		
		
		
		int minRelX = inputPos.getX() - minSecPos.getX();
		int maxRelX = minRelX + 1;
		
		int minRelZ = inputPos.getZ() - minSecPos.getZ();
		int maxRelZ = minRelZ + 1;
		
		minRelX = minRelX * relWidthForSecPos;
		maxRelX = maxRelX * relWidthForSecPos;
		
		minRelZ = minRelZ * relWidthForSecPos;
		maxRelZ = maxRelZ * relWidthForSecPos;
		
		
		
		boolean quadrantFullyGenerated = true;
		boolean quadrantEmpty = true;
		for (int relX = minRelX; relX < maxRelX; relX++)
		{
			for (int relZ = minRelZ; relZ < maxRelZ; relZ++)
			{
				SingleColumnFullDataAccessor column = dataSource.tryGet(relX, relZ);
				if (column == null || !column.doesColumnExist())
				{
					// no data for this relative position
					quadrantFullyGenerated = false;
				}
				else
				{
					// data exists for this pos
					quadrantEmpty = false;
				}
			}
		}
		
		
		if (quadrantFullyGenerated)
		{
			// no generation necessary
			return ESectionPopulationState.COMPLETE;
		}
		else if (quadrantEmpty)
		{
			// nothing exists for this sub quadrant, add this sub-quadrant's position
			return ESectionPopulationState.EMPTY;
		}
		else
		{
			// some data exists in this quadrant, but not all that we need
			// recurse down to determine which sub-quadrant positions will need generation
			return ESectionPopulationState.PARTIAL;
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	private enum ESectionPopulationState
	{
		COMPLETE,
		EMPTY,
		PARTIAL
	}
}
