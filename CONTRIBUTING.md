# Contributing to Screenshot Manager

Thank you for your interest in contributing to Screenshot Manager! This document provides guidelines
and instructions for contributing.

## 🤝 How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported in [Issues](https://github.com/DarkPhilosophy/Ko/issues)
2. If not, create a new issue using the **Bug Report** template
3. Provide as much detail as possible:
- Steps to reproduce
- Expected behavior
- Actual behavior
- Device information (Android version, device model)
- Screenshots if applicable
    - Debug logs from the app's Debug Console

### Suggesting Features

1. Check if the feature has already been suggested in [Issues](https://github.com/DarkPhilosophy/Ko/issues)
2. If not, create a new issue using the **Feature Request** template
3. Clearly describe:
- The problem you're trying to solve
- Your proposed solution
    - Any alternatives you've considered
    - How it fits with the app's screenshot management goals

### Submitting Pull Requests

1. **Fork the repository**
   ```bash
   git clone https://github.com/DarkPhilosophy/Ko.git

cd screenshot-manager
   ```

2. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make your changes**
   - Follow the code style guidelines (see below)
   - Add tests if applicable
   - Update documentation as needed

4. **Run quality checks**
   ```bash
   # Run tests
   ./gradlew test
   
   # Run static analysis
   ./gradlew detekt
   
   # Build the project
   ./gradlew build
   ```

5. **Commit your changes**
   ```bash
   git add .
   git commit -m "feat: add your feature description"
   ```
   
   Follow [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat:` - New feature
   - `fix:` - Bug fix
   - `docs:` - Documentation changes
   - `style:` - Code style changes (formatting, etc.)
   - `refactor:` - Code refactoring
   - `test:` - Adding or updating tests
   - `chore:` - Maintenance tasks

6. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Create a Pull Request**
   - Go to the original repository
   - Click "New Pull Request"
   - Select your branch
   - Fill out the PR template
   - Wait for review

## 📝 Code Style Guidelines

### Kotlin Style

This project follows the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) with additional rules enforced by Detekt and ktlint.

**Key Points:**
- Use 4 spaces for indentation
- Maximum line length: 120 characters
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Prefer `val` over `var` when possible
- Use trailing commas in multi-line declarations

### Example

```kotlin
/**
 * Handles button click events with haptic feedback.
 *
 * @param view The view that was clicked
 */
private fun handleButtonClick(view: View) {
    view.performHapticFeedback(
        HapticFeedbackConstants.VIRTUAL_KEY,
        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
    )
    // Your logic here
}
```

### XML Style

- Use 4 spaces for indentation
- Use `android:` namespace prefix consistently
- Group attributes logically (id, layout, style, content)
- Use `@string` resources for all user-facing text
- Use `@dimen` resources for dimensions
- Use `@color` resources for colors

## 🧪 Testing

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests "ro.snapify.MainActivityTest"

# Run instrumented tests (requires emulator or device)
./gradlew connectedAndroidTest
```

### Writing Tests

- Write unit tests for business logic
- Write instrumented tests for UI interactions
- Aim for meaningful test coverage
- Use descriptive test names: `testButtonClick_shouldProvidHapticFeedback()`

## 🔍 Static Analysis

### Running Detekt

```bash
# Run Detekt
./gradlew detekt

# View report
open app/build/reports/detekt/detekt.html
```

### Fixing Issues

Most formatting issues can be auto-fixed:
```bash
./gradlew detekt --auto-correct
```

## 📦 Project Structure

When adding new features:

- Place Kotlin files in `app/src/main/kotlin/com/araara/screenapp/`
- Place resources in appropriate `res/` subdirectories
- Update `strings.xml` for new text
- Update `colors.xml` for new colors
- Add tests in `app/src/test/` or `app/src/androidTest/`

## 🔄 Development Workflow

1. **Sync with upstream**
   ```bash
   git checkout main
   git pull upstream main
   ```

2. **Create feature branch**
   ```bash
   git checkout -b feature/my-feature
   ```

3. **Develop and test**
   - Make changes
   - Run tests: `./gradlew test`
   - Run static analysis: `./gradlew detekt`

4. **Commit and push**
   ```bash
   git add .
   git commit -m "feat: description"
   git push origin feature/my-feature
   ```

5. **Create PR and wait for review**

## ✅ PR Checklist

Before submitting your PR, ensure:

- [ ] Code follows the style guidelines (Kotlin conventions, 120 char limit)
- [ ] All tests pass (`./gradlew test`)
- [ ] Static analysis passes (`./gradlew detekt`)
- [ ] Build succeeds (`./gradlew build`)
- [ ] Documentation is updated (README, code comments)
- [ ] Commit messages follow Conventional Commits
- [ ] PR description is clear and complete
- [ ] Screenshots added for UI changes
- [ ] Manual testing on device/emulator
- [ ] No new Detekt warnings introduced

## 🎯 Good First Issues

Look for issues labeled `good first issue` to get started!

## 📞 Getting Help

- Open a [Discussion](https://github.com/DarkPhilosophy/Ko/discussions) for questions
- Check the [Debug Console](README.md#configuration) in the app for troubleshooting
- Review existing issues and PRs
- Check the [Wiki](https://github.com/DarkPhilosophy/Ko/wiki) (coming soon)

## 📜 License

By contributing, you agree that your contributions will be licensed under the same license as the project (MIT License).

## 🙏 Thank You!

Your contributions make this project better for everyone. Thank you for taking the time to contribute!

