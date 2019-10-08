import com.ubirch.controllers.{ ApiSwagger, EventLogController, ResourcesController }
import javax.servlet.ServletContext
import org.scalatra.LifeCycle

class ScalatraBootstrap extends LifeCycle {

  implicit val swagger: ApiSwagger = new ApiSwagger

  override def init(context: ServletContext) {

    context.initParameters("org.scalatra.cors.preflightMaxAge") = "5"
    context.initParameters("org.scalatra.cors.allowCredentials") = "false"

    context.mount(new EventLogController, "/", "RestApi")
    context.mount(new ResourcesController, "/api-docs")
  }
}

