"""Summarize actual Gradle XML and Alligator diagnostic logs without external services."""
import argparse
import json
import pathlib
import re
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--xml", type=pathlib.Path)
parser.add_argument("--log", type=pathlib.Path)
args = parser.parse_args()
result = {}
if args.xml:
    totals = dict(suites=0, tests=0, failures=0, errors=0, skipped=0)
    skips = []
    for path in sorted(args.xml.glob("TEST-*.xml")):
        suite = ET.parse(path).getroot()
        totals["suites"] += 1
        for key in ("tests", "failures", "errors", "skipped"):
            totals[key] += int(suite.get(key, "0"))
        for case in suite.findall("testcase"):
            if case.find("skipped") is not None:
                skips.append(dict(suite=suite.get("name"), test=case.get("name"),
                                  reason=case.find("skipped").get("message", "")))
    totals["passed"] = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
    result.update(junit=totals, skips=skips)
if args.log:
    text = args.log.read_text(encoding="utf-8", errors="replace")
    result["gametest"] = dict(
        required_passed=re.findall(r"All (\d+) required tests passed", text),
        required_failed=re.findall(r"(\d+) required tests failed", text),
        build_success="BUILD SUCCESSFUL" in text,
        alligator=[line for line in text.splitlines() if "ALLIGATOR " in line],
    )
print(json.dumps(result, indent=2))
