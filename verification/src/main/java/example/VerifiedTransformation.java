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

import com.github.jcustenborder.kafka.connect.utils.config.Description;
import com.github.jcustenborder.kafka.connect.utils.config.Title;
import com.github.jcustenborder.kafka.connect.utils.jackson.ObjectMapperFactory;
import com.google.common.base.Joiner;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.transforms.Transformation;

import java.util.Map;

/** A small plugin whose execution requires the bundled runtime dependencies. */
@Title("Verified transformation")
@Description("Converts a value into a JSON string for parent verification.")
public class VerifiedTransformation implements Transformation<SourceRecord> {
  @Override
  public void configure(Map<String, ?> configs) {
  }

  @Override
  public ConfigDef config() {
    return new ConfigDef();
  }

  @Override
  public SourceRecord apply(SourceRecord record) {
    try {
      String value = switch (record.value().toString()) {
        case "hello" -> Joiner.on('-').join("java", Runtime.version().feature());
        default -> throw new IllegalArgumentException("Expected hello");
      };
      return record.newRecord(record.topic(), record.kafkaPartition(), null, null,
          null, ObjectMapperFactory.INSTANCE.writeValueAsString(value), record.timestamp());
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize value", e);
    }
  }

  @Override
  public void close() {
  }
}
