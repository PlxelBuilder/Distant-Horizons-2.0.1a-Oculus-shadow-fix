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

package com.seibel.distanthorizons.core.render.renderer;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.logging.ConfigBasedSpamLogger;
import com.seibel.distanthorizons.core.pos.DhBlockPos;
import com.seibel.distanthorizons.core.render.AbstractRenderBuffer;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.render.glObject.GLProxy;
import com.seibel.distanthorizons.core.render.glObject.GLState;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.render.glObject.buffer.QuadElementBuffer;
import com.seibel.distanthorizons.core.render.glObject.texture.*;
import com.seibel.distanthorizons.core.render.renderer.shaders.*;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.RenderUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.ILightMapWrapper;
import com.seibel.distanthorizons.api.enums.rendering.EFogColorMode;
import com.seibel.distanthorizons.core.render.fog.LodFogConfig;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.AbstractOptifineAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.math.Mat4f;
import com.seibel.distanthorizons.coreapi.util.math.Vec3d;
import com.seibel.distanthorizons.coreapi.util.math.Vec3f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL32;

import java.awt.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This is where all the magic happens. <br>
 * This is where LODs are draw to the world.
 */
public class LodRenderer
{
	public static final ConfigBasedLogger EVENT_LOGGER = new ConfigBasedLogger(LogManager.getLogger(LodRenderer.class),
			() -> Config.Client.Advanced.Logging.logRendererBufferEvent.get());
	public static ConfigBasedSpamLogger tickLogger = new ConfigBasedSpamLogger(LogManager.getLogger(LodRenderer.class),
			() -> Config.Client.Advanced.Logging.logRendererBufferEvent.get(), 1);
	
	private static final IIrisAccessor IRIS_ACCESSOR = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
	
	public static final boolean ENABLE_DRAW_LAG_SPIKE_LOGGING = false;
	public static final boolean ENABLE_DUMP_GL_STATE = true;
	public static final long DRAW_LAG_SPIKE_THRESHOLD_NS = TimeUnit.NANOSECONDS.convert(20, TimeUnit.MILLISECONDS);
	
	public static final boolean ENABLE_IBO = true;
	
	// TODO make these private, the LOD Builder can get these variables from the config itself
	public static boolean transparencyEnabled = true;
	public static boolean fakeOceanFloor = true;
	
	/** used to prevent cleaning up render resources while they are being used */
	private static final ReentrantLock renderLock = new ReentrantLock();
	
	// these ID's either what any render is currently using (since only one renderer can be active at a time), or just used previously
	private static int activeFramebufferId = -1;
	private static int activeColorTextureId = -1;
	private static int activeDepthTextureId = -1;
	private int cachedWidth;
	private int cachedHeight;
	
	
	
	public void setupOffset(DhBlockPos pos) throws IllegalStateException
	{
		Vec3d cam = MC_RENDER.getCameraExactPosition();
		Vec3f modelPos = new Vec3f((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z));
		
		if (!GL32.glIsProgram(this.shaderProgram.id))
		{
			throw new IllegalStateException("No GL program exists with the ID: [" + this.shaderProgram.id + "]. This either means a shader program was freed while it was still in use or was never created.");
		}
		
		this.shaderProgram.bind();
		this.shaderProgram.setModelPos(modelPos);
	}
	
	public void drawVbo(GLVertexBuffer vbo)
	{
		//// can be uncommented to add additional debug validation to prevent crashes if invalid buffers are being created
		//// shouldn't be used in production due to the performance hit
		//if (GL32.glIsBuffer(vbo.getId()))
		{
			vbo.bind();
			this.shaderProgram.bindVertexBuffer(vbo.getId());
			GL32.glDrawElements(GL32.GL_TRIANGLES, (vbo.getVertexCount() / 4) * 6, // TODO what does the 4 and 6 here represent?
					this.quadIBO.getType(), 0);
			vbo.unbind();
		}
		//else
		//{
		//	// will spam the log if uncommented, but helpful for validation
		//	//LOGGER.warn("Unable to draw VBO: "+vbo.getId());
		//}
	}
	public Vec3f getLookVector() { return MC_RENDER.getLookAtVector(); }
	
	
	public static class LagSpikeCatcher
	{
		long timer = System.nanoTime();
		
		public LagSpikeCatcher() { }
		
