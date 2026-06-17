package com.laulain.rentals.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 400 — file upload validation failure */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class FileUploadException extends RuntimeException {
    public FileUploadException(String message) {
        super(message);
    }
}
