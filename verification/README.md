# Kafka 4.3.1 / Java 17 consumer verification

After release, consumers should adopt:

```xml
<parent>
    <groupId>com.github.jcustenborder.kafka.connect</groupId>
    <artifactId>kafka-connect-parent</artifactId>
    <version>4.3.1-2</version>
</parent>
```

`4.3.1-1` already exists in Maven Central and as a remote Git tag. This change
uses the next version, `4.3.1-2`, with exactly Kafka **4.3.1**, not 4.3.2 or 4.4.0.
Builds require **JDK 17+ and Maven 3.9.2+**. The verification fixture deliberately
requires an actual JDK 17, rather than testing only `--release 17` on a newer JDK.

## Run

From the repository root, with Python 3.9+ and Maven on `PATH`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17) # macOS; select your JDK 17 elsewhere
python3 verification/verify.py
```

By default the script creates a fresh temporary Maven local repository. To retain
downloaded artifacts between runs, supply `--repository /absolute/path/to/repository`.
All Maven commands use the empty `verification/settings.xml`; no user mirrors,
credentials, or relaxed HTTP blocking are used. Global settings are not modified.
The verification was also run with a stock Maven 3.9.2 distribution and its default
global settings, including `maven-default-http-blocker`.

The script runs these commands with `-B -ntp -s verification/settings.xml` and
`-Dmaven.repo.local=<verification repository>`:

```bash
mvn clean verify org.apache.maven.plugins:maven-help-plugin:3.5.2:effective-pom \
  -Doutput=target/effective-pom.xml
mvn -f verification/pom.xml -P integration-test clean verify \
  org.apache.maven.plugins:maven-help-plugin:3.5.2:effective-pom \
  -Doutput=target/effective-pom.xml
mvn -f verification/pom.xml -P integration-test com.mycila:license-maven-plugin:5.1.2:check
mvn -f verification/pom.xml -P integration-test \
  org.apache.maven.plugins:maven-dependency-plugin:3.11.0:tree \
  -DoutputType=json -DoutputFile=target/dependencies.json
mvn -f verification/pom.xml -P integration-test,maven-central \
  org.apache.maven.plugins:maven-help-plugin:3.5.2:effective-pom \
  -Doutput=target/release-effective-pom.xml
