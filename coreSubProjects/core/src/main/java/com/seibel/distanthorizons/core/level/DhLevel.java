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

package com.seibel.distanthorizons.core.level;

import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkModifiedEvent;
import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.ChunkSizedFullDataAccessor;
import com.seibel.distanthorizons.core.dataObjects.transformers.ChunkToLodBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.coreapi.DependencyInjection.ApiEventInjector;

import java.util.concurrent.CompletableFuture;

public abstract class DhLevel implements IDhLevel
{
	
	public final ChunkToLodBuilder chunkToLodBuilder;
	
	protected DhLevel() { this.chunkToLodBuilder = new ChunkToLodBuilder(); }
	
	public abstract void saveWrites(ChunkSizedFullDataAccessor data);
	
	
	@Override
	public int getMinY()
	{
		return 0;
	}
	
	@Override
	public void updateChunkAsync(IChunkWrapper chunk)
	{
		CompletableFuture<ChunkSizedFullDataAccessor> future = this.chunkToLodBuilder.tryGenerateData(chunk);
		if (future != null)
		{
			future.thenAccept((chunkSizedFullDataAccessor) ->
			{
				if (chunkSizedFullDataAccessor == null)
				{
					// This can happen if, among other reasons, a chunk save is superceded by a later event
					return;
				}
				
				this.saveWrites(chunkSizedFullDataAccessor);
				ApiEventInjector.INSTANCE.fireAllEvents(
						DhApiChunkModifiedEvent.class,
						new DhApiChunkModifiedEvent.EventParam(this.getLevelWrapper(), chunk.getChunkPos().x, chunk.getChunkPos().z));
			});
		}
	}
	
	@Override
	public void close() { this.chunkToLodBuilder.close(); }
	
}
