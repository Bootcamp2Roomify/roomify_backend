# Contributing to Roomify

Thank you for contributing to Roomify. All team members must follow the agreed Git and GitHub workflow.

## Branch Strategy

Roomify uses the following branches:

- `main` contains stable and reviewed code.
- `develop` contains completed development work before release.
- `feature/*` is used for new features.
- `fix/*` is used for bug fixes.
- `docs/*` is used for documentation changes.

Examples:

- `feature/user-login`
- `feature/image-upload`
- `fix/login-validation`
- `docs/repository-setup`

## Development Workflow

1. Switch to the `develop` branch.
2. Make sure `develop` is up to date.
3. Create a new branch from `develop`.
4. Make and test the required changes.
5. Commit the changes using a clear Conventional Commit message.
6. Push the branch to GitHub.
7. Open a Pull Request into `develop`.
8. Request a review from at least one teammate.
9. Address review comments and resolve merge conflicts.
10. Merge the Pull Request only after it is approved.

Team members should not push development work directly into `main` or `develop`.

## Conventional Commits

Use the following commit types:

- `feat:` for a new feature
- `fix:` for a bug fix
- `docs:` for documentation
- `chore:` for project setup or maintenance
- `test:` for tests
- `refactor:` for code improvements without changing behavior

Examples:

```text
feat: add room image upload
fix: validate empty budget input
docs: update project setup instructions
chore: add gitignore
test: add authentication tests
refactor: reorganize room project service
```

## Pull Request Titles

Pull Request titles must include the related Jira issue key:

```text
[ROOM-###] <type>: Brief description
```

Example:

```text
[ROOM-7] docs: Set up backend repository and Git workflow
```

## Pull Request Requirements

Each Pull Request must:

- Target the `develop` branch.
- Include a clear description and summary of the changes.
- Reference the related Jira task.
- Include testing information when applicable.
- Be reviewed by at least one team member.
- Resolve review comments and merge conflicts before merging.
- Contain no passwords, API keys, or other secrets.

## Code Review

Reviewers should confirm:

- The changes match the related Jira task.
- The code is clear and organized.
- The affected functionality has been tested.
- API or database changes are documented.
- No secrets or unnecessary files were committed.

The author must address review feedback before the Pull Request is merged.

## Environment Variables and Secrets

Use `.env.example` to document required environment variables.

Never commit:

- Real `.env` files
- Database usernames or passwords
- JWT secrets
- OpenAI or Gemini API keys
- AWS access keys
- Cloud-storage credentials

Production secrets should be stored securely using AWS Secrets Manager.

## Merge Conflicts

If a branch conflicts with `develop`, update the branch with the latest changes from `develop`, resolve the conflicts, test the result, and push the corrected changes before requesting another review.

## Merge Strategy

Feature, fix, and documentation branches must be merged into `develop` through Pull Requests. The `develop` branch should only be merged into `main` when the application is stable and ready for release.
