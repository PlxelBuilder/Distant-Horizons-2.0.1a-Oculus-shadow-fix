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

package com.seibel.distanthorizons.core.generation;

import com.seibel.distanthorizons.core.generation.tasks.IWorldGenTaskTracker;
import com.seibel.distanthorizons.core.generation.tasks.WorldGenResult;
import com.seibel.distanthorizons.core.pos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhSectionPos;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

public interface IWorldGenerationQueue extends Closeable
{
	/** the largest numerical detail level */
	byte lowestDataDetail();
	/** the smallest numerical detail level */
	byte highestDataDetail();
	
	CompletableFuture<WorldGenResult> submitGenTask(DhSectionPos pos, byte requiredDataDetail, IWorldGenTaskTracker tracker);
	void cancelGenTasks(Iterable<DhSectionPos> positions);
	
	/** @param targetPos the position that world generation should be centered around, generally this will be the player's position. */
	void startGenerationQueueAndSetTargetPos(DhBlockPos2D targetPos);
	
	int getWaitingTaskCount();
	int getInProgressTaskCount();
	
	CompletableFuture<Void> startClosing(boolean cancelCurrentGeneration, boolean alsoInterruptRunning);
	void close();
	
}
