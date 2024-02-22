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

package com.seibel.distanthorizons.common.wrappers.block.cache;

import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.TextureAtlasSpriteWrapper;
import com.seibel.distanthorizons.common.wrappers.block.TintWithoutLevelOverrider;
import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.block.*;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhBlockPos;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
#if POST_MC_1_19_2
import net.minecraft.util.RandomSource;
#else
import java.util.Random;
#endif
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;

/**
 * @version 2022-9-16
 */
public class ClientBlockStateCache
{
	
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private static final HashSet<BlockState> BLOCK_STATES_THAT_NEED_LEVEL = new HashSet<>();
	private static final HashSet<BlockState> BROKEN_BLOCK_STATES = new HashSet<>();
	
	#if PRE_MC_1_19_2
	public static final Random random = new Random(0);
	#else
	public static final RandomSource random = RandomSource.create();
	#endif
	
	public final BlockState blockState;
	public final LevelReader level;
	public final BlockPos pos;
	public ClientBlockStateCache(BlockState blockState, IClientLevelWrapper samplingLevel, DhBlockPos samplingPos)
	{
		this.blockState = blockState;
		level = (LevelReader) samplingLevel.getWrappedMcObject();
		pos = McObjectConverter.Convert(samplingPos);
		resolveColors();
		//LOGGER.info("ClientBlocKCache created for {}", blockState);
	}
	
	boolean isColorResolved = false;
	int baseColor = 0; //TODO: Impl per-face color
	boolean needShade = true;
	boolean needPostTinting = false;
	int tintIndex = 0;
	
	
	public static final int FLOWER_COLOR_SCALE = 5;
	
	enum ColorMode
	{
		Default,
		Flower,
		Leaves;
		static ColorMode getColorMode(Block b)
		{
			if (b instanceof LeavesBlock) return Leaves;
			if (b instanceof FlowerBlock) return Flower;
			return Default;
		}
	}
	
	private static int getWidth(TextureAtlasSprite texture)
	{
        #if PRE_MC_1_19_4
		return texture.getWidth();
        #else
		return texture.contents().width();
        #endif
	}
	
	private static int getHeight(TextureAtlasSprite texture)
	{
        #if PRE_MC_1_19_4
		return texture.getHeight();
        #else
		return texture.contents().height();
        #endif
	}
	
	//TODO: Perhaps make this not just use the first frame?
	private static int calculateColorFromTexture(TextureAtlasSprite texture, ColorMode colorMode)
	{
		int count = 0;
		double alpha = 0;
		double red = 0;
		double green = 0;
		double blue = 0;
		int tempColor;
		{
			// textures normally use u and v instead of x and y
			for (int u = 0; u < getWidth(texture); u++)
			{
				for (int v = 0; v < getHeight(texture); v++)
				{
					//note: Minecraft color format is: 0xAA BB GG RR
					//________ DH mod color format is: 0xAA RR GG BB
					//OpenGL RGBA format native order: 0xRR GG BB AA
					//_ OpenGL RGBA format Java Order: 0xAA BB GG RR
					tempColor = TextureAtlasSpriteWrapper.getPixelRGBA(texture, 0, u, v);
					
					double r = ((tempColor & 0x000000FF)) / 255.;
					double g = ((tempColor & 0x0000FF00) >>> 8) / 255.;
					double b = ((tempColor & 0x00FF0000) >>> 16) / 255.;
					double a = ((tempColor & 0xFF000000) >>> 24) / 255.;
					int scale = 1;
					
					if (colorMode == ColorMode.Leaves)
					{
						r *= a;
						g *= a;
						b *= a;
						a = 1.;
					}
					else if (a == 0.)
					{
						continue;
					}
					else if (colorMode == ColorMode.Flower && (g + 0.1 < b || g + 0.1 < r))
					{
						scale = FLOWER_COLOR_SCALE;
					}
					
					count += scale;
					alpha += a * a * scale;
					red += r * r * scale;
					green += g * g * scale;
					blue += b * b * scale;
				}
			}
		}
		
		if (count == 0)
			// this block is entirely transparent
			tempColor = ColorUtil.rgbToInt(0, 255, 255, 255);
		else
		{
			// determine the average color
			tempColor = ColorUtil.rgbToInt(
					(int) (Math.sqrt(alpha / count) * 255.),
					(int) (Math.sqrt(red / count) * 255.),
					(int) (Math.sqrt(green / count) * 255.),
					(int) (Math.sqrt(blue / count) * 255.));
		}
		return tempColor;
	}
	private static final Direction[] DIRECTION_ORDER = {Direction.UP, Direction.NORTH, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.DOWN};
	
