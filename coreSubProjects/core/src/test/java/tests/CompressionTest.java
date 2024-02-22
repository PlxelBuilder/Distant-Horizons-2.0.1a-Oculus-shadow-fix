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

package tests;

import net.jpountz.lz4.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.nio.file.Files;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;

//import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
//import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
//import com.github.luben.zstd.ZstdInputStream;
//import com.github.luben.zstd.ZstdOutputStream;

/**
 * Results (2023-5-20): <br>
 * 200 files <br><br>
 *
 * <strong>uncompressed</strong> <br><br>
 *
 * render data - ratio 1.0 (shocker :P) <br>
 * read time in - 784 ms, avg 3 ms/file <br>
 * write time in - 803 ms, avg 4 ms/file <br><br>
 *
 * full data - ratio 1.0 <br>
 * read time in - 2,213 ms, avg 11 ms/file <br>
 * write time in - 1,753 ms, avg 8 ms/file <br><br><br>
 *
 *
 * <strong>XZ</strong> <br><br>
 *
 * render data - ratio 0.1044 <br>
 * read time in - 2,413 ms, avg 12 ms/file <br>
 * write time in - 28,441 ms, avg 142 ms/file <br><br>
 *
 * full data - ratio 0.1123 <br>
 * read time in - 5,888 ms, avg 29 ms/file <br>
 * write time in - 79,675 ms, avg 398 ms/file <br><br><br>
 *
 *
 * <strong>LZ4</strong> <br><br>
 *
 * render data - ratio 0.2933 <br>
 * read time in - 846 ms, avg 4 ms/file <br>
 * write time in - 1,040 ms, avg 5 ms/file <br><br>
 *
 * full data - ratio 0.3275 <br>
 * read time in - 1,964 ms, avg 9 ms/file <br>
 * write time in - 1,584 ms, avg 7 ms/file <br><br><br>
 *
 *
 * <strong>Z Standard</strong> <br><br>
 *
 * render data - ratio 0.1791 <br>
 * read time in - 5,170 ms, avg 25 ms/file <br>
 * write time in - 5,294 ms, avg 26 ms/file <br><br>
 *
 * full data - ratio 0.2060 <br>
 * read time in - 14,754 ms, avg 73 ms/file <br>
 * write time in - 14,057 ms, avg 70 ms/file <br><br><br>
 *
 *
 *
 * <strong>Note:</strong>
 * In order to test the compressors that aren't currently in use: <br>
 * 1. Generate DH data and point the {@link CompressionTest#TEST_DIR} variable to the "Distant_Horizons" folder.
 * 2. Add the following to build.gradle's dependencies block: <br>
 * <code>
 * shadowMe("org.tukaani:xz:1.9")
 * shadowMe("org.apache.commons:commons-compress:1.21")
 * shadowMe("com.github.luben:zstd-jni:1.5.5-3")
 * </code><br>
 * 3. Uncomment the tests in this file <br>
 * 4. Run the tests like normal
 */
public class CompressionTest
{
	public static String TEST_DIR = "C:\\DistantHorizonsWorkspace\\distantHorizons\\fabric\\run\\saves\\Arcapelago\\data\\Distant_Horizons";
	public static String RENDER_DATA_PATH = TEST_DIR + "\\renderCache";
	public static String FULL_DATA_PATH = TEST_DIR + "\\data";
	
	/** limits the number of files tested so I don't have to wait 10 minutes for the slower compressors */
	public static int MAX_NUMBER_OF_FILES_TO_TEST = 200;
	
	
	
	//	@Test
	public void NoCompression()
	{
		String compressorName = "Uncompressed";
		
		CreateInputStreamFunc createInputStreamFunc = (inputStream) -> inputStream;
		CreateOutputStreamFunc createOutputStreamFunc = (outputStream) -> outputStream;
		
		
		System.out.println(compressorName + " testing render data");
		this.testCompressor(compressorName, RENDER_DATA_PATH, createInputStreamFunc, createOutputStreamFunc);
		System.out.println(compressorName + " testing full data");
		this.testCompressor(compressorName, FULL_DATA_PATH, createInputStreamFunc, createOutputStreamFunc);
	}
	