```

Outputs are under `target/` and `verification/target/`. The script checks:

- Java 17 compilation, class-file major version 61, and execution on Java 17.
- Exactly three successful unit tests and four successful integration tests, with
  zero failures/errors/skips. `IT*`, `*IT`, `*ITCase`, and `*IntegrationTest` are discovered.
- Real Guava/Mockito behavior, Kafka Struct JSON round trips, connect-utils schema,
  parser and test-data helpers, a functioning Logback backend, and Testcontainers
  Kafka configuration/JUnit extension compatibility. No Docker daemon is required;
  this fixture does not claim to launch a broker or a Connect worker.
- Integration resource filtering and service-provider discovery.
- Generated class Javadoc, attached source/Javadoc JARs, and generated documentation.
- Kafka API/JSON/client resolution at 4.3.1 in `provided` scope; all five managed
  Kafka artifacts at 4.3.1/provided.
- Exact runtime JAR contents in the directory assembly, tar.gz assembly, and Hub
  ZIP. Required Guava and Jackson JARs are included; worker APIs, SLF4J, native Kafka
  libraries, test libraries, and Logback are excluded.
- `META-INF/services/org.apache.kafka.connect.transforms.Transformation` survives
  every distribution. A separate Java 17 JVM loads and executes the packaged
  plugin from each bundle with only worker-provided API/client/SLF4J JARs added.
- HTTPS dependency/plugin repositories and native JUnit Platform configuration.
- The effective Central publishing profile, including suppression of legacy Nexus
  deployment executions by the Central extension.

## Dependency versions

Versions were checked against Maven Central release metadata on 2026-10-20.

| Dependency | Previous | Selected |
| --- | --- | --- |
| Kafka connect-api / connect-json | 4.3.1 | 4.3.1, provided |
| Kafka clients / transforms / runtime | transitive or consumer-defined | managed 4.3.1, provided |
| connect-utils and jackson/parser/testing/testing-data companions | `[0.8.0,0.8.2000)` | pinned 0.8.0 (latest published) |
| Guava | 31.1-jre | 33.7.2-jre |
| JUnit BOM / Jupiter / Platform | 5.9.1 / Platform 1.9.1 | 6.1.3 |
| Mockito BOM / core | 4.8.0 | 5.24.0 |
| Testcontainers BOM / JUnit / Kafka | 2.0.5 | 2.0.5 (latest published) |
| Jackson family | transitive 2.21.2 | BOM 2.22.4 (annotations 2.22, as defined by BOM) |
| JSpecify | competing transitive 1.0.0 / 1.0.1 | managed 1.0.1 |
| SLF4J API | transitive 1.7.36 | managed 1.7.36, provided |
| Logback classic / core | 1.1.8 | aligned 1.2.13, test only |
| Style assemblies / Checkstyle config / license template | `[1.1.0,1.1.1000)` | pinned 1.1.0.0.4 (latest published) |

The JSpecify pin is necessary: the consumer tree initially selected JUnit's
1.0.0 with scope promoted to compile, while the runtime-only assembly graph
selected Guava's 1.0.1. The content verifier exposed that difference; management
now makes the test/runtime graphs consistent.

## Build plugin versions

| Plugin | Previous | Selected |
| --- | --- | --- |
| Compiler | 3.13.0 | 3.16.0, release 17 |
| Enforcer | 1.4.1 | 3.6.3; Java `[17,)`, Maven `[3.9.2,)` |
| Surefire / Failsafe | 2.22.2; profile Failsafe 2.19 | 3.6.0 throughout |
| Javadoc | 2.9.1 | 3.12.0 |
| Checkstyle Maven plugin / engine | 3.1.2 / 6.18 | 3.6.0 / 12.3.1 |
| Resources | implicit 3.3.1 with Maven 3.9.16 | 3.5.0 |
| JAR | 3.2.0 | 3.5.1 |
| Assembly | 3.3.0 | 3.8.0, including Hub's internal assembly |
| Build Helper (integration-test profile) | 1.9.1 | 3.6.2 |
| Confluent Hub | 0.12.0 | 0.12.0 (latest published) |
| Kafka Connect documentation | 0.1.2 | 0.1.3 |
| Mycila License | 3.0 | 5.1.2 |
| Source (inherited) | 3.0.1 | 3.4.0 |
| Clean / Install / Deploy | implicit 3.2.0 / 3.1.2 / 3.1.2 with Maven 3.9.16 | 3.5.0 / 3.2.0 / 3.2.0 |
| GPG (maven-central profile) | 1.6 | 3.2.8 |
| Central Publishing | 0.9.0 | 0.12.0 |
| Nexus Staging (legacy, extensions=false) | 1.6.7 | 1.7.0 |

Plugin descriptors establish Maven 3.6.3 as sufficient for the selected Apache
plugins, but Central Publishing 0.12.0 requires **3.9.2**, so that is the enforced
floor. Maven 4-only beta plugins are not stable Maven 3 upgrades.

The old JUnit Platform Surefire provider is removed, including from the integration
profile. Failsafe's goals now use their proper lifecycle phases instead of both
running during `integration-test`; the duplicated profile execution is removed.
Existing unit-test exclusions, all standard Failsafe filename conventions, and
the parent's additional `*IntegrationTest` convention remain.

## Compatibility limits and retained behavior

- **SLF4J / Logback:** Kafka 4.3.1 provides SLF4J 1.7.36. Logback 1.3+ needs SLF4J
  2.x. A probe with Logback 1.6.5 fails compilation because
  `org.slf4j.spi.LoggingEventAware` is absent from the worker API. Keep the latest
  1.2.x backend, 1.2.13; upgrading the logging major requires changing the worker
  logging baseline, not silently compiling plugins against SLF4J 2.x.
- **Jackson 3:** connect-utils-jackson 0.8.0 uses `com.fasterxml.jackson` types;
  Jackson 3's `tools.jackson` APIs are not a drop-in replacement. The latest
  compatible 2.x BOM is used and Kafka JSON/Struct round trips are verified.
- **Checkstyle:** 12.3.1 is the latest Java 17-compatible engine. 13.x/14.x require
  Java 21 (confirmed from published POM compiler releases).
- **Hub internal JAR plugin:** Hub 0.12.0 rebuilds the artifact into `components/`
  without a classifier. Substituting JAR 3.5.1 fails with “You have to use a
  classifier to attach supplemental artifacts ... instead of replacing them.”
  Its internal JAR 2.6 is retained; the normal lifecycle uses JAR 3.5.1.
- **Hub internal copy/rename:** Hub bytecode hard-codes copy-rename-maven-plugin
  1.0. Although 1.0.1 exists, there is no configurable version parameter. Changing
  it requires an upstream Hub release. Maven's plugin-validation warnings remain
  visible; they are not suppressed.
- **Companions/style/Hub:** no newer published stable releases exist for the
  selected connect-utils, style modules, Testcontainers, or Hub plugin. Old
  companion transitives such as Reflections/Javassist remain upstream-owned;
  Maven 3.9.16 reports an existing Javassist effective-model warning.
- **Documentation plugin:** 0.1.3 reads raw consumer POM metadata. The fixture
  explicitly supplies version, SCM, and issue-management fields so inherited
  metadata does not cause missing-value template failures. Its generated source
  template still mentions JDK 8; the verified build requirements above apply.
- **Licensing:** keep legacy top-level header/properties/excludes configuration
  so consumers can still override it. Explicit Java `JAVADOC_STYLE` preserves
  compatibility with the shared Checkstyle header rule; integration resources
  receive the same exclusion as ordinary test resources. Mycila's deprecation
  warnings for its supported legacy configuration remain visible.
- **Publishing:** retain signing execution ID, key/passphrase-server settings,
  `central` server ID, `autoPublish=true`, `waitUntil=published`, and Hub ownership
  metadata. Both the old and new effective release profiles show the Central
  extension removing inherited Nexus deploy/release executions. No replacement
  release workflow, publication, or deployment is performed by verification.

## Verification record

Final full-suite commands (from the repository root):

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
PATH="/var/folders/7j/9448c83x0zv3mh6q7bqndv380000gn/T/opencode/apache-maven-3.9.2/bin:$PATH" \
python3 verification/verify.py \
  --repository /var/folders/7j/9448c83x0zv3mh6q7bqndv380000gn/T/opencode/kafka-parent-pr-maven

JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
python3 verification/verify.py \
  --repository /var/folders/7j/9448c83x0zv3mh6q7bqndv380000gn/T/opencode/kafka-parent-pr-maven

uvx ruff check --no-cache verification/verify.py
git diff --check
```

