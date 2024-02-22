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

package com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection;


import com.seibel.distanthorizons.api.interfaces.override.IDhApiOverrideable;

public interface IOverrideInjector<BindableType extends IBindable>
{
	/**
	 * All core overrides should have this priority. <Br>
	 * Should be lower than {@link IOverrideInjector#MIN_NON_CORE_OVERRIDE_PRIORITY}.
	 */
	public static final int CORE_PRIORITY = -1;
	/**
	 * The lowest priority non-core overrides can have.
	 * Should be higher than {@link IOverrideInjector#CORE_PRIORITY}.
	 */
	public static final int MIN_NON_CORE_OVERRIDE_PRIORITY = 0;
	/** The priority given to overrides that don't explicitly define a priority. */
	public static final int DEFAULT_NON_CORE_OVERRIDE_PRIORITY = 10;
	
	
	
	/**
	 * See {@link IDependencyInjector#bind(Class, IBindable) bind(Class, IBindable)} for full documentation.
	 *
	 * @throws IllegalArgumentException if a non-Distant Horizons Override with the priority CORE is passed in or a invalid priority value.
	 * @throws IllegalStateException if another override with the given priority already has been bound.
	 * @see IDependencyInjector#bind(Class, IBindable)
	 */
	void bind(Class<? extends IDhApiOverrideable> dependencyInterface, IDhApiOverrideable dependencyImplementation) throws IllegalStateException, IllegalArgumentException;
	
	/**
	 * Returns the bound dependency with the highest priority. <br>
	 * See {@link IDependencyInjector#get(Class, boolean) get(Class, boolean)} for full documentation.
	 *
	 * @see IDependencyInjector#get(Class, boolean)
	 */
	<T extends IDhApiOverrideable> T get(Class<T> interfaceClass) throws ClassCastException;
	
	/**
	 * Returns a dependency of type T with the specified priority if one has been bound. <br>
	 * If there is a dependency, but it was bound with a different priority this will return null. <br> <br>
	 *
	 * See {@link IDependencyInjector#get(Class, boolean) get(Class, boolean)} for more documentation.
	 *
	 * @see IDependencyInjector#get(Class, boolean)
	 */
	<T extends IDhApiOverrideable> T get(Class<T> interfaceClass, int priority) throws ClassCastException;
	
	
	
	/** Removes all bound overrides. */
	void clear();
	
	
}
