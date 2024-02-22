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

import com.seibel.distanthorizons.api.enums.rendering.EDebugRendering;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.ChunkSizedFullDataAccessor;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.file.fullDatafile.IFullDataSourceProvider;
import com.seibel.distanthorizons.core.file.renderfile.RenderSourceFileHandler;
import com.seibel.distanthorizons.core.file.structure.AbstractSaveStructure;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.pos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.CompleteFullDataSource;
import com.seibel.distanthorizons.core.render.LodQuadTree;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.math.Mat4f;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class ClientLevelModule implements Closeable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private final IDhClientLevel parentClientLevel;
	public final AtomicReference<ClientRenderState> ClientRenderStateRef = new AtomicReference<>();
	public final F3Screen.NestedMessage f3Message;
	public ClientLevelModule(IDhClientLevel parentClientLevel)
	{
		this.parentClientLevel = parentClientLevel;
		this.f3Message = new F3Screen.NestedMessage(this::f3Log);
	}
	
	//==============//
	// tick methods //
	//==============//
	
	private EDebugRendering lastDebugRendering = EDebugRendering.OFF;
	
	public void clientTick()
	{
		// can be false if the level is unloading
		if (!MC_CLIENT.playerExists())
		{
			return;
		}
		
		ClientRenderState clientRenderState = this.ClientRenderStateRef.get();
		if (clientRenderState == null)
		{
			return;
		}
		// TODO this should probably be handled via a config change listener
		// recreate the RenderState if the render distance changes
		if (clientRenderState.quadtree.blockRenderDistanceDiameter != Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get() * LodUtil.CHUNK_WIDTH * 2)
		{
			if (!this.ClientRenderStateRef.compareAndSet(clientRenderState, null))
			{
				return;
			}
			
			IClientLevelWrapper clientLevelWrapper = this.parentClientLevel.getClientLevelWrapper();
			if (clientLevelWrapper == null)
			{
				return;
			}
			
			clientRenderState.close();
			clientRenderState = new ClientRenderState(this.parentClientLevel, clientLevelWrapper, this.parentClientLevel.getFileHandler(), this.parentClientLevel.getSaveStructure());
			if (!this.ClientRenderStateRef.compareAndSet(null, clientRenderState))
			{
				//FIXME: How to handle this?
				LOGGER.warn("Failed to set render state due to concurrency after changing view distance");
				clientRenderState.close();
				return;
			}
		}
		clientRenderState.quadtree.tick(new DhBlockPos2D(MC_CLIENT.getPlayerBlockPos()));
		
		boolean isBuffersDirty = false;
		EDebugRendering newDebugRendering = Config.Client.Advanced.Debugging.debugRendering.get();
		if (newDebugRendering != lastDebugRendering)
		{
			lastDebugRendering = newDebugRendering;
			isBuffersDirty = true;
		}
		if (isBuffersDirty)
		{
			clientRenderState.renderer.bufferHandler.MarkAllBuffersDirty();
		}
	}
	
	
	//========//
	// render //
	//========//
	
	/** @return if the {@link ClientRenderState} was successfully swapped */
	public boolean startRenderer(IClientLevelWrapper clientLevelWrapper)
	{
		ClientRenderState ClientRenderState = new ClientRenderState(parentClientLevel, clientLevelWrapper, parentClientLevel.getFileHandler(), parentClientLevel.getSaveStructure());
		if (!this.ClientRenderStateRef.compareAndSet(null, ClientRenderState))
		{
			LOGGER.warn("Failed to start renderer due to concurrency");
			ClientRenderState.close();
			return false;
		}
		else
		{
			return true;
		}
	}
	
	public boolean isRendering()
	{
		return this.ClientRenderStateRef.get() != null;
	}
	
	public void render(Mat4f mcModelViewMatrix, Mat4f mcProjectionMatrix, float partialTicks, IProfilerWrapper profiler)
	{
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState == null)
		{
			// either the renderer hasn't been started yet, or is being reloaded
			return;
		}
		ClientRenderState.renderer.drawLODs(ClientRenderState.clientLevelWrapper, mcModelViewMatrix, mcProjectionMatrix, partialTicks, profiler);
	}
	
	public void stopRenderer()
	{
		LOGGER.info("Stopping renderer for " + this);
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState == null)
		{
			LOGGER.warn("Tried to stop renderer for " + this + " when it was not started!");
			return;
		}
		// stop the render state
		while (!this.ClientRenderStateRef.compareAndSet(ClientRenderState, null)) // TODO why is there a while loop here?
		{
			ClientRenderState = this.ClientRenderStateRef.get();
			if (ClientRenderState == null)
			{
				return;
			}
		}
		ClientRenderState.close();
	}
	
	//===============//
	// data handling //
	//===============//
	public void writeChunkDataToFile(ChunkSizedFullDataAccessor data)
	{
		DhSectionPos pos = data.getSectionPos().convertNewToDetailLevel(CompleteFullDataSource.SECTION_SIZE_OFFSET);
		
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState != null)
		{
			ClientRenderState.renderSourceFileHandler.writeChunkDataToFile(pos, data);
		}
		else
		{
			this.parentClientLevel.getFileHandler().writeChunkDataToFile(pos, data);
		}
	}
	
	public CompletableFuture<Void> saveAsync()
	{
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState != null)
		{
			return ClientRenderState.renderSourceFileHandler.flushAndSaveAsync();
		}
		else
		{
			return CompletableFuture.completedFuture(null);
		}
	}
	
	public void close()
	{
		// shutdown the renderer
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState != null)
		{
			// TODO does this have to be in a while loop, if so why?
			while (!this.ClientRenderStateRef.compareAndSet(ClientRenderState, null))
			{
				ClientRenderState = this.ClientRenderStateRef.get();
				if (ClientRenderState == null)
				{
					break;
				}
			}
			
			if (ClientRenderState != null)
			{
				ClientRenderState.close();
			}
		}
		
		this.f3Message.close();
	}
	
	
	
	
	//=======================//
	// misc helper functions //
	//=======================//
	
	public void dumpRamUsage()
	{
		//TODO
	}
	
	/** Returns what should be displayed in Minecraft's F3 debug menu */
	protected String[] f3Log()
	{
		String dimName = parentClientLevel.getClientLevelWrapper().getDimensionType().getDimensionName();
		ClientRenderState renderState = this.ClientRenderStateRef.get();
		if (renderState == null)
		{
			return new String[]{"level @ " + dimName + ": Inactive"};
		}
		else
		{
			return new String[]{"level @ " + dimName + ": Active"};
		}
	}
	
	public void clearRenderCache()
	{
		ClientRenderState ClientRenderState = this.ClientRenderStateRef.get();
		if (ClientRenderState != null && ClientRenderState.quadtree != null)
		{
			ClientRenderState.quadtree.clearRenderDataCache();
		}
	}
	
	public void reloadPos(DhSectionPos pos)
	{
		ClientRenderState clientRenderState = this.ClientRenderStateRef.get();
		if (clientRenderState != null && clientRenderState.quadtree != null)
		{
			clientRenderState.quadtree.reloadPos(pos);
		}
	}
	
	public static class ClientRenderState
	{
		private static final Logger LOGGER = DhLoggerBuilder.getLogger();
		
		public final IClientLevelWrapper clientLevelWrapper;
		public final LodQuadTree quadtree;
		public final RenderSourceFileHandler renderSourceFileHandler;
		public final LodRenderer renderer;
		
		public ClientRenderState(
				IDhClientLevel dhClientLevel, IClientLevelWrapper clientLevelWrapper, IFullDataSourceProvider fullDataSourceProvider,
				AbstractSaveStructure saveStructure)
		{
			this.clientLevelWrapper = clientLevelWrapper;
			this.renderSourceFileHandler = new RenderSourceFileHandler(fullDataSourceProvider, dhClientLevel, saveStructure);
			
			this.quadtree = new LodQuadTree(dhClientLevel, Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius.get() * LodUtil.CHUNK_WIDTH * 2,
					// initial position is (0,0) just in case the player hasn't loaded in yet, the tree will be moved once the level starts ticking
					0, 0,
					this.renderSourceFileHandler);
			
			RenderBufferHandler renderBufferHandler = new RenderBufferHandler(this.quadtree);
			this.renderer = new LodRenderer(renderBufferHandler);
		}
		
		
		
		public void close()
		{
			LOGGER.info("Shutting down " + ClientRenderState.class.getSimpleName());
			
			this.renderer.close();
			this.quadtree.close();
			this.renderSourceFileHandler.close();
		}
		
	}
	
}
