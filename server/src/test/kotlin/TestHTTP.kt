package edu.illinois.cs.cs125.jeed.server

import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import io.kotlintest.*
import io.kotlintest.assertions.ktor.shouldHaveStatus
import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.specs.StringSpec
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import org.bson.BsonDocument

class TestHTTP : StringSpec() {
    override fun beforeSpec(spec: Spec) {
        configuration[TopLevel.mongodb]?.let {
            val mongoUri = MongoClientURI(it)
            val database = mongoUri.database ?: require { "MONGO must specify database to use" }
            val collection = "${configuration[TopLevel.Mongo.collection]}-test"
            Job.mongoCollection = MongoClient(mongoUri)
                    .getDatabase(database)
                    .getCollection(collection, BsonDocument::class.java)
        }
    }

    override fun beforeTest(testCase: TestCase) {
        Job.mongoCollection?.drop()
        Job.mongoCollection?.countDocuments() shouldBe 0
    }
    override fun afterTest(testCase: TestCase, result: TestResult) {
        Job.mongoCollection?.drop()
        Job.mongoCollection?.countDocuments() shouldBe 0
    }

    init {
        "should accept good snippet request" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("""
{
"label": "test",
"snippet": "System.out.println(\"Here\");",
"tasks": [ "compile", "execute" ],
"waitForSave": true
}""".trim())
                }.apply {
                    response.shouldHaveStatus(HttpStatusCode.OK.value)
                    Job.mongoCollection?.countDocuments() shouldBe 1

                    val result = Result.from(response.content)
                    result.completed.execution?.klass shouldBe "Main"
                    result.completedTasks.size shouldBe 3
                    result.failedTasks.size shouldBe 0
                }
            }
        }
        "should accept good source request" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("""
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": " 
public class Main {
  public static void main() {
    System.out.println(\"Here\");
  }
}"
  }
],
"tasks": [ "compile", "execute" ],
"waitForSave": true
}""".trim())
                }.apply {
                    response.shouldHaveStatus(HttpStatusCode.OK.value)
                    Job.mongoCollection?.countDocuments() shouldBe 1

                    val result = Result.from(response.content)
                    result.completedTasks.size shouldBe 2
                    result.failedTasks.size shouldBe 0
                }
            }
        }
        "f: should accept good kotlin source request" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("""
{
"label": "test",
"sources": [
  {
    "path": "Main.kt",
    "contents": "
fun main() {
  println(\"Here\")
}"
  }
],
"tasks": [ "kompile", "execute" ],
"waitForSave": true
}""".trim())
                }.apply {
                    response.shouldHaveStatus(HttpStatusCode.OK.value)
                    Job.mongoCollection?.countDocuments() shouldBe 1

                    val result = Result.from(response.content)
                    result.completed.execution?.klass shouldBe "MainKt"
                    result.completedTasks.size shouldBe 2
                    result.failedTasks.size shouldBe 0
                }
            }
        }
        "should accept good source checkstyle request" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("""
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
public static void main() {
    System.out.println(\"Here\");
}
}"
  }
],
"tasks": [ "checkstyle", "compile", "execute" ],
"waitForSave": true
}""".trim())
                }.apply {
                    response.shouldHaveStatus(HttpStatusCode.OK.value)
                    Job.mongoCollection?.countDocuments() shouldBe 1

                    val result = Result.from(response.content)
                    result.completedTasks.size shouldBe 3
                    result.failedTasks.size shouldBe 0
                }
            }
        }
        "should reject checkstyle request for non-Java sources" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("""
{
"label": "test",
"sources": [
  {
    "path": "Main.kt",
    "contents": "
fun main() {
  println(\"Here\")
}"
  }
],
"tasks": [ "checkstyle", "kompile", "execute" ],
"waitForSave": true
}""".trim())
                }.apply {
                    response.shouldHaveStatus(HttpStatusCode.BadRequest.value)
                    Job.mongoCollection?.countDocuments() shouldBe 0
                }
            }
        }
        "should accept good templated source request" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("""
{
"label": "test",
"templates": [
  {
    "path": "Main.hbs",
    "contents": "
public class Main {
public static void main() {
    {{{ contents }}}
}
}"
  }
],
"sources": [
  {
    "path": "Main.java",
    "contents": "System.out.println(\"Here\");"
  }
],
"tasks": [ "template", "compile", "execute" ],
"waitForSave": true
}""".trim())
                }.apply {
                    response.shouldHaveStatus(HttpStatusCode.OK.value)
                    Job.mongoCollection?.countDocuments() shouldBe 1

                    val result = Result.from(response.content)
                    result.completedTasks.size shouldBe 3
                    result.failedTasks.size shouldBe 0
                }
            }
        }
        "should handle snippet error" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("""
{
"label": "test",
"snippet": "System.out.println(\"Here\")",
"tasks": [ "snippet" ],
"waitForSave": true
}""".trim())
                }.apply {
                    response.shouldHaveStatus(HttpStatusCode.OK.value)
                    Job.mongoCollection?.countDocuments() shouldBe 1

                    val result = Result.from(response.content)
                    result.completedTasks.size shouldBe 0
                    result.failedTasks.size shouldBe 1
                    result.failed.snippet?.errors?.size ?: 0 shouldBeGreaterThan 0
                }
            }
        }
        "should handle template error" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("""
{
"label": "test",
"templates": [
  {
    "path": "Main.hbs",
    "contents": "
public class Main {
public static void main() {
    {{ contents }}}
}
}"
  }
],
"sources": [
  {
    "path": "Main.java",
    "contents": "System.out.println(\"Here\");"
  }
],
"tasks": [ "template" ],
"waitForSave": true
}""".trim())
                }.apply {
                    response.shouldHaveStatus(HttpStatusCode.OK.value)
                    Job.mongoCollection?.countDocuments() shouldBe 1

                    val result = Result.from(response.content)
                    result.completedTasks.size shouldBe 0
                    result.failedTasks.size shouldBe 1
                    result.failed.template?.errors?.size ?: 0 shouldBeGreaterThan 0
                }
            }
        }
        "should handle compilation error" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("""
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": " 
public class Main {
  public static void main() {
    System.out.println(\"Here\")
  }
}"
  }
],
"tasks": [ "compile" ],
"waitForSave": true
}""".trim())
                }.apply {
                    response.shouldHaveStatus(HttpStatusCode.OK.value)
                    Job.mongoCollection?.countDocuments() shouldBe 1

                    val result = Result.from(response.content)
                    result.completedTasks.size shouldBe 0
                    result.failedTasks.size shouldBe 1
                    result.failed.compilation?.errors?.size ?: 0 shouldBeGreaterThan 0
                }
            }
        }
        "should handle kompilation error" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("""
{
"label": "test",
"sources": [
  {
    "path": "Main.kt",
    "contents": "
fun main() {
  printl(\"Here\")
}"
  }
],
"tasks": [ "kompile" ],
"waitForSave": true
}""".trim())
                }.apply {
                    response.shouldHaveStatus(HttpStatusCode.OK.value)
                    Job.mongoCollection?.countDocuments() shouldBe 1

                    val result = Result.from(response.content)
                    result.completedTasks.size shouldBe 0
                    result.failedTasks.size shouldBe 1
                    result.failed.kompilation?.errors?.size ?: 0 shouldBeGreaterThan 0
                }
            }
        }
        "should handle execution error" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("""
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": " 
public class Main {
  public static void main() {
    Object t = null;
    System.out.println(t.toString());
  }
}"
  }
],
"tasks": [ "compile", "execute" ],
"waitForSave": true
}""".trim())
                }.apply {
                    response.shouldHaveStatus(HttpStatusCode.OK.value)
                    Job.mongoCollection?.countDocuments() shouldBe 1

                    val result = Result.from(response.content)
                    result.completedTasks.size shouldBe 1
                    result.failedTasks.size shouldBe 1
                    result.failed.execution?.threw shouldNotBe ""
                }
            }
        }
        "should reject both source and snippet request" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("""
{
"label": "test",
"snippet": "System.out.println(\"Hello, world!\");",
"sources": [
  {
    "path": "Main.java",
    "contents": " 
public class Main {
  public static void main() {
    Object t = null;
    System.out.println(t.toString());
  }
}"
  }
],
"tasks": [ "compile", "execute" ],
"waitForSave": true
}""".trim())
                }
            }.apply {
                response.shouldHaveStatus(HttpStatusCode.BadRequest.value)
                Job.mongoCollection?.countDocuments() shouldBe 0
            }
        }
        "should reject neither source nor snippet request" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("""
{
"label": "test",
"tasks": [ "compile", "execute" ],
"waitForSave": true
}""".trim())
                }
            }.apply {
                response.shouldHaveStatus(HttpStatusCode.BadRequest.value)
                Job.mongoCollection?.countDocuments() shouldBe 0
            }
        }
        "should reject mapped source request" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("""
{
"label": "test",
"source": {
  "Main.java": " 
public class Main {
  public static void main() {
    System.out.println(\"Here\");
  }
}"
},
"tasks": [ "compile", "execute" ],
"waitForSave": true
}""".trim())
                }
            }.apply {
                response.shouldHaveStatus(HttpStatusCode.BadRequest.value)
                Job.mongoCollection?.countDocuments() shouldBe 0
            }
        }
        "should reject unauthorized request" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("broken")
                }.apply {
                    response.shouldHaveStatus(HttpStatusCode.BadRequest.value)
                    Job.mongoCollection?.countDocuments() shouldBe 0
                }
            }
        }
        "should reject bad request" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Post, "/") {
                    addHeader("content-type", "application/json")
                    setBody("broken")
                }.apply {
                    response.shouldHaveStatus(HttpStatusCode.BadRequest.value)
                    Job.mongoCollection?.countDocuments() shouldBe 0
                }
            }
        }
        "should provide info in response to GET" {
            withTestApplication(Application::jeed) {
                handleRequest(HttpMethod.Get, "/") {
                    addHeader("content-type", "application/json")
                }.apply {
                    response.shouldHaveStatus(HttpStatusCode.OK.value)
                    val status = Status.from(response.content)
                    status.versions.server shouldBe VERSION
                }
            }
        }
    }
}
