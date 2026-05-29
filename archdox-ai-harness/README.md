# archdox-ai-harness

`archdox-ai-harness` contains ArchDox-specific AI harness definitions built on top of `flower-ai-harness`.

This module owns ArchDox prompt builders, input/output schemas, validation wiring, and finding extractors for bounded AI tasks such as document QA and report preflight review. It does not own REST controllers, authentication, office policy lookup, database writes, or UI state transitions. Those remain in `cloud-api`, which submits and orchestrates harness work through ArchDox Flower flows.

Current responsibilities:

- Document QA harness specs and result mapping
- Report preflight review harness specs and result mapping
- ArchDox-specific prompt/schema/finding contracts
- Testable fake-provider harness behavior without external API keys

Not responsibilities:

- Generic AI provider abstraction
- Generic Flower runtime behavior
- Platform authorization or billing policy
- Long-running business workflow orchestration
- Direct document rendering or artifact storage

Future direction:

Some pieces may become generally useful outside ArchDox, especially harness observation, trace export, cost/usage snapshots, or reusable provider adapters. Those should first prove themselves here. If they become product-neutral, they can later be extracted into separate `flower-ai-harness-*` modules. Until then, this module stays intentionally ArchDox-specific.
