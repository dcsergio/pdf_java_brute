# PDF Password Cracker User Guide

## Prerequisites
- Java 21 or later versions (JDK required for compilation)
- Source code or `app.jar` file (main application)
- Password-protected PDF file
- Maven or Gradle (if building from source)

## Installation and Configuration

### 1. Option A: Using Pre-compiled JAR
If you already have the `app.jar` file:
- Create a new folder on your desktop or in an easily accessible location
- Copy the `app.jar` file into the created folder
- Copy the PDF file to be unlocked into the same folder
- Skip to step 3 (Java Verification)

### 1. Option B: Compiling from Source Code

#### Setting up the Development Environment
1. Ensure you have Java Development Kit (JDK) 21 installed
2. Install Gradle build tool
3. Download or clone the source code

#### Compilation Steps

```bash
# Navigate to the project directory
cd pdf-password-cracker

# Clean and build the project
gradle clean build

# The JAR file will be created in build/libs/
```

Note: a gradle wrapper are supplied, too.
For Windows: `gradlew.bat clean install`
For Linux/Mac: `./gradlew clean install`

**Manual Compilation (if no build tool):**
```bash
# Compile all Java files
javac -cp "lib/*" -d build/classes src/**/*.java

# Create JAR file
jar cf app.jar -C build/classes .
```

#### Post-Compilation Setup
- Locate the generated `app.jar` file (usually on `build/libs/` for Gradle)
- Copy the JAR file to your working directory
- Copy the PDF file to be unlocked into the same folder

### 2. Environment Setup

#### Java Version Check
1. Open terminal in the folder (right-click → "Open in Terminal")
2. Execute the command:
   ```bash
   java --version
   ```

#### Java 21 Installation (if needed)
If Java 21 is not installed:
1. Download Java 21 from: [Amazon Corretto 21](https://corretto.aws/downloads/latest/amazon-corretto-21-x64-windows-jdk.msi)
2. Install following the setup wizard
3. **Important**: Close and reopen the terminal after installation

## Application Usage

### 4. Program Launch
Execute the basic command to display help:
```bash
java -jar app.jar
```

### 5. Parameter Configuration

#### Required Parameters
- `--input`: Input PDF file name
- `--output`: Output PDF file name (unlocked)

#### Customization Parameters
- `--chars`: Character set to use for decryption attempts
- `--min`: Minimum number of characters to try (excluding prefixes and suffixes)
- `--max`: Maximum number of characters to try (excluding prefixes and suffixes)

#### Optional Configuration Files
- `prefix.txt`: List of words to prepend to the variable part of the password
- `suffix.txt`: List of suffixes to append to the end of the variable part

### 6. Practical Example
```bash
java -jar app.jar --input document.pdf --output document_unlocked.pdf --chars abcdefghijklmnopqrstuvwxyz0123456789 --min 1 --max 6
```

## Optimization Tips

### Character Selection
- **Numbers only**: `0123456789`
- **Lowercase letters only**: `abcdefghijklmnopqrstuvwxyz`
- **Full alphanumeric**: `abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789`
- **With special characters**: `abcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()`

### Length Configuration
- Start with limited ranges (e.g., `--min 1 --max 4`)
- Gradually increase if necessary
- Keep in mind that each additional character exponentially increases processing time

## Important Notes
- The process may take a long time depending on password complexity
- Use only on files you own or have explicit authorization for
- The program will automatically stop when it finds the correct password

## Common Troubleshooting

### Compilation Errors
- Ensure JDK 21 is installed (not just JRE)
- Check that all dependencies are available in the classpath
- Verify source code compatibility with Java 21
- Run `gradle dependencies` to check dependency tree

### Java Runtime Errors
- Verify that Java 21 is correctly installed
- Restart the terminal after installation
- Check that the `app.jar` file is in the current directory

### Performance Issues
- Reduce the character range if the process is too slow
- Use known prefixes and suffixes to reduce combinations to test
- Monitor CPU usage during execution

### File Access Issues
- Ensure the PDF file is not open in another application
- Check file permissions for both input and output files
- Verify that the file paths are correct

## Advanced Usage

### Using Prefix and Suffix Files
Create text files with common password patterns:

**prefix.txt example:**
```
password
123
admin
user
```

**suffix.txt example:**
```
!
123
2024
@gmail.com
```

### Batch Processing
For multiple files, create a batch script or use shell loops:
```bash
for file in *.pdf; do
    java -jar app.jar --input "$file" --output "unlocked_$file" --chars 0123456789 --min 4 --max 8
done
```

## Security and Legal Considerations
- This tool should only be used on PDF files you own or have explicit permission to access
- Password cracking without authorization may violate local laws
- Always ensure you have legal rights to the content you're attempting to unlock

---

*This guide is provided solely for educational purposes and for recovering forgotten passwords on files you own.*
