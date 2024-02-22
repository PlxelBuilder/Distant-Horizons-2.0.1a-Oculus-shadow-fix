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

import com.seibel.distanthorizons.api.enums.config.EHorizontalQuality;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.file.renderfile.IRenderSourceProvider;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.objects.quadTree.QuadNode;
import com.seibel.distanthorizons.core.util.objects.quadTree.QuadTree;
import com.seibel.distanthorizons.coreapi.util.MathUtil;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This quadTree structure is our core data structure and holds
 * all rendering data.
 */
public class LodQuadTree extends QuadTree<LodRenderSection> implements AutoCloseable
{
	public static final byte TREE_LOWEST_DETAIL_LEVEL = ColumnRenderSource.SECTION_SIZE_OFFSET;
	
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public final int blockRenderDistanceDiameter;
	private final IRenderSourceProvider renderSourceProvider;
	
	/**
	 * This holds every {@link DhSectionPos} that should be reloaded next tick. <br>
	 * This is a {@link ConcurrentLinkedQueue} because new sections can be added to this list via the world generator threads.
	 */
	private final ConcurrentLinkedQueue<DhSectionPos> sectionsToReload = new ConcurrentLinkedQueue<>();
	private final IDhClientLevel level; //FIXME: Proper hierarchy to remove this reference!
	private final ConfigChangeListener<EHorizontalQuality> horizontalScaleChangeListener;
	private final ReentrantLock treeReadWriteLock = new ReentrantLock();
	
	/** the smallest numerical detail level number that can be rendered */
	private byte maxRenderDetailLevel;
	/** the largest numerical detail level number that can be rendered */
	private byte minRenderDetailLevel;
	
	/** used to calculate when a detail drop will occur */
	private double detailDropOffDistanceUnit;
	/** used to calculate when a detail drop will occur */
	private double detailDropOffLogBase;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public LodQuadTree(
			IDhClientLevel level, int viewDiameterInBlocks,
			int initialPlayerBlockX, int initialPlayerBlockZ,
			IRenderSourceProvider provider)
	{
		super(viewDiameterInBlocks, new DhBlockPos2D(initialPlayerBlockX, initialPlayerBlockZ), TREE_LOWEST_DETAIL_LEVEL);
		
		this.level = level;
		this.renderSourceProvider = provider;
		this.blockRenderDistanceDiameter = viewDiameterInBlocks;
		
		this.horizontalScaleChangeListener = new ConfigChangeListener<>(Config.Client.Advanced.Graphics.Quality.horizontalQuality, (newHorizontalScale) -> this.onHorizontalQualityChange());
	}
	
	
	
	//=============//
	// tick update //
	//=============//
	
