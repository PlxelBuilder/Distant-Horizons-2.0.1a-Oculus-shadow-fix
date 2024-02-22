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

import com.seibel.distanthorizons.core.pos.*;
import org.junit.Assert;
import org.junit.Test;

public class DhSectionPosTest
{
	@Test
	public void ContainsPosTest()
	{
		DhSectionPos root = new DhSectionPos((byte) 10, 0, 0);
		DhSectionPos child = new DhSectionPos((byte) 9, 1, 1);
		
		Assert.assertTrue("section pos contains fail", root.contains(child));
		Assert.assertFalse("section pos contains fail", child.contains(root));
		
		
		root = new DhSectionPos((byte) 10, 1, 0);
		
		// out of bounds
		child = new DhSectionPos((byte) 9, 0, 0);
		Assert.assertFalse("position should be out of bounds", root.contains(child));
		child = new DhSectionPos((byte) 9, 1, 1);
		Assert.assertFalse("position should be out of bounds", root.contains(child));
		
		// in bounds
		child = new DhSectionPos((byte) 9, 2, 0);
		Assert.assertTrue("position should be in bounds", root.contains(child));
		child = new DhSectionPos((byte) 9, 3, 1);
		Assert.assertTrue("position should be in bounds", root.contains(child));
		
		// out of bounds
		child = new DhSectionPos((byte) 9, 2, 2);
		Assert.assertFalse("position should be out of bounds", root.contains(child));
		child = new DhSectionPos((byte) 9, 3, 3);
		Assert.assertFalse("position should be out of bounds", root.contains(child));
		
		child = new DhSectionPos((byte) 9, 4, 4);
		Assert.assertFalse("position should be out of bounds", root.contains(child));
		child = new DhSectionPos((byte) 9, 5, 5);
		Assert.assertFalse("position should be out of bounds", root.contains(child));
	}
	
	@Test
	public void ContainsAdjacentPosTest()
	{
		// neither should contain the other, they are single blocks that are next to each other
		DhSectionPos left = new DhSectionPos((byte) 0, 4606, 0);
		DhSectionPos right = new DhSectionPos((byte) 0, 4607, 0);
		Assert.assertFalse(left.contains(right));
		Assert.assertFalse(right.contains(left));
		
		
		// 512 block wide sections that are adjacent, but not overlapping
		left = new DhSectionPos((byte) 9, 0, 0);
		right = new DhSectionPos((byte) 9, 1, 0);
		Assert.assertFalse(left.contains(right));
		Assert.assertFalse(right.contains(left));
		
	}
	
	@Test
	public void ParentPosTest()
	{
		DhSectionPos leaf = new DhSectionPos((byte) 0, 0, 0);
		DhSectionPos convert = leaf.convertNewToDetailLevel((byte) 1);
		DhSectionPos parent = leaf.getParentPos();
		Assert.assertEquals("get parent at 0,0 fail", convert, parent);
		
		
		leaf = new DhSectionPos((byte) 0, 1, 1);
		convert = leaf.convertNewToDetailLevel((byte) 1);
		parent = leaf.getParentPos();
		Assert.assertEquals("get parent at 1,1 fail", convert, parent);
		
		
		leaf = new DhSectionPos((byte) 1, 2, 2);
		convert = leaf.convertNewToDetailLevel((byte) 2);
		parent = leaf.getParentPos();
		Assert.assertEquals("parent upscale fail", convert, parent);
		convert = leaf.convertNewToDetailLevel((byte) 0);
		DhSectionPos childIndex = leaf.getChildByIndex(0);
		Assert.assertEquals("child detail fail", convert, childIndex);
		
	}
	
	@Test
	public void ChildPosTest()
	{
		DhSectionPos node = new DhSectionPos((byte) 1, 2302, 0);
		DhSectionPos nw = node.getChildByIndex(0);
		DhSectionPos sw = node.getChildByIndex(1);
		DhSectionPos ne = node.getChildByIndex(2);
		DhSectionPos se = node.getChildByIndex(3);
		
		// confirm no children have the same values
		Assert.assertNotEquals(nw, sw);
		Assert.assertNotEquals(sw, ne);
		Assert.assertNotEquals(ne, se);
		
		// confirm each child has the correct value
		Assert.assertEquals(nw, new DhSectionPos((byte) 0, 4604, 0));
		Assert.assertEquals(sw, new DhSectionPos((byte) 0, 4605, 0));
		Assert.assertEquals(ne, new DhSectionPos((byte) 0, 4604, 1));
		Assert.assertEquals(se, new DhSectionPos((byte) 0, 4605, 1));
		
	}
	
	@Test
	public void GetCenterTest()
	{
		DhSectionPos node = new DhSectionPos((byte) 1, 2303, 0);
		DhBlockPos2D centerBlockPos = node.getCenterBlockPos();
		DhBlockPos2D expectedCenterNode = new DhBlockPos2D(4606, 0);
		Assert.assertEquals("", expectedCenterNode, centerBlockPos);
		
		
		
		node = new DhSectionPos((byte) 10, 0, 0); // 1024 blocks wide
		centerBlockPos = node.getCenterBlockPos();
		expectedCenterNode = new DhBlockPos2D(1024 / 2, 1024 / 2);
		Assert.assertEquals("", expectedCenterNode, centerBlockPos);

	}
	
