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

package com.seibel.distanthorizons.core.dataObjects.fullData;

import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.SingleColumnFullDataAccessor;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.CompleteFullDataSource;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces.IFullDataSource;
import com.seibel.distanthorizons.core.file.fullDatafile.IFullDataSourceProvider;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhLodPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.LodUtil;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class FullDataDownSampler
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	public static CompletableFuture<IFullDataSource> createDownSamplingFuture(DhSectionPos newTarget, IFullDataSourceProvider provider)
	{
		// TODO: Make this future somehow run with lowest priority (to ensure ram usage stays low)
		return createDownSamplingFuture(CompleteFullDataSource.createEmpty(newTarget), provider);
	}
	
	public static CompletableFuture<IFullDataSource> createDownSamplingFuture(CompleteFullDataSource target, IFullDataSourceProvider provider)
	{
		int sectionSizeNeeded = 1 << target.getDataDetailLevel();
		
		ArrayList<CompletableFuture<IFullDataSource>> futures;
		DhLodPos basePos = target.getSectionPos().getSectionBBoxPos().getCornerLodPos(CompleteFullDataSource.SECTION_SIZE_OFFSET);
		
		
		if (sectionSizeNeeded <= CompleteFullDataSource.SECTION_SIZE_OFFSET)
		{
			futures = new ArrayList<>(sectionSizeNeeded * sectionSizeNeeded);
			for (int xOffset = 0; xOffset < sectionSizeNeeded; xOffset++)
			{
				for (int zOffset = 0; zOffset < sectionSizeNeeded; zOffset++)
				{
					CompletableFuture<IFullDataSource> future = provider.readAsync(new DhSectionPos(
							CompleteFullDataSource.SECTION_SIZE_OFFSET, basePos.x + xOffset, basePos.z + zOffset));
					future = future.whenComplete((source, ex) -> {
						if (ex == null && source != null && source instanceof CompleteFullDataSource)
						{
							downSample(target, (CompleteFullDataSource) source);
						}
						else if (ex != null)
						{
							LOGGER.error("Error while down sampling", ex);
						}
					});
					futures.add(future);
				}
			}
		}
		else
		{
			futures = new ArrayList<>(CompleteFullDataSource.WIDTH * CompleteFullDataSource.WIDTH);
			int multiplier = sectionSizeNeeded / CompleteFullDataSource.WIDTH;
			for (int xOffset = 0; xOffset < CompleteFullDataSource.WIDTH; xOffset++)
			{
				for (int zOffset = 0; zOffset < CompleteFullDataSource.WIDTH; zOffset++)
				{
					CompletableFuture<IFullDataSource> future = provider.readAsync(new DhSectionPos(
							CompleteFullDataSource.SECTION_SIZE_OFFSET, basePos.x + xOffset * multiplier, basePos.z + zOffset * multiplier));
					future = future.whenComplete((source, ex) -> {
						if (ex == null && source != null && source instanceof CompleteFullDataSource)
						{
							downSample(target, (CompleteFullDataSource) source);
						}
						else if (ex != null)
						{
							LOGGER.error("Error while down sampling", ex);
						}
					});
					futures.add(future);
				}
			}
		}
		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> target);
	}
	
	public static void downSample(CompleteFullDataSource target, CompleteFullDataSource source)
	{
		LodUtil.assertTrue(target.getSectionPos().overlapsExactly(source.getSectionPos()));
		LodUtil.assertTrue(target.getDataDetailLevel() > source.getDataDetailLevel());
		
		byte detailDiff = (byte) (target.getDataDetailLevel() - source.getDataDetailLevel());
		DhSectionPos trgPos = target.getSectionPos();
		DhSectionPos srcPos = source.getSectionPos();
		
		if (detailDiff >= CompleteFullDataSource.SECTION_SIZE_OFFSET)
		{
			// The source occupies only 1 datapoint in the target
			// FIXME: TEMP method for down-sampling: take only the corner column
			int sourceSectionPerTargetData = 1 << (detailDiff - CompleteFullDataSource.SECTION_SIZE_OFFSET);
			if (srcPos.getX() % sourceSectionPerTargetData != 0 || srcPos.getZ() % sourceSectionPerTargetData != 0)
			{
				return;
			}
			DhLodPos trgOffset = trgPos.getMinCornerLodPos(target.getDataDetailLevel());
			DhLodPos srcOffset = srcPos.getSectionBBoxPos().convertToDetailLevel(target.getDataDetailLevel());
			int offsetX = trgOffset.x - srcOffset.x;
			int offsetZ = trgOffset.z - srcOffset.z;
			LodUtil.assertTrue(offsetX >= 0 && offsetX < CompleteFullDataSource.WIDTH
					&& offsetZ >= 0 && offsetZ < CompleteFullDataSource.WIDTH);
			target.markNotEmpty();
			source.get(0, 0).deepCopyTo(target.get(offsetX, offsetZ));
			
		}
		else if (detailDiff > 0)
		{
			// The source occupies multiple data-points in the target
			int srcDataPerTrgData = 1 << detailDiff;
			int overlappedTrgDataSize = CompleteFullDataSource.WIDTH / srcDataPerTrgData;
			
			DhLodPos trgOffset = trgPos.getMinCornerLodPos(target.getDataDetailLevel());
			DhLodPos srcOffset = srcPos.getSectionBBoxPos().getCornerLodPos(target.getDataDetailLevel());
			int offsetX = trgOffset.x - srcOffset.x;
			int offsetZ = trgOffset.z - srcOffset.z;
			LodUtil.assertTrue(offsetX >= 0 && offsetX < CompleteFullDataSource.WIDTH
					&& offsetZ >= 0 && offsetZ < CompleteFullDataSource.WIDTH);
			target.markNotEmpty();
			
			for (int ox = 0; ox < overlappedTrgDataSize; ox++)
			{
				for (int oz = 0; oz < overlappedTrgDataSize; oz++)
				{
					SingleColumnFullDataAccessor column = target.get(ox + offsetX, oz + offsetZ);
					column.downsampleFrom(source.subView(srcDataPerTrgData, ox * srcDataPerTrgData, oz * srcDataPerTrgData));
				}
			}
		}
		else
		{
			LodUtil.assertNotReach();
		}
	}
	
}
