# WS0 — Platform guardrails and resilience posture

Tracking issue: [COG-940](https://linear.app/cog-gtm/issue/COG-940/ws0-confirm-guardrails-g1-g10-and-the-resilience-posture-q-plat-02)

Status: **Accepted for the Fineract AWS migration baseline**

Owner: Platform Engineering

This decision closes Q-PLAT-02 and Q-PLAT-06 for the migration design. It applies to
the target platform built by the migration workstreams; an enterprise policy supplied
later supersedes this record where the two conflict. It records migration decisions,
not a claim that the customer's existing policy already defines G1–G10.

## Guardrails in force

| ID | Guardrail | Migration interpretation |
| --- | --- | --- |
| G1 | Approved-service allowlist | Only services approved through Q-PLAT-01 may appear in the target design. An unlisted service requires a recorded exception before implementation. |
| G2 | Containers first | Run Fineract as OCI containers on managed compute. VM lift-and-shift is not an approved default. |
| G3 | Standard target RDBMS | PostgreSQL is the standard engine. COG-941 owns the Aurora PostgreSQL product decision; another engine requires a recorded exception. |
| G4 | Account vending and promotion | Use separate dev, QA and prod accounts from the standard vending process. Promote the same version and infrastructure change dev → QA → prod. |
| G5 | Everything as code | Provision and change target infrastructure through Terraform. Console-created production resources are not permitted. |
| G6 | Approved delivery toolchain | Use Harness for environment promotion and deployment approvals. Existing CI may build and verify artifacts, but Harness owns delivery to AWS environments. |
| G7 | Production entry criteria | Production requires load balancing, the resilience posture below, and evidence from a successful DR/failover test. |
| G8 | Security baseline | Use private networking by default, encryption in transit and at rest, least-privilege workload roles, no long-lived static credentials, and the approved secrets manager. |
| G9 | Approved artifact sources | Images, base images and dependencies must come from approved registries or repository proxies and be deployed by immutable digest or version. |
| G10 | Tagging and observability | Apply mandatory owner, cost-centre, environment and data-classification tags. Send platform logs, metrics and traces to the approved central observability services. |

## Compute decision: ECS Fargate

Use **Amazon ECS on Fargate**, not a dedicated EKS cluster, for this workload.

The application already builds a container image through Jib
(`fineract-provider/build.gradle`) and exposes independent batch-manager and batch-worker
modes through configuration
(`fineract-provider/src/main/resources/application.properties`). The target therefore
needs one singleton service for the manager and independently scalable services for API
and worker traffic; ECS desired counts and service autoscaling provide those primitives
without introducing a Kubernetes control plane or cluster add-ons.

EKS remains an exception path only when Platform Engineering requires this workload to
join an existing shared EKS platform. A future EKS mandate changes the compute modules,
not G1–G10 or the environment resilience posture.

## Environment resilience posture

| Environment | Topology | Required proof |
| --- | --- | --- |
| Dev | One region, workloads and data services distributed across at least two Availability Zones | Deployment health checks and replacement of a task without loss of service |
| QA | One region, workloads and data services distributed across at least two Availability Zones | Load test, Availability Zone failure exercise, restore test and rehearsed deployment rollback |
| Prod | Two regions, active/passive; multi-AZ in each region | Regional failover test with measured RTO/RPO before production approval |

Production traffic terminates at a regional load balancer. The passive region must be
deployable from the same Terraform and Harness definitions as the primary region, use
replicated application artifacts and configuration, and have a tested data failover
procedure. Active/active is not required for this migration baseline.

Target RTO and RPO values remain business-owned inputs. They must be recorded before the
regional design is sized and the G7 failover test is accepted.

## Target platform summary

| Concern | Decision |
| --- | --- |
| Relational database | PostgreSQL; Aurora PostgreSQL remains subject to COG-941 |
| Container compute | ECS Fargate |
| Infrastructure as code | Terraform |
| Delivery | Harness, promoting dev → QA → prod |
| Dev and QA resilience | Multi-AZ, single region |
| Production resilience | Multi-AZ, multi-region active/passive |
| Production gate | Load balancing plus demonstrated regional DR/failover |

## Consequences

* WS1 should build ECS, not EKS, modules and must keep the runtime private and multi-AZ.
* WS4 should model the singleton manager separately from horizontally scaled API and
  worker services.
* WS6 should implement Harness promotion gates using immutable artifacts.
* WS8 must produce the regional failover evidence required by G7.
* Any downstream design that conflicts with this record must identify the guardrail,
  exception owner and approval evidence.
