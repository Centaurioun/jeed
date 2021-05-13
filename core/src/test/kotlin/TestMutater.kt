package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import kotlin.random.Random

class TestMutater : StringSpec({
    "it should find boolean literals to mutate" {
        Source.fromJava(
            """
public class Example {
  public static void example() {
    boolean first = true;
    boolean second = false;
  }
}"""
        ).checkMutations<BooleanLiteral> { mutations, contents ->
            mutations shouldHaveSize 2
            mutations[0].check(contents, "true", "false")
            mutations[1].check(contents, "false", "true")
        }
    }
    "it should find char literals to mutate" {
        Source.fromJava(
            """
public class Example {
  public static void example() {
    char first = 'a';
    char second = '!';
  }
}"""
        ).checkMutations<CharLiteral> { mutations, contents ->
            mutations shouldHaveSize 2
            mutations[0].check(contents, "'a'")
            mutations[1].check(contents, "'!'")
        }
    }
    "it should find string literals to mutate" {
        Source.fromJava(
            """
public class Example {
  public static void example() {
    System.out.println("Hello, world!");
    String s = "";
  }
}"""
        ).checkMutations<StringLiteral> { mutations, contents ->
            mutations shouldHaveSize 2
            mutations[0].check(contents, "\"Hello, world!\"").also {
                it shouldMatch ".*println\\(\".*".toRegex(RegexOption.DOT_MATCHES_ALL)
            }
            mutations[1].check(contents, "\"\"")
        }
    }
    "it should find number literals to mutate" {
        Source.fromJava(
            """
public class Example {
  public static void example() {
    System.out.println(1234);
    float f = 1.01f;
  }
}"""
        ).checkMutations<NumberLiteral> { mutations, contents ->
            mutations shouldHaveSize 2
            mutations[0].check(contents, "1234")
            mutations[1].check(contents, "1.01f")
        }
    }
    "it should find increments and decrements to mutate" {
        Source.fromJava(
            """
public class Example {
  public static void example() {
    int i = 0;
    int j = 1;
    i++;
    --j;
  }
}"""
        ).checkMutations<IncrementDecrement> { mutations, contents ->
            mutations shouldHaveSize 2
            mutations[0].check(contents, "++", "--")
            mutations[1].check(contents, "--", "++")
        }
    }
    "it should find negatives to invert" {
        Source.fromJava(
            """
public class Example {
  public static void example() {
    int i = 0;
    int j = -1;
    int k = -j;
  }
}"""
        ).checkMutations<InvertNegation> { mutations, contents ->
            mutations shouldHaveSize 2
            mutations[0].check(contents, "-", "")
            mutations[1].check(contents, "-", "")
        }
    }
    "it should find math to mutate" {
        Source.fromJava(
            """
public class Example {
  public static void example() {
    int i = 0;
    int j = 1;
    int k = i + j;
    k = i - j;
    k = i * j;
    k = i / j;
    int l = i % 10;
    l = i & j;
    l = j | i;
    l = j ^ i;
    l = i << 2;
    l = i >> 2;
    k = i >>> j;
  }
}"""
        ).checkMutations<MutateMath> { mutations, contents ->
            mutations shouldHaveSize 10
            mutations[0].check(contents, "-", "+")
            mutations[1].check(contents, "*", "/")
            mutations[2].check(contents, "/", "*")
            mutations[3].check(contents, "%", "*")
            mutations[4].check(contents, "&", "|")
            mutations[5].check(contents, "|", "&")
            mutations[6].check(contents, "^", "&")
            mutations[7].check(contents, "<<", ">>")
            mutations[8].check(contents, ">>", "<<")
            mutations[9].check(contents, ">>>", "<<")
        }
    }
    "it should find conditional boundaries to mutate" {
        Source.fromJava(
            """
public class Example {
  public static void example() {
    int i = 0;
    if (i < 10) {
      System.out.println("Here");
    } else if (i >= 20) {
      System.out.println("There");
    }
  }
}"""
        ).checkMutations<ConditionalBoundary> { mutations, contents ->
            mutations shouldHaveSize 2
            mutations[0].check(contents, "<", "<=")
            mutations[1].check(contents, ">=", ">")
        }
    }
    "it should find conditionals to negate" {
        Source.fromJava(
            """
public class Example {
  public static void example() {
    int i = 0;
    if (i < 10) {
      System.out.println("Here");
    } else if (i >= 20) {
      System.out.println("There");
    } else if (i == 10) {
      System.out.println("Again");
    }
  }
}"""
        ).checkMutations<NegateConditional> { mutations, contents ->
            mutations shouldHaveSize 3
            mutations[0].check(contents, "<", ">=")
            mutations[1].check(contents, ">=", "<")
            mutations[2].check(contents, "==", "!=")
        }
    }
    "it should find primitive returns to mutate" {
        Source.fromJava(
            """
                    public class Example {
  public static void first() {}
  public static int second() {
    return 1;
  }
  public static char third() {
    return 'A';
  }
  public static boolean fourth() {
    return true;
  }
  public static int fifth() {
    return 0;
  }
  public static long sixth() {
    return 0L;
  }
  public static double seventh() {
    return 0.0;
  }
  public static double eighth() {
    return 0.0f;
  }
}"""
        ).checkMutations<PrimitiveReturn> { mutations, contents ->
            mutations shouldHaveSize 2
            mutations[0].check(contents, "1", "0")
            mutations[1].check(contents, "'A'", "0")
        }
    }
    "it should find true returns to mutate" {
        Source.fromJava(
            """
public class Example {
  public static void first() {}
  public static boolean second() {
    it = false;
    return it;
  }
  public static boolean third() {
    return false;
  }
  public static boolean fourth() {
    return true;
  }
}"""
        ).checkMutations<TrueReturn> { mutations, contents ->
            mutations shouldHaveSize 2
            mutations[0].check(contents, "it", "true")
            mutations[1].check(contents, "false", "true")
        }
    }
    "it should find false returns to mutate" {
        Source.fromJava(
            """
public class Example {
  public static void first() {}
  public static boolean second() {
    it = false;
    return it;
  }
  public static boolean third() {
    return false;
  }
  public static boolean fourth() {
    return true;
  }
}"""
        ).checkMutations<FalseReturn> { mutations, contents ->
            mutations shouldHaveSize 2
            mutations[0].check(contents, "it", "false")
            mutations[1].check(contents, "true", "false")
        }
    }
    "it should find null returns to mutate" {
        Source.fromJava(
            """
public class Example {
  public static void first() {}
  public static boolean second() {
    it = false;
    return it;
  }
  public static boolean third() {
    return false;
  }
  public static Object fourth() {
    return new Object();
  }
  public static int[] fifth() {
    return new int[] {};
  }
}"""
        ).checkMutations<NullReturn> { mutations, contents ->
            mutations shouldHaveSize 2
            mutations[0].check(contents, "new Object()", "null")
            mutations[1].check(contents, "new int[] {}", "null")
        }
    }
    "it should find asserts to mutate" {
        Source.fromJava(
            """
public class Example {
  public static void test(int first, int second) {
    assert first > 0;
    assert second >= 0 : "Bad second value";
  }
}"""
        ).checkMutations<RemoveAssert> { mutations, contents ->
            mutations shouldHaveSize 2
            mutations[0].check(contents, "assert first > 0;", "")
            mutations[1].check(contents, """assert second >= 0 : "Bad second value";""", "")
        }
    }
    "it should remove entire methods" {
        Source.fromJava(
            """
public class Example {
  public static int test(int first, int second) {
    if (first > second) {
      return first;
    } else {
      return second;
    }
  }
  public static long[] test(int first, int second) {
    return new long[] {1L, 2L, 4L};
  }
}"""
        ).checkMutations<RemoveMethod> { mutations, _ ->
            mutations shouldHaveSize 2
        }
    }
    "it should not remove entire methods if they are already blank" {
        Source.fromJava(
            """
public class Example {
  public static void test(int first, int second) {
  }
  public static void test2(int first, int second) { }
  public static void test3(int first, int second) {
  
  
    }
  public static void test4(int first, int second) { return; }
  public static void test4(int first, int second) {
return ;
}
}"""
        ).checkMutations<RemoveMethod> { mutations, _ ->
            mutations shouldHaveSize 0
        }
    }
    "it should negate if statements" {
        Source.fromJava(
            """
public class Example {
  public static int test(int first, int second) {
    if (first > second) {
      return first;
    } else {
      return second;
    }
  }
}"""
        ).checkMutations<NegateIf> { mutations, contents ->
            mutations shouldHaveSize 1
            mutations[0].check(contents, "(first > second)", "(!(first > second))")
        }
    }
    "it should negate while statements" {
        Source.fromJava(
            """
public class Example {
  public static int test(int first) {
    int i = 0;
    while (i < first) {
      i++;
    }
  }
}"""
        ).checkMutations<NegateWhile> { mutations, contents ->
            mutations shouldHaveSize 1
            mutations[0].check(contents, "(i < first)", "(!(i < first))")
        }
    }
    "it should remove if statements" {
        Source.fromJava(
            """
public class Example {
  public static int test(int first) {
    if (first > 0) {
      System.out.println(1);
    }
    if (first > 0) {
      System.out.println(2);
    } else {
      System.out.println(3);
    }
    if (first > 0) {
      System.out.println(4);
    } else if (first < 0) {
      System.out.println(5);
    } else if (first == 0) {
      System.out.println(6);
    } else {
      if (first < 0) {
        System.out.println(7);
      }
      System.out.println(7);
    }
  }
}"""
        ).checkMutations<RemoveIf> { mutations, contents ->
            mutations shouldHaveSize 8
            mutations[0].check(
                contents,
                """if (first > 0) {
      System.out.println(1);
    }""",
                ""
            )
        }
    }
    "it should flip and and or" {
        Source.fromJava(
            """
public class Example {
  public static int test(int first) {
    if (first > 0 && first < 0) {
      System.out.println(1);
    }
  }
}"""
        ).checkMutations<AndOr> { mutations, contents ->
            mutations shouldHaveSize 1
            mutations[0].check(contents, "&&", "||")
        }
    }
    "it should remove loops correctly" {
        Source.fromJava(
            """
public class Example {
  public static int test(int first) {
    for (int i = 0; i < first; i++) { }
    while (true) { }
    for (int i : new int[] {1, 2, 4}) { }
  }
}"""
        ).checkMutations<RemoveLoop> { mutations, contents ->
            mutations shouldHaveSize 3
            mutations[0].check(contents, "for (int i = 0; i < first; i++) { }", "")
        }
    }
    "it should remove blank lines correctly" {
        val source = Source.fromJava(
            """
public class Example {
  public static void test(int first, int second) {
    assert first > 0;
    assert second >= 0 : "Bad second value";
  }
}""".trim()
        )
        source.allMutations(types = setOf(Mutation.Type.REMOVE_ASSERT)).also { mutations ->
            mutations shouldHaveSize 2
            mutations[0].contents.lines() shouldHaveSize 5
            mutations[0].contents.lines().filter { it.isBlank() } shouldHaveSize 0
            mutations[1].contents.lines() shouldHaveSize 5
            mutations[1].contents.lines().filter { it.isBlank() } shouldHaveSize 0
        }
    }
    "it should ignore suppressed mutations" {
        Source.fromJava(
            """
public class Example {
  public static Object fourth() {
    return new Object(); // mutate-disable
  }
}"""
        ).allMutations().also { mutations ->
            mutations shouldHaveSize 0
        }
    }
    "it should ignore specific suppressed mutations" {
        Source.fromJava(
            """
public class Example {
  public static int fourth(int first, int second) {
    if (first > second) { // mutate-disable-conditional-boundary
      return first;
    } else {
      return second;
    }
  }
}"""
        ).allMutations().also { mutations ->
            mutations shouldHaveSize 7
        }
    }
    "it should apply multiple mutations" {
        Source.fromJava(
            """
public class Example {
  public static void greeting() {
    int i = 0;
    System.out.println("Hello, world!");
  }
}"""
        ).also { source ->
            source.mutater(types = ALL - setOf(Mutation.Type.REMOVE_METHOD)).also { mutater ->
                mutater.appliedMutations shouldHaveSize 0
                val modifiedSource = mutater.apply().contents
                source.contents shouldNotBe modifiedSource
                mutater.appliedMutations shouldHaveSize 1
                mutater.size shouldBe 1
                val anotherModifiedSource = mutater.apply().contents
                setOf(source.contents, modifiedSource, anotherModifiedSource) shouldHaveSize 3
                mutater.size shouldBe 0
            }
            source.mutate().also { mutatedSource ->
                source.contents shouldNotBe mutatedSource.contents
                mutatedSource.mutations shouldHaveSize 1
            }
            source.mutate(limit = Int.MAX_VALUE).also { mutatedSource ->
                source.contents shouldNotBe mutatedSource.contents
                mutatedSource.unappliedMutations shouldBe 0
            }
            source.allMutations(types = ALL - setOf(Mutation.Type.REMOVE_METHOD)).also { mutatedSources ->
                mutatedSources shouldHaveSize 2
                mutatedSources.map { it.contents }.toSet() shouldHaveSize 2
            }
        }
    }
    "it should handle overlapping mutations" {
        Source.fromJava(
            """
public class Example {
  public static int testing() {
    return 10;
  }
}"""
        ).also { source ->
            source.mutater(types = ALL - setOf(Mutation.Type.REMOVE_METHOD)).also { mutater ->
                mutater.size shouldBe 2
                mutater.apply()
                mutater.size shouldBe 0
            }
        }
    }
    "it should shift mutations correctly" {
        Source.fromJava(
            """
public class Example {
  public static int testing() {
    boolean it = true;
    return 10;
  }
}"""
        ).also { source ->
            source.mutater(shuffle = false, types = ALL - setOf(Mutation.Type.REMOVE_METHOD)).also { mutater ->
                mutater.size shouldBe 3
                mutater.apply()
                mutater.size shouldBe 2
                mutater.apply()
                mutater.size shouldBe 0
            }
            source.allMutations(types = ALL - setOf(Mutation.Type.REMOVE_METHOD)).also { mutations ->
                mutations shouldHaveSize 3
                mutations.map { it.contents }.toSet() shouldHaveSize 3
            }
        }
    }
    "it should return predictable mutations" {
        Source.fromJava(
            """
public class Example {
  public static int testing() {
    boolean it = true;
    return 10;
  }
}"""
        ).also { source ->
            val first = source.allMutations(random = Random(seed = 10))
            val second = source.allMutations(random = Random(seed = 10))
            first.size shouldBe second.size
            first.zip(second).forEach { (first, second) ->
                first.contents shouldBe second.contents
            }
        }
    }
    "it should apply mutations correctly with Strings" {
        Source.fromJava(
            """
public class Example {
  String reformatName(String input) {
    if (input == null) {
      return null;
    }
    String[] parts = input.split(",");
    return parts[1].trim() + " " + parts[0].trim();
  }
}"""
        ).also { source ->
            source.allMutations()
        }
    }
    "it should apply stream mutations" {
        Source.fromJava(
            """
public class Example {
  String testStream() {
    String test = "foobarfoobarfoobarfoobar";
    return test;
  }
}"""
        ).also { source ->
            source.mutationStream().take(1024).toList().size shouldBe 1024
        }
    }
    "it should end stream mutations when out of things to mutate" {
        Source.fromJava(
            """
public class Example {
  int testStream() {
    int i = 0;
    i++;
    return i;
  }
}"""
        ).also { source ->
            source.mutationStream().take(1024).toList().size shouldBe 5
        }
    }
    "it should not mutate annotations" {
        Source.fromJava(
            """
public class Example {
  @Suppress("unused")
  void reformatName(String input) {
    return;
  }
}"""
        ).also { source ->
            source.allMutations() shouldHaveSize 0
        }
    }
})

inline fun <reified T : Mutation> Source.checkMutations(
    checker: (mutations: List<Mutation>, contents: String) -> Unit
) = getParsed(name).also { parsedSource ->
    checker(Mutation.find<T>(parsedSource), contents)
}

fun Mutation.check(contents: String, original: String, modified: String? = null): String {
    original shouldNotBe modified
    applied shouldBe false
    this.original shouldBe original
    this.modified shouldBe null
    val toReturn = apply(contents)
    applied shouldBe true
    this.original shouldBe original
    this.modified shouldNotBe original
    modified?.also { this.modified shouldBe modified }
    return toReturn
}
