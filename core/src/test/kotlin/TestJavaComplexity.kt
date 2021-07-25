package edu.illinois.cs.cs125.jeed.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TestJavaComplexity : StringSpec({
    "should calculate complexity for snippets" {
        Source.fromSnippet(
            """
int add(int i, int j) {
    return i + j;
}
int i = 0;
""".trim()
        ).complexity().also {
            it.lookup("").complexity shouldBe 2
        }
    }
    "should calculate complexity for sources" {
        Source(
            mapOf(
                "Test.java" to """
public class Test {
    public Test(int first, double second) {
        if (first > 0) {
            System.out.println("Here");
        }
    }
    int add(int i, int j) {
        return i + j;
    }
}
""".trim()
            )
        ).complexity().also {
            it.lookup("Test.int add(int,int)", "Test.java").complexity shouldBe 1
            it.lookup("Test.Test(int,double)", "Test.java").complexity shouldBe 2
        }
    }
    "should fail properly on parse errors" {
        shouldThrow<ComplexityFailed> {
            Source(
                mapOf(
                    "Test.java" to """
public class Test
    int add(int i, int j) {
        return i + j;
    }
}
""".trim()
                )
            ).complexity()
        }
    }
    "should calculate complexity for simple conditional statements" {
        Source(
            mapOf(
                "Test.java" to """
public class Test {
    int chooser(int i, int j) {
        if (i > j) {
            return i;
        } else {
            return i;
        }
    }
}
""".trim()
            )
        ).complexity().also {
            it.lookup("Test.int chooser(int,int)", "Test.java").complexity shouldBe 2
        }
    }
    "should calculate complexity for complex conditional statements" {
        Source(
            mapOf(
                "Test.java" to """
public class Test {
    int chooser(int i, int j) {
        if (i > j) {
            return i;
        } else if (i < j) {
            return j;
        } else if (i == j) {
            return i + j;
        } else {
            return i;
        }
    }
}
""".trim()
            )
        ).complexity().also {
            it.lookup("Test.int chooser(int,int)", "Test.java").complexity shouldBe 4
        }
    }
    "should calculate complexity for old switch statements" {
        Source(
            mapOf(
                "Test.java" to """
public class Test {
    int chooser(int i) {
        int j = 0;
        switch (i) {
          case 0: j = 1;
          case 1: j = 2;
          case 2: j = 3;
          default: j = 0;
        }
    }
}
""".trim()
            )
        ).complexity().also {
            it.lookup("Test.int chooser(int)", "Test.java").complexity shouldBe 4
        }
    }
    "should calculate complexity for new switch statements" {
        Source(
            mapOf(
                "Test.java" to """
public class Test {
    int chooser(int i) {
        int j = 0;
        switch (i) {
          case 0 -> j = 1;
          case 1 -> j = 2;
          case 2 -> j = 3;
          default -> {
            if (i > 0) {
              j = 0;
            }
          }
        }
    }
}
""".trim()
            )
        ).complexity().also {
            it.lookup("Test.int chooser(int)", "Test.java").complexity shouldBe 5
        }
    }
    "should calculate complexity for classes in snippets" {
        Source.fromSnippet(
            """
class Example {
  int value = 0;
}
            """.trim()
        ).complexity().also {
            it.lookup("Example", "").complexity shouldBe 0
        }
    }
    "should not fail on records in snippets" {
        Source.fromSnippet(
            """
record Example(int value) { };
            """.trim()
        ).complexity().also {
            it.lookup("Example", "").complexity shouldBe 0
        }
    }
    "should not fail on records with contents" {
        Source.fromSnippet(
            """
record Example(int value) {
  public int it() {
    return value;
  }
};
            """.trim()
        ).complexity().also {
            it.lookup("Example", "").complexity shouldBe 1
            it.lookup("Example.int it()", "").complexity shouldBe 1
        }
    }
    "should not fail on interfaces" {
        Source.fromSnippet(
            """
interface Simple {
  int simple(int first);
}
            """.trim()
        ).complexity().also {
            it.lookup("Simple", "").complexity shouldBe 0
        }
    }
    "should not fail on anonymous classes" {
        Source.fromSnippet(
            """
interface Test {
  void test();
}
Test test = new Test() {
  @Override
  public void test() { }
};
            """.trim()
        ).complexity().also {
            it.lookup(".", "").complexity shouldBe 2
        }
    }
    "should not fail on generic methods" {
        Source.fromSnippet(
            """
<T> T max(T[] array) {
  return null;
}
            """.trim()
        ).complexity().also {
            it.lookup(".T max(T[])", "").complexity shouldBe 1
        }
    }
    "should not fail on lambda expressions with body" {
        Source.fromSnippet(
            """
Thread thread = new Thread(() -> {
  System.out.println("Blah");
});
            """.trim()
        ).complexity().also {
            it.lookup(".", "").complexity shouldBe 2
        }
    }
    "should not fail on lambda expressions without body" {
        Source.fromSnippet(
            """
interface Modify {
  int modify(int value);
}
Modify first = (v) -> v + 1;
System.out.println(first.getClass());
            """.trim()
        ).complexity().also {
            it.lookup(".", "").complexity shouldBe 2
        }
    }
    "should not fail on class declarations" {
        Source.fromSnippet(
            """
public class Example {
  public static void main() {
    int[] array = new int[] {1, 2, 4};
    System.out.println("ran");
  }
}""".trim()
        ).complexity().also {
            it.lookup("Example", "").complexity shouldBe 1
        }
    }
    "should parse empty constructors properly" {
        Source.fromSnippet(
            """
public class Example {
  public Example() { }
}""".trim()
        ).complexity().also {
            it.lookup("Example", "").complexity shouldBe 1
        }
    }
    "should not fail on multiple anonymous objects" {
        Source.fromSnippet(
            """
interface Filter {
  boolean accept(int first, int second);
}
Filter bothPositive = new Filter() {
  @Override
  public boolean accept(int first, int second) {
    return first > 0 && second > 0;
  }
};
Filter bothNegative = new Filter() {
  @Override
  public boolean accept(int first, int second) {
    return first < 0 && second < 0;
  }
};
""".trim()
        ).complexity().also {
            it.lookup(".", "").complexity shouldBe 5
        }
    }
    "should not fail on class declarations with initializer blocks" {
        Source.fromSnippet(
            """
public class Example {
  {
    System.out.println("Instance initializer");
  }
}""".trim()
        ).complexity()
    }
    "should not fail on class declarations with static initializer blocks" {
        Source.fromSnippet(
            """
public class Example {
  static {
    System.out.println("Static initializer");
  }
}""".trim()
        ).complexity()
    }
})
