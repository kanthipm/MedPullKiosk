#!/usr/bin/env bash
#
# Tear the Recovery Copilot stack down. The data bucket is created with
# DeletionPolicy: Retain, so the demo database survives unless you pass
# --delete-data.
#
#   ./infra/destroy.sh                # delete the stack, keep the S3 data
#   ./infra/destroy.sh --delete-data  # delete everything, including the database
#
set -euo pipefail

STACK_NAME="${STACK_NAME:-recovery-copilot}"
REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-us-east-1}}"
GROQ_PARAM="${GROQ_PARAM:-/recovery-copilot/groq-api-key}"
ORIGIN_SECRET_PARAM="${ORIGIN_SECRET_PARAM:-/recovery-copilot/origin-verify-secret}"

DELETE_DATA=false
[ "${1:-}" = "--delete-data" ] && DELETE_DATA=true

log()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
aws_() { aws --region "$REGION" "$@"; }

ACCOUNT_ID="$(aws_ sts get-caller-identity --query Account --output text)"
ARTIFACT_BUCKET="${ARTIFACT_BUCKET:-${STACK_NAME}-artifacts-${ACCOUNT_ID}-${REGION}}"

DATA_BUCKET="$(aws_ cloudformation describe-stacks --stack-name "$STACK_NAME" \
  --query "Stacks[0].Outputs[?OutputKey=='DataBucketName'].OutputValue" \
  --output text 2>/dev/null || true)"

log "About to delete stack '$STACK_NAME' in $REGION"
[ -n "$DATA_BUCKET" ] && info "data bucket: $DATA_BUCKET $($DELETE_DATA && echo '(WILL BE DELETED)' || echo '(retained)')"
info "artifact bucket: $ARTIFACT_BUCKET (will be emptied and deleted)"
printf '    Type the stack name to confirm: '
read -r CONFIRM
[ "$CONFIRM" = "$STACK_NAME" ] || { echo "aborted"; exit 1; }

# CloudFormation cannot delete a non-empty bucket; the data bucket is retained
# by policy so only the artifact bucket needs emptying up front.
log "Emptying the artifact bucket"
aws_ s3 rm "s3://$ARTIFACT_BUCKET" --recursive --only-show-errors 2>/dev/null || true

log "Deleting the stack (CloudFront takes several minutes to disable)"
aws_ cloudformation delete-stack --stack-name "$STACK_NAME"
aws_ cloudformation wait stack-delete-complete --stack-name "$STACK_NAME"

log "Cleaning up"
aws_ s3api delete-bucket --bucket "$ARTIFACT_BUCKET" 2>/dev/null || true
aws_ ssm delete-parameter --name "$ORIGIN_SECRET_PARAM" 2>/dev/null || true
aws_ ssm delete-parameter --name "$GROQ_PARAM" 2>/dev/null || true
info "removed the SSM parameters"

if $DELETE_DATA && [ -n "$DATA_BUCKET" ]; then
  log "Deleting the data bucket"
  # Versioning is on, so every version and delete marker has to go explicitly.
  aws_ s3api delete-objects --bucket "$DATA_BUCKET" --delete "$(aws_ s3api list-object-versions \
    --bucket "$DATA_BUCKET" --output json \
    --query '{Objects: Versions[].{Key:Key,VersionId:VersionId}}')" >/dev/null 2>&1 || true
  aws_ s3api delete-objects --bucket "$DATA_BUCKET" --delete "$(aws_ s3api list-object-versions \
    --bucket "$DATA_BUCKET" --output json \
    --query '{Objects: DeleteMarkers[].{Key:Key,VersionId:VersionId}}')" >/dev/null 2>&1 || true
  aws_ s3api delete-bucket --bucket "$DATA_BUCKET" 2>/dev/null || true
  info "deleted $DATA_BUCKET"
elif [ -n "$DATA_BUCKET" ]; then
  info "kept $DATA_BUCKET — delete it by hand, or rerun with --delete-data"
fi

log "Done — nothing left billing"
