## 2025-05-15 - [Avoid committing build artifacts]
**Learning:** Running `gradle assembleDebug` in the sandbox generated a `.gradle` directory and other build artifacts which were accidentally included in the initial git status.
**Action:** Always verify `git status` and explicitly add only the necessary source files to avoid polluting the PR with environment-specific binary data.
