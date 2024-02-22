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

package com.seibel.distanthorizons.core.render;

import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.Pos2D;
import com.seibel.distanthorizons.core.util.objects.SortedArraySet;
import com.seibel.distanthorizons.core.util.objects.quadTree.QuadNode;
import com.seibel.distanthorizons.core.render.renderer.LodRenderer;
import com.seibel.distanthorizons.coreapi.util.math.Vec3f;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This object tells the {@link LodRenderer} what buffers to render
 * TODO rename this class, maybe RenderBufferOrganizer or something more specific?
 */
public class RenderBufferHandler
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	/** contains all relevant data */
	public final LodQuadTree lodQuadTree;
	
	// TODO: Make sorting go into the update loop instead of the render loop as it doesn't need to be done every frame
	private SortedArraySet<LoadedRenderBuffer> loadedNearToFarBuffers = null;
	
	private final AtomicBoolean rebuildAllBuffers = new AtomicBoolean(false);
	
	
	public RenderBufferHandler(LodQuadTree lodQuadTree) { this.lodQuadTree = lodQuadTree; }
	
	
	
	/**
	 * The following buildRenderList sorting method is based on the following reddit post: <br>
	 * <a href="https://www.reddit.com/r/VoxelGameDev/comments/a0l8zc/correct_depthordering_for_translucent_discrete/">correct_depth_ordering_for_translucent_discrete</a> <br><br>
	 *
	 * TODO: This might get locked by update() causing move() call. Is there a way to avoid this?
	 *       Maybe dupe the base list and use atomic swap on render? Or is this not worth it?
	 */
	public void buildRenderListAndUpdateSections(Vec3f lookForwardVector)
	{
		EDhDirection[] axisDirections = new EDhDirection[3];
		
		// Do the axis that are the longest first (i.e. the largest absolute value of the lookForwardVector),
		// with the sign being the opposite of the respective lookForwardVector component's sign
		float absX = Math.abs(lookForwardVector.x);
		float absY = Math.abs(lookForwardVector.y);
		float absZ = Math.abs(lookForwardVector.z);
		EDhDirection xDir = lookForwardVector.x < 0 ? EDhDirection.EAST : EDhDirection.WEST;
		EDhDirection yDir = lookForwardVector.y < 0 ? EDhDirection.UP : EDhDirection.DOWN;
		EDhDirection zDir = lookForwardVector.z < 0 ? EDhDirection.SOUTH : EDhDirection.NORTH;
		
		if (absX >= absY && absX >= absZ)
		{
			axisDirections[0] = xDir;
			if (absY >= absZ)
			{
				axisDirections[1] = yDir;
				axisDirections[2] = zDir;
			}
			else
			{
				axisDirections[1] = zDir;
				axisDirections[2] = yDir;
			}
		}
		else if (absY >= absX && absY >= absZ)
		{
			axisDirections[0] = yDir;
			if (absX >= absZ)
			{
				axisDirections[1] = xDir;
				axisDirections[2] = zDir;
			}
			else
			{
				axisDirections[1] = zDir;
				axisDirections[2] = xDir;
			}
		}
		else
		{
			axisDirections[0] = zDir;
			if (absX >= absY)
			{
				axisDirections[1] = xDir;
				axisDirections[2] = yDir;
			}
			else
			{
				axisDirections[1] = yDir;
				axisDirections[2] = xDir;
			}
		}
		
		Pos2D cPos = this.lodQuadTree.getCenterBlockPos().toPos2D();
		
		// Now that we have the axis directions, we can sort the render list
		Comparator<LoadedRenderBuffer> farToNearComparator = (loadedBufferA, loadedBufferB) ->
		{
			Pos2D aPos = loadedBufferA.pos.getCenterBlockPos().toPos2D();
			Pos2D bPos = loadedBufferB.pos.getCenterBlockPos().toPos2D();
			if (true)
			{
				int aManhattanDistance = aPos.manhattanDist(cPos);
				int bManhattanDistance = bPos.manhattanDist(cPos);
				return bManhattanDistance - aManhattanDistance;
			}
			
			for (EDhDirection axisDirection : axisDirections)
			{
				if (axisDirection.getAxis().isVertical())
				{
					continue; // We only sort in the horizontal direction
				}
				
				int abPosDifference;
				if (axisDirection.getAxis().equals(EDhDirection.Axis.X))
				{
					abPosDifference = aPos.x - bPos.x;
				}
				else
				{
					abPosDifference = aPos.y - bPos.y;
				}
				
				if (abPosDifference == 0)
				{
					continue;
				}
				
				if (axisDirection.getAxisDirection().equals(EDhDirection.AxisDirection.NEGATIVE))
				{
					abPosDifference = -abPosDifference; // Reverse the sign
				}
				return abPosDifference;
			}
			
			return loadedBufferA.pos.getDetailLevel() - loadedBufferB.pos.getDetailLevel(); // If all else fails, sort by detail
		};
		
		// Build the sorted list
		this.loadedNearToFarBuffers = new SortedArraySet<>((a, b) -> -farToNearComparator.compare(a, b)); // TODO is the comparator named wrong?
		
		// Update the sections
		boolean rebuildAllBuffers = this.rebuildAllBuffers.getAndSet(false);
		Iterator<QuadNode<LodRenderSection>> nodeIterator = this.lodQuadTree.nodeIterator();
		while (nodeIterator.hasNext())
		{
			QuadNode<LodRenderSection> node = nodeIterator.next();
			
			DhSectionPos sectionPos = node.sectionPos;
			LodRenderSection renderSection = node.value;
			try
			{
				
				if (renderSection != null)
				{
					if (rebuildAllBuffers)
					{
						renderSection.markBufferDirty();
					}
					renderSection.tryBuildAndSwapBuffer();
					
					if (renderSection.isRenderingEnabled())
					{
						AbstractRenderBuffer buffer = renderSection.activeRenderBufferRef.get();
						if (buffer != null)
						{
							this.loadedNearToFarBuffers.add(new LoadedRenderBuffer(buffer, sectionPos));
						}
					}
				}
			}
			catch (Exception e)
			{
				LOGGER.error("Error updating QuadTree render source at " + renderSection.pos + ".", e);
				renderSection.markBufferDirty();
			}
		}
	}
	
	public void renderOpaque(LodRenderer renderContext)
	{
		//TODO: Directional culling
		this.loadedNearToFarBuffers.forEach(loadedBuffer -> loadedBuffer.buffer.renderOpaque(renderContext));
	}
	public void renderTransparent(LodRenderer renderContext)
	{
		//TODO: Directional culling
		ListIterator<LoadedRenderBuffer> iter = this.loadedNearToFarBuffers.listIterator(this.loadedNearToFarBuffers.size());
		while (iter.hasPrevious())
		{
			LoadedRenderBuffer loadedBuffer = iter.previous();
			loadedBuffer.buffer.renderTransparent(renderContext);
		}
	}
	
	public void MarkAllBuffersDirty()
	{
		rebuildAllBuffers.set(true);
	}
	
	
	public void close()
	{
		Iterator<QuadNode<LodRenderSection>> nodeIterator = this.lodQuadTree.nodeIterator();
		while (nodeIterator.hasNext())
		{
			LodRenderSection renderSection = nodeIterator.next().value;
			if (renderSection != null)
			{
				renderSection.dispose();
			}
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	private static class LoadedRenderBuffer
	{
		public final AbstractRenderBuffer buffer;
		public final DhSectionPos pos;
		
		LoadedRenderBuffer(AbstractRenderBuffer buffer, DhSectionPos pos)
		{
			this.buffer = buffer;
			this.pos = pos;
		}
		
	}
	
}
