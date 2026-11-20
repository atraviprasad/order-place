package com.orderplace.catalog.storage;

import com.orderplace.common.web.ApiException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import java.io.ByteArrayInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Stores product images in MinIO (S3 compatible). Images are served directly by MinIO through a public URL. */
@Component
public class ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(ImageStorage.class);

    private final MinioClient client;
    private final String bucket;
    private final String publicUrl;

    public ImageStorage(MinioClient client,
                        @Value("${minio.bucket}") String bucket,
                        @Value("${minio.public-url}") String publicUrl) {
        this.client = client;
        this.bucket = bucket;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    /** Creates the bucket and makes it readable by anyone (DEV setup). A failure here must not stop the service. */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            String policy = """
                    {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},
                    "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}""".formatted(bucket);
            client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
            log.info("MinIO bucket '{}' is ready", bucket);
        } catch (Exception e) {
            log.warn("Could not prepare MinIO bucket '{}': {}. Image upload will fail until MinIO is reachable.", bucket, e.toString());
        }
    }

    public void put(String key, byte[] data, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(new ByteArrayInputStream(data), data.length, -1).contentType(contentType).build());
        } catch (Exception e) {
            log.error("MinIO upload failed for {}", key, e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "Image storage is not available. Try again later.");
        }
    }

    /** Never throws: a leftover object is harmless, a failed request because of it is not. */
    public void deleteQuietly(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            log.warn("Could not delete image object {}: {}", key, e.toString());
        }
    }

    public String publicUrl(String key) {
        return publicUrl + "/" + bucket + "/" + key;
    }
}
