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

## Release Process

Releases are automated via [release-please](https://github.com/googleapis/release-please):

1. Merge conventional commits to `main`
2. release-please opens or updates a release PR (bumps version in `gradle.properties`)
3. Maintainer reviews and merges the release PR
4. release-please creates a semver tag and GitHub Release with auto-generated notes
5. The publish workflow publishes the release to Maven Central
6. A follow-up PR is automatically created to bump to the next SNAPSHOT version
