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

package com.seibel.distanthorizons.core.config.eventHandlers.presets;

import com.seibel.distanthorizons.api.enums.config.EHorizontalQuality;
import com.seibel.distanthorizons.api.enums.config.EMaxHorizontalResolution;
import com.seibel.distanthorizons.api.enums.config.EVerticalQuality;
import com.seibel.distanthorizons.api.enums.config.quickOptions.EQualityPreset;
import com.seibel.distanthorizons.api.enums.rendering.ETransparency;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.ConfigEntryWithPresetOptions;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.coreapi.interfaces.config.IConfigEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class RenderQualityPresetConfigEventHandler extends AbstractPresetConfigEventHandler<EQualityPreset>
{
	public static final RenderQualityPresetConfigEventHandler INSTANCE = new RenderQualityPresetConfigEventHandler();
	
	private static final Logger LOGGER = LogManager.getLogger();
	
	
	private final ConfigEntryWithPresetOptions<EQualityPreset, EMaxHorizontalResolution> drawResolution = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.Graphics.Quality.maxHorizontalResolution,
			new HashMap<EQualityPreset, EMaxHorizontalResolution>()
			{{
				this.put(EQualityPreset.MINIMUM, EMaxHorizontalResolution.TWO_BLOCKS);
				this.put(EQualityPreset.LOW, EMaxHorizontalResolution.BLOCK);
				this.put(EQualityPreset.MEDIUM, EMaxHorizontalResolution.BLOCK);
				this.put(EQualityPreset.HIGH, EMaxHorizontalResolution.BLOCK);
				this.put(EQualityPreset.EXTREME, EMaxHorizontalResolution.BLOCK);
			}});
	private final ConfigEntryWithPresetOptions<EQualityPreset, EVerticalQuality> verticalQuality = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.Graphics.Quality.verticalQuality,
			new HashMap<EQualityPreset, EVerticalQuality>()
			{{
				this.put(EQualityPreset.MINIMUM, EVerticalQuality.HEIGHT_MAP);
				this.put(EQualityPreset.LOW, EVerticalQuality.LOW);
				this.put(EQualityPreset.MEDIUM, EVerticalQuality.MEDIUM);
				this.put(EQualityPreset.HIGH, EVerticalQuality.HIGH);
				this.put(EQualityPreset.EXTREME, EVerticalQuality.EXTREME);
			}});
	private final ConfigEntryWithPresetOptions<EQualityPreset, EHorizontalQuality> horizontalQuality = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.Graphics.Quality.horizontalQuality,
			new HashMap<EQualityPreset, EHorizontalQuality>()
			{{
				this.put(EQualityPreset.MINIMUM, EHorizontalQuality.LOWEST);
				this.put(EQualityPreset.LOW, EHorizontalQuality.LOW);
				this.put(EQualityPreset.MEDIUM, EHorizontalQuality.MEDIUM);
				this.put(EQualityPreset.HIGH, EHorizontalQuality.HIGH);
				this.put(EQualityPreset.EXTREME, EHorizontalQuality.EXTREME);
			}});
	private final ConfigEntryWithPresetOptions<EQualityPreset, ETransparency> transparency = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.Graphics.Quality.transparency,
			new HashMap<EQualityPreset, ETransparency>()
			{{
				this.put(EQualityPreset.MINIMUM, ETransparency.DISABLED);
				this.put(EQualityPreset.LOW, ETransparency.DISABLED); // should be fake if/when fake is fixed
				this.put(EQualityPreset.MEDIUM, ETransparency.COMPLETE);
				this.put(EQualityPreset.HIGH, ETransparency.COMPLETE);
				this.put(EQualityPreset.EXTREME, ETransparency.COMPLETE);
			}});
	private final ConfigEntryWithPresetOptions<EQualityPreset, Boolean> ssaoEnabled = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.Graphics.Ssao.enabled,
			new HashMap<EQualityPreset, Boolean>()
			{{
				this.put(EQualityPreset.MINIMUM, false);
				this.put(EQualityPreset.LOW, false);
				this.put(EQualityPreset.MEDIUM, true);
				this.put(EQualityPreset.HIGH, true);
				this.put(EQualityPreset.EXTREME, true);
			}});
		
	
	
	//==============//
	// constructors //
	//==============//
	
	/** private since we only ever need one handler at a time */
	private RenderQualityPresetConfigEventHandler()
	{
		// add each config used by this preset
		this.configList.add(this.drawResolution);
		this.configList.add(this.verticalQuality);
		this.configList.add(this.horizontalQuality);
		this.configList.add(this.transparency);
		this.configList.add(this.ssaoEnabled);
		
		
		for (ConfigEntryWithPresetOptions<EQualityPreset, ?> config : this.configList)
		{
			// ignore try-using, the listener should only ever be added once and should never be removed
			new ConfigChangeListener<>(config.configEntry, (val) -> { this.onConfigValueChanged(); });
		}
	}
	
	
	
	//==============//
	// enum getters //
	//==============//
	
	@Override
	protected IConfigEntry<EQualityPreset> getPresetConfigEntry() { return Config.Client.qualityPresetSetting; }
	
	@Override
	protected List<EQualityPreset> getPresetEnumList() { return Arrays.asList(EQualityPreset.values()); }
	@Override
	protected EQualityPreset getCustomPresetEnum() { return EQualityPreset.CUSTOM; }
	
}
