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

package com.seibel.distanthorizons.core.dataObjects.render;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.ChunkSizedFullDataAccessor;
import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.SingleColumnFullDataAccessor;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhLodPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnQuadView;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.IColumnDataView;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores the render data used to generate OpenGL buffers.
 *
 * @see    RenderDataPointUtil
 */
public class ColumnRenderSource
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static final boolean DO_SAFETY_CHECKS = ModInfo.IS_DEV_BUILD;
	public static final byte SECTION_SIZE_OFFSET = DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL;
	public static final int SECTION_SIZE = BitShiftUtil.powerOfTwo(SECTION_SIZE_OFFSET);
	
	public static final byte DATA_FORMAT_VERSION = 1;
	public static final String DATA_NAME = "ColumnRenderSource";
	
	/**
	 * This is the byte put between different sections in the binary save file.
	 * The presence and absence of this byte indicates if the file is correctly formatted.
	 */
	public static final int DATA_GUARD_BYTE = 0xFFFFFFFF;
	/** indicates the binary save file represents an empty data source */
	public static final int NO_DATA_FLAG_BYTE = 0x00000001;
	
	
	
	public int verticalDataCount;
	public final DhSectionPos sectionPos;
	public final int yOffset;
	
	public long[] renderDataContainer;
	
	public final DebugSourceFlag[] debugSourceFlags;
	
	private boolean isEmpty = true;
	public EDhApiWorldGenerationStep worldGenStep;
	
	public AtomicLong localVersion = new AtomicLong(0); // used to track changes to the data source, so that buffers can be updated when necessary
	
	//==============//
	// constructors //
	//==============//
	
	public static ColumnRenderSource createEmptyRenderSource(DhSectionPos sectionPos) { return new ColumnRenderSource(sectionPos, 0, 0); }
	/**
	 * Creates an empty ColumnRenderSource.
	 *
	 * @param sectionPos the relative position of the container
	 * @param maxVerticalSize the maximum vertical size of the container
	 */
	public ColumnRenderSource(DhSectionPos sectionPos, int maxVerticalSize, int yOffset)
	{
		this.verticalDataCount = maxVerticalSize;
		this.renderDataContainer = new long[SECTION_SIZE * SECTION_SIZE * this.verticalDataCount];
		this.debugSourceFlags = new DebugSourceFlag[SECTION_SIZE * SECTION_SIZE];
		this.sectionPos = sectionPos;
		this.yOffset = yOffset;
		this.worldGenStep = EDhApiWorldGenerationStep.EMPTY;
	}
	
	/**
	 * Creates a new ColumnRenderSource from the parsedColumnData.
	 *
	 * @throws IOException if the DataInputStream's detail level isn't what was expected
	 */
	public ColumnRenderSource(DhSectionPos sectionPos, ColumnRenderLoader.ParsedColumnData parsedColumnData, IDhLevel level) throws IOException
	{
		if (sectionPos.getDetailLevel() - SECTION_SIZE_OFFSET != parsedColumnData.detailLevel)
		{
			throw new IOException("Invalid data: detail level does not match");
		}
		
		this.sectionPos = sectionPos;
		this.yOffset = level.getMinY();
		this.verticalDataCount = parsedColumnData.verticalSize;
		this.renderDataContainer = parsedColumnData.dataContainer;
		this.worldGenStep = parsedColumnData.worldGenStep;
		this.isEmpty = parsedColumnData.isEmpty;
		
		this.debugSourceFlags = new DebugSourceFlag[SECTION_SIZE * SECTION_SIZE];
		this.fillDebugFlag(0, 0, SECTION_SIZE, SECTION_SIZE, DebugSourceFlag.FILE);
	}
	
	
	
	//========================//
	// datapoint manipulation //
	//========================//
	
	public void clearDataPoint(int posX, int posZ)
	{
		for (int verticalIndex = 0; verticalIndex < this.verticalDataCount; verticalIndex++)
		{
			this.renderDataContainer[posX * SECTION_SIZE * this.verticalDataCount + posZ * this.verticalDataCount + verticalIndex] = RenderDataPointUtil.EMPTY_DATA;
		}
	}
	
	public boolean setDataPoint(long data, int posX, int posZ, int verticalIndex)
	{
		this.renderDataContainer[posX * SECTION_SIZE * this.verticalDataCount + posZ * this.verticalDataCount + verticalIndex] = data;
		return true;
	}
	
	public boolean copyVerticalData(IColumnDataView newData, int posX, int posZ, boolean overwriteDataWithSameGenerationMode)
	{
		if (DO_SAFETY_CHECKS)
		{
			if (newData.size() != this.verticalDataCount)
				throw new IllegalArgumentException("newData size not the same as this column's vertical size");
			if (posX < 0 || posX >= SECTION_SIZE)
				throw new IllegalArgumentException("X position is out of bounds");
			if (posZ < 0 || posZ >= SECTION_SIZE)
				throw new IllegalArgumentException("Z position is out of bounds");
		}
		
		int dataOffset = posX * SECTION_SIZE * this.verticalDataCount + posZ * this.verticalDataCount;
		int compare = RenderDataPointUtil.compareDatapointPriority(newData.get(0), this.renderDataContainer[dataOffset]);
		if (overwriteDataWithSameGenerationMode)
		{
			if (compare < 0)
			{
				return false;
			}
		}
		else
		{
			if (compare <= 0)
			{
				return false;
			}
		}
		
		// copy the newData into this column's data
		newData.copyTo(this.renderDataContainer, dataOffset, newData.size());
		return true;
	}
	
	
	public long getFirstDataPoint(int posX, int posZ) { return getDataPoint(posX, posZ, 0); }
	public long getDataPoint(int posX, int posZ, int verticalIndex) { return this.renderDataContainer[posX * SECTION_SIZE * this.verticalDataCount + posZ * this.verticalDataCount + verticalIndex]; }
	
	public long[] getVerticalDataPointArray(int posX, int posZ)
	{
		long[] result = new long[this.verticalDataCount];
		int index = posX * SECTION_SIZE * this.verticalDataCount + posZ * this.verticalDataCount;
		System.arraycopy(this.renderDataContainer, index, result, 0, this.verticalDataCount);
		return result;
	}
	
	public ColumnArrayView getVerticalDataPointView(int posX, int posZ)
	{
		return new ColumnArrayView(this.renderDataContainer, this.verticalDataCount,
				posX * SECTION_SIZE * this.verticalDataCount + posZ * this.verticalDataCount,
				this.verticalDataCount);
	}
	
	public ColumnQuadView getFullQuadView() { return this.getQuadViewOverRange(0, 0, SECTION_SIZE, SECTION_SIZE); }
	public ColumnQuadView getQuadViewOverRange(int quadX, int quadZ, int quadXSize, int quadZSize) { return new ColumnQuadView(this.renderDataContainer, SECTION_SIZE, this.verticalDataCount, quadX, quadZ, quadXSize, quadZSize); }
	
	public int getVerticalSize() { return this.verticalDataCount; }
	
	
	
	//========================//
	// data update and output //
	//========================//
	
	public void writeData(DhDataOutputStream outputStream) throws IOException
	{
		outputStream.flush();
		
		outputStream.writeByte(this.getDataDetailLevel());
		outputStream.writeInt(this.verticalDataCount);
		
		if (this.isEmpty)
		{
			// no data is present
			outputStream.writeByte(NO_DATA_FLAG_BYTE);
		}
		else
		{
			// data is present
			outputStream.writeByte(DATA_GUARD_BYTE);
			outputStream.writeInt(this.yOffset);
			
			// write the data for each column
			for (int xz = 0; xz < SECTION_SIZE * SECTION_SIZE; xz++)
			{
				for (int y = 0; y < this.verticalDataCount; y++)
				{
					long currentDatapoint = this.renderDataContainer[xz * this.verticalDataCount + y];
					outputStream.writeLong(Long.reverseBytes(currentDatapoint)); // the reverse bytes is necessary to ensure the data is read in correctly
				}
			}
		}
		
		outputStream.writeByte(DATA_GUARD_BYTE);
		outputStream.writeByte(this.worldGenStep.value);
		
		outputStream.flush();
	}
	
	/** Overrides any data that has not been written directly using write(). Skips empty source dataPoints. */
	public void updateFromRenderSource(ColumnRenderSource renderSource)
	{
		// validate we are writing for the same location
		LodUtil.assertTrue(renderSource.sectionPos.equals(this.sectionPos));
		
		// change the vertical size if necessary (this can happen if the vertical quality was changed in the config) 
		this.clearAndChangeVerticalSize(renderSource.verticalDataCount);
		// validate both objects have the same number of dataPoints
		LodUtil.assertTrue(renderSource.verticalDataCount == this.verticalDataCount);
		
		
		if (renderSource.isEmpty)
		{
			// the source is empty, don't attempt to update anything
			return;
		}
		// the source isn't empty, this object won't be empty after the method finishes
		this.isEmpty = false;
		
		for (int i = 0; i < this.renderDataContainer.length; i += this.verticalDataCount)
		{
			int thisGenMode = RenderDataPointUtil.getGenerationMode(this.renderDataContainer[i]);
			int srcGenMode = RenderDataPointUtil.getGenerationMode(renderSource.renderDataContainer[i]);
			
			if (srcGenMode == 0)
			{
				// the source hasn't been generated, don't write it
				continue;
			}
			
			// this object's column is older than the source's column, update it
			if (thisGenMode <= srcGenMode)
			{
				ColumnArrayView thisColumnArrayView = new ColumnArrayView(this.renderDataContainer, this.verticalDataCount, i, this.verticalDataCount);
				ColumnArrayView srcColumnArrayView = new ColumnArrayView(renderSource.renderDataContainer, renderSource.verticalDataCount, i, renderSource.verticalDataCount);
				thisColumnArrayView.copyFrom(srcColumnArrayView);
				
				this.debugSourceFlags[i / this.verticalDataCount] = renderSource.debugSourceFlags[i / this.verticalDataCount];
			}
		}
		localVersion.incrementAndGet();
	}
	/**
	 * If the newVerticalSize is different than the current verticalSize,
	 * this will delete any data currently in this object and re-size it. <Br>
	 * Otherwise this method will do nothing.
	 */
	private void clearAndChangeVerticalSize(int newVerticalSize)
	{
		if (newVerticalSize != this.verticalDataCount)
		{
			this.verticalDataCount = newVerticalSize;
			this.renderDataContainer = new long[SECTION_SIZE * SECTION_SIZE * this.verticalDataCount];
			this.localVersion.incrementAndGet();
		}
	}
	
	/** 
	 * Doesn't write anything to file.
	 * @return true if any data was changed, false otherwise 
	 */
	public boolean updateWithChunkData(ChunkSizedFullDataAccessor chunkDataView, IDhClientLevel level)
	{
		final String errorMessagePrefix = "Unable to complete fastWrite for RenderSource pos: [" + this.sectionPos + "] and chunk pos: [" + chunkDataView.chunkPos + "]. Error:";
		
		final DhSectionPos renderSourcePos = this.getSectionPos();
		
		final int sourceBlockX = renderSourcePos.getMinCornerLodPos().getCornerBlockPos().x;
		final int sourceBlockZ = renderSourcePos.getMinCornerLodPos().getCornerBlockPos().z;
		
		// offset between the incoming chunk data and this render source
		final int blockOffsetX = (chunkDataView.chunkPos.x * LodUtil.CHUNK_WIDTH) - sourceBlockX;
		final int blockOffsetZ = (chunkDataView.chunkPos.z * LodUtil.CHUNK_WIDTH) - sourceBlockZ;
		
		final int sourceDataPointBlockWidth = BitShiftUtil.powerOfTwo(this.getDataDetailLevel());
		
		boolean dataChanged = false;
		
		if (chunkDataView.detailLevel == this.getDataDetailLevel())
		{
			this.markNotEmpty();
			// confirm the render source contains this chunk
			if (blockOffsetX < 0
					|| blockOffsetX + LodUtil.CHUNK_WIDTH > this.getWidthInDataPoints()
					|| blockOffsetZ < 0
					|| blockOffsetZ + LodUtil.CHUNK_WIDTH > this.getWidthInDataPoints())
			{
				LOGGER.warn(errorMessagePrefix+"Data offset is out of bounds.");
				return false;
			}
			
			
			if (Thread.interrupted())
			{
				LOGGER.warn(errorMessagePrefix+"write interrupted.");
				return false;
			}
			
			
			for (int x = 0; x < LodUtil.CHUNK_WIDTH; x++)
			{
				for (int z = 0; z < LodUtil.CHUNK_WIDTH; z++)
				{
					ColumnArrayView columnArrayView = this.getVerticalDataPointView(blockOffsetX + x, blockOffsetZ + z);
					int hash = columnArrayView.getDataHash();
					SingleColumnFullDataAccessor fullArrayView = chunkDataView.get(x, z);
					FullDataToRenderDataTransformer.convertColumnData(level,
							sourceBlockX + sourceDataPointBlockWidth * (blockOffsetX + x),
							sourceBlockZ + sourceDataPointBlockWidth * (blockOffsetZ + z),
							columnArrayView, fullArrayView, 2);
					dataChanged |= hash != columnArrayView.getDataHash();
				}
			}
			this.fillDebugFlag(blockOffsetX, blockOffsetZ, LodUtil.CHUNK_WIDTH, LodUtil.CHUNK_WIDTH, ColumnRenderSource.DebugSourceFlag.DIRECT);
		}
		else if (chunkDataView.detailLevel < this.getDataDetailLevel() && this.getDataDetailLevel() <= chunkDataView.getSectionPos().getDetailLevel())
		{
			this.markNotEmpty();
			// multiple chunk data points converting to 1 column data point
			DhLodPos dataCornerPos = chunkDataView.getSectionPos().getMinCornerLodPos(chunkDataView.detailLevel);
			DhLodPos sourceCornerPos = renderSourcePos.getMinCornerLodPos(this.getDataDetailLevel());
			DhLodPos sourceStartingChangePos = dataCornerPos.convertToDetailLevel(this.getDataDetailLevel());
			int relStartX = Math.floorMod(sourceStartingChangePos.x, this.getWidthInDataPoints());
			int relStartZ = Math.floorMod(sourceStartingChangePos.z, this.getWidthInDataPoints());
			int dataToSourceScale = sourceCornerPos.getWidthAtDetail(chunkDataView.detailLevel);
			int columnsInChunk = chunkDataView.getSectionPos().getWidthCountForLowerDetailedSection(this.getDataDetailLevel());
			
			for (int xOffset = 0; xOffset < columnsInChunk; xOffset++)
			{
				for (int zOffset = 0; zOffset < columnsInChunk; zOffset++)
				{
					int relSourceX = relStartX + xOffset;
					int relSourceZ = relStartZ + zOffset;
					ColumnArrayView columnArrayView = this.getVerticalDataPointView(relSourceX, relSourceZ);
					int hash = columnArrayView.getDataHash();
					SingleColumnFullDataAccessor fullArrayView = chunkDataView.get(xOffset * dataToSourceScale, zOffset * dataToSourceScale);
					FullDataToRenderDataTransformer.convertColumnData(level,
							sourceBlockX + sourceDataPointBlockWidth * relSourceX,
							sourceBlockZ + sourceDataPointBlockWidth * relSourceZ,
							columnArrayView, fullArrayView, 2);
					dataChanged |= hash != columnArrayView.getDataHash();
				}
			}
			this.fillDebugFlag(relStartX, relStartZ, columnsInChunk, columnsInChunk, ColumnRenderSource.DebugSourceFlag.DIRECT);
		}
		else if (chunkDataView.getSectionPos().getDetailLevel() < this.getDataDetailLevel())
		{
			// The entire chunk is being converted to a single column data point, possibly.
			DhLodPos dataCornerPos = chunkDataView.getSectionPos().getMinCornerLodPos(chunkDataView.detailLevel);
			DhLodPos sourceCornerPos = renderSourcePos.getMinCornerLodPos(this.getDataDetailLevel());
			DhLodPos sourceStartingChangePos = dataCornerPos.convertToDetailLevel(this.getDataDetailLevel());
			int chunksPerColumn = sourceStartingChangePos.getWidthAtDetail(chunkDataView.getSectionPos().getDetailLevel());
			if (chunkDataView.getSectionPos().getX() % chunksPerColumn != 0 || chunkDataView.getSectionPos().getZ() % chunksPerColumn != 0)
			{
				return false; // not a multiple of the column size, so no change
			}
			int relStartX = Math.floorMod(sourceStartingChangePos.x, this.getWidthInDataPoints());
			int relStartZ = Math.floorMod(sourceStartingChangePos.z, this.getWidthInDataPoints());
			ColumnArrayView columnArrayView = this.getVerticalDataPointView(relStartX, relStartZ);
			int hash = columnArrayView.getDataHash();
			SingleColumnFullDataAccessor fullArrayView = chunkDataView.get(0, 0);
			FullDataToRenderDataTransformer.convertColumnData(level, dataCornerPos.x * sourceDataPointBlockWidth,
					dataCornerPos.z * sourceDataPointBlockWidth,
					columnArrayView, fullArrayView, 2);
			dataChanged = hash != columnArrayView.getDataHash();
			this.fillDebugFlag(relStartX, relStartZ, 1, 1, ColumnRenderSource.DebugSourceFlag.DIRECT);
		}
		
		
		if (dataChanged)
		{
			this.localVersion.incrementAndGet();
		}
		
		return dataChanged;
	}
	
	
	
	//=====================//
	// data helper methods //
	//=====================//
	
	public boolean doesDataPointExist(int posX, int posZ) { return RenderDataPointUtil.doesDataPointExist(this.getFirstDataPoint(posX, posZ)); }
	
	public void generateData(ColumnRenderSource lowerDataContainer, int posX, int posZ)
	{
		ColumnArrayView outputView = this.getVerticalDataPointView(posX, posZ);
		ColumnQuadView quadView = lowerDataContainer.getQuadViewOverRange(posX * 2, posZ * 2, 2, 2);
		outputView.mergeMultiDataFrom(quadView);
	}
	
	public int getMaxLodCount() { return SECTION_SIZE * SECTION_SIZE * this.getVerticalSize(); }
	
	public long getRoughRamUsageInBytes() { return (long) this.renderDataContainer.length * Long.BYTES; }
	
	public DhSectionPos getSectionPos() { return this.sectionPos; }
	
	public byte getDataDetailLevel() { return (byte) (this.sectionPos.getDetailLevel() - SECTION_SIZE_OFFSET); }
	
	/** @return how many data points wide this {@link ColumnRenderSource} is. */
	public int getWidthInDataPoints() { return BitShiftUtil.powerOfTwo(this.getDetailOffset()); }
	public byte getDetailOffset() { return SECTION_SIZE_OFFSET; }
	
	public byte getRenderDataFormatVersion() { return DATA_FORMAT_VERSION; }
	
	/**
	 * Whether this object is still valid. If not, a new one should be created.
	 * TODO this will be necessary for dedicated multiplayer support, if the server has newer data this section should no longer be valid
	 */
	public boolean isValid() { return true; }
	
	public boolean isEmpty() { return this.isEmpty; }
	public void markNotEmpty() { this.isEmpty = false; }
	
	/** can be used when debugging */
	public boolean hasNonVoidDataPoints()
	{
		if (this.isEmpty)
		{
			return false;
		}
		
		
		for (int x = 0; x < SECTION_SIZE; x++)
		{
			for (int z = 0; z < SECTION_SIZE; z++)
			{
				ColumnArrayView columnArrayView = this.getVerticalDataPointView(x,z);
				for (int i = 0; i < columnArrayView.size; i++)
				{
					long dataPoint = columnArrayView.get(i);
					if (!RenderDataPointUtil.isVoid(dataPoint))
					{
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
	
	
	//=======//
	// debug //
	//=======//
	
	/** Sets the debug flag for the given area */
	public void fillDebugFlag(int xStart, int zStart, int xWidth, int zWidth, DebugSourceFlag flag)
	{
		for (int x = xStart; x < xStart + xWidth; x++)
		{
			for (int z = zStart; z < zStart + zWidth; z++)
			{
				this.debugSourceFlags[x * SECTION_SIZE + z] = flag;
			}
		}
		localVersion.incrementAndGet();
	}
	
	public DebugSourceFlag debugGetFlag(int ox, int oz) { return this.debugSourceFlags[ox * SECTION_SIZE + oz]; }
	
	
	
	//==============//
	// base methods //
	//==============//
	
	@Override
	public String toString()
	{
		String LINE_DELIMITER = "\n";
		String DATA_DELIMITER = " ";
		String SUBDATA_DELIMITER = ",";
		StringBuilder stringBuilder = new StringBuilder();
		
		stringBuilder.append(this.sectionPos);
		stringBuilder.append(LINE_DELIMITER);
		
		int size = 1;
		for (int z = 0; z < size; z++)
		{
			for (int x = 0; x < size; x++)
			{
				for (int y = 0; y < this.verticalDataCount; y++)
				{
					//Converting the dataToHex
					stringBuilder.append(Long.toHexString(this.getDataPoint(x, z, y)));
					if (y != this.verticalDataCount - 1)
						stringBuilder.append(SUBDATA_DELIMITER);
				}
				
				if (x != size - 1)
					stringBuilder.append(DATA_DELIMITER);
			}
			
			if (z != size - 1)
				stringBuilder.append(LINE_DELIMITER);
		}
		return stringBuilder.toString();
	}
	
	
	
	//==============//
	// helper enums //
	//==============//
	
	public enum DebugSourceFlag
	{
		FULL(ColorUtil.BLUE),
		DIRECT(ColorUtil.WHITE),
		SPARSE(ColorUtil.YELLOW),
		FILE(ColorUtil.BROWN);
		
		public final int color;
		
		DebugSourceFlag(int color) { this.color = color; }
	}
	
}
