#!/usr/bin/env python3

import argparse
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path


def patch_script(source_text: str, input_path: str, output_path: str) -> str:
    patched = source_text

    if re.search(r"^\s*file_path\s*=", patched, flags=re.MULTILINE):
        patched = re.sub(
            r"^\s*file_path\s*=.*$",
            f"file_path = {input_path!r}",
            patched,
            count=1,
            flags=re.MULTILINE,
        )

    if re.search(r"^\s*data_file\s*=", patched, flags=re.MULTILINE):
        patched = re.sub(
            r"^\s*data_file\s*=.*$",
            f"data_file = {input_path!r}",
            patched,
            count=1,
            flags=re.MULTILINE,
        )

    if re.search(r"^\s*csv_file_path\s*=", patched, flags=re.MULTILINE):
        patched = re.sub(
            r"^\s*csv_file_path\s*=.*$",
            f"csv_file_path = {output_path!r}",
            patched,
            count=1,
            flags=re.MULTILINE,
        )

    if re.search(r"^\s*csv_file\s*=", patched, flags=re.MULTILINE):
        patched = re.sub(
            r"^\s*csv_file\s*=.*$",
            f"csv_file = {output_path!r}",
            patched,
            count=1,
            flags=re.MULTILINE,
        )

    return patched


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Run an existing parser script against a chosen input/output path without editing the original file."
    )
    parser.add_argument("script", help="Path to the parser script in the repo")
    parser.add_argument("input", help="Input file to feed into the parser")
    parser.add_argument("output", help="Output file to write")
    args = parser.parse_args()

    script_path = Path(args.script).resolve()
    input_path = str(Path(args.input).resolve())
    output_path = str(Path(args.output).resolve())

    if not script_path.exists():
        raise FileNotFoundError(f"Script not found: {script_path}")

    if not Path(input_path).exists():
        raise FileNotFoundError(f"Input not found: {input_path}")

    os.makedirs(Path(output_path).parent, exist_ok=True)

    source_text = script_path.read_text(encoding="utf-8")
    patched = patch_script(source_text, input_path, output_path)

    with tempfile.TemporaryDirectory(prefix="cheddarflow-validate-") as temp_dir:
        temp_script = Path(temp_dir) / script_path.name
        temp_script.write_text(patched, encoding="utf-8")

        result = subprocess.run(
            [sys.executable, "-B", str(temp_script)],
            check=False,
            capture_output=True,
            text=True,
        )

        if result.stdout:
            sys.stdout.write(result.stdout)
        if result.stderr:
            sys.stderr.write(result.stderr)

        return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
