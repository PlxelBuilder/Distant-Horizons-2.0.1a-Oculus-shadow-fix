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

package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderProxy;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import org.lwjgl.opengl.GL32;

/**
 * Used to interact with Distant Horizons' rendering systems.
 *
 * @author James Seibel
 * @version 2023-2-8
 */
public class DhApiRenderProxy implements IDhApiRenderProxy
{
	public static final DhApiRenderProxy INSTANCE = new DhApiRenderProxy();
	
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	public int targetFrameBufferOverride = -1;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private DhApiRenderProxy() { }
	
	
	
	//=========//
	// methods //
	//=========//
	
	public DhApiResult<Boolean> clearRenderDataCache()
	{
		// make sure this is a valid time to run the method
		AbstractDhWorld world = SharedApi.getAbstractDhWorld();
		if (world == null)
		{
			return DhApiResult.createFail("No world loaded");
		}
		
		
		// clear the render caches for each level
		Iterable<? extends IDhLevel> loadedLevels = world.getAllLoadedLevels();
		for (IDhLevel level : loadedLevels)
		{
			if (level instanceof IDhClientLevel)
			{
				((IDhClientLevel) level).clearRenderCache();
			}
		}
		
		return DhApiResult.createSuccess();
	}
	
	
	@Override
	public DhApiResult<Integer> getDhFrameBufferId()
	{
		int activeFrameBuffer = LodRenderer.getActiveFramebufferId();
		return (activeFrameBuffer == -1) ? DhApiResult.createFail("DH's FrameBuffer hasn't been created and/or bound yet.", -1) : DhApiResult.createSuccess(activeFrameBuffer);
	}
	
	
	@Override
	public DhApiResult<Void> setTargetFrameBufferId(int frameBufferId)
	{
		if (frameBufferId != -1 && !GL32.glIsFramebuffer(frameBufferId))
		{
			return DhApiResult.createFail("FrameBufferID ["+frameBufferId+"] isn't a valid FrameBuffer ID.");
		}
		
		this.targetFrameBufferOverride = frameBufferId;
		return DhApiResult.createSuccess();
	}
	@Override
	public DhApiResult<Integer> getTargetFrameBufferId() { return (this.targetFrameBufferOverride == -1) ? DhApiResult.createSuccess("Default MC FrameBuffer in use.", MC_RENDER.getTargetFrameBuffer()) : DhApiResult.createSuccess("FrameBuffer override active.", this.targetFrameBufferOverride); }
	
	
	@Override
	public DhApiResult<Integer> getDhDepthTextureId()
	{
		int activeTexture = LodRenderer.getActiveDepthTextureId();
		return (activeTexture == -1) ? DhApiResult.createFail("DH's depth texture hasn't been created and/or bound yet.", -1) : DhApiResult.createSuccess(activeTexture);
	}
	@Override
	public DhApiResult<Integer> getDhColorTextureId()
	{
		int activeTexture = LodRenderer.getActiveColorTextureId();
		return (activeTexture == -1) ? DhApiResult.createFail("DH's color texture hasn't been created and/or bound yet.", -1) : DhApiResult.createSuccess(activeTexture);
	}
	
}
