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

package com.seibel.distanthorizons.core.util;

import it.unimi.dsi.fastutil.booleans.BooleanObjectImmutablePair;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Predicate;

/**
 * While java 8 does have built in atomic operations, there doesn't seem to be any Compare And Exchange operation... <br>
 * So here we implement our own.
 */
public class AtomicsUtil
{
	
	public static <T> T conditionalAndExchange(AtomicReference<T> atomic, Predicate<T> requirement, T newValue)
	{
		while (true)
		{
			T oldValue = atomic.get();
			if (!requirement.test(oldValue)) return oldValue;
			if (atomic.weakCompareAndSet(oldValue, newValue)) return oldValue;
		}
	}
	
	public static <T> BooleanObjectImmutablePair<T> conditionalAndExchangeWeak(AtomicReference<T> atomic, Predicate<T> requirement, T newValue)
	{
		T oldValue = atomic.get();
		if (requirement.test(oldValue) && atomic.weakCompareAndSet(oldValue, newValue))
		{
			return new BooleanObjectImmutablePair<>(true, oldValue);
		}
		else
		{
			return new BooleanObjectImmutablePair<>(false, oldValue);
		}
	}
	
	/** 
	 * If the {@link AtomicReference}'s current value matches the expected value, the newValue will be swapped in and the expected value returned. <br>
	 * If the {@link AtomicReference}'s current value DOESN'T match the expected value, the {@link AtomicReference}'s current value will be returned without modification.
	 */
	public static <T> T compareAndExchange(AtomicReference<T> atomic, T expected, T newValue)
	{
		while (true)
		{
			T oldValue = atomic.get();
			if (oldValue != expected)
			{
				return oldValue;
			}
			else if (atomic.weakCompareAndSet(expected, newValue))
			{
				return expected;
			}
		}
	}
	
	public static <T> BooleanObjectImmutablePair<T> compareAndExchangeWeak(AtomicReference<T> atomic, T expected, T newValue)
	{
		T oldValue = atomic.get();
		if (oldValue == expected && atomic.weakCompareAndSet(expected, newValue))
		{
			return new BooleanObjectImmutablePair<>(true, expected);
		}
		else
		{
			return new BooleanObjectImmutablePair<>(false, oldValue);
		}
	}
	
	// Additionally, we implement some helper methods for frequently used atomic operations. //
	
	// Compare with expected value and set new value if equal. Then return whatever value the atomic now contains.
	public static <T> T compareAndSetThenGet(AtomicReference<T> atomic, T expected, T newValue)
	{
		while (true)
		{
			T oldValue = atomic.get();
			if (oldValue != expected) return oldValue;
			if (atomic.weakCompareAndSet(expected, newValue)) return newValue;
		}
	}
	
	
	
	// Below is the array version of the above. //
	
	public static <T> T compareAndExchange(AtomicReferenceArray<T> array, int index, T expected, T newValue)
	{
		while (true)
		{
			T oldValue = array.get(index);
			if (oldValue != expected) return oldValue;
			if (array.weakCompareAndSet(index, expected, newValue)) return expected;
		}
	}
	
	public static <T> BooleanObjectImmutablePair<T> compareAndExchangeWeak(AtomicReferenceArray<T> array, int index, T expected, T newValue)
	{
		T oldValue = array.get(index);
		if (oldValue == expected && array.weakCompareAndSet(index, expected, newValue))
		{
			return new BooleanObjectImmutablePair<>(true, expected);
		}
		else
		{
			return new BooleanObjectImmutablePair<>(false, oldValue);
		}
	}
	
	public static <T> T compareAndSetThenGet(AtomicReferenceArray<T> array, int index, T expected, T newValue)
	{
		while (true)
		{
			T oldValue = array.get(index);
			if (oldValue != expected) return oldValue;
			if (array.weakCompareAndSet(index, expected, newValue)) return newValue;
		}
	}
	
}
