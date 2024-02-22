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

package com.seibel.distanthorizons.core.level;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

/**
 * Handles level overrides initiated by servers that
 * support differentiating between different levels.
 */
public interface IKeyedClientLevelManager extends IBindable
{
	/** Called when a client level is wrapped by a ServerEnhancedClientLevel, for integration into mod internals. */
	void setServerKeyedLevel(IServerKeyedClientLevel clientLevel);
	IServerKeyedClientLevel getOverrideWrapper();
	
	/** Returns a new instance of a ServerEnhancedClientLevel. */
	IServerKeyedClientLevel getServerKeyedLevel(ILevelWrapper level, String serverLevelKey);
	
	/** Sets the LOD engine to use the override wrapper, if the server has communication enabled. */
	void setUseOverrideWrapper(boolean useOverrideWrapper);
	boolean getUseOverrideWrapper();
	
}