	@Test
	public void GetCenter2Test()
	{
		DhSectionPos parentNode = new DhSectionPos((byte) 2, 1151, 0); // width 4 blocks
		DhSectionPos inputPos = new DhSectionPos((byte) 0, 4606, 0); // width 1 block
		Assert.assertTrue(parentNode.contains(inputPos));
		
		DhBlockPos2D parentCenter = parentNode.getCenterBlockPos();
		DhBlockPos2D inputCenter = inputPos.getCenterBlockPos();
		
		Assert.assertEquals(new DhBlockPos2D(4606, 2), parentCenter);
		Assert.assertEquals(new DhBlockPos2D(4606, 0), inputCenter);
		
	}
	
	@Test
	public void CreateFromBlockPos()
	{
		// origin pos //
		
		DhBlockPos originBlockPos = new DhBlockPos(0, 0, 0);
		DhSectionPos originSectionPos = new DhSectionPos(originBlockPos);
		Assert.assertEquals(new DhSectionPos(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, 0, 0), originSectionPos);
		
		
		// offset pos //
		
		DhBlockPos offsetBlockPos = new DhBlockPos(1000, 0, 42000);
		DhSectionPos offsetSectionPos = new DhSectionPos(offsetBlockPos);
		Assert.assertEquals(new DhSectionPos(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, 15, 656), offsetSectionPos);
		
		offsetBlockPos = new DhBlockPos(-987654, 0, 46);
		offsetSectionPos = new DhSectionPos(offsetBlockPos);
		Assert.assertEquals(new DhSectionPos(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, -15433, 0), offsetSectionPos);
		
	}
	
	@Test
	public void CreateFromBlockPos2D()
	{
		// origin pos //
		
		DhBlockPos2D originBlockPos = new DhBlockPos2D(0, 0);
		DhSectionPos originSectionPos = new DhSectionPos(originBlockPos);
		Assert.assertEquals(new DhSectionPos(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, 0, 0), originSectionPos);
		
		
		// offset pos //
		
		DhBlockPos2D offsetBlockPos = new DhBlockPos2D(1000, 42000);
		DhSectionPos offsetSectionPos = new DhSectionPos(offsetBlockPos);
		Assert.assertEquals(new DhSectionPos(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, 15, 656), offsetSectionPos);
		
		offsetBlockPos = new DhBlockPos2D(-987654, 46);
		offsetSectionPos = new DhSectionPos(offsetBlockPos);
		Assert.assertEquals(new DhSectionPos(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, -15433, 0), offsetSectionPos);
		
	}
	
	@Test
	public void CreateFromChunkPos()
	{
		// origin pos //
		
		DhChunkPos originChunkPos = new DhChunkPos(0,0);
		DhSectionPos originSectionPos = new DhSectionPos(originChunkPos);
		Assert.assertEquals(new DhSectionPos(DhSectionPos.SECTION_CHUNK_DETAIL_LEVEL, 0, 0), originSectionPos);
		
		
		// offset pos //
		
		DhChunkPos offsetChunkPos = new DhChunkPos(1000, 42000);
		DhSectionPos offsetSectionPos = new DhSectionPos(offsetChunkPos);
		Assert.assertEquals(new DhSectionPos(DhSectionPos.SECTION_CHUNK_DETAIL_LEVEL, 15, 656), offsetSectionPos);
		
		offsetChunkPos = new DhChunkPos(-987654, 46);
		offsetSectionPos = new DhSectionPos(offsetChunkPos);
		Assert.assertEquals(new DhSectionPos(DhSectionPos.SECTION_CHUNK_DETAIL_LEVEL, -15433, 0), offsetSectionPos);
		
	}
	
	@Test
	public void ConvertToDetailLevel()
	{
		// origin pos //
		
		DhSectionPos originSectionPos = new DhSectionPos((byte) 0,0,0);
		
		originSectionPos = originSectionPos.convertNewToDetailLevel((byte) 1);
		Assert.assertEquals(new DhSectionPos((byte) 1, 0, 0), originSectionPos);
		
		originSectionPos = originSectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL);
		Assert.assertEquals(new DhSectionPos(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, 0, 0), originSectionPos);
		
