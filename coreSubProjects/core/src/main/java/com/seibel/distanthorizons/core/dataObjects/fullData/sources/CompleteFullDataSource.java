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

package com.seibel.distanthorizons.core.dataObjects.fullData.sources;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.ChunkSizedFullDataAccessor;
import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.FullDataArrayAccessor;
import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.SingleColumnFullDataAccessor;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces.IFullDataSource;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces.IStreamableFullDataSource;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataMetaFile;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhLodPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Arrays;

/**
 * This data source contains every datapoint over its given {@link DhSectionPos}.
 *
 * @see FullDataPointUtil
 * @see LowDetailIncompleteFullDataSource
 * @see HighDetailIncompleteFullDataSource
 */
public class CompleteFullDataSource extends FullDataArrayAccessor implements IFullDataSource, IStreamableFullDataSource<IStreamableFullDataSource.FullDataSourceSummaryData, long[][]>
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static final byte SECTION_SIZE_OFFSET = DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
	/** measured in dataPoints */
	public static final int WIDTH = BitShiftUtil.powerOfTwo(SECTION_SIZE_OFFSET);
	
	public static final byte DATA_FORMAT_VERSION = 3;
	public static final String DATA_SOURCE_TYPE = "CompleteFullDataSource";
	
	private DhSectionPos sectionPos;
	private boolean isEmpty = true;
	public EDhApiWorldGenerationStep worldGenStep = EDhApiWorldGenerationStep.EMPTY;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public static CompleteFullDataSource createEmpty(DhSectionPos pos) { return new CompleteFullDataSource(pos); }
	private CompleteFullDataSource(DhSectionPos sectionPos)
	{
		super(new FullDataPointIdMap(sectionPos), new long[WIDTH * WIDTH][0], WIDTH);
		this.sectionPos = sectionPos;
	}
	
	public CompleteFullDataSource(DhSectionPos pos, FullDataPointIdMap mapping, long[][] data)
	{
		super(mapping, data, WIDTH);
		LodUtil.assertTrue(data.length == WIDTH * WIDTH);
		
		this.sectionPos = pos;
		this.isEmpty = false;
	}
	
	
	
	//=================//
	// stream handling //
	//=================//
	
	@Override
	public void writeSourceSummaryInfo(IDhLevel level, DhDataOutputStream outputStream) throws IOException
	{
		outputStream.writeInt(this.getDataDetailLevel());
		outputStream.writeInt(this.width);
		outputStream.writeInt(level.getMinY());
		outputStream.writeByte(this.worldGenStep.value);
		
	}
	@Override
	public FullDataSourceSummaryData readSourceSummaryInfo(FullDataMetaFile dataFile, DhDataInputStream inputStream, IDhLevel level) throws IOException
	{
		int dataDetail = inputStream.readInt();
		if (dataDetail != dataFile.baseMetaData.dataDetailLevel)
		{
			throw new IOException(LodUtil.formatLog("Data level mismatch: " + dataDetail + " != " + dataFile.baseMetaData.dataDetailLevel));
		}
		
		int width = inputStream.readInt();
		if (width != WIDTH)
		{
			throw new IOException(LodUtil.formatLog("Section width mismatch: " + width + " != " + WIDTH + " (Currently only 1 section width is supported)"));
		}
		
		int minY = inputStream.readInt();
		if (minY != level.getMinY())
		{
			LOGGER.warn("Data minY mismatch: " + minY + " != " + level.getMinY() + ". Will ignore data's y level");
		}
		
		byte worldGenByte = inputStream.readByte();
		EDhApiWorldGenerationStep worldGenStep = EDhApiWorldGenerationStep.fromValue(worldGenByte);
		if (worldGenStep == null)
		{
			worldGenStep = EDhApiWorldGenerationStep.SURFACE;
			LOGGER.warn("Missing WorldGenStep, defaulting to: " + worldGenStep.name());
		}
		
		
		return new FullDataSourceSummaryData(width, worldGenStep);
	}
	public void setSourceSummaryData(FullDataSourceSummaryData summaryData)
	{
		this.worldGenStep = summaryData.worldGenStep;
	}
	
	
	@Override
	public boolean writeDataPoints(DhDataOutputStream outputStream) throws IOException
	{
		if (this.isEmpty())
		{
			outputStream.writeInt(IFullDataSource.NO_DATA_FLAG_BYTE);
			return false;
		}
		outputStream.writeInt(IFullDataSource.DATA_GUARD_BYTE);
		
		
		
		// Data array length
		for (int x = 0; x < this.width; x++)
		{
			for (int z = 0; z < this.width; z++)
			{
				outputStream.writeInt(this.get(x, z).getSingleLength());
			}
		}
		
		
		
		// Data array content (only on non-empty columns)
		outputStream.writeInt(IFullDataSource.DATA_GUARD_BYTE);
		for (int x = 0; x < this.width; x++)
		{
			for (int z = 0; z < this.width; z++)
			{
				SingleColumnFullDataAccessor columnAccessor = this.get(x, z);
				if (columnAccessor.doesColumnExist())
				{
					long[] dataPointArray = columnAccessor.getRaw();
					for (long dataPoint : dataPointArray)
					{
						outputStream.writeLong(dataPoint);
					}
				}
			}
		}
		
		
		return true;
	}
	@Override
	public long[][] readDataPoints(FullDataMetaFile dataFile, int width, DhDataInputStream dataInputStream) throws IOException
	{
		// Data array length
		int dataPresentFlag = dataInputStream.readInt();
		if (dataPresentFlag == IFullDataSource.NO_DATA_FLAG_BYTE)
		{
			// Section is empty
			return null;
		}
		else if (dataPresentFlag != IFullDataSource.DATA_GUARD_BYTE)
		{
			throw new IOException("Invalid file format. Data Points guard byte expected: (no data) [" + IFullDataSource.NO_DATA_FLAG_BYTE + "] or (data present) [" + IFullDataSource.DATA_GUARD_BYTE + "], but found [" + dataPresentFlag + "].");
		}
		
		
		
		long[][] dataPointArrays;
		if (this.width == width) // attempt to use the existing dataArrays if possible
		{
			dataPointArrays = this.dataArrays;
		}
		else
		{
			dataPointArrays = new long[width * width][]; 
		}
		
		for (int x = 0; x < width; x++)
		{
			for (int z = 0; z < width; z++)
			{
				int requestedArrayLength = dataInputStream.readInt();
				int arrayIndex = x * width + z;
				
				// attempt to use the existing dataArrays if possible
				if (dataPointArrays[arrayIndex] == null || dataPointArrays[arrayIndex].length != requestedArrayLength)
				{
					dataPointArrays[arrayIndex] = new long[requestedArrayLength];
				}
				else
				{
					// clear the existing array to prevent any data leakage
					Arrays.fill(dataPointArrays[arrayIndex], 0);
				}
			}
		}
		
		
		
		// check if the array start flag is present
		int arrayStartFlag = dataInputStream.readInt();
		if (arrayStartFlag != IFullDataSource.DATA_GUARD_BYTE)
		{
			throw new IOException("invalid data length end guard");
		}
		
		for (int xz = 0; xz < dataPointArrays.length; xz++) // x and z are combined
		{
			if (dataPointArrays[xz].length != 0)
			{
				for (int y = 0; y < dataPointArrays[xz].length; y++)
				{
					dataPointArrays[xz][y] = dataInputStream.readLong();
				}
			}
		}
		
		
		
		return dataPointArrays;
	}
	@Override
	public void setDataPoints(long[][] dataPoints)
	{
		LodUtil.assertTrue(this.dataArrays.length == dataPoints.length, "Data point array length mismatch.");
		
		this.isEmpty = false;
		System.arraycopy(dataPoints, 0, this.dataArrays, 0, dataPoints.length);
	}
	
	
	@Override
	public void writeIdMappings(DhDataOutputStream outputStream) throws IOException
	{
		outputStream.writeInt(IFullDataSource.DATA_GUARD_BYTE);
		this.mapping.serialize(outputStream);
	}
	@Override
	public FullDataPointIdMap readIdMappings(long[][] dataPoints, DhDataInputStream inputStream, ILevelWrapper levelWrapper) throws IOException, InterruptedException
	{
		int guardByte = inputStream.readInt();
		if (guardByte != IFullDataSource.DATA_GUARD_BYTE)
		{
			throw new IOException("Invalid data content end guard for ID mapping");
		}
		
		return FullDataPointIdMap.deserialize(inputStream, this.sectionPos, levelWrapper);
	}
	@Override
	public void setIdMapping(FullDataPointIdMap mappings) { this.mapping.mergeAndReturnRemappedEntityIds(mappings); }
	
	
	
	//======//
	// data //
	//======//
	
	@Override
	public SingleColumnFullDataAccessor tryGet(int relativeX, int relativeZ) { return this.get(relativeX, relativeZ); }
	@Override
	public SingleColumnFullDataAccessor getOrCreate(int relativeX, int relativeZ) { return this.get(relativeX, relativeZ); }
	
	@Override
	public void update(ChunkSizedFullDataAccessor chunkDataView)
	{
		LodUtil.assertTrue(this.sectionPos.overlapsExactly(chunkDataView.getSectionPos()));
		if (this.getDataDetailLevel() == LodUtil.BLOCK_DETAIL_LEVEL)
		{
			DhBlockPos2D chunkBlockPos = new DhBlockPos2D(chunkDataView.chunkPos.x * LodUtil.CHUNK_WIDTH, chunkDataView.chunkPos.z * LodUtil.CHUNK_WIDTH);
			DhBlockPos2D blockOffset = chunkBlockPos.subtract(this.sectionPos.getMinCornerLodPos().getCornerBlockPos());
			LodUtil.assertTrue(blockOffset.x >= 0 && blockOffset.x < WIDTH && blockOffset.z >= 0 && blockOffset.z < WIDTH);
			this.isEmpty = false;
			
			chunkDataView.shadowCopyTo(this.subView(LodUtil.CHUNK_WIDTH, blockOffset.x, blockOffset.z));
			
			// DEBUG ASSERTION
			{
				for (int x = 0; x < LodUtil.CHUNK_WIDTH; x++)
				{
					for (int z = 0; z < LodUtil.CHUNK_WIDTH; z++)
					{
						SingleColumnFullDataAccessor column = this.get(x + blockOffset.x, z + blockOffset.z);
						LodUtil.assertTrue(column.doesColumnExist());
					}
				}
			}
		}
		else if (this.getDataDetailLevel() < LodUtil.CHUNK_DETAIL_LEVEL)
		{
			int dataPerFull = 1 << this.getDataDetailLevel();
			int fullSize = LodUtil.CHUNK_WIDTH / dataPerFull;
			DhLodPos dataOffset = chunkDataView.getSectionPos().getMinCornerLodPos(this.getDataDetailLevel());
			DhLodPos baseOffset = this.sectionPos.getMinCornerLodPos(this.getDataDetailLevel());
			
			int offsetX = dataOffset.x - baseOffset.x;
			int offsetZ = dataOffset.z - baseOffset.z;
			LodUtil.assertTrue(offsetX >= 0 && offsetX < WIDTH && offsetZ >= 0 && offsetZ < WIDTH);
			
			this.isEmpty = false;
			for (int xOffset = 0; xOffset < fullSize; xOffset++)
			{
				for (int zOffset = 0; zOffset < fullSize; zOffset++)
				{
					SingleColumnFullDataAccessor column = this.get(xOffset + offsetX, zOffset + offsetZ);
					column.downsampleFrom(chunkDataView.subView(dataPerFull, xOffset * dataPerFull, zOffset * dataPerFull));
				}
			}
		}
		else if (this.getDataDetailLevel() >= LodUtil.CHUNK_DETAIL_LEVEL)
		{
			//FIXME: TEMPORARY
			int chunkPerFull = 1 << (this.getDataDetailLevel() - LodUtil.CHUNK_DETAIL_LEVEL);
			if (chunkDataView.chunkPos.x % chunkPerFull != 0 || chunkDataView.chunkPos.z % chunkPerFull != 0)
			{
				return;
			}
			
			DhLodPos baseOffset = this.sectionPos.getMinCornerLodPos(this.getDataDetailLevel());
			DhSectionPos dataOffset = chunkDataView.getSectionPos().convertNewToDetailLevel(this.getDataDetailLevel());
			
			int offsetX = dataOffset.getX() - baseOffset.x;
			int offsetZ = dataOffset.getZ() - baseOffset.z;
			LodUtil.assertTrue(offsetX >= 0 && offsetX < WIDTH && offsetZ >= 0 && offsetZ < WIDTH);
			
			this.isEmpty = false;
			chunkDataView.get(0, 0).deepCopyTo(this.get(offsetX, offsetZ));
		}
		else
		{
			LodUtil.assertNotReach();
			//TODO
		}
		
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/** Returns whether data at the given posToWrite can effect the target region file at posToTest. */
	public static boolean firstDataPosCanAffectSecond(DhSectionPos posToWrite, DhSectionPos posToTest)
	{
		if (!posToWrite.overlapsExactly(posToTest))
		{
			// the testPosition is outside the writePosition
			return false;
		}
		else if (posToTest.getDetailLevel() > posToWrite.getDetailLevel())
		{
			// the testPosition is larger (aka is less detailed) than the writePosition,
			// more detailed sections shouldn't be updated by lower detail sections
			return false;
		}
		else if (posToWrite.getDetailLevel() - posToTest.getDetailLevel() <= SECTION_SIZE_OFFSET)
		{
			// if the difference in detail levels is very large, the posToWrite
			// may be skipped, due to how we sample large detail levels by only
			// getting the corners.
			
			// In this case the difference isn't very large, so return true
			return true;
		}
		else
		{
			// the difference in detail levels is very large,
			// check if the posToWrite is in a corner of posToTest
			byte sectPerData = (byte) BitShiftUtil.powerOfTwo(posToWrite.getDetailLevel() - posToTest.getDetailLevel() - SECTION_SIZE_OFFSET);
			LodUtil.assertTrue(sectPerData != 0);
			return posToTest.getX() % sectPerData == 0 && posToTest.getZ() % sectPerData == 0;
		}
	}
	
	
	
	//=====================//
	// setters and getters //
	//=====================//
	
	@Override
	public DhSectionPos getSectionPos() { return this.sectionPos; }
	
	@Override
	public void resizeDataStructuresForRepopulation(DhSectionPos pos)
	{
		// no data structures need to be changed, only the source's position 
		this.sectionPos = pos;
	}
	
	@Override
	public byte getDataDetailLevel() { return (byte) (this.sectionPos.getDetailLevel() - SECTION_SIZE_OFFSET); }
	
	@Override
	public byte getBinaryDataFormatVersion() { return DATA_FORMAT_VERSION; }
	
	@Override
	public EDhApiWorldGenerationStep getWorldGenStep() { return this.worldGenStep; }
	
	@Override
	public boolean isEmpty() { return this.isEmpty; }
	@Override
	public void markNotEmpty() { this.isEmpty = false; }
	
	@Override
	public int getWidthInDataPoints() { return this.width; }
	
	
	
	//========//
	// unused //
	//========//
	
	public void updateFromLowerCompleteSource(CompleteFullDataSource subData)
	{
		LodUtil.assertTrue(this.sectionPos.overlapsExactly(subData.sectionPos));
		LodUtil.assertTrue(subData.sectionPos.getDetailLevel() < this.sectionPos.getDetailLevel());
		if (!firstDataPosCanAffectSecond(this.sectionPos, subData.sectionPos))
		{
			return;
		}
		
		DhSectionPos lowerSectPos = subData.sectionPos;
		byte detailDiff = (byte) (this.sectionPos.getDetailLevel() - subData.sectionPos.getDetailLevel());
		byte targetDataDetail = this.getDataDetailLevel();
		DhLodPos minDataPos = this.sectionPos.getMinCornerLodPos(targetDataDetail);
		if (detailDiff <= SECTION_SIZE_OFFSET)
		{
			int count = 1 << detailDiff;
			int dataPerCount = WIDTH / count;
			DhLodPos subDataPos = lowerSectPos.getSectionBBoxPos().getCornerLodPos(targetDataDetail);
			int dataOffsetX = subDataPos.x - minDataPos.x;
			int dataOffsetZ = subDataPos.z - minDataPos.z;
			LodUtil.assertTrue(dataOffsetX >= 0 && dataOffsetX < WIDTH && dataOffsetZ >= 0 && dataOffsetZ < WIDTH);
			
			for (int xOffset = 0; xOffset < count; xOffset++)
			{
				for (int zOffset = 0; zOffset < count; zOffset++)
				{
					SingleColumnFullDataAccessor column = this.get(xOffset + dataOffsetX, zOffset + dataOffsetZ);
					column.downsampleFrom(subData.subView(dataPerCount, xOffset * dataPerCount, zOffset * dataPerCount));
				}
			}
		}
		else
		{
			// Count == 1
			DhLodPos subDataPos = lowerSectPos.getSectionBBoxPos().convertToDetailLevel(targetDataDetail);
			int dataOffsetX = subDataPos.x - minDataPos.x;
			int dataOffsetZ = subDataPos.z - minDataPos.z;
			LodUtil.assertTrue(dataOffsetX >= 0 && dataOffsetX < WIDTH && dataOffsetZ >= 0 && dataOffsetZ < WIDTH);
			subData.get(0, 0).deepCopyTo(get(dataOffsetX, dataOffsetZ));
		}
	}
	
}
