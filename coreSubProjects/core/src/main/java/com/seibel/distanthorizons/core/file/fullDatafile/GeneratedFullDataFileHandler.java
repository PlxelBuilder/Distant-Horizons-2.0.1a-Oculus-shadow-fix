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
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.CompleteFullDataSource;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces.IFullDataSource;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces.IIncompleteFullDataSource;
import com.seibel.distanthorizons.core.file.structure.AbstractSaveStructure;
import com.seibel.distanthorizons.core.generation.MissingWorldGenPositionFinder;
import com.seibel.distanthorizons.core.generation.IWorldGenerationQueue;
import com.seibel.distanthorizons.core.generation.tasks.IWorldGenTaskTracker;
import com.seibel.distanthorizons.core.generation.tasks.WorldGenResult;
import com.seibel.distanthorizons.core.level.DhLevel;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.LodUtil;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public class GeneratedFullDataFileHandler extends FullDataFileHandler
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final Timer CHUNK_GEN_FINISHED_TIMER = new Timer();
	
	private final AtomicReference<IWorldGenerationQueue> worldGenQueueRef = new AtomicReference<>(null);
	
	private final ArrayList<IOnWorldGenCompleteListener> onWorldGenTaskCompleteListeners = new ArrayList<>();
	
	/** Used to prevent data sources from being garbage collected before their world gen finishes. */
	private final ConcurrentHashMap<DhSectionPos, IFullDataSource> generatingDataSourceByPos = new ConcurrentHashMap<>();
	
	public GeneratedFullDataFileHandler(IDhLevel level, AbstractSaveStructure saveStructure) { super(level, saveStructure); }
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public CompletableFuture<IFullDataSource> readAsync(DhSectionPos pos)
	{
		CompletableFuture<IFullDataSource> future = super.readAsync(pos);
		return future.thenApply((dataSource) -> 
		{
			// add world gen tasks for missing columns in the data source
			IWorldGenerationQueue worldGenQueue = this.worldGenQueueRef.get();
			FullDataMetaFile metaFile = this.loadedMetaFileBySectionPos.get(pos);
			if (worldGenQueue != null && metaFile != null)
			{
				this.queueWorldGenForMissingColumnsInDataSource(worldGenQueue, metaFile, dataSource);
			}
			
			return dataSource;
		});
	}
	
	@Override
	public void onRenderDataFileLoaded(DhSectionPos pos)
	{
		// add world gen tasks for missing columns in the data source
		IWorldGenerationQueue worldGenQueue = this.worldGenQueueRef.get();
		FullDataMetaFile metaFile = this.getLoadOrMakeFile(pos, false);
		if (worldGenQueue != null && metaFile != null)
		{
			metaFile.getDataSourceWithoutCachingAsync().thenApply((fullDataSource) -> 
			{
				this.queueWorldGenForMissingColumnsInDataSource(worldGenQueue, metaFile, fullDataSource);
				return fullDataSource;
			});
		}
	}
	
	
	
	//==================//
	// generation queue //
	//==================//
	
	/**
	 * Assigns the queue for handling world gen and does first time setup as well. <br> 
	 * Assumes there isn't a pre-existing queue. 
	 */ 
	public void setWorldGenerationQueue(IWorldGenerationQueue newWorldGenQueue)
	{
		boolean oldQueueExists = this.worldGenQueueRef.compareAndSet(null, newWorldGenQueue);
		LodUtil.assertTrue(oldQueueExists, "previous world gen queue is still here!");
		LOGGER.info("Set world gen queue for level "+this.level+" to start.");
		
		this.ForEachFile(metaFile -> 
		{
			IFullDataSource dataSource = metaFile.getCachedDataSourceNowOrNull();
			if (dataSource == null)
			{
				return;
			}
			
			metaFile.genQueueChecked = false; // allow the system to check for missing positions again
			this.queueWorldGenForMissingColumnsInDataSource(this.worldGenQueueRef.get(), metaFile, dataSource);
			
			if (dataSource instanceof CompleteFullDataSource)
			{
				return;
			}
			metaFile.markNeedsUpdate();
		});
		
		this.flushAndSaveAsync(); // Trigger an update to the meta files
	}
	
	public void clearGenerationQueue()
	{
		this.worldGenQueueRef.set(null);
		this.generatingDataSourceByPos.clear(); // clear the incomplete data sources
	}
	
	// TODO what is this here for?
	public void removeGenRequestIf(Function<DhSectionPos, Boolean> removeIf)
	{
		this.generatingDataSourceByPos.forEach((pos, dataSource) ->
		{
			if (removeIf.apply(pos))
			{
				//this.worldGenQueueRef.get().cancelGenTasks(pos); // shouldn't this be called if we actually want to stop world gen
				this.generatingDataSourceByPos.remove(pos);
			}
		});
	}
	
	
	
	//=================//
	// event listeners //
	//=================//
	
	public void addWorldGenCompleteListener(IOnWorldGenCompleteListener listener) { this.onWorldGenTaskCompleteListeners.add(listener); }
	
	public void removeWorldGenCompleteListener(IOnWorldGenCompleteListener listener) { this.onWorldGenTaskCompleteListeners.remove(listener); }
	
	private IFullDataSource tryPromoteDataSource(IIncompleteFullDataSource source)
	{
		IFullDataSource newSource = source.tryPromotingToCompleteDataSource();
		if (newSource instanceof CompleteFullDataSource)
		{
			this.generatingDataSourceByPos.remove(source.getSectionPos());
		}
		return newSource;
	}
	
	
	
	//========//
	// events //
	//========//
	
	// Try update the gen queue on this data source. If null, then nothing was done.
	@Nullable
	private CompletableFuture<IFullDataSource> updateFromExistingDataSourcesAsync(FullDataMetaFile file, IIncompleteFullDataSource data, boolean usePooledDataSources)
	{
		DhSectionPos pos = file.pos;
		ArrayList<FullDataMetaFile> existingFiles = new ArrayList<>();
		ArrayList<DhSectionPos> missingPositions = new ArrayList<>();
		this.getDataFilesForPosition(pos, pos, existingFiles, missingPositions);
		
		if (missingPositions.size() == 1)
		{
			// Only missing myself. I.e. no child file data exists yet.
			return this.tryStartGenTask(file, data);
		}
		else
		{
			// There are other data source files to sample from.
			this.makeFiles(missingPositions, existingFiles);
			return this.sampleFromFileArray(data, existingFiles, usePooledDataSources)
					.thenApply(this::tryPromoteDataSource)
					.exceptionally((e) ->
					{
						this.removeCorruptedFile(pos, e);
						return null;
					});
		}
	}
	@Nullable
	private CompletableFuture<IFullDataSource> tryStartGenTask(FullDataMetaFile metaFile, IIncompleteFullDataSource dataSource) // TODO after generation is finished, save and free any full datasources that aren't in use (IE high detail ones below the top)
	{
		IWorldGenerationQueue worldGenQueue = this.worldGenQueueRef.get();
		if (worldGenQueue != null)
		{
			this.queueWorldGenForMissingColumnsInDataSource(worldGenQueue, metaFile, dataSource);
			return CompletableFuture.completedFuture(dataSource);
		}
		return null;
	}
	
	@Override
	public CompletableFuture<IFullDataSource> onDataFileCreatedAsync(FullDataMetaFile file)
	{
		DhSectionPos pos = file.pos;
		IIncompleteFullDataSource data = this.makeEmptyDataSource(pos);
		CompletableFuture<IFullDataSource> future = this.updateFromExistingDataSourcesAsync(file, data, true);
		// Cant start gen task, so return the data
		return future == null ? CompletableFuture.completedFuture(data) : future;
	}
	
	@Override
	public CompletableFuture<DataFileUpdateResult> onDataFileUpdateAsync(IFullDataSource fullDataSource, FullDataMetaFile file, boolean dataChanged)
	{
		LodUtil.assertTrue(this.fullDataRepo.existsWithPrimaryKey(file.pos.serialize()) || dataChanged);
		
		
		if (fullDataSource instanceof CompleteFullDataSource)
		{
			this.generatingDataSourceByPos.remove(fullDataSource.getSectionPos());
		}
		this.fireOnGenPosSuccessListeners(fullDataSource.getSectionPos());
		
		
		if (fullDataSource instanceof IIncompleteFullDataSource && !file.genQueueChecked)
		{
			IWorldGenerationQueue worldGenQueue = this.worldGenQueueRef.get();
			if (worldGenQueue != null)
			{
				CompletableFuture<IFullDataSource> future = this.updateFromExistingDataSourcesAsync(file, (IIncompleteFullDataSource) fullDataSource, false);
				if (future != null)
				{
					final boolean finalDataChanged = dataChanged;
					return future.thenApply((newSource) -> new DataFileUpdateResult(newSource, finalDataChanged));
				}
			}
		}
		
		return CompletableFuture.completedFuture(new DataFileUpdateResult(fullDataSource, dataChanged));
	}
	
	private void onWorldGenTaskComplete(WorldGenResult genTaskResult, Throwable exception, GenTask genTask, DhSectionPos pos)
	{
		if (exception != null)
		{
			// don't log shutdown exceptions
			if (!(exception instanceof CancellationException || exception.getCause() instanceof CancellationException))
			{
				LOGGER.error("Uncaught Gen Task Exception at " + pos + ":", exception);
			}
		}
		else if (genTaskResult.success)
		{
			// generation completed, update the files and listener(s)
			this.flushAndSaveAsync(pos).join();
			
			// FIXME this is a bad fix to prevent full data sources saving incomplete, causing holes in the world after generation.
			//  The problem appears to be that the save may be happening too quickly,
			//  potentially happening before the meta file has the newly generated data added to it.
			CHUNK_GEN_FINISHED_TIMER.schedule(new TimerTask()
			{
				@Override
				public void run() { GeneratedFullDataFileHandler.this.flushAndSaveAsync(pos).join(); }
			}, 4000L);
			
			this.fireOnGenPosSuccessListeners(pos);
			return;
		}
		else
		{
			// generation didn't complete
			LOGGER.debug("Gen Task Failed at " + pos);
		}
		
		
		// if the generation task was split up into smaller positions, add the on-complete event to them
		for (CompletableFuture<WorldGenResult> siblingFuture : genTaskResult.childFutures)
		{
			siblingFuture.whenComplete((siblingGenTaskResult, siblingEx) -> this.onWorldGenTaskComplete(siblingGenTaskResult, siblingEx, genTask, pos));
		}
		
		genTask.releaseStrongReference();
	}
	
	private void fireOnGenPosSuccessListeners(DhSectionPos pos)
	{
		// fire the event listeners 
		for (IOnWorldGenCompleteListener listener : this.onWorldGenTaskCompleteListeners)
		{
			listener.onWorldGenTaskComplete(pos);
		}
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	private void queueWorldGenForMissingColumnsInDataSource(IWorldGenerationQueue worldGenQueue, FullDataMetaFile metaFile, IFullDataSource dataSource)
	{
		// Due to a bug in the current system, some Complete data sources aren't actually complete
		// and will need additional generation to finish
		//if (dataSource instanceof CompleteFullDataSource)
		//{
		//	return;
		//}
		
		if (metaFile.genQueueChecked)
		{
			// world gen has already been checked for this file
			return;
		}
		metaFile.genQueueChecked = true;
		
		
		// get the ungenerated pos list
		byte minGeneratorSectionDetailLevel = (byte) (worldGenQueue.highestDataDetail() + DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL);
		ArrayList<DhSectionPos> genPosList = MissingWorldGenPositionFinder.getUngeneratedPosList(dataSource, minGeneratorSectionDetailLevel, true);
		
		// start each pos generating
		ArrayList<CompletableFuture<WorldGenResult>>  taskFutureList = new ArrayList<>();
		for (DhSectionPos genPos : genPosList)
		{
			// make sure each meta file has been created (not doing this will prevent down sampling and/or saving the generated data source) 
			this.getLoadOrMakeFile(genPos, true);
			this.getLoadOrMakeFile(metaFile.pos, true);
			
			// queue each gen task
			GenTask genTask = new GenTask(dataSource.getSectionPos(), new WeakReference<>(dataSource));
			CompletableFuture<WorldGenResult> worldGenFuture = worldGenQueue.submitGenTask(genPos, dataSource.getDataDetailLevel(), genTask);
			worldGenFuture.whenComplete((genTaskResult, ex) ->
			{
				this.onWorldGenTaskComplete(genTaskResult, ex, genTask, genPos);
				this.onWorldGenTaskComplete(genTaskResult, ex, genTask, metaFile.pos);
			});
			
			taskFutureList.add(worldGenFuture);
		}
		
		
		// mark the data source as generating if necessary
		if (taskFutureList.size() != 0)
		{
			this.generatingDataSourceByPos.put(metaFile.pos, dataSource);
		}
		CompletableFuture.allOf(taskFutureList.toArray(new CompletableFuture[0]))
			.whenComplete((voidObj, ex) ->
			{
				metaFile.flushAndSaveAsync();
				this.generatingDataSourceByPos.remove(metaFile.pos);
			});
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	private class GenTask implements IWorldGenTaskTracker
	{
		private final DhSectionPos pos;
		
		// weak reference (probably) used to prevent overloading the GC when lots of gen tasks are created? // TODO do we still need a weak reference here?
		private final WeakReference<IFullDataSource> targetFullDataSourceRef;
		// the target data source is where the generated chunk data will be put when completed
		private IFullDataSource loadedTargetFullDataSource = null;
		
		
		
		public GenTask(DhSectionPos pos, WeakReference<IFullDataSource> targetFullDataSourceRef)
		{
			this.pos = pos;
			this.targetFullDataSourceRef = targetFullDataSourceRef;
		}
		
		
		
		@Override
		public boolean isMemoryAddressValid() { return this.targetFullDataSourceRef.get() != null; }
		
		@Override
		public Consumer<ChunkSizedFullDataAccessor> getChunkDataConsumer()
		{
			if (this.loadedTargetFullDataSource == null)
			{
				this.loadedTargetFullDataSource = this.targetFullDataSourceRef.get();
			}
			if (this.loadedTargetFullDataSource == null)
			{
				return null;
			}
			
			
			return (chunkSizedFullDataSource) ->
			{
				if (chunkSizedFullDataSource.getSectionPos().overlapsExactly(this.loadedTargetFullDataSource.getSectionPos()))
				{
					((DhLevel) level).saveWrites(chunkSizedFullDataSource);
					//GeneratedFullDataFileHandler.this.write(this.loadedTargetFullDataSource.getSectionPos(), chunkSizedFullDataSource);
				}
			};
		}
		
		public void releaseStrongReference() { this.loadedTargetFullDataSource = null; }
		
	}
	
	/**
	 * used by external event listeners <br>
	 * TODO may or may not be best to have this in a separate file
	 */
	@FunctionalInterface
	public interface IOnWorldGenCompleteListener
	{
		/** Fired whenever a section has completed generating */
		void onWorldGenTaskComplete(DhSectionPos pos);
		
	}
	
}