	/**
	 * This function updates the quadTree based on the playerPos and the current game configs (static and global)
	 *
	 * @param playerPos the reference position for the player
	 */
	public void tick(DhBlockPos2D playerPos)
	{
		if (this.level == null)
		{
			// the level hasn't finished loading yet
			// TODO sometimes null pointers still happen, when logging back into a world (maybe the old level isn't null but isn't valid either?)
			return;
		}
		
		
		
		// this shouldn't be updated while the tree is being iterated through
		this.updateDetailLevelVariables();
		
		// don't traverse the tree if it is being modified
		if (this.treeReadWriteLock.tryLock())
		{
			try
			{
				// recenter if necessary, removing out of bounds sections
				this.setCenterBlockPos(playerPos, LodRenderSection::dispose);
				
				this.updateAllRenderSections(playerPos);
			}
			catch (Exception e)
			{
				LOGGER.error("Quad Tree tick exception for dimension: " + this.level.getClientLevelWrapper().getDimensionType().getDimensionName() + ", exception: " + e.getMessage(), e);
			}
			finally
			{
				this.treeReadWriteLock.unlock();
			}
		}
	}
	private void updateAllRenderSections(DhBlockPos2D playerPos)
	{
		// reload any sections that need it
		DhSectionPos pos;
		while ((pos = this.sectionsToReload.poll()) != null)
		{
			// walk up the tree until we hit the root node
			// this is done so any high detail changes flow up to the lower detail render sections as well
			while (pos.getDetailLevel() <= this.treeMinDetailLevel)
			{
				try
				{
					LodRenderSection renderSection = this.getValue(pos);
					if (renderSection != null)
					{
						renderSection.reload(this.renderSourceProvider);
					}
				}
				catch (IndexOutOfBoundsException e)
				{ /* the section is now out of bounds, it doesn't need to be reloaded */ }
				
				pos = pos.getParentPos();
			}
		}
		
		
		// walk through each root node
		Iterator<DhSectionPos> rootPosIterator = this.rootNodePosIterator();
		while (rootPosIterator.hasNext())
		{
			// make sure all root nodes have been created
			DhSectionPos rootPos = rootPosIterator.next();
			if (this.getNode(rootPos) == null)
			{
				this.setValue(rootPos, new LodRenderSection(this, rootPos));
			}
			
			QuadNode<LodRenderSection> rootNode = this.getNode(rootPos);
			this.recursivelyUpdateRenderSectionNode(playerPos, rootNode, rootNode, rootNode.sectionPos, false);
		}
	}
	/** @return whether the current position is able to render (note: not if it IS rendering, just if it is ABLE to.) */
	private boolean recursivelyUpdateRenderSectionNode(
			DhBlockPos2D playerPos, 
			QuadNode<LodRenderSection> rootNode, QuadNode<LodRenderSection> quadNode, DhSectionPos sectionPos, 
			boolean parentRenderSectionIsEnabled)
	{
		//===============================//
		// node and render section setup //
		//===============================//
		
		// make sure the node is created
		if (quadNode == null && this.isSectionPosInBounds(sectionPos)) // the position bounds should only fail when at the edge of the user's render distance
		{
			rootNode.setValue(sectionPos, new LodRenderSection(this, sectionPos));
			quadNode = rootNode.getNode(sectionPos);
		}
		if (quadNode == null)
		{
			// this node must be out of bounds, or there was an issue adding it to the tree
			return false;
		}
		
		// make sure the render section is created
		LodRenderSection renderSection = quadNode.value;
		// create a new render section if missing
		if (renderSection == null)
		{
			LodRenderSection newRenderSection = new LodRenderSection(this, sectionPos);
			rootNode.setValue(sectionPos, newRenderSection);
			
			renderSection = newRenderSection;
		}
		
		
		
		
		//===============================//
		// handle enabling, loading,     //
		// and disabling render sections //
		//===============================//

		//byte expectedDetailLevel = DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL + 3; // can be used instead of the following logic for testing
		byte expectedDetailLevel = this.calculateExpectedDetailLevel(playerPos, sectionPos);
		expectedDetailLevel = (byte) Math.min(expectedDetailLevel, this.minRenderDetailLevel);
		expectedDetailLevel += DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL;
		
		
		if (sectionPos.getDetailLevel() > expectedDetailLevel)
		{
			// section detail level too high //
			boolean canThisPosRender = renderSection.isRenderingEnabled();
			boolean allChildrenSectionsAreLoaded = true;
			
			// recursively update all child render sections
			Iterator<DhSectionPos> childPosIterator = quadNode.getChildPosIterator();
			while (childPosIterator.hasNext())
			{
				DhSectionPos childPos = childPosIterator.next();
				QuadNode<LodRenderSection> childNode = rootNode.getNode(childPos);
				
				boolean childSectionLoaded = this.recursivelyUpdateRenderSectionNode(playerPos, rootNode, childNode, childPos, canThisPosRender || parentRenderSectionIsEnabled);
				allChildrenSectionsAreLoaded = childSectionLoaded && allChildrenSectionsAreLoaded;
			}
			
			if (!allChildrenSectionsAreLoaded)
			{
				// not all child positions are loaded yet, or this section is out of render range
				return canThisPosRender;
			}
			else
			{
				// all child positions are loaded, disable this section and enable its children.
				renderSection.disableRendering();
				renderSection.disposeRenderData();
				
				// walk back down the tree and enable the child sections //TODO there are probably more efficient ways of doing this, but this will work for now
				childPosIterator = quadNode.getChildPosIterator();
				while (childPosIterator.hasNext())
				{
					DhSectionPos childPos = childPosIterator.next();
					QuadNode<LodRenderSection> childNode = rootNode.getNode(childPos);
					
					boolean childSectionLoaded = this.recursivelyUpdateRenderSectionNode(playerPos, rootNode, childNode, childPos, parentRenderSectionIsEnabled);
					allChildrenSectionsAreLoaded = childSectionLoaded && allChildrenSectionsAreLoaded;
				}
				if (!allChildrenSectionsAreLoaded)
				{
					// FIXME having world generation enabled in a pre-generated world that doesn't have any DH data can cause this to happen
					//  surprisingly reloadPos() doesn't appear to be the culprit, maybe there is an issue with reloading/changing the full data source?
					//LOGGER.warn("Potential QuadTree concurrency issue. All child sections should be enabled and ready to render for pos: "+sectionPos);
				}
				
				// this section is now being rendered via its children
				return true;
			}
		}
		// TODO this should only equal the expected detail level, the (expectedDetailLevel-1) is a temporary fix to prevent corners from being cut out 
		else if (sectionPos.getDetailLevel() == expectedDetailLevel || sectionPos.getDetailLevel() == expectedDetailLevel - 1)
		{
			/* Can be uncommented to easily debug a single render section. */ 
			/* Don't forget the disableRendering() at the bottom though. */
			//if (sectionPos.getDetailLevel() == 10
			//	&&
			//	(
			//			sectionPos.getX() == 0 &&
			//			sectionPos.getZ() == -4
			//	))
			{
				// this is the detail level we want to render //
				// prepare this section for rendering
				renderSection.loadRenderSource(this.renderSourceProvider, this.level); // TODO this should fire for the lowest detail level first, wait for it to finish then fire the next highest to prevent waiting forever for 2 million chunk section to finish sampling everything
				
				// wait for the parent to disable before enabling this section, so we don't overdraw/overlap render sections
				if (!parentRenderSectionIsEnabled && renderSection.canRenderNow())
				{
					// if rendering is already enabled we don't have to re-enable it
					if (!renderSection.isRenderingEnabled())
					{
						renderSection.enableRendering();
						
						// delete/disable children, all of them will be a lower detail level than requested
						quadNode.deleteAllChildren((childRenderSection) ->
						{
							if (childRenderSection != null)
							{
								childRenderSection.disableRendering();
								childRenderSection.disposeRenderData();
							}
						});
					}
				}
			}
			//else
			//{
			//	renderSection.disableRendering();
			//}
			
			return renderSection.canRenderNow();
		}
		else
		{
			throw new IllegalStateException("LodQuadTree shouldn't be updating renderSections below the expected detail level: [" + expectedDetailLevel + "].");
		}
	}
	
	
	
