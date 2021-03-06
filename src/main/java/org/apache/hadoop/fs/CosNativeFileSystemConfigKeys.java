/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.hadoop.fs;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;

/**
 * This class contains constants for configuration keys used in the cos file system.
 */
@InterfaceAudience.Private
@InterfaceStability.Unstable
public class CosNativeFileSystemConfigKeys extends CommonConfigurationKeys {
    public static final String USER_AGENT = "fs.cosn.user.agent";
    public static final String DEFAULT_USER_AGENT = "cos-hadoop-plugin-v5.5.3";

    public static final String COS_CREDENTIALS_PROVIDER = "fs.cosn.credentials.provider";
    public static final String COS_APPID_KEY = "fs.cosn.userinfo.appid";
    public static final String COS_SECRET_ID_KEY = "fs.cosn.userinfo.secretId";
    public static final String COS_SECRET_KEY_KEY = "fs.cosn.userinfo.secretKey";
    public static final String COS_REGION_KEY = "fs.cosn.userinfo.region";
    public static final String COS_ENDPOINT_SUFFIX_KEY = "fs.cosn.userinfo.endpoint_suffix";

    public static final String COS_USE_HTTPS_KEY = "fs.cosn.useHttps";
    public static final boolean DEFAULT_USE_HTTPS = false;

    public static final String COS_BUFFER_DIR_KEY = "fs.cosn.tmp.dir";
    public static final String DEFAULT_BUFFER_DIR = "/tmp/hadoop_cos";

    public static final String COS_UPLOAD_BUFFER_SIZE_KEY = "fs.cosn.buffer.size";
    public static final int DEFAULT_UPLOAD_BUFFER_SIZE = 32 * Unit.MB;

    public static final String COS_BLOCK_SIZE_KEY = "fs.cosn.block.size";
    public static final int DEFAULT_BLOCK_SIZE = 8 * Unit.MB;

    public static final String COS_MAX_RETRIES_KEY = "fs.cosn.maxRetries";
    public static final int DEFAULT_MAX_RETRIES = 5;
    public static final String COS_RETRY_INTERVAL_KEY = "fs.cosn.retry.interval.seconds";
    public static final long DEFAULT_RETRY_INTERVAL = 3;

    public static final String UPLOAD_THREAD_POOL_SIZE_KEY = "fs.cosn.upload_thread_pool";
    public static final int DEFAULT_UPLOAD_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 5;

    public static final String COPY_THREAD_POOL_SIZE_KEY = "fs.cosn.copy_thread_pool";
    public static final int DEFAULT_COPY_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 3;

    public static final String THREAD_KEEP_ALIVE_TIME_KEY = "fs.cosn.threads.keep_alive_time";
    public static final long DEFAULT_THREAD_KEEP_ALIVE_TIME = 60L;

    public static final String READ_AHEAD_BLOCK_SIZE_KEY = "fs.cosn.read.ahead.block.size";
    public static final long DEFAULT_READ_AHEAD_BLOCK_SIZE = 512 * Unit.KB;
    public static final String READ_AHEAD_QUEUE_SIZE = "fs.cosn.read.ahead.queue.size";
    public static final int DEFAULT_READ_AHEAD_QUEUE_SIZE = 10;

    public static final String MAX_CONNECTION_NUM = "fs.cosn.max.connection.num";
    public static final int DEFAULT_MAX_CONNECTION_NUM = 2048;
}
