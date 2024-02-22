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
import com.seibel.distanthorizons.core.network.messages.CloseReasonMessage;
import com.seibel.distanthorizons.core.network.messages.CloseMessage;
import com.seibel.distanthorizons.core.network.messages.HelloMessage;
import com.seibel.distanthorizons.core.network.protocol.NetworkChannelInitializer;
//import io.netty.bootstrap.ServerBootstrap;
//import io.netty.channel.*;
//import io.netty.channel.nio.NioEventLoopGroup;
//import io.netty.channel.socket.nio.NioServerSocketChannel;
//import io.netty.handler.logging.LogLevel;
//import io.netty.handler.logging.LoggingHandler;
import org.apache.logging.log4j.Logger;

public class NetworkServer //extends NetworkEventSource implements AutoCloseable
{
//	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
//	
//	// TODO move to the config
//	private final int port;
//	
//	private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
//	private final EventLoopGroup workerGroup = new NioEventLoopGroup();
//	private Channel channel;
//	private boolean isClosed = false;
//	
//	
//	
//	public NetworkServer(int port)
//	{
//		this.port = port;
//		
//		LOGGER.info("Starting server on port "+port);
//		this.registerHandlers();
//		this.bind();
//	}
//	
//	private void registerHandlers()
//	{
//		this.registerHandler(HelloMessage.class, (helloMessage, channelContext) -> 
//		{
//			LOGGER.info("Client connected: "+channelContext.channel().remoteAddress());
//			channelContext.channel().writeAndFlush(new HelloMessage());
//		});
//		
//		this.registerHandler(CloseMessage.class, (closeMessage, channelContext) -> 
//		{
//			LOGGER.info("Client disconnected: "+channelContext.channel().remoteAddress());
//		});
//	}
//	
//	private void bind()
//	{
//		ServerBootstrap bootstrap = new ServerBootstrap()
//				.group(this.bossGroup, this.workerGroup)
//				.channel(NioServerSocketChannel.class)
//				.handler(new LoggingHandler(LogLevel.DEBUG))
//				.childHandler(new NetworkChannelInitializer(this.messageHandler));
//		
//		ChannelFuture bindFuture = bootstrap.bind(this.port);
//		bindFuture.addListener((ChannelFuture channelFuture) -> 
//		{
//			if (!channelFuture.isSuccess())
//			{
//				throw new RuntimeException("Failed to bind: " + channelFuture.cause());
//			}
//			
//			LOGGER.info("Server is started on port "+this.port);
//		});
//		
//		this.channel = bindFuture.channel();
//		this.channel.closeFuture().addListener(future -> this.close());
//	}
//	
//	public void disconnectClient(ChannelHandlerContext ctx, String reason)
//	{
//		ctx.channel().config().setAutoRead(false);
//		ctx.writeAndFlush(new CloseReasonMessage(reason))
//				.addListener(ChannelFutureListener.CLOSE);
//	}
//	
//	@Override
//	public void close()
//	{
//		if (this.closeReason != null)
//		{
//			LOGGER.error(this.closeReason);
//		}
//		
//		if (this.isClosed)
//		{
//			return;
//		}
//		this.isClosed = true;
//		
//		LOGGER.info("Shutting down the network server.");
//		this.workerGroup.shutdownGracefully().syncUninterruptibly();
//		this.bossGroup.shutdownGracefully().syncUninterruptibly();
//		LOGGER.info("Network server has been closed.");
//	}
	
}
