package mixit.controller

import mixit.model.User
import mixit.repository.UserRepository
import mixit.support.security.OAuth
import mixit.support.security.OAuthFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.MediaType.TEXT_HTML
import org.springframework.stereotype.Controller
import org.springframework.web.reactive.function.BodyExtractors
import org.springframework.web.reactive.function.server.RequestPredicates.accept
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.route
import reactor.core.publisher.Mono


@Controller
class AuthenticationController(val userRepository: UserRepository, val oAuthFactory: OAuthFactory) : RouterFunction<ServerResponse> {

    override fun route(req: ServerRequest) = route(req) {
        accept(TEXT_HTML).apply {
            GET("/login") { loginView() }
            GET("/oauth/{provider}") { oauthCallback(req) }
            // TODO Use POST
            GET("/logout") { logout(req) }
        }
        accept(MediaType.APPLICATION_FORM_URLENCODED).apply {
            POST("/login") { login(req) }
        }
    }

    fun loginView() = ok().render("login")

    fun login(req: ServerRequest) = req.body(BodyExtractors.toFormData()).then { data ->
        val provider: String? = data.toSingleValueMap()["provider"]

        if (provider.isNullOrBlank()) {
            req.session().then { session ->
                //TODO use search by mail
                session.attributes["username"] = data.toSingleValueMap()["email"]
                ok().render("home")
            }
        } else {
            // When user chooses an OAuth authentication we call the provider to start the dance
            val oauthService: OAuth = oAuthFactory.create(provider.orEmpty())
            ServerResponse.status(HttpStatus.SEE_OTHER).location(oauthService.providerOauthUri(req)).build()
        }
    }


    fun logout(req: ServerRequest) = req.session().then { session ->
        session.attributes.remove("username")
        ok().render("home")
    }

    /**
     * Endpoint called by an OAuth provider when the user is authenticated
     */
    fun oauthCallback(request: ServerRequest): Mono<ServerResponse> {
        val oauthService: OAuth = oAuthFactory.create(request.pathVariable("provider").orEmpty())
        val oauthId = oauthService.getOAuthId(request)

        if (oauthId.isPresent()) {
            userRepository.findByLogin(oauthId.get()).then { user ->
                if (user != null) {
                    request.session().block().attributes["username"] = "${user.firstname}"
                } else {
                    userRepository.save(User(oauthId.get(), "", "", "")).block()
                }
                ok().render("home")
            }
        }
        return ok().render("login")
    }

}

