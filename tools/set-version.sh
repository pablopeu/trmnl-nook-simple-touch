#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <tag>" >&2
  exit 1
fi

TAG="$1"
TAG="${TAG#v}"

if [[ ! "${TAG}" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-rc\.([0-9]+))?$ ]]; then
  echo "error: unsupported tag format: ${TAG}" >&2
  echo "expected: v<major>.<minor>.<patch> or v<major>.<minor>.<patch>-rc.<n>" >&2
  exit 1
fi

MAJOR="${BASH_REMATCH[1]}"
MINOR="${BASH_REMATCH[2]}"
PATCH="${BASH_REMATCH[3]}"
RC_NUM="${BASH_REMATCH[5]:-}"

BASE_CODE=$((MAJOR * 10000 + MINOR * 100 + PATCH))
if [[ -n "${RC_NUM}" ]]; then
  VERSION_CODE=$((BASE_CODE * 100 + RC_NUM))
  VERSION_NAME="${MAJOR}.${MINOR}.${PATCH}-rc.${RC_NUM}"
else
  VERSION_CODE=$((BASE_CODE * 100 + 99))
  VERSION_NAME="${MAJOR}.${MINOR}.${PATCH}"
fi

MANIFEST="AndroidManifest.xml"
if [[ ! -f "${MANIFEST}" ]]; then
  echo "error: ${MANIFEST} not found" >&2
  exit 1
fi

# Guard: ensure new versionCode is strictly higher than the previous release tag
PREV_TAG="$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || true)"
if [[ -n "${PREV_TAG}" ]]; then
  PREV_CODE="$(git show "${PREV_TAG}:${MANIFEST}" 2>/dev/null \
    | sed -n 's/.*android:versionCode="\([0-9]*\)".*/\1/p' || true)"
  if [[ -n "${PREV_CODE}" && "${VERSION_CODE}" -le "${PREV_CODE}" ]]; then
    echo "error: new versionCode ${VERSION_CODE} is not higher than previous tag ${PREV_TAG} (versionCode=${PREV_CODE})" >&2
    echo "hint: this usually means the tag version was not bumped correctly" >&2
    exit 1
  fi
  if [[ -n "${PREV_CODE}" ]]; then
    echo "versionCode check passed: ${VERSION_CODE} > ${PREV_CODE} (${PREV_TAG})"
  fi
fi

python3 - <<PY
import re
from pathlib import Path

path = Path("${MANIFEST}")
data = path.read_text(encoding="utf-8")
data = re.sub(r'android:versionCode="[^"]+"', 'android:versionCode="${VERSION_CODE}"', data)
data = re.sub(r'android:versionName="[^"]+"', 'android:versionName="${VERSION_NAME}"', data)
path.write_text(data, encoding="utf-8")
PY

echo "Set AndroidManifest.xml versionCode=${VERSION_CODE} versionName=${VERSION_NAME}"
