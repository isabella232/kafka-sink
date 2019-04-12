/*
 * Copyright DataStax, Inc.
 *
 *   This software is subject to the below license agreement.
 *   DataStax may make changes to the agreement from time to time,
 *   and will post the amended terms at
 *   https://www.datastax.com/terms/datastax-apache-kafka-connector-license-terms.
 */
package com.datastax.kafkaconnector.record;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StructTtlConverterTest {

  @ParameterizedTest(name = "[{index}] fieldValue={0}, expectedSeconds={1}")
  @MethodSource("expectedToSeconds")
  void should_convert_java_number_types_that_are_supported_in_struct(
      Number fieldValue, Number expectedSeconds) {
    Object result = StructTtlConverter.transformField(TimeUnit.MILLISECONDS, fieldValue);

    // then
    assertThat(result).isEqualTo(expectedSeconds);
  }

  private static Stream<? extends Arguments> expectedToSeconds() {
    return Stream.of(
        Arguments.of(1000F, 1.0F),
        Arguments.of(-1000F, 0F),
        Arguments.of(1000D, 1D),
        Arguments.of(-1000D, 0D),
        Arguments.of(1000L, 1L),
        Arguments.of(-1000L, 0L),
        Arguments.of(1000, 1),
        Arguments.of(-1000, 0),
        Arguments.of((short) 1000, (short) 1),
        Arguments.of((short) -1000, (short) 0),
        Arguments.of(
            Long.valueOf(1000).byteValue(),
            Long.valueOf(0).byteValue()), // 1000L overflows byte making it < 1000L
        Arguments.of(Long.valueOf(-1000).byteValue(), Long.valueOf(0).byteValue()),
        Arguments.of(BigDecimal.valueOf(1000), BigDecimal.valueOf(1)),
        Arguments.of(BigDecimal.valueOf(-1000), BigDecimal.ZERO),
        Arguments.of(BigInteger.valueOf(1000), BigInteger.valueOf(1)),
        Arguments.of(BigInteger.valueOf(-1000), BigInteger.ZERO));
  }
}