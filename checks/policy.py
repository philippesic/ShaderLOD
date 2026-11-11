import ast
from datetime import date
import hashlib
import json
from pathlib import Path
import re
import subprocess
from checks.comments import lexical, scan

RULES = {
    "D1",
    "D2",
    "D3",
    "D5",
    "D6a",
    "D6b",
    "D6c",
    "D6d",
    "D7",
    "D8",
    "D9",
    "S1",
    "S2",
    "S3",
    "S4",
    "S5",
    "T-JFMT",
    "T-GFMT",
    "T-PFMT",
    "T-JTYPE",
    "T-EP",
    "T-CS",
    "T-RUFF",
    "T-MYPY",
    "T-JUNIT",
    "T-PYTEST",
    "T-DEP",
    "T-ARCH",
}
TASK_SECTIONS = ["Goal", "Authorized", "Done when", "State", "Next"]


def files(root: Path) -> dict[str, bytes]:
    names = subprocess.check_output(
        ["git", "-C", str(root), "ls-files", "-co", "--exclude-standard", "-z"]
    ).split(b"\0")
    return {
        name.decode(): (root / name.decode()).read_bytes()
        for name in sorted(set(names))
        if name and (root / name.decode()).is_file()
    }


def exceptions(entries: list[dict[str, object]]) -> list[str]:
    errors = []
    for e in entries:
        required = {
            "rule",
            "path",
            "kind",
            "token",
            "sha256",
            "reason",
            "review",
            "owner",
            "decision",
            "expires",
        }
        if (
            set(e) != required
            or not e.get("reason")
            or e.get("review") is not True
            or not e.get("owner")
        ):
            errors.append("D7: incomplete exception")
            continue
        if (
            any(x in str(e["path"]) + str(e["token"]) for x in ("*", "?"))
            or e["rule"] not in RULES
        ):
            errors.append("D7: wildcard or unknown rule")
        if hashlib.sha256(str(e["token"]).encode()).hexdigest() != e["sha256"]:
            errors.append("D7: exception digest mismatch")
        expiry = str(e["expires"])
        if expiry == "permanent":
            if e["kind"] not in ("license", "shebang") and not (
                e["kind"] == "directive"
                and str(e["path"]).startswith(
                    "src/main/java/com/pp/shaderlod/integration/mixin/"
                )
                and e["token"]
                in (
                    '@SuppressWarnings("UnusedMethod")',
                    '@SuppressWarnings("UnusedVariable")',
                )
            ):
                errors.append(
                    "D7: only structural shebangs and required licenses are permanent"
                )
        elif date.fromisoformat(expiry) < date.today():
            errors.append("D7: expired exception")
        if e["kind"] not in ("shebang", "license", "directive", "adapter"):
            errors.append("D7: unknown exception kind")
    return errors


