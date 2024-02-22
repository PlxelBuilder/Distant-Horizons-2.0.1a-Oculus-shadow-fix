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

package com.seibel.distanthorizons.core.network.protocol;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.logging.log4j.Logger;

public class NetworkExceptionHandler //extends ChannelInboundHandlerAdapter
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();

//    @Override
//	public void exceptionCaught(ChannelHandlerContext channelContext, Throwable cause)
//	{
//		LOGGER.error("Exception caught in channel: ["+channelContext.name()+"].", cause);
//		channelContext.close();
//	}
	
}
