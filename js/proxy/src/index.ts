import { Request, Response, ServerStatus } from "@cs124/jeed-types"
import { googleLogin } from "@cs124/koa-google-login"
import cors from "@koa/cors"
import Router from "@koa/router"
import { createHttpTerminator } from "http-terminator"
import Koa, { Context } from "koa"
import koaBody from "koa-body"
import ratelimit from "koa-ratelimit"
import { MongoClient as mongo } from "mongodb"
import mongodbUri from "mongodb-uri"
import fetch from "node-fetch"
import { String } from "runtypes"

const BACKEND = String.check(process.env.JEED_SERVER)

const { database } = String.guard(process.env.MONGODB) ? mongodbUri.parse(process.env.MONGODB) : { database: undefined }
const client = String.guard(process.env.MONGODB) ? mongo.connect(process.env.MONGODB) : undefined
const _collection = client?.then((c) => c.db(database).collection(process.env.MONGODB_COLLECTION || "jeed"))
const validDomains = process.env.VALID_DOMAINS?.split(",").map((s) => s.trim())

const router = new Router<Record<string, unknown>, { email?: string }>()

const audience = process.env.GOOGLE_CLIENT_IDS?.split(",").map((s) => s.trim())

const PORT = process.env.PORT || 8888

const STATUS = Object.assign(
  {
    backend: BACKEND,
    what: "jeed",
    started: new Date(),
    port: PORT,
  },
  audience ? { audience } : null,
  { mongoDB: client !== undefined }
)
const getStatus = async () => {
  return { ...STATUS, status: ServerStatus.check(await fetch(BACKEND).then((r) => r.json())) }
}

router.get("/", async (ctx: Context) => {
  ctx.body = await getStatus()
})
router.post("/", async (ctx) => {
  const start = new Date()
  const collection = await _collection
  const request = Request.check(ctx.request.body)
  let response: Response
  try {
    response = await fetch(BACKEND, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    }).then(async (r) => {
      if (r.status === 200) {
        return Response.check(await r.json())
      } else {
        throw await r.text()
      }
    })
  } catch (err) {
    collection?.insertOne(
      Object.assign(
        { succeeded: false, ...request, start, end: new Date(), ip: ctx.request.ip, err },
        String.guard(process.env.SEMESTER) ? { semester: process.env.SEMESTER } : null,
        ctx.email ? { email: ctx.email } : null
      )
    )
    return ctx.throw(err, 400)
  }
  ctx.body = response
  collection?.insertOne(
    Object.assign(
      { succeeded: true, ...response, start, end: new Date(), ip: ctx.request.ip },
      String.guard(process.env.SEMESTER) ? { semester: process.env.SEMESTER } : null,
      ctx.email ? { email: ctx.email } : null
    )
  )
})

const db = new Map()
const server = new Koa({ proxy: true })
  .use(
    cors({
      origin: (ctx) => {
        if (!ctx.headers.origin || (validDomains && !validDomains.includes(ctx.headers.origin))) {
          return ""
        } else {
          return ctx.headers.origin
        }
      },
      maxAge: 86400,
    })
  )
  .use(
    ratelimit({
      driver: "memory",
      db: db,
      duration: process.env.RATE_LIMIT_MS ? parseInt(process.env.RATE_LIMIT_MS) : 1000,
      headers: {
        remaining: "Rate-Limit-Remaining",
        reset: "Rate-Limit-Reset",
        total: "Rate-Limit-Total",
      },
      max: 1,
      whitelist: (ctx) => ctx.request.method === "GET",
    })
  )
  .use(audience ? googleLogin({ audience, required: false }) : (_, next) => next())
  .use(koaBody({ jsonLimit: "8mb" }))
  .use(router.routes())
  .use(router.allowedMethods())

Promise.resolve().then(async () => {
  console.log(await getStatus())
  const s = server.listen(process.env.PORT || 8888)
  server.on("error", (err) => {
    console.error(err)
  })
  const terminator = createHttpTerminator({ server: s })
  process.on("SIGTERM", async () => await terminator.terminate())
})
process.on("uncaughtException", (err) => {
  console.error(err)
})
