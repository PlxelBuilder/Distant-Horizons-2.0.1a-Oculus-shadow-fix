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

package com.seibel.distanthorizons.core.wrapperInterfaces;

import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.worldGeneration.AbstractBatchGenerationEnvironmentWrapper;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

import java.io.IOException;
import java.util.HashSet;

/**
 * This handles creating abstract wrapper objects.
 *
 * @author James Seibel
 * @version 2022-12-5
 */
public interface IWrapperFactory extends IBindable
{
	AbstractBatchGenerationEnvironmentWrapper createBatchGenerator(IDhLevel targetLevel);
	IBiomeWrapper deserializeBiomeWrapper(String str, ILevelWrapper levelWrapper) throws IOException;
	IBlockStateWrapper deserializeBlockStateWrapper(String str, ILevelWrapper levelWrapper) throws IOException;
	IBlockStateWrapper getAirBlockStateWrapper();
	/**
	 * Returns the set of {@link IBlockStateWrapper}'s that shouldn't be rendered. <br>
	 * Generally this contains blocks like: air, barriers, light blocks, etc. 
	 */
	HashSet<IBlockStateWrapper> getRendererIgnoredBlocks(ILevelWrapper levelWrapper);
	
	/**
	 * Specifically designed to be used with the API.
	 *
	 * @throws ClassCastException with instructions on expected objects if the object couldn't be cast
	 */
	IChunkWrapper createChunkWrapper(Object[] objectArray) throws ClassCastException;
	
}
