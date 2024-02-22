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

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EGLProxyContext;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.AbstractRenderBuffer;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.StatsMap;
import com.seibel.distanthorizons.api.enums.config.EGpuUploadMethod;
import com.seibel.distanthorizons.core.util.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.*;

/**
 * Java representation of one or more OpenGL buffers for rendering.
 *
 * @see ColumnRenderBufferBuilder
 */
public class ColumnRenderBuffer extends AbstractRenderBuffer
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	private static final IMinecraftClientWrapper minecraftClient = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	private static final long MAX_BUFFER_UPLOAD_TIMEOUT_NANOSECONDS = 1_000_000;
	
	
	public final DhBlockPos pos;
	
	public boolean buffersUploaded = false;
	
	private GLVertexBuffer[] vbos;
	private GLVertexBuffer[] vbosTransparent;
	
	private final DhSectionPos debugPos;
	
	
	//==============//
	// constructors //
	//==============//
	
	public ColumnRenderBuffer(DhBlockPos pos, DhSectionPos debugPos)
	{
		this.pos = pos;
		this.debugPos = debugPos;
		this.vbos = new GLVertexBuffer[0];
		this.vbosTransparent = new GLVertexBuffer[0];
	}
	
	
	
	
	
	//==================//
	// buffer uploading //
	//==================//
	
	/** Should be run on a DH thread. */
	public void uploadBuffer(LodQuadBuilder builder, EGpuUploadMethod gpuUploadMethod) throws InterruptedException
	{
		LodUtil.assertTrue(Thread.currentThread().getName().startsWith(ThreadUtil.THREAD_NAME_PREFIX), "Buffer uploading needs to be done on a DH thread to prevent locking up any MC threads.");
		
		
		// the async is relative to MC's render thread
		boolean uploadAsync = Config.Client.Advanced.GpuBuffers.gpuUploadAsync.get();
		if (uploadAsync)
		{
			// upload here on a DH thread
			GLProxy glProxy = GLProxy.getInstance();
			EGLProxyContext oldContext = glProxy.getGlContext();
			glProxy.setGlContext(EGLProxyContext.LOD_BUILDER);
			try
			{
				this.uploadBuffersUsingUploadMethod(builder, gpuUploadMethod);
			}
			finally
			{
				glProxy.setGlContext(oldContext);
			}
		}
		else
		{
			// upload on MC's render thread
			CompletableFuture<Void> uploadFuture = new CompletableFuture<>();
			minecraftClient.executeOnRenderThread(() ->
			{
				try
				{
					this.uploadBuffersUsingUploadMethod(builder, gpuUploadMethod);
					uploadFuture.complete(null);
				}
				catch (InterruptedException e)
				{
					throw new CompletionException(e);
				}
			});
			
			
			try
			{
				// wait for the upload to finish
				uploadFuture.get(1000, TimeUnit.MILLISECONDS);
			}
			catch (ExecutionException e)
			{
				LOGGER.warn("Error uploading builder ["+builder+"] synchronously. Error: "+e.getMessage(), e);
			}
			catch (TimeoutException e)
			{
				// timeouts can be ignored because it generally means the
				// MC Render thread executor was closed 
				//LOGGER.warn("Error uploading builder ["+builder+"] synchronously. Error: "+e.getMessage(), e);
			}
			
			
		}
	}
	private void uploadBuffersUsingUploadMethod(LodQuadBuilder builder, EGpuUploadMethod gpuUploadMethod) throws InterruptedException
	{
		if (gpuUploadMethod.useEarlyMapping)
		{
			this.uploadBuffersMapped(builder, gpuUploadMethod);
		}
		else
		{
			this.uploadBuffersDirect(builder, gpuUploadMethod);
		}
		
		this.buffersUploaded = true;
	}
	
	
	
	private void uploadBuffersMapped(LodQuadBuilder builder, EGpuUploadMethod method)
	{
		// opaque vbos //
		
		this.vbos = ColumnRenderBufferBuilder.resizeBuffer(this.vbos, builder.getCurrentNeededOpaqueVertexBufferCount());
		for (int i = 0; i < this.vbos.length; i++)
		{
			if (this.vbos[i] == null)
			{
				this.vbos[i] = new GLVertexBuffer(method.useBufferStorage);
			}
		}
		LodQuadBuilder.BufferFiller func = builder.makeOpaqueBufferFiller(method);
		for (GLVertexBuffer vbo : this.vbos)
		{
			func.fill(vbo);
		}
		
		
		// transparent vbos //
		
		this.vbosTransparent = ColumnRenderBufferBuilder.resizeBuffer(this.vbosTransparent, builder.getCurrentNeededTransparentVertexBufferCount());
		for (int i = 0; i < this.vbosTransparent.length; i++)
		{
			if (this.vbosTransparent[i] == null)
			{
				this.vbosTransparent[i] = new GLVertexBuffer(method.useBufferStorage);
			}
		}
		LodQuadBuilder.BufferFiller transparentFillerFunc = builder.makeTransparentBufferFiller(method);
		for (GLVertexBuffer vbo : this.vbosTransparent)
		{
			transparentFillerFunc.fill(vbo);
		}
	}
	
	private void uploadBuffersDirect(LodQuadBuilder builder, EGpuUploadMethod method) throws InterruptedException
	{
		this.vbos = ColumnRenderBufferBuilder.resizeBuffer(this.vbos, builder.getCurrentNeededOpaqueVertexBufferCount());
		uploadBuffersDirect(this.vbos, builder.makeOpaqueVertexBuffers(), method);
		
		this.vbosTransparent = ColumnRenderBufferBuilder.resizeBuffer(this.vbosTransparent, builder.getCurrentNeededTransparentVertexBufferCount());
		uploadBuffersDirect(this.vbosTransparent, builder.makeTransparentVertexBuffers(), method);
	}
	private static void uploadBuffersDirect(GLVertexBuffer[] vbos, Iterator<ByteBuffer> iter, EGpuUploadMethod method) throws InterruptedException
	{
		long remainingMS = 0;
		long MBPerMS = Config.Client.Advanced.GpuBuffers.gpuUploadPerMegabyteInMilliseconds.get();
		int vboIndex = 0;
		while (iter.hasNext())
		{
			if (vboIndex >= vbos.length)
			{
				throw new RuntimeException("Too many vertex buffers!!");
			}
			
			
			// get or create the VBO
			if (vbos[vboIndex] == null)
			{
				vbos[vboIndex] = new GLVertexBuffer(method.useBufferStorage);
			}
			GLVertexBuffer vbo = vbos[vboIndex];
			
			
			ByteBuffer bb = iter.next();
			int size = bb.limit() - bb.position();
			
			try
			{
				vbo.bind();
				vbo.uploadBuffer(bb, size / LodUtil.LOD_VERTEX_FORMAT.getByteSize(), method, FULL_SIZED_BUFFER);
			}
			catch (Exception e)
			{
				vbos[vboIndex] = null;
				vbo.close();
				LOGGER.error("Failed to upload buffer: ", e);
			}
			
			
			if (MBPerMS > 0)
			{
				// upload buffers over an extended period of time
				// to hopefully prevent stuttering.
				remainingMS += size * MBPerMS;
				if (remainingMS >= TimeUnit.NANOSECONDS.convert(1000 / 60, TimeUnit.MILLISECONDS))
				{
					if (remainingMS > MAX_BUFFER_UPLOAD_TIMEOUT_NANOSECONDS)
					{
						remainingMS = MAX_BUFFER_UPLOAD_TIMEOUT_NANOSECONDS;
					}
					
					Thread.sleep(remainingMS / 1000000, (int) (remainingMS % 1000000));
					remainingMS = 0;
				}
			}
			
			vboIndex++;
		}
		
		if (vboIndex < vbos.length)
		{
			throw new RuntimeException("Too few vertex buffers!!");
		}
	}
	
	
	
	
	
	//========//
	// render //
	//========//
	
	@Override
	public boolean renderOpaque(LodRenderer renderContext)
	{
		boolean hasRendered = false;
		renderContext.setupOffset(this.pos);
		for (GLVertexBuffer vbo : this.vbos)
		{
			if (vbo == null)
			{
				continue;
			}
			
			if (vbo.getVertexCount() == 0)
			{
				continue;
			}
			
			hasRendered = true;
			renderContext.drawVbo(vbo);
			//LodRenderer.tickLogger.info("Vertex buffer: {}", vbo);
		}
		return hasRendered;
	}
	
	@Override
	public boolean renderTransparent(LodRenderer renderContext)
	{
		boolean hasRendered = false;
		
		try
		{
			// can throw an IllegalStateException if the GL program was freed before it should've been
			renderContext.setupOffset(this.pos);
			
			for (GLVertexBuffer vbo : this.vbosTransparent)
			{
				if (vbo == null)
				{
					continue;
				}
				
				if (vbo.getVertexCount() == 0)
				{
					continue;
				}
				
				hasRendered = true;
				renderContext.drawVbo(vbo);
				//LodRenderer.tickLogger.info("Vertex buffer: {}", vbo);
			}
		}
		catch (IllegalStateException e)
		{
			LOGGER.error("renderContext program doesn't exist for pos: "+this.pos, e);
		}
		
		return hasRendered;
	}
	
	
	
	//==============//
	// misc methods //
	//==============//
	
	/** can be used when debugging */
	public boolean hasNonEmptyBuffers()
	{
		for (GLVertexBuffer vertexBuffer : this.vbos)
		{
			if (vertexBuffer != null && vertexBuffer.getSize() != 0)
			{
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public void debugDumpStats(StatsMap statsMap)
	{
		statsMap.incStat("RenderBuffers");
		statsMap.incStat("SimpleRenderBuffers");
		for (GLVertexBuffer vertexBuffer : vbos)
		{
			if (vertexBuffer != null)
			{
				statsMap.incStat("VBOs");
				if (vertexBuffer.getSize() == FULL_SIZED_BUFFER)
				{
					statsMap.incStat("FullsizedVBOs");
				}
				
				if (vertexBuffer.getSize() == 0)
				{
					GLProxy.GL_LOGGER.warn("VBO with size 0");
				}
				statsMap.incBytesStat("TotalUsage", vertexBuffer.getSize());
			}
		}
	}
	
	@Override
	public void close()
	{
		this.buffersUploaded = false;
		
		GLProxy.getInstance().recordOpenGlCall(() ->
		{
			for (GLVertexBuffer buffer : this.vbos)
			{
				if (buffer != null)
				{
					buffer.destroy(false);
				}
			}
			
			for (GLVertexBuffer buffer : this.vbosTransparent)
			{
				if (buffer != null)
				{
					buffer.destroy(false);
				}
			}
		});
	}
	
}
