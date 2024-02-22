package com.seibel.distanthorizons.core.sql;

public class DbConnectionClosedException extends Exception
{
	public DbConnectionClosedException() {
		super("The database connection is closed.");
	}
	
	public DbConnectionClosedException(String message) {
		super(message);
	}
	
	public DbConnectionClosedException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public DbConnectionClosedException(Throwable cause) {
		super(cause);
	}
}
