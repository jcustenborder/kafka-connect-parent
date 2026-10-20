# Kafka 4.3.1 / Java 17 implementation plan

**Goal:** Release-ready `com.github.jcustenborder.kafka.connect:kafka-connect-parent:4.3.1-2`.

**Approved design:** Update the parent in place and use a standalone consumer inheriting
`../pom.xml`. Preserve the assembly layout, Hub metadata, documentation, licensing,
source/Javadoc attachments, signing, and Central publishing behavior. Use native
JUnit Platform discovery, BOM alignment, provided Kafka APIs, and runtime dependency
bundles that retain service descriptors.

**Tech stack:** Maven 3, Java 17, JUnit, Mockito, Python standard-library archive/XML checks.

## Constraints

- Exactly Apache Kafka 4.3.1; Java compiler release 17 and build JDK >= 17.
- Select stable releases compatible with Java 17 and the enforced Maven floor.
- Resolve with empty user settings and Maven's standard HTTP blocker.
- No publication during verification. Inspect release configuration without deploying.
- Keep changes limited to the parent, consumer fixture, and verification documentation.

## Execution

- [x] Establish baseline: Java 17 effective POM, dependency tree, and parent `verify`.
- [ ] Inspect release metadata, companion artifacts, assembly descriptor, plugin requirements,
  and inherited/profile configuration. Record selected versions and concrete blockers.
- [ ] Add `verification/pom.xml`, a Java 17 service provider, meaningful unit and integration
  tests, service resources, and a Python verifier. Run the consumer against the old parent
  to capture downstream failures before changing the parent.
- [ ] Update `pom.xml`: versions/BOMs, Kafka scopes, native Surefire/Failsafe, Java/Maven
  enforcement, packaging exclusions, and targeted release-profile corrections.
- [ ] Run the consumer `clean verify -P integration-test` with an actual Java 17 JDK;
  check test counts, Javadoc, directory/tar/Hub contents, services, and dependency scopes.
- [ ] Repeat using a fresh Maven local repository and empty user settings; inspect effective
  POMs including `maven-central`, verify licensing, and exercise signing locally.
- [ ] Document exact commands/results and version changes in `verification/README.md`.
- [ ] Review the complete diff, commit only intended files, push, and open the requested PR.

## Acceptance checks

The verifier must fail on missing/duplicate tests, wrong Kafka versions or scopes,
Java bytecode other than release 17, missing Javadoc, missing runtime JARs, bundled
worker/test dependencies, or lost `META-INF/services` resources. Unit and integration
tests exercise Guava, connect-utils companions, Jackson, Mockito, logging, and Kafka
APIs rather than merely checking that classes exist.
