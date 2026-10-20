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
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.transforms.Transformation;

import java.util.Map;
import java.util.ServiceLoader;

/** Runs in a fresh JVM with only worker APIs and the actual distribution JARs. */
class PackagedPluginCheck {
  public static void main(String[] args) {
    if (Runtime.version().feature() != 17) {
      throw new IllegalStateException("Use an actual Java 17 JDK");
    }
    var providers = ServiceLoader.load(Transformation.class).stream()
        .filter(provider -> provider.type().getName().equals("example.VerifiedTransformation"))
        .toList();
    if (providers.size() != 1) {
      throw new IllegalStateException("Expected exactly one packaged service provider: " + providers);
    }
    try (Transformation<?> plugin = providers.get(0).get()) {
      SourceRecord input = new SourceRecord(Map.of(), Map.of(), "verify", null, "hello");
      Object result = plugin.getClass().getMethod("apply", SourceRecord.class).invoke(plugin, input);
      if (!(result instanceof SourceRecord record) || !"\"java-17\"".equals(record.value())) {
        throw new IllegalStateException("Packaged transformation returned " + result);
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot invoke packaged plugin", e);
    }
    System.out.println("Packaged service loaded and executed with worker-only APIs");
  }
}
