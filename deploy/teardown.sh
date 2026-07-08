#!/usr/bin/env bash
# deploy/teardown.sh — delete all AWS resources created by setup.sh.

set -euo pipefail

STATE_FILE="$(dirname "$0")/.state"
[[ -f "${STATE_FILE}" ]] || { echo "ERROR: ${STATE_FILE} not found — has setup.sh been run?"; exit 1; }

# shellcheck disable=SC1090
source "${STATE_FILE}"

log() { echo "[$(date '+%H:%M:%S')] $*"; }

log "Tearing down ${APP_NAME} in ${AWS_REGION}..."


# ── ECS service ───────────────────────────────────────────────────────────────
log "Scaling ECS service to 0 and deleting..."
aws ecs update-service \
  --cluster "${ECS_CLUSTER}" \
  --service "${ECS_SERVICE}" \
  --desired-count 0 \
  --region "${AWS_REGION}" 2>/dev/null || true
aws ecs delete-service \
  --cluster "${ECS_CLUSTER}" \
  --service "${ECS_SERVICE}" \
  --force \
  --region "${AWS_REGION}" 2>/dev/null || true


# ── ECS task definitions ──────────────────────────────────────────────────────
log "Deregistering task definitions (family: ${TASK_DEF_FAMILY})..."
TASK_DEF_ARNS="$(aws ecs list-task-definitions \
  --family-prefix "${TASK_DEF_FAMILY}" \
  --query 'taskDefinitionArns[*]' --output text \
  --region "${AWS_REGION}" 2>/dev/null | tr '\t' ' ')"
for arn in ${TASK_DEF_ARNS}; do
  aws ecs deregister-task-definition \
    --task-definition "${arn}" \
    --region "${AWS_REGION}" 2>/dev/null || true
done


# ── ALB listener, ALB, target group ──────────────────────────────────────────
log "Deleting ALB listener..."
aws elbv2 delete-listener \
  --listener-arn "${LISTENER_ARN}" \
  --region "${AWS_REGION}" 2>/dev/null || true

log "Deleting ALB..."
aws elbv2 delete-load-balancer \
  --load-balancer-arn "${ALB_ARN}" \
  --region "${AWS_REGION}" 2>/dev/null || true

log "Waiting for ALB deletion..."
aws elbv2 wait load-balancers-deleted \
  --load-balancer-arns "${ALB_ARN}" \
  --region "${AWS_REGION}" 2>/dev/null || true

log "Deleting target group..."
aws elbv2 delete-target-group \
  --target-group-arn "${TG_ARN}" \
  --region "${AWS_REGION}" 2>/dev/null || true


# ── ECS cluster ───────────────────────────────────────────────────────────────
log "Deleting ECS cluster ${ECS_CLUSTER}..."
aws ecs delete-cluster \
  --cluster "${ECS_CLUSTER}" \
  --region "${AWS_REGION}" 2>/dev/null || true


# ── RDS ───────────────────────────────────────────────────────────────────────
# story-vault shares book-service-pg-db — do NOT delete the instance or its SG.
# Only revoke the ingress rule that allowed this service's ECS tasks to connect.
log "RDS is shared (book-service-pg-db) — skipping instance deletion."
log "Revoking story-vault ECS access to shared RDS SG ${RDS_SG}..."
aws ec2 revoke-security-group-ingress \
  --group-id "${RDS_SG}" --protocol tcp --port 5432 \
  --source-group "${ECS_SG}" --region "${AWS_REGION}" 2>/dev/null || true


# ── CloudWatch log group ──────────────────────────────────────────────────────
log "Deleting CloudWatch log group ${LOG_GROUP}..."
aws logs delete-log-group \
  --log-group-name "${LOG_GROUP}" \
  --region "${AWS_REGION}" 2>/dev/null || true


# ── Security groups ────────────────────────────────────────────────────────────
log "Revoking ALB->ECS rule..."
aws ec2 revoke-security-group-ingress \
  --group-id "${ECS_SG}" --protocol tcp --port 8080 \
  --source-group "${ALB_SG}" --region "${AWS_REGION}" 2>/dev/null || true

# RDS_SG is the shared book-service-pg RDS SG — do not delete it.
log "Deleting service-owned security groups (ALB and ECS only)..."
for sg_id in "${ECS_SG}" "${ALB_SG}"; do
  aws ec2 delete-security-group \
    --group-id "${sg_id}" \
    --region "${AWS_REGION}" 2>/dev/null || log "  SG ${sg_id} not deleted (check console)"
done


# ── IAM ───────────────────────────────────────────────────────────────────────
log "Deleting IAM role..."
aws iam detach-role-policy \
  --role-name "${ECS_EXEC_ROLE_NAME}" \
  --policy-arn "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy" \
  2>/dev/null || true
aws iam delete-role \
  --role-name "${ECS_EXEC_ROLE_NAME}" \
  2>/dev/null || true


# ── ECR ───────────────────────────────────────────────────────────────────────
log "Deleting ECR repository ${APP_NAME}..."
aws ecr delete-repository \
  --repository-name "${APP_NAME}" \
  --force \
  --region "${AWS_REGION}" 2>/dev/null || true


# ── State file ─────────────────────────────────────────────────────────────────
rm -f "${STATE_FILE}"

log ""
log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log " Teardown complete. All ${APP_NAME} AWS resources deleted."
log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
