#
# Copyright © 2017 Jeremy Custenborder (jcustenborder@gmail.com)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""Build and verify the parent consumer with an actual JDK 17 and clean Maven settings."""

import argparse
import io
import json
import logging
import os
import subprocess
import tarfile
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

logger = logging.getLogger(__name__)
ROOT = Path(__file__).resolve().parent
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
SERVICE = "META-INF/services/org.apache.kafka.connect.transforms.Transformation"


def run(command: list[str]) -> None:
    logger.info("Running %s", " ".join(command))
    subprocess.run(command, cwd=ROOT.parent, check=True)


def check(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def descendants(node: dict) -> list[dict]:
    return [node] + [item for child in node.get("children", []) for item in descendants(child)]


def verify(repository: Path) -> None:
    java = str(Path(os.environ["JAVA_HOME"]) / "bin" / "java")
    run([java, "-version"])
    maven = ["mvn", "-B", "-ntp", "-s", str(ROOT / "settings.xml"),
             f"-Dmaven.repo.local={repository}"]
    help_goal = "org.apache.maven.plugins:maven-help-plugin:3.5.2:effective-pom"
    dependency_goal = "org.apache.maven.plugins:maven-dependency-plugin:3.11.0:tree"
    run(maven + ["clean", "verify", help_goal, "-Doutput=target/effective-pom.xml"])
    consumer = maven + ["-f", str(ROOT / "pom.xml"), "-P", "integration-test"]
    run(consumer + ["clean", "verify", help_goal, "-Doutput=target/effective-pom.xml"])
    run(consumer + ["com.mycila:license-maven-plugin:5.1.2:check"])
    run(consumer + [dependency_goal, "-DoutputType=json", "-DoutputFile=target/dependencies.json"])
    run(consumer + ["-P", "integration-test,maven-central", help_goal,
                    "-Doutput=target/release-effective-pom.xml"])

    target = ROOT / "target"
    effective = ET.parse(target / "effective-pom.xml").getroot()
    version = effective.findtext("m:version", namespaces=NS)
    artifact = f"parent-verification-{version}.jar"
    tree = json.loads((target / "dependencies.json").read_text())
    dependencies = descendants(tree)[1:]
    kafka = [d for d in dependencies if d["groupId"] == "org.apache.kafka"]
    check({d["artifactId"] for d in kafka} >= {"connect-api", "connect-json", "kafka-clients"},
          "Consumer must resolve Kafka APIs, JSON converter, and clients")
    check(all(d["version"] == "4.3.1" and d["scope"] == "provided" for d in kafka),
          f"Kafka versions/scopes differ: {kafka}")
    managed_kafka = [d for d in effective.findall(
        "m:dependencyManagement/m:dependencies/m:dependency", NS)
        if d.findtext("m:groupId", namespaces=NS) == "org.apache.kafka"]
    check(len(managed_kafka) == 5 and all(
        d.findtext("m:version", namespaces=NS) == "4.3.1"
        and d.findtext("m:scope", namespaces=NS) == "provided" for d in managed_kafka),
        "Every managed Kafka worker artifact must be 4.3.1/provided")

    for suite, expected in [("surefire", {"example.BaselineTest": 3}),
                            ("failsafe", {"example.ServiceIT": 1,
                                          "example.ContainerIntegrationTest": 1,
                                          "example.ITConfiguration": 1,
                                          "example.ValidationITCase": 1})]:
        reports = list((target / f"{suite}-reports").glob("TEST-*.xml"))
        results = {}
        for report in reports:
            result = ET.parse(report).getroot()
            check(all(int(result.get(key, "0")) == 0 for key in ("errors", "failures", "skipped")),
                  f"Failed/skipped tests in {report}")
            results[result.attrib["name"]] = int(result.attrib["tests"])
        check(results == expected, f"Wrong {suite} discovery/counts: {results}")
    with zipfile.ZipFile(target / artifact.replace(".jar", "-javadoc.jar")) as docs:
        check("example/VerifiedTransformation.html" in docs.namelist(), "Missing Javadoc")
    with zipfile.ZipFile(target / artifact.replace(".jar", "-sources.jar")) as sources:
        check(sources.read("example/VerifiedTransformation.java") == (
            ROOT / "src/main/java/example/VerifiedTransformation.java").read_bytes(),
            "Source attachment does not match the plugin source")
    check((target / "docs/info/source.rst").is_file(), "Missing generated documentation")

    expected_jars = {
        f"{d['artifactId']}-{d['version']}.jar" for d in dependencies
        if d["scope"] in ("compile", "runtime")
    } | {artifact}
    worker = [repository / "org/apache/kafka" / name / "4.3.1" / f"{name}-4.3.1.jar"
              for name in ("connect-api", "kafka-clients")]
    worker.append(repository / "org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar")
    directory = target / "kafka-connect-target/usr/share/kafka-connect/parent-verification"
    with tarfile.open(target / artifact.replace(".jar", ".tar.gz")) as archive:
        tar_jars = {}
        for member in archive.getmembers():
            if member.name.endswith(".jar"):
                stream = archive.extractfile(member)
                if stream is None:
                    raise AssertionError(f"Not a regular JAR file: {member.name}")
                check(Path(member.name).parent.as_posix()
                      == "usr/share/kafka-connect/parent-verification", "Wrong tar layout")
                check(Path(member.name).name not in tar_jars, "Duplicate tar JAR")
                tar_jars[Path(member.name).name] = stream.read()
    with zipfile.ZipFile(target / "components/packages" /
                         f"jcustenborder-parent-verification-{version}.zip") as archive:
        hub_names = [name for name in archive.namelist() if name.endswith(".jar")]
        hub_jars = {Path(name).name: archive.read(name) for name in hub_names}
        check(len(hub_names) == len(hub_jars), "Duplicate Hub JAR")
        check(all(Path(name).parent.as_posix()
                  == f"jcustenborder-parent-verification-{version}/lib" for name in hub_names),
              "Wrong Hub layout")
    directory_jars = {p.name: p.read_bytes() for p in directory.glob("*.jar")}
    for label, jars in [("directory", directory_jars), ("tar", tar_jars), ("hub", hub_jars)]:
        check(jars.keys() == expected_jars,
              f"{label} missing={expected_jars - jars.keys()}, extra={jars.keys() - expected_jars}")
        with zipfile.ZipFile(io.BytesIO(jars[artifact])) as plugin:
            check(plugin.read(SERVICE).decode().strip() == "example.VerifiedTransformation",
                  f"Lost service descriptor in {label}")
            bytecode = plugin.read("example/VerifiedTransformation.class")
            check(int.from_bytes(bytecode[6:8], "big") == 61, f"Wrong Java release in {label}")
        with tempfile.TemporaryDirectory(prefix=f"kafka-parent-{label}-") as bundle:
            for name, contents in jars.items():
                (Path(bundle) / name).write_bytes(contents)
            classpath = os.pathsep.join([*(str(p) for p in worker), f"{bundle}/*"])
            run([java, "--class-path", classpath, str(ROOT / "PackagedPluginCheck.java")])

    release = ET.parse(target / "release-effective-pom.xml").getroot()
    for model in (effective, release):
        for url in model.findall("m:repositories/m:repository/m:url", NS) + model.findall(
                "m:pluginRepositories/m:pluginRepository/m:url", NS):
            check((url.text or "").startswith("https://"), f"Non-HTTPS repository: {url.text}")
    plugins = release.findall("m:build/m:plugins/m:plugin", NS)
    for plugin in plugins:
        check(not any(d.findtext("m:artifactId", namespaces=NS) == "junit-platform-surefire-provider"
                      for d in plugin.findall("m:dependencies/m:dependency", NS)),
              "Obsolete JUnit Platform provider")
        if plugin.findtext("m:artifactId", namespaces=NS) == "nexus-staging-maven-plugin":
            executions = plugin.findall("m:executions/m:execution", NS)
            check(all(e.findtext("m:phase", namespaces=NS) == "none" for e in executions),
                  "Legacy Nexus deployment is still bound")
    logger.info("PASS: Java 17; 3 unit + 4 integration tests; Kafka 4.3.1 provided; "
                "Javadoc/docs; exact runtime contents and executable services in directory/tar/Hub; "
                "HTTPS repositories and release configuration")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path,
                        help="Maven local repository; defaults to a new temporary repository")
    args = parser.parse_args()
    if args.repository:
        verify(args.repository.resolve())
    else:
        with tempfile.TemporaryDirectory(prefix="kafka-parent-maven-") as repository:
            verify(Path(repository))
