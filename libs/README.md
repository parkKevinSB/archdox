# Runtime Library Snapshots

ArchDox vendors the small runtime source snapshots it currently needs from
Flower and Bloom:

- `libs/bloom`
- `libs/flower`

This keeps GitHub Actions and fresh checkouts self-contained. The source
repositories may still exist separately, but ArchDox builds against these
snapshots unless `settings.gradle.kts` is changed deliberately.

When syncing from the source repositories:

1. Copy only source and lightweight metadata needed for compilation.
2. Do not copy build outputs, logs, IDE files, or local assistant files.
3. Run `./gradlew test`.
4. Document the reason for the sync in the commit message.
