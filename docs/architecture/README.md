# Migration Architecture Package — status index

Point-in-time architecture baseline for the AWS migration review. All evidence was read from
`develop` @ `8c187f9d1`; citations are pinned to that commit.

| Document | Status | Notes |
|---|---|---|
| [`current-state.md`](current-state.md) | **Evidence-backed** | Every claim carries a `file:line` citation; anything beyond what the repo literally says is labelled `[INFERENCE]` |
| [`target-state.md`](target-state.md) | **DRAFT — not approved** | Blocked on the approved ("blessed") AWS service list, which was never supplied. Written against an *assumed* list; the mapping table in §2 is a proposal, **not a decision** |
| [`open-questions.md`](open-questions.md) | Open | 49 questions addressed to named roles; answers should be recorded inline so this becomes the decision log |
| [`migration-plan.md`](migration-plan.md) | **Provisional** | Sequencing holds regardless of service choices, but WS1/WS5/WS7 detail depends on the answer to Q0 |

Nothing in this directory changes application code or configuration.
