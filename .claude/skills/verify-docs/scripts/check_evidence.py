#!/usr/bin/env python3
"""Checks the evidence behind a /verify-docs report, mechanically.

An agent verifying documentation can be wrong about the code in the same way
the documentation was. So every verdict it reaches has to cite evidence - a
verbatim quote and the file and lines it came from - and this script confirms
each quote really is there. A verdict whose evidence cannot be found is
downgraded to "unverifiable": the claim has not been checked, whatever the
report says.

usage: check_evidence.py REPORT.json

REPORT.json:
{
  "document": "docs/ci.md",
  "claims": [
    {
      "id": 1,
      "line": 12,
      "claim": "ingest-build.yml calls ingest-validation.yml",
      "verdict": "confirmed" | "contradicted" | "unverifiable",
      "evidence": [{"file": ".github/workflows/ingest-build.yml", "lines": [24, 26], "quote": "uses: ./.github/workflows/ingest-validation.yml"}],
      "note": "optional - what is wrong, for a contradicted claim"
    }
  ]
}

Paths are relative to the repository root. Whitespace is compared loosely, so
a quote may be reflowed, but every word must be present, in order, within the
cited lines. Exits 1 if any confirmed or contradicted claim lacks evidence
that checks out.
"""

import json
import re
import sys
from pathlib import Path

VERDICTS = {"confirmed", "contradicted", "unverifiable"}


def repository_root() -> Path:
    here = Path(__file__).resolve()
    for parent in here.parents:
        if (parent / ".git").exists():
            return parent
    sys.exit("check_evidence.py: not inside a git repository")


def normalise(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def check_evidence(root: Path, evidence: dict) -> str | None:
    """Returns None if the quote is where it says, or the reason it is not."""
    path = root / evidence.get("file", "")
    lines = evidence.get("lines")
    quote = evidence.get("quote", "")

    if not evidence.get("file") or not path.is_file():
        return f"no such file: {evidence.get('file')!r}"
    if not (isinstance(lines, list) and len(lines) == 2 and 1 <= lines[0] <= lines[1]):
        return f"lines must be [first, last], got {lines!r}"
    if not normalise(quote):
        return "empty quote"

    text = path.read_text(errors="replace").splitlines()
    if lines[1] > len(text):
        return f"{evidence['file']} has {len(text)} lines, cited up to {lines[1]}"

    excerpt = normalise("\n".join(text[lines[0] - 1 : lines[1]]))
    if normalise(quote) not in excerpt:
        return f"quote not found in {evidence['file']}:{lines[0]}-{lines[1]}"

    return None


def main() -> int:
    if len(sys.argv) != 2:
        sys.exit(__doc__)

    root = repository_root()
    report = json.loads(Path(sys.argv[1]).read_text())
    claims = report.get("claims", [])
    failures = 0
    tally = {verdict: 0 for verdict in VERDICTS}

    for claim in claims:
        verdict = claim.get("verdict")
        label = f"claim {claim.get('id')} (line {claim.get('line')})"

        if verdict not in VERDICTS:
            print(f"FAIL  {label}: verdict must be one of {sorted(VERDICTS)}, got {verdict!r}")
            failures += 1
            continue

        problems = [p for e in claim.get("evidence", []) if (p := check_evidence(root, e))]

        if verdict != "unverifiable" and not claim.get("evidence"):
            problems.append(f"a {verdict} claim needs evidence")

        if problems:
            failures += 1
            print(f"FAIL  {label}: {verdict}, but - {'; '.join(problems)}")
            print(f"      {claim.get('claim')}")
            tally["unverifiable"] += 1
        else:
            tally[verdict] += 1

    print(
        f"\n{report.get('document')}: {len(claims)} claims - "
        f"{tally['confirmed']} confirmed, {tally['contradicted']} contradicted, "
        f"{tally['unverifiable']} unverifiable"
        + (f" ({failures} downgraded for missing evidence)" if failures else "")
    )
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
