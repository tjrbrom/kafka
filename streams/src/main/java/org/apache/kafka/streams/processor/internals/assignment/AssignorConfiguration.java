/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.streams.processor.internals.assignment;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.ConsumerPartitionAssignor.RebalanceProtocol;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.utils.LogContext;
import org.apache.kafka.common.utils.Time;
import org.apache.kafka.common.utils.Utils;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.StreamsConfig.InternalConfig;
import org.apache.kafka.streams.processor.internals.ClientUtils;
import org.apache.kafka.streams.processor.internals.InternalTopicManager;
import org.apache.kafka.streams.processor.internals.StreamsMetadataState;
import org.apache.kafka.streams.processor.internals.TaskManager;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.kafka.common.utils.Utils.getHost;
import static org.apache.kafka.common.utils.Utils.getPort;
import static org.apache.kafka.streams.StreamsConfig.InternalConfig.INTERNAL_TASK_ASSIGNOR_CLASS;
import static org.apache.kafka.streams.processor.internals.assignment.StreamsAssignmentProtocolVersions.LATEST_SUPPORTED_VERSION;

public final class AssignorConfiguration {
    private final String taskAssignorClass;

    private final String logPrefix;
    private final Logger log;
    private final TaskManager taskManager;
    private final Admin adminClient;

    private final StreamsConfig streamsConfig;
    private final Map<String, ?> internalConfigs;

    public AssignorConfiguration(final Map<String, ?> configs) {
        // NOTE: If you add a new config to pass through to here, be sure to test it in a real
        // application. Since we filter out some configurations, we may have to explicitly copy
        // them over when we construct the Consumer.
        streamsConfig = new ClientUtils.QuietStreamsConfig(configs);
        internalConfigs = configs;

        // Setting the logger with the passed in client thread name
        logPrefix = String.format("stream-thread [%s] ", streamsConfig.getString(CommonClientConfigs.CLIENT_ID_CONFIG));
        final LogContext logContext = new LogContext(logPrefix);
        log = logContext.logger(getClass());

        {
            final Object o = configs.get(StreamsConfig.InternalConfig.TASK_MANAGER_FOR_PARTITION_ASSIGNOR);
            if (o == null) {
                final KafkaException fatalException = new KafkaException("TaskManager is not specified");
                log.error(fatalException.getMessage(), fatalException);
                throw fatalException;
            }

            if (!(o instanceof TaskManager)) {
                final KafkaException fatalException = new KafkaException(
                    String.format("%s is not an instance of %s", o.getClass().getName(), TaskManager.class.getName())
                );
                log.error(fatalException.getMessage(), fatalException);
                throw fatalException;
            }

            taskManager = (TaskManager) o;
        }

        {
            final Object o = configs.get(StreamsConfig.InternalConfig.STREAMS_ADMIN_CLIENT);
            if (o == null) {
                final KafkaException fatalException = new KafkaException("Admin is not specified");
                log.error(fatalException.getMessage(), fatalException);
                throw fatalException;
            }

            if (!(o instanceof Admin)) {
                final KafkaException fatalException = new KafkaException(
                    String.format("%s is not an instance of %s", o.getClass().getName(), Admin.class.getName())
                );
                log.error(fatalException.getMessage(), fatalException);
                throw fatalException;
            }

            adminClient = (Admin) o;
        }

        {
            final String o = (String) configs.get(INTERNAL_TASK_ASSIGNOR_CLASS);
            if (o == null) {
                taskAssignorClass = HighAvailabilityTaskAssignor.class.getName();
            } else {
                taskAssignorClass = o;
            }
        }
    }

    public AtomicInteger assignmentErrorCode() {
        final Object ai = internalConfigs.get(StreamsConfig.InternalConfig.ASSIGNMENT_ERROR_CODE);
        if (ai == null) {
            final KafkaException fatalException = new KafkaException("assignmentErrorCode is not specified");
            log.error(fatalException.getMessage(), fatalException);
            throw fatalException;
        }

        if (!(ai instanceof AtomicInteger)) {
            final KafkaException fatalException = new KafkaException(
                String.format("%s is not an instance of %s", ai.getClass().getName(), AtomicInteger.class.getName())
            );
            log.error(fatalException.getMessage(), fatalException);
            throw fatalException;
        }
        return (AtomicInteger) ai;
    }

    public AtomicLong nextScheduledRebalanceMs() {
        final Object al = internalConfigs.get(InternalConfig.NEXT_SCHEDULED_REBALANCE_MS);
        if (al == null) {
            final KafkaException fatalException = new KafkaException("nextProbingRebalanceMs is not specified");
            log.error(fatalException.getMessage(), fatalException);
            throw fatalException;
        }

        if (!(al instanceof AtomicLong)) {
            final KafkaException fatalException = new KafkaException(
                String.format("%s is not an instance of %s", al.getClass().getName(), AtomicLong.class.getName())
            );
            log.error(fatalException.getMessage(), fatalException);
            throw fatalException;
        }

        return (AtomicLong) al;
    }

