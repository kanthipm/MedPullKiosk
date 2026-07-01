#!/usr/bin/env bash
#
# run_overnight.sh — build the MedPull ortho demo fully autonomously, overnight.
#
# WHAT IT DOES
#   1. Launches Claude Code headless with permissions bypassed on BUILD_PROMPT.md.
#   2. Loops: while the build isn't finished (no DEMO_COMPLETE.md sentinel), it
#      resumes the same session and tells it to keep going.
#   3. CREDIT/USAGE WATCHDOG: if a run stops because you hit a usage / credit /
#      rate limit (or a transient network/overload error), it does NOT give up —
#      it polls every 5 minutes with a tiny probe to see whether your usage has
#      revived, and the moment it has, it resumes the build where it left off.
#      A limit pause does NOT consume the build-iteration safety budget.
#   4. Stops only when the sentinel appears (success) or a safety cap is hit.
#
# USAGE
#   1. Put BUILD_PROMPT.md in this directory.
#   2. Create ./reference/ and drop in:
#        - micro_lab_my_body_phone_app.html            (RA visual-style file)
#        - MedPull-Ortho-Metrics-and-Task-Library.md   (metrics + tasks doc)
#        - (optionally) the analytics doc + product spec
#   3. chmod +x run_overnight.sh && ./run_overnight.sh
#      (or run detached so it survives logout:  nohup ./run_overnight.sh &  )
#
# NOTE: Claude Code CLI flags vary by version. If `-c` / `--output-format` differ
#       in yours, run `claude --help` and adjust the invocations in step() and
#       credits_available() below.
#
set -uo pipefail

# ------------------------- config (tweak as you like) -------------------------
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

PROMPT_FILE="BUILD_PROMPT.md"
SENTINEL="DEMO_COMPLETE.md"
LOG_DIR="$PROJECT_DIR/.build-logs"
MAX_ITERS=40            # safety cap on *build* resume loops (limit pauses don't count)
POLL_SECONDS=300        # 5 minutes — how often to check if usage has revived
MAX_WAIT_HOURS=14       # give up waiting for revival after this long per stall
PROBE_MODEL=""          # optional cheap model for the availability probe, e.g.
                        #   PROBE_MODEL="claude-haiku-4-5-20251001"  (keeps probe cost minimal)
# -----------------------------------------------------------------------------

mkdir -p "$LOG_DIR"
[[ -f "$PROMPT_FILE" ]] || { echo "ERROR: $PROMPT_FILE not found in $PROJECT_DIR" >&2; exit 1; }

# portable timeout wrapper (Linux `timeout`, macOS `gtimeout`, else none)
if command -v timeout  >/dev/null 2>&1; then TIMEOUT="timeout"
elif command -v gtimeout >/dev/null 2>&1; then TIMEOUT="gtimeout"
else TIMEOUT=""; fi

COMMON_FLAGS=(--dangerously-skip-permissions --verbose --output-format text)

# Patterns that mean "usage/credit/rate limited" vs. "transient/overload".
LIMIT_RE='usage limit|rate limit|rate_limit|429|too many requests|credit balance|out of credits|insufficient (credit|quota|balance)|limit reached|quota exceeded|reset at'
TRANSIENT_RE='timed out|timeout|etimedout|econnreset|network error|connection error|502|503|529|overloaded'

call_no=0
build_iters=0
started=false

RESUME_MSG="Continue the autonomous build described in BUILD_PROMPT.md. Re-read \
PLAN.md and your Definition-of-Done checklist. Verify every surface by launching a \
headless browser and viewing screenshots, fix anything broken or unpolished, and \
DO NOT STOP until all Definition-of-Done items pass and you have written \
DEMO_COMPLETE.md. Do not ask questions."

log(){ echo "[$(date '+%F %T')] $*"; }

