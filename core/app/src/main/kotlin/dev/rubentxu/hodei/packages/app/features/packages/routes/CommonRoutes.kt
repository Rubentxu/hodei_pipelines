package dev.rubentxu.hodei.packages.app.features.packages.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.commonRoutes() { // Consider injecting services if needed, e.g., for search

    // GET /search
    get("/search") {
        // TODO: Implement search logic
        call.respond(HttpStatusCode.NotImplemented, "Search endpoint not implemented yet.")
    }

    // GET /content/{hash}
    get("/content/{hash}") {
        val hash = call.parameters["hash"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Hash parameter is missing.")
        // TODO: Implement content download logic by hash
        call.respond(HttpStatusCode.NotImplemented, "Content download for hash $hash not implemented yet.")
    }
}
