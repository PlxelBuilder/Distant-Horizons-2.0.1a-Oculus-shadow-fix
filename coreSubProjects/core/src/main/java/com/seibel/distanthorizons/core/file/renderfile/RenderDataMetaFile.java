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

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.ChunkSizedFullDataAccessor;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces.IFullDataSource;
import com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer;
import com.seibel.distanthorizons.core.file.DataSourceReferenceTracker;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataMetaFile;
import com.seibel.distanthorizons.core.file.fullDatafile.IFullDataSourceProvider;
import com.seibel.distanthorizons.core.file.metaData.AbstractMetaDataContainerFile;
import com.seibel.distanthorizons.core.file.metaData.BaseMetaData;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderLoader;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.render.renderer.IDebugRenderable;
import com.seibel.distanthorizons.core.sql.MetaDataDto;
import com.seibel.distanthorizons.core.util.AtomicsUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.Reference;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.*;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Represents a File that contains a {@link ColumnRenderSource}. */
public class RenderDataMetaFile extends AbstractMetaDataContainerFile implements IDebugRenderable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public static final boolean ALWAYS_INVALIDATE_CACHE = false;
	public static final String RENDER_SOURCE_TYPE = ColumnRenderSource.DATA_NAME;
	
	
	/**
	 * Can be cleared if the garbage collector determines there isn't enough space. <br><br>
	 *
	 * When clearing, don't set to null, instead create a SoftReference containing null.
	 * This makes null checks simpler.
	 */
	private DataSourceReferenceTracker.RenderDataSourceSoftRef cachedRenderDataSourceRef = new DataSourceReferenceTracker.RenderDataSourceSoftRef(this, null);
	private final AtomicReference<CompletableFuture<ColumnRenderSource>> renderSourceLoadFutureRef = new AtomicReference<>(null);
	
	private final IDhClientLevel clientLevel;
	private final IFullDataSourceProvider fullDataSourceProvider;
	private final IRenderSourceProvider renderDataSourceProvider;
	private boolean doesDtoExist;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	/**
	 * NOTE: should only be used if there is NOT an existing file.
	 * @throws IOException if a file already exists for this position
	 */
	public static RenderDataMetaFile createNewFileForPos(IFullDataSourceProvider fullDataSourceProvider, IRenderSourceProvider renderDataSourceProvider, IDhClientLevel clientLevel, DhSectionPos pos) throws IOException { return new RenderDataMetaFile(fullDataSourceProvider, renderDataSourceProvider, clientLevel, pos); }
	private RenderDataMetaFile(IFullDataSourceProvider fullDataSourceProvider, IRenderSourceProvider renderDataSourceProvider, IDhClientLevel clientLevel, DhSectionPos pos) throws IOException
	{
		super(pos);
		this.fullDataSourceProvider = fullDataSourceProvider;
		this.renderDataSourceProvider = renderDataSourceProvider;
		this.clientLevel = clientLevel;
		LodUtil.assertTrue(this.baseMetaData == null);
		this.doesDtoExist = false;
		DebugRenderer.register(this, Config.Client.Advanced.Debugging.DebugWireframe.showRenderDataFileStatus);
	}
	
	
	/**
	 * NOTE: should only be used if there IS an existing file.
	 * @throws IOException if no file exists for this position
	 */
	public static RenderDataMetaFile createFromExistingFile(IFullDataSourceProvider fullDataSourceProvider, IRenderSourceProvider renderDataSourceProvider, IDhClientLevel clientLevel, MetaDataDto metaDataDto) throws IOException { return new RenderDataMetaFile(fullDataSourceProvider, renderDataSourceProvider, clientLevel, metaDataDto); }
	private RenderDataMetaFile(IFullDataSourceProvider fullDataSourceProvider, IRenderSourceProvider renderDataSourceProvider, IDhClientLevel clientLevel, MetaDataDto metaDataDto) throws IOException
	{
		super(metaDataDto.baseMetaData);
		this.fullDataSourceProvider = fullDataSourceProvider;
		this.renderDataSourceProvider = renderDataSourceProvider;
		this.clientLevel = clientLevel;
		LodUtil.assertTrue(this.baseMetaData != null);
		this.doesDtoExist = true;
		DebugRenderer.register(this, Config.Client.Advanced.Debugging.DebugWireframe.showRenderDataFileStatus);
		
		// handles world gen queuing for missing columns
		this.fullDataSourceProvider.onRenderDataFileLoaded(this.baseMetaData.pos);
	}
	
	
	
	//=============//
	// data update //
	//=============//
	
	public void updateChunkIfSourceExistsAsync(ChunkSizedFullDataAccessor chunkDataView)
	{
		DhSectionPos chunkSectionPos = chunkDataView.getSectionPos();
		LodUtil.assertTrue(this.pos.overlapsExactly(chunkSectionPos), "Chunk pos " + chunkSectionPos + " doesn't overlap with section " + this.pos);
		
		// update the render source if one exists
		CompletableFuture<ColumnRenderSource> renderSourceLoadFuture = this.getCachedDataSourceAsync(true);
		if (renderSourceLoadFuture == null)
		{
			return;
		}
		
		
		renderSourceLoadFuture.thenAccept((renderSource) ->
		{
			boolean dataUpdated = renderSource.updateWithChunkData(chunkDataView, this.clientLevel);
			
			
			// add a debug particle
			boolean showRenderDataFileStatus = Config.Client.Advanced.Debugging.DebugWireframe.showRenderDataFileStatus.get();
			if (showRenderDataFileStatus)
			{
				float offset = new Random(System.nanoTime() ^ Thread.currentThread().getId()).nextFloat() * 16f;
				Color debugColor = dataUpdated ? Color.blue : Color.red;
				DebugRenderer.makeParticle(
						new DebugRenderer.BoxParticle(
								new DebugRenderer.Box(chunkDataView.getSectionPos(), 32f, 64f + offset, 0.07f, debugColor),
								2.0, 16f
						)
				);
			}
		});
	}
	
	
	
	//======================//
	// render source getter //
	//======================//
	
	public CompletableFuture<ColumnRenderSource> getOrLoadCachedDataSourceAsync(Executor fileReaderThreads)
	{
		CompletableFuture<ColumnRenderSource> renderSourceLoadFuture = this.getCachedDataSourceAsync(true);
		if (renderSourceLoadFuture != null)
		{
			// return the in-process future
			return renderSourceLoadFuture;
		}
		else
		{
			// there is no cached data, we'll have to load it
			
			renderSourceLoadFuture = new CompletableFuture<>();
			if (!this.renderSourceLoadFutureRef.compareAndSet(null, renderSourceLoadFuture))
			{
				// two threads attempted to start this job at the same time, only use the first future
				renderSourceLoadFuture = this.renderSourceLoadFutureRef.get();
			}
		}
		
		
		
		final CompletableFuture<ColumnRenderSource> getSourceFuture = renderSourceLoadFuture;
		if (!this.doesDtoExist)
		{
			// create a new Meta file and render source
			
			
			// create an empty render source
			byte dataDetailLevel = (byte) (this.pos.getDetailLevel() - ColumnRenderSource.SECTION_SIZE_OFFSET);
			int verticalSize = Config.Client.Advanced.Graphics.Quality.verticalQuality.get().calculateMaxVerticalData(dataDetailLevel);
			ColumnRenderSource newColumnRenderSource = new ColumnRenderSource(this.pos, verticalSize, this.clientLevel.getMinY());
			
			this.baseMetaData = new BaseMetaData(
					newColumnRenderSource.getSectionPos(), -1, newColumnRenderSource.getDataDetailLevel(), 
					newColumnRenderSource.worldGenStep, RENDER_SOURCE_TYPE, 
					newColumnRenderSource.getRenderDataFormatVersion(), Long.MAX_VALUE);
			
			this.updateRenderCacheAsync(newColumnRenderSource).whenComplete((voidObj, ex) ->
				{
					this.cachedRenderDataSourceRef = new DataSourceReferenceTracker.RenderDataSourceSoftRef(this, newColumnRenderSource);

					this.renderSourceLoadFutureRef.set(null);
					getSourceFuture.complete(newColumnRenderSource);
				});
		}
		else
		{
			// load the existing Meta file and render source
			
			CompletableFuture.supplyAsync(() ->
					{
						if (this.baseMetaData == null)
						{
							throw new IllegalStateException("Meta data not loaded!");
						}
						
						// Load the render source file.
						ColumnRenderSource renderSource;
						try (InputStream inputStream = this.getInputStream(); // throws IoException
								DhDataInputStream compressedInputStream = new DhDataInputStream(inputStream))
						{
							renderSource = ColumnRenderLoader.INSTANCE.loadRenderSource(this, compressedInputStream, this.clientLevel);
						}
						catch (IOException ex)
						{
							throw new CompletionException(ex);
						}
						
						return renderSource;
					}, fileReaderThreads)
					// TODO: Check for file version and only update if needed.
					.thenCompose(renderSource -> this.updateRenderCacheAsync(renderSource))
					.whenComplete((renderSource, ex) ->
					{
						if (ex != null)
						{
							if (!LodUtil.isInterruptOrReject(ex))
							{
								LOGGER.error("Error loading pos: "+this.pos+": ", ex);
							}
							
							// set the render source to null to prevent instances where a corrupt or incomplete render source is returned
							renderSource = null;
						}
						
						this.renderSourceLoadFutureRef.set(null);
						
						this.cachedRenderDataSourceRef = new DataSourceReferenceTracker.RenderDataSourceSoftRef(this, renderSource);
						getSourceFuture.complete(renderSource);
					});
		}
		
		return getSourceFuture;
	}
	// TODO merge with FullDataMetaFile
	private InputStream getInputStream() throws IOException
	{
		MetaDataDto dto = this.renderDataSourceProvider.getRepo().getByPrimaryKey(this.pos.serialize());
		return new ByteArrayInputStream(dto.dataArray);
	}
	
	
	
	//===============//
	// cache handler //
	//===============//
	
	public CompletableFuture<ColumnRenderSource> updateRenderCacheAsync(ColumnRenderSource renderSource)
	{
		DebugRenderer.BoxWithLife debugBox = new DebugRenderer.BoxWithLife(new DebugRenderer.Box(renderSource.sectionPos, 74f, 86f, 0.1f, Color.red), 1.0, 32f, Color.green.darker());
		
		
		// Skip updating the cache if the data file is already up-to-date
		FullDataMetaFile dataFile = this.fullDataSourceProvider.getFileIfExist(this.pos);
		if (!ALWAYS_INVALIDATE_CACHE && dataFile != null && dataFile.baseMetaData != null && dataFile.baseMetaData.checksum == this.baseMetaData.dataVersion.get()) // TODO can we make it so the version comparisons either both use the checksum or the dataVersion? Comparing checksum and dataVersion is kinda confusing
		{
			LOGGER.debug("Skipping render cache update for " + this.pos);
			renderSource.localVersion.incrementAndGet();
			return CompletableFuture.completedFuture(renderSource);
		}
		
		
		
		final Reference<Integer> renderDataVersionRef = new Reference<>(Integer.MAX_VALUE);
		
		// get the full data source
		CompletableFuture<IFullDataSource> fullDataSourceFuture =
				this.fullDataSourceProvider.readAsync(renderSource.getSectionPos())
						.thenApply((fullDataSource) ->
						{
							debugBox.box.color = Color.yellow.darker();
							
							// get the metaFile's version
							FullDataMetaFile renderSourceMetaFile = this.fullDataSourceProvider.getFileIfExist(this.pos);
							if (renderSourceMetaFile != null && renderSourceMetaFile.baseMetaData != null)
							{
								renderDataVersionRef.value = renderSourceMetaFile.baseMetaData.checksum;
							}
							
							return fullDataSource;
						}).exceptionally((ex) ->
						{
							LOGGER.error("Exception when getting data for updateCache()", ex);
							return null;
						});
		
		
		
		// convert the full data source into a render source
		CompletableFuture<ColumnRenderSource> transformFuture = fullDataSourceFuture 
				.handle((fullDataSource, ex) ->
				{
					if (ex == null)
					{
						ColumnRenderSource newRenderSource = null;
						try
						{
							newRenderSource = FullDataToRenderDataTransformer.transformFullDataToRenderSource(fullDataSource, this.clientLevel);
						}
						catch (Exception e)
						{
							LOGGER.error("Unable to transform full data to render data for pos: "+this.pos, e);
						}
						
						try
						{
							if (newRenderSource != null)
							{
								renderSource.updateFromRenderSource(newRenderSource);
								
								// update the meta data
								this.baseMetaData.dataVersion.set(renderDataVersionRef.value);
								this.baseMetaData.dataDetailLevel = renderSource.getDataDetailLevel();
								this.baseMetaData.dataType = RENDER_SOURCE_TYPE;
								this.baseMetaData.binaryDataFormatVersion = renderSource.getRenderDataFormatVersion();
								
								// save to file
								this.save(renderSource);
							}
						}
						catch (Throwable e)
						{
							LOGGER.error("Exception when writing render data for pos: "+this.pos, e);
						}
					}
					else if (!LodUtil.isInterruptOrReject(ex))
					{
						LOGGER.error("Exception when updating render file using data source: ", ex);
					}
					
					debugBox.close();
					return renderSource;
				});
		return transformFuture;
	}
	
	
	
	//===============//
	// file handling //
	//===============//
	
	public CompletableFuture<Void> flushAndSaveAsync()
	{
		if (!this.renderDataSourceProvider.getRepo().existsWithPrimaryKey(this.pos.serialize()))
		{
			return CompletableFuture.completedFuture(null); // No need to save if the file doesn't exist.
		}
		
		// FIXME: TODO: Change updateRenderSource to true. Currently is false cause a dead future making render handler hang,
		//   and that render cache aren't actually used really yet due to missing versioning atm. So disabling for now.
		CompletableFuture<ColumnRenderSource> getSourceFuture = this.getCachedDataSourceAsync(false);
		if (getSourceFuture == null)
		{
			return CompletableFuture.completedFuture(null); // If there is no cached data, there is no need to save.
		}
		
		// Wait for the data to be read, which also flushes changes to the file.
		return getSourceFuture.thenAccept((columnRenderSource) -> { /* discard the render source, it doesn't need to be returned */ });
	}
	
	/** writes the given {@link ColumnRenderSource} to file */
	private void save(ColumnRenderSource renderSource)
	{
		if (renderSource.isEmpty())
		{
			// delete the empty data source
			this.fullDataSourceProvider.getRepo().deleteByPrimaryKey(this.pos.serialize());
			this.doesDtoExist = false;
		}
		else
		{
			//LOGGER.info("Saving updated render file v[{}] at sect {}", this.metaData.dataVersion.get(), this.pos);
			try
			{
				super.writeToDatabase((dhDataOutputStream) -> renderSource.writeData(dhDataOutputStream), this.renderDataSourceProvider.getRepo());
				this.doesDtoExist = true;
			}
			catch (IOException e)
			{
				LOGGER.error("Failed to save updated render data for pos "+this.pos, e);
			}
		}
	}
	
	
	
	//=======//
	// debug //
	//=======//
	
	@Override
	public void debugRender(DebugRenderer debugRenderer)
	{
		if (this.cachedRenderDataSourceRef.get() != null)
		{
			return;
			//color = Color.GREEN;
		}
		
		// determine the color
		Color color = Color.black;
		if (this.renderSourceLoadFutureRef.get() != null)
		{
			color = Color.BLUE;
		}
		else if (this.doesDtoExist)
		{
			color = Color.RED;
		}
		
		debugRenderer.renderBox(new DebugRenderer.Box(this.pos, 64, 72, 0.05f, color));
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/** @return returns null if {@link RenderDataMetaFile#renderSourceLoadFutureRef} is empty and no cached {@link ColumnRenderSource} exists. */
	@Nullable
	private CompletableFuture<ColumnRenderSource> getCachedDataSourceAsync(boolean updateRenderSourceCache)
	{
		// check if another thread is already loading the data source
		CompletableFuture<ColumnRenderSource> renderSourceLoadFuture = this.renderSourceLoadFutureRef.get();
		if (renderSourceLoadFuture != null)
		{
			return renderSourceLoadFuture;
		}
		
		
		// attempt to get the cached render source
		ColumnRenderSource cachedRenderDataSource = this.cachedRenderDataSourceRef.get();
		if (cachedRenderDataSource == null)
		{
			// no cached data exists and no one is trying to load it
			return null;
		}
		else
		{
			// cached data exists
			
			if (!updateRenderSourceCache)
			{
				// just return the render source
				return CompletableFuture.completedFuture(cachedRenderDataSource);
			}
			
			// update the render cache, wait for the update to finish, then return the render source  
			
			// Create a new future if one doesn't already exist
			CompletableFuture<ColumnRenderSource> newFuture = new CompletableFuture<>();
			CompletableFuture<ColumnRenderSource> oldFuture = AtomicsUtil.compareAndExchange(this.renderSourceLoadFutureRef, null, newFuture);
			
			if (oldFuture != null)
			{
				// An update is already in progress, return its future.
				return oldFuture;
			}
			else
			{
				this.updateRenderCacheAsync(cachedRenderDataSource)		
						// wait for the handler to finish before returning the renderSource
						.handle((ignoredRenderSource, ex) -> 
						{
							newFuture.complete(cachedRenderDataSource);
							this.renderSourceLoadFutureRef.set(null);
							
							return null;
						});
				return newFuture;
			}
		}
	}
	
}
