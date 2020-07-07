package shop

import cats.Monad
import cats.data.ReaderT
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
import zio._
import zio.internal.Platform
import zio.interop.catz._
import zio.interop.catz.implicits._

object Main extends CatsApp {
  implicit def deriveContext[A, B, F[_]](implicit ctx: F HasContext A, ex: A Contains B): F HasContext B = HasContext[F, A].extract(ex)

  implicit val logger: SelfAwareStructuredLogger[Task] = Slf4jLogger.getLogger[Task]

  @ClassyOptics
  case class Environment[F[_]](@promote resources: AppResources[F],
                               @promote config: AppConfig)

  def runApp[F[_] : Monad : Logger,
    G[_] : ConcurrentEffect : Logger : Timer : ContextShift : WithRun[
      *[_],
      F,
      Environment[G]
    ]]: Environment[G] => F[ExitCode] =
    runContext[G](for {
      env <- context[G]
      security <- Security.make[G]
      algebras <- Algebras.make[G]
      clients <- HttpClients.make[G]
      programs <- Programs.make[G](algebras, clients)
      api <- HttpApi.make[G](algebras, programs, security)
      _ <- BlazeServerBuilder[G]
        .bindHttp(
          env.config.httpServerConfig.port.value,
          env.config.httpServerConfig.host.value
        )
        .withHttpApp(api.httpApp)
        .serve
        .compile
        .drain
    } yield ExitCode.Success)

  type ResIO[A] = Resource[IO, A]
  implicit val resourceLogger: SelfAwareStructuredLogger[ResIO] = Slf4jLogger.getLogger[ResIO]

  override def run(args: List[String]): IO[ExitCode] = Resource.liftF(config.load[IO]).flatMap {
    cfg => Resource.liftF(Logger[RIO].info(s"Loaded config $cfg")) >> AppResources.make[RIO](cfg).map(Environment(_, cfg))
  }.use(runApp[ResIO, RIO])

  //    config.load[IO].flatMap {
  //      cfg =>
  //        Logger[IO].info(s"Loaded config $cfg") >>
  //        AppResources.make[IO](cfg).map(Environment(_, cfg)).use {
  //          implicit env =>
  //            for {
  //              security <- Security.make[IO]
  //              algebras <- Algebras.make[IO]
  //              clients  <- HttpClients.make[IO]
  //              programs <- Programs.make[IO](cfg.checkoutConfig, algebras, clients)
  //              api      <- HttpApi.make[IO](algebras, programs, security)
  //              _ <- BlazeServerBuilder[IO]
  //                    .bindHttp(
  //                      cfg.httpServerConfig.port.value,
  //                      cfg.httpServerConfig.host.value
  //                    )
  //                    .withHttpApp(api.httpApp)
  //                    .serve
  //                    .compile
  //                    .drain
  //            } yield ExitCode.Success
  //        }
  //    }
}
