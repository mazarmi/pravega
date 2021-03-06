/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.segmentstore.contracts;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Defines all operations that are supported on a StreamSegment.
 *
 * Notes about all AttributeUpdates parameters in this interface's methods:
 * * Only the Attributes contained in this collection will be touched; all other attributes will be left intact.
 * * This can update both Core or Extended Attributes. If an Extended Attribute is updated, its latest value will be kept
 * in memory for a while (based on Segment Metadata eviction or other rules), which allow for efficient pipelining.
 * * If an Extended Attribute is not loaded, use getAttributes() to load its latest value up.
 * * To delete an Attribute, set its value to Attributes.NULL_ATTRIBUTE_VALUE.
 */
public interface StreamSegmentStore {
    /**
     * Appends a range of bytes at the end of a StreamSegment and atomically updates the given attributes. The byte range
     * will be appended as a contiguous block, however there is no guarantee of ordering between different calls to this
     * method.
     *
     * @param streamSegmentName The name of the StreamSegment to append to.
     * @param data              The data to add.
     * @param attributeUpdates  A Collection of Attribute-Values to set or update. May be null (which indicates no updates).
     *                          See Notes about AttributeUpdates in the interface Javadoc.
     * @param timeout           Timeout for the operation
     * @return A CompletableFuture that, will completed normally, if the add was added. If the
     * operation failed, the future will be failed with the causing exception.
     * @throws NullPointerException     If any of the arguments are null, except attributeUpdates.
     * @throws IllegalArgumentException If the StreamSegment Name is invalid (NOTE: this doesn't
     *                                  check if the StreamSegment does not exist - that exception will be set in the
     *                                  returned CompletableFuture).
     */
    CompletableFuture<Void> append(String streamSegmentName, byte[] data, Collection<AttributeUpdate> attributeUpdates, Duration timeout);

    /**
     * Appends a range of bytes at the end of a StreamSegment an atomically updates the given attributes, but only if the
     * current length of the StreamSegment equals a certain value. The byte range will be appended as a contiguous block.
     * This method guarantees ordering (among subsequent calls).
     *
     * @param streamSegmentName The name of the StreamSegment to append to.
     * @param offset            The offset at which to append. If the current length of the StreamSegment does not equal
     *                          this value, the operation will fail with a BadOffsetException.
     * @param data              The data to add.
     * @param attributeUpdates  A Collection of Attribute-Values to set or update. May be null (which indicates no updates).
     *                          See Notes about AttributeUpdates in the interface Javadoc.
     * @param timeout           Timeout for the operation
     * @return A CompletableFuture that, when completed normally, will indicate the append completed successfully.
     * If the operation failed, the future will be failed with the causing exception.
     * @throws NullPointerException     If any of the arguments are null, except attributeUpdates.
     * @throws IllegalArgumentException If the StreamSegment Name is invalid (NOTE: this doesn't check if the StreamSegment
     *                                  does not exist - that exception will be set in the returned CompletableFuture).
     */
    CompletableFuture<Void> append(String streamSegmentName, long offset, byte[] data, Collection<AttributeUpdate> attributeUpdates, Duration timeout);

    /**
     * Performs an attribute update operation on the given Segment.
     *
     * @param streamSegmentName The name of the StreamSegment which will have its attributes updated.
     * @param attributeUpdates  A Collection of Attribute-Values to set or update. May be null (which indicates no updates).
     *                          See Notes about AttributeUpdates in the interface Javadoc.
     * @param timeout           Timeout for the operation
     * @return A CompletableFuture that, when completed normally, will indicate the update completed successfully.
     * If the operation failed, the future will be failed with the causing exception.
     * @throws NullPointerException     If any of the arguments are null.
     * @throws IllegalArgumentException If the StreamSegment Name is invalid (NOTE: this doesn't check if the StreamSegment
     *                                  does not exist - that exception will be set in the returned CompletableFuture).
     */
    CompletableFuture<Void> updateAttributes(String streamSegmentName, Collection<AttributeUpdate> attributeUpdates, Duration timeout);

    /**
     * Gets the values of the given Attributes (Core or Extended).
     *
     * Lookup order:
     * 1. (Core or Extended) In-memory Segment Metadata cache (which always has the latest value of an attribute).
     * 2. (Extended only) Backing Attribute Index for this Segment.
     *
     * @param streamSegmentName The name of the StreamSegment for which to get attributes.
     * @param attributeIds      A Collection of Attribute Ids to fetch. These may be Core or Extended Attributes.
     * @param cache             If set, then any Extended Attribute values that are not already in the in-memory Segment
     *                          Metadata cache will be atomically added using a conditional update (comparing against a missing value).
     *                          This argument will be ignored if the StreamSegment is currently Sealed.
     * @param timeout           Timeout for the operation.
     * @return A Completable future that, when completed, will contain a Map of Attribute Ids to their latest values. Any
     * Attribute that is not set will also be returned (with a value equal to Attributes.NULL_ATTRIBUTE_VALUE). If the operation
     * failed, the future will be failed with the causing exception.
     * @throws NullPointerException     If any of the arguments are null.
     * @throws IllegalArgumentException If the StreamSegment Name is invalid (NOTE: this doesn't check if the StreamSegment
     *                                  does not exist - that exception will be set in the returned CompletableFuture).
     */
    CompletableFuture<Map<UUID, Long>> getAttributes(String streamSegmentName, Collection<UUID> attributeIds, boolean cache, Duration timeout);

