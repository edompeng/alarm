<!--
# Sync Impact Report
- Version change: Unversioned Scaffold → 1.0.0
- Ratified: 2026-09-13
- Modified principles:
  - [PRINCIPLE_1_NAME] → I. Minimal Change & Anti-Overengineering (YAGNI)
  - [PRINCIPLE_2_NAME] → II. Functionality & User Experience Priority
  - [PRINCIPLE_3_NAME] → III. Robustness, Stability & Safe Refactoring
  - [PRINCIPLE_4_NAME] → IV. Performance & Efficiency Guarantee
  - [PRINCIPLE_5_NAME] → V. Design Patterns & Decoupled Architecture
  - Added Principle VI: Test-First & Comprehensive Test Coverage
  - Added Principle VII: Google C++ Style & Documentation Rigor
- Added sections:
  - Technology Stack & Tooling Constraints
  - Development Workflow & Quality Gates
  - Governance & Amendment Protocol
- Removed sections: None
- Follow-up TODOs: None
-->

# Alarm Constitution

## Core Principles

### I. Minimal Change & Anti-Overengineering (YAGNI)
All coding and modification work MUST follow the minimal change principle. Developers MUST NOT introduce speculative abstractions, redundant layers, or premature generalization for unverified future requirements. Every modification MUST address concrete, current requirements with the most concise and direct solution that remains maintainable.

### II. Functionality & User Experience Priority
Feature functionality and user experience MUST be treated as the highest priority across the system. System interactions, interface responsiveness, and functional workflows MUST be designed to be intuitive, reliable, and predictable. When technical trade-offs arise, solutions preserving user experience and end-to-end functionality MUST take precedence over theoretical architectural elegance.

### III. Robustness, Stability & Safe Refactoring
All code MUST guarantee resilience, error recovery, and runtime stability under invalid inputs, edge conditions, and environmental failures. When refactoring existing modules:
- Functions and logic MUST NOT be replaced with simplified stubs, empty implementations, or deferred `TODO` placeholders.
- Existing capabilities, edge case handling, and regressions MUST remain fully protected and operational.

### IV. Performance & Efficiency Guarantee
The system MUST maintain high runtime efficiency, minimal latency, and disciplined resource consumption (CPU, memory, storage, and I/O). Critical paths MUST avoid unnecessary allocations, redundant computations, or blocking operations. Algorithmic complexity and resource footprints MUST be evaluated and kept optimal.

### V. Design Patterns & Decoupled Architecture
New features and architectural refactoring MUST adhere to the Six Principles of Object-Oriented Design (SOLID and Demeter's Law). Appropriate design patterns MUST be leveraged where applicable to decouple components, clarify responsibilities, and maintain modular testability, while avoiding gratuitous complexity.

### VI. Test-First & Comprehensive Test Coverage
All testable modules, business logic, and utilities MUST have accompanying unit tests. Changes MUST NOT be merged without passing automated test suites. Automated tests MUST validate both nominal paths and critical boundary conditions to ensure regression-free iteration.

### VII. Google C++ Style & Documentation Rigor
All C++ codebase additions and maintenance MUST strictly adhere to the Google C++ Style Guide:
- Class and Function names MUST use UpperCamelCase (`PascalCase`).
- Local variables, function parameters, and struct members MUST use `snake_case`.
- Constants MUST follow the `kCamelCase` format.
- Macros MUST be formatted in uppercase with underscores (`UPPER_SNAKE_CASE`).
- Class member variables MUST terminate with a trailing underscore (`member_var_`).
- Header files MUST include `#pragma once` as the include guard.
- Deeply nested namespaces MUST be avoided; prefer flat declarations such as `namespace A::B { ... }`.
- If `.clang-format` exists in the repository, code MUST be formatted strictly according to its rules.
- Code comments MUST be written in English and reserved solely for meaningful explanations (documenting intent, algorithmic rationale, caveats, or domain nuances).

## Technology Stack & Tooling Constraints

The build and runtime environment is governed by standard development paths and configuration flags:
- Bazel path: `/opt/homebrew/bin/bazel`. Bazel invocations MUST include the `--keep_going` parameter to aggregate build diagnostics and enable comprehensive fixes.
- CMake path: `/opt/homebrew/bin/cmake`.
- PROJ library installation path: `/opt/homebrew/opt/proj`.
- Qt installation path: `/Users/edom/Qt/6.9.1/macos`.
- Both Bazel and CMake builds, alongside automated unit tests, MUST compile cleanly and pass without errors.

## Development Workflow & Quality Gates

Every code addition and refactor MUST clear the following sequential quality verification gates:
1. **Formatting & Style Compliance**: Validate formatting against `.clang-format` and verify naming and structural conventions.
2. **Clean Build**: Execute build scripts under Bazel (`/opt/homebrew/bin/bazel build --keep_going //...`) or CMake to ensure zero compilation errors or breaking warnings.
3. **Automated Test Suite**: Run all unit test targets to guarantee complete green status.
4. **Behavioral Integrity**: Confirm that existing behaviors remain intact and non-regressive before landing changes.

## Governance

This constitution supersedes ad-hoc coding conventions and serves as the non-negotiable architectural contract for the project.

- **Amendment Procedure**: Amendments to this constitution require documentation, technical rationale, and an explicit migration strategy.
- **Versioning Policy**: Semantic versioning MUST be followed for all constitutional amendments:
  - **MAJOR**: Incompatible governance modifications, principle removals, or substantial redefinitions.
  - **MINOR**: Addition of new principles or material expansion of governance sections.
  - **PATCH**: Non-semantic clarifications, typographical corrections, or formatting updates.
- **Compliance Reviews**: All feature specifications, implementation plans, and peer reviews MUST cross-verify compliance with these core tenets.

**Version**: 1.0.0 | **Ratified**: 2026-09-13 | **Last Amended**: 2026-09-13

