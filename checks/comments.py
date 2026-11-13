import ast
import hashlib
import io
import re
import tokenize
from pathlib import Path


def lexical(text: str, language: str) -> list[str]:
    if language == "java":
        text = re.sub(r"\\u+([0-9a-fA-F]{4})", lambda m: chr(int(m[1], 16)), text)
    if language == "python":
        tokens = list(tokenize.generate_tokens(io.StringIO(text).readline))
        comments = [t.string for t in tokens if t.type == tokenize.COMMENT]
        tree = ast.parse(text)
        for node in ast.walk(tree):
            if (
                isinstance(
                    node,
                    (ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef),
                )
                and ast.get_docstring(node, clean=False) is not None
            ):
                comments.append(
                    "docstring:" + str(ast.get_docstring(node, clean=False))
                )
        return comments
    if language == "batch":
        return [
            line
            for line in text.splitlines()
            if re.match(r"(?i)^\s*(?:@?rem(?:\s|$)|::)", line)
        ]
    comments = []
    i = 0
    while i < len(text):
        if text.startswith("<!--", i):
            end = text.find("-->", i + 4)
            if end < 0:
                raise ValueError("D8: unterminated XML comment")
            comments.append(text[i : end + 3])
            i = end + 3
        elif text[i] in "'\"`":
            quote = text[i] * (3 if text.startswith(text[i] * 3, i) else 1)
            i += len(quote)
            while i < len(text) and not text.startswith(quote, i):
                i += 2 if text[i] == "\\" else 1
            i += len(quote)
        elif language == "groovy" and (
            text.startswith("$/", i)
            or (text[i] == "/" and re.search(r"[=(,:]\s*$", text[:i]))
        ):
            ender = "/$" if text.startswith("$/", i) else "/"
            i += 2 if ender == "/$" else 1
            while i < len(text) and not text.startswith(ender, i):
                i += 2 if text[i] == "\\" else 1
            i += len(ender)
        elif language in ("java", "groovy", "shader") and text.startswith("//", i):
            end = text.find("\n", i)
            end = len(text) if end < 0 else end
            comments.append(text[i:end])
            i = end
        elif language in ("java", "groovy", "shader") and text.startswith("/*", i):
            end = text.find("*/", i + 2)
            if end < 0:
                raise ValueError("D8: unterminated block comment")
            comments.append(text[i : end + 2])
            i = end + 2
        elif (
            language in ("shell", "config")
            and text[i] == "#"
            and (i == 0 or text[i - 1].isspace())
        ):
            end = text.find("\n", i)
            end = len(text) if end < 0 else end
            comments.append(text[i:end])
            i = end
        elif language in ("java", "groovy") and text.startswith("@SuppressWarnings", i):
            end = text.find(")", i)
            if end < 0:
                raise ValueError("D8: malformed suppression")
            comments.append(text[i : end + 1])
            i = end + 1
        else:
            i += 1
    return comments


def scan(path: str, text: str, entries: list[dict[str, object]]) -> list[str]:
    if "/META-INF/services/" in path:
        lines = text.splitlines()
        if not lines or any(
            re.fullmatch(r"[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)+", line) is None
            for line in lines
        ):
            return [f"D8: invalid service provider declaration {path}"]
        return []
    suffix = Path(path).suffix
    language = {
        ".java": "java",
        ".gradle": "groovy",
        ".groovy": "groovy",
        ".kts": "java",
        ".kt": "java",
        ".py": "python",
        ".sh": "shell",
        ".glsl": "shader",
        ".vsh": "shader",
        ".fsh": "shader",
        ".gsh": "shader",
        ".properties": "config",
        ".lock": "config",
        ".ps1": "config",
        ".bat": "batch",
        ".cmd": "batch",
        ".toml": "config",
        ".yml": "config",
        ".yaml": "config",
        ".xml": "xml",
        ".svg": "xml",
        ".html": "xml",
        ".lockfile": "config",
    }.get(suffix)
    if path in ("check", "verify", "gradlew", ".githooks/pre-commit"):
        language = "shell"
    if path in (".gitignore", ".gitattributes", ".ignore", ".github/CODEOWNERS"):
        language = "config"
    if language is None:
        if suffix in (".md", ".json", ".csv", ".jar") or path in (
            ".gitignore",
            ".python-version",
            "LICENSE",
            ".github/CODEOWNERS",
        ):
            return []
        return [f"D8: unknown authored language {path}"]
    directive_comments: list[str] = []
    if path.endswith("shaders.properties"):
        for line in text.splitlines():
            if re.match(
                r"^\s*#(?:if|ifdef|ifndef|else|elif|endif|define|undef|include|version|extension)\b",
                line,
            ):
                directive_comments.extend(lexical(line, "shader"))
        text = re.sub(
            r"(?m)^\s*#(?:if|ifdef|ifndef|else|elif|endif|define|undef|include|version|extension)\b[^\n]*",
            "",
            text,
        )
    allowed = [e for e in entries if e["path"] == path and e["rule"] == "D8"]
    remaining = text
    for e in allowed:
        token = str(e["token"])
        kind = e["kind"]
        legal = (
            (
                kind == "shebang"
                and re.fullmatch(
                    r"#!(?:/bin/(?:sh|bash)|/usr/bin/env (?:python3|bash))", token
                )
                is not None
                and text.startswith(token + "\n")
            )
            or (
                kind == "license"
                and hashlib.sha256(token.encode()).hexdigest()
                == "e15fc5bbdbf28dc1e99f1851a336d1cbd33abec6e2af6070f2f388ef2d04922d"
                and path == "gradlew"
            )
            or (
                kind == "directive"
                and re.fullmatch(r'@SuppressWarnings\("[A-Za-z]+"\)', token) is not None
            )
        )
        if not legal:
            return [f"D8: prose exception or invalid directive {path}"]
        if remaining.count(token) != 1:
            return [f"D8: exception site absent or repeated {path}"]
        remaining = remaining.replace(token, "", 1)
    errors = [
        f"D8: comment in {path}: {c[:60]}"
        for c in [*lexical(remaining, language), *directive_comments]
    ]
    if suffix == ".html":
        for embedded in re.findall(
            r"<(?:style|script)\b[^>]*>(.*?)</(?:style|script)>", text, re.S | re.I
        ):
            errors.extend(
                f"D8: comment in {path}: {token}" for token in lexical(embedded, "java")
            )
    return errors
