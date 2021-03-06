package org.apache.hadoop.fs;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.qcloud.cos.model.PartETag;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class CosFsDataOutputStream extends OutputStream {
    static final Logger LOG = LoggerFactory.getLogger(CosFsDataOutputStream.class);

    private final Configuration conf;
    private final NativeFileSystemStore store;
    private MessageDigest digest;
    private long blockSize;
    private String key;
    private int currentBlockId = 0;
    private final Set<ByteBufferWrapper> blockCacheByteBufferWrappers = new HashSet<ByteBufferWrapper>();
    private ByteBufferWrapper currentBlockByteBufferWrapper = null;
    private OutputStream currentBlockOutputStream = null;
    private String uploadId = null;
    private final ListeningExecutorService executorService;
    private final List<ListenableFuture<PartETag>> partEtagList = new LinkedList<ListenableFuture<PartETag>>();
    private int blockWritten = 0;
    private boolean closed = false;

    public CosFsDataOutputStream(
            Configuration conf,
            NativeFileSystemStore store,
            String key, long blockSize,
            ExecutorService executorService) throws IOException {
        this.conf = conf;
        this.store = store;
        this.key = key;
        this.blockSize = blockSize;
        if (this.blockSize < Constants.MIN_PART_SIZE) {
            LOG.warn(String.format("The minimum size of a single block is limited to %d.", Constants.MIN_PART_SIZE));
            this.blockSize = Constants.MIN_PART_SIZE;
        }
        if (this.blockSize > Constants.MAX_PART_SIZE) {
            LOG.warn(String.format("The maximum size of a single block is limited to %d.", Constants.MAX_PART_SIZE));
            this.blockSize = Constants.MAX_PART_SIZE;
        }

        this.executorService = MoreExecutors.listeningDecorator(executorService);

        try {
            this.currentBlockByteBufferWrapper = BufferPool.getInstance().getBuffer((int) this.blockSize);
        } catch (InterruptedException e) {
            throw new IOException("Getting a buffer size: " + String.valueOf(this.blockSize) + " from buffer pool occurs an exception: ", e);
        }
        try {
            this.digest = MessageDigest.getInstance("MD5");
            this.currentBlockOutputStream = new DigestOutputStream(
                    new ByteBufferOutputStream(this.currentBlockByteBufferWrapper.getByteBuffer()), this.digest);
        } catch (NoSuchAlgorithmException e) {
            this.digest = null;
            this.currentBlockOutputStream = new ByteBufferOutputStream(this.currentBlockByteBufferWrapper.getByteBuffer());
        }
    }

    @Override
    public void flush() throws IOException {
        this.currentBlockOutputStream.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        if (this.closed) {
            return;
        }
        this.currentBlockOutputStream.flush();
        this.currentBlockOutputStream.close();
        if (!this.blockCacheByteBufferWrappers.contains(this.currentBlockByteBufferWrapper)) {
            this.blockCacheByteBufferWrappers.add(this.currentBlockByteBufferWrapper);
        }
        // 加到块列表中去
        if (this.blockCacheByteBufferWrappers.size() == 1) {
            // 单个文件就可以上传完成
            byte[] md5Hash = this.digest == null ? null : this.digest.digest();
            store.storeFile(this.key, new ByteBufferInputStream(this.currentBlockByteBufferWrapper.getByteBuffer()), md5Hash,
                    this.currentBlockByteBufferWrapper.getByteBuffer().remaining());
        } else {
            PartETag partETag = null;
            if (this.blockWritten > 0) {
                LOG.info("upload last part... blockId: " + this.currentBlockId + " written: " + this.blockWritten);
                partETag = store.uploadPart(
                        new ByteBufferInputStream(currentBlockByteBufferWrapper.getByteBuffer()), key, uploadId,
                        currentBlockId + 1, currentBlockByteBufferWrapper.getByteBuffer().remaining());
            }
            final List<PartETag> futurePartEtagList = this.waitForFinishPartUploads();
            if (null == futurePartEtagList) {
                throw new IOException("Failed to multipart upload to oss, abort it.");
            }
            List<PartETag> tempPartETagList = new LinkedList<PartETag>(futurePartEtagList);
            if (null != partETag) {
                tempPartETagList.add(partETag);
            }
            store.completeMultipartUpload(this.key, this.uploadId, tempPartETagList);
        }
        try {
            BufferPool.getInstance().returnBuffer(this.currentBlockByteBufferWrapper);
        } catch (InterruptedException e) {
            LOG.error("Returning the buffer to BufferPool occurs an exception.", e);
        }
        LOG.info("OutputStream for key '{}' upload complete", key);
        this.blockWritten = 0;
        this.closed = true;
    }

    private List<PartETag> waitForFinishPartUploads() throws IOException {
        try {
            LOG.info("waiting for finish part uploads ....");
            return Futures.allAsList(this.partEtagList).get();
        } catch (InterruptedException e) {
            LOG.error("Interrupt the part upload", e);
            return null;
        } catch (ExecutionException e) {
            LOG.error("cancelling futures");
            for (ListenableFuture<PartETag> future : this.partEtagList) {
                future.cancel(true);
            }
            (store).abortMultipartUpload(this.key, this.uploadId);
            LOG.error("Multipart upload with id: " + this.uploadId + " to " + this.key, e);
            throw new IOException("Multipart upload with id: " + this.uploadId + " to " + this.key, e);
        }
    }

    private void uploadPart() throws IOException {
        this.currentBlockOutputStream.flush();
        this.currentBlockOutputStream.close();
        this.blockCacheByteBufferWrappers.add(this.currentBlockByteBufferWrapper);

        if (this.currentBlockId == 0) {
            uploadId = (store).getUploadId(key);
        }

        ListenableFuture<PartETag> partETagListenableFuture = this.executorService.submit(new Callable<PartETag>() {
            private final ByteBufferWrapper byteBufferWrapper = currentBlockByteBufferWrapper;
            private final String localKey = key;
            private final String localUploadId = uploadId;
            private final int blockId = currentBlockId;

            @Override
            public PartETag call() throws Exception {
                PartETag partETag = (store).uploadPart(
                        new ByteBufferInputStream(this.byteBufferWrapper.getByteBuffer()), this.localKey, this.localUploadId,
                        this.blockId + 1, this.byteBufferWrapper.getByteBuffer().remaining());
                BufferPool.getInstance().returnBuffer(this.byteBufferWrapper);
                return partETag;
            }
        });
        this.partEtagList.add(partETagListenableFuture);
        try {
            this.currentBlockByteBufferWrapper = BufferPool.getInstance().getBuffer((int) this.blockSize);
        } catch (InterruptedException e) {
            throw new IOException("Getting a buffer size: " + String.valueOf(this.blockSize) + " from buffer pool occurs an exception: ", e);
        }
        this.currentBlockId++;
        if (null != this.digest) {
            this.digest.reset();
            this.currentBlockOutputStream = new DigestOutputStream(
                    new ByteBufferOutputStream(this.currentBlockByteBufferWrapper.getByteBuffer()), this.digest);
        } else {
            this.currentBlockOutputStream = new ByteBufferOutputStream(this.currentBlockByteBufferWrapper.getByteBuffer());
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (this.closed) {
            throw new IOException("block stream has been closed.");
        }

        while (len > 0) {
            long writeBytes = 0;
            if (this.blockWritten + len > this.blockSize) {
                writeBytes = this.blockSize - this.blockWritten;
            } else {
                writeBytes = len;
            }

            this.currentBlockOutputStream.write(b, off, (int) writeBytes);
            this.blockWritten += writeBytes;
            if (this.blockWritten >= this.blockSize) {
                this.uploadPart();
                this.blockWritten = 0;
            }
            len -= writeBytes;
            off += writeBytes;
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    @Override
    public void write(int b) throws IOException {
        if (this.closed) {
            throw new IOException("block stream has been closed.");
        }

        byte[] singleBytes = new byte[1];
        singleBytes[0] = (byte) b;
        this.currentBlockOutputStream.write(singleBytes, 0, 1);
        this.blockWritten += 1;
        if (this.blockWritten >= this.blockSize) {
            this.uploadPart();
            this.blockWritten = 0;
        }
    }
}
