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
//import io.netty.buffer.ByteBuf;

import java.util.UUID;

public class PlayerUUIDMessage implements INetworkMessage
{
	public UUID playerUUID;
	
	
	
	public PlayerUUIDMessage() { }
	public PlayerUUIDMessage(UUID playerUUID) { this.playerUUID = playerUUID; }

//    @Override
//    public void encode(ByteBuf out)
//	{
//        out.writeLong(this.playerUUID.getMostSignificantBits());
//        out.writeLong(this.playerUUID.getLeastSignificantBits());
//    }
//
//    @Override
//    public void decode(ByteBuf in) { this.playerUUID = new UUID(in.readLong(), in.readLong()); }
	
}
