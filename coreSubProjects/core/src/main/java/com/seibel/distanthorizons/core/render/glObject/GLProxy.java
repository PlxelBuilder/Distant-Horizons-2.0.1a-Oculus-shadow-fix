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

package com.seibel.distanthorizons.core.render.glObject;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.seibel.distanthorizons.api.enums.config.EGLErrorHandlingMode;
import com.seibel.distanthorizons.api.enums.config.EGlProfileMode;
import com.seibel.distanthorizons.api.enums.config.EGpuUploadMethod;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.enums.EGLProxyContext;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.ReflectionUtil;
import com.seibel.distanthorizons.core.util.objects.GLMessage;
import com.seibel.distanthorizons.core.util.objects.GLMessageOutputStream;
import com.seibel.distanthorizons.core.util.objects.Pair;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.coreapi.util.StringUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;

import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * A singleton that holds references to different openGL contexts
 * and GPU capabilities.
 *
 * <p>
 * Helpful OpenGL resources:
 * <p>
 * https://www.seas.upenn.edu/~pcozzi/OpenGLInsights/OpenGLInsights-AsynchronousBufferTransfers.pdf <br>
 * https://learnopengl.com/Advanced-OpenGL/Advanced-Data <br>
 * https://www.slideshare.net/CassEveritt/approaching-zero-driver-overhead <br><br>
 *
 * https://gamedev.stackexchange.com/questions/91995/edit-vbo-data-or-create-a-new-one <br>
 * https://stackoverflow.com/questions/63509735/massive-performance-loss-with-glmapbuffer <br><br>
 *
 * @author James Seibel
 */