		public void end(String source)
		{
			if (!ENABLE_DRAW_LAG_SPIKE_LOGGING)
			{
				return;
			}
			
			this.timer = System.nanoTime() - this.timer;
			if (this.timer > DRAW_LAG_SPIKE_THRESHOLD_NS)
			{
				//4 ms
				EVENT_LOGGER.debug("NOTE: " + source + " took " + Duration.ofNanos(this.timer) + "!");
			}
			
		}
		
	}
	
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	private final ReentrantLock setupLock = new ReentrantLock();
	
	public final RenderBufferHandler bufferHandler;
	
	// The shader program
	LodRenderProgram shaderProgram = null;
	public QuadElementBuffer quadIBO = null;
	public boolean isSetupComplete = false;
	
	// frameBuffer and texture ID's for this renderer
	private DhFramebuffer framebuffer;
	private DhColorTexture colorTexture;
	private DHDepthTexture depthTexture;
	/** 
	 * If true the {@link LodRenderer#framebuffer} is the same as MC's.
	 * This should only be true in the case of Optifine so LODs won't be overwritten when shaders are enabled.
	 */
	private boolean usingMcFrameBuffer = false;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public LodRenderer(RenderBufferHandler bufferHandler)
	{
		this.bufferHandler = bufferHandler;
	}
	
	private boolean rendererClosed = false;
	public void close()
	{
		if (this.rendererClosed)
		{
			EVENT_LOGGER.warn("close() called twice!");
			return;
		}
		
		
		this.rendererClosed = true;
		
		// wait for the renderer to finish before closing (to prevent closing resources that are currently in use)
		renderLock.lock();
		try
		{
			EVENT_LOGGER.info("Shutting down " + LodRenderer.class.getSimpleName() + "...");
			
			this.cleanup();
			this.bufferHandler.close();
			
			EVENT_LOGGER.info("Finished shutting down " + LodRenderer.class.getSimpleName());
		}
		finally
		{
			renderLock.unlock();
		}
	}
	
	public void resize(int width, int height) 
	{
		this.colorTexture.resize(width, height);
		this.depthTexture.resize(width, height, EDhDepthBufferFormat.DEPTH32F);
	}
	
	
	
	//===============//
	// main renderer //
	//===============//
	
	public void drawLODs(IClientLevelWrapper clientLevelWrapper, Mat4f baseModelViewMatrix, Mat4f baseProjectionMatrix, float partialTicks, IProfilerWrapper profiler)
	{
		if (this.rendererClosed)
		{
			EVENT_LOGGER.error("drawLODs() called after close()!");
			return;
		}
		
		if (AbstractOptifineAccessor.optifinePresent() && MC_RENDER.getTargetFrameBuffer() == -1)
		{
			// wait for MC to finish setting up their renderer
			return;
		}
		
		if (!renderLock.tryLock())
		{
			// never lock the render thread, if the lock isn't available don't wait for it
			return;
		}
		
		try
		{
			if (IRIS_ACCESSOR != null && IRIS_ACCESSOR.isRenderingShadowPass())
			{
				// Do not do this while Iris compat is being worked on.
				
				// We do not have a wy to properly render shader shadow pass, since they can
				// and often do change the projection entirely, as well as the output usage.
				
				//EVENT_LOGGER.debug("Skipping shadow pass render.");
				return;
			}
			
			// Note: Since lightmapTexture is changing every frame, it's faster to recreate it than to reuse the old one.
			ILightMapWrapper lightmap = MC_RENDER.getLightmapWrapper(clientLevelWrapper);
			if (lightmap == null)
			{
				// this shouldn't normally happen, but just in case
				return;
			}
			
			// Save Minecraft's GL state so it can be restored at the end of LOD rendering
			LagSpikeCatcher drawSaveGLState = new LagSpikeCatcher();
			GLState minecraftGlState = new GLState();
			if (ENABLE_DUMP_GL_STATE)
			{
				tickLogger.debug("Saving GL state: " + minecraftGlState);
			}
			drawSaveGLState.end("drawSaveGLState");
			
			
			
			//===================//
			// draw params setup //
			//===================//
			
			profiler.push("LOD draw setup");
			
			if (!this.isSetupComplete)
			{
				this.setup();
				
				// shouldn't normally happen, but just in case
				if (!this.isSetupComplete)
				{
					return;
				}
			}
			
			if (MC_RENDER.getTargetFrameBufferViewportWidth() != this.cachedWidth || MC_RENDER.getTargetFrameBufferViewportHeight() != this.cachedHeight) 
			{
				this.cachedWidth = MC_RENDER.getTargetFrameBufferViewportWidth();
				this.cachedHeight = MC_RENDER.getTargetFrameBufferViewportHeight();
				this.resize(this.cachedWidth, this.cachedHeight);
			}
			
			this.setActiveFramebufferId(framebuffer.getId());
			this.setActiveDepthTextureId(depthTexture.getTextureId());
			this.setActiveColorTextureId(colorTexture.getTextureId());
			// Bind LOD frame buffer
			this.framebuffer.bind();
			
			
			if (this.usingMcFrameBuffer)
			{
				// don't clear the color texture, that removes the sky 
				GL32.glClear(GL32.GL_DEPTH_BUFFER_BIT);
			}
			else
			{
				GL32.glClear(GL32.GL_COLOR_BUFFER_BIT | GL32.GL_DEPTH_BUFFER_BIT);
			}
			
			
			GL32.glEnable(GL32.GL_DEPTH_TEST);
			GL32.glDepthFunc(GL32.GL_LESS);
			
			// Set OpenGL polygon mode
			boolean renderWireframe = Config.Client.Advanced.Debugging.renderWireframe.get();
			if (renderWireframe)
			{
				GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_LINE);
				//GL32.glDisable(GL32.GL_CULL_FACE);
			}
			else
			{
				GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_FILL);
				GL32.glEnable(GL32.GL_CULL_FACE);
			}
			