def python_rules(path: str, text: str, entries: list[dict[str, object]]) -> list[str]:
    tree = ast.parse(text)
    errors = []
    aliases: dict[str, str] = {}
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for item in node.names:
                aliases[item.asname or item.name] = item.name
        if isinstance(node, ast.ImportFrom):
            for item in node.names:
                aliases[item.asname or item.name] = (
                    (node.module or "") + "." + item.name
                )

    def name(node: ast.AST) -> str:
        if isinstance(node, ast.Name):
            return aliases.get(node.id, node.id)
        if isinstance(node, ast.Attribute):
            return name(node.value) + "." + node.attr
        return ""

    for node in ast.walk(tree):
        if (
            isinstance(node, ast.Assign)
            and isinstance(node.value, ast.Call)
            and name(node.value.func) in ("io.StringIO", "io.BytesIO")
        ):
            for target_node in node.targets:
                if isinstance(target_node, ast.Name):
                    aliases[target_node.id] = name(node.value.func)
    for node in ast.walk(tree):
        if isinstance(node, ast.ExceptHandler) and (
            node.type is None
            or any(
                name(part) in ("Exception", "BaseException")
                for part in ast.walk(node.type)
            )
        ):
            token = ast.get_source_segment(text, node) or ""
            permitted = any(
                e["path"] == path and e["rule"] == "D6a" and e["token"] == token
                for e in entries
            )
            explicit = any(
                isinstance(n, ast.Raise)
                or (isinstance(n, ast.Return) and "unavailable" in ast.unparse(n))
                for n in ast.walk(node)
            )
            if not permitted or not explicit:
                errors.append("D6a: broad catch without exact failure adapter")
        if isinstance(node, ast.Call):
            target = name(node.func)
            if (
                (
                    target
                    in (
                        "os.getenv",
                        "os.environ.get",
                        "os.environ.copy",
                        "os.uname",
                        "platform.machine",
                        "platform.system",
                    )
                    or target.startswith("os.environ.")
                )
                and path not in ("checks/bootstrap.py", "shaderlod_host/config.py")
                and not path.startswith("tests/")
            ):
                errors.append("D6b: settings read outside config owner")
            if target.rsplit(".", 1)[-1] in (
                "write_text",
                "write_bytes",
                "dump",
                "dumps",
                "open",
                "write",
                "rename",
                "replace",
                "unlink",
                "touch",
            ):
                method = target.rsplit(".", 1)[-1]
                writing = method not in ("open", "dumps", "replace")
                if method == "open":
                    index = (
                        1
                        if target in ("open", "builtins.open", "io.open", "codecs.open")
                        else 0
                    )
                    mode = next(
                        (
                            keyword.value
                            for keyword in node.keywords
                            if keyword.arg == "mode"
                        ),
                        node.args[index] if len(node.args) > index else None,
                    )
                    writing = mode is not None and not (
                        isinstance(mode, ast.Constant)
                        and mode.value in ("r", "rb", "rt")
                    )
                if (
                    method == "replace"
                    and isinstance(node.func, ast.Attribute)
                    and isinstance(node.func.value, ast.Call)
                ):
                    writing = name(node.func.value.func) in ("pathlib.Path", "Path")
                if target.startswith(("io.StringIO", "io.BytesIO")):
                    writing = False
                if (
                    writing
                    and not path.startswith(("tests/",))
                    and path
                    not in (
                        "checks/bootstrap.py",
                        "checks/driver.py",
                        "checks/tooltests.py",
                        "checks/publisher.py",
                        "checks/selftest.py",
                        "shaderlod_host/storage.py",
                        "shaderlod_host/runtime.py",
                        "shaderlod_host/runtime_live.py",
                    )
                ):
                    errors.append("D3: result write outside publisher")
            if (
                (
                    target
                    in ("os.system", "os.popen", "os.posix_spawn", "os.posix_spawnp")
                    or target.startswith(("os.exec", "os.spawn"))
                    or target.startswith("subprocess.")
                    or target.endswith("ArgumentParser")
                    or target.endswith("parse_args")
                    or target.endswith("parse_known_args")
                )
                and not path.startswith(("tests/",))
                and path
                not in (
                    "checks/bootstrap.py",
                    "checks/driver.py",
                    "checks/tooltests.py",
                    "checks/policy.py",
                    "shaderlod_host/runtime.py",
                    "shaderlod_host/runtime_live.py",
                    "shaderlod_host/__main__.py",
                    "shaderlod_host/worker.py",
                )
            ):
                errors.append("D6d: undeclared launcher or option parser")
            if (
                re.search(
                    r"gl(Finish|FenceSync|BeginQuery|EndQuery|GetQuery|QueryCounter|ClientWaitSync|WaitSync)",
                    target,
                )
                and "/measurement/" not in path
            ):
                errors.append("D6c: GPU call outside measurement adapter")
        if (
            isinstance(node, (ast.Attribute, ast.Name))
            and name(node) == "os.environ"
            and path not in ("checks/bootstrap.py", "shaderlod_host/config.py")
            and not path.startswith("tests/")
        ):
            errors.append("D6b: settings read outside config owner")
        if (
            isinstance(node, ast.Subscript)
            and name(node.value) == "os.environ"
            and path not in ("checks/bootstrap.py", "shaderlod_host/config.py")
            and not path.startswith("tests/")
        ):
            errors.append("D6b: settings read outside config owner")
    return errors


