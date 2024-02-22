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

package com.seibel.distanthorizons.api.methods.events.sharedParameterObjects;

import com.seibel.distanthorizons.coreapi.util.math.Mat4f;

/**
 * Contains information relevant to Distant Horizons and Minecraft rendering.
 *
 * @author James Seibel
 * @version 2022-9-5
 * @since API 1.0.0
 */
public class DhApiRenderParam
{
	/** The projection matrix Minecraft is using to render this frame. */
	public final Mat4f mcProjectionMatrix;
	/** The model view matrix Minecraft is using to render this frame. */
	public final Mat4f mcModelViewMatrix;
	
	/** The projection matrix Distant Horizons is using to render this frame. */
	public final Mat4f dhProjectionMatrix;
	/** The model view matrix Distant Horizons is using to render this frame. */
	public final Mat4f dhModelViewMatrix;
	
	/** Indicates how far into this tick the frame is. */
	public final float partialTicks;
	
	
	
	public DhApiRenderParam(
			Mat4f newMcProjectionMatrix, Mat4f newMcModelViewMatrix,
			Mat4f newDhProjectionMatrix, Mat4f newDhModelViewMatrix,
			float newPartialTicks)
	{
		this.mcProjectionMatrix = newMcProjectionMatrix;
		this.mcModelViewMatrix = newMcModelViewMatrix;
		
		this.dhProjectionMatrix = newDhProjectionMatrix;
		this.dhModelViewMatrix = newDhModelViewMatrix;
		
		this.partialTicks = newPartialTicks;
	}
	
}
