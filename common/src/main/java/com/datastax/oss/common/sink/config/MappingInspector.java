/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.common.sink.config;

import com.datastax.oss.common.sink.ConfigException;
import com.datastax.oss.common.sink.record.RawData;
import com.datastax.oss.common.sink.util.FunctionMapper;
import com.datastax.oss.common.sink.util.SinkUtil;
import com.datastax.oss.common.sink.util.StringUtil;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.kafka.generated.schema.MappingBaseVisitor;
import com.datastax.oss.kafka.generated.schema.MappingLexer;
import com.datastax.oss.kafka.generated.schema.MappingParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/** Processor for a mapping string. */
class MappingInspector extends MappingBaseVisitor<CqlIdentifier> {

  // A mapping spec may refer to these special variables which are used to bind
  // input fields to the write timestamp or ttl of the record.
  // NB: This logic isn't currently used in the Kafka connector, but it might be
  // some day...

  private static final String EXTERNAL_TTL_VARNAME = "__ttl";
  private static final String EXTERNAL_TIMESTAMP_VARNAME = "__timestamp";

  private Map<CqlIdentifier, CqlIdentifier> mapping;
  private List<String> errors;

  public MappingInspector(String mapping, String settingName) {
    CodePointCharStream input = CharStreams.fromString(mapping);
    MappingLexer lexer = new MappingLexer(input);
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    MappingParser parser = new MappingParser(tokens);
    BaseErrorListener listener =
        new BaseErrorListener() {

          @Override
          public void syntaxError(
              Recognizer<?, ?> recognizer,
              Object offendingSymbol,
              int line,
              int col,
              String msg,
              RecognitionException e) {
            throw new ConfigException(
                settingName,
                StringUtil.singleQuote(mapping),
                String.format("Could not be parsed at line %d:%d: %s", line, col, msg));
          }
        };
    lexer.removeErrorListeners();
    lexer.addErrorListener(listener);
    parser.removeErrorListeners();
    parser.addErrorListener(listener);
    MappingParser.MappingContext ctx = parser.mapping();

    this.mapping = new LinkedHashMap<>();
    errors = new ArrayList<>();
    visit(ctx);
  }

  public Map<CqlIdentifier, CqlIdentifier> getMapping() {
    return mapping;
  }

  List<String> getErrors() {
    return errors;
  }

  @Override
  public CqlIdentifier visitMapping(MappingParser.MappingContext ctx) {
    if (!ctx.mappedEntry().isEmpty()) {
      for (MappingParser.MappedEntryContext entry : ctx.mappedEntry()) {
        visitMappedEntry(entry);
      }
    }
    return null;
  }

  @Override
  public CqlIdentifier visitMappedEntry(MappingParser.MappedEntryContext ctx) {
    CqlIdentifier field = visitField(ctx.field());
    CqlIdentifier column = visitColumn(ctx.column());
    if (mapping.containsKey(column)) {
      errors.add(String.format("Mapping already defined for column '%s'", column.asInternal()));
    }
    String fieldString = field.asInternal();
    if (fieldString.equals("value") || fieldString.equals("key")) {
      field = CqlIdentifier.fromInternal(fieldString + '.' + RawData.FIELD_NAME);
    } else if (!fieldString.startsWith("key.")
        && !fieldString.startsWith("value.")
        && !fieldString.startsWith("header.")
        && !FunctionMapper.SUPPORTED_FUNCTIONS_IN_MAPPING.contains(field)) {
      errors.add(generateErrorMessage(fieldString));
    }
    mapping.put(column, field);
    return null;
  }

  public static String generateErrorMessage(String fieldString) {
    return String.format(
        "Invalid field name '%s': field names in mapping must be 'key', 'value', or start with 'key.' or 'value.' or 'header.', or be one of supported functions: '%s'.",
        fieldString, FunctionMapper.SUPPORTED_FUNCTIONS_IN_MAPPING);
  }

  @Override
  public CqlIdentifier visitField(MappingParser.FieldContext ctx) {
    String field = ctx.getText();

    // If the field name is unquoted, treat it as a literal (no real parsing).
    // Otherwise parse it as cql. The idea is that users should be able to specify
    // case-sensitive identifiers in the mapping spec.

    return ctx.QUOTED_STRING() == null
        ? CqlIdentifier.fromInternal(field)
        : CqlIdentifier.fromCql(field);
  }

  @Override
  public CqlIdentifier visitColumn(MappingParser.ColumnContext ctx) {
    String column = ctx.getText();

    // If the column name is unquoted, treat it as a literal (no real parsing).
    // Otherwise parse it as cql. The idea is that users should be able to specify
    // case-sensitive identifiers in the mapping spec.

    if (ctx.QUOTED_STRING() == null) {
      // Rename the user-specified __ttl and __timestamp vars to the (legal) bound variable
      // names.
      if (column.equals(EXTERNAL_TTL_VARNAME)) {
        column = SinkUtil.TTL_VARNAME;
      } else if (column.equals(EXTERNAL_TIMESTAMP_VARNAME)) {
        column = SinkUtil.TIMESTAMP_VARNAME;
      }
      return CqlIdentifier.fromInternal(column);
    }
    return CqlIdentifier.fromCql(column);
  }
}
