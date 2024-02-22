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

package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnRenderBufferBuilder;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.file.renderfile.IRenderSourceProvider;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.renderer.IDebugRenderable;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.Reference;
import com.seibel.distanthorizons.core.util.objects.quadTree.QuadTree;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.ColumnRenderBuffer;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A render section represents an area that could be rendered.
 * For more information see {@link LodQuadTree}.
 */
public class LodRenderSection implements IDebugRenderable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	public final DhSectionPos pos;
	
	private boolean isRenderingEnabled = false;
	/**
	 * If this is true, then {@link LodRenderSection#reload(IRenderSourceProvider)} was called while
	 * a {@link IRenderSourceProvider} was already being loaded.
	 */
	private boolean reloadRenderSourceOnceLoaded = false;
	
	private IRenderSourceProvider renderSourceProvider = null;
	private CompletableFuture<ColumnRenderSource> renderSourceLoadFuture;
	private ColumnRenderSource renderSource;
	
	private IDhClientLevel level = null;
	
	//FIXME: Temp Hack to prevent swapping buffers too quickly
	private long lastNs = -1;
	private long lastSwapLocalVersion = -1;
	private boolean neighborUpdated = false;
	/** 2 sec */
	private static final long SWAP_TIMEOUT_IN_NS = 2_000000000L;
	/** 1 sec */
	private static final long SWAP_BUSY_COLLISION_TIMEOUT_IN_NS = 1_000000000L;
	
	private CompletableFuture<ColumnRenderBuffer> buildRenderBufferFuture = null;
	private final Reference<ColumnRenderBuffer> inactiveRenderBufferRef = new Reference<>();
	
	/** a reference is used so the render buffer can be swapped to and from the buffer builder */
	public final AtomicReference<ColumnRenderBuffer> activeRenderBufferRef = new AtomicReference<>();
	private volatile boolean disposeActiveBuffer = false;
	
	private final QuadTree<LodRenderSection> parentQuadTree;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public LodRenderSection(QuadTree<LodRenderSection> parentQuadTree, DhSectionPos pos)
	{
		this.pos = pos;
		this.parentQuadTree = parentQuadTree;
		
		DebugRenderer.register(this, Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus);
	}
	
	
	
	//===========//
	// rendering //
	//===========//
	
	public void enableRendering() { this.isRenderingEnabled = true; }
	public void disableRendering() { this.isRenderingEnabled = false; }
	
	
	
	//=============//
	// render data //
	//=============//
	
	/** does nothing if a render source is already loaded or in the process of loading */
	public void loadRenderSource(IRenderSourceProvider renderDataProvider, IDhClientLevel level)
	{
		this.renderSourceProvider = renderDataProvider;
		this.level = level;
		if (this.renderSourceProvider == null)
		{
			LOGGER.warn("LodRenderSection [" + this.pos + "] called loadRenderSource with a empty source provider");
			return;
		}
		// don't re-load or double load the render source
		if (this.renderSource != null || this.renderSourceLoadFuture != null)
		{
			// since the render source has been loaded, make sure the render buffers are populated
			// FIXME this is a duck tape solution, since the renderBufferRef should be populated elsewhere, but this does fix empty LODs when moving around the world
			if (this.activeRenderBufferRef.get() == null)
			{
				this.markBufferDirty(); // empty LOD fix #3, all solutions revolve around markBufferDirty()
			}
			
			return;
		}
		
		this.startLoadRenderSourceAsync();
	}
	
	public void reload(IRenderSourceProvider renderDataProvider)
	{
		// debug rendering
		boolean showRenderSectionStatus = Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus.get();
		if (showRenderSectionStatus && this.pos.getDetailLevel() == DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL)
		{
			DebugRenderer.makeParticle(
					new DebugRenderer.BoxParticle(
							new DebugRenderer.Box(this.pos, 0, 256f, 0.03f, Color.cyan),
							0.5, 512f
					)
			);
		}
		
		
		this.renderSourceProvider = renderDataProvider;
		if (this.renderSourceProvider == null)
		{
			LOGGER.warn("LodRenderSection [" + this.pos + "] called reload with a empty source provider");
			return;
		}
		
		// don't accidentally enable rendering for a disabled section
		if (!this.isRenderingEnabled)
		{
			return;
		}
		// wait for the current load future to finish before re-loading
		if (this.renderSourceLoadFuture != null)
		{
			this.reloadRenderSourceOnceLoaded = true;
			return;
		}
		
		this.startLoadRenderSourceAsync();
	}
	
	private void startLoadRenderSourceAsync()
	{
		this.renderSourceLoadFuture = this.renderSourceProvider.readAsync(this.pos);
		this.renderSourceLoadFuture.whenComplete((renderSource, ex) ->
		{
			this.renderSource = renderSource;
			this.lastNs = -1;
			this.markBufferDirty();
			if (this.reloadRenderSourceOnceLoaded)
			{
				this.reloadRenderSourceOnceLoaded = false;
				this.reload(this.renderSourceProvider);
			}
			
			this.renderSourceLoadFuture = null;
		});
	}
	
	
	
	//========================//
	// getters and properties //
	//========================//
	
	/** This can return true before the render data is loaded */
	public boolean isRenderingEnabled() { return this.isRenderingEnabled; }
	
	public ColumnRenderSource getRenderSource() { return this.renderSource; }
	
	public boolean canRenderNow()
	{
		if (this.renderSourceLoadFuture != null || this.buildRenderBufferFuture != null)
		{
			// wait for loading to finish
			return false;
		}
		
		return this.renderSource != null
				&&
				(
					(
						// if true; either this section represents empty chunks or un-generated chunks. 
						// Either way, there isn't any data to render, but this should be considered "loaded"
						this.renderSource.isEmpty()
					)
					||
					(
						// check if the buffers have been loaded
						this.activeRenderBufferRef.get() != null // in the case of missing sections, this is probably null
						&& this.lastSwapLocalVersion != -1
					)
				);
	}
	
	public void markBufferDirty() { this.lastSwapLocalVersion = -1; }
	
	
	
	//=================//
	// buffer building //
	//=================//
	
	private LodRenderSection[] getNeighbors()
	{
		LodRenderSection[] adjacentRenderSections = new LodRenderSection[EDhDirection.ADJ_DIRECTIONS.length];
		for (EDhDirection direction : EDhDirection.ADJ_DIRECTIONS)
		{
			try
			{
				DhSectionPos adjPos = this.pos.getAdjacentPos(direction);
				LodRenderSection adjRenderSection = this.parentQuadTree.getValue(adjPos);
				// adjacent render sources might be null
				adjacentRenderSections[direction.ordinal() - 2] = adjRenderSection;
			}
			catch (IndexOutOfBoundsException e)
			{
				// adjacent positions can be out of bounds, in that case a null render source will be used
			}
		}
		
		return adjacentRenderSections;
	}
	
	private void tellNeighborsUpdated()
	{
		LodRenderSection[] adjacentRenderSections = this.getNeighbors();
		for (LodRenderSection adj : adjacentRenderSections)
		{
			if (adj != null)
			{
				adj.neighborUpdated = true;
			}
		}
	}
	
	/** @return true if this section is loaded and set to render */
	public boolean canBuildBuffer() { return this.renderSourceLoadFuture == null && this.renderSource != null && this.buildRenderBufferFuture == null && !this.renderSource.isEmpty() && this.isBufferOutdated(); }
	private boolean isBufferOutdated() { return this.neighborUpdated || this.renderSource.localVersion.get() != this.lastSwapLocalVersion; }
	
	/** @return true if this section is loaded and set to render */
	public boolean canSwapBuffer() { return this.buildRenderBufferFuture != null && this.buildRenderBufferFuture.isDone(); }
	
	
	public synchronized void disposeRenderData() // synchronized is a band-aid solution to prevent a rare bug where the future isn't canceled in the right order
	{
		if (this.buildRenderBufferFuture != null)
		{
			//LOGGER.info("Cancelling build of render buffer for {}", sectionPos);
			this.buildRenderBufferFuture.cancel(true);
			this.buildRenderBufferFuture = null;
		}
		this.disposeActiveBuffer = true;
		
		this.renderSource = null;
		if (this.renderSourceLoadFuture != null)
		{
			this.renderSourceLoadFuture.cancel(true);
			this.renderSourceLoadFuture = null;
		}
	}
	
	
	/**
	 * Try and swap in new render buffer for this section. Note that before this call, there should be no other
	 * places storing or referencing the render buffer.
	 *
	 * @return True if the swap was successful. False if swap is not needed or if it is in progress.
	 */
	public boolean tryBuildAndSwapBuffer()
	{
		// delete the existing buffer if it should be disposed
		if (this.disposeActiveBuffer && this.activeRenderBufferRef.get() != null)
		{
			this.disposeActiveBuffer = false;
			this.activeRenderBufferRef.getAndSet(null).close();
			return false;
		}
		
		
		// attempt to build the buffer
		boolean didSwapped = false;
		if (this.canBuildBuffer())
		{
			// debug
			boolean showRenderSectionStatus = Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus.get();
			if (showRenderSectionStatus && this.pos.getDetailLevel() == DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL)
			{
				DebugRenderer.makeParticle(
					new DebugRenderer.BoxParticle(
						new DebugRenderer.Box(this.pos, 32f, 64f, 0.2f, Color.yellow),
						0.5, 16f
					)
				);
			}
			
			
			this.neighborUpdated = false;
			long newVersion = this.renderSource.localVersion.get();
			if (this.lastSwapLocalVersion != newVersion)
			{
				this.lastSwapLocalVersion = newVersion;
				this.tellNeighborsUpdated();
			}
			
			
			LodRenderSection[] adjacentRenderSections = this.getNeighbors();
			ColumnRenderSource[] adjacentSources = new ColumnRenderSource[EDhDirection.ADJ_DIRECTIONS.length];
			for (int i = 0; i < EDhDirection.ADJ_DIRECTIONS.length; i++)
			{
				LodRenderSection adj = adjacentRenderSections[i];
				if (adj != null)
				{
					adjacentSources[i] = adj.getRenderSource();
				}
			}
			
			this.buildRenderBufferFuture = ColumnRenderBufferBuilder.buildBuffersAsync(this.level, this.inactiveRenderBufferRef, this.renderSource, adjacentSources);
		}
		
		
		// attempt to swap in the buffer
		if (this.canSwapBuffer())
		{
			this.lastNs = System.nanoTime();
			ColumnRenderBuffer newBuffer;
			try
			{
				newBuffer = this.buildRenderBufferFuture.getNow(null);
				if (newBuffer == null)
				{
					// failed.
					this.markBufferDirty();
					return false;
				}
				
				LodUtil.assertTrue(newBuffer.buffersUploaded, "The buffer future for " + this.pos + " returned an un-built buffer.");
				ColumnRenderBuffer oldBuffer = this.activeRenderBufferRef.getAndSet(newBuffer);
				if (oldBuffer != null)
				{
					// the old buffer is now considered unloaded, it will need to be freshly re-loaded
					oldBuffer.buffersUploaded = false;
					oldBuffer.close();
				}
				ColumnRenderBuffer swapped = this.inactiveRenderBufferRef.swap(oldBuffer);
				didSwapped = true;
				LodUtil.assertTrue(swapped == null);
			}
			catch (CancellationException e1)
			{
				// ignore.
				this.buildRenderBufferFuture = null;
			}
			catch (CompletionException e)
			{
				LOGGER.error("Unable to get render buffer for " + pos + ".", e);
				this.buildRenderBufferFuture = null;
			}
			finally
			{
				this.buildRenderBufferFuture = null;
			}
		}
		
		return didSwapped;
	}
	
	
	
	//==============//
	// base methods //
	//==============//
	
	public String toString()
	{
		return "LodRenderSection{" +
				"pos=" + this.pos +
				", lodRenderSource=" + this.renderSource +
				", loadFuture=" + this.renderSourceLoadFuture +
				", isRenderEnabled=" + this.isRenderingEnabled +
				'}';
	}
	
	public void dispose()
	{
		this.disposeRenderData();
		DebugRenderer.unregister(this, Config.Client.Advanced.Debugging.DebugWireframe.showRenderSectionStatus);
		if (this.activeRenderBufferRef.get() != null)
		{
			this.activeRenderBufferRef.get().close();
		}
		if (this.inactiveRenderBufferRef.value != null)
		{
			this.inactiveRenderBufferRef.value.close();
		}
	}
	
	@Override
	public void debugRender(DebugRenderer debugRenderer)
	{
		Color color = Color.red;
		if (this.renderSourceProvider == null)
		{
			color = Color.black;
		}
		else if (this.renderSourceLoadFuture != null)
		{
			color = Color.yellow;
		}
		else if (this.renderSource != null)
		{
			color = Color.blue;
			if (this.buildRenderBufferFuture != null)
			{
				color = Color.magenta;
			}
			else if (this.canRenderNow())
			{
				color = Color.cyan;
			}
			else if (this.canRenderNow() && this.isRenderingEnabled)
			{
				color = Color.green;
			}
		}
		
		debugRenderer.renderBox(new DebugRenderer.Box(this.pos, 400, 8f, Objects.hashCode(this), 0.1f, color));
	}
	
}
