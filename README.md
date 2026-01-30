# JavaObfuscator

Java bytecode obfuscator with multiple transformation techniques.

## Features

- **String Encryption** - Encrypts string constants with XOR-based encryption
- **Number Obfuscation** - Obfuscates integer, long, float, and double constants
- **Boolean Obfuscation** - Transforms boolean values with complex expressions
- **Flow Obfuscation** - Creates complex control flow patterns
- **Dispatcher** - Converts methods to use dispatcher pattern
- **Shuffle** - Randomizes order of fields and methods

## Building

```bash
./gradlew build
```

The output JAR will be in `build/libs/JavaObfuscator-1.0-SNAPSHOT.jar`

## Usage

```bash
java -jar JavaObfuscator.jar <input.jar> <output.jar> [options]
```

### Options

- `--names-length <n>` - Name length for generated identifiers (default: 40)
- `--numbers` - Enable number obfuscation
- `--strings` - Enable string encryption
- `--booleans` - Enable boolean obfuscation
- `--flow` - Enable flow obfuscation
- `--dispatcher` - Enable dispatcher transformation
- `--shuffle` - Enable shuffle transformation
- `--zip-comment` - Add ZIP comment to output JAR
- `--zip-comment-text <text>` - Custom ZIP comment text

If no transformer options are specified, all transformers are enabled by default.

### Examples

Enable all transformers (default):
```bash
java -jar JavaObfuscator.jar input.jar output.jar
```

Enable specific transformers:
```bash
java -jar JavaObfuscator.jar input.jar output.jar --strings --numbers --flow
```

Custom name length:
```bash
java -jar JavaObfuscator.jar input.jar output.jar --names-length 60
```

## Requirements

- Java 21 or higher

## Author

by @core2k21 (tg)
