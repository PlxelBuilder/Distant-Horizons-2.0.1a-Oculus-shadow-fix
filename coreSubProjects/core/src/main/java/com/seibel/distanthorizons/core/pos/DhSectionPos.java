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

package com.seibel.distanthorizons.core.pos;

import com.seibel.distanthorizons.core.enums.EDhDirection;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import com.seibel.distanthorizons.core.util.LodUtil;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The position object used to define LOD objects in the quad trees. <br><br>
 *
 * A section contains 64 x 64 LOD columns at a given quality.
 * The Section detail level is different from the LOD detail level.
 * For the specifics of how they compare can be viewed in the constants {@link #SECTION_BLOCK_DETAIL_LEVEL},
 * {@link #SECTION_CHUNK_DETAIL_LEVEL}, and {@link #SECTION_REGION_DETAIL_LEVEL}).<br><br>
 *
 * <strong>Why does the smallest render section represent 2x2 MC chunks (section detail level 6)? </strong> <br>
 * A section defines what unit the quad tree works in, because of that we don't want that unit to be too big or too small. <br>
 * <strong>Too small</strong>, and we'll have 1,000s of sections running around, all needing individual files and render buffers.<br>
 * <strong>Too big</strong>, and the LOD dropoff will be very noticeable.<br>
 * With those thoughts in mind we decided on a smallest section size of 32 data points square (IE 2x2 chunks).
 *
 * @author Leetom
 */
public class DhSectionPos
{
	/**
	 * The lowest detail level a Section position can hold.
	 * This section DetailLevel holds 64 x 64 Block level (detail level 0) LODs.
	 */
	public final static byte SECTION_MINIMUM_DETAIL_LEVEL = 6;
	
	public final static byte SECTION_BLOCK_DETAIL_LEVEL = SECTION_MINIMUM_DETAIL_LEVEL + LodUtil.BLOCK_DETAIL_LEVEL;
	public final static byte SECTION_CHUNK_DETAIL_LEVEL = SECTION_MINIMUM_DETAIL_LEVEL + LodUtil.CHUNK_DETAIL_LEVEL;
	public final static byte SECTION_REGION_DETAIL_LEVEL = SECTION_MINIMUM_DETAIL_LEVEL + LodUtil.REGION_DETAIL_LEVEL;
	
	
	protected byte detailLevel;
	
	/** in a sectionDetailLevel grid */
	protected int x;
	/** in a sectionDetailLevel grid */
	protected int z;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public DhSectionPos(byte detailLevel, int x, int z)
	{
		this.detailLevel = detailLevel;
		this.x = x;
		this.z = z;
	}
	
	public DhSectionPos(DhBlockPos blockPos)
	{
		this(LodUtil.BLOCK_DETAIL_LEVEL, blockPos.x, blockPos.z);
		this.convertSelfToDetailLevel(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL);
	}
	public DhSectionPos(DhBlockPos2D blockPos)
	{
		this(LodUtil.BLOCK_DETAIL_LEVEL, blockPos.x, blockPos.z);
		this.convertSelfToDetailLevel(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL);
	}
	
	public DhSectionPos(DhChunkPos chunkPos)
	{
		this(LodUtil.CHUNK_DETAIL_LEVEL, chunkPos.x, chunkPos.z);
		this.convertSelfToDetailLevel(DhSectionPos.SECTION_CHUNK_DETAIL_LEVEL);
	}
	
	public DhSectionPos(byte detailLevel, DhLodPos dhLodPos)
	{
		this.detailLevel = detailLevel;
		this.x = dhLodPos.x;
		this.z = dhLodPos.z;
	}
	
	
	
	//============//
	// converters //
	//============//
	
	/**
	 * uses the absolute detail level aka detail levels like {@link LodUtil#CHUNK_DETAIL_LEVEL} instead of the dhSectionPos detailLevels.
	 *
	 * @return the new position closest to negative infinity with the new detail level
	 */
	public DhSectionPos convertNewToDetailLevel(byte newSectionDetailLevel)
	{
		DhSectionPos newPos = new DhSectionPos(this.detailLevel, this.x, this.z);
		newPos.convertSelfToDetailLevel(newSectionDetailLevel);
		
		return newPos;
	}
	
	/** uses the absolute detail level aka detail levels like {@link LodUtil#CHUNK_DETAIL_LEVEL} instead of the dhSectionPos detailLevels. */
	protected void convertSelfToDetailLevel(byte newDetailLevel)
	{
		// logic originally taken from DhLodPos
		if (newDetailLevel >= this.detailLevel)
		{
			this.x = Math.floorDiv(this.x, BitShiftUtil.powerOfTwo(newDetailLevel - this.detailLevel));
			this.z = Math.floorDiv(this.z, BitShiftUtil.powerOfTwo(newDetailLevel - this.detailLevel));
		}
		else
		{
			this.x = this.x * BitShiftUtil.powerOfTwo(this.detailLevel - newDetailLevel);
			this.z = this.z * BitShiftUtil.powerOfTwo(this.detailLevel - newDetailLevel);
		}
		
		this.detailLevel = newDetailLevel;
	}
	
	
	
	//==================//
	// property getters //
	//==================//
	
	public byte getDetailLevel() { return this.detailLevel; }
	
	public int getX() { return this.x; }
	public int getZ() { return this.z; }
	
	
	
	//=========//
	// getters //
	//=========//
	
	/** @return the corner with the smallest X and Z coordinate */
	public DhLodPos getMinCornerLodPos() { return this.getMinCornerLodPos((byte) (this.detailLevel - 1)); }
	/** @return the corner with the smallest X and Z coordinate */
	public DhLodPos getMinCornerLodPos(byte returnDetailLevel)
	{
		LodUtil.assertTrue(returnDetailLevel <= this.detailLevel, "returnDetailLevel must be less than sectionDetail");
		
		byte offset = (byte) (this.detailLevel - returnDetailLevel);
		return new DhLodPos(returnDetailLevel,
				this.x * BitShiftUtil.powerOfTwo(offset),
				this.z * BitShiftUtil.powerOfTwo(offset));
	}
	
	/** 
	 * A detail level of X lower than this section's detail level will return: <br>
	 * 0 -> 1 <br>
	 * 1 -> 2 <br>
	 * 2 -> 4 <br>
	 * 3 -> 8 <br>
	 * etc.
	 * 
	 * @return how many {@link DhSectionPos}'s at the given detail level it would take to span the width of this section.
	 */
	public int getWidthCountForLowerDetailedSection(byte returnDetailLevel)
	{
		LodUtil.assertTrue(returnDetailLevel <= this.detailLevel, "returnDetailLevel must be less than sectionDetail");
		byte offset = (byte) (this.detailLevel - returnDetailLevel);
		return BitShiftUtil.powerOfTwo(offset);
	}
	
	/** @return how wide this section is in blocks */
	public int getBlockWidth() { return BitShiftUtil.powerOfTwo(this.detailLevel); }
	
	
	public DhBlockPos2D getCenterBlockPos() { return new DhBlockPos2D(this.getCenterBlockPosX(), this.getCenterBlockPosZ()); }
	
	public int getCenterBlockPosX() { return this.getCenterBlockPos(true); }
	public int getCenterBlockPosZ() { return this.getCenterBlockPos(false); }
	private int getCenterBlockPos(boolean returnX)
	{
		int centerBlockPos = returnX ? this.x : this.z;
		
		if (this.detailLevel == 0)
		{
			// already at block detail level, no conversion necessary
			return centerBlockPos;
		}
		
		// we can't get the center of the position at block level, only attempt to get the position offset for detail levels above 0
		int positionOffset = 0;
		if (this.detailLevel != 1)
		{
			positionOffset = BitShiftUtil.powerOfTwo(this.detailLevel - 1);
		}
		
		return (centerBlockPos * BitShiftUtil.powerOfTwo(this.detailLevel)) + positionOffset;
	}
	
	
	
	//==================//
	// parent child pos //
	//==================//
	
	/**
	 * Returns the DhLodPos 1 detail level lower <br><br>
	 *
	 * Relative child positions returned for each index: <br>
	 * 0 = (0,0) - North West <br>
	 * 1 = (1,0) - South West <br>
	 * 2 = (0,1) - North East <br>
	 * 3 = (1,1) - South East <br>
	 *
	 * @param child0to3 must be an int between 0 and 3
	 */
	public DhSectionPos getChildByIndex(int child0to3) throws IllegalArgumentException, IllegalStateException
	{
		if (child0to3 < 0 || child0to3 > 3)
		{
			throw new IllegalArgumentException("child0to3 must be between 0 and 3");
		}
		if (this.detailLevel <= 0)
		{
			throw new IllegalStateException("section detail must be greater than 0");
		}
		
		return new DhSectionPos((byte) (this.detailLevel - 1),
				this.x * 2 + (child0to3 & 1),
				this.z * 2 + BitShiftUtil.half(child0to3 & 2));
	}
	/** Returns this position's child index in its parent */
	public int getChildIndexOfParent() { return (this.x & 1) + BitShiftUtil.square(this.z & 1); }
	
	public DhSectionPos getParentPos() { return new DhSectionPos((byte) (this.detailLevel + 1), BitShiftUtil.half(this.x), BitShiftUtil.half(this.z)); }
	
	
	
	
	public DhSectionPos getAdjacentPos(EDhDirection dir)
	{
		return new DhSectionPos(this.detailLevel,
				this.x + dir.getNormal().x,
				this.z + dir.getNormal().z);
	}
	
	public DhLodPos getSectionBBoxPos() { return new DhLodPos(this.detailLevel, this.x, this.z); }
	
	
	
	//=============//
	// comparisons //
	//=============//
	
	public boolean overlapsExactly(DhSectionPos other)
	{
		// original logic from DhLodPos
		if (this.equals(other))
		{
			return true;
		}
		else if (this.detailLevel == other.detailLevel)
		{
			return false;
		}
		else if (this.detailLevel > other.detailLevel)
		{
			return this.equals(other.convertNewToDetailLevel(this.detailLevel));
		}
		else
		{
			return other.equals(this.convertNewToDetailLevel(other.detailLevel));
		}
	}
	
	public boolean contains(DhSectionPos otherPos)
	{
		DhBlockPos2D thisMinBlockPos = this.getMinCornerLodPos(LodUtil.BLOCK_DETAIL_LEVEL).getCornerBlockPos();
		DhBlockPos2D otherCornerBlockPos = otherPos.getMinCornerLodPos(LodUtil.BLOCK_DETAIL_LEVEL).getCornerBlockPos();
		
		int thisBlockWidth = this.getBlockWidth() - 1; // minus 1 to account for zero based positional indexing
		DhBlockPos2D thisMaxBlockPos = new DhBlockPos2D(thisMinBlockPos.x + thisBlockWidth, thisMinBlockPos.z + thisBlockWidth);
		
		return thisMinBlockPos.x <= otherCornerBlockPos.x && otherCornerBlockPos.x <= thisMaxBlockPos.x &&
				thisMinBlockPos.z <= otherCornerBlockPos.z && otherCornerBlockPos.z <= thisMaxBlockPos.z;
	}
	
	
	
	//===========//
	// iterators //
	//===========//
	
	/** Applies the given consumer to all 4 of this position's children. */
	public void forEachChild(Consumer<DhSectionPos> callback)
	{
		for (int i = 0; i < 4; i++)
		{
			callback.accept(this.getChildByIndex(i));
		}
	}
	
	/** Applies the given consumer to all children of the position at the given section detail level. */
	public void forEachChildAtLevel(byte sectionDetailLevel, Consumer<DhSectionPos> callback)
	{
		if (sectionDetailLevel == this.detailLevel)
		{
			callback.accept(this);
			return;
		}
		
		for (int i = 0; i < 4; i++)
		{
			this.getChildByIndex(i).forEachChildAtLevel(sectionDetailLevel, callback);
		}
	}
	
	
	
	//===============//
	// serialization //
	//===============//
	
	/** Serialize() is different from toString() as it must NEVER be changed, and should be in a short format */
	public String serialize() { return "[" + this.detailLevel + ',' + this.x + ',' + this.z + ']'; }
	
	@Nullable
	public static DhSectionPos deserialize(String value)
	{
		if (value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']') return null;
		String[] split = value.substring(1, value.length() - 1).split(",");
		if (split.length != 3) return null;
		return new DhSectionPos(Byte.parseByte(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
		
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	public String toString() { return "{" + this.detailLevel + "*" + this.x + "," + this.z + "}"; }
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (obj == null || obj.getClass() != DhSectionPos.class)
		{
			return false;
		}
		
		DhSectionPos that = (DhSectionPos) obj;
		return this.detailLevel == that.detailLevel &&
				this.x == that.x &&
				this.z == that.z;
	}
	
	@Override
	public int hashCode()
	{
		return Integer.hashCode(this.detailLevel) ^ // XOR
				Integer.hashCode(this.x) ^ // XOR
				Integer.hashCode(this.z);
	}
	
	
	
	//=============//
	// sub classes //
	//=============//
	
	/**
	 * Identical to {@link DhSectionPos} except it is mutable.
	 * See {@link DhSectionPos} for full documentation.
	 * 
	 * @see DhSectionPos
	 */
	public static class DhMutableSectionPos extends DhSectionPos
	{
		
		//==============//
		// constructors //
		//==============//
		
		public DhMutableSectionPos(byte sectionDetailLevel, int sectionX, int sectionZ) { super(sectionDetailLevel, sectionX, sectionZ); }
		public DhMutableSectionPos(DhBlockPos blockPos) { super(blockPos); }
		public DhMutableSectionPos(DhBlockPos2D blockPos) { super(blockPos); }
		public DhMutableSectionPos(DhChunkPos chunkPos) { super(chunkPos); }
		public DhMutableSectionPos(byte detailLevel, DhLodPos dhLodPos) { super(detailLevel, dhLodPos); }
		
		
		
		//============//
		// converters //
		//============//
		
		/**
		 * Overwrites this section pos with the given input. <br>
		 * Can be useful to prevent duplicate allocations in high traffic loops but should 
		 * be used sparingly as it could accidentally cause bugs due to unexpected modifications.
		 */
		public void mutate(byte sectionDetailLevel, int sectionX, int sectionZ)
		{
			this.detailLevel = sectionDetailLevel;
			this.x = sectionX;
			this.z = sectionZ;
		}
		
		@Override
		public void convertSelfToDetailLevel(byte newDetailLevel) { super.convertSelfToDetailLevel(newDetailLevel); }
		
		
		
		//==================//
		// property getters //
		//==================//
		
		public void setDetailLevel(byte sectionDetailLevel) { this.detailLevel = sectionDetailLevel; }
		
		public void setX(int sectionX) { this.x = sectionX; }
		
		public void setZ(int sectionZ) { this.z = sectionZ; }
		
	}
	
}
