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
import com.seibel.distanthorizons.core.network.messages.CloseMessage;
//import io.netty.channel.ChannelHandler;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

//@ChannelHandler.Sharable
public class MessageHandler //extends SimpleChannelInboundHandler<INetworkMessage>
{
//	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
//	
//	private final Map<Class<? extends INetworkMessage>, List<BiConsumer<INetworkMessage, ChannelHandlerContext>>> handlers = new HashMap<>();
//	
//	
//	
//	public <T extends INetworkMessage> void registerHandler(Class<T> handlerClass, BiConsumer<T, ChannelHandlerContext> handlerImplementation)
//	{
//		this.handlers.computeIfAbsent(handlerClass, missingHandlerClass -> new LinkedList<>())
//				.add((BiConsumer<INetworkMessage, ChannelHandlerContext>) handlerImplementation);
//	}
//	
//	@Override
//	protected void channelRead0(ChannelHandlerContext channelContext, INetworkMessage message)
//	{
//		LOGGER.trace("Received message: "+message.getClass().getSimpleName());
//		
//		List<BiConsumer<INetworkMessage, ChannelHandlerContext>> handlerList = this.handlers.get(message.getClass());
//		if (handlerList == null)
//		{
//			LOGGER.warn("Unhandled message type: "+message.getClass().getSimpleName());
//			return;
//		}
//		
//		for (BiConsumer<INetworkMessage, ChannelHandlerContext> handler : handlerList)
//		{
//			handler.accept(message, channelContext);
//		}
//	}
//	
//	@Override
//	public void channelInactive(@NotNull ChannelHandlerContext channelContext) { this.channelRead0(channelContext, new CloseMessage()); }
	
}
