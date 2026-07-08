#!/usr/bin/env bash
# deploy/setup.sh — provision ECR + ECS Fargate + ALB + RDS for story-vault-service.
#
# Usage:
#   export AWS_REGION=us-east-2
#   export DB_PASSWORD=<password>
#   bash deploy/setup.sh

set -euo pipefail

# ── Configuration ──────────────────────────────────────────────────────────────
APP_NAME="story-vault"
AWS_REGION="${AWS_REGION:-us-east-1}"
DB_PASSWORD="${DB_PASSWORD:?ERROR: DB_PASSWORD environment variable is required}"
DB_USERNAME="storyvault"
DB_NAME="storyvaultdb"
RDS_INSTANCE_CLASS="db.t3.micro"
RDS_STORAGE_GB=20
ECS_CPU="512"
ECS_MEMORY="1024"
# ──────────────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
STATE_FILE="${SCRIPT_DIR}/.state"

log() { echo "[$(date '+%H:%M:%S')] $*"; }
die() { echo "ERROR: $*" >&2; exit 1; }

command -v aws    >/dev/null 2>&1 || die "aws CLI not found"
command -v docker >/dev/null 2>&1 || die "docker not found"
docker info >/dev/null 2>&1       || die "Docker daemon is not running"

JWT_SECRET="$(openssl rand -base64 32 | tr -d '\n')"

log "Fetching AWS account ID..."
AWS_ACCOUNT_ID="$(aws sts get-caller-identity \
  --query 'Account' --output text --region "${AWS_REGION}")"
log "Account: ${AWS_ACCOUNT_ID}  Region: ${AWS_REGION}"

ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
ECR_REPO="${APP_NAME}"
ECR_URI="${ECR_REGISTRY}/${ECR_REPO}"
LOG_GROUP="/ecs/${APP_NAME}"
ECS_CLUSTER="${APP_NAME}-cluster"
ECS_EXEC_ROLE_NAME="${APP_NAME}-ecs-execution-role"
DB_SUBNET_GROUP="${APP_NAME}-db-subnet-group"


# ── 1. ECR repository ─────────────────────────────────────────────────────────
log "[1/11] ECR repository..."
aws ecr create-repository \
  --repository-name "${ECR_REPO}" \
  --region "${AWS_REGION}" \
  --image-scanning-configuration scanOnPush=true \
  2>/dev/null || log "  ECR repository already exists"


# ── 2. Docker build + push ────────────────────────────────────────────────────
log "[2/11] Authenticating Docker with ECR..."
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

log "[2/11] Building Docker image (Maven build)..."
docker build -t "${APP_NAME}:latest" "${SCRIPT_DIR}/.."
docker tag  "${APP_NAME}:latest" "${ECR_URI}:latest"
docker push "${ECR_URI}:latest"
log "  Pushed: ${ECR_URI}:latest"


# ── 3. IAM — ECS task execution role ─────────────────────────────────────────
log "[3/11] IAM role..."
aws iam create-service-linked-role \
  --aws-service-name ecs.amazonaws.com 2>/dev/null || true

ECS_EXEC_TRUST='{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ecs-tasks.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
ECS_EXEC_ROLE_ARN="$(aws iam create-role \
  --role-name "${ECS_EXEC_ROLE_NAME}" \
  --assume-role-policy-document "${ECS_EXEC_TRUST}" \
  --query 'Role.Arn' --output text 2>/dev/null \
  || aws iam get-role \
       --role-name "${ECS_EXEC_ROLE_NAME}" \
       --query 'Role.Arn' --output text)"
aws iam attach-role-policy \
  --role-name "${ECS_EXEC_ROLE_NAME}" \
  --policy-arn "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy" \
  2>/dev/null || true
log "  ECS execution role: ${ECS_EXEC_ROLE_ARN}"
log "  IAM propagation delay (15 s)..."
sleep 15


# ── 4. VPC and subnets ────────────────────────────────────────────────────────
log "[4/11] VPC and subnets..."
VPC_ID="$(aws ec2 describe-vpcs \
  --filters "Name=isDefault,Values=true" \
  --query 'Vpcs[0].VpcId' --output text --region "${AWS_REGION}")"
[[ "${VPC_ID}" == "None" || -z "${VPC_ID}" ]] \
  && die "No default VPC found"

