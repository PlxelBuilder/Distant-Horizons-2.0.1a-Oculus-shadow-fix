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

import com.seibel.distanthorizons.api.enums.config.EGpuUploadMethod;
import com.seibel.distanthorizons.api.enums.config.ELoggerMode;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.logging.ConfigBasedSpamLogger;
import com.seibel.distanthorizons.core.pos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhLodPos;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.glObject.GLState;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLElementBuffer;
import com.seibel.distanthorizons.core.render.glObject.buffer.GLVertexBuffer;
import com.seibel.distanthorizons.core.render.glObject.shader.ShaderProgram;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.AbstractVertexAttribute;
import com.seibel.distanthorizons.core.render.glObject.vertexAttribute.VertexPointer;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper;
import com.seibel.distanthorizons.coreapi.util.math.Mat4f;
import com.seibel.distanthorizons.coreapi.util.math.Vec3d;
import com.seibel.distanthorizons.coreapi.util.math.Vec3f;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL32;

import java.awt.*;
import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

public class DebugRenderer
{
	public static DebugRenderer INSTANCE = new DebugRenderer();
	
	public static final ConfigBasedLogger logger = new ConfigBasedLogger(
			LogManager.getLogger(TestRenderer.class), () -> ELoggerMode.LOG_ALL_TO_CHAT);
	public static final ConfigBasedSpamLogger spamLogger = new ConfigBasedSpamLogger(
			LogManager.getLogger(TestRenderer.class), () -> ELoggerMode.LOG_ALL_TO_CHAT, 1);
	private static final IMinecraftRenderWrapper MC_RENDER = SingletonInjector.INSTANCE.get(IMinecraftRenderWrapper.class);
	
	
	// rendering setup
	private ShaderProgram basicShader;
	private GLVertexBuffer boxBuffer;
	private GLElementBuffer boxOutlineBuffer;
	private AbstractVertexAttribute va;
	private boolean init = false;
	
	// used when rendering
	private Mat4f transformThiFrame;
	private Vec3f camPosFloatThisFrame;
	
	
	private final RendererLists rendererLists = new RendererLists();
	private final PriorityBlockingQueue<BoxParticle> particles = new PriorityBlockingQueue<>();
	
	
	
	// A box from 0,0,0 to 1,1,1
	private static final float[] box_vertices = {
			// Pos x y z
			0, 0, 0,
			1, 0, 0,
			1, 1, 0,
			0, 1, 0,
			0, 0, 1,
			1, 0, 1,
			1, 1, 1,
			0, 1, 1,
	};
	
	private static final int[] box_outline_indices = {
			0, 1,
			1, 2,
			2, 3,
			3, 0,
			
			4, 5,
			5, 6,
			6, 7,
			7, 4,
			
			0, 4,
			1, 5,
			2, 6,
			3, 7,
	};
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public DebugRenderer() { }
	
	
	
	//==============//
	// registration //
	//==============//
	
	public static void makeParticle(BoxParticle particle)
	{
		if (INSTANCE != null && Config.Client.Advanced.Debugging.DebugWireframe.enableRendering.get())
		{
			INSTANCE.particles.add(particle);
		}
	}
	
	public static void register(IDebugRenderable renderable, ConfigEntry<Boolean> config) { if (INSTANCE != null) { INSTANCE.addRenderer(renderable, config); } }
	public void addRenderer(IDebugRenderable renderable, ConfigEntry<Boolean> config) { this.rendererLists.addRenderable(renderable, config); }
	
	public static void unregister(IDebugRenderable renderable, ConfigEntry<Boolean> config) { if (INSTANCE != null) { INSTANCE.removeRenderer(renderable, config); } }
	private void removeRenderer(IDebugRenderable renderable, ConfigEntry<Boolean> config) { this.rendererLists.removeRenderable(renderable, config); }
	
	public static void clearRenderables() { INSTANCE.rendererLists.clearRenderables(); }
	
	
	
	
	
	//===========//
	// rendering //
	//===========//
	
