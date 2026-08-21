#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
#
# Runs the Liquibase migration task definition as a one-off ECS task and blocks until it
# finishes. Liquibase never runs on service start: the tenant upgrade is single-threaded,
# so startup time is proportional to tenant count and would stall every scale-out.
#
# Usage:
#   CLUSTER=fineract-dev TASK_DEFINITION=fineract-dev-migrate \
#   SUBNETS=subnet-a,subnet-b SECURITY_GROUP=sg-123 \
#   scripts/ecs-run-liquibase.sh

set -euo pipefail

: "${CLUSTER:?CLUSTER is required}"
: "${TASK_DEFINITION:?TASK_DEFINITION is required}"
: "${SUBNETS:?SUBNETS is required (comma separated)}"
: "${SECURITY_GROUP:?SECURITY_GROUP is required}"

CONTAINER_NAME="${CONTAINER_NAME:-liquibase}"
LOG_GROUP="${LOG_GROUP:-/aws/ecs/${CLUSTER}-migrate}"

network_configuration="awsvpcConfiguration={subnets=[${SUBNETS}],securityGroups=[${SECURITY_GROUP}],assignPublicIp=DISABLED}"

echo "Starting one-off migration task from ${TASK_DEFINITION} in ${CLUSTER}"
task_arn="$(aws ecs run-task \
  --cluster "${CLUSTER}" \
  --task-definition "${TASK_DEFINITION}" \
  --launch-type FARGATE \
  --count 1 \
  --network-configuration "${network_configuration}" \
  --started-by "liquibase-gate" \
  --query 'tasks[0].taskArn' \
  --output text)"

if [[ -z "${task_arn}" || "${task_arn}" == "None" ]]; then
  echo "run-task did not return a task ARN" >&2
  exit 1
fi

task_id="${task_arn##*/}"
echo "Task ${task_id} started; waiting for completion"

aws ecs wait tasks-stopped --cluster "${CLUSTER}" --tasks "${task_arn}"

read -r exit_code stopped_reason < <(aws ecs describe-tasks \
  --cluster "${CLUSTER}" \
  --tasks "${task_arn}" \
  --query "tasks[0].[containers[?name=='${CONTAINER_NAME}'].exitCode | [0], stoppedReason]" \
  --output text)

echo "Migration logs: ${LOG_GROUP}/${CONTAINER_NAME}/${task_id}"

if [[ "${exit_code}" != "0" ]]; then
  echo "Liquibase task failed (exit code ${exit_code}): ${stopped_reason}" >&2
  aws logs tail "${LOG_GROUP}" --since 1h --format short || true
  exit 1
fi

echo "Liquibase migration completed successfully"
