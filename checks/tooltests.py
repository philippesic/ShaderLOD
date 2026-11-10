from pathlib import Path
import json
import shutil
import subprocess
import tempfile


def run(command: list[str], cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=240,
    )


def demand(
    result: subprocess.CompletedProcess[str], ok: bool, marker: str, rule: str
) -> None:
    if (result.returncode == 0) != ok or (not ok and marker not in result.stdout):
        raise RuntimeError(
            f"S2: {rule} tool crash or unexpected result\n{result.stdout}"
        )


def python_pairs(root: Path) -> dict[str, dict[str, object]]:
    import sys

    results: dict[str, dict[str, object]] = {}
    data = {
        "T-PFMT": (
            ["ruff", "format", "--check"],
            "def value( ):\n return 1\n",
            "def value():\n    return 1\n",
            "would be reformatted",
        ),
        "T-RUFF": (
            ["ruff", "check", "--select", "F401"],
            "import os\n",
            "import os\nvalue = os.name\n",
            "F401",
        ),
        "T-MYPY": (
            ["mypy", "--strict"],
            'def value() -> int:\n    return "bad"\n',
            "def value() -> int:\n    return 1\n",
            "[return-value]",
        ),
        "T-PYTEST": (
            ["pytest", "-q"],
            "def test_value() -> None:\n    assert False\n",
            "def test_value() -> None:\n    assert True\n",
            "1 failed",
        ),
    }
    with tempfile.TemporaryDirectory(prefix="shaderlod-python-specimens-") as temp:
        work = Path(temp)
        for rule, (args, bad, good, marker) in data.items():
            path = work / "test_specimen.py"
            for ok, content in ((True, good), (False, bad)):
                path.write_text(content)
                result = run([sys.executable, "-m", *args, str(path)], work)
                demand(result, ok, marker, rule)
            results[rule] = {
                "bad": rule,
                "control": "PASS",
                "bad_count": 1,
                "control_count": 1,
            }
    return results


def java_pairs(root: Path) -> dict[str, dict[str, object]]:
    results: dict[str, dict[str, object]] = {}
    with tempfile.TemporaryDirectory(prefix="shaderlod-gradle-specimens-") as temp:
        work = Path(temp)
        for name in (
            "gradlew",
            "gradle",
            "settings.gradle",
            "gradle.properties",
            "checks/checkstyle.xml",
        ):
            source, target = root / name, work / name
            target.parent.mkdir(parents=True, exist_ok=True)
            if source.is_dir():
                shutil.copytree(source, target)
            else:
                shutil.copyfile(source, target)
        build = (root / "build.gradle").read_text().split("def python =")[0]
        build = build.replace(
            "systemProperty 'foundation.root', projectDir.absolutePath",
            "systemProperty 'foundation.root', projectDir.absolutePath",
        )
        (work / "build.gradle").write_text(build)
        source = work / "src/test/java/probe/ToolsTest.java"
        source.parent.mkdir(parents=True)
        good = """package probe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ToolsTest {
  @Test
  void readable() {
    assertEquals(1, new ClassFileImporter().importClasses(ToolsTest.class).size());
  }
}
"""
        source.write_text(good)
        prefix = [
            str(root / "gradlew"),
            "--no-daemon",
            "-p",
            str(work),
            "--console=plain",
            "--write-locks",
        ]
        formatted = run([*prefix, "spotlessApply"], work)
        demand(formatted, True, "", "tool setup")
        good = source.read_text()
        cleanbuild = (work / "build.gradle").read_text()
        control = run(
            [
                *prefix,
                "spotlessCheck",
                "compileTestJava",
                "checkstyleTest",
                "test",
                "buildHealth",
            ],
            work,
        )
        demand(control, True, "", "Java controls")
        (root / "build/reports/foundation/java-tools-control.txt").write_text(
            control.stdout
        )
        cases = {
            "T-JFMT": (
                "spotlessJavaCheck",
                good.replace("  @Test", " @Test"),
                "format violations",
            ),
            "T-GFMT": ("spotlessGroovyGradleCheck", good, "format violations"),
            "T-JTYPE": (
                "compileTestJava",
                good.replace("void readable()", "int readable()"),
                "missing return statement",
            ),
            "T-EP": (
                "compileTestJava",
                good.replace(
                    "    assertEquals", "    int unreadValue = 1;\n    assertEquals"
                ),
                "[UnusedVariable]",
            ),
            "T-CS": (
                "checkstyleTest",
                good.replace(
                    "import org.junit", "import java.util.List;\nimport org.junit"
                ),
                "UnusedImports",
            ),
            "T-JUNIT": (
                "test",
                good.replace("assertEquals(1,", "assertEquals(2,"),
                "test completed, 1 failed",
            ),
            "T-DEP": ("buildHealth", good, "fatal dependency violations"),
        }
        for rule, (task, bad, marker) in cases.items():
            source.write_text(bad)
            (work / "build.gradle").write_text(cleanbuild)
            if rule == "T-GFMT":
                (work / "build.gradle").write_text(
                    cleanbuild
                    + '\ntasks.register("misformatted") {\n doLast {\nprintln("data")\n }\n}\n'
                )
            if rule == "T-DEP":
                (work / "build.gradle").write_text(
                    cleanbuild
                    + '\ndependencies { implementation "com.tngtech.archunit:archunit:1.5.1" }\n'
                )
            result = run([*prefix, task], work)
            demand(result, False, marker, rule)
            (root / f"build/reports/foundation/{rule}.txt").write_text(result.stdout)
            results[rule] = {
                "bad": rule,
                "control": "PASS",
                "bad_count": 1,
                "control_count": 1,
            }
    return results


def graph_pair(root: Path) -> dict[str, object]:
    from checks.policy import files

    with tempfile.TemporaryDirectory(prefix="shaderlod-graph-specimen-") as temp:
        work = Path(temp)
        for name, data in files(root).items():
            target = work / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        init = work / "disable.gradle"
        init.write_text(
            "gradle.projectsEvaluated { rootProject.tasks.named('repoPolicy').get().enabled = false }\n"
        )
        prefix = [
            str(root / "gradlew"),
            "--no-daemon",
            "-p",
            str(work),
            "--dry-run",
            "check",
        ]
        demand(run(prefix, work), True, "", "S1")
        result = run([*prefix, "--init-script", str(init)], work)
        demand(
            result, False, "S1: required validator disabled or absent: repoPolicy", "S1"
        )
        (root / "build/reports/foundation/S1-graph.txt").write_text(result.stdout)
    return {"bad": "S1", "control": "PASS", "bad_count": 1, "control_count": 1}


def main(root: Path) -> dict[str, dict[str, object]]:
    results = python_pairs(root)
    results["S1"] = graph_pair(root)
    results.update(java_pairs(root))
    (root / "build/reports/foundation/tool-self-tests.json").write_text(
        json.dumps(results, indent=2) + "\n"
    )
    return results