	public void init()
	{
		if (this.init)
		{
			return;
		}
		
		this.init = true;
		this.va = AbstractVertexAttribute.create();
		this.va.bind();
		// Pos
		this.va.setVertexAttribute(0, 0, VertexPointer.addVec3Pointer(false));
		this.va.completeAndCheck(Float.BYTES * 3);
		this.basicShader = new ShaderProgram("shaders/debug/vert.vert", "shaders/debug/frag.frag",
				"fragColor", new String[]{"vPosition"});
		this.createBuffer();
	}
	
	private void createBuffer()
	{
		ByteBuffer buffer = ByteBuffer.allocateDirect(box_vertices.length * Float.BYTES);
		buffer.order(ByteOrder.nativeOrder());
		buffer.asFloatBuffer().put(box_vertices);
		buffer.rewind();
		
		this.boxBuffer = new GLVertexBuffer(false);
		this.boxBuffer.bind();
		this.boxBuffer.uploadBuffer(buffer, 8, EGpuUploadMethod.DATA, box_vertices.length * Float.BYTES);
		
		buffer = ByteBuffer.allocateDirect(box_outline_indices.length * Integer.BYTES);
		buffer.order(ByteOrder.nativeOrder());
		buffer.asIntBuffer().put(box_outline_indices);
		buffer.rewind();
		
		this.boxOutlineBuffer = new GLElementBuffer(false);
		this.boxOutlineBuffer.uploadBuffer(buffer, EGpuUploadMethod.DATA, box_outline_indices.length * Integer.BYTES, GL32.GL_STATIC_DRAW);
	}
	
	public void render(Mat4f transform)
	{
		this.transformThiFrame = transform;
		Vec3d camPos = MC_RENDER.getCameraExactPosition();
		camPosFloatThisFrame = new Vec3f((float) camPos.x, (float) camPos.y, (float) camPos.z);
		
		GLState glState = new GLState();
		this.init();
		
		GL32.glBindFramebuffer(GL32.GL_FRAMEBUFFER, MC_RENDER.getTargetFrameBuffer());
		GL32.glViewport(0, 0, MC_RENDER.getTargetFrameBufferViewportWidth(), MC_RENDER.getTargetFrameBufferViewportHeight());
		GL32.glPolygonMode(GL32.GL_FRONT_AND_BACK, GL32.GL_LINE);
		//GL32.glLineWidth(2);
		GL32.glEnable(GL32.GL_DEPTH_TEST);
		GL32.glDisable(GL32.GL_STENCIL_TEST);
		GL32.glDisable(GL32.GL_BLEND);
		GL32.glDisable(GL32.GL_SCISSOR_TEST);
		
		this.basicShader.bind();
		this.va.bind();
		this.va.bindBufferToAllBindingPoints(this.boxBuffer.getId());
		
		this.boxOutlineBuffer.bind();
		
		this.rendererLists.render(this);
		
		
		BoxParticle head = null;
		while ((head = this.particles.poll()) != null && head.isDead(System.nanoTime()))
		{
		}
		if (head != null)
		{
			this.particles.add(head);
		}
		
		for (BoxParticle particle : this.particles)
		{
			this.renderBox(particle.getBox());
		}
		
		
		glState.restore();
	}
	
