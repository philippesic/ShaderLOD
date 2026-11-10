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
    "D4",
    "D5",
    "D6a",
    "D6b",
    "D6c",
    "D6d",
    "D7",
    "D8",
    "D9",
    "D10",
    "S1",
    "S2",
    "S3",
    "S4",
    "S5",
    "S6",
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
            if e["kind"] != "license":
                errors.append("D7: only required licenses are permanent")
        elif date.fromisoformat(expiry) < date.today():
            errors.append("D7: expired exception")
        if e["kind"] not in ("shebang", "license", "directive", "adapter"):
            errors.append("D7: unknown exception kind")
    return errors


def discover(tree: dict[str, bytes]) -> list[str]:
    found = []
    for path, data in tree.items():
        if re.search(r"(PackSpec|Workload|ScopeSpec)\.java$", path):
            match = re.search(r'\bID\s*=\s*"([\w.-]+)"', data.decode())
            if not match:
                raise ValueError("D2: descriptor ID missing")
            found.append(match[1])
    if len(found) != len(set(found)):
        raise ValueError("D2: duplicate provider ID")
    return sorted(found)


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
        if isinstance(node, ast.ExceptHandler) and (
            node.type is None or name(node.type) in ("Exception", "BaseException")
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
            ) and path != "checks/bootstrap.py":
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
                writing = target.rsplit(".", 1)[-1] not in (
                    "open",
                    "dumps",
                    "replace",
                ) or (
                    target.endswith("open")
                    and any(
                        isinstance(a, ast.Constant)
                        and isinstance(a.value, str)
                        and any(c in a.value for c in ("w", "a", "x", "+"))
                        for a in [*node.args, *(k.value for k in node.keywords)]
                    )
                )
                if writing and path not in (
                    "checks/bootstrap.py",
                    "checks/driver.py",
                    "checks/tooltests.py",
                    "checks/publisher.py",
                    "python/publisher.py",
                ):
                    errors.append("D3: result write outside publisher")
            if (
                target.startswith("subprocess.")
                or target.endswith("ArgumentParser")
                or target.endswith("parse_args")
                or target.endswith("parse_known_args")
            ) and path not in (
                "checks/bootstrap.py",
                "checks/driver.py",
                "checks/tooltests.py",
                "checks/policy.py",
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
            isinstance(node, ast.Subscript)
            and name(node.value) == "os.environ"
            and path != "checks/bootstrap.py"
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
            if not path.endswith(".jar"):
                errors.extend(scan(path, content.decode(), entries))
    if family == "comments" or family == "exceptions":
        return errors
    fixture_manifest = json.loads(tree.get("checks/fixtures.json", b"{}"))
    ids = discover(tree)
    names: dict[str, str] = {}
    for path, content in tree.items():
        if path.endswith(".jar"):
            if path != "gradle/wrapper/gradle-wrapper.jar":
                errors.append("S4: tracked binary")
            continue
        try:
            text = content.decode()
        except UnicodeError:
            errors.append("S4: binary content")
            continue
        parts = Path(path).parts
        fixture = fixture_manifest.get(path, {})
        authored_csv = (
            path.endswith(".csv")
            and fixture.get("schema")
            and fixture.get("purpose")
            and fixture.get("sha256") == hashlib.sha256(content).hexdigest()
            and len(content) < 16384
        )
        if any(
            p.lower()
            in (
                "run",
                "runs",
                "logs",
                ".gradle",
                ".git",
                "scratch",
                "plans",
                "world",
                "region",
            )
            for p in parts
        ) or re.search(r"(\.log|\.zip|\.mca|\.png|PLAN\.md)$", path, re.I):
            errors.append("S4: raw output or plan path")
        if path.endswith(".csv") and not authored_csv:
            errors.append("S4: CSV lacks authored schema")
        if (
            re.search(
                r'"(frames|operationId|rawFrameIntervals|captureVersion)"\s*:', text
            )
            or text.startswith(("PK\x03\x04", "\x1f\x8b"))
            or (
                "# " in text[:10]
                and re.search(r"implementation plan|porting plan", text, re.I)
            )
        ):
            errors.append("S4: renamed raw output or plan")
        if Path(path).name in ("AGENTS.md", "CLAUDE.md") and "/" in path:
            errors.append("S3: nested instructions")
        if path == "AGENTS.md" and len(content) >= 2048:
            errors.append("S3: instruction budget")
        if path == "CLAUDE.md" and text != "@AGENTS.md\n":
            errors.append("S3: CLAUDE mirror")
        if re.search(
            r"(^|/)(STATUS|STATUS_AUDIT|STATUS_HISTORY|EXPERIMENTS)(\.|$)", path, re.I
        ):
            errors.append("S5: competing findings index")
        if re.search(
            r"gpu.{0,12}(lock|lease|semaphore)|(lock|lease|semaphore).{0,12}gpu",
            path,
            re.I,
        ) or (
            path.endswith((".py", ".java", ".sh"))
            and re.search(r"(flock|Semaphore|FileLock|lockf)\s*\(", text)
            and re.search(r"gpu", text, re.I)
        ):
            errors.append("S6: GPU allocation lock")
        if path.endswith(".py"):
            errors.extend(python_rules(path, text, entries))
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
            if "/engine/" in path and any(
                re.search(r'"' + re.escape(i) + r'"', text) for i in ids
            ):
                errors.append("D2: pack-name branch in engine")
            if (
                path.startswith("src/")
                and path.endswith(".java")
                and "/test/" not in path
            ):
                match = re.search(r"\b(class|record|enum|interface)\s+(\w+)", text)
                if match:
                    names[match[2]] = path
        if path.startswith("docs/decisions/"):
            if "Correction: resolved" in text:
                rule = re.search(r"Rule: ([\w-]+)", text)
                test = re.search(r"Test: ([\w-]+)", text)
                mapped = (
                    rule is not None
                    and test is not None
                    and rule[1] in RULES
                    and test[1] in RULES
                )
                if not mapped and not re.search(r"Uncheckable reason: \S.+", text):
                    errors.append("D10: resolved correction without check")
        if path == ".github/workflows/check.yml" and re.findall(
            r"^\s*- run: (.+)$", text, re.M
        ) != ["./check"]:
            errors.append("S1: CI command differs")
        if path == ".githooks/pre-commit" and text != "#!/bin/sh\n./check\n":
            errors.append("S1: hook command differs")
        if path == "FINDINGS.md":
            rows = [line for line in text.splitlines() if re.match(r"\| F\d+", line)]
            if len(rows) != 7 or any(
                not any(
                    level in row
                    for level in (
                        "source-inspected",
                        "cpu-synthetic",
                        "cpu-integration",
                        "live-functional",
                        "live-performance-qualified",
                    )
                )
                or not re.search(r"(sha256:[a-f0-9]{64}|/blob/[a-f0-9]{40}/)", row)
                for row in rows
            ):
                errors.append("S5: finding lacks frozen evidence or scope")
    roots = json.loads(tree.get("checks/entrypoints.json", b"[]"))
    if any(path not in tree for path in roots):
        errors.append("D4: declared root missing")
    reachable = set(roots)
    changed = True
    while changed:
        changed = False
        for name, path in names.items():
            if path in reachable:
                for other, target in names.items():
                    if (
                        re.search(r"\b" + re.escape(other) + r"\b", tree[path].decode())
                        and target not in reachable
                    ):
                        reachable.add(target)
                        changed = True
    for path in names.values():
        if path not in reachable and not re.search(
            r"(PackSpec|Workload|ScopeSpec)\.java$", path
        ):
            errors.append("D4: orphan class " + path)
    if "docs/SYSTEM_MAP.md" in tree:
        for line in tree["docs/SYSTEM_MAP.md"].decode().splitlines():
            if line.startswith("| `"):
                columns = [c.strip() for c in line.strip("|").split("|")]
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
    if ".github/CODEOWNERS" in tree:
        owners = tree[".github/CODEOWNERS"].decode()
        if not all(
            p + " @philippesic" in owners
            for p in ("/checks/", "/dependencies/", "/.github/", "/build.gradle")
        ):
            errors.append("D7: protected config routing missing")
    return sorted(set(errors))
