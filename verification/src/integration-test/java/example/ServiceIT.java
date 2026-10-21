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

import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.transforms.Transformation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceIT {
  @Test
  void loadsServiceProviderAndIntegrationResources() throws Exception {
    try (var resource = getClass().getResourceAsStream("/integration-version.txt")) {
      assertEquals("4.3.1", new String(resource.readAllBytes()).trim());
    }
    Transformation<SourceRecord> plugin = ServiceLoader.load(Transformation.class).stream()
        .filter(provider -> provider.type() == VerifiedTransformation.class)
        .map(provider -> (Transformation<SourceRecord>) provider.get()).findFirst().orElseThrow();
    try (plugin) {
      assertEquals("\"java-17\"", plugin.apply(
          new SourceRecord(Map.of(), Map.of(), "verify", null, "hello")).value());
    }
  }
}