	//====================//
	// detail level logic //
	//====================//
	
	/**
	 * This method will compute the detail level based on player position and section pos
	 * Override this method if you want to use a different algorithm
	 *
	 * @param playerPos player position as a reference for calculating the detail level
	 * @param sectionPos section position
	 * @return detail level of this section pos
	 */
	public byte calculateExpectedDetailLevel(DhBlockPos2D playerPos, DhSectionPos sectionPos) { return this.getDetailLevelFromDistance(playerPos.dist(sectionPos.getCenterBlockPosX(), sectionPos.getCenterBlockPosZ())); }
	private byte getDetailLevelFromDistance(double distance)
	{
		// special case, never drop the quality
		if (Config.Client.Advanced.Graphics.Quality.horizontalQuality.get() == EHorizontalQuality.UNLIMITED)
		{
			return this.maxRenderDetailLevel;
		}
		
		
		double maxDetailDistance = this.getDrawDistanceFromDetail(Byte.MAX_VALUE - 1);
		if (distance > maxDetailDistance)
		{
			return Byte.MAX_VALUE - 1;
		}
		
		
		int detailLevel = (int) (Math.log(distance / this.detailDropOffDistanceUnit) / this.detailDropOffLogBase);
		return (byte) MathUtil.clamp(this.maxRenderDetailLevel, detailLevel, Byte.MAX_VALUE - 1);
	}
	
