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

import com.github.jcustenborder.kafka.connect.utils.config.ConfigUtils;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ITConfiguration {
  @Test
  void parsesWorkerAddressUsingCompanionAndKafkaConfigApis() {
    ConfigDef definition = new ConfigDef().define("address", ConfigDef.Type.STRING,
        "localhost:9092", ConfigDef.Importance.HIGH, "Worker address");
    AbstractConfig config = new AbstractConfig(definition, Map.of());
    assertEquals(9092, ConfigUtils.hostAndPort(config, "address").getPort());
  }
}
