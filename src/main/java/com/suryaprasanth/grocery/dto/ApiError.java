package com.suryaprasanth.grocery.dto;

/** Simple uniform error payload returned to the frontend as JSON. */
public class ApiError {

    private String message;

    public ApiError() {
    }

    public ApiError(String message) {
        this.message = message;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
