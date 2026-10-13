package com.suryaprasanth.grocery.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Stars and an optional comment for a product the buyer received. */
public class ReviewRequest {

    @NotNull(message = "Choose 1 to 5 stars")
    @Min(value = 1, message = "Choose 1 to 5 stars")
    @Max(value = 5, message = "Choose 1 to 5 stars")
    private Integer rating;

    @Size(max = 500, message = "Comments can be at most 500 characters")
    private String comment;

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
