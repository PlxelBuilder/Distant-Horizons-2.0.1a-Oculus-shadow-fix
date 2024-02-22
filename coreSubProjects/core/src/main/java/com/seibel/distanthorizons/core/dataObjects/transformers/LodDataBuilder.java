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

package com.seibel.distanthorizons.core.dataObjects.transformers;

import com.seibel.distanthorizons.core.dataObjects.fullData.accessor.ChunkSizedFullDataAccessor;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.IWrapperFactory;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.Logger;

public class LodDataBuilder
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IBlockStateWrapper AIR = SingletonInjector.INSTANCE.get(IWrapperFactory.class).getAirBlockStateWrapper();
	
	private static boolean getTopErrorLogged = false;
	
	
	public static ChunkSizedFullDataAccessor createChunkData(IChunkWrapper chunkWrapper)
	{
		if (!canGenerateLodFromChunk(chunkWrapper))
		{
			return null;
		}
		
		
		ChunkSizedFullDataAccessor chunkData = new ChunkSizedFullDataAccessor(chunkWrapper.getChunkPos());
		int minBuildHeight = chunkWrapper.getMinFilledHeight();
		
		for (int x = 0; x < LodUtil.CHUNK_WIDTH; x++)
		{
			for (int z = 0; z < LodUtil.CHUNK_WIDTH; z++)
			{
				LongArrayList longs = new LongArrayList(chunkWrapper.getHeight() / 4);
				int lastY = chunkWrapper.getMaxBuildHeight();
				IBiomeWrapper biome = chunkWrapper.getBiome(x, lastY, z);
				IBlockStateWrapper blockState = AIR;
				int mappedId = chunkData.getMapping().addIfNotPresentAndGetId(biome, blockState);
				// FIXME: The +1 offset to reproduce the old behavior. Remove this when we get per-face lighting
				byte light = (byte) ((chunkWrapper.getBlockLight(x, lastY + 1, z) << 4) + chunkWrapper.getSkyLight(x, lastY + 1, z));
				
				
				// determine the starting Y Pos
				int y = chunkWrapper.getLightBlockingHeightMapValue(x,z);
				// go up until we reach open air or the world limit
				IBlockStateWrapper topBlockState = chunkWrapper.getBlockState(x, y, z);
				while (!topBlockState.isAir() && y < chunkWrapper.getMaxBuildHeight())
				{
					try
					{
						// This is necessary in some edge cases with snow layers and some other blocks that may not appear in the height map but do block light.
						// Interestingly this doesn't appear to be the case in the DhLightingEngine, if this same logic is added there the lighting breaks for the affected blocks.
						y++;
						topBlockState = chunkWrapper.getBlockState(x, y, z);
					}
					catch (Exception e)
					{
						if (!getTopErrorLogged)
						{
							LOGGER.warn("Unexpected issue in LodDataBuilder, future errors won't be logged. Chunk [" + chunkWrapper.getChunkPos() + "] with max height: [" + chunkWrapper.getMaxBuildHeight() + "] had issue getting block at pos [" + x + "," + y + "," + z + "] error: " + e.getMessage(), e);
							getTopErrorLogged = true;
						}
						
						y--;
						break;
					}
				}
				
				
				for (; y >= minBuildHeight; y--)
				{
					IBiomeWrapper newBiome = chunkWrapper.getBiome(x, y, z);
					IBlockStateWrapper newBlockState = chunkWrapper.getBlockState(x, y, z);
					byte newLight = (byte) ((chunkWrapper.getBlockLight(x, y + 1, z) << 4) + chunkWrapper.getSkyLight(x, y + 1, z));
					
					if (!newBiome.equals(biome) || !newBlockState.equals(blockState))
					{
						longs.add(FullDataPointUtil.encode(mappedId, lastY - y, y + 1 - chunkWrapper.getMinBuildHeight(), light));
						biome = newBiome;
						blockState = newBlockState;
						mappedId = chunkData.getMapping().addIfNotPresentAndGetId(biome, blockState);
						light = newLight;
						lastY = y;
					}
//                    else if (newLight != light) {
//                        longs.add(FullFormat.encode(mappedId, lastY-y, y+1 - chunk.getMinBuildHeight(), light));
//                        light = newLight;
//                        lastY = y;
//                    }
				}
				longs.add(FullDataPointUtil.encode(mappedId, lastY - y, y + 1 - chunkWrapper.getMinBuildHeight(), light));
				
				chunkData.setSingleColumn(longs.toLongArray(), x, z);
			}
		}
		if (!canGenerateLodFromChunk(chunkWrapper)) return null;
		LodUtil.assertTrue(chunkData.emptyCount() == 0);
		return chunkData;
	}
	
	public static boolean canGenerateLodFromChunk(IChunkWrapper chunk)
	{
		//return true;
		return chunk != null && chunk.isLightCorrect(); // TODO client only chunks return chunks with bad lighting, preventing chunk building (or transparent only chunks)
	}
	
}
