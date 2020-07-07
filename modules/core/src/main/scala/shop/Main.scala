package shop

import cats.Monad
import cats.effect._
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.server.blaze.BlazeServerBuilder
import shop.config.data.AppConfig
import shop.modules._
import tofu.optics.macros._
import tofu.syntax.context._
import tofu.{HasContext, WithRun}

object Main extends IOApp {

  implicit val logger = Slf4jLogger.getLogger[IO]

  @ClassyOptics
  case class Environment[F[_]](@promote resources: AppResources[F],
                               @promote config: AppConfig)

  def createApp[F[_]: ConcurrentEffect: ContextShift: Logger, G[_]: ConcurrentEffect: Logger: Timer: ContextShift: WithRun[
    *[_],
    F,
    Environment[G]
  ]] =
    config.load[F].flatMap {
      cfg =>
        Logger[F].info(s"Loaded config $cfg") >>
        AppResources.make[G](cfg).map(Environment[G](_, cfg)).use {
          env => runContext[G].apply[Unit, Environment[G], F](for {
              security <- Security.make[G]
              algebras <- Algebras.make[G]
              clients  <- HttpClients.make[G]
              programs <- Programs.make[G](algebras, clients)
              api      <- HttpApi.make[G](algebras, programs, security)
              _ <- BlazeServerBuilder[G]
                    .bindHttp(
                      cfg.httpServerConfig.port.value,
                      cfg.httpServerConfig.host.value
                    )
                    .withHttpApp(api.httpApp)
                    .serve
                    .compile
                    .drain
            } yield ExitCode.Success)(env)
        }
    }

  override def run(args: List[String]): IO[ExitCode] = ???

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
