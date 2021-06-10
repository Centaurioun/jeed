package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TestFeatures : StringSpec({
    "should count variable declarations in snippets" {
        Source.fromSnippet(
            """
int i = 0;
int j;
i = 4;
i++;
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.LOCAL_VARIABLE_DECLARATIONS] shouldBe 2
            it.lookup(".").features.featureMap[FeatureName.VARIABLE_ASSIGNMENTS] shouldBe 2
            it.lookup(".").features.featureMap[FeatureName.VARIABLE_REASSIGNMENTS] shouldBe 1
            it.lookup("").features.featureMap[FeatureName.METHOD] shouldBe 1
        }
    }
    "should count for loops in snippets" {
        Source.fromSnippet(
            """
for (int i = 0; i < 10; i++) {
    System.out.println(i);
}
int[] arr = new int[10];
for (int num : arr) {
    num++;
}
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.FOR_LOOPS] shouldBe 2
            it.lookup(".").features.featureMap[FeatureName.VARIABLE_ASSIGNMENTS] shouldBe 2
            it.lookup(".").features.featureMap[FeatureName.LOCAL_VARIABLE_DECLARATIONS] shouldBe 3
            it.lookup(".").features.featureMap[FeatureName.ENHANCED_FOR] shouldBe 1
        }
    }
    "should count nested for loops in snippets" {
        Source.fromSnippet(
            """
for (int i = 0; i < 10; i++) {
    for (int j = 0; j < 10; j++) {
        System.out.println(i + j);
    }
}
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.FOR_LOOPS] shouldBe 2
            it.lookup(".").features.featureMap[FeatureName.NESTED_FOR] shouldBe 1
        }
    }
    "should count while loops in snippets" {
        Source.fromSnippet(
            """
int i = 0;
while (i < 10) {
    while (j < 10) {
        j++;
    }
    i++;
}
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.WHILE_LOOPS] shouldBe 2
            it.lookup(".").features.featureMap[FeatureName.NESTED_WHILE] shouldBe 1
            it.lookup(".").features.featureMap[FeatureName.DO_WHILE_LOOPS] shouldBe 0
        }
    }
    "should count do-while loops in snippets" {
        Source.fromSnippet(
            """
int i = 0;
do {
    System.out.println(i);
    i++;
    
    int j = 0;
    do {
        j++;
    } while (j < 10);
} while (i < 10);
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.DO_WHILE_LOOPS] shouldBe 2
            it.lookup(".").features.featureMap[FeatureName.NESTED_DO_WHILE] shouldBe 1
            it.lookup(".").features.featureMap[FeatureName.WHILE_LOOPS] shouldBe 0
        }
    }
    "should count simple if-else statements in snippets" {
        Source.fromSnippet(
            """
int i = 0;
if (i < 5) {
    i++;
} else {
    i--;
}
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.IF_STATEMENTS] shouldBe 1
            it.lookup(".").features.featureMap[FeatureName.ELSE_STATEMENTS] shouldBe 1
        }
    }
    "should count a chain of if-else statements in snippets" {
        Source.fromSnippet(
            """
int i = 0;
if (i < 5) {
    i++;
} else if (i < 10) {
    i--;
} else if (i < 15) {
    i++;
} else {
    i--;
}
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.IF_STATEMENTS] shouldBe 1
            it.lookup(".").features.featureMap[FeatureName.ELSE_STATEMENTS] shouldBe 1
            it.lookup(".").features.featureMap[FeatureName.ELSE_IF] shouldBe 2
        }
    }
    "should count nested if statements in snippets" {
        Source.fromSnippet(
            """
int i = 0;
if (i < 15) {
    if (i < 10) {
        i--;
        if (i < 5) {
            i++;
        }
    }
    if (i > 10) {
        i--;
    }
}
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.IF_STATEMENTS] shouldBe 4
            it.lookup(".").features.featureMap[FeatureName.NESTED_IF] shouldBe 3
        }
    }
    "should count conditional expressions and complex conditionals in snippets" {
        Source.fromSnippet(
            """
int i = 0;
if (i < 5 || i > 15) {
    if (i < 0) {
        i--;
    }
} else if (i > 5 && i < 15) {
    i++;
} else {
    i--;
}
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.CONDITIONAL] shouldBe 5
            it.lookup(".").features.featureMap[FeatureName.COMPLEX_CONDITIONAL] shouldBe 2
        }
    }
    "should count try blocks, switch statements, finally blocks, and assertions in snippets" {
        Source.fromSnippet(
            """
int i = 0;
try {
    assert i > -1;
    switch(i) {
        case 0:
            System.out.println("zero");
            break;
        case 1:
            System.out.println("one");
            break;
        default:
            System.out.println("not zero or one");
    }
} catch (Exception e) {
    System.out.println("Oops");
} finally { }
        
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.TRY_BLOCK] shouldBe 1
            it.lookup(".").features.featureMap[FeatureName.ASSERT] shouldBe 1
            it.lookup(".").features.featureMap[FeatureName.SWITCH] shouldBe 1
            it.lookup(".").features.featureMap[FeatureName.FINALLY] shouldBe 1
        }
    }
    "should count operators in snippets" {
        Source.fromSnippet(
            """
int i = 0;
int j = 0;
if (i < 5) {
    i += 5;
    j = i - 1;
} else if (i < 10) {
    i++;
    j = j & i;
} else if (i < 15) {
    i--;
    j = j % i;
} else {
    i -= 5;
    j = i < j ? i : j;
}
j = j << 2;
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.UNARY_OPERATORS] shouldBe 2
            it.lookup(".").features.featureMap[FeatureName.ARITHMETIC_OPERATORS] shouldBe 2
            it.lookup(".").features.featureMap[FeatureName.BITWISE_OPERATORS] shouldBe 2
            it.lookup(".").features.featureMap[FeatureName.ASSIGNMENT_OPERATORS] shouldBe 2
            it.lookup(".").features.featureMap[FeatureName.TERNARY_OPERATOR] shouldBe 1
        }
    }
    "should count the new keyword and array accesses in snippets" {
        Source.fromSnippet(
            """
int[] arr = new int[3];
arr[0 + 0] = 5;
arr[1] = 10;
arr[2] = arr[0] + arr[1];
int[] nums = {1, 2, 4};
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.NEW_KEYWORD] shouldBe 1
            it.lookup(".").features.featureMap[FeatureName.ARRAY_ACCESS] shouldBe 5
            it.lookup(".").features.featureMap[FeatureName.ARRAY_LITERAL] shouldBe 1
        }
    }
    "should count strings, streams, and null in snippets" {
        Source.fromSnippet(
            """
import java.util.stream.Stream;
String first = "Hello, world!";
String second = null;
Stream<String> stream;
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.STRING] shouldBe 2
            it.lookup(".").features.featureMap[FeatureName.NULL] shouldBe 1
            it.lookup(".").features.featureMap[FeatureName.STREAM] shouldBe 1
        }
    }
    "should count multidimensional arrays in snippets" {
        Source.fromSnippet(
            """
int[][] array = new int[5][5];
char[][] array1 = new char[10][10];
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.MULTIDIMENSIONAL_ARRAYS] shouldBe 2
        }
    }
    "should count use of type inference in snippets" {
        Source.fromSnippet(
            """
var first = 0;
var second = "Hello, world!";
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.TYPE_INFERENCE] shouldBe 2
        }
    }
    "should count methods and classes" {
        Source.fromSnippet(
            """
System.out.println("Hello, world!");
""".trim()
        ).features().also {
            it.lookup("").features.featureMap[FeatureName.METHOD] shouldBe 1
            it.lookup("").features.featureMap[FeatureName.CLASS] shouldBe 1
        }
    }
    "should count constructors, methods, getters, setters, visibility modifiers, and static methods in classes" {
        Source(
            mapOf(
                "Test.java" to """
public class Test {
    private int number;
    
    public Test(int setNumber) {
        number = setNumber;
    }
    
    void setNumber(int setNumber) {
        number = setNumber;
    }
    
    int getNumber() {
        return number;
    }
    
    static int add(int i, int j) {
        number = i + j;
        return number;
    }
    
    class InnerClass { }
    
}
""".trim()
            )
        ).features().also {
            it.lookup("", "Test.java").features.featureMap[FeatureName.CLASS] shouldBe 2
            it.lookup("Test", "Test.java").features.featureMap[FeatureName.CONSTRUCTOR] shouldBe 1
            it.lookup("Test", "Test.java").features.featureMap[FeatureName.METHOD] shouldBe 3
            it.lookup("Test", "Test.java").features.featureMap[FeatureName.GETTER] shouldBe 1
            it.lookup("Test", "Test.java").features.featureMap[FeatureName.SETTER] shouldBe 1
            it.lookup("Test", "Test.java").features.featureMap[FeatureName.STATIC_METHOD] shouldBe 1
            it.lookup("Test", "Test.java").features.featureMap[FeatureName.VISIBILITY_MODIFIERS] shouldBe 2
        }
    }
    "should count the extends keyword, the super constructor, and the 'this' keyword in classes" {
        Source.fromSnippet(
            """
public class Person {
    private int age;
    public Person(int setAge) {
        this.age = setAge;
    }
}
public class Student extends Person {
    private String school;
    public Student(int setAge, String setSchool) {
        super(setAge);
        this.school = setSchool;
    }
}
""".trim()
        ).features().also {
            it.lookup("Student").features.featureMap[FeatureName.EXTENDS] shouldBe 1
            it.lookup("Student").features.featureMap[FeatureName.SUPER] shouldBe 1
            it.lookup("Student").features.featureMap[FeatureName.THIS] shouldBe 1
        }
    }
    "should count instanceof and casting" {
        Source.fromSnippet(
            """
double temperature = 72.5;
String name = "Geoff";
if (name instanceof String) {
    int rounded = (int) temperature;
}
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.INSTANCEOF] shouldBe 1
            it.lookup(".").features.featureMap[FeatureName.CASTING] shouldBe 1
        }
    }
    "should count override annotation and import statements" {
        Source(
            mapOf(
                "Test.java" to """
import java.util.Random;

public class Test {
    private int number;
    
    public Test(int setNumber) {
        number = setNumber;
    }
    
    @Override
    String toString() {
        return "String";
    }
}
""".trim()
            )
        ).features().also {
            it.lookup("Test", "Test.java").features.featureMap[FeatureName.OVERRIDE] shouldBe 1
            it.lookup("", "Test.java").features.featureMap[FeatureName.IMPORT] shouldBe 1
        }
    }
    "should count reference equality" {
        Source.fromSnippet(
            """
String first = "Hello";
String second = "World";
boolean third = first == second;
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.REFERENCE_EQUALITY] shouldBe 1
        }
    }
    "should count interfaces and classes that implement interfaces" {
        Source.fromSnippet(
            """
public interface Test {
    int add(int x, int y);
    int subtract(int x, int y);
}

public class Calculator implements Test {
    int add(int x, int y) {
        return x + y;
    }
    
    int subtract(int x, int y) {
        return x - y;
    }
}
""".trim()
        ).features().also {
            it.lookup("Test").features.featureMap[FeatureName.INTERFACE] shouldBe 1
            // it.lookup("Test").features.featureMap[FeatureName.METHOD] shouldBe 2
            it.lookup("Calculator").features.featureMap[FeatureName.IMPLEMENTS] shouldBe 1
        }
    }
    "should count final and abstract methods" {
        Source.fromSnippet(
            """
public abstract class Test {
    abstract int add(int x, int y);
    abstract int subtract(int x, int y);
}
public class Calculator implements Test {
    final int count;
    
    final int add(int x, int y) {
        return x + y;
    }
    
    int subtract(int x, int y) {
        return x - y;
    }
}
""".trim()
        ).features().also {
            it.lookup("Test").features.featureMap[FeatureName.ABSTRACT_METHOD] shouldBe 2
            it.lookup("Calculator").features.featureMap[FeatureName.FINAL_METHOD] shouldBe 1
        }
    }
    "should count anonymous classes" {
        Source.fromSnippet(
            """
public class Person {
  public String getType() {
    return "Person";
  }
}
Person student = new Person() {
  @Override
  public String getType() {
    return "Student";
  }
};
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.ANONYMOUS_CLASSES] shouldBe 1
        }
    }
    "should count lambda expressions" {
        Source.fromSnippet(
            """
interface Modify {
  int modify(int value);
}

Modify first = (value) -> value + 1;
Modify second = (value) -> value - 10;
""".trim()
        ).features().also {
            it.lookup(".").features.featureMap[FeatureName.LAMBDA_EXPRESSIONS] shouldBe 2
        }
    }
    "should count throwing exceptions" {
        Source.fromSnippet(
            """
void container(int setSize) throws IllegalArgumentException {
    if (setSize <= 0) {
      throw new IllegalArgumentException("Container size must be positive");
    }
    values = new int[setSize];
}
""".trim()
        ).features().also {
            it.lookup("").features.featureMap[FeatureName.THROW] shouldBe 1
            it.lookup("").features.featureMap[FeatureName.THROWS] shouldBe 2 // main method for snippets also throws exception
        }
    }
    "should count generic classes" {
        Source(
            mapOf(
                "Counter.java" to """
public class Counter<T> {
  private T value;
  private int count;
  public Counter(T setValue) {
    if (setValue == null) {
      throw new IllegalArgumentException();
    }
    value = setValue;
    count = 0;
  }
  public void add(T newValue) {
    if (value.equals(newValue)) {
      count++;
    }
  }
  public int getCount() {
    return count;
  }
}
""".trim()
            )
        ).features().also {
            it.lookup("Counter", "Counter.java").features.featureMap[FeatureName.GENERIC_CLASS] shouldBe 1
        }
    }
    "should count classes declared inside methods" {
        Source(
            mapOf(
                "Test.java" to """
public class Test {
    private int number;
    
    public Test(int setNumber) {
        number = setNumber;
    }
    
    void makeClass() {
        class Class { }
    }
}
""".trim()
            )
        ).features().also {
            it.lookup("", "Test.java").features.featureMap[FeatureName.CLASS] shouldBe 2
        }
    }
    "should correctly create a code skeleton for snippets" {
        Source.fromSnippet(
            """
int i = 0;
if (i < 15) {
    for (int j = 0; j < 10; j++) {
        i--;
        if (i < 5) {
            i++;
        } else {
            i--;
        }
    }
    while (i > 10) {
        i--;
    }
} else {
    System.out.println("Hello, world!");
}
""".trim()
        ).features().also {
            it.lookup("").features.skeleton.trim() shouldBe "if { for { if else } while } else"
        }
    }
    "should count final classes" {
        Source(
            mapOf(
                "Test.java" to """
public final class Test {
    private int number;
    
    public Test(int setNumber) {
        number = setNumber;
    }
    
    public final class First { }
    public abstract class AbstractFirst { }
    
    public void makeClass() {
        public final class Second { }
        public abstract class AbstractSecond { }
    }
}
""".trim()
            )
        ).features().also {
            it.lookup("", "Test.java").features.featureMap[FeatureName.FINAL_CLASS] shouldBe 3
            it.lookup("", "Test.java").features.featureMap[FeatureName.ABSTRACT_CLASS] shouldBe 2
        }
    }
})
