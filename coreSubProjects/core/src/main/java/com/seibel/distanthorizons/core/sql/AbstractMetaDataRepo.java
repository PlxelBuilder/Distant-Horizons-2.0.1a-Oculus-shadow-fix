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

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.file.metaData.BaseMetaData;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.coreapi.util.StringUtil;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

public abstract class AbstractMetaDataRepo extends AbstractDhRepo<MetaDataDto>
{
	public AbstractMetaDataRepo(String databaseType, String databaseLocation) throws SQLException
	{
		super(databaseType, databaseLocation, MetaDataDto.class);
	}
	
	
	
	@Override 
	public String getPrimaryKeyName() { return "DhSectionPos"; }
	
	
	@Override 
	public MetaDataDto convertDictionaryToDto(Map<String, Object> objectMap) throws ClassCastException
	{
		String posString = (String) objectMap.get("DhSectionPos");
		DhSectionPos pos = DhSectionPos.deserialize(posString);
		
		// meta data
		int checksum = (Integer) objectMap.get("Checksum");
		long dataVersion = (Long) objectMap.get("DataVersion");
		byte dataDetailLevel = (Byte) objectMap.get("DataDetailLevel");
		String worldGenStepString = (String) objectMap.get("WorldGenStep");
		EDhApiWorldGenerationStep worldGenStep = EDhApiWorldGenerationStep.fromName(worldGenStepString);
		
		String dataType = (String) objectMap.get("DataType");
		byte binaryDataFormatVersion = (Byte) objectMap.get("BinaryDataFormatVersion");
		
		BaseMetaData baseMetaData = new BaseMetaData(pos, 
				checksum, dataDetailLevel, worldGenStep,
				dataType, binaryDataFormatVersion, dataVersion);
		
		// binary data
		byte[] dataByteArray = (byte[]) objectMap.get("Data");
		
		MetaDataDto metaFile = new MetaDataDto(baseMetaData, dataByteArray);
		return metaFile;
	}
	
	@Override 
	public String createSelectPrimaryKeySql(String primaryKey) { return "SELECT * FROM "+this.getTableName()+" WHERE DhSectionPos = '"+primaryKey+"'"; }
	
	@Override
	public PreparedStatement createInsertStatement(MetaDataDto dto) throws SQLException
	{
		String sql =
			"INSERT INTO "+this.getTableName() + "\n" +
			"  (DhSectionPos, \n" +
			"Checksum, DataVersion, DataDetailLevel, WorldGenStep, DataType, BinaryDataFormatVersion, \n" +
			"Data) \n" +
			"   VALUES( \n" +
			"    ? \n" +
			"   ,? ,? ,? ,? ,? ,? \n" +
			"   ,? \n" +
			// created/lastModified are automatically set by Sqlite
			");";
		PreparedStatement statement = this.createPreparedStatement(sql);
		
		int i = 1;
		statement.setObject(i++, dto.getPrimaryKeyString());
		
		statement.setObject(i++, dto.baseMetaData.checksum);
		statement.setObject(i++, dto.baseMetaData.dataVersion);
		statement.setObject(i++, dto.baseMetaData.dataDetailLevel);
		statement.setObject(i++, dto.baseMetaData.worldGenStep);
		statement.setObject(i++, dto.baseMetaData.dataType);
		statement.setObject(i++, dto.baseMetaData.binaryDataFormatVersion);
		
		statement.setObject(i++, dto.dataArray);
		
		return statement;
	}
	
	@Override
	public PreparedStatement createUpdateStatement(MetaDataDto dto) throws SQLException
	{
		String sql =
			"UPDATE "+this.getTableName()+" \n" +
			"SET \n" +
			"    Checksum = ? \n" +
			"   ,DataVersion = ? \n" +
			"   ,DataDetailLevel = ? \n" +
			"   ,WorldGenStep = ? \n" +
			"   ,DataType = ? \n" +
			"   ,BinaryDataFormatVersion = ? \n" +
					
			"   ,Data = ? \n" +
			
			"   ,LastModifiedDateTime = CURRENT_TIMESTAMP \n" +
			"WHERE DhSectionPos = ?";
		PreparedStatement statement = this.createPreparedStatement(sql);
		
		int i = 1;
		statement.setObject(i++, dto.baseMetaData.checksum);
		statement.setObject(i++, dto.baseMetaData.dataVersion);
		statement.setObject(i++, dto.baseMetaData.dataDetailLevel);
		statement.setObject(i++, dto.baseMetaData.worldGenStep);
		statement.setObject(i++, dto.baseMetaData.dataType);
		statement.setObject(i++, dto.baseMetaData.binaryDataFormatVersion);
		
		statement.setObject(i++, dto.dataArray);
		
		statement.setObject(i++, dto.getPrimaryKeyString());
		
		return statement;
	}
	
	
}
