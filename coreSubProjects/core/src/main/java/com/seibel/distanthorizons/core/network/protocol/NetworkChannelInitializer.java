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

//import io.netty.channel.*;
//import io.netty.channel.socket.SocketChannel;
//import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
//import io.netty.handler.codec.LengthFieldPrepender;

import org.jetbrains.annotations.NotNull;

/** used when creating a network channel */
public class NetworkChannelInitializer //extends ChannelInitializer<SocketChannel> 
{
	private final MessageHandler messageHandler;
	
	
	
	public NetworkChannelInitializer(MessageHandler messageHandler) { this.messageHandler = messageHandler; }

//    @Override
//    public void initChannel(@NotNull SocketChannel socketChannel) 
//	{
//        ChannelPipeline pipeline = socketChannel.pipeline();
//		
//        // Encoder
//        pipeline.addLast(new LengthFieldPrepender(Short.BYTES));
//        pipeline.addLast(new MessageEncoder());
//        pipeline.addLast(new NetworkOutboundExceptionRouter());
//		
//        // Decoder
//        pipeline.addLast(new LengthFieldBasedFrameDecoder(Short.MAX_VALUE, 0, Short.BYTES, 0, Short.BYTES));
//        pipeline.addLast(new MessageDecoder());
//		
//        // Handler
//        pipeline.addLast(this.messageHandler);
//        pipeline.addLast(new NetworkExceptionHandler());
//    }
//	
}