	//	@Test
	public void Lz4()
	{
		String compressorName = "LZ4";
		
		CreateInputStreamFunc createInputStreamFunc = (inputStream) -> new LZ4FrameInputStream(inputStream);
		CreateOutputStreamFunc createOutputStreamFunc = (outputStream) -> new LZ4FrameOutputStream(outputStream);
		
		
		System.out.println(compressorName + " testing render data");
		this.testCompressor(compressorName, RENDER_DATA_PATH, createInputStreamFunc, createOutputStreamFunc);
		System.out.println(compressorName + " testing full data");
		this.testCompressor(compressorName, FULL_DATA_PATH, createInputStreamFunc, createOutputStreamFunc);
	}

//	@Test
//	public void Zstandard()
//	{
//		String compressorName = "Z_std";
//		
//		CreateInputStreamFunc createInputStreamFunc = (inputStream) -> new ZstdInputStream(inputStream);
//		CreateOutputStreamFunc createOutputStreamFunc = (outputStream) -> new ZstdOutputStream(outputStream);
//		
//		
//		System.out.println(compressorName+" testing render data");
//		this.testCompressor(compressorName, RENDER_DATA_PATH, createInputStreamFunc, createOutputStreamFunc);
//		System.out.println(compressorName+" testing full data");
//		this.testCompressor(compressorName, FULL_DATA_PATH, createInputStreamFunc, createOutputStreamFunc);
//	}

//	@Test
//	public void Xz()
//	{
//		String compressorName = "XZ";
//		
//		CreateInputStreamFunc createInputStreamFunc = (inputStream) -> new XZCompressorInputStream(inputStream);
//		CreateOutputStreamFunc createOutputStreamFunc = (outputStream) -> new XZCompressorOutputStream(outputStream);
//		
//		
//		System.out.println(compressorName+" testing render data");
//		this.testCompressor(compressorName, RENDER_DATA_PATH, createInputStreamFunc, createOutputStreamFunc);
//		System.out.println(compressorName+" testing full data");
//		this.testCompressor(compressorName, FULL_DATA_PATH, createInputStreamFunc, createOutputStreamFunc);
//	}
	
	
	
	//=================//
	// testing methods //
	//=================//
	
	@FunctionalInterface
	public interface CreateInputStreamFunc
	{
		InputStream apply(InputStream inputStream) throws Exception;
		
	}
	
	@FunctionalInterface
	public interface CreateOutputStreamFunc
	{
		OutputStream apply(OutputStream outputStream) throws Exception;
		
	}
	
