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

import com.seibel.distanthorizons.api.enums.rendering.EFogFalloff;
import com.seibel.distanthorizons.api.enums.rendering.EHeightFogMixMode;
import com.seibel.distanthorizons.api.enums.rendering.EHeightFogMode;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.interfaces.config.client.IDhApiHeightFogConfig;
import com.seibel.distanthorizons.api.objects.config.DhApiConfigValue;
import com.seibel.distanthorizons.core.config.Config;

public class DhApiHeightFogConfig implements IDhApiHeightFogConfig
{
	public static DhApiHeightFogConfig INSTANCE = new DhApiHeightFogConfig();
	
	private DhApiHeightFogConfig() { }
	
	
	
	@Override
	public IDhApiConfigValue<EHeightFogMixMode> heightFogMixMode()
	{ return new DhApiConfigValue<EHeightFogMixMode, EHeightFogMixMode>(Config.Client.Advanced.Graphics.Fog.AdvancedFog.HeightFog.heightFogMixMode); }
	
	@Override
	public IDhApiConfigValue<EHeightFogMode> heightFogMode()
	{ return new DhApiConfigValue<EHeightFogMode, EHeightFogMode>(Config.Client.Advanced.Graphics.Fog.AdvancedFog.HeightFog.heightFogMode); }
	
	@Override
	public IDhApiConfigValue<Double> heightFogBaseHeight()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.AdvancedFog.HeightFog.heightFogBaseHeight); }
	
	@Override
	public IDhApiConfigValue<Double> heightFogStartingHeightPercent()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.AdvancedFog.HeightFog.heightFogStart); }
	
	@Override
	public IDhApiConfigValue<Double> heightFogEndingHeightPercent()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.AdvancedFog.HeightFog.heightFogEnd); }
	
	@Override
	public IDhApiConfigValue<Double> heightFogMinThickness()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.AdvancedFog.HeightFog.heightFogMin); }
	
	@Override
	public IDhApiConfigValue<Double> heightFogMaxThickness()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.AdvancedFog.HeightFog.heightFogMax); }
	
	@Override
	public IDhApiConfigValue<EFogFalloff> heightFogFalloff()
	{ return new DhApiConfigValue<EFogFalloff, EFogFalloff>(Config.Client.Advanced.Graphics.Fog.AdvancedFog.HeightFog.heightFogFalloff); }
	
	@Override
	public IDhApiConfigValue<Double> heightFogDensity()
	{ return new DhApiConfigValue<Double, Double>(Config.Client.Advanced.Graphics.Fog.AdvancedFog.HeightFog.heightFogDensity); }
	
}
