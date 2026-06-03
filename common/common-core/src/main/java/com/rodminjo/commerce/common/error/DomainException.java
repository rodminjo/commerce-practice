package com.rodminjo.commerce.common.error;

public class DomainException extends RuntimeException {

	private final ErrorCode errorCode;

	public DomainException(ErrorCode errorCode) {
		super(errorCode.defaultMessage());
		this.errorCode = errorCode;
	}

	public DomainException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}
}
