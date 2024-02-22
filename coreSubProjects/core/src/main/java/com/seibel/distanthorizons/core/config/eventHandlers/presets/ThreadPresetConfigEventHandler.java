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

import com.seibel.distanthorizons.api.enums.config.quickOptions.EThreadPreset;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.ConfigEntryWithPresetOptions;
import com.seibel.distanthorizons.coreapi.interfaces.config.IConfigEntry;
import com.seibel.distanthorizons.coreapi.util.MathUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ThreadPresetConfigEventHandler extends AbstractPresetConfigEventHandler<EThreadPreset>
{
	public static final ThreadPresetConfigEventHandler INSTANCE = new ThreadPresetConfigEventHandler();
	
	private static final Logger LOGGER = LogManager.getLogger();
	private static final boolean LOW_THREAD_COUNT_CPU = (Runtime.getRuntime().availableProcessors() <= 4);
	
	
	
	public static int getWorldGenDefaultThreadCount() { return getThreadCountByPercent(0.15); }
	private final ConfigEntryWithPresetOptions<EThreadPreset, Integer> worldGenThreadCount = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.MultiThreading.numberOfWorldGenerationThreads,
			new HashMap<EThreadPreset, Integer>()
			{{
				this.put(EThreadPreset.MINIMAL_IMPACT, 1);
				this.put(EThreadPreset.LOW_IMPACT, getWorldGenDefaultThreadCount());
				this.put(EThreadPreset.BALANCED, getThreadCountByPercent(0.25));
				this.put(EThreadPreset.AGGRESSIVE, getThreadCountByPercent(0.5));
				this.put(EThreadPreset.I_PAID_FOR_THE_WHOLE_CPU, getThreadCountByPercent(1.0));
			}});
	public static double getWorldGenDefaultRunTimeRatio() { return LOW_THREAD_COUNT_CPU ? 0.5 : 0.75; }
	private final ConfigEntryWithPresetOptions<EThreadPreset, Double> worldGenRunTime = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.MultiThreading.runTimeRatioForWorldGenerationThreads,
			new HashMap<EThreadPreset, Double>()
			{{
				this.put(EThreadPreset.MINIMAL_IMPACT, LOW_THREAD_COUNT_CPU ? 0.1 : 0.25);
				this.put(EThreadPreset.LOW_IMPACT, getWorldGenDefaultRunTimeRatio());
				this.put(EThreadPreset.BALANCED, LOW_THREAD_COUNT_CPU ? 0.5 : 0.75);
				this.put(EThreadPreset.AGGRESSIVE, LOW_THREAD_COUNT_CPU ? 0.75 : 1.0);
				this.put(EThreadPreset.I_PAID_FOR_THE_WHOLE_CPU, 1.0);
			}});
	
	
	public static int getFileHandlerDefaultThreadCount() { return getThreadCountByPercent(0.1); }
	private final ConfigEntryWithPresetOptions<EThreadPreset, Integer> fileHandlerThreadCount = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.MultiThreading.numberOfFileHandlerThreads,
			new HashMap<EThreadPreset, Integer>()
			{{
				this.put(EThreadPreset.MINIMAL_IMPACT, 1);
				this.put(EThreadPreset.LOW_IMPACT, getFileHandlerDefaultThreadCount());
				this.put(EThreadPreset.BALANCED, getThreadCountByPercent(0.2));
				this.put(EThreadPreset.AGGRESSIVE, getThreadCountByPercent(0.2));
				this.put(EThreadPreset.I_PAID_FOR_THE_WHOLE_CPU, getThreadCountByPercent(1.0));
			}});
	public static double getFileHandlerDefaultRunTimeRatio() { return 0.5; }
	private final ConfigEntryWithPresetOptions<EThreadPreset, Double> fileHandlerRunTime = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.MultiThreading.runTimeRatioForFileHandlerThreads,
			new HashMap<EThreadPreset, Double>()
			{{
				this.put(EThreadPreset.MINIMAL_IMPACT, 0.25);
				this.put(EThreadPreset.LOW_IMPACT, getFileHandlerDefaultRunTimeRatio());
				this.put(EThreadPreset.BALANCED, 0.75);
				this.put(EThreadPreset.AGGRESSIVE, 1.0);
				this.put(EThreadPreset.I_PAID_FOR_THE_WHOLE_CPU, 1.0);
			}});
	
	
	public static int getLodBuilderDefaultThreadCount() { return getThreadCountByPercent(0.1); }
	private final ConfigEntryWithPresetOptions<EThreadPreset, Integer> lodBuilderThreadCount = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.MultiThreading.numberOfLodBuilderThreads,
			new HashMap<EThreadPreset, Integer>()
			{{
				this.put(EThreadPreset.MINIMAL_IMPACT, 1);
				this.put(EThreadPreset.LOW_IMPACT, getLodBuilderDefaultThreadCount());
				this.put(EThreadPreset.BALANCED, getThreadCountByPercent(0.2));
				this.put(EThreadPreset.AGGRESSIVE, getThreadCountByPercent(0.4));
				this.put(EThreadPreset.I_PAID_FOR_THE_WHOLE_CPU, getThreadCountByPercent(1.0));
			}});
	public static double getLodBuilderDefaultRunTimeRatio() { return LOW_THREAD_COUNT_CPU ? 0.25 : 0.5; }
	private final ConfigEntryWithPresetOptions<EThreadPreset, Double> lodBuilderRunTime = new ConfigEntryWithPresetOptions<>(Config.Client.Advanced.MultiThreading.runTimeRatioForLodBuilderThreads,
			new HashMap<EThreadPreset, Double>()
			{{
				this.put(EThreadPreset.MINIMAL_IMPACT, 0.1);
				this.put(EThreadPreset.LOW_IMPACT, getLodBuilderDefaultRunTimeRatio());
				this.put(EThreadPreset.BALANCED, LOW_THREAD_COUNT_CPU ? 0.5 : 0.75);
				this.put(EThreadPreset.AGGRESSIVE, 1.0);
				this.put(EThreadPreset.I_PAID_FOR_THE_WHOLE_CPU, 1.0);
			}});
	
	
	
	//==============//
	// constructors //
	//==============//
	
	/** private since we only ever need one handler at a time */
	private ThreadPresetConfigEventHandler()
	{
		// add each config used by this preset
		this.configList.add(this.worldGenThreadCount);
		this.configList.add(this.worldGenRunTime);
		
		this.configList.add(this.fileHandlerThreadCount);
		this.configList.add(this.fileHandlerRunTime);
		
		this.configList.add(this.lodBuilderThreadCount);
		this.configList.add(this.lodBuilderRunTime);
		
		
		for (ConfigEntryWithPresetOptions<EThreadPreset, ?> config : this.configList)
		{
			// ignore try-using, the listeners should only ever be added once and should never be removed
			new ConfigChangeListener<>(config.configEntry, (val) -> { this.onConfigValueChanged(); });
		}
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	/**
	 * Pre-computed values for your convenience: <br>
	 * Format: percent: 4coreCpu-8coreCpu-16coreCpu <br><br>
	 * <code>
	 * 0.1: 1-1-2	<br>
	 * 0.2: 1-2-4	<br>
	 * 0.4: 2-4-7	<br>
	 * 0.6: 3-5-10	<br>
	 * 0.8: 4-7-13	<br>
	 * 1.0: 4-8-16	<br>
	 * </code>
	 */
	private static int getThreadCountByPercent(double percent) throws IllegalArgumentException
	{
		if (percent <= 0 || percent > 1)
		{
			throw new IllegalArgumentException("percent must be greater than 0 and less than or equal to 1.");
		}
		
		// this is logical processor count, not physical CPU cores
		int totalProcessorCount = Runtime.getRuntime().availableProcessors();
		int coreCount = (int) Math.ceil(totalProcessorCount * percent);
		return MathUtil.clamp(1, coreCount, totalProcessorCount);
	}
	
	
	
	//==============//
	// enum getters //
	//==============//
	
	@Override
	protected IConfigEntry<EThreadPreset> getPresetConfigEntry() { return Config.Client.threadPresetSetting; }
	
	@Override
	protected List<EThreadPreset> getPresetEnumList() { return Arrays.asList(EThreadPreset.values()); }
	@Override
	protected EThreadPreset getCustomPresetEnum() { return EThreadPreset.CUSTOM; }
	
}
