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
import com.seibel.distanthorizons.core.network.messages.CloseMessage;
import com.seibel.distanthorizons.core.network.messages.CloseReasonMessage;
import com.seibel.distanthorizons.core.network.messages.HelloMessage;
import com.seibel.distanthorizons.core.network.protocol.NetworkChannelInitializer;
//import io.netty.bootstrap.Bootstrap;
//import io.netty.channel.Channel;
//import io.netty.channel.ChannelFuture;
//import io.netty.channel.ChannelOption;
//import io.netty.channel.EventLoopGroup;
//import io.netty.channel.nio.NioEventLoopGroup;
//import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class NetworkClient //extends NetworkEventSource implements AutoCloseable 
{
//    private static final Logger LOGGER = DhLoggerBuilder.getLogger();
//	
//    private enum EConnectionState
//	{
//        OPEN,
//        RECONNECT,
//        RECONNECT_FORCE,
//        CLOSE_WAIT,
//        CLOSED
//    }
//	
//    private static final int FAILURE_RECONNECT_DELAY_SEC = 5;
//    private static final int FAILURE_RECONNECT_ATTEMPTS = 3;
//	
//    // TODO move to payload of some sort
//    private final InetSocketAddress address;
//	
//    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
//    private final Bootstrap clientBootstrap = new Bootstrap()
//            .group(this.workerGroup)
//            .channel(NioSocketChannel.class)
//            .option(ChannelOption.SO_KEEPALIVE, true)
//            .handler(new NetworkChannelInitializer(this.messageHandler));
//	
//    private EConnectionState connectionState;
//    private Channel channel;
//    private int reconnectAttempts = FAILURE_RECONNECT_ATTEMPTS;
//	
//	
//	
//    public NetworkClient(String host, int port)
//	{
//        this.address = new InetSocketAddress(host, port);
//		
//		this.registerHandlers();
//		this.connect();
//    }
//	
//    private void registerHandlers() 
//	{
//		this.registerHandler(HelloMessage.class, (helloMessage, channelContext) -> 
//		{
//            LOGGER.info("Connected to server: "+channelContext.channel().remoteAddress());
//        });
//		
//		this.registerHandler(CloseReasonMessage.class, (closeReasonMessage, channelContext) -> 
//		{
//            LOGGER.info(closeReasonMessage.reason);
//			this.connectionState = EConnectionState.CLOSE_WAIT;
//        });
//		
//		this.registerHandler(CloseMessage.class, (closeMessage, channelContext) ->
//		{
//            LOGGER.info("Disconnected from server: "+channelContext.channel().remoteAddress());
//            if (this.connectionState == EConnectionState.CLOSE_WAIT)
//			{
//				this.close();
//			}
//        });
//    }
//
//    private void connect() 
//	{
//        LOGGER.info("Connecting to server: "+this.address);
//		this.connectionState = EConnectionState.OPEN;
//
//		// FIXME sometimes this causes the MC connection to crash 
//		//  this might happen if the URL can't be converted to a IP (IE UnknownHostException)
//        ChannelFuture connectFuture = this.clientBootstrap.connect(this.address);
//        connectFuture.addListener((ChannelFuture channelFuture) -> 
//		{
//            if (!channelFuture.isSuccess()) 
//			{
//                LOGGER.warn("Connection failed: "+channelFuture.cause());
//                return;
//            }
//			
//			this.channel.writeAndFlush(new HelloMessage());
//        });
//		
//		this.channel = connectFuture.channel();
//		this. channel.closeFuture().addListener((ChannelFuture channelFuture) -> 
//		{
//			switch (this.connectionState)
//			{
//				case OPEN:
//					this.reconnectAttempts--;
//					LOGGER.info("Reconnection attempts left: ["+this.reconnectAttempts+"] of ["+FAILURE_RECONNECT_ATTEMPTS+"].");
//					if (this.reconnectAttempts == 0)
//					{
//						this.connectionState = EConnectionState.CLOSE_WAIT;
//						return;
//					}
//					
//					this.connectionState = EConnectionState.RECONNECT;
//					this.workerGroup.schedule(this::connect, FAILURE_RECONNECT_DELAY_SEC, TimeUnit.SECONDS);
//					break;
//					
//				case RECONNECT_FORCE:
//					LOGGER.info("Reconnecting forcefully.");
//					this.reconnectAttempts = FAILURE_RECONNECT_ATTEMPTS;
//					
//					this.connectionState = EConnectionState.RECONNECT;
//					this.workerGroup.schedule(this::connect, 0, TimeUnit.SECONDS);
//					break;
//			}
//        });
//    }
//
//    /** Kills the current connection, triggering auto-reconnection immediately. */
//    public void reconnect() 
//	{
//		this.connectionState = EConnectionState.RECONNECT_FORCE;
//		this.channel.disconnect();
//    }
//
//    @Override
//    public void close() 
//	{
//        if (this.closeReason != null)
//		{
//			LOGGER.error(this.closeReason);
//		}
//
//        if (this.connectionState == EConnectionState.CLOSED)
//		{
//			return;
//		}
//		
//		this.connectionState = EConnectionState.CLOSED;
//		this.workerGroup.shutdownGracefully().syncUninterruptibly();
//		this.channel.close().syncUninterruptibly();
//    }
	
}