    public Time time() {
        final Object t = internalConfigs.get(InternalConfig.TIME);
        if (t == null) {
            final KafkaException fatalException = new KafkaException("time is not specified");
            log.error(fatalException.getMessage(), fatalException);
            throw fatalException;
        }

        if (!(t instanceof Time)) {
            final KafkaException fatalException = new KafkaException(
                String.format("%s is not an instance of %s", t.getClass().getName(), Time.class.getName())
            );
            log.error(fatalException.getMessage(), fatalException);
            throw fatalException;
        }

        return (Time) t;
    }

    public TaskManager taskManager() {
        return taskManager;
    }

    public StreamsMetadataState streamsMetadataState() {
        final Object o = internalConfigs.get(StreamsConfig.InternalConfig.STREAMS_METADATA_STATE_FOR_PARTITION_ASSIGNOR);
        if (o == null) {
            final KafkaException fatalException = new KafkaException("StreamsMetadataState is not specified");
            log.error(fatalException.getMessage(), fatalException);
            throw fatalException;
        }

        if (!(o instanceof StreamsMetadataState)) {
            final KafkaException fatalException = new KafkaException(
                String.format("%s is not an instance of %s", o.getClass().getName(), StreamsMetadataState.class.getName())
            );
            log.error(fatalException.getMessage(), fatalException);
            throw fatalException;
        }

        return (StreamsMetadataState) o;
    }

    public RebalanceProtocol rebalanceProtocol() {
        final String upgradeFrom = streamsConfig.getString(StreamsConfig.UPGRADE_FROM_CONFIG);
        if (upgradeFrom != null) {
            switch (upgradeFrom) {
                case StreamsConfig.UPGRADE_FROM_0100:
                case StreamsConfig.UPGRADE_FROM_0101:
                case StreamsConfig.UPGRADE_FROM_0102:
                case StreamsConfig.UPGRADE_FROM_0110:
                case StreamsConfig.UPGRADE_FROM_10:
                case StreamsConfig.UPGRADE_FROM_11:
                case StreamsConfig.UPGRADE_FROM_20:
                case StreamsConfig.UPGRADE_FROM_21:
                case StreamsConfig.UPGRADE_FROM_22:
                case StreamsConfig.UPGRADE_FROM_23:
                    log.info("Eager rebalancing enabled now for upgrade from {}.x", upgradeFrom);
                    return RebalanceProtocol.EAGER;
                default:
                    throw new IllegalArgumentException("Unknown configuration value for parameter 'upgrade.from': " + upgradeFrom);
            }
        }
        log.info("Cooperative rebalancing enabled now");
        return RebalanceProtocol.COOPERATIVE;
    }

    public String logPrefix() {
        return logPrefix;
    }

    public int configuredMetadataVersion(final int priorVersion) {
        final String upgradeFrom = streamsConfig.getString(StreamsConfig.UPGRADE_FROM_CONFIG);
        if (upgradeFrom != null) {
            switch (upgradeFrom) {
                case StreamsConfig.UPGRADE_FROM_0100:
                    log.info(
                        "Downgrading metadata version from {} to 1 for upgrade from 0.10.0.x.",
                        LATEST_SUPPORTED_VERSION
                    );
                    return 1;
                case StreamsConfig.UPGRADE_FROM_0101:
                case StreamsConfig.UPGRADE_FROM_0102:
                case StreamsConfig.UPGRADE_FROM_0110:
                case StreamsConfig.UPGRADE_FROM_10:
                case StreamsConfig.UPGRADE_FROM_11:
                    log.info(
                        "Downgrading metadata version from {} to 2 for upgrade from {}.x.",
                        LATEST_SUPPORTED_VERSION,
                        upgradeFrom
                    );
                    return 2;
                case StreamsConfig.UPGRADE_FROM_20:
                case StreamsConfig.UPGRADE_FROM_21:
                case StreamsConfig.UPGRADE_FROM_22:
                case StreamsConfig.UPGRADE_FROM_23:
                    // These configs are for cooperative rebalancing and should not affect the metadata version
                    break;
                default:
                    throw new IllegalArgumentException(
                        "Unknown configuration value for parameter 'upgrade.from': " + upgradeFrom
                    );
            }
        }
        return priorVersion;
    }

    @SuppressWarnings("deprecation")
    public org.apache.kafka.streams.processor.PartitionGrouper partitionGrouper() {
        return streamsConfig.getConfiguredInstance(
            StreamsConfig.PARTITION_GROUPER_CLASS_CONFIG,
            org.apache.kafka.streams.processor.PartitionGrouper.class
        );
    }

