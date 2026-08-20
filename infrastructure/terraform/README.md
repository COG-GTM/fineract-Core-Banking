# Infrastructure as code

Terraform for the AWS target architecture described in `docs/architecture/target-state.md`.

```
infrastructure/terraform
├── modules/edge          ALB, ACM, WAF, Route 53 (WS1)
└── environments/dev      dev composition of the modules
```

## `modules/edge`

Internet-facing entry point for the Fineract API:

- **ALB** in the public subnets, `drop_invalid_header_fields` on, HTTP/2 on.
- **HTTPS listener on 443 only**, TLS 1.3/1.2 policy (`ELBSecurityPolicy-TLS13-1-2-2021-06`), certificate from ACM. Port 80 exists solely to answer `HTTP_301` to HTTPS.
- **ACM** certificate, DNS validated, renewed unattended.
- **Route 53** public hosted zone (optional) plus the alias record for the service name.
- **WAF** regional web ACL — AWS managed common, known-bad-inputs, SQLi and IP-reputation rule groups plus a rate-based rule — associated with the ALB.

TLS terminating here replaces the in-process TLS the application configures from a keystore committed to the repository (`fineract-provider/src/main/resources/application.properties`, `server.ssl.key-store`). Tasks keep listening on 8443 inside the VPC; nothing in the application changes.

The VPC and public subnets come from the network module and are passed in as `vpc_id` and `public_subnet_ids`.

## Tests

Terraform's native test framework against a mocked AWS provider — no credentials, no API calls, nothing created:

```bash
cd infrastructure/terraform/modules/edge
terraform init -backend=false
terraform test
```

Covered: the ALB is internet-facing across multiple subnets, HTTPS is the only listener serving traffic, port 80 permanently redirects, the TLS policy is modern and the listener uses the *validated* certificate, the web ACL is associated with this ALB and carries every rule, the alias record targets the ALB, and weak-TLS or single-subnet inputs are rejected by variable validation.

## Applying an environment

```bash
cd infrastructure/terraform/environments/dev
terraform init -backend-config=backend.hcl
terraform plan -var-file=dev.tfvars
```

`backend.hcl` and `dev.tfvars` are account-specific and are not committed.