	public void renderBox(Box box)
	{
		Mat4f boxTransform = Mat4f.createTranslateMatrix(box.a.x - this.camPosFloatThisFrame.x, box.a.y - this.camPosFloatThisFrame.y, box.a.z - this.camPosFloatThisFrame.z);
		boxTransform.multiply(Mat4f.createScaleMatrix(box.b.x - box.a.x, box.b.y - box.a.y, box.b.z - box.a.z));
		Mat4f t = this.transformThiFrame.copy();
		t.multiply(boxTransform);
		this.basicShader.setUniform(this.basicShader.getUniformLocation("transform"), t);
		this.basicShader.setUniform(this.basicShader.getUniformLocation("uColor"), box.color);
		GL32.glDrawElements(GL32.GL_LINES, box_outline_indices.length, GL32.GL_UNSIGNED_INT, 0);
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	public static final class Box
	{
		public Vec3f a;
		public Vec3f b;
		public Color color;
		
		public Box(Vec3f a, Vec3f b, Color color)
		{
			this.a = a;
			this.b = b;
			this.color = color;
		}
		
		public Box(Vec3f a, Vec3f b, Color color, Vec3f margin)
		{
			this.a = a;
			this.a.add(margin);
			this.b = b;
			this.b.subtract(margin);
			this.color = color;
		}
		
		public Box(DhLodPos pos, float minY, float maxY, float marginPercent, Color color)
		{
			DhBlockPos2D blockMin = pos.getCornerBlockPos();
			DhBlockPos2D blockMax = blockMin.add(pos.getBlockWidth(), pos.getBlockWidth());
			float edge = pos.getBlockWidth() * marginPercent;
			Vec3f a = new Vec3f(blockMin.x + edge, minY, blockMin.z + edge);
			Vec3f b = new Vec3f(blockMax.x - edge, maxY, blockMax.z - edge);
			this.a = a;
			this.b = b;
			this.color = color;
		}
		
		public Box(DhLodPos pos, float y, float yDiff, Object hash, float marginPercent, Color color)
		{
			float hashY = ((float) hash.hashCode() / Integer.MAX_VALUE) * yDiff;
			DhBlockPos2D blockMin = pos.getCornerBlockPos();
			DhBlockPos2D blockMax = blockMin.add(pos.getBlockWidth(), pos.getBlockWidth());
			float edge = pos.getBlockWidth() * marginPercent;
			Vec3f a = new Vec3f(blockMin.x + edge, hashY, blockMin.z + edge);
			Vec3f b = new Vec3f(blockMax.x - edge, hashY, blockMax.z - edge);
			this.a = a;
			this.b = b;
			this.color = color;
		}
		
		public Box(DhSectionPos pos, float minY, float maxY, float marginPercent, Color color)
		{
			this(pos.getSectionBBoxPos(), minY, maxY, marginPercent, color);
		}
		
		public Box(DhSectionPos pos, float y, float yDiff, Object hash, float marginPercent, Color color)
		{
			this(pos.getSectionBBoxPos(), y, yDiff, hash, marginPercent, color);
		}
		
	}
	
	public static final class BoxParticle implements Comparable<BoxParticle>
	{
		public Box box;
		public long startTime;
		public long duration;
		public float yChange;
		
		public BoxParticle(Box box, long startTime, long duration, float yChange)
		{
			this.box = box;
			this.startTime = startTime;
			this.duration = duration;
			this.yChange = yChange;
		}
		
		public BoxParticle(Box box, long nanoSecondDuratoin, float yChange) { this(box, System.nanoTime(), nanoSecondDuratoin, yChange); }
		
		public BoxParticle(Box box, double secondDuration, float yChange) { this(box, System.nanoTime(), (long) (secondDuration * 1000000000), yChange); }
		
		
		@Override
		public int compareTo(@NotNull BoxParticle particle)
		{
			return Long.compare(this.startTime + this.duration, particle.startTime + particle.duration);
		}
		
		public Box getBox()
		{
			long now = System.nanoTime();
			float percent = (now - this.startTime) / (float) this.duration;
			percent = (float) Math.pow(percent, 4);
			float yDiff = this.yChange * percent;
			return new Box(new Vec3f(this.box.a.x, this.box.a.y + yDiff, this.box.a.z), new Vec3f(this.box.b.x, this.box.b.y + yDiff, this.box.b.z), this.box.color);
		}
		
		public boolean isDead(long time) { return (time - this.startTime) > this.duration; }
		
	}
	
	public static final class BoxWithLife implements IDebugRenderable, Closeable
	{
		public Box box;
		public BoxParticle particaleOnClose;
		
		
		public BoxWithLife(Box box, long ns, float yChange, Color deathColor)
		{
			this.box = box;
			this.particaleOnClose = new BoxParticle(new Box(box.a, box.b, deathColor), -1, ns, yChange);
			register(this, null);
		}
		
		
		public BoxWithLife(Box box, long ns, float yChange) { this(box, ns, yChange, box.color); }
		
		public BoxWithLife(Box box, double s, float yChange, Color deathColor)
		{
			this.box = box;
			this.particaleOnClose = new BoxParticle(new Box(box.a, box.b, deathColor), s, yChange);
		}
		
		public BoxWithLife(Box box, double s, float yChange) { this(box, s, yChange, box.color); }
		
		@Override
		public void debugRender(DebugRenderer renderer) { renderer.renderBox(this.box); }
		
		@Override
		public void close()
		{
			makeParticle(new BoxParticle(this.particaleOnClose.getBox(), System.nanoTime(), this.particaleOnClose.duration, this.particaleOnClose.yChange));
			unregister(this, null);
		}
		
	}
	
	
	
	private static class RendererLists
	{
		public final LinkedList<WeakReference<IDebugRenderable>> generalRenderableList = new LinkedList<>();
		
		private final HashMap<ConfigEntry<Boolean>, LinkedList<WeakReference<IDebugRenderable>>> renderableListByConfig = new HashMap<>();
		
		
		
		// registration //
		
		public void addRenderable(IDebugRenderable renderable, @Nullable ConfigEntry<Boolean> config)
		{
			synchronized (this)
			{
				if (config != null)
				{
					if (!this.renderableListByConfig.containsKey(config))
					{
						this.renderableListByConfig.put(config, new LinkedList<>());
					}
					
					LinkedList<WeakReference<IDebugRenderable>> renderableList = this.renderableListByConfig.get(config);
					renderableList.add(new WeakReference<>(renderable));
				}
				else
				{
					this.generalRenderableList.add(new WeakReference<>(renderable));
				}
			}
		}
		
		public void removeRenderable(IDebugRenderable renderable, @Nullable ConfigEntry<Boolean> config)
		{
			synchronized (this)
			{
				if (config != null)
				{
					if (this.renderableListByConfig.containsKey(config))
					{
						LinkedList<WeakReference<IDebugRenderable>> renderableList = this.renderableListByConfig.get(config);
						this.removeRenderableFromInternalList(renderableList, renderable);	
					}
				}
				else
				{
					this.removeRenderableFromInternalList(this.generalRenderableList, renderable);
				}
			}
		}
		private void removeRenderableFromInternalList(LinkedList<WeakReference<IDebugRenderable>> rendererList, IDebugRenderable renderable)
		{
			Iterator<WeakReference<IDebugRenderable>> iterator = rendererList.iterator();
			while (iterator.hasNext())
			{
				WeakReference<IDebugRenderable> renderableRef = iterator.next();
				if (renderableRef.get() == null)
				{
					iterator.remove();
					continue;
				}
				
				if (renderableRef.get() == renderable)
				{
					iterator.remove();
					return;
				}
			}
		}
		
		public void clearRenderables()
		{
			for (ConfigEntry<Boolean> config : this.renderableListByConfig.keySet())
			{
				LinkedList<WeakReference<IDebugRenderable>> renderableList = this.renderableListByConfig.get(config);
				if (config.get() && renderableList != null)
				{
					renderableList.clear();
				}
			}
		}
		
		
		
		// rendering //
		
		public void render(DebugRenderer debugRenderer)
		{
			this.renderList(debugRenderer, this.generalRenderableList);
			
			for (ConfigEntry<Boolean> config : this.renderableListByConfig.keySet())
			{
				LinkedList<WeakReference<IDebugRenderable>> renderableList = this.renderableListByConfig.get(config);
				if (config.get() && renderableList != null && renderableList.size() != 0)
				{
					this.renderList(debugRenderer, renderableList);
				}
			}
		}
		private void renderList(DebugRenderer debugRenderer, LinkedList<WeakReference<IDebugRenderable>> rendererList)
		{
			synchronized (this)
			{
				Iterator<WeakReference<IDebugRenderable>> iterator = rendererList.iterator();
				while (iterator.hasNext())
				{
					WeakReference<IDebugRenderable> ref = iterator.next();
					IDebugRenderable renderable = ref.get();
					if (renderable == null)
					{
						iterator.remove();
						continue;
					}
					
					renderable.debugRender(debugRenderer);
				}
			}
		}
	}
	
}
