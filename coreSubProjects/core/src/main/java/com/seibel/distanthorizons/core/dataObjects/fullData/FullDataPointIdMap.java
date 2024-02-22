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

package com.seibel.distanthorizons.core.dataObjects.fullData;

import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataInputStream;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * WARNING: This is not THREAD-SAFE!
 * <p>
 * Used to map a numerical IDs to a Biome/BlockState pair.
 *
 * @author Leetom
 */
public class FullDataPointIdMap
{
	private static final Logger LOGGER = LogManager.getLogger();
	/**
	 * Should only be enabled when debugging.
	 * Has the system check if any duplicate Entries were read/written
	 * when (de)serializing.
	 */
	private static final boolean RUN_SERIALIZATION_DUPLICATE_VALIDATION = false;
	/** Distant Horizons - Block State Wrapper */
	private static final String BLOCK_STATE_SEPARATOR_STRING = "_DH-BSW_";
	
	
	// FIXME: Improve performance maybe?
	/** used when the data point map is running normally */
	private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	
	/** should only be used for debugging */
	private DhSectionPos pos;
	
	/** The index should be the same as the Entry's ID */
	private final ArrayList<Entry> entryList = new ArrayList<>();
	private final HashMap<Entry, Integer> idMap = new HashMap<>();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public FullDataPointIdMap(DhSectionPos pos) { this.pos = pos; }
	
	
	
	//=========//
	// getters //
	//=========//
	
	/** @throws IndexOutOfBoundsException if the given ID isn't in the {@link FullDataPointIdMap#entryList} */
	private Entry getEntry(int id) throws IndexOutOfBoundsException
	{
		try
		{
			this.readWriteLock.readLock().lock();
			Entry entry;
			try
			{
				entry = this.entryList.get(id);
			}
			catch (IndexOutOfBoundsException e)
			{
				throw new IndexOutOfBoundsException("FullData ID Map out of sync for pos: "+this.pos+". ID: ["+id+"] greater than the number of known ID's: ["+this.entryList.size()+"].");
			}
			
			return entry;
		}
		finally
		{
			this.readWriteLock.readLock().unlock();
		}
	}
	
	/** @see FullDataPointIdMap#getEntry(int) */
	public IBiomeWrapper getBiomeWrapper(int id) throws IndexOutOfBoundsException { return this.getEntry(id).biome; }
	/** @see FullDataPointIdMap#getEntry(int) */
	public IBlockStateWrapper getBlockStateWrapper(int id) throws IndexOutOfBoundsException { return this.getEntry(id).blockState; }
	
	
	/** @return -1 if the list is empty */
	public int getMaxValidId() { return this.entryList.size() - 1; }
	
	public DhSectionPos getPos() { return this.pos; }
	
	
	
	//=========//
	// setters //
	//=========//
	
	/**
	 * If an entry with the given values already exists nothing will
	 * be added but the existing item's ID will still be returned.
	 */
	public int addIfNotPresentAndGetId(IBiomeWrapper biome, IBlockStateWrapper blockState) { return this.addIfNotPresentAndGetId(new Entry(biome, blockState), true); }
	/** @param useWriteLocks should only be false if this method is already in a write lock to prevent unlocking at the wrong time */
	private int addIfNotPresentAndGetId(Entry biomeBlockStateEntry, boolean useWriteLocks)
	{
		try
		{
			if (useWriteLocks)
			{
				this.readWriteLock.writeLock().lock();
			}
			
			
			int id;
			if (this.idMap.containsKey(biomeBlockStateEntry))
			{
				// use the existing ID
				id = this.idMap.get(biomeBlockStateEntry);
			}
			else
			{
				// Add the new ID
				id = this.entryList.size();
				this.entryList.add(biomeBlockStateEntry);
				this.idMap.put(biomeBlockStateEntry, id);
			}
			
			return id;
		}
		finally
		{
			if (useWriteLocks)
			{
				this.readWriteLock.writeLock().unlock();
			}
		}
	}
	
	
	/**
	 * Adds each entry from the given map to this map.
	 *
	 * @return an array of each added entry's ID in this map in order
	 */
	public int[] mergeAndReturnRemappedEntityIds(FullDataPointIdMap target)
	{
		try
		{
			LOGGER.trace("merging {" + this.pos + ", " + this.entryList.size() + "} and {" + target.pos + ", " + target.entryList.size() + "}");
			
			target.readWriteLock.readLock().lock();
			this.readWriteLock.writeLock().lock();
			
			ArrayList<Entry> entriesToMerge = target.entryList;
			int[] remappedEntryIds = new int[entriesToMerge.size()];
			for (int i = 0; i < entriesToMerge.size(); i++)
			{
				Entry entity = entriesToMerge.get(i);
				int id = this.addIfNotPresentAndGetId(entity, false);
				remappedEntryIds[i] = id;
			}
			
			return remappedEntryIds;
		}
		finally
		{
			this.readWriteLock.writeLock().unlock();
			target.readWriteLock.readLock().unlock();
			
			LOGGER.trace("finished merging {" + this.pos + ", " + this.entryList.size() + "} and {" + target.pos + ", " + target.entryList.size() + "}");
		}
	}
	
	/** Should only be used if this map is going to be reused, otherwise bad things will happen. */
	public void clear(DhSectionPos pos)
	{
		this.pos = pos;
		this.entryList.clear();
		this.idMap.clear();
	}
	
	
	
