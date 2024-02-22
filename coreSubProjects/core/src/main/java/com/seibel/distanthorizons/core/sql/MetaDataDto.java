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

package com.seibel.distanthorizons.core.sql;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.interfaces.IFullDataSource;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.file.metaData.BaseMetaData;

/** handles storing both {@link IFullDataSource}'s and {@link ColumnRenderSource}'s in the database. */
public class MetaDataDto implements IBaseDTO
{
	public final BaseMetaData baseMetaData;
	public final byte[] dataArray;
	
	
	public MetaDataDto(BaseMetaData baseMetaData, byte[] dataArray)
	{
		this.baseMetaData = baseMetaData;
		this.dataArray = dataArray;
	}
	
	
	@Override
	public String getPrimaryKeyString() { return this.baseMetaData.pos.serialize(); }
	
}