			// Enable depth test and depth mask
			GL32.glEnable(GL32.GL_DEPTH_TEST);
			GL32.glDepthFunc(GL32.GL_LESS);
			GL32.glDepthMask(true);
			
			// Disable blending
			// We render opaque first, then transparent
			GL32.glDisable(GL32.GL_BLEND);
			
			/*---------Bind required objects--------*/
			// Setup LodRenderProgram and the LightmapTexture if it has not yet been done
			// also binds LightmapTexture, VAO, and ShaderProgram
			if (!this.isSetupComplete)
			{
				this.setup();
			}
			else
			{
				LodFogConfig newFogConfig = this.shaderProgram.isShaderUsable();
				if (newFogConfig != null)
				{
					this.shaderProgram.free();
					this.shaderProgram = new LodRenderProgram(newFogConfig);
					
					FogShader.INSTANCE.free();
					FogShader.INSTANCE = new FogShader(newFogConfig);
				}
				this.shaderProgram.bind();
			}
			
			/*---------Get required data--------*/
			//int vanillaBlockRenderedDistance = MC_RENDER.getRenderDistance() * LodUtil.CHUNK_WIDTH;
			//Mat4f modelViewProjectionMatrix = RenderUtil.createCombinedModelViewProjectionMatrix(baseProjectionMatrix, baseModelViewMatrix, partialTicks);
			
			Mat4f projectionMatrix = RenderUtil.createLodProjectionMatrix(baseProjectionMatrix, partialTicks);
			
			Mat4f modelViewProjectionMatrix = new Mat4f(projectionMatrix);
			modelViewProjectionMatrix.multiply(RenderUtil.createLodModelViewMatrix(baseModelViewMatrix));
			
			/*---------Fill uniform data--------*/
			this.shaderProgram.fillUniformData(modelViewProjectionMatrix, /*Light map = GL_TEXTURE0*/ 0,
					MC.getWrappedClientLevel().getMinHeight(), partialTicks);
			
			lightmap.bind();
			if (ENABLE_IBO)
			{
				this.quadIBO.bind();
			}
			
			this.bufferHandler.buildRenderListAndUpdateSections(this.getLookVector());
			
			
			
			//===========//
			// rendering //
			//===========//
			
			LagSpikeCatcher drawLagSpikeCatcher = new LagSpikeCatcher();
			
			profiler.popPush("LOD Opaque");
			// TODO: Directional culling
			this.bufferHandler.renderOpaque(this);
			
			if (Config.Client.Advanced.Graphics.Ssao.enabled.get())
			{
				profiler.popPush("LOD SSAO");
				SSAORenderer.INSTANCE.render(minecraftGlState, projectionMatrix, partialTicks);
			}
			