# --- tiny probe: is Claude usable right now? returns 0 = yes, 1 = still limited ---
credits_available(){
  local out rc
  if [[ -n "$TIMEOUT" ]]; then
    out="$("$TIMEOUT" 90 claude -p "Reply with exactly: OK" ${PROBE_MODEL:+--model "$PROBE_MODEL"} \
           --dangerously-skip-permissions --output-format text 2>&1)"; rc=$?
  else
    out="$(claude -p "Reply with exactly: OK" ${PROBE_MODEL:+--model "$PROBE_MODEL"} \
           --dangerously-skip-permissions --output-format text 2>&1)"; rc=$?
  fi
  echo "$out" | grep -qiE "$LIMIT_RE" && return 1
  (( rc != 0 )) && return 1
  echo "$out" | grep -qi "OK" && return 0
  return 1
}

# --- poll every POLL_SECONDS until usage revives (or MAX_WAIT_HOURS elapses) ---
wait_for_revival(){
  local waited=0 max=$(( MAX_WAIT_HOURS * 3600 ))
  log "PAUSE  Claude stopped on a usage/credit limit. Polling every $((POLL_SECONDS/60)) min for revival..." \
      | tee -a "$LOG_DIR/watchdog.log"
  while (( waited < max )); do
    if credits_available; then
      log "REVIVED  Usage is back - resuming the build." | tee -a "$LOG_DIR/watchdog.log"
      return 0
    fi
    log "...still limited; sleeping ${POLL_SECONDS}s (waited $((waited/60)) min so far)" \
        | tee -a "$LOG_DIR/watchdog.log"
    sleep "$POLL_SECONDS"
    waited=$(( waited + POLL_SECONDS ))
  done
  log "GIVEUP  Still limited after ${MAX_WAIT_HOURS}h - giving up on this stall." | tee -a "$LOG_DIR/watchdog.log"
  return 1
}

# --- run one claude invocation, tee to a unique log, classify the outcome ---
# usage: step <extra claude args...>   returns: 0 ok/done | 10 limit | 11 transient | N other
step(){
  printf -v cn '%04d' "$call_no"
  local logf="$LOG_DIR/call-$cn.log"
  echo "      log -> $logf"
  claude "${COMMON_FLAGS[@]}" "$@" 2>&1 | tee "$logf"
  local rc=${PIPESTATUS[0]}
  call_no=$(( call_no + 1 ))
  [[ -f "$SENTINEL" ]] && return 0
  grep -qiE "$LIMIT_RE"     "$logf" && return 10
  grep -qiE "$TRANSIENT_RE" "$logf" && return 11
  return "$rc"
}

# ------------------------------- main loop -----------------------------------
echo "=== MedPull ortho demo - autonomous overnight build ==="
echo "Project : $PROJECT_DIR"
echo "Started : $(date)"
echo "Logs    : $LOG_DIR   (watchdog: $LOG_DIR/watchdog.log)"
echo "Sentinel: $SENTINEL   |  poll every $((POLL_SECONDS/60)) min on limit  |  probe timeout: ${TIMEOUT:-none}"
echo

while :; do
  [[ -f "$SENTINEL" ]] && break
  if (( build_iters > MAX_ITERS )); then
    log "safety cap of $MAX_ITERS build iterations reached without sentinel."; break
  fi

  if ! $started; then
    log "initial build (call #$call_no)..."
    step -p "$(cat "$PROMPT_FILE")"; status=$?
    started=true
  else
    log "resume build - iteration $build_iters (call #$call_no)..."
    step -c -p "$RESUME_MSG"; status=$?
  fi

  case $status in
    10)  # usage / credit / rate limit -> wait for revival, resume WITHOUT burning budget
         if wait_for_revival; then continue; else break; fi ;;
    11)  # transient network / overload -> same treatment, cheap to retry
         log "transient error; will verify availability then retry."
         if wait_for_revival; then continue; else break; fi ;;
  esac

  build_iters=$(( build_iters + 1 ))
done

echo
if [[ -f "$SENTINEL" ]]; then
  echo "=== DONE ==="
  echo "Sentinel found: $SENTINEL"
  echo "Finished: $(date)"
  echo "Open the demo:  open index.html   (or deploy the folder to Vercel)"
  echo
  echo "----- $SENTINEL -----"
  cat "$SENTINEL"
else
  echo "=== STOPPED without completing ==="
  echo "Either the build cap or the ${MAX_WAIT_HOURS}h revival wait was exhausted."
  echo "Check $LOG_DIR (and watchdog.log) for where it stalled, then re-run to resume."
  exit 2
fi
