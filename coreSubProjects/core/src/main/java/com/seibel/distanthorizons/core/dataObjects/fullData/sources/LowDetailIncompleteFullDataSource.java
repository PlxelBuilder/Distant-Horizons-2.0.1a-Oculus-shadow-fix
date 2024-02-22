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
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces.IIncompleteFullDataSource;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces.IStreamableFullDataSource;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataMetaFile;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
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
import java.util.BitSet;

/**
 * Used for large incomplete LOD blocks. <Br>
 * Handles incomplete full data with a detail level higher than
 * {@link HighDetailIncompleteFullDataSource#MAX_SECTION_DETAIL}. <br><br>
 *
 * Formerly "SpottyFullDataSource".
 *
 * @see HighDetailIncompleteFullDataSource
 * @see CompleteFullDataSource
 * @see FullDataPointUtil
 */
public class LowDetailIncompleteFullDataSource extends FullDataArrayAccessor implements IIncompleteFullDataSource, IStreamableFullDataSource<IStreamableFullDataSource.FullDataSourceSummaryData, LowDetailIncompleteFullDataSource.StreamDataPointContainer>
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static final byte SECTION_SIZE_OFFSET = DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
	/** measured in dataPoints */
	public static final int WIDTH = BitShiftUtil.powerOfTwo(SECTION_SIZE_OFFSET);
	
	public static final byte DATA_FORMAT_VERSION = 3;
	public static final String DATA_SOURCE_TYPE = "LowDetailIncompleteFullDataSource";
	
	
	private DhSectionPos sectionPos;
	private final BitSet isColumnNotEmpty;
	
	private boolean isEmpty = true;
	public EDhApiWorldGenerationStep worldGenStep = EDhApiWorldGenerationStep.EMPTY;
	private boolean isPromoted = false;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public static LowDetailIncompleteFullDataSource createEmpty(DhSectionPos pos) { return new LowDetailIncompleteFullDataSource(pos); }
	private LowDetailIncompleteFullDataSource(DhSectionPos sectionPos)
	{
		super(new FullDataPointIdMap(sectionPos), new long[WIDTH * WIDTH][0], WIDTH);
		LodUtil.assertTrue(sectionPos.getDetailLevel() > HighDetailIncompleteFullDataSource.MAX_SECTION_DETAIL);
		
		this.sectionPos = sectionPos;
		this.isColumnNotEmpty = new BitSet(WIDTH * WIDTH);
		this.worldGenStep = EDhApiWorldGenerationStep.EMPTY;
	}
	
	private LowDetailIncompleteFullDataSource(DhSectionPos pos, FullDataPointIdMap mapping, EDhApiWorldGenerationStep worldGenStep, BitSet isColumnNotEmpty, long[][] data)
	{
		super(mapping, data, WIDTH);
		LodUtil.assertTrue(data.length == WIDTH * WIDTH);
		
		this.sectionPos = pos;
		this.isColumnNotEmpty = isColumnNotEmpty;
		this.worldGenStep = worldGenStep;
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
		int dataDetailLevel = inputStream.readInt();
		if (dataDetailLevel != dataFile.baseMetaData.dataDetailLevel)
		{
			throw new IOException(LodUtil.formatLog("Data level mismatch: " + dataDetailLevel + " != " + dataFile.baseMetaData.dataDetailLevel));
		}
		
		int width = inputStream.readInt();
		if (width != WIDTH)
		{
			throw new IOException(LodUtil.formatLog("Section size mismatch: " + width + " != " + WIDTH + " (Currently only 1 section size is supported)"));
		}
		
		int minY = inputStream.readInt();
		if (minY != level.getMinY())
		{
			LOGGER.warn("Data minY mismatch: " + minY + " != " + level.getMinY() + ". Will ignore data's y level");
		}
		
		EDhApiWorldGenerationStep worldGenStep = EDhApiWorldGenerationStep.fromValue(inputStream.readByte());
		if (worldGenStep == null)
		{
			worldGenStep = EDhApiWorldGenerationStep.SURFACE;
			LOGGER.warn("Missing WorldGenStep, defaulting to: " + worldGenStep.name());
		}
		
		
		return new FullDataSourceSummaryData(this.width, worldGenStep);
	}
	public void setSourceSummaryData(FullDataSourceSummaryData summaryData)
	{
		this.worldGenStep = summaryData.worldGenStep;
	}
	
	
	@Override
	public boolean writeDataPoints(DhDataOutputStream dataOutputStream) throws IOException
	{
		if (this.isEmpty)
		{
			dataOutputStream.writeInt(IFullDataSource.NO_DATA_FLAG_BYTE);
			return false;
		}
		dataOutputStream.writeInt(IFullDataSource.DATA_GUARD_BYTE);
		
		
		// data column presence
		byte[] bytes = this.isColumnNotEmpty.toByteArray();
		dataOutputStream.writeInt(bytes.length);
		dataOutputStream.write(bytes);
		
		
		// Data content
		dataOutputStream.writeInt(IFullDataSource.DATA_GUARD_BYTE);
		for (int i = this.isColumnNotEmpty.nextSetBit(0); i >= 0; i = this.isColumnNotEmpty.nextSetBit(i + 1))
		{
			dataOutputStream.writeByte(this.dataArrays[i].length);
			for (long dataPoint : this.dataArrays[i])
			{
				dataOutputStream.writeLong(dataPoint);
			}
		}
		
		
		return true;
	}
	@Override
	public StreamDataPointContainer readDataPoints(FullDataMetaFile dataFile, int width, DhDataInputStream inputStream) throws IOException
	{
		// is source empty flag
		int dataPresentFlag = inputStream.readInt();
		if (dataPresentFlag == IFullDataSource.NO_DATA_FLAG_BYTE)
		{
			// Section is empty
			return null;
		}
		else if (dataPresentFlag != IFullDataSource.DATA_GUARD_BYTE)
		{
			throw new IOException("Invalid file format. Data Points guard byte expected: (no data) [" + IFullDataSource.NO_DATA_FLAG_BYTE + "] or (data present) [" + IFullDataSource.DATA_GUARD_BYTE + "], but found [" + dataPresentFlag + "].");
		}
		
		
		// data column presence
		int length = inputStream.readInt();
		if (length < 0 || length > (WIDTH * WIDTH / 8 + 64) * 2) // TODO replace magic numbers or comment what they mean
		{
			throw new IOException(LodUtil.formatLog("Spotty Flag BitSet size outside reasonable range: {} (expects {} to {})",
					length, 1, WIDTH * WIDTH / 8 + 63));
		}
		
		byte[] bytes = new byte[length];
		inputStream.readFully(bytes, 0, length);
		BitSet isColumnNotEmpty = BitSet.valueOf(bytes);
		
		
		
		// Data array content
		long[][] dataPointArray = new long[WIDTH * WIDTH][];
		dataPresentFlag = inputStream.readInt();
		if (dataPresentFlag != IFullDataSource.DATA_GUARD_BYTE)
		{
			throw new IOException("invalid spotty flag end guard");
		}
		
		for (int xz = isColumnNotEmpty.nextSetBit(0); xz >= 0; xz = isColumnNotEmpty.nextSetBit(xz + 1))
		{
			long[] array = new long[inputStream.readByte()];
			for (int y = 0; y < array.length; y++)
			{
				array[y] = inputStream.readLong();
			}
			dataPointArray[xz] = array;
		}
		
		
		return new StreamDataPointContainer(dataPointArray, isColumnNotEmpty);
	}
	@Override
	public void setDataPoints(StreamDataPointContainer streamDataPointContainer)
	{
		long[][] dataPoints = streamDataPointContainer.dataPoints;
		
		// copy over the datapoints
		LodUtil.assertTrue(this.dataArrays.length == dataPoints.length, "Data point array length mismatch.");
		System.arraycopy(dataPoints, 0, this.dataArrays, 0, dataPoints.length);
		
		// overwrite the bitset
		for (int i = 0; i < streamDataPointContainer.isColumnNotEmpty.length(); i++)
		{
			this.isColumnNotEmpty.set(i, streamDataPointContainer.isColumnNotEmpty.get(i));
		}
		
		this.isEmpty = false;
	}
	
	
	@Override
	public void writeIdMappings(DhDataOutputStream outputStream) throws IOException
	{
		outputStream.writeInt(IFullDataSource.DATA_GUARD_BYTE);
		this.mapping.serialize(outputStream);
		
	}
	@Override
	public FullDataPointIdMap readIdMappings(StreamDataPointContainer streamDataPointContainer, DhDataInputStream inputStream, ILevelWrapper levelWrapper) throws IOException, InterruptedException
	{
		// Id mapping
		int dataPresentFlag = inputStream.readInt();
		if (dataPresentFlag != IFullDataSource.DATA_GUARD_BYTE)
		{
			throw new IOException("invalid ID mapping end guard");
		}
		return FullDataPointIdMap.deserialize(inputStream, this.sectionPos, levelWrapper);
	}
	@Override
	public void setIdMapping(FullDataPointIdMap mappings) { this.mapping.mergeAndReturnRemappedEntityIds(mappings); }
	
	
	
	//======//
	// data //
	//======//
	
	@Override
	public SingleColumnFullDataAccessor tryGet(int relativeX, int relativeZ) { return this.tryGetOrCreate(relativeX, relativeZ, false); }
	@Override
	public SingleColumnFullDataAccessor getOrCreate(int relativeX, int relativeZ) { return this.tryGetOrCreate(relativeX, relativeZ, true); }
	private SingleColumnFullDataAccessor tryGetOrCreate(int relativeX, int relativeZ, boolean createIfMissing)
	{
		int notEmptyIndex = relativeX * WIDTH + relativeZ;
		boolean columnEmpty = this.isColumnNotEmpty.get(notEmptyIndex);
		
		// "create" the missing column if necessary
		if (columnEmpty && createIfMissing)
		{
			this.isColumnNotEmpty.set(notEmptyIndex, true);
			columnEmpty = false;
		}
		
		return !columnEmpty ? this.get(relativeX, relativeZ) : null;
	}
	
	
	
	//=====================//
	// getters and setters //
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
	public int getWidthInDataPoints() { return WIDTH; }
	
	
	
	//===============//
	// Data updating //
	//===============//
	
	@Override
	public void update(ChunkSizedFullDataAccessor data)
	{
		LodUtil.assertTrue(this.sectionPos.overlapsExactly(data.getSectionPos()));
		
		if (this.getDataDetailLevel() >= 4)
		{
			//FIXME: TEMPORARY
			int chunkPerFull = 1 << (this.getDataDetailLevel() - 4);
			if (data.chunkPos.x % chunkPerFull != 0 || data.chunkPos.z % chunkPerFull != 0)
			{
				return;
			}
			
			DhLodPos baseOffset = this.sectionPos.getMinCornerLodPos(this.getDataDetailLevel());
			DhSectionPos dataOffset = data.getSectionPos().convertNewToDetailLevel(this.getDataDetailLevel());
			int offsetX = dataOffset.getX() - baseOffset.x;
			int offsetZ = dataOffset.getZ() - baseOffset.z;
			LodUtil.assertTrue(offsetX >= 0 && offsetX < WIDTH && offsetZ >= 0 && offsetZ < WIDTH);
			this.isEmpty = false;
			
			SingleColumnFullDataAccessor columnFullDataAccessor = this.get(offsetX, offsetZ);
			data.get(0, 0).deepCopyTo(columnFullDataAccessor);
			
			this.isColumnNotEmpty.set(offsetX * WIDTH + offsetZ, columnFullDataAccessor.doesColumnExist());
		}
		else
		{
			LodUtil.assertNotReach();
			//TODO;
		}
		
	}
	
	@Override
	public IFullDataSource tryPromotingToCompleteDataSource()
	{
		// promotion can only be completed if every column has data
		if (this.isEmpty)
		{
			return this;
		}
		else if (this.isColumnNotEmpty.cardinality() != WIDTH * WIDTH)
		{
			return this;
		}
		this.isPromoted = true;
		return new CompleteFullDataSource(this.sectionPos, this.mapping, this.dataArrays);
	}
	
	@Override
	public boolean hasBeenPromoted() { return this.isPromoted; }
	
	
	
	//================//
	// helper classes //
	//================//
	
	/** used when reading the datapoints to and from the {@link IStreamableFullDataSource} */
	public static class StreamDataPointContainer
	{
		public long[][] dataPoints;
		public BitSet isColumnNotEmpty;
		
		public StreamDataPointContainer(long[][] dataPoints, BitSet isColumnNotEmpty)
		{
			this.dataPoints = dataPoints;
			this.isColumnNotEmpty = isColumnNotEmpty;
		}
		
	}
	
	
	
	//========//
	// unused //
	//========//
	
	public static boolean neededForPosition(DhSectionPos posToWrite, DhSectionPos posToTest)
	{
		if (!posToWrite.overlapsExactly(posToTest))
			return false;
		if (posToTest.getDetailLevel() > posToWrite.getDetailLevel())
			return false;
		if (posToWrite.getDetailLevel() - posToTest.getDetailLevel() <= SECTION_SIZE_OFFSET)
			return true;
		byte sectPerData = (byte) (1 << (posToWrite.getDetailLevel() - posToTest.getDetailLevel() - SECTION_SIZE_OFFSET));
		return posToTest.getX() % sectPerData == 0 && posToTest.getZ() % sectPerData == 0;
	}
	
	
}