		originSectionPos = originSectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_REGION_DETAIL_LEVEL);
		Assert.assertEquals(new DhSectionPos(DhSectionPos.SECTION_REGION_DETAIL_LEVEL, 0, 0), originSectionPos);
		
		
		// offset pos //
		
		DhSectionPos sectionPos = new DhSectionPos((byte) 0,-10000,5000);
		
		sectionPos = sectionPos.convertNewToDetailLevel((byte) 1);
		Assert.assertEquals(new DhSectionPos((byte) 1, -5000, 2500), sectionPos);
		
		sectionPos = sectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL);
		Assert.assertEquals(new DhSectionPos(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL, -157, 78), sectionPos);
		
		sectionPos = sectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_REGION_DETAIL_LEVEL);
		Assert.assertEquals(new DhSectionPos(DhSectionPos.SECTION_REGION_DETAIL_LEVEL, -1, 0), sectionPos);
		
	}
	
	@Test
	public void GetOffsetWidth()
	{
		DhSectionPos originSectionPos = new DhSectionPos((byte) 0,0,0);
		DhSectionPos sectionPos = new DhSectionPos((byte) 0,-10000,5000);
		
		
		
		// 1 -> 0
		byte returnDetailLevel = 0;
		originSectionPos = originSectionPos.convertNewToDetailLevel((byte) 1);
		Assert.assertEquals(2, originSectionPos.getWidthCountForLowerDetailedSection(returnDetailLevel));
		
		sectionPos = sectionPos.convertNewToDetailLevel((byte) 1);
		Assert.assertEquals(2, sectionPos.getWidthCountForLowerDetailedSection(returnDetailLevel));
		
		
		// 2 -> 1
		returnDetailLevel = 1;
		originSectionPos = originSectionPos.convertNewToDetailLevel((byte) 2);
		Assert.assertEquals(2, originSectionPos.getWidthCountForLowerDetailedSection(returnDetailLevel));
		
		sectionPos = sectionPos.convertNewToDetailLevel((byte) 2);
		Assert.assertEquals(2, sectionPos.getWidthCountForLowerDetailedSection(returnDetailLevel));
		
		
		// Block -> 0
		returnDetailLevel = 0;
		originSectionPos = originSectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL);
		Assert.assertEquals(64, originSectionPos.getWidthCountForLowerDetailedSection(returnDetailLevel));
		
		sectionPos = sectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL);
		Assert.assertEquals(64, sectionPos.getWidthCountForLowerDetailedSection(returnDetailLevel));
		
		
		// Region -> 3
		returnDetailLevel = 3;
		originSectionPos = originSectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_REGION_DETAIL_LEVEL);
		Assert.assertEquals(4096, originSectionPos.getWidthCountForLowerDetailedSection(returnDetailLevel));
		
		sectionPos = sectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_REGION_DETAIL_LEVEL);
		Assert.assertEquals(4096, sectionPos.getWidthCountForLowerDetailedSection(returnDetailLevel));
		
	}
	
	@Test
	public void GetBlockWidth()
	{
		DhSectionPos originSectionPos = new DhSectionPos((byte) 0,0,0);
		DhSectionPos sectionPos = new DhSectionPos((byte) 0,-10000,5000);
		
		
		Assert.assertEquals(1, originSectionPos.getBlockWidth());
		Assert.assertEquals(1, sectionPos.getBlockWidth());
		
		originSectionPos = originSectionPos.convertNewToDetailLevel((byte) 1);
		Assert.assertEquals(2, originSectionPos.getBlockWidth());
		sectionPos = sectionPos.convertNewToDetailLevel((byte) 1);
		Assert.assertEquals(2, sectionPos.getBlockWidth());
		
		originSectionPos = originSectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL);
		Assert.assertEquals(64, originSectionPos.getBlockWidth());
		sectionPos = sectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL);
		Assert.assertEquals(64, sectionPos.getBlockWidth());
		
		originSectionPos = originSectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_REGION_DETAIL_LEVEL);
		Assert.assertEquals(32768, originSectionPos.getBlockWidth());
		sectionPos = sectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_REGION_DETAIL_LEVEL);
		Assert.assertEquals(32768, sectionPos.getBlockWidth());
		
	}
	
	@Test
	public void GetCenterBlockPos()
	{
		DhSectionPos originSectionPos = new DhSectionPos((byte) 0,0,0);
		DhSectionPos sectionPos = new DhSectionPos((byte) 0,-10000,5000);
		
		
		Assert.assertEquals(new DhBlockPos2D(0, 0), originSectionPos.getCenterBlockPos());
		Assert.assertEquals(new DhBlockPos2D(-10000, 5000), sectionPos.getCenterBlockPos());
		
		
		originSectionPos = originSectionPos.convertNewToDetailLevel((byte) 1);
		Assert.assertEquals(new DhBlockPos2D(0, 0), originSectionPos.getCenterBlockPos());
		sectionPos = sectionPos.convertNewToDetailLevel((byte) 1);
		Assert.assertEquals(new DhBlockPos2D(-10000, 5000), sectionPos.getCenterBlockPos());
		
		
		originSectionPos = originSectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL);
		Assert.assertEquals(new DhBlockPos2D(32, 32), originSectionPos.getCenterBlockPos());
		sectionPos = sectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL);
		Assert.assertEquals(new DhBlockPos2D(-10016, 5024), sectionPos.getCenterBlockPos());
		
		
		originSectionPos = originSectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_REGION_DETAIL_LEVEL);
		Assert.assertEquals(new DhBlockPos2D(16384, 16384), originSectionPos.getCenterBlockPos());
		sectionPos = sectionPos.convertNewToDetailLevel(DhSectionPos.SECTION_REGION_DETAIL_LEVEL);
		Assert.assertEquals(new DhBlockPos2D(-16384, 16384), sectionPos.getCenterBlockPos());
		
	}
	
	
}