SUBNET_RAW="$(aws ec2 describe-subnets \
  --filters "Name=vpc-id,Values=${VPC_ID}" \
  --query 'Subnets[*].SubnetId' --output text --region "${AWS_REGION}" | tr '\t' ' ')"
SUBNET_1="$(echo "${SUBNET_RAW}" | awk '{print $1}')"
SUBNET_2="$(echo "${SUBNET_RAW}" | awk '{print $2}')"
[[ -z "${SUBNET_1}" || -z "${SUBNET_2}" ]] \
  && die "Need at least 2 subnets in VPC ${VPC_ID}"
log "  VPC: ${VPC_ID}  Subnets: ${SUBNET_1} ${SUBNET_2}"


# ── 5. Security groups ────────────────────────────────────────────────────────
log "[5/11] Security groups..."

_sg() {
  local name="$1" desc="$2"
  local id
  id="$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=${name}" "Name=vpc-id,Values=${VPC_ID}" \
    --query 'SecurityGroups[0].GroupId' --output text --region "${AWS_REGION}")"
  if [[ "${id}" == "None" || -z "${id}" ]]; then
    aws ec2 create-security-group \
      --group-name "${name}" --description "${desc}" \
      --vpc-id "${VPC_ID}" \
      --query 'GroupId' --output text --region "${AWS_REGION}"
  else
    echo "${id}"
  fi
}

ALB_SG="$(_sg "${APP_NAME}-alb-sg" "Story Vault ALB public HTTP")"
ECS_SG="$(_sg "${APP_NAME}-ecs-sg" "Story Vault ECS Fargate tasks")"
RDS_SG="$(_sg "${APP_NAME}-rds-sg" "Story Vault RDS PostgreSQL")"

aws ec2 authorize-security-group-ingress \
  --group-id "${ALB_SG}" --protocol tcp --port 80 --cidr 0.0.0.0/0 \
  --region "${AWS_REGION}" 2>/dev/null || true
aws ec2 authorize-security-group-ingress \
  --group-id "${ECS_SG}" --protocol tcp --port 8080 \
  --source-group "${ALB_SG}" --region "${AWS_REGION}" 2>/dev/null || true
aws ec2 authorize-security-group-ingress \
  --group-id "${RDS_SG}" --protocol tcp --port 5432 \
  --source-group "${ECS_SG}" --region "${AWS_REGION}" 2>/dev/null || true

log "  ALB=${ALB_SG}  ECS=${ECS_SG}  RDS=${RDS_SG}"


# ── 6. RDS — reuse book-service-pg-db (account limit: 2 RDS instances) ───────
# The account free plan caps RDS at 2 instances. storyvaultdb was created on
# book-service-pg-db via a one-off psql ECS task. We just look up the endpoint.
log "[6/11] RDS — reusing shared book-service-pg-db instance..."
# Allow story-vault ECS SG to reach book-service-pg's RDS SG
SHARED_RDS_SG="$(aws rds describe-db-instances \
  --db-instance-identifier book-service-pg-db \
  --query 'DBInstances[0].VpcSecurityGroups[0].VpcSecurityGroupId' --output text \
  --region "${AWS_REGION}")"
aws ec2 authorize-security-group-ingress \
  --group-id "${SHARED_RDS_SG}" --protocol tcp --port 5432 \
  --source-group "${ECS_SG}" --region "${AWS_REGION}" 2>/dev/null || true
log "  Shared RDS SG: ${SHARED_RDS_SG} — ingress from ${ECS_SG} allowed"


# ── 7. CloudWatch log group ───────────────────────────────────────────────────
log "[7/11] CloudWatch log group ${LOG_GROUP}..."
aws logs create-log-group \
  --log-group-name "${LOG_GROUP}" \
  --region "${AWS_REGION}" 2>/dev/null || true
aws logs put-retention-policy \
  --log-group-name "${LOG_GROUP}" \
  --retention-in-days 30 \
  --region "${AWS_REGION}" 2>/dev/null || true


# ── 8. ECS cluster ────────────────────────────────────────────────────────────
log "[8/11] ECS cluster ${ECS_CLUSTER}..."
aws ecs create-cluster \
  --cluster-name "${ECS_CLUSTER}" \
  --region "${AWS_REGION}" 2>/dev/null || true


