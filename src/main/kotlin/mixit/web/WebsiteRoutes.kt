package mixit.web

import mixit.MixitProperties
import mixit.repository.EventRepository
import mixit.web.handler.*
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType.*
import org.springframework.web.reactive.function.server.*
import org.springframework.web.reactive.function.server.RouterFunctions.resources
import reactor.core.publisher.toMono


@Configuration
class WebsiteRoutes(val adminHandler: AdminHandler,
                    val authenticationHandler: AuthenticationHandler,
                    val blogHandler: BlogHandler,
                    val globalHandler: GlobalHandler,
                    val newsHandler: NewsHandler,
                    val talkHandler: TalkHandler,
                    val userHandler: UserHandler,
                    val sponsorHandler: SponsorHandler,
                    val ticketingHandler: TicketingHandler,
                    val messageSource: MessageSource,
                    val properties: MixitProperties,
                    val eventRepository: EventRepository) {


    @Bean
    @DependsOn("databaseInitializer")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun websiteRouter() = router {
        accept(TEXT_HTML).nest {
            GET("/") { sponsorHandler.viewWithSponsors("home", null, it) }
            GET("/about", globalHandler::findAboutView)
            GET("/news", newsHandler::newsView)
            GET("/ticketing", ticketingHandler::ticketing)

            // Authentication
            GET("/login", authenticationHandler::loginView)

            // Talks
            eventRepository.findAll().toIterable().map { it.year }.forEach { year ->
                GET("/$year") { talkHandler.findByEventView(year, it) }
                GET("/$year/makers") { talkHandler.findByEventView(year, it, "makers") }
                GET("/$year/aliens") { talkHandler.findByEventView(year, it, "aliens") }
                GET("/$year/tech") { talkHandler.findByEventView(year, it, "tech") }
                GET("/$year/design") { talkHandler.findByEventView(year, it, "design") }
                GET("/$year/hacktivism") { talkHandler.findByEventView(year, it, "hacktivism") }
                GET("/$year/learn") { talkHandler.findByEventView(year, it, "learn") }
                GET("/$year/{slug}") { talkHandler.findOneView(year, it) }
            }

            // Users
            (GET("/user/{login}") or GET("/sponsor/{login}")).invoke(userHandler::findOneView)
            GET("/sponsors") { sponsorHandler.viewWithSponsors("sponsors", "sponsors.title", it) }

            "/admin".nest {
                GET("/", adminHandler::admin)
                GET("/ticketing", adminHandler::adminTicketing)
                GET("/talks", adminHandler::adminTalks)
                DELETE("/")
                GET("/talks/edit/{slug}", adminHandler::editTalk)
                GET("/talks/create", adminHandler::createTalk)
                GET("/users", adminHandler::adminUsers)
            }

            "/blog".nest {
                GET("/", blogHandler::findAllView)
                GET("/{slug}", blogHandler::findOneView)
            }
        }

        accept(TEXT_EVENT_STREAM).nest {
            GET("/news/sse", newsHandler::newsSse)
        }

        contentType(APPLICATION_FORM_URLENCODED).nest {
            POST("/login", authenticationHandler::login)
            //POST("/ticketing", ticketingHandler::submit)
            "/admin".nest {
                POST("/talks", adminHandler::adminSaveTalk)
                POST("/talks/delete", adminHandler::adminDeleteTalk)
            }
        }
    }.filter { request, next ->
        val locale = request.headers().asHttpHeaders().acceptLanguageAsLocale
        val session = request.session().block()
        val path = request.uri().path
        val model = generateModel(properties.baseUri!!, path, locale, session, messageSource)
                next.handle(request).then { response -> if (response is RenderingResponse) RenderingResponse.from(response).modelAttributes(model).build() else response.toMono() }
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun resourceRouter() = resources("/**", ClassPathResource("static/"))

}