	private double getDrawDistanceFromDetail(int detail)
	{
		if (detail <= this.maxRenderDetailLevel)
		{
			return 0;
		}
		else if (detail >= Byte.MAX_VALUE)
		{
			return this.blockRenderDistanceDiameter * 2;
		}
		
		
		double base = Config.Client.Advanced.Graphics.Quality.horizontalQuality.get().quadraticBase;
		return Math.pow(base, detail) * this.detailDropOffDistanceUnit;
	}
	
	private void updateDetailLevelVariables()
	{
		this.detailDropOffDistanceUnit = Config.Client.Advanced.Graphics.Quality.horizontalQuality.get().distanceUnitInBlocks * LodUtil.CHUNK_WIDTH;
		this.detailDropOffLogBase = Math.log(Config.Client.Advanced.Graphics.Quality.horizontalQuality.get().quadraticBase);
		
		this.maxRenderDetailLevel = Config.Client.Advanced.Graphics.Quality.maxHorizontalResolution.get().detailLevel;
		
		// The minimum detail level is done to prevent single corner sections rendering 1 detail level lower than the others.
		// If not done corners may not be flush with the other LODs, which looks bad.
		byte minSectionDetailLevel = this.getDetailLevelFromDistance(this.blockRenderDistanceDiameter); // get the minimum allowed detail level
		minSectionDetailLevel -= 1; // -1 so corners can't render lower than their adjacent neighbors. space
		minSectionDetailLevel = (byte) Math.min(minSectionDetailLevel, this.treeMinDetailLevel); // don't allow rendering lower detail sections than what the tree contains
		this.minRenderDetailLevel = (byte) Math.max(minSectionDetailLevel, this.maxRenderDetailLevel); // respect the user's selected max resolution if it is lower detail (IE they want 2x2 block, but minSectionDetailLevel is specifically for 1x1 block render resolution)
	}
	
	
	
	//=============//
	// render data //
	//=============//
	
	/**
	 * Re-creates the color, render data.
	 * This method should be called after resource packs are changed or LOD settings are modified.
	 */
	public void clearRenderDataCache()
	{
		if (this.treeReadWriteLock.tryLock())
		{
			try
			{
				LOGGER.info("Clearing render cache...");
				
				// clear the tree
				Iterator<QuadNode<LodRenderSection>> nodeIterator = this.nodeIterator();
				while (nodeIterator.hasNext())
				{
					QuadNode<LodRenderSection> quadNode = nodeIterator.next();
					if (quadNode.value != null)
					{
						quadNode.value.disposeRenderData();
						quadNode.value = null;
					}
				}
				
				// delete the cache files
				// TODO this will only delete the files for this level/world
				this.renderSourceProvider.deleteRenderCache();
				
				LOGGER.info("Render cache invalidated, please wait a moment for everything to reload...");
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected error when clearing LodQuadTree render cache: " + e.getMessage(), e);
			}
			finally
			{
				this.treeReadWriteLock.unlock();
			}
		}
	}
	
	/**
	 * Can be called whenever a render section's data needs to be refreshed. <br>
	 * This should be called whenever a world generation task is completed or if the connected server has new data to show.
	 */
	public void reloadPos(DhSectionPos pos)
	{
		if (pos == null)
		{
			// shouldn't happen, but James saw it happen once, so this is here just in case
			LOGGER.warn("reloadPos given a null pos.");
			return;
		}
		
		//LOGGER.info("LodQuadTree reloadPos ["+pos+"].");
		this.sectionsToReload.add(pos);
	}
	
	
	
	//==================//
	// config listeners //
	//==================//
	
	private void onHorizontalQualityChange()
	{
		this.clearRenderDataCache();
	}
	
	
	
	//==============//
	// base methods //
	//==============//
	
	@Override
	public void close()
	{
		LOGGER.info("Shutting down " + LodQuadTree.class.getSimpleName() + "...");
		
		this.horizontalScaleChangeListener.close();
		
		Iterator<QuadNode<LodRenderSection>> nodeIterator = this.nodeIterator();
		while (nodeIterator.hasNext())
		{
			QuadNode<LodRenderSection> quadNode = nodeIterator.next();
			if (quadNode.value != null)
			{
				quadNode.value.dispose();
				quadNode.value = null;
			}
		}
		
		LOGGER.info("Finished shutting down " + LodQuadTree.class.getSimpleName());
	}
	
}
