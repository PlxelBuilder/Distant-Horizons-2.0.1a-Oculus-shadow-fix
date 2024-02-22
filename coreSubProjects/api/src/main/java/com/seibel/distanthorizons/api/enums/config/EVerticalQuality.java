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

package com.seibel.distanthorizons.api.enums.config;

import com.seibel.distanthorizons.coreapi.util.MathUtil;

/**
 * HEIGHT_MAP <br>
 * LOW <br>
 * MEDIUM <br>
 * HIGH <br>
 * EXTREME <br>
 *
 * @author Leonardo Amato
 * @version 2023-2-5
 * @since API 1.0.0
 */
public enum EVerticalQuality
{
	HEIGHT_MAP(new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}),
	LOW(new int[]{4, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1}),
	MEDIUM(new int[]{6, 4, 3, 2, 2, 1, 1, 1, 1, 1, 1}),
	HIGH(new int[]{8, 6, 4, 2, 2, 2, 2, 1, 1, 1, 1}),
	EXTREME(new int[]{16, 8, 4, 2, 2, 2, 2, 1, 1, 1, 1});
	
	/** represents how many LODs can be rendered in a single vertical slice */
	public final int[] maxVerticalData;
	
	
	
	EVerticalQuality(int[] maxVerticalData) { this.maxVerticalData = maxVerticalData; }
	
	
	
	public int calculateMaxVerticalData(byte dataDetail)
	{
		// for detail levels lower than what the enum defines, use the lowest quality item
		int index = MathUtil.clamp(0, dataDetail, this.maxVerticalData.length - 1);
		return this.maxVerticalData[index];
	}
	
}