# ── 9. ALB + target group + listener + wait for RDS ──────────────────────────
log "[9/11] Application Load Balancer..."

ALB_ARN="$(aws elbv2 create-load-balancer \
  --name "${APP_NAME}-alb" \
  --type application \
  --scheme internet-facing \
  --subnets "${SUBNET_1}" "${SUBNET_2}" \
  --security-groups "${ALB_SG}" \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text \
  --region "${AWS_REGION}" 2>/dev/null \
  || aws elbv2 describe-load-balancers \
       --names "${APP_NAME}-alb" \
       --query 'LoadBalancers[0].LoadBalancerArn' --output text \
       --region "${AWS_REGION}")"
log "  ALB: ${ALB_ARN}"

TG_ARN="$(aws elbv2 create-target-group \
  --name "${APP_NAME}-tg" \
  --protocol HTTP \
  --port 8080 \
  --target-type ip \
  --vpc-id "${VPC_ID}" \
  --health-check-protocol HTTP \
  --health-check-path /actuator/health \
  --health-check-interval-seconds 30 \
  --health-check-timeout-seconds 10 \
  --healthy-threshold-count 2 \
  --unhealthy-threshold-count 3 \
  --query 'TargetGroups[0].TargetGroupArn' --output text \
  --region "${AWS_REGION}" 2>/dev/null \
  || aws elbv2 describe-target-groups \
       --names "${APP_NAME}-tg" \
       --query 'TargetGroups[0].TargetGroupArn' --output text \
       --region "${AWS_REGION}")"
log "  Target group: ${TG_ARN}"

LISTENER_ARN="$(aws elbv2 create-listener \
  --load-balancer-arn "${ALB_ARN}" \
  --protocol HTTP --port 80 \
  --default-actions "Type=forward,TargetGroupArn=${TG_ARN}" \
  --query 'Listeners[0].ListenerArn' --output text \
  --region "${AWS_REGION}" 2>/dev/null \
  || aws elbv2 describe-listeners \
       --load-balancer-arn "${ALB_ARN}" \
       --query 'Listeners[0].ListenerArn' --output text \
       --region "${AWS_REGION}")"
log "  Listener: ${LISTENER_ARN}"

log "  Waiting for ALB to become active..."
aws elbv2 wait load-balancer-available \
  --load-balancer-arns "${ALB_ARN}" \
  --region "${AWS_REGION}"
ALB_DNS="$(aws elbv2 describe-load-balancers \
  --load-balancer-arns "${ALB_ARN}" \
  --query 'LoadBalancers[0].DNSName' --output text \
  --region "${AWS_REGION}")"
log "  ALB DNS: ${ALB_DNS}"

RDS_ENDPOINT="$(aws rds describe-db-instances \
  --db-instance-identifier book-service-pg-db \
  --query 'DBInstances[0].Endpoint.Address' --output text \
  --region "${AWS_REGION}")"
log "  RDS endpoint (shared): ${RDS_ENDPOINT}"


# ── 10. ECS task definition ───────────────────────────────────────────────────
log "[10/11] ECS task definition (ARM64)..."

DATASOURCE_URL="jdbc:postgresql://${RDS_ENDPOINT}:5432/${DB_NAME}"

CONTAINER_DEFS="$(cat <<EOF
[{
  "name": "${APP_NAME}",
  "image": "${ECR_URI}:latest",
  "portMappings": [{"containerPort": 8080, "protocol": "tcp"}],
  "environment": [
    {"name": "SPRING_DATASOURCE_URL",        "value": "${DATASOURCE_URL}"},
    {"name": "SPRING_DATASOURCE_USERNAME",   "value": "${DB_USERNAME}"},
    {"name": "SPRING_DATASOURCE_PASSWORD",   "value": "${DB_PASSWORD}"},
    {"name": "SPRING_JPA_HIBERNATE_DDL_AUTO","value": "validate"},
    {"name": "JWT_SECRET",                   "value": "${JWT_SECRET}"},
    {"name": "JWT_EXPIRATION",               "value": "2592000000"},
    {"name": "SPRING_PROFILES_ACTIVE",       "value": "production"},
    {"name": "JAVA_OPTS",                    "value": "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"}
  ],
  "logConfiguration": {
    "logDriver": "awslogs",
    "options": {
      "awslogs-group":         "${LOG_GROUP}",
      "awslogs-region":        "${AWS_REGION}",
      "awslogs-stream-prefix": "ecs"
    }
  },
  "essential": true
}]
EOF
)"

