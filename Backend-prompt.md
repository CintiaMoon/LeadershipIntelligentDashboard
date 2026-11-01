# 1. FIRST: UNDERSTAND THE PROJECT
Read and understand these documents:
- `CLAUDE.md`
- `spec.md`
- `architect.md`


These documents are the primary sources of truth.

Use the following precedence:

1. `spec.md`: functional requirements and scoring semantics.
2. `architect.md`: architectural decisions and technical contracts.
3. `frontend-spec.md`: frontend requirements and presentation.
4. `CLAUDE.md`: repository-wide implementation instructions.

If two documents contradict each other:

- Do not silently choose one.
- Identify the conflicting requirements.
- Explain the impact.
- Propose a resolution.
- Wait for approval if the conflict changes business behavior or scoring semantics.

Do not invent requirements to fill gaps.
Before implementation, create:

`docs/implementation/`

Include:
- `IMPLEMENTATION_PLAN.md`
- `REQUIREMENTS_TRACEABILITY.md`
- `ARCHITECTURE_GAPS.md`
- `API_CONTRACT.md`
- `TEST_STRATEGY.md`

The requirements traceability document must map every functional requirement to:

- Specification reference.
- Backend component.
- Frontend component, where applicable.
- Required tests.
- Implementation status.

Do not start implementation until this analysis is complete.

# 2. PRODUCT OBJECTIVE

Implement the Leadership Intelligence Scoring Engine.

The engine transforms organizational assessment answers into a deterministic, explainable diagnostic model.

The platform must calculate:

- Team Health.
- Delivery Stability.
- Technical Risk.
- Leadership Load.
- Leadership Intelligence Score (LIS).

The platform must additionally identify:

- Organizational patterns.
- Cross-dimensional patterns.
- Organizational imbalances.
- Risk severity.
- Evidence and confidence.
- Scoring penalties.
- Recommended actions.
- Detailed scoring explanations.

The engine must help engineering leaders understand not only WHAT their organizational scores are, but WHY they received those scores.

The product must not be perceived as a generic survey dashboard.

It should provide an organizational intelligence and diagnostic experience.
