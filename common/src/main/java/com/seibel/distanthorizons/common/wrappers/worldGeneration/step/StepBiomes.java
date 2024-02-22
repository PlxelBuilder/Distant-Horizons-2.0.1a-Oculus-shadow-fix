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

package com.seibel.distanthorizons.common.wrappers.worldGeneration.step;

import java.util.ArrayList;
import java.util.List;

import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.ThreadedParameters;

import net.minecraft.server.level.WorldGenRegion;
#if PRE_MC_1_19_2
#endif
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
#if POST_MC_1_18_2
import net.minecraft.world.level.levelgen.blending.Blender;
#endif

public final class StepBiomes
{
	public static final ChunkStatus STATUS = ChunkStatus.BIOMES;
	
	private final BatchGenerationEnvironment environment;
	
	
	public StepBiomes(BatchGenerationEnvironment batchGenerationEnvironment) { this.environment = batchGenerationEnvironment; }
	
	
	
	public void generateGroup(
			ThreadedParameters tParams, WorldGenRegion worldGenRegion,
			List<ChunkWrapper> chunkWrappers)
	{
		
		ArrayList<ChunkAccess> chunksToDo = new ArrayList<ChunkAccess>();
		
		for (ChunkWrapper chunkWrapper : chunkWrappers)
		{
			ChunkAccess chunk = chunkWrapper.getChunk();
			if (chunk.getStatus().isOrAfter(STATUS)) continue;
			((ProtoChunk) chunk).setStatus(STATUS);
			chunksToDo.add(chunk);
		}
		
		for (ChunkAccess chunk : chunksToDo)
		{
			// System.out.println("StepBiomes: "+chunk.getPos());
			#if PRE_MC_1_18_2
			environment.params.generator.createBiomes(environment.params.biomes, chunk);
			#elif PRE_MC_1_19_2
			chunk = environment.joinSync(environment.params.generator.createBiomes(environment.params.biomes, Runnable::run, Blender.of(worldGenRegion),
					tParams.structFeat.forWorldGenRegion(worldGenRegion), chunk));
			#elif PRE_MC_1_19_4
			chunk = environment.joinSync(environment.params.generator.createBiomes(environment.params.biomes, Runnable::run, environment.params.randomState, Blender.of(worldGenRegion),
					tParams.structFeat.forWorldGenRegion(worldGenRegion), chunk));
			#else
			chunk = environment.joinSync(environment.params.generator.createBiomes(Runnable::run, environment.params.randomState, Blender.of(worldGenRegion),
					tParams.structFeat.forWorldGenRegion(worldGenRegion), chunk));
			#endif
		}
	}
	
}