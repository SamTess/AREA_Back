# Checkstyle Guide

This guide explains how to use Checkstyle in the AREA Backend project for maintaining consistent code quality and style.

## Table of Contents

- [Overview](#overview)
- [Checkstyle Configuration](#checkstyle-configuration)
- [Running Checkstyle](#running-checkstyle)
- [Understanding Reports](#understanding-reports)
- [Common Rules and Fixes](#common-rules-and-fixes)
- [IDE Integration](#ide-integration)
- [Suppressing Violations](#suppressing-violations)
- [Customizing Rules](#customizing-rules)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

## Overview

Checkstyle is a development tool that helps ensure Java code adheres to coding standards. The AREA Backend project uses Checkstyle to maintain consistent code style across the entire codebase.

### Current Configuration

- **Checkstyle Version**: 10.12.4
- **Configuration File**: `config/checkstyle/checkstyle.xml`
- **Suppressions**: `config/checkstyle/suppressions.xml`
- **Maximum Warnings**: 50
- **Maximum Errors**: 20
- **Line Length Limit**: 120 characters

## Checkstyle Configuration

### Build Configuration

The Checkstyle plugin is configured in `build.gradle`:

```gradle
checkstyle {
    toolVersion = '10.12.4'
    configFile = file("config/checkstyle/checkstyle.xml")
    ignoreFailures = true
    maxWarnings = 50
    maxErrors = 20
}

checkstyleMain {
    reports {
        xml.required = true
        html.required = true
        html.outputLocation = file("build/reports/checkstyle/main.html")
    }
}

checkstyleTest {
    reports {
        xml.required = true
        html.required = true
        html.outputLocation = file("build/reports/checkstyle/test.html")
    }
}
```

### Main Configuration File

The main configuration is located at `config/checkstyle/checkstyle.xml`:

```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
    "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
    "https://checkstyle.org/dtds/configuration_1_3.dtd">

<module name="Checker">
    <property name="charset" value="UTF-8"/>
    <property name="severity" value="error"/>
    <property name="fileExtensions" value="java, properties, xml"/>

    <!-- Suppressions -->
    <module name="SuppressionFilter">
        <property name="file" value="${config_loc}/suppressions.xml"/>
        <property name="optional" value="true"/>
    </module>

    <!-- File checks -->
    <module name="FileLength">
        <property name="max" value="2000"/>
    </module>

    <module name="LineLength">
        <property name="fileExtensions" value="java"/>
        <property name="max" value="120"/>
        <property name="ignorePattern" value="^package.*|^import.*|a href|href|http://|https://|ftp://"/>
    </module>

    <!-- Other checks... -->
</module>
```

## Running Checkstyle

### Command Line

```bash
# Run checkstyle on main source code
./gradlew checkstyleMain

# Run checkstyle on test source code
./gradlew checkstyleTest

# Run checkstyle on both main and test code
./gradlew check

# Run only checkstyle (excluding other checks)
./gradlew checkstyleMain checkstyleTest
```

### Output Location

Checkstyle reports are generated in:
- **HTML Reports**: 
  - Main: `build/reports/checkstyle/main.html`
  - Test: `build/reports/checkstyle/test.html`
- **XML Reports**: 
  - Main: `build/reports/checkstyle/main.xml`
  - Test: `build/reports/checkstyle/test.xml`

## Understanding Reports

### HTML Report Structure

The HTML report provides:
- **Summary**: Total violations by severity
- **File List**: Files with violations
- **Detailed View**: Line-by-line violations with descriptions

### Reading Violations

Each violation shows:
- **Severity**: Error, Warning, or Info
- **Rule**: The specific Checkstyle rule violated
- **Line Number**: Where the violation occurs
- **Message**: Description of the issue
- **Column**: Specific column position (if applicable)

**Example Violation:**
```
[ERROR] LineLength: Line is longer than 120 characters (found 125). [LineLength]
File: UserController.java
Line: 45
Column: 125
```

## Common Rules and Fixes

### 1. Line Length Violations

**Rule**: Maximum 120 characters per line

**Common Violation:**
```java
// BAD - Line too long
public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request, HttpServletRequest httpRequest) {
```

**Fix:**
```java
// GOOD - Line wrapped appropriately
public ResponseEntity<UserResponse> createUser(
        @Valid @RequestBody CreateUserRequest request,
        HttpServletRequest httpRequest) {
```

### 2. Indentation Issues

**Rule**: Consistent indentation (4 spaces)

**Common Violation:**
```java
// BAD - Inconsistent indentation
public void someMethod() {
  if (condition) {
      doSomething();
    }
}
```

**Fix:**
```java
// GOOD - Consistent 4-space indentation
public void someMethod() {
    if (condition) {
        doSomething();
    }
}
```

### 3. Whitespace Issues

**Rule**: Proper whitespace around operators and keywords

**Common Violations:**
```java
// BAD - Missing whitespace
if(condition){
    int result=a+b;
}

// BAD - Extra whitespace
if ( condition ) {
    int result = a + b ;
}
```

**Fix:**
```java
// GOOD - Proper whitespace
if (condition) {
    int result = a + b;
}
```

### 4. Import Organization

**Rule**: Proper import ordering and no unused imports

**Common Violations:**
```java
// BAD - Wrong order, unused import
import java.util.List;
import area.server.AREA_Back.entity.User;
import java.time.LocalDateTime;
import java.util.Map; // unused
```

**Fix:**
```java
// GOOD - Proper order, no unused imports
import java.time.LocalDateTime;
import java.util.List;

import area.server.AREA_Back.entity.User;
```

### 5. Method Length

**Rule**: Methods should not exceed reasonable length (typically 150 lines)

**Fix**: Break large methods into smaller, focused methods:

```java
// BAD - Method too long
public void processUser() {
    // ... 200 lines of code
}

// GOOD - Broken into smaller methods
public void processUser() {
    validateUser();
    enrichUserData();
    saveUser();
}

private void validateUser() {
    // validation logic
}

private void enrichUserData() {
    // enrichment logic
}

private void saveUser() {
    // save logic
}
```

### 6. Naming Conventions

**Rules**: 
- Classes: PascalCase
- Methods/Variables: camelCase
- Constants: UPPER_SNAKE_CASE
- Packages: lowercase

**Examples:**
```java
// GOOD
public class UserController {
    private static final String DEFAULT_ROLE = "USER";
    private UserService userService;
    
    public ResponseEntity<UserResponse> createUser() {
        // method body
    }
}
```

### 7. Javadoc Requirements

**Rule**: Public classes and methods should have Javadoc

**Example:**
```java
/**
 * Controller for managing user-related operations.
 * Provides endpoints for CRUD operations on users.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    /**
     * Creates a new user in the system.
     *
     * @param request the user creation request containing user details
     * @return ResponseEntity containing the created user response
     * @throws ValidationException if the request contains invalid data
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        // method body
    }
}
```

## IDE Integration

### IntelliJ IDEA

1. **Install Checkstyle Plugin:**
   - File → Settings → Plugins
   - Search for "CheckStyle-IDEA"
   - Install and restart

2. **Configure Plugin:**
   - File → Settings → Tools → Checkstyle
   - Add configuration file: `config/checkstyle/checkstyle.xml`
   - Set as active

3. **Real-time Checking:**
   - Enable "Real-time scan"
   - Violations will be highlighted in the editor

### VS Code

1. **Install Extension:**
   - Install "Checkstyle for Java" extension

2. **Configure:**
   - Set checkstyle configuration file in settings
   - Enable automatic checking

### Eclipse

1. **Install Plugin:**
   - Help → Eclipse Marketplace
   - Search for "Checkstyle Plug-in"

2. **Configure:**
   - Window → Preferences → Checkstyle
   - Add configuration file

## Suppressing Violations

### Method 1: Suppressions File

Add suppressions to `config/checkstyle/suppressions.xml`:

```xml
<?xml version="1.0"?>
<!DOCTYPE suppressions PUBLIC
    "-//Checkstyle//DTD SuppressionFilter Configuration 1.2//EN"
    "https://checkstyle.org/dtds/suppressions_1_2.dtd">

<suppressions>
    <!-- Suppress LineLength for generated files -->
    <suppress checks="LineLength" files=".*Generated\.java"/>
    
    <!-- Suppress specific check for specific file -->
    <suppress checks="MagicNumber" files="Constants.java"/>
</suppressions>
```

### Method 2: Inline Suppressions

Use `@SuppressWarnings` annotation:

```java
@SuppressWarnings("checkstyle:LineLength")
public void methodWithLongLine() {
    // This method is exempt from line length checking
    String veryLongString = "This is a very long string that would normally violate the line length rule but is suppressed";
}
```

### Method 3: Comment-based Suppressions

```java
// CHECKSTYLE:OFF
public void methodWithoutChecks() {
    // Checkstyle is disabled for this method
}
// CHECKSTYLE:ON
```

## Customizing Rules

### Modifying Existing Rules

Edit `config/checkstyle/checkstyle.xml`:

```xml
<!-- Increase line length limit -->
<module name="LineLength">
    <property name="max" value="140"/>
    <property name="ignorePattern" value="^package.*|^import.*"/>
</module>

<!-- Customize method length -->
<module name="MethodLength">
    <property name="max" value="100"/>
    <property name="countEmpty" value="false"/>
</module>
```

### Adding New Rules

```xml
<!-- Add missing switch default check -->
<module name="MissingSwitchDefault"/>

<!-- Add empty catch block check -->
<module name="EmptyCatchBlock">
    <property name="exceptionVariableName" value="expected|ignore"/>
</module>
```

### Severity Levels

```xml
<!-- Change severity of specific checks -->
<module name="UnusedImports">
    <property name="severity" value="warning"/>
</module>

<module name="LineLength">
    <property name="severity" value="error"/>
</module>
```

## Best Practices

### 1. Regular Checking

- Run Checkstyle before committing code
- Set up pre-commit hooks to run Checkstyle automatically
- Include Checkstyle in CI/CD pipeline

### 2. Team Consistency

- Ensure all team members use the same configuration
- Document any project-specific style decisions
- Regular code reviews to catch style issues

### 3. Gradual Implementation

- Start with basic rules and gradually add more
- Fix existing violations incrementally
- Focus on critical violations first

### 4. Configuration Management

- Keep configuration files in version control
- Document any customizations
- Review and update rules periodically

## Troubleshooting

### Common Issues

1. **"Configuration file not found"**
   ```bash
   # Solution: Check file path in build.gradle
   checkstyle {
       configFile = file("config/checkstyle/checkstyle.xml")
   }
   ```

2. **"Too many violations"**
   ```bash
   # Temporary solution: Increase limits
   checkstyle {
       maxWarnings = 100
       maxErrors = 50
   }
   ```

3. **"False positives"**
   - Add specific suppressions
   - Review and adjust rule configuration
   - Consider if the rule is appropriate for your project

### Performance Issues

If Checkstyle is slow:
- Exclude unnecessary file patterns
- Use suppressions instead of disabling rules entirely
- Consider running only on changed files in CI

### Integration Issues

For CI/CD integration:
```bash
# Fail build on checkstyle violations
./gradlew check -PcheckstyleFailOnError=true

# Generate reports without failing build
./gradlew checkstyleMain checkstyleTest -PcheckstyleIgnoreFailures=true
```

## Continuous Improvement

### Metrics Tracking

Monitor checkstyle metrics over time:
- Number of violations per build
- Most common violation types
- Files with most violations

### Regular Reviews

- Monthly review of checkstyle configuration
- Assess if rules are helping code quality
- Update rules based on team feedback

### Tool Evolution

- Keep Checkstyle version updated
- Review new rules in updates
- Adapt configuration as project grows

This guide should help you effectively use Checkstyle to maintain high code quality standards in the AREA Backend project.