			profiler.popPush("LOD Fog");
			FogShader.INSTANCE.setModelViewProjectionMatrix(modelViewProjectionMatrix);
			FogShader.INSTANCE.render(partialTicks);
			
			//DarkShader.INSTANCE.render(partialTicks); // A test shader to make the world darker
			
			// Render transparent LOD sections (such as water)
			transparencyEnabled = Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled;
			fakeOceanFloor = Config.Client.Advanced.Graphics.Quality.transparency.get().fakeTransparencyEnabled;
			
			if (Config.Client.Advanced.Graphics.Quality.transparency.get().transparencyEnabled)
			{
				profiler.popPush("LOD Transparent");
				
				GL32.glEnable(GL32.GL_BLEND);
				GL32.glBlendEquation(GL32.GL_FUNC_ADD);
				GL32.glBlendFunc(GL32.GL_ONE, GL32.GL_ONE_MINUS_SRC_ALPHA);
				this.bufferHandler.renderTransparent(this);
				GL32.glDepthMask(true); // Apparently the depth mask state is stored in the FBO, so glState fails to restore it...
				
				FogShader.INSTANCE.render(partialTicks);
			}
			
			
			if (this.usingMcFrameBuffer)
			{
				// If MC's framebuffer is being used the depth needs to be cleared to prevent rendering on top of MC.
				// This should only happen when Optifine shaders are being used.
				GL32.glClear(GL32.GL_DEPTH_BUFFER_BIT);
			}
			
			drawLagSpikeCatcher.end("LodDraw");
			
			
			
			//=============================//
			// Apply to the MC FrameBuffer //
			//=============================//
			
			profiler.popPush("LOD Apply");
			
			GLState dhApplyGlState = new GLState();
			
			// Copy the LOD framebuffer to Minecraft's framebuffer
			DhApplyShader.INSTANCE.render(partialTicks);
			
			dhApplyGlState.restore();
			
			
			
			
			//================//
			// render cleanup //
			//================//
			
			profiler.popPush("LOD cleanup");
			LagSpikeCatcher drawCleanup = new LagSpikeCatcher();
			lightmap.unbind();
			if (ENABLE_IBO)
			{
				this.quadIBO.unbind();
			}
			
			this.shaderProgram.unbind();
			
			if (Config.Client.Advanced.Debugging.DebugWireframe.enableRendering.get())
			{
				profiler.popPush("Debug wireframes");
				// Note: this can be very slow if a lot of boxes are being rendered 
				DebugRenderer.INSTANCE.render(modelViewProjectionMatrix);
				profiler.popPush("LOD cleanup");
			}
			
			minecraftGlState.restore();
			drawCleanup.end("LodDrawCleanup");
			