	private void resolveColors()
	{
		if (isColorResolved) return;
		if (blockState.getFluidState().isEmpty())
		{
			List<BakedQuad> quads = null;
			for (Direction direction : DIRECTION_ORDER)
			{
				quads = Minecraft.getInstance().getModelManager().getBlockModelShaper().
						getBlockModel(blockState).getQuads(blockState, direction, random);
				if (quads != null && !quads.isEmpty() &&
						!(blockState.getBlock() instanceof RotatedPillarBlock && direction == Direction.UP))
					break;
			} ;
			if (quads == null || quads.isEmpty())
			{
				quads = Minecraft.getInstance().getModelManager().getBlockModelShaper().
						getBlockModel(blockState).getQuads(blockState, null, random);
			}
			if (quads != null && !quads.isEmpty())
			{
				needPostTinting = quads.get(0).isTinted();
				needShade = quads.get(0).isShade();
				tintIndex = quads.get(0).getTintIndex();
				baseColor = calculateColorFromTexture(
                        #if PRE_MC_1_17_1 quads.get(0).sprite,
						#else quads.get(0).getSprite(), #endif
						ColorMode.getColorMode(blockState.getBlock()));
			}
			else
			{ // Backup method.
				needPostTinting = false;
				needShade = false;
				tintIndex = 0;
				baseColor = calculateColorFromTexture(Minecraft.getInstance().getModelManager().getBlockModelShaper().getParticleIcon(blockState),
						ColorMode.getColorMode(blockState.getBlock()));
			}
		}
		else
		{ // Liquid Block
			needPostTinting = true;
			needShade = false;
			tintIndex = 0;
			baseColor = calculateColorFromTexture(Minecraft.getInstance().getModelManager().getBlockModelShaper().getParticleIcon(blockState),
					ColorMode.getColorMode(blockState.getBlock()));
		}
		isColorResolved = true;
	}
	
	public int getAndResolveFaceColor(BiomeWrapper biome, DhBlockPos pos)
	{
		// FIXME: impl per-face colors
		
		// only get the tint if the block needs to be tinted
		if (!this.needPostTinting)
		{
			return this.baseColor;
		}
		
		// don't try tinting blocks that don't support our method of tint getting
		if (BROKEN_BLOCK_STATES.contains(this.blockState))
		{
			return this.baseColor;
		}
		
		
		// attempt to get the tint
		int tintColor = -1;
		try
		{
			// try to use the fast tint getter logic first
			if (!BLOCK_STATES_THAT_NEED_LEVEL.contains(this.blockState))
			{
				try
				{
					tintColor = Minecraft.getInstance().getBlockColors()
							.getColor(this.blockState, new TintWithoutLevelOverrider(biome), McObjectConverter.Convert(pos), this.tintIndex);
				}
				catch (UnsupportedOperationException e)
				{
					// this exception generally occurs if the tint requires other blocks besides itself
					LOGGER.debug("Unable to use ["+TintWithoutLevelOverrider.class.getSimpleName()+"] to get the block tint for block: [" + this.blockState + "] and biome: [" + biome + "] at pos: " + pos + ". Error: [" + e.getMessage() + "]. Attempting to use backup method...", e);
					BLOCK_STATES_THAT_NEED_LEVEL.add(this.blockState);
				}
			}
			
			// use the level logic only if requested
			if (BLOCK_STATES_THAT_NEED_LEVEL.contains(this.blockState))
			{
				// this logic can't be used all the time due to it breaking some blocks tinting
				// specifically oceans don't render correctly
				tintColor = Minecraft.getInstance().getBlockColors()
						.getColor(this.blockState, new TintGetterOverrideFast(this.level), McObjectConverter.Convert(pos), this.tintIndex);
			}
		}
		catch (Exception e)
		{
			// only display the error once per block/biome type to reduce log spam
			if (!BROKEN_BLOCK_STATES.contains(this.blockState))
			{
				LOGGER.warn("Failed to get block color for block: [" + this.blockState + "] and biome: [" + biome + "] at pos: " + pos + ". Error: ["+e.getMessage() + "]. Note: future errors for this block/biome will be ignored.", e);
				BROKEN_BLOCK_STATES.add(this.blockState);
			}
		}
		
		
		
		if (tintColor != -1)
		{
			return ColorUtil.multiplyARGBwithRGB(this.baseColor, tintColor);
		}
		else
		{
			// unable to get the tinted color, use the base color instead
			return this.baseColor;
		}
	}
	
}
