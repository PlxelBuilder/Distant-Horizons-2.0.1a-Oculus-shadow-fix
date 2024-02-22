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

package com.seibel.distanthorizons.core.file;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces.IFullDataSource;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataMetaFile;
import com.seibel.distanthorizons.core.file.metaData.AbstractMetaDataContainerFile;
import com.seibel.distanthorizons.core.file.renderfile.RenderDataMetaFile;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.ThreadUtil;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 
 * Keeps track of {@link FullDataMetaFile} and {@link RenderDataMetaFile}'s
 * and handles freeing their underlying data sources if they go unused for a certain amount of time. 
 */
public class DataSourceReferenceTracker
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final boolean LOG_GARBAGE_COLLECTIONS = false;
	
	/** How often the garbage collector thread will run */
	private static final long MS_BETWEEN_GARBAGE_CHECKS = TimeUnit.SECONDS.toMillis(60);
	/** How long a data source has to go unused before it can be freed */
	private static final long MS_TO_EXPIRE_DATA_SOURCE = TimeUnit.SECONDS.toMillis(60);
	
	
	// these queues are populated by the JVM's garbage collector after the assigned soft reference is freed
	private static final ReferenceQueue<IFullDataSource> FULL_DATA_GARBAGE_COLLECTED_QUEUE = new ReferenceQueue<>();
	private static final ReferenceQueue<ColumnRenderSource> RENDER_DATA_GARBAGE_COLLECTED_QUEUE = new ReferenceQueue<>();
	
	// TODO using a ConcurrentHashMap may or may not be the best choice here
	private static final Set<FullDataSourceSoftRef> FULL_DATA_SOFT_REFS = ConcurrentHashMap.newKeySet();
	private static final Set<RenderDataSourceSoftRef> RENDER_DATA_SOFT_REFS = ConcurrentHashMap.newKeySet();
	
	private static final ThreadPoolExecutor GARBAGE_COLLECTOR_THREAD = ThreadUtil.makeSingleThreadPool("DataSourceReferenceTracker", ThreadUtil.MINIMUM_RELATIVE_PRIORITY);
	
	
	
	//=================//
	// collector logic //
	//=================//
	
	/** Warning: this should not be called more than once. */
	public static void startGarbageCollectorBackgroundThread() { /*GARBAGE_COLLECTOR_THREAD.execute(() -> garbageCollectorLoop());*/ }
	private static void garbageCollectorLoop()
	{
		while(true)
		{
			try
			{
				runGarbageCollection();
				Thread.sleep(MS_BETWEEN_GARBAGE_CHECKS);
			}
			catch (InterruptedException e)
			{
				LOGGER.error("Garbage collector thread interrupted.", e);
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected data source garbage collector exception: " + e.getMessage(), e);
			}
		}
	}
	
	public static void runGarbageCollection()
	{ 
		removeGarbageCollectedDataSources();
		removeExpiredDataSources();
	}
	private static void removeGarbageCollectedDataSources()
	{
		FullDataSourceSoftRef garbageCollectedFullDataSoftRef = (FullDataSourceSoftRef) FULL_DATA_GARBAGE_COLLECTED_QUEUE.poll();
		while (garbageCollectedFullDataSoftRef != null)
		{
			if (LOG_GARBAGE_COLLECTIONS)
			{
				LOGGER.info("Full Data at pos: " + garbageCollectedFullDataSoftRef.metaFile.pos + " has been soft released.");
			}
			garbageCollectedFullDataSoftRef.close();
			
			garbageCollectedFullDataSoftRef = (FullDataSourceSoftRef) FULL_DATA_GARBAGE_COLLECTED_QUEUE.poll();
		}
		
		RenderDataSourceSoftRef renderSoftRef = (RenderDataSourceSoftRef) RENDER_DATA_GARBAGE_COLLECTED_QUEUE.poll();
		while (renderSoftRef != null)
		{
			if (LOG_GARBAGE_COLLECTIONS)
			{
				LOGGER.info("Render Data at pos: " + renderSoftRef.metaFile.pos + " has been soft released.");
			}
			renderSoftRef.close();
			
			renderSoftRef = (RenderDataSourceSoftRef) RENDER_DATA_GARBAGE_COLLECTED_QUEUE.poll();
		}
	}
	private static void removeExpiredDataSources()
	{
		// TODO merge these loops
		FULL_DATA_SOFT_REFS.removeIf((fullDataSoftRef) ->
		{
			boolean remove = fullDataSoftRef.isDataSourceExpired() || (fullDataSoftRef.silentGet() == null);
			if (remove)
			{
				fullDataSoftRef.clear();
				fullDataSoftRef.close();
				
				if (LOG_GARBAGE_COLLECTIONS)
				{
					LOGGER.info("Full Data at pos: " + fullDataSoftRef.metaFile.pos + " has expired and will be released at the next GC. ["+FULL_DATA_SOFT_REFS.size()+"] Full data sources remain.");
				}
			}
			
			return remove;
		});
		
		// TODO merge these loops
		RENDER_DATA_SOFT_REFS.removeIf((renderDataSoftRef) ->
		{
			boolean remove = renderDataSoftRef.isDataSourceExpired() || (renderDataSoftRef.silentGet() == null);
			if (remove)
			{
				renderDataSoftRef.clear();
				renderDataSoftRef.close();
				
				if (LOG_GARBAGE_COLLECTIONS)
				{
					LOGGER.info("Render Data at pos: " + renderDataSoftRef.metaFile.pos + " has expired and will be released at the next GC. ["+RENDER_DATA_SOFT_REFS.size()+"] Render data sources remain.");
				}
			}
			
			return remove;
		});
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	public static class FullDataSourceSoftRef extends AbstractDataSourceSoftTracker<FullDataMetaFile, IFullDataSource>
	{
		public FullDataSourceSoftRef(FullDataMetaFile metaFile, IFullDataSource data)
		{
			super(metaFile, data, FULL_DATA_GARBAGE_COLLECTED_QUEUE);
			FULL_DATA_SOFT_REFS.add(this);
		}
		
		@Override
		public void close() { FULL_DATA_SOFT_REFS.remove(this); }
	}
	public static class RenderDataSourceSoftRef extends AbstractDataSourceSoftTracker<RenderDataMetaFile, ColumnRenderSource>
	{
		public RenderDataSourceSoftRef(RenderDataMetaFile metaFile, ColumnRenderSource data)
		{
			super(metaFile, data, RENDER_DATA_GARBAGE_COLLECTED_QUEUE);
			RENDER_DATA_SOFT_REFS.add(this);
		}
		
		@Override
		public void close() { RENDER_DATA_SOFT_REFS.remove(this); }
	}
	
	/** wrapper for a {@link SoftReference} so we can track and manually remove unused sources */
	public static abstract class AbstractDataSourceSoftTracker<TMetaFile extends AbstractMetaDataContainerFile, TDataSource> extends SoftReference<TDataSource> implements Closeable
	{
		public final TMetaFile metaFile;
		public final long createdMsTime;
		
		private long expirationMsTime;
		
		
		
		public AbstractDataSourceSoftTracker(TMetaFile metaFile, TDataSource dataSource, ReferenceQueue<TDataSource> referenceQueue)
		{
			super(dataSource, referenceQueue);
			this.metaFile = metaFile;
			
			this.createdMsTime = System.currentTimeMillis();
			this.expirationMsTime = System.currentTimeMillis();
		}
		
		
		
		public void updateLastAccessedTime() { this.expirationMsTime = System.currentTimeMillis() + MS_TO_EXPIRE_DATA_SOURCE; }
		public long getExpirationMsTime() { return this.expirationMsTime; }
		public boolean isDataSourceExpired() { return this.expirationMsTime > System.currentTimeMillis(); }		
		
		
		@Override
		public TDataSource get()
		{
			this.updateLastAccessedTime();
			return super.get();
		}
		
		/** 
		 * Gets the underlying datasource without updating the {@link AbstractDataSourceSoftTracker#expirationMsTime} 
		 * Note: this still updates {@link SoftReference}'s timestamp variable which may prevent the JVM from
		 * marking this reference as valid for deletion.
		 */
		public TDataSource silentGet() { return super.get(); }
		
	}
	
}
