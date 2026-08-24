#!/usr/bin/env bash
# 15번 문서(docs/k3s-learning/15-node-a-failure-alerting-and-auto-recovery.md) 3~4장을
# 실제로 적용하는 산출물이다. 이슈 4는 이 스크립트를 작성만 하고 실행하지 않는다.
# AWS 콘솔/CLI 조작은 사람이 직접 검토 후 실행한다.
set -Eeuo pipefail

: "${AWS_REGION:?AWS_REGION을 설정하세요 (예: ap-northeast-2)}"
: "${NODE_A_INSTANCE_ID:?NODE_A_INSTANCE_ID를 설정하세요 (Node A EC2 instance ID)}"
: "${ALERT_EMAIL:?ALERT_EMAIL을 설정하세요 (SNS 알림을 받을 이메일)}"

SNS_TOPIC_NAME="openbake-infra-alerts"

echo "=== 1. SNS Topic 생성/조회: ${SNS_TOPIC_NAME} ==="
SNS_TOPIC_ARN="$(aws sns create-topic \
  --name "$SNS_TOPIC_NAME" \
  --region "$AWS_REGION" \
  --query 'TopicArn' --output text)"
echo "SNS_TOPIC_ARN=$SNS_TOPIC_ARN"

echo "=== 2. 이메일 구독 (최초 1회, 수신 확인 메일에서 Confirm 필요) ==="
aws sns subscribe \
  --topic-arn "$SNS_TOPIC_ARN" \
  --protocol email \
  --notification-endpoint "$ALERT_EMAIL" \
  --region "$AWS_REGION"

echo "=== 3. System status check 알람 (하드웨어 장애 → EC2 Recover) ==="
aws cloudwatch put-metric-alarm \
  --alarm-name "NodeA-SystemStatusCheckFailed" \
  --alarm-description "Node A 호스트 하드웨어 장애 감지 시 EC2 Recover" \
  --namespace "AWS/EC2" \
  --metric-name "StatusCheckFailed_System" \
  --dimensions "Name=InstanceId,Value=${NODE_A_INSTANCE_ID}" \
  --statistic Maximum \
  --period 60 \
  --evaluation-periods 2 \
  --threshold 1 \
  --comparison-operator GreaterThanOrEqualToThreshold \
  --alarm-actions "$SNS_TOPIC_ARN" "arn:aws:automate:${AWS_REGION}:ec2:recover" \
  --ok-actions "$SNS_TOPIC_ARN" \
  --region "$AWS_REGION"

echo "=== 4. Instance status check 알람 (OS 레벨 hang → EC2 Reboot) ==="
aws cloudwatch put-metric-alarm \
  --alarm-name "NodeA-InstanceStatusCheckFailed" \
  --alarm-description "Node A OS 레벨 장애 감지 시 EC2 Reboot" \
  --namespace "AWS/EC2" \
  --metric-name "StatusCheckFailed_Instance" \
  --dimensions "Name=InstanceId,Value=${NODE_A_INSTANCE_ID}" \
  --statistic Maximum \
  --period 60 \
  --evaluation-periods 2 \
  --threshold 1 \
  --comparison-operator GreaterThanOrEqualToThreshold \
  --alarm-actions "$SNS_TOPIC_ARN" "arn:aws:automate:${AWS_REGION}:ec2:reboot" \
  --ok-actions "$SNS_TOPIC_ARN" \
  --region "$AWS_REGION"

echo
echo "완료. 다음을 사람이 직접 확인한다:"
echo "  1. ${ALERT_EMAIL}에서 SNS 구독 확인(Confirm subscription) 메일 클릭"
echo "  2. aws cloudwatch describe-alarms --alarm-names NodeA-SystemStatusCheckFailed NodeA-InstanceStatusCheckFailed --region ${AWS_REGION}"
echo "  3. t3.large가 이 리전에서 Auto Recovery를 지원하는지 AWS 문서로 재확인"
echo "  4. Node A에서: systemctl is-enabled k3s && systemctl show k3s -p Restart"
