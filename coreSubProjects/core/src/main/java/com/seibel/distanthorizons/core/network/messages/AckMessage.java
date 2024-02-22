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

package com.seibel.distanthorizons.core.network.messages;

import com.seibel.distanthorizons.core.network.protocol.INetworkMessage;
import com.seibel.distanthorizons.core.network.protocol.MessageRegistry;
//import io.netty.buffer.ByteBuf;

/**
 * Simple empty response message.
 * This message is not sent automatically.
 */
public class AckMessage implements INetworkMessage
{
	public Class<? extends INetworkMessage> messageType;
	
	
	
	public AckMessage() { }
	public AckMessage(Class<? extends INetworkMessage> messageType) { this.messageType = messageType; }

//    @Override
//    public void encode(ByteBuf out) { out.writeInt(MessageRegistry.INSTANCE.getMessageId(this.messageType)); }
//	
//    @Override
//    public void decode(ByteBuf in) { this.messageType = MessageRegistry.INSTANCE.getMessageClassById(in.readInt()); }
	
}
