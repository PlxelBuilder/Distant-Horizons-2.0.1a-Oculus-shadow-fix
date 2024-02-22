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

package com.seibel.distanthorizons.core.api.external.methods.config.client;

import com.seibel.distanthorizons.api.enums.config.*;
import com.seibel.distanthorizons.api.enums.rendering.ETransparency;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiAmbientOcclusionConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiFogConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiGraphicsConfig;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiNoiseTextureConfig;
import com.seibel.distanthorizons.api.objects.config.DhApiConfigValue;
import com.seibel.distanthorizons.api.enums.rendering.ERendererMode;
import com.seibel.distanthorizons.core.config.Config;

public class DhApiGraphicsConfig implements IDhApiGraphicsConfig
{
	public static DhApiGraphicsConfig INSTANCE = new DhApiGraphicsConfig();
	
	private DhApiGraphicsConfig() { }
	
	
	
	//==============//
	// inner layers //
	//==============//
	
	public IDhApiFogConfig fog() { return DhApiFogConfig.INSTANCE; }
	public IDhApiAmbientOcclusionConfig ambientOcclusion() { return DhApiAmbientOcclusionConfig.INSTANCE; }
	public IDhApiNoiseTextureConfig noiseTexture() { return DhApiNoiseTextureConfig.INSTANCE; }
	
	
	
	//========================//
	// basic graphic settings //
	//========================//
	
	@Override
	public IDhApiConfigValue<Integer> chunkRenderDistance()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Client.Advanced.Graphics.Quality.lodChunkRenderDistanceRadius); }
	
	@Override
	public IDhApiConfigValue<Boolean> renderingEnabled()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.quickEnableRendering); }
	
	@Override
	public IDhApiConfigValue<ERendererMode> renderingMode()
	{ return new DhApiConfigValue<ERendererMode, ERendererMode>(Config.Client.Advanced.Debugging.rendererMode); }
	
	
	
	//==================//
	// graphic settings //
	//==================//
	
	@Override
	public IDhApiConfigValue<EMaxHorizontalResolution> maxHorizontalResolution()
	{ return new DhApiConfigValue<EMaxHorizontalResolution, EMaxHorizontalResolution>(Config.Client.Advanced.Graphics.Quality.maxHorizontalResolution); }
	
	@Override
	public IDhApiConfigValue<EVerticalQuality> verticalQuality()
	{ return new DhApiConfigValue<EVerticalQuality, EVerticalQuality>(Config.Client.Advanced.Graphics.Quality.verticalQuality); }
	
	@Override
	public IDhApiConfigValue<EHorizontalQuality> horizontalQuality()
	{ return new DhApiConfigValue<EHorizontalQuality, EHorizontalQuality>(Config.Client.Advanced.Graphics.Quality.horizontalQuality); }
	
	@Override
	public IDhApiConfigValue<ETransparency> transparency()
	{ return new DhApiConfigValue<ETransparency, ETransparency>(Config.Client.Advanced.Graphics.Quality.transparency); }
	
	@Override
	public IDhApiConfigValue<EBlocksToAvoid> blocksToAvoid()
	{ return new DhApiConfigValue<EBlocksToAvoid, EBlocksToAvoid>(Config.Client.Advanced.Graphics.Quality.blocksToIgnore); }
	
	@Override
	public IDhApiConfigValue<Boolean> tintWithAvoidedBlocks()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.Quality.tintWithAvoidedBlocks); }
	
	// TODO re-implement
//	@Override
//	public IDhApiConfigValue<Integer> getBiomeBlending()
//	{ return new DhApiConfigValue<Integer, Integer>(Quality.lodBiomeBlending); }
	
	
	
	//===========================//
	// advanced graphic settings //
	//===========================//

//	@Override
//	public IDhApiConfigValue<Boolean> getDisableDirectionalCulling()
//	{ return new DhApiConfigValue<Boolean, Boolean>(AdvancedGraphics.disableDirectionalCulling); }
	
	@Override
	public IDhApiConfigValue<EOverdrawPrevention> overdrawPrevention()
	{ return new DhApiConfigValue<EOverdrawPrevention, EOverdrawPrevention>(Config.Client.Advanced.Graphics.AdvancedGraphics.overdrawPrevention); }
	
	@Override
	public IDhApiConfigValue<Double> brightnessMultiplier()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.AdvancedGraphics.brightnessMultiplier); }
	
	@Override
	public IDhApiConfigValue<Double> saturationMultiplier()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.AdvancedGraphics.saturationMultiplier); }
	
	@Override
	public IDhApiConfigValue<Boolean> caveCullingEnabled()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Graphics.AdvancedGraphics.enableCaveCulling); }
	
	@Override
	public IDhApiConfigValue<Integer> caveCullingHeight()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Client.Advanced.Graphics.AdvancedGraphics.caveCullingHeight); }
	
	@Override
	public IDhApiConfigValue<Integer> earthCurvatureRatio()
	{ return new DhApiConfigValue<Integer, Integer>(Config.Client.Advanced.Graphics.AdvancedGraphics.earthCurveRatio); }
	
	@Override
	public IDhApiConfigValue<Boolean> lodOnlyMode()
	{ return new DhApiConfigValue<Boolean, Boolean>(Config.Client.Advanced.Debugging.lodOnlyMode); }
	
	@Override
	public IDhApiConfigValue<Double> lodBias()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.AdvancedGraphics.lodBias); }
	
	
	
}
