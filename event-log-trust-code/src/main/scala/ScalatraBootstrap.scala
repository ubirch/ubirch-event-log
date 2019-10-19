import com.ubirch.Service
import com.ubirch.controllers.{ EventLogTrustCodeController, ResourcesController }
import javax.servlet.ServletContext
import org.scalatra.LifeCycle

class ScalatraBootstrap extends LifeCycle {

  lazy val eventLogController = Service.get[EventLogTrustCodeController]
  lazy val resourceController = Service.get[ResourcesController]

  override def init(context: ServletContext) {

    context.initParameters("org.scalatra.cors.preflightMaxAge") = "5"
    context.initParameters("org.scalatra.cors.allowCredentials") = "false"

    context.mount(eventLogController, "/", "RestApi")
    context.mount(resourceController, "/api-docs")
  }
}

