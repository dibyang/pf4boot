package net.xdob.pf4boot.exception;

public class DependencyNotStartedException extends RuntimeException{


	public DependencyNotStartedException(String dependency) {
		this(dependency, null);
	}

	public DependencyNotStartedException(String dependency, Throwable cause) {
		super("Plugin dependency " + dependency+ " is not started", cause);
	}

}
