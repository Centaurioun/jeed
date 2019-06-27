package edu.illinois.cs.cs125.jeed.server

import io.kotlintest.assertions.ktor.shouldHaveStatus
import io.kotlintest.specs.StringSpec
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody

class TestHTTP : StringSpec({
    "f:should accept good request" {
        withTestApplication(Application::jeed) {
            handleRequest(HttpMethod.Post, "/") {
                addHeader("content-type", "application/json")
                setBody("""
{
  "source": "System.out.println(\"Here\");",
  "tasks": [ "compile" ]
}""".trim())
            }.apply {
                response.shouldHaveStatus(HttpStatusCode.OK.value)
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
            }
        }
    }
})