def validate(
    tree: dict[str, bytes], entries: list[dict[str, object]], family: str = "all"
) -> list[str]:
    errors = []
    if family in ("all", "exceptions"):
        errors.extend(exceptions(entries))
    if family in ("all", "comments"):
        for path, content in tree.items():
            if not path.endswith((".jar", ".png")):
                try:
                    authored_text = content.decode()
                except UnicodeError:
                    errors.append("S4: unclassified binary content in " + path)
                    continue
                errors.extend(scan(path, authored_text, entries))
    if family == "comments" or family == "exceptions":
        return errors
    fixture_manifest = json.loads(tree.get("checks/fixtures.json", b"{}"))
    for path, content in tree.items():
        if path.endswith(".png"):
            fixture = fixture_manifest.get(path, {})
            if (
                fixture.get("sha256") != hashlib.sha256(content).hexdigest()
                or not fixture.get("purpose")
                or len(content) > 262144
            ):
                errors.append("S4: binary asset requires digest and purpose")
            continue
        if path.endswith(".jar"):
            if path != "gradle/wrapper/gradle-wrapper.jar":
                errors.append("S4: tracked binary")
            continue
        try:
            text = content.decode()
        except UnicodeError:
            errors.append("S4: binary content")
            continue
        fixture = fixture_manifest.get(path, {})
        authored_csv = (
            path.endswith(".csv")
            and fixture.get("schema")
            and fixture.get("purpose")
            and fixture.get("sha256") == hashlib.sha256(content).hexdigest()
            and len(content) < 16384
        )
        generated_roots = ("build/", "runs/", "logs/", ".gradle/", ".git/", "scratch/")
        if path.startswith(generated_roots) or path.endswith((".log", ".zip", ".mca")):
            errors.append("S4: generated output belongs outside authored tree")
        if len(content) > 2 * 1024 * 1024:
            errors.append("S4: authored file exceeds size limit")
        if path.endswith(".csv") and not authored_csv:
            errors.append("S4: CSV lacks authored schema")
        if Path(path).name in ("AGENTS.md", "CLAUDE.md") and "/" in path:
            errors.append("S3: nested instructions")
        if path == "CLAUDE.md" and text != "@AGENTS.md\n":
            errors.append("S3: CLAUDE mirror")
        if re.search(
            r"(^|/)(STATUS|STATUS_AUDIT|STATUS_HISTORY|EXPERIMENTS)(\.|$)", path, re.I
        ):
            errors.append(
                "S5: status/history document "
                + path
                + "; put open work in tasks/NAME.md and closed results in FINDINGS.md"
            )
        if path.endswith(".py"):
            errors.extend(
                message + " in " + path for message in python_rules(path, text, entries)
            )
        if path.endswith(".sh") and re.search(
            r"(?:^|[\s;&|])(?:exec\s+)?(?:java|minecraft|gradlew|\./gradlew)(?:\s|$)",
            text,
        ):
            errors.append("D6d: undeclared shell launcher")
        if path.endswith((".java", ".gradle")):
            if any(
                t.startswith("@SuppressWarnings") for t in lexical(text, "java")
            ) and not any(
                e["path"] == path and e["kind"] == "directive" for e in entries
            ):
                errors.append("D7: unlisted suppression")
            for caught in re.finditer(
                r"catch\s*\(\s*(?:Exception|Throwable|RuntimeException)\b[^)]*\)\s*\{[^}]*\}",
                text,
            ):
                token = caught.group()
                listed = any(
                    e["path"] == path and e["rule"] == "D6a" and e["token"] == token
                    for e in entries
                )
                if not listed or not re.search(
                    r'return\s+"unavailable"|throw\s+', token
                ):
                    errors.append("D6a: broad Java catch")
            if re.search(
                r"(exclude|onlyIf|enabled\s*=\s*false|ignoreFailures\s*=\s*true|disableAllChecks|suppressLintsFor)",
                text,
            ) and path.endswith(".gradle"):
                errors.append("S1: disabled check or excluded source")
        if path == ".githooks/pre-commit" and text != "#!/bin/sh\n./check --full\n":
            errors.append("S1: pre-commit hook must run exactly ./check --full")
        if path.startswith("tasks/") and path != "tasks/README.md":
            headings = re.findall(r"^## (.+)$", text, re.M)
            if not path.endswith(".md") or "/" in path[len("tasks/") :]:
                errors.append("S5: tasks/ holds only tasks/NAME.md files, in " + path)
            elif not text.startswith("# ") or headings != TASK_SECTIONS:
                errors.append(
                    "S5: task file "
                    + path
                    + " needs a '# Title' and exactly these sections in order: "
                    + ", ".join("## " + h for h in TASK_SECTIONS)
                )
        if path == "FINDINGS.md":
            rows = [line for line in text.splitlines() if re.match(r"\| F\d+", line)]
            if not rows:
                errors.append("S5: findings map is empty")
            for row in rows:
                target = re.search(r"\[Result\]\((results/[^)]+/result\.md)\)", row)
                result = tree.get(target[1], b"").decode() if target else ""
                if (
                    not result
                    or not re.search(r"sha256:[a-f0-9]{64}", result)
                    or not any(
                        level in result
                        for level in (
                            "source-inspected",
                            "cpu-synthetic",
                            "cpu-integration",
                            "live-functional",
                            "live-performance-qualified",
                        )
                    )
                ):
                    errors.append(
                        "S5: finding lacks linked result, frozen evidence or scope"
                    )
    if "docs/architecture.md" in tree:
        section = ""
        for line in tree["docs/architecture.md"].decode().splitlines():
            if line.startswith("## "):
                section = line[3:].strip()
            if section == "Entry points" and line.startswith("| `"):
                columns = [c.strip() for c in line.strip("|").split("|")]
                if ":" not in columns[0]:
                    errors.append(
                        "D9: entry point row needs `path:symbol` in docs/architecture.md"
                    )
                    continue
                path, symbol = columns[0].strip("`").split(":", 1)
                testpath = columns[-1].strip("`")
                exists = path in tree and testpath in tree
                if exists and symbol and path.endswith(".py"):
                    exists = symbol in {
                        node.name
                        for node in ast.walk(ast.parse(tree[path].decode()))
                        if isinstance(
                            node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)
                        )
                    }
                elif exists and symbol:
                    exists = symbol in tree[path].decode()
                if not exists:
                    errors.append("D9: mapped entry or test missing")
    if "checks/tests.json" in tree:
        lanes = json.loads(tree["checks/tests.json"])
        if set(lanes) != {"java", "python"} or any(
            not names for names in lanes.values()
        ):
            errors.append("S2: required test registration missing")
    if "checks/self-tests.json" in tree:
        manifest = json.loads(tree["checks/self-tests.json"])
        if set(manifest) != RULES or any(
            not cases or any(not c.get("bad") or not c.get("control") for c in cases)
            for cases in manifest.values()
        ):
            errors.append("S2: missing specimen pair")
    return sorted(set(errors))
