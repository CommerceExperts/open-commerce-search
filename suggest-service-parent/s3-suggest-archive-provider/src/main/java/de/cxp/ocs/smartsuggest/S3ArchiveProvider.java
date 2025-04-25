package de.cxp.ocs.smartsuggest;

import de.cxp.ocs.smartsuggest.spi.CompoundIndexArchiveProvider;
import de.cxp.ocs.smartsuggest.spi.IndexArchive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Stores the index-archives in a s3 bucket (optionally with some prefix) in the following structure:
 * <pre>
 *     s3://{bucket}/{prefix}{indexName}/
 *     	  - {suffix_1}/archive.tar.gz
 *     	  - {suffix_1}/archive.tar.gz
 * </pre>
 *
 * Configuration for this provider:
 * <ul>
 *     <li>bucket: Required: The name of the s3 bucket</li>
 *     <li>prefix: Optional value that should be prepended to all keys.
 *     If it does not end with a slash, it won't be added, so the prefix will just be part of the index name prefix.</li>
 *     <li>region: Optional value to specify in which region the bucket is located</li>
 * </ul>
 *
 * Make sure the bucket exists prior to the startup of the according service, as verification is done at startup and not at runtime.
 * A bucket won't be created automatically, since this provider won't choose a policy / ACL rules for you.
 */
public class S3ArchiveProvider extends CompoundIndexArchiveProvider implements Closeable {

	private final static String ARCHIVE_FILE_NAME         = "archive.tar.gz";
	private final static String ARCHIVE_MOD_TIME_META_KEY = "archive-mtime";

	private static final Logger   log = LoggerFactory.getLogger(S3ArchiveProvider.class);
	private              String   bucketName;
	private              String   prefix;
	private              S3Client s3Client;

	@Override
	public void configure(Map<String, Object> config) {
		bucketName = Objects.requireNonNull(config.get("bucket"), "bucket config is required").toString();
		prefix = config.getOrDefault("prefix", "").toString();

		var s3ClientBuilder = S3Client.builder().httpClientBuilder(ApacheHttpClient.builder());
		Optional.ofNullable(config.get("region")).map(r -> Region.of(r.toString())).ifPresent(s3ClientBuilder::region);
		// not documented, should only be used for testing
		Optional.ofNullable(config.get("_forcePathStyle")).map(e -> Boolean.parseBoolean(e.toString())).ifPresent(s3ClientBuilder::forcePathStyle);
		Optional.ofNullable(config.get("_endpoint")).map(e -> URI.create(e.toString())).ifPresent(s3ClientBuilder::endpointOverride);
		s3Client = s3ClientBuilder.build();

		verifyBucketExists();
		verifyBucketIsWriteable();

		log.info("initialized s3 client for suggest-index-archiving to connect to s3://{}/{}", bucketName, prefix);
	}

	private void verifyBucketExists() {
		HeadBucketResponse headBucketResponse = s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
		if (headBucketResponse.sdkHttpResponse().statusCode() >= 400) {
			throw new IllegalStateException("bucket " + bucketName + " does not exist! Please create bucket prior to usage!");
		}
	}

	private void verifyBucketIsWriteable() {
		// use prefix as well, in case there is a partial write access only with that prefix
		String testKey = prefix + "write-access-test.txt";
		try {
			// Attempt to upload the file
			s3Client.putObject(PutObjectRequest.builder()
							.bucket(bucketName)
							.key(testKey)
							.build(),
					RequestBody.fromBytes(Instant.now().toString().getBytes()));
		}
		catch (S3Exception e) {
			throw new IllegalStateException("Error verifying write access to bucket " + bucketName + ": " + e.getMessage());
		}
	}

	@Override
	public Collection<String> getIndexSuffixes(String indexName) {
		String fullPrefix = prefix + indexName + "/";
		ListObjectsV2Response objectList = s3Client.listObjectsV2(b -> b.bucket(bucketName).prefix(prefix));

		Set<String> indexSuffixes = objectList.contents().stream()
				.map(S3Object::key)
				.filter(k -> k.startsWith(indexName))
				.map(k -> {
					String stripped = k.substring(fullPrefix.length());
					int i = stripped.indexOf('/');
					if (i > 0) stripped = stripped.substring(0, i);
					return stripped;
				})
				.filter(str -> !ARCHIVE_FILE_NAME.equals(str))
				.collect(Collectors.toSet());
		log.debug("for index {} found suffixes: {}", indexName, indexSuffixes);
		return indexSuffixes;
	}

	@Override
	public void store(String indexName, IndexArchive archive) throws IOException {
		String fullObjectName = getObjectKey(indexName);

		try {
			s3Client.putObject(PutObjectRequest.builder()
							.bucket(bucketName)
							.key(fullObjectName)
							.metadata(Map.of(ARCHIVE_MOD_TIME_META_KEY, String.valueOf(archive.getModificationTime())))
							.build(),
					RequestBody.fromFile(archive.zippedTarFile()));
		}
		catch (Exception e) {
			throw new IOException("failed to upload archive " + fullObjectName + " for index " + indexName, e);
		}
	}

	private String getObjectKey(String indexName) {
		return prefix + indexName + "/" + ARCHIVE_FILE_NAME;
	}

	@Override
	public long getLastDataModTime(String indexName) throws IOException {
		try {
			HeadObjectResponse objectMeta = s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(getObjectKey(indexName)).build());
			return objectMeta == null ? -1L : getModTime(objectMeta.metadata());
		}
		catch (Exception e) {
			throw new IOException("object for indexName " + indexName + " could not be found", e);
		}
	}

	private static long getModTime(Map<String, String> objectMeta) {
		String modTimeMetaValue = objectMeta != null ? objectMeta.get(ARCHIVE_MOD_TIME_META_KEY) : null;
		return modTimeMetaValue != null ? Long.parseLong(modTimeMetaValue) : -1;
	}

	@Override
	public boolean hasData(String indexName) {
		try {
			return getLastDataModTime(indexName) > 0L;
		}
		catch (IOException e) {
			return false;
		}
	}

	@Override
	public IndexArchive loadData(String indexName) throws IOException {
		try {
			// only create parent directory, not the file itself; its created by the getObject call
			Path tempFilePath = Files.createTempDirectory(indexName.replace('/', '_')).resolve(ARCHIVE_FILE_NAME);
			GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(getObjectKey(indexName)).build();
			GetObjectResponse objectMeta = s3Client.getObject(getObjectRequest, tempFilePath);
			return new IndexArchive(tempFilePath.toFile(), getModTime(objectMeta.metadata()));
		}
		catch (Exception e) {
			throw new IOException("could not retrieve index archive for " + indexName, e);
		}
	}

	@Override
	public void close() {
		if (s3Client != null) s3Client.close();
	}
}