public class GLProxy
{
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	private ExecutorService workerThread = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(GLProxy.class.getSimpleName() + "-Worker-Thread").build());
	
	private static final Logger LOGGER = DhLoggerBuilder.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());
	public static final ConfigBasedLogger GL_LOGGER = new ConfigBasedLogger(LogManager.getLogger(GLProxy.class),
			() -> Config.Client.Advanced.Logging.logRendererGLEvent.get());
	
	/** newest version first */
	private static final ArrayList<Pair<Integer, Integer>> SUPPORTED_GL_VERSIONS = new ArrayList<>(
			Arrays.asList(
				new Pair<>(4,6), new Pair<>(4,5), new Pair<>(4,4), new Pair<>(4,3), new Pair<>(4,2), new Pair<>(4,1), new Pair<>(4,0),
				new Pair<>(3,3), new Pair<>(3,2)	
			));
	
	private static GLProxy instance = null;
	
	
	/** Minecraft's GLFW window */
	public final long minecraftGlContext;
	/** Minecraft's GL capabilities */
	public final GLCapabilities minecraftGlCapabilities;
	
	/** the LodBuilder's GLFW window */
	public final long lodBuilderGlContext;
	/** the LodBuilder's GL capabilities */
	public final GLCapabilities lodBuilderGlCapabilities;
	
	/** the proxyWorker's GLFW window */
	public final long proxyWorkerGlContext;
	/** the proxyWorker's GL capabilities */
	public final GLCapabilities proxyWorkerGlCapabilities;
	
	public boolean namedObjectSupported = false; // ~OpenGL 4.5 (UNUSED CURRENTLY)
	public boolean bufferStorageSupported = false; // ~OpenGL 4.4
	public boolean VertexAttributeBufferBindingSupported = false; // ~OpenGL 4.3
	
	private final EGpuUploadMethod preferredUploadMethod;
	
	public final GLMessage.Builder vanillaDebugMessageBuilder = GLMessage.Builder.DEFAULT_MESSAGE_BUILDER;
	public final GLMessage.Builder lodBuilderDebugMessageBuilder = GLMessage.Builder.DEFAULT_MESSAGE_BUILDER;
	public final GLMessage.Builder proxyWorkerDebugMessageBuilder = GLMessage.Builder.DEFAULT_MESSAGE_BUILDER;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	private GLProxy() throws IllegalStateException
	{
		// this must be created on minecraft's render context to work correctly
		
		GL_LOGGER.info("Creating " + GLProxy.class.getSimpleName() + "... If this is the last message you see there must have been an OpenGL error.");
		GL_LOGGER.info("Lod Render OpenGL version [" + GL32.glGetString(GL32.GL_VERSION) + "].");
		
		// getting Minecraft's context has to be done on the render thread,
		// where the GL context is
		if (GLFW.glfwGetCurrentContext() == 0L)
		{
			throw new IllegalStateException(GLProxy.class.getSimpleName() + " was created outside the render thread!");
		}
		
		
		
		//============================//
		// get Minecraft's GL context //
		//============================//
		
		// get Minecraft's context
		this.minecraftGlContext = GLFW.glfwGetCurrentContext();
		this.minecraftGlCapabilities = GL.getCapabilities();
		
		// crash the game if the GPU doesn't support OpenGL 3.2
		if (!this.minecraftGlCapabilities.OpenGL32)
		{
			String supportedVersionInfo = this.getFailedVersionInfo(this.minecraftGlCapabilities);
			
			// See full requirement at above.
			String errorMessage = ModInfo.READABLE_NAME + " was initializing " + GLProxy.class.getSimpleName()
					+ " and discovered this GPU doesn't meet the OpenGL requirements. Sorry I couldn't tell you sooner :(\n" +
					"Additional info:\n" + supportedVersionInfo;
			MC.crashMinecraft(errorMessage, new UnsupportedOperationException("Distant Horizon OpenGL requirements not met"));
		}
	 	GL_LOGGER.info("minecraftGlCapabilities:\n" + this.versionInfoToString(this.minecraftGlCapabilities));
		
		if (Config.Client.Advanced.Debugging.OpenGl.overrideVanillaGLLogger.get())
		{
			GLUtil.setupDebugMessageCallback(new PrintStream(new GLMessageOutputStream(GLProxy::logMessage, this.vanillaDebugMessageBuilder), true));
		}
		
		
		
		//================================//
		// create the lod builder context //
		//================================//
		
		String contextCreateErrorMessage = "";
		long potentialLodBuilderGlContext = 0;
		GLCapabilities potentialLodBuilderGlCapabilities = null;
		
		int majorGlVersion = Config.Client.Advanced.Debugging.OpenGl.glContextMajorVersion.get();
		int minorGlVersion = Config.Client.Advanced.Debugging.OpenGl.glContextMinorVersion.get();
		
		ArrayList<Pair<Integer, Integer>> glVersions = new ArrayList<>();
		if (majorGlVersion != 0)
		{
			glVersions.add(new Pair<>(majorGlVersion, minorGlVersion));	
		}
		else
		{
			glVersions.addAll(SUPPORTED_GL_VERSIONS);
		}
		
		for (Pair<Integer, Integer> supportedGlVersion : glVersions)
		{
			int glMajorVersion = supportedGlVersion.first;
			int glMinorVersion = supportedGlVersion.second;
			
			GL_LOGGER.info("Attempting to create a context with GL version: ["+glMajorVersion+"."+glMinorVersion+"]");
			
			
			
			GLFW.glfwMakeContextCurrent(0L);
			
			// context creation setup
			GLFW.glfwDefaultWindowHints();
			// make the context window invisible
			GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
			
			boolean debugContextEnabled = Config.Client.Advanced.Debugging.OpenGl.enableGlDebugContext.get();
			boolean forwardCompatEnabled = Config.Client.Advanced.Debugging.OpenGl.enableGlForwardCompatibilityMode.get();
			
			GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, glMajorVersion);
			GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, glMinorVersion);
			GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, debugContextEnabled ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
			
			GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, forwardCompatEnabled ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
			int profileModeInt;
			EGlProfileMode profileModeEnum = Config.Client.Advanced.Debugging.OpenGl.glProfileMode.get();
			switch (profileModeEnum)
			{
				case CORE:
					profileModeInt = GLFW.GLFW_OPENGL_CORE_PROFILE;
					break;
				case COMPAT:
					profileModeInt = GLFW.GLFW_OPENGL_COMPAT_PROFILE;
					break;
				default:
				case ANY:
					profileModeInt = GLFW.GLFW_OPENGL_ANY_PROFILE;
					break;
			}
			GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, profileModeInt);
			
			contextCreateErrorMessage =
					"Failed to create OpenGL GLFW context for OpenGL Version: [" + glMajorVersion + "." + glMinorVersion + "] \n" +
					"with Debugging: [" + (debugContextEnabled ? "Enabled" : "Disabled") + "], \n" +
					"Forward Compatibility: [" + (true ? "Enabled" : "Disabled") + "], \n" +
					"and Profile: [" + profileModeEnum.name() + "]. ";
			
			
			// try creating the Lod Builder context
			potentialLodBuilderGlContext = GLFW.glfwCreateWindow(64, 64, "LOD Builder Window", 0L, this.minecraftGlContext);
			if (potentialLodBuilderGlContext == 0)
			{
				GL_LOGGER.info(contextCreateErrorMessage);
				GL_LOGGER.debug("Minecraft GL Capabilities:\n [\n" + ReflectionUtil.getAllFieldValuesAsString(this.minecraftGlCapabilities) + "\n]\n");
				
				continue;
			}
			
			// create the window
			GLFW.glfwMakeContextCurrent(potentialLodBuilderGlContext);
			GL_LOGGER.info("Successfully created a context with GL version: ["+glMajorVersion+"."+glMinorVersion+"]");
			
			// set and log the capabilities
			potentialLodBuilderGlCapabilities = GL.createCapabilities();
			GL_LOGGER.info("lodBuilderGlCapabilities:\n" + this.versionInfoToString(potentialLodBuilderGlCapabilities));
			// override the GL logger
			GLUtil.setupDebugMessageCallback(new PrintStream(new GLMessageOutputStream(GLProxy::logMessage, this.lodBuilderDebugMessageBuilder), true));
			// clear the context for the next stage
			GLFW.glfwMakeContextCurrent(0L);
			
			break;
		}
		
		if (potentialLodBuilderGlContext == 0)
		{
			// no context was created
			throw new UnsupportedOperationException("ERROR: Unable to create a GL Context using any of the supported GL versions: ["+ StringUtil.join(",", SUPPORTED_GL_VERSIONS) +"]");
		}
		
		this.lodBuilderGlContext = potentialLodBuilderGlContext;
		this.lodBuilderGlCapabilities = potentialLodBuilderGlCapabilities;
		
		
		
		
		//=================================//
		// create the proxy worker context //
		//=================================//
		
		// create the proxyWorker's context
		this.proxyWorkerGlContext = GLFW.glfwCreateWindow(64, 48, "LOD proxy worker Window", 0L, this.minecraftGlContext);
		if (this.proxyWorkerGlContext == 0)
		{
			GL_LOGGER.error(contextCreateErrorMessage + 
					"\n Your OS and GPU Driver may have not support this combination.");
			GL_LOGGER.error("Minecraft GL Capabilities:\n [\n"+ReflectionUtil.getAllFieldValuesAsString(this.minecraftGlCapabilities)+"\n]\n");
			
			throw new UnsupportedOperationException("Forward Compat Core Profile 3.2 creation failure");
		}
		// create the window
		GLFW.glfwMakeContextCurrent(this.proxyWorkerGlContext);
		// set and log the capabilities
		this.proxyWorkerGlCapabilities = GL.createCapabilities();
		GL_LOGGER.info("proxyWorkerGlCapabilities:\n" + this.versionInfoToString(this.lodBuilderGlCapabilities));
		// override the GL logger
		GLUtil.setupDebugMessageCallback(new PrintStream(new GLMessageOutputStream(GLProxy::logMessage, this.proxyWorkerDebugMessageBuilder), true));
		// clear the context for the next stage
		GLFW.glfwMakeContextCurrent(0L);
		
		
		
		//======================//
		// get GPU capabilities //
		//======================//
		
		// get capabilities from a context we use
		this.setGlContext(EGLProxyContext.LOD_BUILDER);
		
		// Check if we can use the make-over version of Vertex Attribute, which is available in GL4.3 or after
		this.VertexAttributeBufferBindingSupported = this.minecraftGlCapabilities.glBindVertexBuffer != 0L; // Nullptr
		
		// UNUSED currently
		// Check if we can use the named version of all calls, which is available in GL4.5 or after
		this.namedObjectSupported = this.minecraftGlCapabilities.glNamedBufferData != 0L; //Nullptr
		
		// get specific capabilities
		// Check if we can use the Buffer Storage, which is available in GL4.4 or after
		this.bufferStorageSupported = this.minecraftGlCapabilities.glBufferStorage != 0L && this.lodBuilderGlCapabilities.glBufferStorage != 0L; // Nullptr
		if (!this.bufferStorageSupported)
		{
			GL_LOGGER.warn("This GPU doesn't support Buffer Storage (OpenGL 4.4), falling back to using other methods.");
		}
		
		// get the best automatic upload method
		String vendor = GL32.glGetString(GL32.GL_VENDOR).toUpperCase(); // example return: "NVIDIA CORPORATION"
		if (vendor.contains("NVIDIA") || vendor.contains("GEFORCE"))
		{
			// NVIDIA card
			this.preferredUploadMethod = this.bufferStorageSupported ? EGpuUploadMethod.BUFFER_STORAGE : EGpuUploadMethod.SUB_DATA;
		}
		else
		{
			// AMD or Intel card
			this.preferredUploadMethod = this.bufferStorageSupported ? EGpuUploadMethod.BUFFER_STORAGE : EGpuUploadMethod.DATA;
		}
		GL_LOGGER.info("GPU Vendor [" + vendor + "], Preferred upload method is [" + this.preferredUploadMethod + "].");
		
		
		
		//==========//
		// clean up //
		//==========//
		
		// Since this is created on the render thread, make sure the Minecraft context is used in the end
		this.setGlContext(EGLProxyContext.MINECRAFT);
		
		// GLProxy creation success
		GL_LOGGER.info(GLProxy.class.getSimpleName() + " creation successful. OpenGL smiles upon you this day.");
	}
	
	
	
	//==================//
	// context handling //
	//==================//
	
	/**
	 * A wrapper function to make switching contexts easier. <br>
	 * Does nothing if the calling thread is already using newContext.
	 */
	public void setGlContext(EGLProxyContext newContext)
	{
		EGLProxyContext currentContext = this.getGlContext();
		
		// we don't have to change the context, we are already there.
		if (currentContext == newContext)
			return;
		
		long contextPointer;
		GLCapabilities newGlCapabilities = null;
		
		// get the pointer(s) for this context
		switch (newContext)
		{
			case LOD_BUILDER:
				contextPointer = this.lodBuilderGlContext;
				newGlCapabilities = this.lodBuilderGlCapabilities;
				break;
			
			case MINECRAFT:
				contextPointer = this.minecraftGlContext;
				newGlCapabilities = this.minecraftGlCapabilities;
				break;
			
			case PROXY_WORKER:
				contextPointer = this.proxyWorkerGlContext;
				newGlCapabilities = this.proxyWorkerGlCapabilities;
				break;
			
			default: // default should never happen, it is just here to make the compiler happy
			case NONE:
				// 0L is equivalent to null
				contextPointer = 0L;
				break;
		}
		
		GLFW.glfwMakeContextCurrent(contextPointer);
		GL.setCapabilities(newGlCapabilities);
	}
	
	/** Returns this thread's OpenGL context. */
	public EGLProxyContext getGlContext()
	{
		long currentContext = GLFW.glfwGetCurrentContext();
		
		
		if (currentContext == this.lodBuilderGlContext)
		{
			return EGLProxyContext.LOD_BUILDER;
		}
		else if (currentContext == this.minecraftGlContext)
		{
			return EGLProxyContext.MINECRAFT;
		}
		else if (currentContext == this.proxyWorkerGlContext)
		{
			return EGLProxyContext.PROXY_WORKER;
		}
		else if (currentContext == 0L)
		{
			return EGLProxyContext.NONE;
		}
		else
		{
			// hopefully this shouldn't happen
			throw new IllegalStateException(Thread.currentThread().getName() +
					" has a unknown OpenGl context: [" + currentContext + "]. "
					+ "Minecraft context [" + this.minecraftGlContext + "], "
					+ "LodBuilder context [" + this.lodBuilderGlContext + "], "
					+ "ProxyWorker context [" + this.proxyWorkerGlContext + "], "
					+ "no context [0].");
		}
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	public static boolean hasInstance() { return instance != null; }
	public static GLProxy getInstance()
	{
		if (instance == null)
		{
			instance = new GLProxy();
		}
		
		return instance;
	}
	
	public EGpuUploadMethod getGpuUploadMethod()
	{
		EGpuUploadMethod method = Config.Client.Advanced.GpuBuffers.gpuUploadMethod.get();
		if (!this.bufferStorageSupported && method == EGpuUploadMethod.BUFFER_STORAGE)
		{
			// if buffer storage isn't supported
			// default to DATA since that is the most compatible
			method = EGpuUploadMethod.DATA;
		}
		return method == EGpuUploadMethod.AUTO ? this.preferredUploadMethod : method;
	}
	
	
	
	//============================//
	// MC render thread runnables //
	//============================//
	
	/**
	 * Asynchronously calls the given runnable on proxy's OpenGL context.
	 * Useful for creating/destroying OpenGL objects in a thread
	 * that doesn't normally have access to a OpenGL context. <br>
	 * No rendering can be done through this method.
	 */
	public void recordOpenGlCall(Runnable renderCall)
	{
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		this.workerThread.execute(() -> this.runnableContainer(renderCall, stackTrace));
	}
	private void runnableContainer(Runnable renderCall, StackTraceElement[] stackTrace)
	{
		try
		{
			// set up the context...
			this.setGlContext(EGLProxyContext.PROXY_WORKER);
			// ...run the actual code...
			renderCall.run();
		}
		catch (Exception e)
		{
			RuntimeException error = new RuntimeException("Uncaught Exception during execution:", e);
			error.setStackTrace(stackTrace);
			GL_LOGGER.error(Thread.currentThread().getName() + " ran into a issue: ", error);
		}
		finally
		{
			// ...and make sure the context is released when the thread finishes
			this.setGlContext(EGLProxyContext.NONE);
		}
	}
	
	public static void ensureAllGLJobCompleted()
	{
		if (!hasInstance())
		{
			return;
		}
		
		
		LOGGER.info("Blocking until GL jobs finished...");
		try
		{
			instance.workerThread.shutdown();
			boolean worked = instance.workerThread.awaitTermination(30, TimeUnit.SECONDS);
			if (!worked)
			{
				LOGGER.error("GLWorkerThread shutdown timed out! Game may crash on exit due to cleanup failure!");
			}
		}
		catch (InterruptedException e)
		{
			LOGGER.error("GLWorkerThread shutdown is interrupted! Game may crash on exit due to cleanup failure!");
			e.printStackTrace();
		}
		finally
		{
			instance.workerThread = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(GLProxy.class.getSimpleName() + "-Worker-Thread").build());
		}
		LOGGER.info("All GL jobs finished!");
	}
	
	
	
	//=========//
	// logging //
	//=========//
	
	private static void logMessage(GLMessage msg)
	{
		EGLErrorHandlingMode errorHandlingMode = Config.Client.Advanced.Debugging.OpenGl.glErrorHandlingMode.get();
		if (errorHandlingMode == EGLErrorHandlingMode.IGNORE)
		{
			return;
		}
		
		
		
		if (msg.type == GLMessage.EType.ERROR || msg.type == GLMessage.EType.UNDEFINED_BEHAVIOR)
		{
			// critical error
			
			GL_LOGGER.error("GL ERROR " + msg.id + " from " + msg.source + ": " + msg.message);
			
			if (errorHandlingMode == EGLErrorHandlingMode.LOG_THROW)
			{
				throw new RuntimeException("GL ERROR: " + msg);
			}
			
		}
		else
		{
			// non-critical log
			
			GLMessage.ESeverity severity = msg.severity;
			RuntimeException ex = new RuntimeException("GL MESSAGE: " + msg);
			
			if (severity == null)
			{
				// just in case the message was malformed
				severity = GLMessage.ESeverity.LOW;
			}
			
			switch (severity)
			{
				case HIGH:
					GL_LOGGER.error("{}", ex);
					break;
				case MEDIUM:
					GL_LOGGER.warn("{}", ex);
					break;
				case LOW:
					GL_LOGGER.info("{}", ex);
					break;
				case NOTIFICATION:
					GL_LOGGER.debug("{}", ex);
					break;
			}
		}
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	private String getFailedVersionInfo(GLCapabilities c)
	{
		return "Your OpenGL support:\n" +
				"openGL version 3.2+: [" + c.OpenGL32 + "] <- REQUIRED\n" +
				"Vertex Attribute Buffer Binding: [" + (c.glVertexAttribBinding != 0) + "] <- optional improvement\n" +
				"Buffer Storage: [" + (c.glBufferStorage != 0) + "] <- optional improvement\n" +
				"If you noticed that your computer supports higher OpenGL versions"
				+ " but not the required version, try running the game in compatibility mode."
				+ " (How you turn that on, I have no clue~)";
	}
	
	private String versionInfoToString(GLCapabilities c)
	{
		return "Your OpenGL support:\n" +
				"openGL version 3.2+: [" + c.OpenGL32 + "] <- REQUIRED\n" +
				"Vertex Attribute Buffer Binding: [" + (c.glVertexAttribBinding != 0) + "] <- optional improvement\n" +
				"Buffer Storage: [" + (c.glBufferStorage != 0) + "] <- optional improvement\n";
	}
	
	
	
}
