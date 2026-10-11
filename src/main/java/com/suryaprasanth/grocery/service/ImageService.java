package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.model.StoredImage;
import com.suryaprasanth.grocery.repository.StoredImageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks and stores uploaded photos, and decides which image addresses a shop may use. */
@Service
public class ImageService {

    private static final Pattern OWN_IMAGE = Pattern.compile("^/api/images/(\\d+)$");

    private final StoredImageRepository repository;

    public ImageService(StoredImageRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public StoredImage store(Long shopId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a photo to upload");
        }
        if (file.getSize() > StoredImage.MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Photo is too big. The limit is 300 KB; the page normally shrinks photos for you.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the photo");
        }
        // Trust the file's first bytes, not its name or the type the browser claims.
        String type = detectType(bytes);
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Upload a JPEG, PNG or WebP photo");
        }
        return repository.save(new StoredImage(shopId, type, bytes));
    }

    @Transactional(readOnly = true)
    public StoredImage get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
    }

    /**
     * A seller may only use photos their own shop uploaded ("/api/images/42"), never an outside
     * web address. Returns the cleaned value (null = no photo) or throws 400.
     */
    public String checkSellerImageUrl(Long shopId, String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher m = OWN_IMAGE.matcher(url.trim());
        if (!m.matches() || !repository.existsByIdAndShopId(Long.valueOf(m.group(1)), shopId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a photo uploaded from your own shop");
        }
        return url.trim();
    }

    static String detectType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
