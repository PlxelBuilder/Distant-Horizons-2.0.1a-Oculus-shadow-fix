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

package com.seibel.distanthorizons.core.file.metaData;

import java.io.*;
import java.nio.channels.ClosedChannelException;
import java.nio.file.*;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.AbstractDhRepo;
import com.seibel.distanthorizons.core.sql.MetaDataDto;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.dataStreams.DhDataOutputStream;
import org.apache.logging.log4j.Logger;

/**
 * This represents the data appended to any file we write. <br>
 * Contains a {@link BaseMetaData} which holds most of the necessary values written to the file. <br><br>
 *
 * Used size: 40 bytes <br>
 * Remaining space: 24 bytes <br>
 * Total size: 64 bytes <br><br><br>
 *
 *
 * <Strong>Metadata format: </Strong><br><br>
 * <code>
 * 4 bytes: metadata identifier bytes: "DHv0" (in ascii: 0x44 48 76 30) this signals the file is in the metadata format <br>
 * 4 bytes: section X position <br>
 * 4 bytes: section Y position (Unused, for future proofing) <br>
 * 4 bytes: section Z position <br> <br>
 *
 * 4 bytes: data checksum <br>
 * 1 byte: section detail level <br>
 * 1 byte: data detail level // Note: not sure if this is needed <br>
 * 1 byte: loader version <br>
 * 1 byte: unused <br> <br>
 *
 * 8 bytes: datatype identifier <br> <br>
 *
 * 8 bytes: data version
 * </code>
 */
public abstract class AbstractMetaDataContainerFile
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	/**
	 * Will be null if no file exists for this object. <br>
	 * NOTE: Only use {@link BaseMetaData#pos} when initially setting up this object, afterwards the standalone {@link AbstractMetaDataContainerFile#pos} should be used.
	 */
	public volatile BaseMetaData baseMetaData = null;
	
	/** Should be used instead of the position inside {@link AbstractMetaDataContainerFile#baseMetaData} */
	public final DhSectionPos pos;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	/**
	 * Create a metaFile in this path.
	 *
	 * @throws FileAlreadyExistsException If the path already has a file.
	 */
	protected AbstractMetaDataContainerFile(DhSectionPos pos) { this.pos = pos; }
	
	/**
	 * Creates an {@link AbstractMetaDataContainerFile} with the file at the given path.
	 *
	 * @throws IOException if the file was formatted incorrectly
	 * @throws FileNotFoundException if no file exists for the given path
	 */
	protected AbstractMetaDataContainerFile(BaseMetaData baseMetaData) throws IOException
	{
		this.baseMetaData = baseMetaData;
		this.pos = this.baseMetaData.pos;
	}
	
	
	//==============//
	// file writing //
	//==============//
	
	public void writeToDatabase(IMetaDataWriterFunc<DhDataOutputStream> dataWriterFunc, AbstractDhRepo<MetaDataDto> repo) throws IOException
	{
		LodUtil.assertTrue(this.baseMetaData != null);
		
		try
		{
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			
			// the order of these streams is important, otherwise the checksum won't be calculated
			CheckedOutputStream checkedOut = new CheckedOutputStream(byteArrayOutputStream, new Adler32());
			// normally a DhStream should be the topmost stream to prevent closing the stream accidentally, but since this stream will be closed immediately after writing anyway, it won't be an issue
			DhDataOutputStream compressedOut = new DhDataOutputStream(checkedOut);
			
			// write the contained data
			dataWriterFunc.writeBinaryDataToStream(compressedOut);
			compressedOut.flush();
			this.baseMetaData.checksum = (int) checkedOut.getChecksum().getValue();
			
			
			byteArrayOutputStream.close();
			
			
			MetaDataDto dto = new MetaDataDto(this.baseMetaData, byteArrayOutputStream.toByteArray());
			repo.save(dto);
		}
		catch (ClosedChannelException e) // includes ClosedByInterruptException
		{
			// expected if the file handler is shut down, the exception can be ignored
			//LOGGER.warn(AbstractMetaDataContainerFile.class.getSimpleName()+" file writing interrupted. Error: "+e.getMessage());
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	@FunctionalInterface
	public interface IMetaDataWriterFunc<T> { void writeBinaryDataToStream(T t) throws IOException; }
	
}
