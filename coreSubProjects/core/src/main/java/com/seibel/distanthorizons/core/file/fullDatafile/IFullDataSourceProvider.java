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

package com.seibel.distanthorizons.core.file.fullDatafile;

import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.ChunkSizedFullDataAccessor;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces.IFullDataSource;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.FullDataRepo;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public interface IFullDataSourceProvider extends AutoCloseable
{
	CompletableFuture<IFullDataSource> readAsync(DhSectionPos pos);
	void writeChunkDataToFile(DhSectionPos sectionPos, ChunkSizedFullDataAccessor chunkData);
	CompletableFuture<Void> flushAndSaveAsync();
	CompletableFuture<Void> flushAndSaveAsync(DhSectionPos sectionPos);
	
	//long getCacheVersion(DhSectionPos sectionPos);
	//boolean isCacheVersionValid(DhSectionPos sectionPos, long cacheVersion);
	
	CompletableFuture<IFullDataSource> onDataFileCreatedAsync(FullDataMetaFile file);
	default CompletableFuture<DataFileUpdateResult> onDataFileUpdateAsync(IFullDataSource fullDataSource, FullDataMetaFile file, boolean dataChanged) { return CompletableFuture.completedFuture(new DataFileUpdateResult(fullDataSource, dataChanged)); }
	/** Can be used to update world gen queues or run any other data checking necessary when initially loading a file */
	default void onRenderDataFileLoaded(DhSectionPos pos) {  }
	
	@Nullable
    FullDataMetaFile getFileIfExist(DhSectionPos pos);
	
	FullDataRepo getRepo();
	
	
	
	//================//
	// helper classes //
	//================//
	
	/** 
	 * After a {@link FullDataMetaFile} has been updated the {@link IFullDataSourceProvider} may also need to modify it. <br>
	 * This specifically happens during world generation. 
	 */
	class DataFileUpdateResult
	{
		IFullDataSource fullDataSource;
		boolean dataSourceChanged;
		
		public DataFileUpdateResult(IFullDataSource fullDataSource, boolean dataSourceChanged)
		{
			this.fullDataSource = fullDataSource;
			this.dataSourceChanged = dataSourceChanged;
		}
	}
	
}