    /**
     * Initiates a Read operation on a particular StreamSegment and returns a ReadResult which can be used to consume the
     * read data.
     *
     * @param streamSegmentName The name of the StreamSegment to read from.
     * @param offset            The offset within the stream to start reading at.
     * @param maxLength         The maximum number of bytes to read.
     * @param timeout           Timeout for the operation.
     * @return A CompletableFuture that, when completed normally, will contain a ReadResult instance that can be used to
     * consume the read data. If the operation failed, the future will be failed with the causing exception. The future
     * will be failed with a {@link java.util.concurrent.CancellationException} if the segment container is shutting down
     * or the segment is evicted from memory.
     * @throws NullPointerException     If any of the arguments are null.
     * @throws IllegalArgumentException If any of the arguments are invalid.
     */
    CompletableFuture<ReadResult> read(String streamSegmentName, long offset, int maxLength, Duration timeout);

    /**
     * Gets information about a StreamSegment.
     *
     * @param streamSegmentName The name of the StreamSegment.
     * @param waitForPendingOps If true, it waits for all operations that are currently pending to complete before returning
     *                          the result. Use this parameter if you need consistency with respect to operation order
     *                          (for example, if a series of Appends were just added but not yet processed, a call to
     *                          this method with isSync==false would not guarantee those appends are taken into consideration).
     *                          A side effect of setting this to true is that the operation may take longer to process
     *                          because it needs to wait for pending ops to complete.
     * @param timeout           Timeout for the operation.
     * @return A CompletableFuture that, when completed normally, will contain the result. If the operation failed, the
     * future will be failed with the causing exception. Note that this result will only contain those attributes that
     * are loaded in memory (if any) or Core Attributes. To ensure that Extended Attributes are also included, you must use
     * getAttributes(), which will fetch all attributes, regardless of where they are currently located.
     * @throws IllegalArgumentException If any of the arguments are invalid.
     */
    CompletableFuture<SegmentProperties> getStreamSegmentInfo(String streamSegmentName, boolean waitForPendingOps, Duration timeout);

    /**
     * Creates a new StreamSegment.
     *
     * @param streamSegmentName The name of the StreamSegment to create.
     * @param attributes        A Collection of Attribute-Values to set on the newly created StreamSegment. May be null.
     *                          See Notes about AttributeUpdates in the interface Javadoc.
     * @param timeout           Timeout for the operation.
     * @return A CompletableFuture that, when completed normally, will indicate the operation completed. If the operation
     * failed, the future will be failed with the causing exception.
     * @throws IllegalArgumentException If any of the arguments are invalid.
     */
    CompletableFuture<Void> createStreamSegment(String streamSegmentName, Collection<AttributeUpdate> attributes, Duration timeout);

    /**
     * Merges a Sealed StreamSegment into another.
     *
     * @param targetSegmentName The name of the StreamSegment to merge into.
     * @param sourceSegmentName The name of the StreamSegment to merge.
     * @param timeout           Timeout for the operation.
     * @return A CompletableFuture that, when completed normally, will contain a SegmentProperties instance with the last known
     * state of the source Segment. If the operation failed, the future will be failed with the causing exception.
     * @throws IllegalArgumentException If any of the arguments are invalid.
     */
    CompletableFuture<SegmentProperties> mergeStreamSegment(String targetSegmentName, String sourceSegmentName, Duration timeout);

    /**
     * Seals a StreamSegment for modifications.
     *
     * @param streamSegmentName The name of the StreamSegment to seal.
     * @param timeout           Timeout for the operation
     * @return A CompletableFuture that, when completed normally, will contain the final length of the StreamSegment.
     * If the operation failed, the future will be failed with the causing exception.
     * @throws IllegalArgumentException If any of the arguments are invalid.
     */
    CompletableFuture<Long> sealStreamSegment(String streamSegmentName, Duration timeout);

    /**
     * Deletes a StreamSegment.
     *
     * @param streamSegmentName The name of the StreamSegment to delete.
     * @param timeout           Timeout for the operation.
     * @return A CompletableFuture that, when completed normally, will indicate the operation completed. If the operation
     * failed, the future will be failed with the causing exception.
     * @throws IllegalArgumentException If any of the arguments are invalid.
     */
    CompletableFuture<Void> deleteStreamSegment(String streamSegmentName, Duration timeout);

    /**
     * Truncates a StreamSegment at a given offset.
     *
     * @param streamSegmentName The name of the StreamSegment to truncate.
     * @param offset            The offset at which to truncate. This must be at least equal to the existing truncation
     *                          offset and no larger than the StreamSegment's length. After the operation is complete,
     *                          no offsets below this one will be accessible anymore.
     * @param timeout           Timeout for the operation.
     * @return A CompletableFuture that, when completed normally, will indicate the operation completed. If the operation
     * failed, the future will be failed with the causing exception.
     */
    CompletableFuture<Void> truncateStreamSegment(String streamSegmentName, long offset, Duration timeout);
}
