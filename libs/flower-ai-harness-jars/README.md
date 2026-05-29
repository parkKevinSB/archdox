Vendored flower-ai-harness snapshot jars used only until the framework is published
to an internal or public Maven repository.

Docker builds must not depend on a developer workstation's Maven local cache, so
ArchDox resolves these jars first and falls back to Maven coordinates when the
vendored files are absent.
