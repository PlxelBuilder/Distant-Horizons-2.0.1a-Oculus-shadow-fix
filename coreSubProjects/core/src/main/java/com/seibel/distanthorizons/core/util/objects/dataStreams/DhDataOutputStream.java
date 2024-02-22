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

package com.seibel.distanthorizons.core.util.objects.dataStreams;

import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.*;

/**
 * See {@link DhDataInputStream} for more information about these custom streams.
 *
 * @see DhDataInputStream
 */
public class DhDataOutputStream extends DataOutputStream
{
	public DhDataOutputStream(OutputStream stream) throws IOException
	{
		super(new LZ4FrameOutputStream(new BufferedOutputStream(stream)));
	}
	
	@Override
	public void close() throws IOException { /* Do nothing. */ }
	
}
