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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.seibel.distanthorizons.core.network.messages.*;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class MessageRegistry
{
	public static final MessageRegistry INSTANCE = new MessageRegistry();
	
	private final Map<Integer, Supplier<? extends INetworkMessage>> idToSupplier = new HashMap<>();
	private final BiMap<Class<? extends INetworkMessage>, Integer> classToId = HashBiMap.create();
	
	
	
	private MessageRegistry()
	{
		// Note: Messages must have parameterless constructors
		
		// Keep messages below intact so client/server can disconnect if version does not match
		this.registerMessage(HelloMessage.class, HelloMessage::new);
		this.registerMessage(CloseReasonMessage.class, CloseReasonMessage::new);
		
		// Define your messages after this line
		this.registerMessage(AckMessage.class, AckMessage::new);
		this.registerMessage(PlayerUUIDMessage.class, PlayerUUIDMessage::new);
		this.registerMessage(RemotePlayerConfigMessage.class, RemotePlayerConfigMessage::new);
		this.registerMessage(RequestChunksMessage.class, RequestChunksMessage::new);
	}
	
	
	
	public <T extends INetworkMessage> void registerMessage(Class<T> clazz, Supplier<T> supplier)
	{
		int id = this.idToSupplier.size() + 1;
		this.idToSupplier.put(id, supplier);
		this.classToId.put(clazz, id);
	}
	
	public Class<? extends INetworkMessage> getMessageClassById(int messageId) { return this.classToId.inverse().get(messageId); }
	
	public INetworkMessage createMessage(int messageId) throws IllegalArgumentException
	{
		try
		{
			return this.idToSupplier.get(messageId).get();
		}
		catch (NullPointerException e)
		{
			throw new IllegalArgumentException("Invalid message ID");
		}
	}
	
	public int getMessageId(INetworkMessage message) { return this.getMessageId(message.getClass()); }
	
	public int getMessageId(Class<? extends INetworkMessage> messageClass) { return this.classToId.get(messageClass); }
	
}
