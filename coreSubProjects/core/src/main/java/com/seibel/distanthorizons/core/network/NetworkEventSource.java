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

package com.seibel.distanthorizons.core.network;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.network.messages.AckMessage;
import com.seibel.distanthorizons.core.network.messages.HelloMessage;
import com.seibel.distanthorizons.core.network.protocol.INetworkMessage;
import com.seibel.distanthorizons.core.network.protocol.MessageHandler;
import com.seibel.distanthorizons.coreapi.ModInfo;
//import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class NetworkEventSource implements AutoCloseable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	protected final MessageHandler messageHandler = new MessageHandler();
	protected String closeReason = null;



//	public NetworkEventSource()
//	{
//		this.registerHandler(HelloMessage.class, (helloMessage, channelContext) -> 
//		{
//			if (helloMessage.version != ModInfo.PROTOCOL_VERSION)
//			{
//				try
//				{
//					String closeReason = "Ignoring message from channel ["+channelContext.name()+"], due to version mismatch. Expected version: ["+ModInfo.PROTOCOL_VERSION+"], received version: ["+helloMessage.version+"].";
//					LOGGER.info(closeReason);
//					this.close(closeReason);
//				}
//				catch (Exception e)
//				{
//					throw new RuntimeException(e);
//				}
//			}
//		});
//	}
//	
//	public <T extends INetworkMessage> void registerHandler(Class<T> clazz, BiConsumer<T, ChannelHandlerContext> handler) { this.messageHandler.registerHandler(clazz, handler); }
//	
//	public <T extends INetworkMessage> void registerAckHandler(Class<T> clazz, Consumer<ChannelHandlerContext> handler)
//	{
//		this.messageHandler.registerHandler(AckMessage.class, (ackMessage, channelContext) -> 
//		{
//			if (ackMessage.messageType == clazz)
//			{
//				handler.accept(channelContext);
//			}
//		});
//	}
//	
//	public void close(String reason) throws Exception
//	{
//		this.closeReason = reason;
//		this.close();
//	}
	
}
