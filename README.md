# Mem4J (Memory Manipulation Library)

Mem4J is a powerful and flexible library that leverages the capabilities of JNA (**Java Native Access**) to interact with native operating system libraries, enabling secure and controlled access to process memory. With Mem4J, developers can read and write data directly in memory, opening up new possibilities for high-performance Java application development and tackling complex programming challenges.

## Features

- Read Pid and BaseAddress From Process 
- Write By Offsets Array Or Long Value
- Read Address By Signatures
- Read and Write From PTR

## Installation
```xml
    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
      <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

<plugins>
    <plugin>
        <groupId>com.github.christopherproject</groupId>
        <artifactId>Mem4J</artifactId>
        <!-- Use the latest released version:
        https://repo1.maven.org/maven2/com/github/christopherproject/Mem4J/ -->
        <version>LATEST_VERSION</version>
        ...
    </plugin>
...
```

## Credits

- Princekin (He Helped me to know what jna is)
- Foiks (He Helped me and give me some self-confidence for do this)
- Backq (He Helped me to know memory, offsets and signatures)
