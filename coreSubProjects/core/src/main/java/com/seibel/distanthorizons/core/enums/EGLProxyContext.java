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

package com.seibel.distanthorizons.core.enums;

/**
 * Minecraft, 		<br>
 * Lod_Builder,		<br>
 * Proxy_Worker,	<br>
 * None				<br>
 *
 * @author James Seibel
 * @version 10-1-2021
 */
public enum EGLProxyContext
{
	/** Minecraft's render thread */
	MINECRAFT,
	
	/** The context we send buffers to the GPU on */
	LOD_BUILDER,
	
	/** A context that can be used for miscellaneous tasks, owned by the GLProxy */
	PROXY_WORKER,
	
	/** used to un-bind threads */
	NONE,
}