"""Tool lookup for local authentic fixtures; never assume a developer's Mac installation."""
import os
from pathlib import Path
import shutil
import unittest


def java_tool(name):
    home = os.environ.get('SHADERLOD_TEST_JDK') or os.environ.get('JAVA_HOME')
    if home:
        path = Path(home)/'bin'/name
    else:
        # A system JRE can expose java without sibling javac/javap. Prefer a
        # coherent full JDK, including Gradle's already prepared toolchains.
        compiler = shutil.which('javac')
        candidates = [Path(compiler).resolve().parent] if compiler else []
        candidates.extend(sorted((Path.home()/'.gradle/jdks').glob('*/bin')))
        binary = next((candidate for candidate in candidates
                       if all((candidate/tool).is_file() for tool in ('java','javac','javap'))), None)
        path = binary/name if binary else Path(shutil.which(name) or '/missing-jdk-tool')
    if not path.is_file():
        raise unittest.SkipTest('Full test JDK prerequisite missing: set SHADERLOD_TEST_JDK or JAVA_HOME, or put '+name+' on PATH')
    return str(path)
