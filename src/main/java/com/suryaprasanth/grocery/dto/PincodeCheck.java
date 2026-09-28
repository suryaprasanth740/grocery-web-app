package com.suryaprasanth.grocery.dto;

public record PincodeCheck(String pincode, boolean valid, boolean serviceable, String message, String eta) {
}