    public String userEndPoint() {
        final String configuredUserEndpoint = streamsConfig.getString(StreamsConfig.APPLICATION_SERVER_CONFIG);
        if (configuredUserEndpoint != null && !configuredUserEndpoint.isEmpty()) {
            try {
                final String host = getHost(configuredUserEndpoint);
                final Integer port = getPort(configuredUserEndpoint);

                if (host == null || port == null) {
                    throw new ConfigException(
                        String.format(
                            "%s Config %s isn't in the correct format. Expected a host:port pair but received %s",
                            logPrefix, StreamsConfig.APPLICATION_SERVER_CONFIG, configuredUserEndpoint
                        )
                    );
                }
            } catch (final NumberFormatException nfe) {
                throw new ConfigException(
                    String.format("%s Invalid port supplied in %s for config %s: %s",
                                  logPrefix, configuredUserEndpoint, StreamsConfig.APPLICATION_SERVER_CONFIG, nfe)
                );
            }
            return configuredUserEndpoint;
        } else {
            return null;
        }
    }

    public Admin adminClient() {
        return adminClient;
    }

    public InternalTopicManager internalTopicManager() {
        return new InternalTopicManager(time(), adminClient, streamsConfig);
    }

    public CopartitionedTopicsEnforcer copartitionedTopicsEnforcer() {
        return new CopartitionedTopicsEnforcer(logPrefix);
    }

    public AssignmentConfigs assignmentConfigs() {
        return new AssignmentConfigs(streamsConfig);
    }

    public TaskAssignor taskAssignor() {
        try {
            return Utils.newInstance(taskAssignorClass, TaskAssignor.class);
        } catch (final ClassNotFoundException e) {
            throw new IllegalArgumentException(
                "Expected an instantiable class name for " + INTERNAL_TASK_ASSIGNOR_CLASS,
                e
            );
        }
    }

    public AssignmentListener assignmentListener() {
        final Object o = internalConfigs.get(InternalConfig.ASSIGNMENT_LISTENER);
        if (o == null) {
            return stable -> { };
        }

        if (!(o instanceof AssignmentListener)) {
            final KafkaException fatalException = new KafkaException(
                String.format("%s is not an instance of %s", o.getClass().getName(), AssignmentListener.class.getName())
            );
            log.error(fatalException.getMessage(), fatalException);
            throw fatalException;
        }

        return (AssignmentListener) o;
    }

    public interface AssignmentListener {
        void onAssignmentComplete(final boolean stable);
    }

    public static class AssignmentConfigs {
        public final long acceptableRecoveryLag;
        public final int maxWarmupReplicas;
        public final int numStandbyReplicas;
        public final long probingRebalanceIntervalMs;

        private AssignmentConfigs(final StreamsConfig configs) {
            acceptableRecoveryLag = configs.getLong(StreamsConfig.ACCEPTABLE_RECOVERY_LAG_CONFIG);
            maxWarmupReplicas = configs.getInt(StreamsConfig.MAX_WARMUP_REPLICAS_CONFIG);
            numStandbyReplicas = configs.getInt(StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG);
            probingRebalanceIntervalMs = configs.getLong(StreamsConfig.PROBING_REBALANCE_INTERVAL_MS_CONFIG);
        }

        AssignmentConfigs(final Long acceptableRecoveryLag,
                          final Integer maxWarmupReplicas,
                          final Integer numStandbyReplicas,
                          final Long probingRebalanceIntervalMs) {
            this.acceptableRecoveryLag = validated(StreamsConfig.ACCEPTABLE_RECOVERY_LAG_CONFIG, acceptableRecoveryLag);
            this.maxWarmupReplicas = validated(StreamsConfig.MAX_WARMUP_REPLICAS_CONFIG, maxWarmupReplicas);
            this.numStandbyReplicas = validated(StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG, numStandbyReplicas);
            this.probingRebalanceIntervalMs = validated(StreamsConfig.PROBING_REBALANCE_INTERVAL_MS_CONFIG, probingRebalanceIntervalMs);
        }

        private static <T> T validated(final String configKey, final T value) {
            final ConfigDef.Validator validator = StreamsConfig.configDef().configKeys().get(configKey).validator;
            if (validator != null) {
                validator.ensureValid(configKey, value);
            }
            return value;
        }

        @Override
        public String toString() {
            return "AssignmentConfigs{" +
                "\n  acceptableRecoveryLag=" + acceptableRecoveryLag +
                "\n  maxWarmupReplicas=" + maxWarmupReplicas +
                "\n  numStandbyReplicas=" + numStandbyReplicas +
                "\n  probingRebalanceIntervalMs=" + probingRebalanceIntervalMs +
                "\n}";
        }
    }
}
