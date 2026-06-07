package com.rodminjo.commerce.common.error;

public interface ErrorCode {
	String code();

	String defaultMessage();

	/** Abstract meaning of this error; the web adapter maps it to an HTTP status. */
	ErrorType type();
}
