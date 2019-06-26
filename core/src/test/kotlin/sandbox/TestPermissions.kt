package edu.illinois.cs.cs125.jeed.core.sandbox

import edu.illinois.cs.cs125.jeed.core.*
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNot
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.IllegalArgumentException
import java.util.*

class TestPermissions : StringSpec({
    "should prevent threads from populating a new thread group" {
        val executionResult = Source.fromSnippet("""
public class Example implements Runnable {
    public void run() {
        System.out.println("Here");
        System.exit(1);
    }
}
ThreadGroup threadGroup = new ThreadGroup("test");
Thread thread = new Thread(new ThreadGroup("test"), new Example());
thread.start();
try {
    thread.join();
} catch (Exception e) { }
System.out.println("There");
        """.trim()).compile().execute(SourceExecutionArguments(maxExtraThreads = 7))

        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "should prevent snippets from exiting" {
        val executionResult = Source.fromSnippet("""
System.exit(2);
        """.trim()).compile().execute()

        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "should prevent snippets from redirecting System.out" {
        val executionResult = Source.fromSnippet("""
import java.io.*;

ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
PrintStream printStream = new PrintStream(byteArrayOutputStream);
System.setOut(printStream);
        """.trim()).compile().execute()

        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "should prevent trusted task code from redirecting System.out" {
        val executionResult = Sandbox.execute<Any> {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val printStream = PrintStream(byteArrayOutputStream)
            System.setOut(printStream)
        }

        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "should prevent snippets from reading files" {
        val executionResult = Source.fromSnippet("""
import java.io.*;
System.out.println(new File("/").listFiles().length);
        """.trim()).compile().execute()

        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "should prevent snippets from reading system properties" {
        val executionResult = Source.fromSnippet("""
System.out.println(System.getProperty("file.separator"));
        """.trim()).compile().execute()

        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "should allow snippets to read system properties if allowed" {
        val executionResult = Source.fromSnippet("""
System.out.println(System.getProperty("file.separator"));
        """.trim()).compile().execute(SourceExecutionArguments(permissions=listOf(PropertyPermission("*", "read"))))

        executionResult should haveCompleted()
        executionResult.permissionDenied shouldBe false
    }
    "should allow permissions to be changed between runs" {
        val compiledSource = Source.fromSnippet("""
System.out.println(System.getProperty("file.separator"));
        """.trim()).compile()

        val failedExecution = compiledSource.execute()
        failedExecution shouldNot haveCompleted()
        failedExecution.permissionDenied shouldBe true

        val successfulExecution = compiledSource.execute(
                SourceExecutionArguments(permissions=listOf(PropertyPermission("*", "read"))
                ))
        successfulExecution should haveCompleted()
        successfulExecution.permissionDenied shouldBe false
    }
    "should prevent snippets from starting threads by default" {
        val executionResult = Source.fromSnippet("""
public class Example implements Runnable {
    public void run() { }
}
Thread thread = new Thread(new Example());
thread.start();
System.out.println("Started");
        """.trim()).compile().execute()

        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "should allow snippets to start threads when configured" {
        val compiledSource = Source.fromSnippet("""
public class Example implements Runnable {
    public void run() {
        System.out.println("Ended");
    }
}
Thread thread = new Thread(new Example());
System.out.println("Started");
thread.start();
try {
    thread.join();
} catch (Exception e) {
    System.out.println(e);
}
        """.trim()).compile()

        val failedExecutionResult = compiledSource.execute()
        failedExecutionResult shouldNot haveCompleted()
        failedExecutionResult.permissionDenied shouldBe true

        val successfulExecutionResult = compiledSource.execute(SourceExecutionArguments(maxExtraThreads=1))
        successfulExecutionResult.permissionDenied shouldBe false
        successfulExecutionResult should haveCompleted()
        successfulExecutionResult should haveOutput("Started\nEnded")
    }
    "should not allow unsafe permissions to be provided" {
        shouldThrow<IllegalArgumentException> {
            Source.fromSnippet("""
System.exit(3);
            """.trim()).compile().execute(
                    SourceExecutionArguments(permissions=listOf(RuntimePermission("exitVM")))
            )
        }
    }
    "should allow Java streams with default permissions" {
        val executionResult = Source.fromSnippet("""
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

List<String> strings = new ArrayList<>(Arrays.asList(new String[] { "test", "me", "another" }));
strings.stream()
    .filter(string -> string.length() <= 4)
    .map(String::toUpperCase)
    .sorted()
    .forEach(System.out::println);
        """.trim()).compile().execute()

        executionResult should haveCompleted()
        executionResult should haveOutput("ME\nTEST")
    }
    "should allow generic methods with the default permissions" {
        val executionResult = Source(mapOf(
                "A" to """
public class A implements Comparable<A> {
    public int compareTo(A other) {
        return 0;
    }
}
                """.trim(),
                "Main" to """
public class Main {
    public static <T extends Comparable<T>> int test(T[] values) {
        return 8;
    }
    public static void main() {
        System.out.println(test(new A[] { }));
    }
}
        """.trim())).compile().execute()

        executionResult should haveCompleted()
        executionResult should haveOutput("8")
    }
    "it should not allow snippets to read from the internet" {
        val executionResult = Source.fromSnippet("""
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

BufferedReader br = null;
URL url = new URL("http://cs125.cs.illinois.edu");
br = new BufferedReader(new InputStreamReader(url.openStream()));

String line;
StringBuilder sb = new StringBuilder();
while ((line = br.readLine()) != null) {
    sb.append(line);
    sb.append(System.lineSeparator());
}

System.out.println(sb);
if (br != null) {
    br.close();
}
        """.trim()).compile().execute()

        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "it should not allow snippets to execute commands" {
        val executionResult = Source.fromSnippet("""
import java.io.*;

Process p = Runtime.getRuntime().exec("/bin/sh ls");
BufferedReader in = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
String line = null;

while ((line = in.readLine()) != null) {
    System.out.println(line);
}
        """.trim()).compile().execute()

        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "should not allow SecurityManager to be set again through reflection" {
        val executionResult = Source.fromSnippet("""
Class<System> c = System.class;
System s = c.newInstance();
        """.trim()).compile().execute()

        executionResult shouldNot haveCompleted()
    }
    "should not allow SecurityManager to be created again through reflection" {
        val executionResult = Source.fromSnippet("""
Class<SecurityManager> c = SecurityManager.class;
SecurityManager s = c.newInstance();
        """.trim()).compile().execute()

        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "should not allow access to the compiler" {
        val executionResult = Source.fromSnippet("""
import java.lang.reflect.*;

Class<?> sourceClass = Class.forName("edu.illinois.cs.cs125.jeed.core.Source");
Field sourceCompanion = sourceClass.getField("Companion");
Class<?> snippetKtClass = Class.forName("edu.illinois.cs.cs125.jeed.core.SnippetKt");
Method fromSnippet = snippetKtClass.getMethod("fromSnippet", sourceCompanion.getType(), String.class, int.class);
Object snippet = fromSnippet.invoke(null, sourceCompanion.get(null), "System.out.println(403);", 4);
Class<?> snippetClass = snippet.getClass();
Class<?> compileArgsClass = Class.forName("edu.illinois.cs.cs125.jeed.core.CompilationArguments");
Method compile = Class.forName("edu.illinois.cs.cs125.jeed.core.CompileKt").getMethod("compile", sourceClass, compileArgsClass);
Object compileArgs = compileArgsClass.newInstance();
Object compiledSource = compile.invoke(null, snippet, compileArgs);
        """.trim()).compile().execute()

        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "should not allow reflection to disable sandboxing" {
        val executionResult = Source.fromSnippet("""
import java.lang.reflect.*;
import java.util.Map;

Class<?> sandboxClass = Class.forName("edu.illinois.cs.cs125.jeed.core.Sandbox");
Field field = sandboxClass.getDeclaredField("confinedTasks");
field.setAccessible(true);
Map confinedTasks = (Map) field.get(null);
        """.trim()).compile().execute()

        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "should not prevent trusted code from using reflection" {
        val executionResult = Sandbox.execute {
            val sandboxClass = Class.forName("edu.illinois.cs.cs125.jeed.core.Sandbox")
            val field = sandboxClass.getDeclaredField("confinedTasks")
            field.isAccessible = true
        }

        executionResult should haveCompleted()
    }
    "should not allow static{} to escape the sandbox" {
        val executionResult = Source(mapOf(
                "Example" to """
public class Example {
    static {
        System.out.println("Static initializer");
        System.exit(-1);
    }
    public static void main() {
        System.out.println("Main");
    }
}
        """)).compile().execute(SourceExecutionArguments("Example"))

        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
})