TASK_DEF_ARN="$(aws ecs register-task-definition \
  --family "${APP_NAME}" \
  --network-mode awsvpc \
  --requires-compatibilities FARGATE \
  --cpu "${ECS_CPU}" \
  --memory "${ECS_MEMORY}" \
  --execution-role-arn "${ECS_EXEC_ROLE_ARN}" \
  --container-definitions "${CONTAINER_DEFS}" \
  --runtime-platform "cpuArchitecture=ARM64,operatingSystemFamily=LINUX" \
  --query 'taskDefinition.taskDefinitionArn' --output text \
  --region "${AWS_REGION}")"
log "  Task definition: ${TASK_DEF_ARN}"


# ── 11. ECS Fargate service ───────────────────────────────────────────────────
log "[11/11] ECS Fargate service..."

NET_CFG="$(printf \
  '{"awsvpcConfiguration":{"subnets":["%s","%s"],"securityGroups":["%s"],"assignPublicIp":"ENABLED"}}' \
  "${SUBNET_1}" "${SUBNET_2}" "${ECS_SG}")"

LB_CFG="$(printf \
  '[{"targetGroupArn":"%s","containerName":"%s","containerPort":8080}]' \
  "${TG_ARN}" "${APP_NAME}")"

ECS_SERVICE_ARN="$(aws ecs create-service \
  --cluster "${ECS_CLUSTER}" \
  --service-name "${APP_NAME}-service" \
  --task-definition "${TASK_DEF_ARN}" \
  --desired-count 1 \
  --launch-type FARGATE \
  --network-configuration "${NET_CFG}" \
  --load-balancers "${LB_CFG}" \
  --health-check-grace-period-seconds 90 \
  --query 'service.serviceArn' --output text \
  --region "${AWS_REGION}" 2>/dev/null \
  || aws ecs update-service \
       --cluster "${ECS_CLUSTER}" \
       --service "${APP_NAME}-service" \
       --task-definition "${TASK_DEF_ARN}" \
       --force-new-deployment \
       --query 'service.serviceArn' --output text \
       --region "${AWS_REGION}")"
log "  Service: ${ECS_SERVICE_ARN}"

log "  Waiting for service to stabilize (up to 10 minutes)..."
log "  Stream logs: aws logs tail ${LOG_GROUP} --follow --region ${AWS_REGION}"
aws ecs wait services-stable \
  --cluster "${ECS_CLUSTER}" \
  --services "${APP_NAME}-service" \
  --region "${AWS_REGION}"
log "  Service is stable."


# ── Save state ─────────────────────────────────────────────────────────────────
cat > "${STATE_FILE}" <<EOF
AWS_REGION=${AWS_REGION}
APP_NAME=${APP_NAME}
ECR_URI=${ECR_URI}
ECS_CLUSTER=${ECS_CLUSTER}
ECS_SERVICE=${APP_NAME}-service
TASK_DEF_FAMILY=${APP_NAME}
TASK_DEF_ARN=${TASK_DEF_ARN}
ALB_ARN=${ALB_ARN}
ALB_DNS=${ALB_DNS}
TG_ARN=${TG_ARN}
LISTENER_ARN=${LISTENER_ARN}
RDS_IDENTIFIER=book-service-pg-db
RDS_SHARED=true
ALB_SG=${ALB_SG}
ECS_SG=${ECS_SG}
RDS_SG=${RDS_SG}
LOG_GROUP=${LOG_GROUP}
DB_SUBNET_GROUP=${DB_SUBNET_GROUP}
ECS_EXEC_ROLE_NAME=${ECS_EXEC_ROLE_NAME}
EOF

log ""
log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log " Deployment complete!"
log ""
log "  Public API:   http://${ALB_DNS}"
log "  Health:       http://${ALB_DNS}/actuator/health"
log ""
log "  Stream logs:  aws logs tail ${LOG_GROUP} --follow --region ${AWS_REGION}"
log "  Tear down:    bash deploy/teardown.sh"
log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
