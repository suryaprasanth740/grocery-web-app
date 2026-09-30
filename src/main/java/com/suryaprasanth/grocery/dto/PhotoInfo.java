package com.suryaprasanth.grocery.dto;

/** A recipe photo and its credit line (required by the CC BY-SA licence). */
public record PhotoInfo(String url, String author, String sourcePage, String license, String licenseUrl) {
}
