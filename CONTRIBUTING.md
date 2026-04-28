# Contributing to micronaut-utils

## Commit Messages

This project follows [Conventional Commits](https://www.conventionalcommits.org/).

### Format

All commits on pull request branches must follow this format. CI validates each commit individually:

```
<type>(<scope>)?: <description>
```

### Valid Types

- `feat` — new feature
- `fix` — bug fix
- `chore` — non-code changes (deps, CI, configs)
- `deps` — dependency updates (Renovate uses this)
- `docs` — documentation changes
- `ci` — CI/CD changes
- `refactor` — code refactoring
- `test` — test-only changes

### Examples

✅ Good commit messages:
- `feat: add OAuth2 token refresh`
- `fix(auth): handle expired tokens correctly`
- `chore(deps): update Gradle to 9.5.0`
- `docs: add configuration examples`

❌ Bad commit messages:
- `Update dependencies` (missing type)
- `feat Update dependencies` (missing colon)
- `FEAT: add feature` (uppercase type)

### Validation

CI checks every commit on your PR branch. `fixup!`, `squash!`, and merge commits are skipped automatically. All other commits must match the format above.