			// end of internal LOD profiling
			profiler.pop();
			tickLogger.incLogTries();
			
		}
		finally
		{
			renderLock.unlock();
		}
	}
	
	private static final Logger LOGGER = LogManager.getLogger();
	
	
	//=================//
	// Setup Functions //
	//=================//
	
	/** Setup all render objects - REQUIRES to be in render thread */
	private void setup()
	{
		if (this.isSetupComplete)
		{
			EVENT_LOGGER.warn("Renderer setup called but it has already completed setup!");
			return;
		}
		if (GLProxy.getInstance() == null)
		{
			// shouldn't normally happen, but just in case
			EVENT_LOGGER.warn("Renderer setup called but GLProxy has not yet been setup!");
			return;
		}
		
		try
		{
			this.setupLock.lock();
			
			
			EVENT_LOGGER.info("Setting up renderer");
			this.isSetupComplete = true;
			this.shaderProgram = new LodRenderProgram(LodFogConfig.generateFogConfig()); // TODO this doesn't actually use the fog config
			if (ENABLE_IBO)
			{
				this.quadIBO = new QuadElementBuffer();
				this.quadIBO.reserve(AbstractRenderBuffer.MAX_QUADS_PER_BUFFER);
			}
			
			
			// create or get the frame buffer
			if (AbstractOptifineAccessor.optifinePresent())
			{
				// use MC/Optifine's default FrameBuffer so shaders won't remove the LODs
				int currentFrameBufferId = MC_RENDER.getTargetFrameBuffer();
				this.framebuffer = new DhFramebuffer(currentFrameBufferId);
				this.usingMcFrameBuffer = true;
			}
			else 
			{
				// normal use case
				this.framebuffer = new DhFramebuffer();
				this.usingMcFrameBuffer = false;
			}
			
			// color and depth texture
			this.colorTexture = DhColorTexture.builder().setDimensions(MC_RENDER.getTargetFrameBufferViewportWidth(), MC_RENDER.getTargetFrameBufferViewportHeight())
					.setInternalFormat(EDhInternalTextureFormat.RGBA8)
					.setPixelType(EDhPixelType.UNSIGNED_BYTE)
					.setPixelFormat(EDhPixelFormat.RGBA)
					.build();
			this.depthTexture = new DHDepthTexture(MC_RENDER.getTargetFrameBufferViewportWidth(), MC_RENDER.getTargetFrameBufferViewportHeight(), EDhDepthBufferFormat.DEPTH32F);
			
			this.framebuffer.addDepthAttachment(this.depthTexture.getTextureId(), EDhDepthBufferFormat.DEPTH32F);
			this.framebuffer.addColorAttachment(0, this.colorTexture.getTextureId());
			
			this.cachedWidth = MC_RENDER.getTargetFrameBufferViewportWidth();
			this.cachedHeight = MC_RENDER.getTargetFrameBufferViewportHeight();
			
			if(this.framebuffer.getStatus() != GL32.GL_FRAMEBUFFER_COMPLETE)
			{
				// This generally means something wasn't bound, IE missing either the color or depth texture
				tickLogger.warn("FrameBuffer ["+this.framebuffer.getId()+"] isn't complete.");
			}
			
			
			EVENT_LOGGER.info("Renderer setup complete");
		}
		finally
		{
			this.setupLock.unlock();
		}
	}
	
	private Color getFogColor(float partialTicks)
	{
		Color fogColor;
		
		if (Config.Client.Advanced.Graphics.Fog.colorMode.get() == EFogColorMode.USE_SKY_COLOR)
		{
			fogColor = MC_RENDER.getSkyColor();
		}
		else
		{
			fogColor = MC_RENDER.getFogColor(partialTicks);
		}
		
		return fogColor;
	}
	private Color getSpecialFogColor(float partialTicks) { return MC_RENDER.getSpecialFogColor(partialTicks); }
	
	
	
	//===============//
	// API functions //
	//===============//
	
	private void setActiveFramebufferId(int frameBufferId) { activeFramebufferId = frameBufferId; }
	/** Returns -1 if no frame buffer has been bound yet */
	public static int getActiveFramebufferId() { return activeFramebufferId; }
	
	private void setActiveColorTextureId(int colorTextureId) { activeColorTextureId = colorTextureId; }
	/** Returns -1 if no texture has been bound yet */
	public static int getActiveColorTextureId() { return activeColorTextureId; }
	
	private void setActiveDepthTextureId(int depthTextureId) { activeDepthTextureId = depthTextureId; }
	/** Returns -1 if no texture has been bound yet */
	public static int getActiveDepthTextureId() { return activeDepthTextureId; }
	
	
	
	//======================//
	// Cleanup Functions    //
	//======================//
	
	/**
	 * cleanup and free all render objects. MUST be on the render thread
	 * (Many objects are Native, outside of JVM, and need manual cleanup)
	 */
	private void cleanup()
	{
		if (GLProxy.getInstance() == null)
		{
			// shouldn't normally happen, but just in case
			EVENT_LOGGER.warn("Renderer Cleanup called but the GLProxy has never been initialized!");
			return;
		}
		
		try
		{
			this.setupLock.lock();
			
			EVENT_LOGGER.info("Queuing Renderer Cleanup for main render thread");
			GLProxy.getInstance().recordOpenGlCall(() ->
			{
				EVENT_LOGGER.info("Renderer Cleanup Started");
				
				if (this.shaderProgram != null)
				{
					this.shaderProgram.free();
					this.shaderProgram = null;
				}
				
				if (this.quadIBO != null)
					this.quadIBO.destroy(false);
				
				// Delete framebuffer, color texture, and depth texture
				if (this.framebuffer != null && !this.usingMcFrameBuffer)
					this.framebuffer.destroyInternal();
				if (this.colorTexture != null)
					this.colorTexture.destroy();
				if (this.depthTexture != null)
					this.depthTexture.destroy();
				
				EVENT_LOGGER.info("Renderer Cleanup Complete");
			});
		}
		catch (Exception e)
		{
			this.setupLock.unlock();
		}
	}
	
}
