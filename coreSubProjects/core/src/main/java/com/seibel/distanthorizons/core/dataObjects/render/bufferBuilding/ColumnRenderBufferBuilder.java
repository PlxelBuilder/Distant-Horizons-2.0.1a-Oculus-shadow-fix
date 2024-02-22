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

package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.api.enums.rendering.EDebugRendering;
import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhBlockPos;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.util.objects.Reference;
import com.seibel.distanthorizons.core.util.objects.UncheckedInterruptedException;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.util.threading.ThreadPools;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Used to populate the buffers in a {@link ColumnRenderSource} object.
 *
 * @see ColumnRenderSource
 */
public class ColumnRenderBufferBuilder
{
	public static final ConfigBasedLogger EVENT_LOGGER = new ConfigBasedLogger(LogManager.getLogger(),
			() -> Config.Client.Advanced.Logging.logRendererBufferEvent.get());
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	
	public static final int MAX_NUMBER_OF_CONCURRENT_CALLS_PER_THREAD = 3;
	public static int maxNumberOfConcurrentCalls = MAX_NUMBER_OF_CONCURRENT_CALLS_PER_THREAD;
	
	
	
	
	//==============//
	// vbo building //
	//==============//
	
	public static CompletableFuture<ColumnRenderBuffer> buildBuffersAsync(
			IDhClientLevel clientLevel, Reference<ColumnRenderBuffer> renderBufferRef,
			ColumnRenderSource renderSource, ColumnRenderSource[] adjData)
	{
		ThreadPoolExecutor bufferBuilderExecutor = ThreadPools.getBufferBuilderExecutor();
		ThreadPoolExecutor bufferUploaderExecutor = ThreadPools.getBufferUploaderExecutor();
		if ((bufferBuilderExecutor == null || bufferBuilderExecutor.isTerminated()) ||
			(bufferUploaderExecutor == null || bufferUploaderExecutor.isTerminated()))
		{
			// one or more of the thread pools has been shut down
			CompletableFuture<ColumnRenderBuffer> future = new CompletableFuture<>();
			future.cancel(true);
			return future;
		}
		
		//LOGGER.info("RenderRegion startBuild @ "+renderSource.sectionPos);
		return CompletableFuture.supplyAsync(() ->
				{
					try
					{
						boolean enableTransparency = Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled;
						
						EVENT_LOGGER.trace("RenderRegion start QuadBuild @ " + renderSource.sectionPos);
						boolean enableSkyLightCulling = !clientLevel.getLevelWrapper().hasCeiling() && Config.Client.Advanced.Graphics.AdvancedGraphics.enableCaveCulling.get();
						
						int skyLightCullingBelow = Config.Client.Advanced.Graphics.AdvancedGraphics.caveCullingHeight.get();
						// FIXME: Clamp also to the max world height.
						skyLightCullingBelow = Math.max(skyLightCullingBelow, clientLevel.getMinY());
						
						
						long builderStartTime = System.currentTimeMillis();
						
						LodQuadBuilder builder = new LodQuadBuilder(enableSkyLightCulling, (short) (skyLightCullingBelow - clientLevel.getMinY()), enableTransparency);
						makeLodRenderData(builder, renderSource, adjData);
						
						long builderEndTime = System.currentTimeMillis();
						long buildMs = builderEndTime - builderStartTime;
						LOGGER.debug("RenderRegion end QuadBuild @ " + renderSource.sectionPos + " took: " + buildMs);
						
						return builder;
					}
					catch (UncheckedInterruptedException e)
					{
						throw e;
					}
					catch (Throwable e3)
					{
						LOGGER.error("\"LodNodeBufferBuilder\" was unable to build quads: ", e3);
						throw e3;
					}
				}, bufferBuilderExecutor)
				.thenApplyAsync((quadBuilder) ->
				{
					try
					{
						EVENT_LOGGER.trace("RenderRegion start Upload @ " + renderSource.sectionPos);
						
						ColumnRenderBuffer buffer = renderBufferRef.swap(null);
						if (buffer == null)
						{
							buffer = new ColumnRenderBuffer(new DhBlockPos(renderSource.sectionPos.getMinCornerLodPos().getCornerBlockPos(), clientLevel.getMinY()), renderSource.sectionPos);
						}
						
						try
						{
							buffer.uploadBuffer(quadBuilder, GLProxy.getInstance().getGpuUploadMethod());
							LodUtil.assertTrue(buffer.buffersUploaded);
							EVENT_LOGGER.trace("RenderRegion end Upload @ " + renderSource.sectionPos);
							return buffer;
						}
						catch (Exception e)
						{
							buffer.close();
							throw e;
						}
					}
					catch (InterruptedException e)
					{
						throw UncheckedInterruptedException.convert(e);
					}
					catch (Throwable e3)
					{
						LOGGER.error("\"LodNodeBufferBuilder\" was unable to upload buffer: ", e3);
						throw e3;
					}
				}, bufferUploaderExecutor)
				.handle((columnRenderBuffer, ex) ->
				{
					//LOGGER.info("RenderRegion endBuild @ {}", renderSource.sectionPos);
					if (ex != null)
					{
						LOGGER.warn("Buffer building failed: " + ex.getMessage(), ex);
						
						if (!renderBufferRef.isEmpty())
						{
							ColumnRenderBuffer buffer = renderBufferRef.swap(null);
							buffer.close();
						}
						
						return null;
					}
					else
					{
						if (columnRenderBuffer != null)
						{
							LodUtil.assertTrue(columnRenderBuffer.buffersUploaded);
						}
						
						return columnRenderBuffer; 
					}
				});
	}
	private static void makeLodRenderData(LodQuadBuilder quadBuilder, ColumnRenderSource renderSource, ColumnRenderSource[] adjRegions)
	{
		// Variable initialization
		EDebugRendering debugMode = Config.Client.Advanced.Debugging.debugRendering.get();
		
		// TODO make a config for this
		// can be uncommented to limit which section positions are build and thus, rendered
		// useful when debugging a specific section
//		if (renderSource.sectionPos.getDetailLevel() == 6 
//			&& renderSource.sectionPos.getZ() == 0 && renderSource.sectionPos.getX() == 0)
//		{
//			int test = 0;
//		}
//		else
//		{
//			return;
//		}
		
		byte detailLevel = renderSource.getDataDetailLevel();
		for (int x = 0; x < ColumnRenderSource.SECTION_SIZE; x++)
		{
			for (int z = 0; z < ColumnRenderSource.SECTION_SIZE; z++)
			{
				// TODO make a config for this
				// can be uncommented to limit the buffer building to a specific
				// relative position in this section.
				// useful for debugging a single column's rendering
//				if (x != 0 || (z != 0 && z != 1))
//				{
//					continue;
//				}
				
				
				UncheckedInterruptedException.throwIfInterrupted();
				
				ColumnArrayView columnRenderData = renderSource.getVerticalDataPointView(x, z);
				if (columnRenderData.size() == 0
						|| !RenderDataPointUtil.doesDataPointExist(columnRenderData.get(0))
						|| RenderDataPointUtil.isVoid(columnRenderData.get(0)))
				{
					continue;
				}
				
				ColumnRenderSource.DebugSourceFlag debugSourceFlag = renderSource.debugGetFlag(x, z);
				
				ColumnArrayView[][] adjColumnViews = new ColumnArrayView[4][];
				// We extract the adj data in the four cardinal direction
				
				// we first reset the adjShadeDisabled. This is used to disable the shade on the
				// border when we have transparent block like water or glass
				// to avoid having a "darker border" underground
				// Arrays.fill(adjShadeDisabled, false);
				
				
				// We check every adj block in each direction
				
				// If the adj block is rendered in the same region and with same detail
				// and is positioned in a place that is not going to be rendered by vanilla game
				// then we can set this position as adj
				// We avoid cases where the adjPosition is in player chunk while the position is
				// not
				// to always have a wall underwater
				for (EDhDirection lodDirection : EDhDirection.ADJ_DIRECTIONS)
				{
					try
					{
						int xAdj = x + lodDirection.getNormal().x;
						int zAdj = z + lodDirection.getNormal().z;
						boolean isCrossRegionBoundary =
								(xAdj < 0 || xAdj >= ColumnRenderSource.SECTION_SIZE) ||
										(zAdj < 0 || zAdj >= ColumnRenderSource.SECTION_SIZE);
						
						ColumnRenderSource adjRenderSource;
						byte adjDetailLevel;
						
						//we check if the detail of the adjPos is equal to the correct one (region border fix)
						//or if the detail is wrong by 1 value (region+circle border fix)
						if (isCrossRegionBoundary)
						{
							//we compute at which detail that position should be rendered
							adjRenderSource = adjRegions[lodDirection.ordinal() - 2];
							if (adjRenderSource == null)
							{
								continue;
							}
							
							adjDetailLevel = adjRenderSource.getDataDetailLevel();
							if (adjDetailLevel != detailLevel)
							{
								//TODO: Implement this
							}
							else
							{
								if (xAdj < 0)
									xAdj += ColumnRenderSource.SECTION_SIZE;
								
								if (zAdj < 0)
									zAdj += ColumnRenderSource.SECTION_SIZE;
								
								if (xAdj >= ColumnRenderSource.SECTION_SIZE)
									xAdj -= ColumnRenderSource.SECTION_SIZE;
								
								if (zAdj >= ColumnRenderSource.SECTION_SIZE)
									zAdj -= ColumnRenderSource.SECTION_SIZE;
							}
						}
						else
						{
							adjRenderSource = renderSource;
							adjDetailLevel = detailLevel;
						}
						
						if (adjDetailLevel < detailLevel - 1 || adjDetailLevel > detailLevel + 1)
						{
							continue;
						}
						
						if (adjDetailLevel == detailLevel || adjDetailLevel > detailLevel)
						{
							adjColumnViews[lodDirection.ordinal() - 2] = new ColumnArrayView[1];
							adjColumnViews[lodDirection.ordinal() - 2][0] = adjRenderSource.getVerticalDataPointView(xAdj, zAdj);
						}
						else
						{
							adjColumnViews[lodDirection.ordinal() - 2] = new ColumnArrayView[2];
							adjColumnViews[lodDirection.ordinal() - 2][0] = adjRenderSource.getVerticalDataPointView(xAdj, zAdj);
							adjColumnViews[lodDirection.ordinal() - 2][1] = adjRenderSource.getVerticalDataPointView(
									xAdj + (lodDirection.getAxis() == EDhDirection.Axis.X ? 0 : 1),
									zAdj + (lodDirection.getAxis() == EDhDirection.Axis.Z ? 0 : 1));
						}
					}
					catch (RuntimeException e)
					{
						EVENT_LOGGER.warn("Failed to get adj data for [" + detailLevel + ":" + x + "," + z + "] at [" + lodDirection + "]");
						EVENT_LOGGER.warn("Detail exception: ", e);
					}
				} // for adjacent directions
				
				
				// We render every vertical lod present in this position
				// We only stop when we find a block that is void or non-existing block
				for (int i = 0; i < columnRenderData.size(); i++)
				{
					// TODO make a config for this
					// can be uncommented to limit which vertical LOD is generated
//					if (i != 0)
//					{
//						continue;
//					}
					
					long data = columnRenderData.get(i);
					// If the data is not render-able (Void or non-existing) we stop since there is
					// no data left in this position
					if (RenderDataPointUtil.isVoid(data) || !RenderDataPointUtil.doesDataPointExist(data))
					{
						break;
					}
					
					long topDataPoint = (i - 1) >= 0 ? columnRenderData.get(i - 1) : RenderDataPointUtil.EMPTY_DATA;
					long bottomDataPoint = (i + 1) < columnRenderData.size() ? columnRenderData.get(i + 1) : RenderDataPointUtil.EMPTY_DATA;
					
					CubicLodTemplate.addLodToBuffer(data, topDataPoint, bottomDataPoint, adjColumnViews, detailLevel,
							x, z, quadBuilder, debugMode, debugSourceFlag);
				}
				
			}// for z
		}// for x
		
		quadBuilder.finalizeData();
	}
	
	
	
	//=================//
	// vbo interaction //
	//=================//
	
	public static GLVertexBuffer[] resizeBuffer(GLVertexBuffer[] vbos, int newSize)
	{
		if (vbos.length == newSize)
		{
			return vbos;
		}
		
		GLVertexBuffer[] newVbos = new GLVertexBuffer[newSize];
		System.arraycopy(vbos, 0, newVbos, 0, Math.min(vbos.length, newSize));
		if (newSize < vbos.length)
		{
			for (int i = newSize; i < vbos.length; i++)
			{
				if (vbos[i] != null)
				{
					vbos[i].close();
				}
			}
		}
		return newVbos;
	}
	
}
