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

package com.seibel.distanthorizons.core;

import com.seibel.distanthorizons.core.file.DataSourceReferenceTracker;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.core.world.DhApiWorldProxy;
import com.seibel.distanthorizons.core.api.external.methods.config.DhApiConfig;
import com.seibel.distanthorizons.core.api.external.methods.data.DhApiTerrainDataRepo;
import com.seibel.distanthorizons.core.dataObjects.fullData.loader.CompleteFullDataSourceLoader;
import com.seibel.distanthorizons.core.dataObjects.fullData.loader.HighDetailIncompleteFullDataSourceLoader;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.core.dataObjects.fullData.loader.LowDetailIncompleteFullDataSourceLoader;
import com.seibel.distanthorizons.core.render.DhApiRenderProxy;
//import io.netty.buffer.ByteBuf;
import net.jpountz.lz4.LZ4Compressor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Handles first time Core setup. */
public class Initializer
{
	private static final Logger LOGGER = LogManager.getLogger(ModInfo.NAME + "-" + Initializer.class.getSimpleName());
	
	public static void init()
	{
		// confirm that all referenced libraries are available to use
		try
		{
			// if any library isn't present in the jar its class
			// will throw an error (not an exception)
			Class<?> compressor = LZ4Compressor.class;
			//Class<?> networking = ByteBuf.class;
			Class<?> toml = com.electronwill.nightconfig.core.Config.class;
		}
		catch (NoClassDefFoundError e)
		{
			LOGGER.fatal("Critical programmer error: One or more libraries aren't present. Error: [" + e.getMessage() + "].");
			throw e;
		}
		
		
		
		CompleteFullDataSourceLoader unused2 = new CompleteFullDataSourceLoader(); // Auto register into the loader system
		HighDetailIncompleteFullDataSourceLoader unused3 = new HighDetailIncompleteFullDataSourceLoader(); // Auto register
		LowDetailIncompleteFullDataSourceLoader unused4 = new LowDetailIncompleteFullDataSourceLoader(); // Auto register
		
		// link Core's config to the API
		DhApi.Delayed.configs = DhApiConfig.INSTANCE;
		DhApi.Delayed.terrainRepo = DhApiTerrainDataRepo.INSTANCE;
		DhApi.Delayed.worldProxy = DhApiWorldProxy.INSTANCE;
		DhApi.Delayed.renderProxy = DhApiRenderProxy.INSTANCE;
		
		DataSourceReferenceTracker.startGarbageCollectorBackgroundThread();
		
	}
	
}
