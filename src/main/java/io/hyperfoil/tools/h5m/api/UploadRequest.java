package io.hyperfoil.tools.h5m.api;

import io.hyperfoil.tools.jjq.value.JqValue;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Request body for uploading JSON data to a folder")
public record UploadRequest(
        @Schema(description = "JSON data to upload (file or paste JSON)") JqValue file,
        @Schema(description = "URL to fetch JSON from") String url) {
}
