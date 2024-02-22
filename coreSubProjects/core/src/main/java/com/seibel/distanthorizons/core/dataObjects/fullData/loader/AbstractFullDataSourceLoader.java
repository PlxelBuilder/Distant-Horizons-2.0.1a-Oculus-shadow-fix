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

package com.seibel.distanthorizons.core.dataObjects.fullData.loader;

import com.google.common.collect.HashMultimap;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces.IFullDataSource;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataMetaFile;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractFullDataSourceLoader
{
	public static final HashMultimap<Class<? extends IFullDataSource>, AbstractFullDataSourceLoader> LOADER_REGISTRY = HashMultimap.create();
	public static final HashMap<String, Class<? extends IFullDataSource>> DATATYPE_REGISTRY = new HashMap<>();
	
	
	public final Class<? extends IFullDataSource> fullDataSourceClass;
	
	public final String datatype;
	public final byte[] loaderSupportedVersions;
	
	/** used when pooling data sources */
	private final ArrayList<IFullDataSource> cachedSources = new ArrayList<>();
	private final ReentrantLock cacheLock = new ReentrantLock();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public AbstractFullDataSourceLoader(Class<? extends IFullDataSource> fullDataSourceClass, String datatype, byte[] loaderSupportedVersions)
	{
		this.datatype = datatype;
		this.loaderSupportedVersions = loaderSupportedVersions;
		Arrays.sort(loaderSupportedVersions); // sort to allow fast access
		this.fullDataSourceClass = fullDataSourceClass;
		
		if (DATATYPE_REGISTRY.containsKey(datatype) && DATATYPE_REGISTRY.get(datatype) != fullDataSourceClass)
		{
			throw new IllegalArgumentException("Loader for datatype: [" + datatype + "] already registered with different class: "
					+ DATATYPE_REGISTRY.get(datatype) + " != " + fullDataSourceClass);
		}
		
		Set<AbstractFullDataSourceLoader> loaders = LOADER_REGISTRY.get(fullDataSourceClass);
		if (loaders.stream().anyMatch(other ->
			{
				// see if any loaderSupportsVersion conflicts with this one
				for (byte otherVer : other.loaderSupportedVersions)
				{
					if (Arrays.binarySearch(loaderSupportedVersions, otherVer) >= 0)
					{
						return true;
					}
				}
				return false;
			}))
		{
			throw new IllegalArgumentException("Loader for class " + fullDataSourceClass + " that supports one of the version in "
					+ Arrays.toString(loaderSupportedVersions) + " already registered!");
		}
		
		DATATYPE_REGISTRY.put(datatype, fullDataSourceClass);
		LOADER_REGISTRY.put(fullDataSourceClass, this);
	}
	
	
	
	//================//
	// loader getters // 
	//================//
	
	public static AbstractFullDataSourceLoader getLoader(String dataType, byte dataVersion)
	{
		return LOADER_REGISTRY.get(DATATYPE_REGISTRY.get(dataType)).stream()
				.filter(loader -> Arrays.binarySearch(loader.loaderSupportedVersions, dataVersion) >= 0)
				.findFirst().orElse(null);
	}
	
	public static AbstractFullDataSourceLoader getLoader(Class<? extends IFullDataSource> clazz, byte dataVersion)
	{
		return LOADER_REGISTRY.get(clazz).stream()
				.filter(loader -> Arrays.binarySearch(loader.loaderSupportedVersions, dataVersion) >= 0)
				.findFirst().orElse(null);
	}
	
	
	
	//==================//
	// abstract methods //
	//==================//
	
	protected abstract IFullDataSource createEmptyDataSource(DhSectionPos pos);
	
	
	
	//==============//
	// data loading //
	//==============//
	
	/**
	 * Can return null if any of the requirements aren't met.
	 *
	 * @throws InterruptedException if the loader thread is interrupted, generally happens when the level is shutting down
	 */
	public IFullDataSource loadDataSource(FullDataMetaFile dataFile, DhDataInputStream inputStream, IDhLevel level) throws IOException, InterruptedException
	{
		IFullDataSource dataSource = this.createEmptyDataSource(dataFile.pos);
		dataSource.populateFromStream(dataFile, inputStream, level);
		return dataSource;
	}
	
	/** Should be used in conjunction with {@link AbstractFullDataSourceLoader#returnPooledDataSource} to return the pooled sources. */
	public IFullDataSource loadTemporaryDataSource(FullDataMetaFile dataFile, DhDataInputStream inputStream, IDhLevel level) throws IOException, InterruptedException
	{
		IFullDataSource dataSource = this.tryGetPooledSource();
		if (dataSource != null)
		{
			dataSource.repopulateFromStream(dataFile, inputStream, level);
		}
		else
		{
			dataSource = this.loadDataSource(dataFile, inputStream, level);
		}
		
		return dataSource;
	}
	
	
	
	//=====================//
	// data source pooling //
	//=====================//

	/** @return null if no pooled source exists */ 
	public IFullDataSource tryGetPooledSource()
	{
		try
		{
			this.cacheLock.lock();

			int index = this.cachedSources.size() - 1;
			if (index == -1)
			{
				return null;
			}
			else
			{
				return this.cachedSources.remove(index);
			}
		}
		finally
		{
			this.cacheLock.unlock();
		}
	}
	
	/** 
	 * Doesn't have to be called, if a data source isn't returned, nothing will be leaked. 
	 * It just means a new source must be constructed next time {@link AbstractFullDataSourceLoader#tryGetPooledSource} is called.
	 */
	public void returnPooledDataSource(IFullDataSource dataSource)
	{
		if (dataSource == null)
		{
			return;
		}
		else if (dataSource.getClass() != this.fullDataSourceClass)
		{
			return;
		}
		else if (this.cachedSources.size() > 25)
		{
			return;
		}
		
		try
		{
			this.cacheLock.lock();
			this.cachedSources.add(dataSource);
		}
		finally
		{
			this.cacheLock.unlock();
		}
	}
	
}
