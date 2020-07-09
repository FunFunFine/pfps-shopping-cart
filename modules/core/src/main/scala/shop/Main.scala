package shop

import java.util.concurrent.Executors

import cats.{Applicative, Monad, Parallel}
import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.{Logger, SelfAwareStructuredLogger}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.server.blaze.BlazeServerBuilder
import shop.config.data.AppConfig
import shop.modules._
import tofu.optics.Contains
import tofu.optics.macros._
import tofu.syntax.context._
import tofu.{HasContext, WithRun}
import tofu.syntax.context._
import tofu.zioInstances.implicits._
import tofu.zioOptics._
import zio._
import zio.internal.Platform
import zio.interop.catz._
import zio.interop.catz.implicits._

object Main extends CatsApp {

  implicit def deriveContext[A, B, F[_]](implicit ctx: F HasContext A,
                                         ex: A Contains B): F HasContext B =
    HasContext[F, A].extract(ex)

  implicit val logger: SelfAwareStructuredLogger[Task] = Slf4jLogger.getLogger[Task]

  @ClassyOptics
  case class Environment[F[_]](@promote resources: AppResources[F],
                               @promote config: AppConfig)

  type WithRunEnv[G[_], F[_]] = WithRun[G, F, Environment[G]]

  def runApp[
    F[_]: Monad,
    G[_]: ConcurrentEffect: Parallel: Logger: Timer: ContextShift: WithRunEnv[*[_], F]

  ](env: Environment[G]): F[Unit] =
    runContext[G].apply[Unit, Environment[G], F](for {
      env      <- context[G]
      security <- Security.make[G]
      algebras <- Algebras.make[G]
      clients  <- HttpClients.make[G]
      programs <- Programs.make[G](algebras, clients)
      api      <- HttpApi.make[G](algebras, programs, security)
      _ <- BlazeServerBuilder[G]
            .bindHttp(
              env.config.httpServerConfig.port.value,
              env.config.httpServerConfig.host.value
            )
            .withHttpApp(api.httpApp)
            .serve
            .compile
            .drain
    } yield ())(env)
  type ZE[A] = ZIO[Environment[ZE], Throwable, A]
  def zwr[R: Runtime] = implicitly[ConcurrentEffect[ZIO[R, Throwable, *]]]
  type ResIO[A] = Resource[Task, A]

  def wr[R, E] = implicitly[WithRun[ZIO[R, E, *], ZIO[Any, E, *], R]]

  private val blocker =
    Blocker.liftExecutorService(Executors.newCachedThreadPool())

  def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    (for {
      cfg       <- Resource.liftF(config.load[ZE])
      _         <- Resource.liftF(Logger[ZE].info(s"Loaded config $cfg"))
      resources <- AppResources.make[ZE](cfg)
    } yield Environment[ZE](resources, cfg))
      .map(
        env =>
          runApp[ZIO[Any, Throwable, *], ZE](env)
      )
      .use(_ => Task.never)
      .catchAllCause(cause => logger.error(s"Fatal error: ${cause.prettyPrint}"))
      .fold(_ => 0, _ => 1)

}
