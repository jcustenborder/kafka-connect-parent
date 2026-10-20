/**
 * Copyright © 2017 Jeremy Custenborder (jcustenborder@gmail.com)
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
package example;

import com.github.jcustenborder.kafka.connect.utils.AssertStruct;
import com.github.jcustenborder.kafka.connect.utils.data.SchemaBuilders;
import com.github.jcustenborder.kafka.connect.utils.data.Parser;
import com.github.jcustenborder.kafka.connect.utils.data.TestDataUtils;
import com.github.jcustenborder.kafka.connect.utils.jackson.ObjectMapperFactory;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaselineTest {
  @Test
  void transformsUsingRuntimeLibrariesOnJava17() {
    assertEquals(17, Runtime.version().feature(), "Verification must run on an actual JDK 17");
    SourceRecord input = mock(SourceRecord.class);
    when(input.value()).thenReturn("hello");
    // Use a real record for newRecord; Mockito still exercises Java 17 instrumentation.
    SourceRecord record = new SourceRecord(Map.of(), Map.of(), "verify", null, input.value());
    try (VerifiedTransformation transformation = new VerifiedTransformation()) {
      assertEquals("\"java-17\"", transformation.apply(record).value());
    }
  }

  @Test
  void roundTripsKafkaStructWithCompanionModules() throws Exception {
    Schema schema = SchemaBuilder.struct().field("message", Schema.STRING_SCHEMA).build();
    Struct value = new Struct(schema).put("message", "hello");
    Struct restored = ObjectMapperFactory.INSTANCE.readValue(
        ObjectMapperFactory.INSTANCE.writeValueAsBytes(value), Struct.class);
    AssertStruct.assertStruct(value, restored);
    assertEquals(Schema.Type.STRUCT, SchemaBuilders.of(schema).build().type());
    assertEquals(42, new Parser().parseString(Schema.INT32_SCHEMA, "42"));
    assertEquals(2, TestDataUtils.randomBigDecimal(2).scale());
    try (JsonConverter converter = new JsonConverter()) {
      converter.configure(Map.of("schemas.enable", true), false);
      AssertStruct.assertStruct(value, (Struct) converter.toConnectData("verify",
          converter.fromConnectData("verify", schema, value)).value());
    }
  }

  @Test
  void usesWorkingLogbackBackend() {
    ch.qos.logback.classic.Logger logger = assertInstanceOf(
        ch.qos.logback.classic.Logger.class, LoggerFactory.getLogger(BaselineTest.class));
    assertEquals(Level.INFO.toString(), ch.qos.logback.classic.Level.INFO.toString());
    logger.info("Verified test logging backend");
  }
}
