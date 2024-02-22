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

package com.seibel.distanthorizons.core.file.renderfile;

import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.ChunkSizedFullDataAccessor;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.sql.RenderDataRepo;

import java.util.concurrent.CompletableFuture;

/**
 * This represents LOD data that is stored in long term storage (IE LOD files stored on the hard drive) <br>
 * Example: {@link RenderSourceFileHandler RenderSourceFileHandler} <br><br>
 *
 * This is used to create {@link ColumnRenderSource}'s
 */
public interface IRenderSourceProvider extends AutoCloseable
{
	CompletableFuture<ColumnRenderSource> readAsync(DhSectionPos pos);
	
	void writeChunkDataToFile(DhSectionPos sectionPos, ChunkSizedFullDataAccessor chunkData);
	CompletableFuture<Void> flushAndSaveAsync();
	
	/** Deletes any data stored in the render cache so it can be re-created */
	void deleteRenderCache();
	
	
	RenderDataRepo getRepo();
	
}
