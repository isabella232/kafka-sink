/*
 * Copyright DataStax, Inc.
 *
 * This software is subject to the below license agreement.
 * DataStax may make changes to the agreement from time to time,
 * and will post the amended terms at
 * https://www.datastax.com/terms/datastax-dse-bulk-utility-license-terms.
 */
package com.datastax.kafkaconnector.config;

import static com.datastax.kafkaconnector.config.DseSinkConfig.CONTACT_POINTS_OPT;
import static com.datastax.kafkaconnector.config.DseSinkConfig.DC_OPT;
import static com.datastax.kafkaconnector.config.DseSinkConfig.PORT_OPT;
import static com.datastax.kafkaconnector.config.TopicConfig.KEYSPACE_OPT;
import static com.datastax.kafkaconnector.config.TopicConfig.MAPPING_OPT;
import static com.datastax.kafkaconnector.config.TopicConfig.TABLE_OPT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datastax.kafkaconnector.util.SinkUtil;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Map;
import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.Test;

class DseSinkConfigTest {
  @Test
  void should_error_invalid_port() {
    Map<String, String> props =
        Maps.newHashMap(ImmutableMap.<String, String>builder().put(PORT_OPT, "foo").build());
    assertThatThrownBy(() -> new DseSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Invalid value foo for configuration port");

    props.put(PORT_OPT, "0");
    assertThatThrownBy(() -> new DseSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Value must be at least 1");

    props.put(PORT_OPT, "-1");
    assertThatThrownBy(() -> new DseSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Value must be at least 1");
  }

  @Test
  void should_error_missing_dc_with_contactPoints() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder().put(CONTACT_POINTS_OPT, "127.0.0.1").build();
    assertThatThrownBy(() -> new DseSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining(
            String.format("When contact points is provided, %s must also be specified", DC_OPT));
  }

  @Test
  void should_handle_dc_with_contactPoints() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder()
            .put(CONTACT_POINTS_OPT, "127.0.0.1, 127.0.1.1")
            .put(DC_OPT, "local")
            .build();

    DseSinkConfig d = new DseSinkConfig(props);
    assertThat(d.getContactPoints()).containsExactly("127.0.0.1", "127.0.1.1");
    assertThat(d.getLocalDc()).isEqualTo("local");
  }

  @Test
  void should_handle_port() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder().put(PORT_OPT, "5725").build();

    DseSinkConfig d = new DseSinkConfig(props);
    assertThat(d.getPort()).isEqualTo(5725);
  }

  @Test
  void should_handle_instance_name() {
    Map<String, String> props =
        ImmutableMap.<String, String>builder().put(SinkUtil.NAME_OPT, "myinst").build();

    DseSinkConfig d = new DseSinkConfig(props);
    assertThat(d.getInstanceName()).isEqualTo("myinst");
  }

  @Test
  void should_parse_multiple_topic_configs() {
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder()
                .put(TopicConfig.getTopicSettingName("mytopic", KEYSPACE_OPT), "MyKs")
                .put(TopicConfig.getTopicSettingName("mytopic", TABLE_OPT), "MyTable")
                .put(TopicConfig.getTopicSettingName("mytopic", MAPPING_OPT), "c1=value.f1")
                .put(TopicConfig.getTopicSettingName("yourtopic", KEYSPACE_OPT), "MyKs2")
                .put(TopicConfig.getTopicSettingName("yourtopic", TABLE_OPT), "MyTable2")
                .put(TopicConfig.getTopicSettingName("yourtopic", MAPPING_OPT), "d1=value.f1")
                .build());
    DseSinkConfig d = new DseSinkConfig(props);
    Map<String, TopicConfig> topicConfigs = d.getTopicConfigs();
    assertThat(topicConfigs.size()).isEqualTo(2);
    assertTopic(
        "MyKs",
        "MyTable",
        ImmutableMap.<CqlIdentifier, CqlIdentifier>builder()
            .put(CqlIdentifier.fromInternal("c1"), CqlIdentifier.fromInternal("value.f1"))
            .build(),
        topicConfigs.get("mytopic"));
    assertTopic(
        "MyKs2",
        "MyTable2",
        ImmutableMap.<CqlIdentifier, CqlIdentifier>builder()
            .put(CqlIdentifier.fromInternal("d1"), CqlIdentifier.fromInternal("value.f1"))
            .build(),
        topicConfigs.get("yourtopic"));
  }

  @Test
  void should_error_when_missing_topic_settings() {
    Map<String, String> props =
        Maps.newHashMap(
            ImmutableMap.<String, String>builder()
                .put("topics", "mytopic2")
                .put(TopicConfig.getTopicSettingName("mytopic", KEYSPACE_OPT), "MyKs")
                .put(TopicConfig.getTopicSettingName("mytopic", TABLE_OPT), "MyTable")
                .put(TopicConfig.getTopicSettingName("mytopic", MAPPING_OPT), "c1=value.f1")
                .build());

    assertThatThrownBy(() -> new DseSinkConfig(props))
        .isInstanceOf(ConfigException.class)
        .hasMessageContaining("Missing topic settings (topic.mytopic2.*) for topic mytopic2");
  }

  private void assertTopic(
      String keyspace,
      String table,
      Map<CqlIdentifier, CqlIdentifier> mapping,
      TopicConfig config) {
    assertThat(config.getKeyspace()).isEqualTo(CqlIdentifier.fromInternal(keyspace));
    assertThat(config.getTable()).isEqualTo(CqlIdentifier.fromInternal(table));
    assertThat(config.getMapping()).isEqualTo(mapping);
  }
}