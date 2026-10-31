#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

export PYTHONPATH="${WORKSPACE_DIR}:${SCRIPT_DIR}:${SCRIPT_DIR}/e2e:${PYTHONPATH}"

python3 "${SCRIPT_DIR}/run_e2e_tests.py" "$@"