	//=============//
	// serializing //
	//=============//
	
	/** Serializes all contained entries into the given stream, formatted in UTF */
	public void serialize(DhDataOutputStream outputStream) throws IOException
	{
		try
		{
			this.readWriteLock.readLock().lock();
			outputStream.writeInt(this.entryList.size());
			
			// only used when debugging
			HashMap<String, FullDataPointIdMap.Entry> dataPointEntryBySerialization = new HashMap<>();
			
			for (Entry entry : this.entryList)
			{
				String entryString = entry.serialize();
				outputStream.writeUTF(entryString);
				
				if (RUN_SERIALIZATION_DUPLICATE_VALIDATION)
				{
					if (dataPointEntryBySerialization.containsKey(entryString))
					{
						LOGGER.error("Duplicate serialized entry found with serial: " + entryString);
					}
					if (dataPointEntryBySerialization.containsValue(entry))
					{
						LOGGER.error("Duplicate serialized entry found with value: " + entry.serialize());
					}
					dataPointEntryBySerialization.put(entryString, entry);
				}
			}
		}
		finally
		{
			this.readWriteLock.readLock().unlock();
			LOGGER.trace("serialize " + this.pos + " " + this.entryList.size());
		}
	}
	
	/** Creates a new IdBiomeBlockStateMap from the given UTF formatted stream */
	public static FullDataPointIdMap deserialize(DhDataInputStream inputStream, DhSectionPos pos, ILevelWrapper levelWrapper) throws IOException, InterruptedException
	{
		int entityCount = inputStream.readInt();
		
		// only used when debugging
		HashMap<String, FullDataPointIdMap.Entry> dataPointEntryBySerialization = new HashMap<>();
		
		FullDataPointIdMap newMap = new FullDataPointIdMap(pos);
		for (int i = 0; i < entityCount; i++)
		{
			String entryString = inputStream.readUTF();
			Entry newEntry = Entry.deserialize(entryString, levelWrapper);
			newMap.entryList.add(newEntry);
			
			if (RUN_SERIALIZATION_DUPLICATE_VALIDATION)
			{
				if (dataPointEntryBySerialization.containsKey(entryString))
				{
					LOGGER.error("Duplicate deserialized entry found with serial: " + entryString);
				}
				if (dataPointEntryBySerialization.containsValue(newEntry))
				{
					LOGGER.error("Duplicate deserialized entry found with value: " + newEntry.serialize());
				}
				dataPointEntryBySerialization.put(entryString, newEntry);
			}
		}
		
		LOGGER.trace("deserialized " + pos + " " + newMap.entryList.size() + "-" + entityCount);
		
		return newMap;
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public boolean equals(Object other)
	{
		if (other == this)
			return true;
/*        if (!(other instanceof FullDataPointIdMap)) return false;
		FullDataPointIdMap otherMap = (FullDataPointIdMap) other;
        if (entries.size() != otherMap.entries.size()) return false;
        for (int i=0; i<entries.size(); i++) {
            if (!entries.get(i).equals(otherMap.entries.get(i))) return false;
        }*/
		return false;
	}
	
	
	
	//==============//
	// helper class //
	//==============//
	
	private static final class Entry
	{
		private static final IWrapperFactory WRAPPER_FACTORY = SingletonInjector.INSTANCE.get(IWrapperFactory.class);
		
		public final IBiomeWrapper biome;
		public final IBlockStateWrapper blockState;
		
		private Integer hashCode = null;
		
		
		// constructor //
		
		public Entry(IBiomeWrapper biome, IBlockStateWrapper blockState)
		{
			this.biome = biome;
			this.blockState = blockState;
		}
		
		
		
		// methods //
		
		@Override
		public int hashCode()
		{
			// cache the hash code to improve speed
			if (this.hashCode == null)
			{
				this.hashCode = this.serialize().hashCode();
			}
			
			return this.hashCode;
		}
		
		@Override
		public boolean equals(Object otherObj)
		{
			if (otherObj == this)
				return true;
			
			if (!(otherObj instanceof Entry))
				return false;
			
			Entry other = (Entry) otherObj;
			return other.biome.getSerialString().equals(this.biome.getSerialString())
					&& other.blockState.getSerialString().equals(this.blockState.getSerialString());
		}
		
		@Override
		public String toString() { return this.serialize(); }
		
		
		
		public String serialize() { return this.biome.getSerialString() + BLOCK_STATE_SEPARATOR_STRING + this.blockState.getSerialString(); }
		
		public static Entry deserialize(String str, ILevelWrapper levelWrapper) throws IOException, InterruptedException
		{
			String[] stringArray = str.split(BLOCK_STATE_SEPARATOR_STRING);
			if (stringArray.length != 2)
			{
				throw new IOException("Failed to deserialize BiomeBlockStateEntry");
			}
			
			// necessary to prevent issues with deserializing objects after the level has been closed
			if (Thread.interrupted())
			{
				throw new InterruptedException(FullDataPointIdMap.class.getSimpleName() + " task interrupted.");
			}
			
			IBiomeWrapper biome = WRAPPER_FACTORY.deserializeBiomeWrapper(stringArray[0], levelWrapper);
			IBlockStateWrapper blockState = WRAPPER_FACTORY.deserializeBlockStateWrapper(stringArray[1], levelWrapper);
			return new Entry(biome, blockState);
		}
		
	}
	
	
}