The first command used a new local repository and Maven 3.9.2; the second used
Maven 3.9.16. Both ended with the verifier's `PASS`; lint and diff checks passed.

- **Baseline:** unchanged 4.3.1-1 passed parent `help:effective-pom dependency:tree
  verify` on Temurin 17.0.1 / Maven 3.9.16, but ran no tests and skipped Javadoc.
  The consumer's first `clean verify -P integration-test` failed in Checkstyle
  while parsing its Java switch expression, demonstrating the downstream gap.
- **Full checks:** passed on actual Temurin **17.0.1**, macOS x86_64, with Maven
  **3.9.16** and stock **3.9.2**. Fresh local-repository resolution, 3 unit + 4
  integration tests, zero skips/failures/errors, generated Javadoc/docs,
  licensing, and executable directory/tar/Hub bundles are verified.
- **Negative enforcement:** actual Maven 3.9.1 is rejected by `requireMavenVersion`;
  actual JDK 11.0.2 is rejected by `requireJavaVersion`, using
  `mvn -B -ntp -s verification/settings.xml
  org.apache.maven.plugins:maven-enforcer-plugin:3.6.3:enforce@enforce-versions`.
- **Local signing:** `verify -P integration-test,maven-central` succeeds with a
  disposable GPG key and isolated `GNUPGHOME`; detached artifact signatures are
  checked with `gpg --verify`. Neither `deploy` nor `publish` is invoked.

For local signing, after generating a disposable signing key in a short-path
temporary `GNUPGHOME`, the exact Maven invocation is:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
GNUPGHOME=/var/folders/7j/9448c83x0zv3mh6q7bqndv380000gn/T/opencode/kgpg \
/var/folders/7j/9448c83x0zv3mh6q7bqndv380000gn/T/opencode/apache-maven-3.9.2/bin/mvn \
  -B -ntp -s verification/settings.xml -f verification/pom.xml \
  -Dmaven.repo.local=/var/folders/7j/9448c83x0zv3mh6q7bqndv380000gn/T/opencode/kafka-parent-minimum-maven \
  -P integration-test,maven-central \
  -Dgpg.keyname=7445E3CBE027C7C15B990330F7E1A8324FC36C0B verify
```

No release has been published. The coordinates above are for adoption after release.
