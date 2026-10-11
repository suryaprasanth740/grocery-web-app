package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.model.StoredImage;
import com.suryaprasanth.grocery.service.ImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Serves uploaded product photos and shop logos. Photos never change, so browsers may cache them. */
@RestController
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @GetMapping("/api/images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        StoredImage image = imageService.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.getData());
    }
}
