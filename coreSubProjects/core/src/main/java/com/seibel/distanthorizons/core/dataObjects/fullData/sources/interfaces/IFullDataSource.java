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

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.dataObjects.fullData.FullDataPointIdMap;
import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.ChunkSizedFullDataAccessor;
import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.IFullDataAccessor;
import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.SingleColumnFullDataAccessor;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataMetaFile;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Base for all Full Data Source objects. <br><br>
 *
 * Contains full DH data, methods related to file/stream reading/writing, and the data necessary to create {@link ColumnRenderSource}'s. <br>
 * {@link IFullDataSource}'s will either implement or contain {@link IFullDataAccessor}'s.
 *
 * @see IFullDataAccessor
 * @see IIncompleteFullDataSource
 * @see IStreamableFullDataSource
 */
public interface IFullDataSource
{
	/**
	 * This is the byte put between different sections in the binary save file.
	 * The presence and absence of this byte indicates if the file is correctly formatted.
	 */
	int DATA_GUARD_BYTE = 0xFFFFFFFF;
	/** indicates the binary save file represents an empty data source */
	int NO_DATA_FLAG_BYTE = 0x00000001;
	
	
	
	DhSectionPos getSectionPos();
	
	/** Returns the detail level of the data contained by this {@link IFullDataSource}. */
	byte getDataDetailLevel();
	byte getBinaryDataFormatVersion();
	EDhApiWorldGenerationStep getWorldGenStep();
	
	void update(ChunkSizedFullDataAccessor data);
	
	boolean isEmpty();
	void markNotEmpty();
	
	/** AKA; the max relative position that {@link IFullDataSource#tryGet(int, int)} can accept for either X or Z */
	int getWidthInDataPoints();
	
	
	
	//======//
	// data //
	//======//
	
	/**
	 * Attempts to get the data column for the given relative x and z position.
	 *
	 * @return null if the data doesn't exist
	 */
	@Nullable
	SingleColumnFullDataAccessor tryGet(int relativeX, int relativeZ);
	/**
	 * Attempts to get the data column for the given relative x and z position. <br>
	 * If no data exists yet an empty data column will be created.
	 */
	SingleColumnFullDataAccessor getOrCreate(int relativeX, int relativeZ);
	
	FullDataPointIdMap getMapping();
	
	
	
	//=======================//
	// basic stream handling // 
	//=======================//
	
	// TODO make this blow up in IStreamableFullDataSource instead of the children
	/**
	 * Should only be implemented by {@link IStreamableFullDataSource} to prevent potential stream read/write inconsistencies.
	 *
	 * @see IStreamableFullDataSource#writeToStream(DhDataOutputStream, IDhLevel)
	 */
	void writeToStream(DhDataOutputStream outputStream, IDhLevel level) throws IOException;
	
	/**
	 * Should only be implemented by {@link IStreamableFullDataSource} to prevent potential stream read/write inconsistencies.
	 *
	 * @see IStreamableFullDataSource#populateFromStream(FullDataMetaFile, DhDataInputStream, IDhLevel)
	 */
	void populateFromStream(FullDataMetaFile dataFile, DhDataInputStream inputStream, IDhLevel level) throws IOException, InterruptedException;
	
	/**
	 * Should only be implemented by {@link IStreamableFullDataSource} to prevent potential stream read/write inconsistencies.
	 *
	 * @see IStreamableFullDataSource#repopulateFromStream(FullDataMetaFile, DhDataInputStream, IDhLevel) 
	 */
	void repopulateFromStream(FullDataMetaFile dataFile, DhDataInputStream inputStream, IDhLevel level) throws IOException, InterruptedException;
	
}