	private void testCompressor(
			String compressorName, String inputFolderPath,
			CreateInputStreamFunc createInputStreamFunc,
			CreateOutputStreamFunc createOutputStreamFunc)
	{
		long totalUncompressedFileSizeInBytes = 0;
		long totalCompressedFileSizeInBytes = 0;
		
		long totalReadTimeInMs = 0;
		long totalWriteTimeInMs = 0;
		
		try
		{
			File inputFolder = new File(inputFolderPath);
			File[] inputFileArray = inputFolder.listFiles();
			Assert.assertNotNull(inputFileArray);
			
			File compressedFolder = new File(inputFolderPath + "\\" + compressorName);
			compressedFolder.delete();
			compressedFolder.mkdirs();
			
			
			int processedFileCount = 0;
			for (File inputFile : inputFileArray)
			{
				if (inputFile.isDirectory())
				{
					continue;
				}
				
				// can be used to speed up the tests
				if (processedFileCount >= MAX_NUMBER_OF_FILES_TO_TEST)
				{
					break;
				}
				
				
				
				// uncompressed file input //
				ArrayList<Byte> originalFileByteArray = new ArrayList<>();
				totalUncompressedFileSizeInBytes += Files.size(inputFile.toPath());
				
				try (FileInputStream fileStream = new FileInputStream(inputFile);
						BufferedInputStream bufferedStream = new BufferedInputStream(fileStream);
						DataInputStream dataStream = new DataInputStream(bufferedStream))
				{
					try
					{
						while (true)
						{
							byte nextByte = dataStream.readByte();
							originalFileByteArray.add(nextByte);
						}
					}
					catch (EOFException e)
					{ /* end of file reached */ }
				}
				
				
				
				// compress file //
				long startWriteMsTime = System.currentTimeMillis();
				
				File compressedFile = new File(inputFolderPath + "\\" + compressorName + "\\" + inputFile.getName());
				compressedFile.delete();
				compressedFile.createNewFile();
				
				try (FileOutputStream fileStream = new FileOutputStream(compressedFile);
						BufferedOutputStream bufferedStream = new BufferedOutputStream(fileStream);
						OutputStream compressorStream = createOutputStreamFunc.apply(bufferedStream);
						DataOutputStream dataStream = new DataOutputStream(compressorStream))
				{
					for (byte nextByte : originalFileByteArray)
					{
						dataStream.writeByte(nextByte);
					}
				}
				
				long endWriteMsTime = System.currentTimeMillis();
				totalWriteTimeInMs += (endWriteMsTime - startWriteMsTime);
				
				totalCompressedFileSizeInBytes += Files.size(compressedFile.toPath());
				
				
				
				// read compressed file //
				long startReadMsTime = System.currentTimeMillis();
				ArrayList<Byte> compressedFileByteArray = new ArrayList<>();
				
				try (FileInputStream fileStream = new FileInputStream(compressedFile);
						BufferedInputStream bufferedStream = new BufferedInputStream(fileStream);
						InputStream compressorStream = createInputStreamFunc.apply(bufferedStream);
						DataInputStream dataStream = new DataInputStream(compressorStream))
				{
					try
					{
						while (true)
						{
							byte nextByte = dataStream.readByte();
							compressedFileByteArray.add(nextByte);
						}
					}
					catch (EOFException e)
					{ /* end of file reached */ }
				}
				
				long endReadMsTime = System.currentTimeMillis();
				totalReadTimeInMs += (endReadMsTime - startReadMsTime);
				
				
				// confirm the file contents are the same
				Assert.assertEquals("byte array size mismatch", compressedFileByteArray.size(), originalFileByteArray.size());
				for (int i = 0; i < compressedFileByteArray.size(); i++)
				{
					Assert.assertEquals("array content mismatch at index [" + i + "]", compressedFileByteArray.get(i), originalFileByteArray.get(i));
				}
				
				
				processedFileCount++;
			}
			
			
			double compressionRatio = (totalCompressedFileSizeInBytes / (double) totalUncompressedFileSizeInBytes);
			String compressionRatioString = compressionRatio + "";
			compressionRatioString = compressionRatioString.substring(0, Math.min(6, compressionRatioString.length()));
			
			System.out.println("Uncompressed file size: [" + humanReadableByteCountSI(totalUncompressedFileSizeInBytes) + "] Compressed file size: [" + humanReadableByteCountSI(totalCompressedFileSizeInBytes) + "]. Compression ratio: [" + compressionRatioString + "].");
			System.out.println("Total read time in MS: [" + totalReadTimeInMs + "] Average read time per file: [" + (totalReadTimeInMs / processedFileCount) + "]");
			System.out.println("Total write time in MS: [" + totalWriteTimeInMs + "] Average write time per file: [" + (totalWriteTimeInMs / processedFileCount) + "]");
		}
		catch (Exception e)
		{
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}
	
	
	/**
	 * Source:
	 * https://stackoverflow.com/questions/3758606/how-can-i-convert-byte-size-into-a-human-readable-format-in-java#3758880
	 */
	public static String humanReadableByteCountSI(long bytes)
	{
		if (-1000 < bytes && bytes < 1000)
		{
			return bytes + " B";
		}
		CharacterIterator ci = new StringCharacterIterator("kMGTPE");
		while (bytes <= -999_950 || bytes >= 999_950)
		{
			bytes /= 1000;
			ci.next();
		}
		return String.format("%.1f %cB", bytes / 1000.0, ci.current());
	}
	
}
